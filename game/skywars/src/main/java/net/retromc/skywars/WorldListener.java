package net.retromc.skywars;

import java.util.ArrayList;
import java.util.List;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.event.PlayerJoinGameEvent;
import net.retromc.skywars.challenge.ChallengeTracker;
import net.retromc.skywars.chest.ChestService;
import net.retromc.skywars.kit.KitSettingsGui;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

/**
 * Welt in SkyWars (Spec 04 §6.2.2, §6.2.3): alle Blöcke innerhalb der Map abbaubar (außer Käfigen), keine Blöcke
 * außerhalb der Grenzen; zählt platzierte Blöcke und geöffnete Loot-Container (Rundenblock, Challenge); lädt Kits und
 * Level beim Betreten der Wartelobby; Klick auf das Hologramm „Kit-Einstellungen“ öffnet GUI 5.10.
 */
final class WorldListener implements Listener {

    private final SkyWarsMode mode;

    WorldListener(SkyWarsMode mode) {
        this.mode = mode;
    }

    @EventHandler
    public void onJoinGame(PlayerJoinGameEvent event) {
        if (!event.asSpectator()) {
            mode.loadPlayer(event.player());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        mode.forgetPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (mode.game().state() != GameState.INGAME || !mode.playing(player)) {
            return;
        }
        if (mode.caged() || !mode.game().maps().inBounds(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        mode.sw(player.getUniqueId()).blockPlaced();
        mode.game().player(player.getUniqueId()).ifPresent(gamePlayer -> gamePlayer.addExtra("blocks_placed", 1));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (mode.game().state() != GameState.INGAME) {
            return;
        }
        Block block = event.getBlock();
        if (mode.caged() || mode.cageBlock(block) || !mode.game().maps().inBounds(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !mode.playing(player)) {
            return;
        }
        for (Block block : blocks(event.getInventory().getHolder())) {
            if (!mode.chests().isLoot(block)) {
                continue;
            }
            if (mode.sw(player.getUniqueId()).openChest(ChestService.key(block.getX(), block.getY(), block.getZ()))) {
                mode.game().player(player.getUniqueId()).ifPresent(gamePlayer -> gamePlayer.addExtra("chests_opened",
                        1));
                ChallengeTracker tracker = mode.sw(player.getUniqueId()).challenge();
                if (tracker != null) {
                    mode.challenges().report(player, tracker.chestOpened());
                }
            }
            return;
        }
    }

    private static List<Block> blocks(@org.jspecify.annotations.Nullable InventoryHolder holder) {
        List<Block> blocks = new ArrayList<>();
        if (holder instanceof BlockState state) {
            blocks.add(state.getBlock());
        } else if (holder instanceof DoubleChest chest) {
            for (InventoryHolder side : new InventoryHolder[] {chest.getLeftSide(), chest.getRightSide()}) {
                if (side instanceof BlockState state) {
                    blocks.add(state.getBlock());
                }
            }
        }
        return blocks;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !mode.kitHologram(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (mode.game().lifecycle().waiting()) {
            KitSettingsGui.open(mode.game(), mode.kits(), event.getPlayer());
        }
    }
}
