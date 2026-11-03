package net.retromc.game.framework.event;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Spieler betritt den Spielserver (Wartelobby oder als Zuschauer). */
public final class PlayerJoinGameEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final boolean asSpectator;

    public PlayerJoinGameEvent(Player player, boolean asSpectator) {
        this.player = Objects.requireNonNull(player, "player");
        this.asSpectator = asSpectator;
    }

    public Player player() {
        return player;
    }

    public boolean asSpectator() {
        return asSpectator;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
