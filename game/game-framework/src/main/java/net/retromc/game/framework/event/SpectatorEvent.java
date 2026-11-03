package net.retromc.game.framework.event;

import java.util.Objects;
import net.retromc.game.framework.spectate.SpectatorReason;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Spieler wird Zuschauer (Spec 03 §6.8). */
public final class SpectatorEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SpectatorReason reason;

    public SpectatorEvent(Player player, SpectatorReason reason) {
        this.player = Objects.requireNonNull(player, "player");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Player player() {
        return player;
    }

    public SpectatorReason reason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
