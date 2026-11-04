package net.retromc.bedwars.event;

import java.util.Objects;
import net.retromc.bedwars.trap.TrapType;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Falle wird ausgelöst (abbrechbar, z. B. Sneaker). */
public final class TrapTriggerEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameTeam owner;
    private final TrapType trap;
    private final Player intruder;
    private boolean cancelled;

    public TrapTriggerEvent(GameTeam owner, TrapType trap, Player intruder) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.trap = Objects.requireNonNull(trap, "trap");
        this.intruder = Objects.requireNonNull(intruder, "intruder");
    }

    public GameTeam owner() {
        return owner;
    }

    public TrapType trap() {
        return trap;
    }

    public Player intruder() {
        return intruder;
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
