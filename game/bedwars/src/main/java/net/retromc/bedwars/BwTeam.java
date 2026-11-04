package net.retromc.bedwars;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.retromc.bedwars.trap.TrapQueue;
import net.retromc.bedwars.upgrade.TeamUpgrades;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * BedWars-Stand eines Teams: Bett, Spawn, Händler, Team-Truhe (eine geteilte {@code Inventory}-Instanz, Spec 04
 * §5.6), Upgrades und Fallen.
 */
public final class BwTeam {

    private final GameTeam team;
    private final Location spawn;
    private final @Nullable Location bedHead;
    private final @Nullable Location bedFoot;
    private final @Nullable Location chestBlock;
    private final Inventory chest;
    private final TeamUpgrades upgrades = new TeamUpgrades();
    private final TrapQueue traps = new TrapQueue();
    private volatile boolean bed;

    public BwTeam(GameTeam team, Location spawn, @Nullable Location bedHead, @Nullable Location bedFoot,
            @Nullable Location chestBlock, Component chestTitle) {
        this.team = Objects.requireNonNull(team, "team");
        this.spawn = Objects.requireNonNull(spawn, "spawn");
        this.bedHead = bedHead;
        this.bedFoot = bedFoot;
        this.chestBlock = chestBlock;
        this.chest = Bukkit.createInventory(null, 27, chestTitle);
        this.bed = bedHead != null;
    }

    public GameTeam team() {
        return team;
    }

    public Location spawn() {
        return spawn.clone();
    }

    /** Mitte der Basis (Bett, sonst Spawn) für Fallen und Basis-Effekte. */
    public Location base() {
        return bedHead != null ? bedHead.clone().add(0.5, 0, 0.5) : spawn.clone();
    }

    public boolean bedAlive() {
        return bed;
    }

    public void destroyBed() {
        bed = false;
    }

    /** Ob {@code block} zum Bett dieses Teams gehört. */
    public boolean isBed(Block block) {
        return same(block, bedHead) || same(block, bedFoot);
    }

    public @Nullable Location bedHead() {
        return bedHead;
    }

    public @Nullable Location bedFoot() {
        return bedFoot;
    }

    public boolean isChest(Block block) {
        return same(block, chestBlock);
    }

    public Inventory chest() {
        return chest;
    }

    public TeamUpgrades upgrades() {
        return upgrades;
    }

    public TrapQueue traps() {
        return traps;
    }

    private static boolean same(Block block, @Nullable Location location) {
        return location != null && block.getWorld().equals(location.getWorld())
                && block.getX() == location.getBlockX() && block.getY() == location.getBlockY()
                && block.getZ() == location.getBlockZ();
    }
}
