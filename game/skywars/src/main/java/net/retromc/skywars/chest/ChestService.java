package net.retromc.skywars.chest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import net.retromc.game.framework.loot.LootItems;
import net.retromc.game.framework.loot.LootStack;
import net.retromc.game.framework.loot.LootTable;
import net.retromc.skywars.map.SkyWarsMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Füllt die Loot-Container der Map (Spec 04 §6.2.4, §5.13: Vanilla-Truhe/-Fass): beim Start Inseln mit Tier 1 und
 * Garantien je Insel, Mitte mit Tier 2; beim Chestrefill alle Container neu aus der Refill-Stufe.
 */
public final class ChestService {

    /** Ein Loot-Container: Insel-Index oder −1 für die Mitte. */
    public record Spot(int island, SkyWarsMap.Block block) {
    }

    private final LootTables tables;
    private final Random random;
    private final Logger log;
    private final Map<Long, Spot> spots = new HashMap<>();
    private @Nullable World world;

    public ChestService(LootTables tables, Random random, Logger log) {
        this.tables = Objects.requireNonNull(tables, "tables");
        this.random = Objects.requireNonNull(random, "random");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Blockposition als Zahl (x, z je 27 Bit, y 10 Bit). */
    public static long key(int x, int y, int z) {
        return ((long) x & 0x7FFFFFFL) | (((long) z & 0x7FFFFFFL) << 27) | (((long) y & 0x3FFL) << 54);
    }

    /** Ob ein Block ein Loot-Container der Map ist. */
    public boolean isLoot(Block block) {
        return block.getWorld().equals(world) && spots.containsKey(key(block.getX(), block.getY(), block.getZ()));
    }

    /** Container der Mitte? */
    public boolean isMid(Block block) {
        Spot spot = spots.get(key(block.getX(), block.getY(), block.getZ()));
        return spot != null && spot.island() < 0;
    }

    /** Erstfüllung (Rundenstart) für die genutzten Inseln ({@code islands} = Anzahl Teams). */
    public void fillStart(World target, SkyWarsMap map, int islands) {
        world = target;
        spots.clear();
        List<SkyWarsMap.Island> all = map.islands();
        for (int i = 0; i < all.size(); i++) {
            SkyWarsMap.Island island = all.get(i);
            island.chests().forEach(block -> spots.put(key(block.x(), block.y(), block.z()), new Spot(-2, block)));
            if (i >= islands) {
                continue;
            }
            List<Map<Integer, LootStack>> contents = tables.fillIsland(island.chests().size(), random);
            for (int c = 0; c < island.chests().size(); c++) {
                SkyWarsMap.Block block = island.chests().get(c);
                spots.put(key(block.x(), block.y(), block.z()), new Spot(i, block));
                fill(block, contents.get(c));
            }
        }
        for (SkyWarsMap.Block block : map.mid().chests()) {
            spots.put(key(block.x(), block.y(), block.z()), new Spot(-1, block));
            fill(block, tables.fillMid(random));
        }
    }

    /** Chestrefill: alle Container (genutzte Inseln und Mitte) neu füllen. */
    public int refill(int stage) {
        LootTables.Refill refill = tables.refill(stage);
        int filled = 0;
        for (Spot spot : new ArrayList<>(spots.values())) {
            if (spot.island() == -2) {
                continue;
            }
            LootTable table = spot.island() < 0 ? refill.mid() : refill.islands();
            fill(spot.block(), table.roll(random));
            filled++;
        }
        return filled;
    }

    private void fill(SkyWarsMap.Block block, Map<Integer, LootStack> content) {
        World target = world;
        if (target == null) {
            return;
        }
        BlockState state = target.getBlockAt(block.x(), block.y(), block.z()).getState();
        Inventory inventory = state instanceof Chest chest ? chest.getBlockInventory()
                : state instanceof Container container ? container.getInventory() : null;
        if (inventory == null) {
            log.warn("Loot-Container {} {} {} ist keine Truhe/kein Fass ({})", block.x(), block.y(), block.z(),
                    state.getType());
            return;
        }
        try {
            LootItems.apply(inventory, content);
        } catch (IllegalArgumentException e) {
            log.warn("Loot nicht gesetzt: {}", e.getMessage());
        }
    }
}
