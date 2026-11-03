package net.retromc.game.framework.event;

import java.util.Objects;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Teamwahl in der Wartelobby (abbrechbar). */
public final class TeamJoinEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final GameTeam team;
    private boolean cancelled;

    public TeamJoinEvent(Player player, GameTeam team) {
        this.player = Objects.requireNonNull(player, "player");
        this.team = Objects.requireNonNull(team, "team");
    }

    public Player player() {
        return player;
    }

    public GameTeam team() {
        return team;
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
