package net.retromc.bedwars.event;

import java.util.Objects;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.Nullable;

/** Tod ohne Bett (Final Kill). */
public final class FinalKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player victim;
    private final @Nullable Player killer;
    private final GameTeam team;

    public FinalKillEvent(Player victim, @Nullable Player killer, GameTeam team) {
        this.victim = Objects.requireNonNull(victim, "victim");
        this.killer = killer;
        this.team = Objects.requireNonNull(team, "team");
    }

    public Player victim() {
        return victim;
    }

    public @Nullable Player killer() {
        return killer;
    }

    public GameTeam team() {
        return team;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
