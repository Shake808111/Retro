package net.retromc.game.framework.event;

import java.util.Objects;
import net.retromc.game.framework.GameState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Phasenwechsel der Runde (Spec 03 §8.1). */
public final class GamePhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameState from;
    private final GameState to;

    public GamePhaseChangeEvent(GameState from, GameState to) {
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
    }

    public GameState from() {
        return from;
    }

    public GameState to() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
