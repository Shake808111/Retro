package net.retromc.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.retromc.bedwars.map.BedWarsMap;
import net.retromc.bedwars.shop.ShopCatalog;
import net.retromc.bedwars.shop.ShopItem;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapDraft;
import net.retromc.game.framework.map.MapValidator;
import net.retromc.game.framework.team.TeamColor;
import net.retromc.paper.core.map.MapJson;
import org.junit.jupiter.api.Test;

/** Shop-Katalog (Spec 04 §6.1.6, §5.1), Map-Marker (§6.1.8) und die Beispiel-Konfiguration. */
class ShopUndMapTest {

    private static ShopCatalog catalog(boolean classic) throws IOException {
        try (Reader reader = new InputStreamReader(ShopUndMapTest.class.getResourceAsStream("/shop.yml"),
                StandardCharsets.UTF_8)) {
            return ShopCatalog.load(reader, classic);
        }
    }

    private static void price(ShopCatalog catalog, String id, int amount, int price, Resource currency) {
        ShopItem item = catalog.item(id).orElseThrow();
        assertEquals(amount, item.amount(), id + " Menge");
        assertEquals(price, item.price(), id + " Preis");
        assertEquals(currency, item.currency(), id + " Währung");
    }

    @Test
    void belegtePreiseUndMengen() throws IOException {
        ShopCatalog shop = catalog(false);
        price(shop, "clay", 16, 8, Resource.COPPER);
        price(shop, "glass", 4, 2, Resource.COPPER);
        price(shop, "end_stone", 3, 12, Resource.COPPER);
        price(shop, "ladder", 8, 4, Resource.COPPER);
        price(shop, "chain_armor", 1, 32, Resource.COPPER);
        price(shop, "iron_armor", 1, 12, Resource.IRON);
        price(shop, "diamond_armor", 1, 16, Resource.GOLD);
        price(shop, "stick", 1, 7, Resource.IRON);
        price(shop, "iron_sword", 1, 15, Resource.IRON);
        price(shop, "diamond_sword", 1, 5, Resource.GOLD);
        price(shop, "arrows", 8, 2, Resource.GOLD);
        price(shop, "ender_pearl", 1, 11, Resource.GOLD);
        price(shop, "platform", 1, 3, Resource.GOLD);
        price(shop, "teleporter", 1, 3, Resource.IRON);
        price(shop, "mobile_shop", 1, 8, Resource.IRON);
        price(shop, "portal", 2, 4, Resource.GOLD);
        price(shop, "freezer", 1, 6, Resource.IRON);
        price(shop, "bridge", 1, 4, Resource.IRON);
        price(shop, "sneaker", 1, 8, Resource.COPPER);
        price(shop, "bed_builder", 1, 16, Resource.IRON);
        price(shop, "super_tnt", 1, 6, Resource.GOLD);
        price(shop, "c4", 1, 1, Resource.GOLD);
        price(shop, "vest", 1, 10, Resource.IRON);
        assertEquals(Map.of("knockback", 1), shop.item("stick").orElseThrow().enchants());
        assertTrue(shop.item("iron_armor").orElseThrow().permanent());
        assertTrue(shop.items().stream().noneMatch(item -> item.id().contains("booster")
                || item.material().equals("GLOWSTONE") || item.material().equals("HONEY_BLOCK")), "entfernt");
    }

    @Test
    void kategorienUndSchnellkauf() throws IOException {
        ShopCatalog shop = catalog(false);
        for (int category = 1; category <= 8; category++) {
            assertFalse(shop.category(category).isEmpty(), "Kategorie " + category);
            assertTrue(shop.category(category).size() <= 21);
        }
        Map<Integer, String> quick = shop.defaultQuickshop();
        assertEquals("clay", quick.get(0));
        assertEquals("stone_sword", quick.get(1));
        assertEquals("chain_armor", quick.get(2));
        assertEquals("end_stone", quick.get(3));
        assertEquals("shears", quick.get(4));
        assertEquals("chest", quick.get(5));
        assertEquals("glass", quick.get(6));
        assertEquals("diamond_sword", quick.get(7));
        assertEquals("fireball", quick.get(8));
        assertEquals("iron_armor", quick.get(9));
        assertEquals("bridge", quick.get(16));
        assertEquals(17, quick.size());
        assertEquals(9, ShopCatalog.CATEGORIES.size());
        assertEquals("IRON_PICKAXE", shop.tier("pickaxe", 2).orElseThrow().material());
        assertEquals("RED_TERRACOTTA", shop.item("clay").orElseThrow().material("red"));
    }

    @Test
    void classicPreset() throws IOException {
        ShopCatalog shop = catalog(true);
        price(shop, "clay", 1, 1, Resource.BRONZE);
        price(shop, "end_stone", 1, 7, Resource.BRONZE);
        price(shop, "stick", 1, 8, Resource.BRONZE);
        price(shop, "iron_sword", 1, 5, Resource.GOLD);
        price(shop, "sandstone", 2, 1, Resource.BRONZE);
        price(shop, "platform", 1, 3, Resource.GOLD);
        assertTrue(shop.item("wind_cannon").isEmpty());
        assertTrue(catalog(false).item("sandstone").isEmpty());
        assertTrue(shop.defaultQuickshop().isEmpty());
    }

    @Test
    void beispielKonfigurationIstGueltig() throws IOException {
        Path file = Path.of("../../deploy/bedwars/maps/beispiel/map.json");
        MapMeta meta = MapJson.parse(Files.readString(file, StandardCharsets.UTF_8));
        GameMap map = new GameMap(meta);
        for (String key : meta.variants()) {
            GameVariant variant = GameVariant.parse(key);
            assertEquals(List.of(), MapValidator.validate(map, TeamMode.TEAMS, variant, TeamColor.BEDWARS_ORDER,
                    false), key);
            assertEquals(List.of(), new BedWarsMap(map).validate(MapValidator.teamIds(map, variant,
                    TeamColor.BEDWARS_ORDER), false), key);
        }
        BedWarsMap bedwars = new BedWarsMap(map);
        assertTrue(bedwars.bed("red").isPresent());
        assertEquals(110, bedwars.buildMaxY());
    }

    /** Alle mitgelieferten BedWars-Maps (CC BY 4.0) sind für jede ihrer Varianten spielbar. */
    @Test
    void mitgelieferteMapsSindGueltig() throws IOException {
        List<Path> files;
        try (java.util.stream.Stream<Path> folders = Files.list(Path.of("../../deploy/bedwars/maps"))) {
            files = folders.map(folder -> folder.resolve("map.json")).filter(Files::isRegularFile).sorted().toList();
        }
        assertTrue(files.size() >= 9, "Beispiel + 8 Maps: " + files.size());
        for (Path file : files) {
            MapMeta meta = MapJson.parse(Files.readString(file, StandardCharsets.UTF_8));
            GameMap map = new GameMap(meta);
            assertFalse(meta.variants().isEmpty(), file.toString());
            for (String key : meta.variants()) {
                GameVariant variant = GameVariant.parse(key);
                String where = meta.id() + " " + key;
                assertEquals(List.of(), MapValidator.validate(map, TeamMode.TEAMS, variant, TeamColor.BEDWARS_ORDER,
                        map.markers().has("waitingWorld")), where);
                assertEquals(List.of(), new BedWarsMap(map).validate(MapValidator.teamIds(map, variant,
                        TeamColor.BEDWARS_ORDER), false), where);
            }
            if (!meta.id().equals("beispiel")) {
                assertEquals("cc-by-4.0", meta.license(), meta.id());
                assertTrue(Files.isRegularFile(file.resolveSibling("CREDITS.md")), meta.id());
                MapMeta.Spawn waiting = java.util.Objects.requireNonNull(meta.waitingLobby(), meta.id());
                assertTrue(waiting.y() > new BedWarsMap(map).buildMaxY(), meta.id() + ": Wartelobby über buildMaxY");
            }
        }
    }

    @Test
    void setupEntwurfBautMapJson() {
        MapDraft draft = new MapDraft("insel", "bedwars");
        assertEquals(List.of("builder", "bounds", "spectator"), draft.missing());
        draft.displayName("Insel");
        draft.addBuilder("Bauteam");
        draft.corner(1, new MapMeta.Position(100, 0, 100));
        draft.corner(2, new MapMeta.Position(-100, 150, -100));
        draft.spectator(new MapMeta.Spawn(0.5, 100, 0.5, 0, 0));
        draft.waiting(new MapMeta.Spawn(0.5, 120, 0.5, 90, 0), "wartelobby");
        draft.put("spawns", "red", MapDraft.json(new MapMeta.Spawn(0.5, 64, -50.5, 0, 0)));
        draft.value("buildMaxY", 110);
        MapMeta meta = draft.build();
        assertEquals(new MapMeta.Position(-100, 0, -100), meta.bounds().min());
        assertEquals("own", meta.license());
        MapMeta again = MapJson.parse(MapJson.write(meta));
        GameMap map = new GameMap(again);
        assertEquals("wartelobby", GameMap.string(map.markers(), "waitingWorld"));
        assertTrue(map.teamSpawn("red").isPresent());
        assertEquals(2, MapDraft.of(again).build().version());
    }

    @Test
    void fehlendeMarkerWerdenGemeldet() {
        MapMeta meta = new MapMeta("leer", "Leer", "bedwars", List.of(), 1, List.of("Bauteam"), "own", 2, 8,
                new MapMeta.Bounds(new MapMeta.Position(-10, 0, -10), new MapMeta.Position(10, 100, 10)),
                new MapMeta.Spawn(0, 50, 0, 0, 0), null, "{}");
        List<String> errors = new BedWarsMap(new GameMap(meta)).validate(List.of("red"), false);
        assertTrue(errors.contains("buildMaxY fehlt"));
        assertTrue(errors.contains("beds.red fehlt"));
        assertTrue(errors.contains("upgrades.red fehlt"));
        assertTrue(errors.contains("spawners: copper-Spawner von red fehlt"));
        assertTrue(errors.contains("spawners: mindestens 4 diamond-Spawner nötig (0)"));
        assertFalse(new BedWarsMap(new GameMap(meta)).validate(List.of("red"), true).contains("upgrades.red fehlt"),
                "Classic ohne Upgrade-Händler");
    }
}
