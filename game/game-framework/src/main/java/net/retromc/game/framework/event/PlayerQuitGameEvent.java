package net.retromc.game.framework.event;

import java.util.Objects;
import net.retromc.game.framework.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Spieler verlässt den Spielserver. */
public final class PlayerQuitGameEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final GameState phase;

    public PlayerQuitGameEvent(Player player, GameState phase) {
        this.player = Objects.requireNonNull(player, "player");
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    public Player player() {
        return player;
    }

    public GameState phase() {
        return phase;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
