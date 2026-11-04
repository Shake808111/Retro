package net.retromc.bedwars.event;

import java.util.Objects;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Letztes Mitglied eines Teams ist endgültig raus. */
public final class TeamEliminatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameTeam team;
    private final @Nullable Player lastKiller;

    public TeamEliminatedEvent(GameTeam team, @Nullable Player lastKiller) {
        this.team = Objects.requireNonNull(team, "team");
        this.lastKiller = lastKiller;
    }

    public GameTeam team() {
        return team;
    }

    public @Nullable Player lastKiller() {
        return lastKiller;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
