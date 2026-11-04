package net.retromc.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.retromc.skywars.kit.AbilityTracker;
import net.retromc.skywars.kit.AbilityType;
import net.retromc.skywars.kit.KitCatalog;
import net.retromc.skywars.kit.KitDef;
import net.retromc.skywars.kit.KitItem;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Spec 04 §9 #14 (Kit-Fähigkeit in der Schutzzeit gesperrt, Nutzungen und Abklingzeit, Teufel 30 s) und der
 * Kit-Katalog §6.2.5 (27 aktive Kits mit Preisen, frühere Kits, Classic nur Item-Kits).
 */
class KitTest {

    @Test
    void katalogNachSpec() throws IOException {
        KitCatalog catalog = Resources.kits();
        List<KitDef> active = catalog.available(false, false);
        assertEquals(27, active.size(), "27 aktive Kits = Slots 9–35");
        assertEquals(List.of("starter", "engel", "teufel", "jaeger", "phantom", "ziege", "elytra", "panzer", "ziegel",
                "stampfer", "ninja", "poseidon", "wolke", "hundefluesterer", "luftbaendiger", "nachtmagier", "hexe",
                "goldsucher", "bienen", "grillmeister", "zauberer", "crafter", "assassine", "creeper", "enderman",
                "angler", "miner"), active.stream().map(KitDef::id).toList(), "Katalogreihenfolge §6.2.5");
        Map<String, Long> prices = Map.ofEntries(Map.entry("starter", 0L), Map.entry("engel", 25_000L),
                Map.entry("teufel", 60_000L), Map.entry("jaeger", 40_000L), Map.entry("phantom", 35_000L),
                Map.entry("ziege", 30_000L), Map.entry("elytra", 50_000L), Map.entry("panzer", 80_000L),
                Map.entry("ziegel", 15_000L), Map.entry("stampfer", 45_000L), Map.entry("ninja", 40_000L),
                Map.entry("poseidon", 60_000L), Map.entry("wolke", 30_000L), Map.entry("hundefluesterer", 40_000L),
                Map.entry("luftbaendiger", 45_000L), Map.entry("nachtmagier", 45_000L), Map.entry("hexe", 35_000L),
                Map.entry("goldsucher", 20_000L), Map.entry("bienen", 30_000L), Map.entry("grillmeister", 18_000L),
                Map.entry("zauberer", 35_000L), Map.entry("crafter", 18_000L), Map.entry("assassine", 75_000L),
                Map.entry("creeper", 75_000L), Map.entry("enderman", 50_000L), Map.entry("angler", 20_000L),
                Map.entry("miner", 25_000L), Map.entry("mushroom", 100_000L), Map.entry("maurer", 5_000L),
                Map.entry("pyro", 30_000L));
        prices.forEach((id, price) -> assertEquals(price, catalog.byId(id).orElseThrow().price(), id));
        assertEquals(List.of("pirat", "mushroom", "maurer", "pyro", "bandit"), catalog.kits().stream()
                .filter(KitDef::legacy).map(KitDef::id).toList(), "frühere Kits");
        assertEquals(32, catalog.available(false, true).size(), "mit „Frühere Kits“");
        List<KitDef> classic = catalog.available(true, false);
        assertTrue(classic.stream().noneMatch(KitDef::activeAbility), "Classic ohne Fähigkeiten-Kits");
        assertEquals(List.of("starter", "grillmeister", "zauberer", "crafter", "assassine", "creeper", "enderman",
                "angler", "miner", "pirat", "mushroom", "maurer", "pyro", "bandit"), classic.stream()
                .map(KitDef::id).toList(), "Item-Kits 20–27 + frühere Kits");
        for (KitDef kit : catalog.kits()) {
            assertTrue(Material.matchMaterial(kit.icon()) != null, kit.id() + " Icon");
            for (KitItem item : kit.items()) {
                assertTrue(Material.matchMaterial(item.material()) != null, kit.id() + " " + item.material());
            }
            if (kit.ability() != null && kit.ability().item() != null) {
                assertTrue(Material.matchMaterial(kit.ability().item().material()) != null, kit.id());
            }
        }
    }

    @Test
    void engelUndTeufelWieBelegt() throws IOException {
        KitCatalog catalog = Resources.kits();
        KitDef angel = catalog.byId("engel").orElseThrow();
        assertEquals("TOTEM_OF_UNDYING", angel.items().getFirst().material());
        assertEquals("sw.kit.item.angel_totem", angel.items().getFirst().nameKey(), "Itemname §7Engeltotem");
        assertEquals(AbilityType.ANGEL, angel.ability().type());
        KitDef devil = catalog.byId("teufel").orElseThrow();
        assertEquals(AbilityType.DEVIL, devil.ability().type());
        assertEquals(3, devil.ability().uses());
        assertEquals(30, devil.ability().cooldown(), "Teufel-Cooldown 30 s");
        assertEquals(6, catalog.byId("phantom").orElseThrow().ability().uses(), "[6 Nutzungen]");
        assertEquals(7, catalog.byId("ziege").orElseThrow().ability().uses(), "7 Nutzungen");
        assertEquals(10, catalog.byId("panzer").orElseThrow().items().getFirst().durability(), "Haltbarkeit 10");
    }

    @Test
    void faehigkeitGesperrtNutzungenUndAbklingzeit() throws IOException {
        KitDef devil = Resources.kits().byId("teufel").orElseThrow();
        AbilityTracker tracker = new AbilityTracker(devil.ability().uses(), devil.ability().cooldown());
        long now = 1_000_000;
        assertEquals(AbilityTracker.Result.PROTECTION, tracker.use(now, true).result(), "Schutzzeit sperrt");
        assertEquals(3, tracker.usesLeft(), "gesperrter Versuch kostet nichts");
        AbilityTracker.Use first = tracker.use(now, false);
        assertEquals(AbilityTracker.Result.OK, first.result());
        assertEquals(2, first.usesLeft());
        assertEquals(30, tracker.cooldown(now), "Nutzbar in 30 Sekunden");
        AbilityTracker.Use blocked = tracker.use(now + 29_001, false);
        assertEquals(AbilityTracker.Result.COOLDOWN, blocked.result());
        assertEquals(1, blocked.cooldown(), "Nutzbar in 1 Sekunde");
        assertFalse(tracker.becameReady(now + 29_001));
        assertTrue(tracker.becameReady(now + 30_000), "Ab jetzt nutzbar");
        assertFalse(tracker.becameReady(now + 31_000), "nur einmal");
        assertEquals(AbilityTracker.Result.OK, tracker.use(now + 30_000, false).result());
        assertEquals(AbilityTracker.Result.OK, tracker.use(now + 60_000, false).result());
        assertEquals(0, tracker.usesLeft());
        assertFalse(tracker.becameReady(now + 90_000), "keine Nutzung mehr übrig");
        assertEquals(AbilityTracker.Result.EMPTY, tracker.use(now + 90_000, false).result());
        AbilityTracker unlimited = new AbilityTracker(0, 15);
        assertEquals(-1, unlimited.use(now, false).usesLeft(), "Stampfer: unbegrenzt, 15 s Abklingzeit");
        assertEquals(AbilityTracker.Result.COOLDOWN, unlimited.use(now + 14_000, false).result());
    }
}
