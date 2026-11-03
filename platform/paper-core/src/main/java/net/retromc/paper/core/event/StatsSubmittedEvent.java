package net.retromc.paper.core.event;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.stats.MatchResult;
import net.retromc.core.api.stats.StatDelta;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Rundenergebnis verbucht, nach dem Commit (Spec 02 §6.4 Schritt 4, §8.2); Haupt-Thread. */
public final class StatsSubmittedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final MatchResult result;
    private final Map<UUID, StatDelta> deltas;

    public StatsSubmittedEvent(MatchResult result, Map<UUID, StatDelta> deltas) {
        this.result = Objects.requireNonNull(result, "result");
        this.deltas = Map.copyOf(deltas);
    }

    /** Rundenergebnis. */
    public MatchResult result() {
        return result;
    }

    /** Wirkung je Spieler. */
    public Map<UUID, StatDelta> deltas() {
        return deltas;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
