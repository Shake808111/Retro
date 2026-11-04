package net.retromc.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import net.retromc.game.framework.loot.LootStack;
import net.retromc.game.framework.loot.LootTable;
import net.retromc.skywars.chest.LootTables;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Spec 04 §9 #12 (Mitte-Truhen enthalten Schneebälle/Eier, Inseltruhen nie; 1.000 Füllungen), U7 (Garantien je Insel
 * über 10.000 Seeds) und die Tabellen des Chestrefills (#16).
 */
class LootTest {

    private static final Set<String> MID_ONLY = Set.of("SNOWBALL", "EGG");
    private final LootTables tables = Resources.loot();

    private static Stream<LootStack> stacks(Collection<Map<Integer, LootStack>> containers) {
        return containers.stream().flatMap(content -> content.values().stream());
    }

    @Test
    void schneebaelleUndEierNurInDerMitte() {
        Random random = new Random(42);
        boolean midHasThem = false;
        for (int fill = 0; fill < 1_000; fill++) {
            List<Map<Integer, LootStack>> island = tables.fillIsland(3, random);
            assertTrue(stacks(island).noneMatch(stack -> MID_ONLY.contains(stack.material())), "Inseltruhe " + fill);
            for (int stage = 1; stage <= 2; stage++) {
                Map<Integer, LootStack> refill = tables.refill(stage).islands().roll(random);
                assertTrue(refill.values().stream().noneMatch(stack -> MID_ONLY.contains(stack.material())),
                        "Insel-Refill Stufe " + stage);
            }
            Map<Integer, LootStack> mid = tables.fillMid(random);
            midHasThem |= mid.values().stream().anyMatch(stack -> MID_ONLY.contains(stack.material()));
        }
        assertTrue(midHasThem, "Mitte-Truhen enthalten Schneebälle/Eier");
        assertFalse(tables.tier1().possibleMaterials().contains("SNOWBALL"));
        assertTrue(tables.tier2().possibleMaterials().containsAll(MID_ONLY));
        assertTrue(tables.refill(2).mid().possibleMaterials().containsAll(MID_ONLY));
    }

    @Test
    void garantienJeInsel() {
        for (long seed = 0; seed < 10_000; seed++) {
            Random random = new Random(seed);
            int containers = 2 + (int) (seed % 2);
            List<Map<Integer, LootStack>> island = tables.fillIsland(containers, random);
            assertEquals(containers, island.size());
            String where = "Seed " + seed;
            assertTrue(stacks(island).anyMatch(stack -> stack.category().equals("sword")
                    && !stack.material().equals("WOODEN_SWORD")), where + ": Schwert ≥ Stein");
            assertTrue(stacks(island).anyMatch(stack -> stack.category().equals("armor")), where + ": Rüstung");
            assertTrue(stacks(island).anyMatch(stack -> stack.category().equals("blocks") && stack.amount() >= 16),
                    where + ": Block-Stack ≥ 16");
            assertTrue(stacks(island).anyMatch(stack -> stack.category().equals("food")), where + ": Essen");
        }
    }

    @Test
    void stapelzahlUndSlots() {
        Random random = new Random(7);
        for (int fill = 0; fill < 1_000; fill++) {
            Map<Integer, LootStack> island = tables.tier1().roll(random);
            assertTrue(island.size() >= 5 && island.size() <= 8, "T1 5–8 Stapel: " + island.size());
            Map<Integer, LootStack> mid = tables.fillMid(random);
            assertTrue(mid.size() >= 6 && mid.size() <= 9, "T2 6–9 Stapel: " + mid.size());
            Stream.concat(island.keySet().stream(), mid.keySet().stream()).forEach(slot ->
                    assertTrue(slot >= 0 && slot < LootTable.CONTAINER_SIZE));
        }
    }

    @Test
    void itempoolUndVerzauberungen() {
        Random random = new Random(3);
        boolean sharpness = false;
        boolean curse = false;
        for (int i = 0; i < 20_000; i++) {
            LootStack stack = tables.tier2().pick(random, entry -> entry.id().equals("diamond_sword")).roll(random);
            int level = stack.enchantments().getOrDefault("sharpness", 0);
            assertTrue(level >= 1 && level <= 2, "Diamantschwert Schärfe I–II");
            sharpness |= level == 2;
            curse |= stack.enchantments().containsKey("vanishing_curse");
        }
        assertTrue(sharpness && curse, "Schärfe II und Fluch des Verschwindens kommen vor");
        for (LootTable table : List.of(tables.tier1(), tables.tier2(), tables.refill(1).islands(),
                tables.refill(2).islands(), tables.refill(2).mid())) {
            table.possibleMaterials().forEach(material -> assertTrue(Material.matchMaterial(material) != null,
                    "Material " + material));
        }
    }

    @Test
    void refillStufen() {
        assertSame(tables.tier1(), tables.refill(1).islands(), "Stufe 1: Inseln Tier 1");
        assertSame(tables.tier2(), tables.refill(1).mid(), "Stufe 1: Mitte Tier 2");
        assertTrue(tables.refill(2).islands().possibleMaterials().contains("NETHERITE_SWORD"), "Stufe 2: Tier + 1");
        assertSame(tables.refill(2), tables.refill(5), "höhere Stufen nutzen die letzte");
    }
}
