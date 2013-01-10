package org.drooms.gui.swing

import java.awt.Color
import java.awt.Dimension
import scala.swing.Component
import scala.swing.GridBagPanel
import scala.swing.Label
import scala.swing.Reactor
import scala.swing.ScrollPane
import scala.swing.Table
import org.drooms.gui.swing.event.PlaygroundGridDisabled
import org.drooms.gui.swing.event.PlaygroundGridEnabled
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import org.drooms.gui.swing.event.NewGameReportChosen
import java.awt.Font
import org.drooms.gui.swing.event.TurnStepPerformed
import org.drooms.gui.swing.event.DroomsEventPublisher
import javax.swing.table.DefaultTableModel
import org.drooms.gui.swing.event.PlaygroundGridEnabled
import scala.swing.Alignment
import scala.swing.BoxPanel
import scala.swing.FlowPanel
import javax.swing.UIManager
import org.drooms.gui.swing.event.CoordinantsVisibilityChanged

class Playground extends ScrollPane with Reactor {
  val CELL_SIZE = 15
  val eventPublisher = DroomsEventPublisher.get()
  var cellModel: PlaygroundModel = _
  var table: Option[Table] = None
  val worms: collection.mutable.Set[Worm] = collection.mutable.Set()
  var showCoords = false

  listenTo(eventPublisher)
  reactions += {
    case PlaygroundGridEnabled() => showGrid
    case PlaygroundGridDisabled() => hideGrid
    case NewGameReportChosen(gameReport, file) => {
      createNew(gameReport.playgroundWidth, gameReport.playgroundHeight)
      for (node <- gameReport.playgroundInit)
        cellModel.updatePosition(Empty(node))
      initWorms(gameReport.wormInitPositions)
    }

    case TurnStepPerformed(step) =>
      step match {
        case WormMoved(ownerName, nodes) =>
          moveWorm(ownerName, nodes)
        case WormCrashed(ownerName) =>
          removeWorm(ownerName)
        case WormDeactivated(ownerName) =>
          removeWorm(ownerName)
        case CollectibleAdded(collectible) =>
          updatePosition(collectible)
        case CollectibleRemoved(collectible) =>
          updatePosition(Empty(collectible.node))
        case CollectibleCollected(player, collectible) =>
        case _ => new RuntimeException("Unrecognized TurnStep: " + step)
      }
  }

  def createNew(width: Int, height: Int): Unit = {
    cellModel = new PlaygroundModel(width, height)
    // plus two in each direction (x and y) for border around the playground
    val actualTableWidth = width + 2 + 1 // +2 for wall border and +1 for coordinate numbers
    val actualTableHeight = height + 2 + 1 // +2 for wall border and +1 for coordinate numbers
    table = Some(new Table(actualTableWidth, actualTableHeight) {
      val widthPixels = CELL_SIZE * actualTableWidth - 1 // minus one so the line at the end is not rendered
      val heightPixels = CELL_SIZE * actualTableHeight - 1 // minus one so the line at the end is not rendered
      preferredSize = new Dimension(widthPixels, heightPixels)
      rowHeight = CELL_SIZE
      selection.intervalMode = Table.IntervalMode.Single
      selection.elementMode = Table.ElementMode.None
      peer.setTableHeader(null)
      model = new DefaultTableModel(actualTableHeight, actualTableWidth) { // rows == height, cols == width
        override def setValueAt(value: Any, row: Int, col: Int) {
          super.setValueAt(value, row, col)
        }
        override def isCellEditable(row: Int, column: Int) = false
      }
      showGrid = false
      peer.setIntercellSpacing(new Dimension(0, 0))
      lazy val wallIcon = createImageIcon("/images/brick-wall-small.png", "Wall")
      lazy val bonusIcon = createImageIcon("/images/strawberry-icon.png", "Bonus")
      val emptyComponent = new FlowPanel {
        // just empty space
      }
      trait CellType
      object Blank extends CellType
      object Border extends CellType
      object AxisXNumber extends CellType
      object AxisYNumber extends CellType
      object PlaygroundCell extends CellType

      // table has (0,0) in left upper corner, model has (0,0) in left down corner -> we need to translate the 
      // coordinates accordingly
      override def rendererComponent(isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int): Component = {
        def determineCellType(row: Int, col: Int): CellType = {
          // numbers on Y axis
          if (col == 0) {
            if (showCoords && row <= actualTableHeight - 3 && row > 0)
              AxisYNumber
            else
              Blank
          } else if (row == actualTableHeight - 1) { // number on X axis
            if (showCoords && col >= 2 && col < actualTableWidth - 1)
              AxisXNumber
            else
              Blank
          } else if (col == 1 || row == 0 || col == actualTableWidth - 1 || row == actualTableHeight - 2) { // border around the playground
            // second column | first row | last column | last row - 1
            Border
          } else {
            PlaygroundCell
          }
        }
        
        val wallLabel = new Label("") {
          icon = wallIcon
        }
        val cellType = determineCellType(row, col)
        cellType match {
          case Blank => emptyComponent
          case Border => wallLabel
          case AxisYNumber => {
            // number on Y axis
            new Label(actualTableHeight - row - 3 + "") {
              opaque = true
              background = UIManager.getColor("Panel.background")
              font = new Font("Serif", Font.BOLD, 10)

            }
          }
          case AxisXNumber => {
            // number on X axis
            // start numbering from the second col, so the 0,0 points to correct cell
            new Label(col - 2 + "") {
              opaque = true
              background = UIManager.getColor("Panel.background")
              font = new Font("Serif", Font.BOLD, 8)
            }
          }
          case PlaygroundCell => {
            val pos = cellModel.positions(col - 2)(actualTableHeight - row - 3)
            val cell = pos match {
              case Empty(_) => emptyComponent
              case WormPiece(_, wormType, playerName) => new Label() {
                opaque = true
                background = PlayersList.get().getPlayer(playerName).color
                border = BorderFactory.createRaisedBevelBorder()
                if (wormType == "Head") {
                  //border = BorderFactory.createLoweredBevelBorder()
                  text = "\u25CF" // full circle
                }
              }
              case Wall(_) => wallLabel

              case Collectible(_, _, p) => new Label(p + "") {
                opaque = true
                font = new Font("Serif", Font.BOLD, 10)
                icon = bonusIcon
                background = UIManager.getColor("Panel.background")
                verticalTextPosition = Alignment.Center
                horizontalTextPosition = Alignment.Center
              }
            }
            cell.tooltip = (pos.node.x + "," + pos.node.y)
            cell
          }
        }
      }
    })
    reactions += {
      case PositionChanged(position) =>
        // Y-axis numbering in playground model and talbe is reversed  
        // starting from 0 to actualTableHeight -1 and need to subtract the current position and -2 for number and wall down
        table.get.updateCell(actualTableHeight - 1 - position.node.y - 2, position.node.x + 2) // y == row and x == col
      case CoordinantsVisibilityChanged(value) => {
        showCoords = value
        // update the table, so the headers are painted
        table match {
          case Some(table) => {
            for (i <- 0 until actualTableWidth)
              table.updateCell(actualTableHeight - 1, i)
            for (j <- 0 until actualTableHeight)
              table.updateCell(j, 0)
          }
          case None =>
        }
      }
    }

    viewportView = new GridBagPanel {
      layout(table.get) = new Constraints
    }
  }

  def updatePositions(positions: List[Position]): Unit = {
    for (pos <- positions)
      updatePosition(pos)
  }

  def updatePosition(pos: Position) {
    cellModel.updatePosition(pos)
  }

  /** Initialize worms from specified list of pairs 'ownerName' -> 'list of Nodes' */
  def initWorms(wormsInit: Set[(String, List[Node])]): Unit = {
    worms.clear()
    for ((name, nodes) <- wormsInit) {
      worms.add(Worm(name, (for (node <- nodes) yield WormPiece(node, "Head", name)).toList))
    }
    // update model
    for (worm <- worms) updatePositions(worm.pieces)
  }

  /** Moves the worm to the new position */
  def moveWorm(ownerName: String, nodes: List[Node]): Unit = {
    this.synchronized {
      // removes current worm pieces
      removeWormPieces(ownerName)
      // worm must have at least head
      val head = nodes.head
      updateWormIfLegal(head, ownerName, "Head")

      if (nodes.size > 2) {
        for (node <- nodes.tail.init) {
          updateWormIfLegal(node, ownerName, "Body")
        }
      }

      if (nodes.size > 1) {
        val tail = nodes.last
        updateWormIfLegal(tail, ownerName, "Tail")
      }

      /**
       * Updates the wom only if the underlaying node is empty or collectible == eligible to be occupied by current worm
       */
      def updateWormIfLegal(node: Node, ownerName: String, wormType: String): Unit = {
        // we can only update Empty nodes and Collectibles, if the worm crashed into wall or other worm piece must not be updated!
        cellModel.positions(node.x)(node.y) match {
          case Empty(node) =>
            updateWorm(ownerName, new WormPiece(node, wormType, ownerName))
          case Collectible(node, _, _) =>
            updateWorm(ownerName, new WormPiece(node, wormType, ownerName))
          case _ =>
        }
      }
    }
  }

  def updateWorm(ownerName: String, piece: WormPiece) = {
    getWorm(ownerName).addPiece(piece)
    updatePosition(piece)
  }

  def getWorm(ownerName: String): Worm = {
    worms.find(_.ownerName == ownerName) match {
      case Some(worm) => worm
      case None => throw new RuntimeException("Can't update non existing worm! Owner=" + ownerName)
    }
  }

  /**
   * Removes the worm from the list of worms and also makes sure that all worm pieces are removed from playground
   */
  def removeWorm(ownerName: String): Unit = {
    val worm = getWorm(ownerName)
    removeWormPieces(ownerName)
    worms.remove(worm)
  }

  def removeWormPieces(ownerName: String): Unit = {
    worms.find(_.ownerName == ownerName) match {
      case Some(worm) =>
        for (piece <- worm.pieces) {
          cellModel.positions(piece.node.x)(piece.node.y) match {
            case WormPiece(node, t, owner) =>
              if (piece.playerName == owner) {
                updatePosition(Empty(node))
              }
            case _ =>
          }
          worm.pieces = List()
        }
      case None =>
    }
  }

  def isGridVisible(): Boolean = {
    table.get.showGrid
  }

  def hideGrid(): Unit = {
    table match {
      case Some(table) =>
        table.showGrid = false
        table.peer.setIntercellSpacing(new Dimension(0, 0))
      case None =>
    }
  }

  def showGrid(): Unit = {
    table match {
      case Some(table) =>
        table.showGrid = true
        table.peer.setIntercellSpacing(new Dimension(1, 1))
      case None =>
    }
  }

  def createImageIcon(path: String, description: String): ImageIcon = {
    val imgUrl = getClass().getResource(path)
    if (imgUrl != null) {
      new ImageIcon(imgUrl, description)
    } else {
      throw new RuntimeException("Could not find image file " + path)
    }
  }
}
