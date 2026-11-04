package net.retromc.bedwars;

import java.util.Optional;
import net.retromc.bedwars.shop.ShopGui;
import net.retromc.bedwars.upgrade.UpgradeGui;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.event.PlayerJoinGameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.block.Action;

/**
 * Bauregeln (Spec 04 §6.1.2 Nr. 6, §6.1.4 Nr. 1, §6.1.8): abbaubar sind nur von Spielern gesetzte Blöcke und
 * gegnerische Betten; keine Blöcke im Spawn-Raum (Radius 3) und über {@code buildMaxY}; Explosionen zerstören nur
 * gesetzte Blöcke außer Glas; Team-Truhe nur fürs eigene Team; Händler öffnen Shop und Upgrades.
 */
final class BuildListener implements Listener {

    private final BedWarsMode mode;

    BuildListener(BedWarsMode mode) {
        this.mode = mode;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (mode.game().state() != GameState.INGAME) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (Tag.BEDS.isTagged(block.getType())) {
            event.setCancelled(true);
            for (BwTeam team : mode.bwTeams()) {
                if (!team.isBed(block) || !team.bedAlive()) {
                    continue;
                }
                if (team.team().contains(player.getUniqueId())) {
                    mode.texts().send(player, "bw.bed.own");
                } else if (mode.playing(player)) {
                    mode.destroyBed(player, team, block.getLocation());
                }
                return;
            }
            return;
        }
        if (!mode.placed(block)) {
            event.setCancelled(true);
            return;
        }
        mode.markPlaced(block, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (mode.game().state() != GameState.INGAME) {
            return;
        }
        Block block = event.getBlockPlaced();
        Location location = block.getLocation().add(0.5, 0, 0.5);
        if (block.getY() > mode.map().buildMaxY() || !mode.game().maps().inBounds(location)) {
            event.setCancelled(true);
            return;
        }
        int radius = mode.config().spawnProtection();
        for (BwTeam team : mode.bwTeams()) {
            Location spawn = team.spawn();
            if (spawn.getWorld() == location.getWorld() && Math.abs(spawn.getX() - location.getX()) <= radius + 0.5
                    && Math.abs(spawn.getZ() - location.getZ()) <= radius + 0.5
                    && Math.abs(spawn.getY() - location.getY()) <= radius + 1) {
                event.setCancelled(true);
                return;
            }
        }
        mode.markPlaced(block, true);
    }

    /** Explosionen: nur gesetzte Blöcke, kein Glas („Explosionsschutz“), keine Betten. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedFromExplosion);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedFromExplosion);
    }

    private boolean protectedFromExplosion(Block block) {
        if (!mode.placed(block) || block.getType().name().endsWith("_STAINED_GLASS") || Tag.BEDS.isTagged(
                block.getType())) {
            return true;
        }
        mode.markPlaced(block, false);
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        event.setCancelled(true);
    }

    /** Team-Truhe (Spec 04 §5.6): eine Instanz je Team, nur Mitglieder. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || mode.game().state() != GameState.INGAME) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return;
        }
        for (BwTeam team : mode.bwTeams()) {
            if (team.isChest(block)) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                if (!mode.playing(player)) {
                    return;
                }
                if (team.team().contains(player.getUniqueId())) {
                    player.openInventory(team.chest());
                } else {
                    mode.texts().send(player, "bw.chest.foreign");
                }
                return;
            }
        }
    }

    /** Händler: Item-Shop bzw. Upgrades (Spec 04 §4.10); Shift-Rechtsklick am Händler = Vorschau. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntity(PlayerInteractEntityEvent event) {
        Optional<String> kind = NpcSpawner.kind(event.getRightClicked());
        if (kind.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!mode.playing(player)) {
            return;
        }
        if (kind.get().equals(NpcSpawner.UPGRADE)) {
            UpgradeGui.open(mode, player);
        } else {
            ShopGui.open(mode, player, player.isSneaking() ? ShopGui.Mode.PREVIEW : ShopGui.Mode.BUY);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageEvent event) {
        if (NpcSpawner.kind(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Aufgehobene Schwerter/Werkzeuge/Rüstung bekommen die Team-Upgrades. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && mode.game().state() == GameState.INGAME) {
            String name = event.getItem().getItemStack().getType().name();
            if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")) {
                org.bukkit.Bukkit.getScheduler().runTask(mode.plugin(), () -> mode.shop().applyUpgrades(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        mode.shop().load(event.getPlayer().getUniqueId());
        mode.goldVote().join(event.getPlayer());
    }

    /** Späte Zuschauer sehen das Ingame-Board. */
    @EventHandler
    public void onGameJoin(PlayerJoinGameEvent event) {
        if (event.asSpectator() && mode.game().state() == GameState.INGAME) {
            mode.board().show(event.getPlayer());
        }
    }
}
