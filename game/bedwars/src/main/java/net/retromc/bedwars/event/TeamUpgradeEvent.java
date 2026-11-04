package net.retromc.bedwars.event;

import java.util.Objects;
import net.retromc.bedwars.upgrade.UpgradeType;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Team-Upgrade gekauft. */
public final class TeamUpgradeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameTeam team;
    private final UpgradeType upgrade;
    private final int level;
    private final Player buyer;

    public TeamUpgradeEvent(GameTeam team, UpgradeType upgrade, int level, Player buyer) {
        this.team = Objects.requireNonNull(team, "team");
        this.upgrade = Objects.requireNonNull(upgrade, "upgrade");
        this.level = level;
        this.buyer = Objects.requireNonNull(buyer, "buyer");
    }

    public GameTeam team() {
        return team;
    }

    public UpgradeType upgrade() {
        return upgrade;
    }

    public int level() {
        return level;
    }

    public Player buyer() {
        return buyer;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
