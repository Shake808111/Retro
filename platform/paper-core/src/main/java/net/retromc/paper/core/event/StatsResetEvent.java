package net.retromc.paper.core.event;

import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.ResetScope;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Statistiken eines Modus gelöscht (Spec 02 §6.9, §8.2); Haupt-Thread. */
public final class StatsResetEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final ModeKey mode;
    private final ResetScope scope;

    public StatsResetEvent(UUID player, ModeKey mode, ResetScope scope) {
        this.player = Objects.requireNonNull(player, "player");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    /** Spieler. */
    public UUID player() {
        return player;
    }

    /** Modus. */
    public ModeKey mode() {
        return mode;
    }

    /** Art des Resets. */
    public ResetScope scope() {
        return scope;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
