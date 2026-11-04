package net.retromc.skywars.event;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Kit-Fähigkeit wird benutzt (abbrechbar, Spec 04 §8.2); {@code usesLeft} nach dieser Nutzung (−1 = unbegrenzt). */
public final class KitAbilityUseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String kitId;
    private final int usesLeft;
    private boolean cancelled;

    public KitAbilityUseEvent(Player player, String kitId, int usesLeft) {
        this.player = Objects.requireNonNull(player, "player");
        this.kitId = Objects.requireNonNull(kitId, "kitId");
        this.usesLeft = usesLeft;
    }

    public Player player() {
        return player;
    }

    public String kitId() {
        return kitId;
    }

    public int usesLeft() {
        return usesLeft;
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
