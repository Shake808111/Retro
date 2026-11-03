package net.retromc.game.framework.event;

import java.util.Objects;
import java.util.UUID;
import net.retromc.game.framework.end.GameResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Rundenende mit Ergebnis (Spec 03 §6.10, ersetzt {@code GameWinEvent}). */
public final class GameEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID matchId;
    private final GameResult result;

    public GameEndEvent(UUID matchId, GameResult result) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.result = Objects.requireNonNull(result, "result");
    }

    public UUID matchId() {
        return matchId;
    }

    public GameResult result() {
        return result;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
