package net.retromc.game.framework.event;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Endgültiges Ausscheiden (Spieler wird Zuschauer). */
public final class PlayerEliminatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player victim;
    private final @Nullable Player killer;
    private final int remaining;

    public PlayerEliminatedEvent(Player victim, @Nullable Player killer, int remaining) {
        this.victim = Objects.requireNonNull(victim, "victim");
        this.killer = killer;
        this.remaining = remaining;
    }

    public Player victim() {
        return victim;
    }

    public @Nullable Player killer() {
        return killer;
    }

    public int remaining() {
        return remaining;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
