package net.retromc.bedwars.event;

import java.util.Objects;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Bett eines Teams wird abgebaut (abbrechbar, Spec 04 §8.2). */
public final class BedDestroyEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameTeam team;
    private final Player destroyer;
    private final Location bed;
    private boolean cancelled;

    public BedDestroyEvent(GameTeam team, Player destroyer, Location bed) {
        this.team = Objects.requireNonNull(team, "team");
        this.destroyer = Objects.requireNonNull(destroyer, "destroyer");
        this.bed = Objects.requireNonNull(bed, "bed");
    }

    public GameTeam team() {
        return team;
    }

    public Player destroyer() {
        return destroyer;
    }

    public Location bed() {
        return bed;
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
