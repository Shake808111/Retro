package net.retromc.bedwars.event;

import java.util.Map;
import java.util.Objects;
import net.retromc.bedwars.shop.ShopItem;
import net.retromc.bedwars.spawner.Resource;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Kauf im Item-Shop (abbrechbar). */
public final class ShopPurchaseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ShopItem item;
    private final Map<Resource, Integer> price;
    private boolean cancelled;

    public ShopPurchaseEvent(Player player, ShopItem item, Map<Resource, Integer> price) {
        this.player = Objects.requireNonNull(player, "player");
        this.item = Objects.requireNonNull(item, "item");
        this.price = Map.copyOf(price);
    }

    public Player player() {
        return player;
    }

    public ShopItem item() {
        return item;
    }

    public Map<Resource, Integer> price() {
        return price;
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
