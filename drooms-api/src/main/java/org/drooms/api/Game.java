package org.drooms.api;

import java.util.Properties;

/**
 * Represents a certain type of game, with its own rules and constraints.
 */
public interface Game {

    /**
     * Initialize the game and play it through.
     * 
     * @param id
     *            Unique identifier of the game, use any arbitrary string.
     * @param config
     *            Game configuration.
     * @param players
     *            Configuration of players that enter the game.
     * @return A complete account of the game's progress.
     */
    public GameProgressListener play(String id, Properties config,
            Properties players);

}