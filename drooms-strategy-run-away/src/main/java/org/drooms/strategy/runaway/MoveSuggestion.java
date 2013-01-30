package org.drooms.strategy.runaway;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.drooms.api.Move;

/**
 * Player move suggestions including rating of that move.
 */
public class MoveSuggestion implements Serializable {
    
    private final Move move;

    private final AtomicInteger rating;

    private final AtomicInteger turn;
    
    private final String origin;
    
    public MoveSuggestion(final String origin, final Move move, final int turn) {
        this.move = move;
        this.origin = origin;
        this.rating = new AtomicInteger();
        this.turn = new AtomicInteger(turn);
    }

    public Move getMove() {
        return this.move;
    }

    public String getOrigin() {
        return this.origin;
    }
    
    public int getTurn() {
        return this.turn.intValue();
    }

    public int getRating() {
        return this.rating.intValue();
    }

    public void addRating(final int delta) {
        this.rating.addAndGet(delta);
    }
    
    public void reset() {
        this.rating.set(0);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.move != null ? this.move.hashCode() : 0);
        hash = 79 * hash + Objects.hashCode(this.rating);
        hash = 79 * hash + Objects.hashCode(this.turn);
        hash = 79 * hash + Objects.hashCode(this.origin);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MoveSuggestion other = (MoveSuggestion) obj;
        if (this.move != other.move) {
            return false;
        }
        if (!Objects.equals(this.rating, other.rating)) {
            return false;
        }
        if (!Objects.equals(this.turn, other.turn)) {
            return false;
        }
        if (!Objects.equals(this.origin, other.origin)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "MoveSuggestion{" + "move=" + move + ", origin=" + origin + ", rating=" + rating + '}';
    }
}
