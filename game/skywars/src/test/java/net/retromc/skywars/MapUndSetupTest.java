package net.retromc.skywars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapDraft;
import net.retromc.game.framework.map.MapValidator;
import net.retromc.paper.core.map.MapJson;
import net.retromc.skywars.map.SkyWarsMap;
import net.retromc.skywars.setup.SkyWarsSetup;
import org.junit.jupiter.api.Test;

/** Map-Marker (Spec 04 §6.2.2, §8.3), Beispiel-{@code map.json} unter {@code deploy/skywars} und {@code /swsetup}. */
class MapUndSetupTest {

    private static MapMeta beispiel() throws IOException {
        return MapJson.parse(Files.readString(Path.of("../../deploy/skywars/maps/beispiel/map.json"),
                StandardCharsets.UTF_8));
    }

    @Test
    void beispielIstFuerAlleVariantenGueltig() throws IOException {
        MapMeta meta = beispiel();
        assertEquals("skywars", meta.mode());
        assertEquals(List.of(), SkyWarsSetup.errors(meta, "8x1", GameVariant::parse));
        GameMap map = new GameMap(meta);
        SkyWarsMap skywars = new SkyWarsMap(map);
        assertEquals(8, skywars.islands().size());
        assertTrue(skywars.islands().stream().allMatch(island -> island.chests().size() >= 2
                && island.chests().size() <= 3), "2–3 Inseltruhen");
        assertTrue(skywars.mid().chests().size() >= 4 && skywars.mid().chests().size() <= 8, "4–8 Mitte-Truhen");
        assertTrue(skywars.mid().contains(0, 66, 0));
        assertFalse(skywars.mid().contains(60, 65, 0), "Insel liegt nicht in der Mitte");
        assertEquals(30, skywars.voidY());
        assertEquals(90, skywars.borderRadius());
        assertTrue(skywars.kitSettings().isPresent());
        // Solo nutzt spawns.solo, Teamvarianten spawns.{n} mit Teams 1 … N
        assertEquals(8, map.soloSpawns().size());
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8"), MapValidator.teamIds(map,
                GameVariant.parse("8x2"), List.of()));
        assertEquals(List.of(), MapValidator.validate(map, TeamMode.NUMBERED, GameVariant.parse("8x2"), List.of(),
                false));
    }

    @Test
    void fehlendeMarkerWerdenGemeldet() throws IOException {
        MapMeta meta = beispiel();
        List<String> errors = new SkyWarsMap(new GameMap(meta)).validate(GameVariant.parse("16x1"));
        assertTrue(errors.contains("islands: 8 < 16 Teams der Variante"), errors.toString());
        MapMeta empty = new MapMeta("leer", "Leer", "skywars", List.of(), 1, List.of("Bauteam"), "own", 2, 8,
                new MapMeta.Bounds(new MapMeta.Position(-10, 0, -10), new MapMeta.Position(10, 100, 10)),
                new MapMeta.Spawn(0, 50, 0, 0, 0), null, "{\"islands\":[{\"spawns\":[{\"x\":0,\"y\":60,\"z\":0}],"
                        + "\"chests\":[]}]}");
        List<String> missing = new SkyWarsMap(new GameMap(empty)).validate(GameVariant.parse("1x2"));
        assertTrue(missing.contains("islands[1]: 1 Spawns < Teamgröße 2"), missing.toString());
        assertTrue(missing.contains("islands[1]: 0 Truhen (erlaubt 2–3)"));
        assertTrue(missing.contains("mid: Region (min/max) fehlt"));
        assertTrue(missing.contains("mid: 0 Truhen (erlaubt 4–8)"));
        assertTrue(missing.contains("voidY fehlt"));
    }

    @Test
    void setupLeitetTeamsUndSpawnsAusDenInselnAb() {
        MapDraft draft = new MapDraft("wolken", "skywars");
        JsonArray islands = new JsonArray();
        for (int i = 0; i < 3; i++) {
            JsonObject island = new JsonObject();
            JsonArray spawns = new JsonArray();
            spawns.add(MapDraft.json(new MapMeta.Spawn(i * 10 + 0.5, 64, 0.5, 0, 0)));
            spawns.add(MapDraft.json(new MapMeta.Spawn(i * 10 + 2.5, 64, 0.5, 0, 0)));
            island.add("spawns", spawns);
            island.add("chests", new JsonArray());
            islands.add(island);
        }
        draft.markers().add("islands", islands);
        SkyWarsSetup.deriveSpawns(draft.markers());
        JsonObject markers = draft.markers();
        assertEquals(3, markers.getAsJsonArray("teams").size());
        assertEquals(10.5, markers.getAsJsonObject("spawns").getAsJsonObject("2").get("x").getAsDouble());
        assertEquals(3, markers.getAsJsonObject("spawns").getAsJsonArray("solo").size());
    }
}
