package net.retromc.game.framework.event;

import java.util.Objects;
import net.retromc.game.framework.map.GameMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** {@code /forcemap} vor dem Wechsel (abbrechbar). */
public final class ForcemapEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final GameMap map;
    private boolean cancelled;

    public ForcemapEvent(Player player, GameMap map) {
        this.player = Objects.requireNonNull(player, "player");
        this.map = Objects.requireNonNull(map, "map");
    }

    public Player player() {
        return player;
    }

    public GameMap map() {
        return map;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
