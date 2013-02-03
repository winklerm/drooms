package org.drooms.gui.swing

import java.awt.Dimension
import java.awt.Font
import java.io.File
import java.util.Timer
import java.util.TimerTask
import scala.swing.Action
import scala.swing.BorderPanel
import scala.swing.BoxPanel
import scala.swing.Button
import scala.swing.CheckMenuItem
import scala.swing.FileChooser
import scala.swing.FlowPanel
import scala.swing.Label
import scala.swing.MainFrame
import scala.swing.Menu
import scala.swing.MenuBar
import scala.swing.MenuItem
import scala.swing.Orientation
import scala.swing.ProgressBar
import scala.swing.Publisher
import scala.swing.Reactor
import scala.swing.SimpleSwingApplication
import scala.swing.Slider
import scala.swing.SplitPane
import scala.swing.event.ButtonClicked
import scala.swing.event.ValueChanged
import org.drooms.gui.swing.event.DroomsEventPublisher
import org.drooms.gui.swing.event.GameFinished
import org.drooms.gui.swing.event.GameRestarted
import org.drooms.gui.swing.event.NewGameReportChosen
import org.drooms.gui.swing.event.NextTurnInitiated
import org.drooms.gui.swing.event.PlaygroundGridDisabled
import org.drooms.gui.swing.event.PlaygroundGridEnabled
import org.drooms.gui.swing.event.ReplayContinued
import org.drooms.gui.swing.event.ReplayInitiated
import org.drooms.gui.swing.event.ReplayPaused
import org.drooms.gui.swing.event.TurnDelayChanged
import org.drooms.gui.swing.event.TurnStepPerformed
import javax.swing.filechooser.FileFilter
import javax.swing.SwingUtilities
import org.drooms.gui.swing.event.CoordinantsVisibilityChanged
import scala.swing.TextField

object DroomsSwingApp extends SimpleSwingApplication {
  val eventPublisher = DroomsEventPublisher.get()
  val leftPane = new LeftPane
  val rightPane = new RightPane
  var gameController: GameController = _
  var gameReport: (GameReport, File) = _
  var turnDelay = 100

  def top = new MainFrame {
    title = "Drooms"
    minimumSize = new Dimension(1300, 700)
    menuBar = new MainMenu()
    listenTo(eventPublisher)

    contents = new SplitPane(Orientation.Vertical, leftPane, rightPane) {
      resizeWeight = 1.0
      rightComponent.minimumSize = new Dimension(200, 500)
      leftComponent.minimumSize = new Dimension(500, 500)
    }
    var timer: Option[Timer] = None

    reactions += {
      case NewGameReportChosen(report, file) =>
        gameReport = (report, file)
        gameController = new ReplayGameController(report)
      case NextTurnInitiated() =>
        val turn = gameController.nextTurn
        for (step <- turn.steps) {
          eventPublisher.publish(new TurnStepPerformed(step))
        }
        if (!gameController.hasNextTurn()) {
          eventPublisher.publish(new GameFinished)
        }
      case GameRestarted() =>
        eventPublisher.publish(new NewGameReportChosen(gameReport._1, gameReport._2))
      case ReplayInitiated() | ReplayContinued() =>
        timer match {
          case Some(x) =>
            x.cancel()
          case None =>
        }
        timer = Some(new Timer())
        timer.get.schedule(new ScheduleNextTurn(), 0, turnDelay)
      case ReplayPaused() =>
        timer.get.cancel()
        timer = None
      case TurnDelayChanged(value) =>
        turnDelay = value
        timer match {
          // currently running replay;; update timer to new delay
          case Some(x) =>
            x.cancel()
            timer = Some(new Timer())
            timer.get.schedule(new ScheduleNextTurn(), turnDelay, turnDelay)
          case None =>
        }
      case GameFinished() =>
        timer match {
          case Some(x) => x.cancel()
          case None =>
        }
        timer = None
    }

    class ScheduleNextTurn extends TimerTask {
      def run(): Unit = {
        if (gameController.hasNextTurn()) {
          SwingUtilities.invokeAndWait(new Runnable() {
            override def run(): Unit = {
              eventPublisher.publish(NextTurnInitiated())
            }
          })
        }
      }
    }
    centerOnScreen()
  }
}

class LeftPane extends BorderPanel {
  val playground = new Playground
  val controlPanel = new ControlPanel

  layout(playground) = BorderPanel.Position.Center
  layout(controlPanel) = BorderPanel.Position.South
}

class RightPane extends BoxPanel(Orientation.Horizontal) with Reactor {
  val playersListView = new PlayersListView
  contents += playersListView
}

trait GameStatus
case class GameNotStarted extends GameStatus
case class GameReplaying extends GameStatus
case class GameReplayingPaused extends GameStatus
