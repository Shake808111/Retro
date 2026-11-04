package net.retromc.game.framework.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Rundenstart nach dem Teleport (Spec 03 §6.5 Nr. 6). */
public final class GameStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID matchId;
    private final GameMap map;
    private final List<GameTeam> teams;

    public GameStartEvent(UUID matchId, GameMap map, List<GameTeam> teams) {
        this.matchId = Objects.requireNonNull(matchId, "matchId");
        this.map = Objects.requireNonNull(map, "map");
        this.teams = Objects.requireNonNull(teams, "teams");
    }

    public UUID matchId() {
        return matchId;
    }

    public GameMap map() {
        return map;
    }

    public List<GameTeam> teams() {
        return teams;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
