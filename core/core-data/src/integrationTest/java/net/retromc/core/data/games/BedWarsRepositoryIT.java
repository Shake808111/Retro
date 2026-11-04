package net.retromc.core.data.games;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.games.BedWarsStore;
import net.retromc.core.api.games.BedWarsStore.ShopSettings;
import net.retromc.core.api.games.ModeLevelStore.ModeLevel;
import net.retromc.core.api.util.LevelCurve;
import net.retromc.core.data.social.DataFixture;
import net.retromc.core.data.testing.TestTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 04 §9 #7 (Schnellkauf bleibt nach Reconnect erhalten), Shop-Einstellungen, Gold-Wahl und Modus-Level gegen
 * PostgreSQL 18.6 ({@code V4_1__bedwars_skywars.sql}).
 */
class BedWarsRepositoryIT {

    DataFixture fixture;
    BedWarsRepository store;
    ModeLevelRepository levels;

    @BeforeEach
    void setUp() {
        fixture = new DataFixture();
        store = new BedWarsRepository(fixture.async);
        levels = new ModeLevelRepository(fixture.async, fixture.clock);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void schnellkaufBleibtGespeichert() {
        UUID player = fixture.player("Quick");
        assertEquals(Map.of(), await(store.quickshop(player, BedWarsStore.MODERN)));
        await(store.saveQuickshop(player, BedWarsStore.MODERN, Map.of(0, "clay", 1, "stone_sword")));
        await(store.setQuickshopSlot(player, BedWarsStore.MODERN, 20, "fireball"));
        await(store.setQuickshopSlot(player, BedWarsStore.MODERN, 1, "iron_sword"));
        await(store.clearQuickshopSlot(player, BedWarsStore.MODERN, 0));
        // "Reconnect": neue Repository-Instanz liest denselben Stand
        BedWarsRepository fresh = new BedWarsRepository(fixture.async);
        assertEquals(Map.of(1, "iron_sword", 20, "fireball"), await(fresh.quickshop(player, BedWarsStore.MODERN)));
        assertEquals(Map.of(), await(fresh.quickshop(player, BedWarsStore.CLASSIC)), "Gruppen getrennt");
        assertThrows(IllegalArgumentException.class, () -> store.setQuickshopSlot(player, BedWarsStore.MODERN, 21,
                "x"));
    }

    @Test
    void shopEinstellungenUndGoldWahl() {
        UUID player = fixture.player("Settings");
        assertEquals(ShopSettings.DEFAULT, await(store.shopSettings(player)));
        ShopSettings changed = new ShopSettings(List.of(1, 0, 2, 3, 4, 5, 6, 8, 7), Map.of("weapon", 2, "blocks", 8));
        await(store.saveShopSettings(player, changed));
        assertEquals(changed, await(store.shopSettings(player)));
        assertEquals(2, changed.slot("weapon"));
        assertEquals(1, changed.slot("pickaxe"), "nicht gesetzte Art = Standard");
        assertEquals(Optional.empty(), await(store.goldVote(player)));
        await(store.saveGoldVote(player, "high"));
        await(store.saveGoldVote(player, "low"));
        assertEquals(Optional.of("low"), await(store.goldVote(player)));
        assertThrows(IllegalArgumentException.class, () -> store.saveGoldVote(player, "viel"));
    }

    @Test
    void modusLevel() {
        UUID player = fixture.player("Leveler");
        ModeLevel start = await(levels.level(player, "bedwars"));
        assertEquals(0, start.xp());
        ModeLevel first = await(levels.addXp(player, "bedwars", 181));
        assertEquals(181, first.xp());
        long next = LevelCurve.xpForLevel(first.level() + 1);
        ModeLevel up = await(levels.addXp(player, "bedwars", next - 181));
        assertTrue(up.levelUp());
        assertEquals(first.level() + 1, up.level());
        assertFalse(await(levels.addXp(player, "bedwars", 0)).levelUp());
        assertEquals(0, await(levels.level(player, "skywars")).xp(), "je Modus getrennt");
        assertEquals(1, TestTransactions.count(fixture.database.dataSource(),
                "SELECT count(*) FROM games.mode_level WHERE uuid = ?", player));
    }

    @Test
    void classicImStatistikKatalog() {
        assertEquals(1, TestTransactions.count(fixture.database.dataSource(),
                "SELECT count(*) FROM stats.mode WHERE key = 'bedwars_classic' AND points_formula = '{\"wins\":1}'"));
    }
}
