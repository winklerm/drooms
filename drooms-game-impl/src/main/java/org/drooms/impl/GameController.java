package org.drooms.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.drooms.api.Collectible;
import org.drooms.api.Game;
import org.drooms.api.GameReport;
import org.drooms.api.Move;
import org.drooms.api.Node;
import org.drooms.api.Player;
import org.drooms.impl.logic.CommandDistributor;
import org.drooms.impl.logic.commands.AddCollectibleCommand;
import org.drooms.impl.logic.commands.CollectCollectibleCommand;
import org.drooms.impl.logic.commands.Command;
import org.drooms.impl.logic.commands.CrashPlayerCommand;
import org.drooms.impl.logic.commands.DeactivatePlayerCommand;
import org.drooms.impl.logic.commands.MovePlayerCommand;
import org.drooms.impl.logic.commands.RemoveCollectibleCommand;
import org.drooms.impl.logic.commands.RewardSurvivalCommand;
import org.drooms.impl.util.PlayerAssembly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GameController implements Game {

    protected enum CollectibleType {

        CHEAP("cheap"), GOOD("good"), EXTREME("extreme");

        private static final String COMMON_PREFIX = "collectible.";
        private static final String PROBABILITY_PREFIX = CollectibleType.COMMON_PREFIX
                + "probability.";
        private static final String PRICE_PREFIX = CollectibleType.COMMON_PREFIX
                + "price.";
        private static final String EXPIRATION_PREFIX = CollectibleType.COMMON_PREFIX
                + "expiration.";
        private final String collectibleName;

        CollectibleType(final String propertyName) {
            this.collectibleName = propertyName;
        }

        public int getExpiration(final Properties config) {
            final String price = config.getProperty(
                    CollectibleType.EXPIRATION_PREFIX + this.collectibleName,
                    "1");
            return Integer.valueOf(price);
        }

        public int getPoints(final Properties config) {
            final String price = config.getProperty(
                    CollectibleType.PRICE_PREFIX + this.collectibleName, "1");
            return Integer.valueOf(price);
        }

        public BigDecimal getProbabilityOfAppearance(final Properties config) {
            final String probability = config.getProperty(
                    CollectibleType.PROBABILITY_PREFIX + this.collectibleName,
                    "0.1");
            return new BigDecimal(probability);
        }
    }

    private final File reportFolder;

    private static final Logger LOGGER = LoggerFactory
            .getLogger(GameController.class);

    protected static final SecureRandom RANDOM = new SecureRandom();

    private final String timestamp;

    private final Map<Player, Integer> playerPoints = new HashMap<Player, Integer>();

    private final Map<Player, Integer> lengths = new HashMap<Player, Integer>();

    private final Map<Player, Deque<Node>> positions = new HashMap<Player, Deque<Node>>();

    private final Map<Collectible, Node> nodesByCollectible = new HashMap<Collectible, Node>();

    private final Map<Node, Collectible> collectiblesByNode = new HashMap<Node, Collectible>();

    private final Map<Player, SortedMap<Integer, Move>> decisionRecord = new HashMap<Player, SortedMap<Integer, Move>>();

    protected GameController(final File reportFolder, final String timestamp) {
        this.reportFolder = new File(reportFolder, timestamp);
        this.timestamp = timestamp;
    }

    private void addCollectible(final Collectible c, final Node n) {
        this.collectiblesByNode.put(n, c);
        this.nodesByCollectible.put(c, n);
    }

    private void addDecision(final Player p, final Move m, final int turnNumber) {
        if (!this.decisionRecord.containsKey(p)) {
            this.decisionRecord.put(p, new TreeMap<Integer, Move>());
        }
        this.decisionRecord.get(p).put(turnNumber, m);
    }

    protected Collectible getCollectible(final Node n) {
        return this.collectiblesByNode.get(n);
    }

    protected Deque<Move> getDecisionRecord(final Player p) {
        final LinkedList<Move> moves = new LinkedList<Move>();
        for (final SortedMap.Entry<Integer, Move> entry : this.decisionRecord
                .get(p).entrySet()) {
            moves.add(entry.getKey(), entry.getValue());
        }
        return moves;
    }

    protected int getPlayerLength(final Player p) {
        if (!this.lengths.containsKey(p)) {
            throw new IllegalStateException(
                    "Player doesn't have any length assigned: " + p);
        }
        return this.lengths.get(p);
    }

    protected Deque<Node> getPlayerPosition(final Player p) {
        if (!this.positions.containsKey(p)) {
            throw new IllegalStateException(
                    "Player doesn't have any position assigned: " + p);
        }
        return this.positions.get(p);
    }

    public File getReportFolder() {
        return this.reportFolder;
    }

    protected abstract Map<Collectible, Player> performCollectibleCollection(
            final Collection<Player> players);

    protected abstract Map<Collectible, Node> performCollectibleDistribution(
            final Properties gameConfig, final DefaultPlayground playground,
            final Collection<Player> players, final int currentTurnNumber);

    protected abstract Set<Player> performCollisionDetection(
            final DefaultPlayground playground,
            final Collection<Player> currentPlayers);

    protected abstract Set<Player> performInactivityDetection(
            final Collection<Player> currentPlayers,
            final int currentTurnNumber, final int allowedInactiveTurns);

    protected abstract Deque<Node> performPlayerMove(final Player player,
            final Move decision);

    @Override
    public GameReport play(final Properties gameConfig,
            final Properties playerConfig) {
        // prepare the playground
        DefaultPlayground playground;
        try (final InputStream fis = new FileInputStream(new File(
                gameConfig.getProperty("playground.file")))) {
            playground = DefaultPlayground.read(fis);
        } catch (final IOException e) {
            throw new IllegalArgumentException(
                    "Playground file cannot be read!", e);
        }
        final int wormLength = Integer.valueOf(gameConfig.getProperty(
                "worm.length.start", "1"));
        final int allowedInactiveTurns = Integer.valueOf(gameConfig
                .getProperty("worm.max.inactive.turns", "3"));
        final int allowedTurns = Integer.valueOf(gameConfig.getProperty(
                "worm.max.turns", "1000"));
        final int wormSurvivalBonus = Integer.valueOf(gameConfig.getProperty(
                "worm.survival.bonus", "1"));
        final int wormTimeout = Integer.valueOf(gameConfig.getProperty(
                "worm.timeout.seconds", "1"));
        // prepare players and their starting positions
        final List<Player> players = new PlayerAssembly(playerConfig)
                .assemblePlayers();
        final List<Node> startingPositions = playground.getStartingPositions();
        final int playersSupported = startingPositions.size();
        final int playersAvailable = players.size();
        if (playersSupported < playersAvailable) {
            throw new IllegalArgumentException(
                    "The playground doesn't support " + playersAvailable
                            + " players, only " + playersSupported + "! ");
        }
        int i = 0;
        for (final Player player : players) {
            final Deque<Node> pos = new LinkedList<Node>();
            pos.push(startingPositions.get(i));
            this.setPlayerPosition(player, pos);
            this.setPlayerLength(player, wormLength);
            GameController.LOGGER.info("Player {} assigned position {}.",
                    player.getName(), i);
            i++;
        }
        // prepare situation
        final CommandDistributor playerControl = new CommandDistributor(
                playground, players, new XmlReport(playground, gameConfig,
                        this.timestamp), this.getReportFolder(), wormTimeout);
        final Set<Player> currentPlayers = new HashSet<Player>(players);
        Map<Player, Move> decisions = new HashMap<Player, Move>();
        for (final Player p : currentPlayers) { // initialize players
            decisions.put(p, Move.STAY);
        }
        // start the game
        int turnNumber = 0;
        do {
            GameController.LOGGER.info("--- Starting turn no. {}.", turnNumber);
            final List<Command> commands = new LinkedList<>();
            // remove inactive worms
            for (final Player player : this.performInactivityDetection(
                    currentPlayers, turnNumber, allowedInactiveTurns)) {
                currentPlayers.remove(player);
                commands.add(new DeactivatePlayerCommand(player));
            }
            // move the worms
            for (final Player p : currentPlayers) {
                final Move m = decisions.get(p);
                this.addDecision(p, m, turnNumber);
                final Deque<Node> newPosition = this.performPlayerMove(p, m);
                this.setPlayerPosition(p, newPosition);
                commands.add(new MovePlayerCommand(p, m, newPosition));
            }
            // resolve worms colliding
            for (final Player player : this.performCollisionDetection(
                    playground, currentPlayers)) {
                currentPlayers.remove(player);
                commands.add(new CrashPlayerCommand(player));
            }
            if (turnNumber > 0) {
                // reward surviving worms; but not in the first round
                for (final Player p : currentPlayers) {
                    this.reward(p, 1);
                    commands.add(new RewardSurvivalCommand(p, wormSurvivalBonus));
                }
            }
            // expire uncollected collectibles
            final Set<Collectible> removeCollectibles = new HashSet<Collectible>();
            for (final Collectible c : this.collectiblesByNode.values()) {
                if (c.expires() && turnNumber >= c.expiresInTurn()) {
                    removeCollectibles.add(c);
                }
            }
            for (final Collectible c : removeCollectibles) {
                this.removeCollectible(c);
                commands.add(new RemoveCollectibleCommand(c));
            }
            // add points for collected collectibles
            for (final Map.Entry<Collectible, Player> entry : this
                    .performCollectibleCollection(currentPlayers).entrySet()) {
                final Collectible c = entry.getKey();
                final Player p = entry.getValue();
                this.reward(p, c.getPoints());
                this.removeCollectible(c);
                this.setPlayerLength(p, this.getPlayerLength(p) + 1);
                commands.add(new CollectCollectibleCommand(c, p));
            }
            // distribute new collectibles
            for (final Map.Entry<Collectible, Node> entry : this
                    .performCollectibleDistribution(gameConfig, playground,
                            currentPlayers, turnNumber).entrySet()) {
                final Collectible c = entry.getKey();
                final Node n = entry.getValue();
                this.addCollectible(c, n);
                commands.add(new AddCollectibleCommand(c, n));
            }
            // make the move decision
            decisions = playerControl.execute(commands);
            turnNumber++;
            if (turnNumber == allowedTurns) {
                GameController.LOGGER
                        .info("Reached a pre-defined limit of {} turns. Terminating game.",
                                allowedTurns);
                break;
            } else if (currentPlayers.size() < 2) {
                GameController.LOGGER
                        .info("There are no more players. Terminating game.");
                break;
            }
        } while (true);
        playerControl.terminate(); // clean up all the sessions
        // output player status
        GameController.LOGGER.info("--- Game over.");
        for (final Map.Entry<Player, Integer> entry : this.playerPoints
                .entrySet()) {
            GameController.LOGGER.info("Player {} earned {} points.", entry
                    .getKey().getName(), entry.getValue());
        }
        return playerControl.getReport();
    }

    private void removeCollectible(final Collectible c) {
        final Node n = this.nodesByCollectible.remove(c);
        this.collectiblesByNode.remove(n);
    }

    private void reward(final Player p, final int points) {
        if (!this.playerPoints.containsKey(p)) {
            this.playerPoints.put(p, 0);
        }
        this.playerPoints.put(p, this.playerPoints.get(p) + points);
    }

    private void setPlayerLength(final Player p, final int length) {
        this.lengths.put(p, length);
    }

    private void setPlayerPosition(final Player p, final Deque<Node> position) {
        this.positions.put(p, position);
    }

}
