package net.retromc.game.framework.event;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Tod eines Spielers mit oder ohne Killer (Kill-Feed, Spec 03 §6.9). */
public final class GamePlayerKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player victim;
    private final @Nullable Player killer;
    private final String mode;

    public GamePlayerKillEvent(Player victim, @Nullable Player killer, String mode) {
        this.victim = Objects.requireNonNull(victim, "victim");
        this.killer = killer;
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public Player victim() {
        return victim;
    }

    public @Nullable Player killer() {
        return killer;
    }

    public String mode() {
        return mode;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
