package net.retromc.paper.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.retromc.core.api.map.MapMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code map.json} lesen/schreiben (Spec 08 §6.11) und die Ladeprüfung des Standard-Map-Laders. */
class MapJsonTest {

    static final String JSON = """
            {
              "id": "beispiel",
              "displayName": "Beispiel",
              "mode": "bedwars",
              "variants": ["4x4", "8x1"],
              "version": 1,
              "builders": ["Bauteam"],
              "license": "own",
              "minPlayers": 8,
              "maxPlayers": 16,
              "bounds": {"min": {"x": -150, "y": 0, "z": -150}, "max": {"x": 150, "y": 160, "z": 150}},
              "spectatorSpawn": {"x": 0.5, "y": 110, "z": 0.5, "yaw": 0, "pitch": 30},
              "waitingLobby": {"x": 0.5, "y": 150, "z": 0.5},
              "markers": {"buildMaxY": 120, "spawns": {"red": {"x": 0, "y": 64, "z": -60, "yaw": 0}}}
            }
            """;

    @Test
    void liestAllePflichtfelder() {
        MapMeta meta = MapJson.parse(JSON);
        assertEquals("beispiel", meta.id());
        assertEquals(List.of("4x4", "8x1"), meta.variants());
        assertEquals(List.of("Bauteam"), meta.builders());
        assertEquals(30f, meta.spectatorSpawn().pitch());
        assertNotNull(meta.waitingLobby());
        assertEquals(0f, meta.waitingLobby().yaw());
        assertTrue(meta.markersJson().contains("buildMaxY"));
        assertTrue(meta.supportsVariant("8x1"));
        assertTrue(meta.fits(16));
    }

    @Test
    void schreibenUndWiederLesen() {
        MapMeta meta = MapJson.parse(JSON);
        MapMeta again = MapJson.parse(MapJson.write(meta));
        assertEquals(meta.id(), again.id());
        assertEquals(meta.bounds(), again.bounds());
        assertEquals(meta.spectatorSpawn(), again.spectatorSpawn());
        assertEquals(meta.waitingLobby(), again.waitingLobby());
        assertEquals(MapJson.parse(again.markersJson().isEmpty() ? JSON : JSON).markersJson(), again.markersJson());
    }

    @Test
    void fehlerWerdenGemeldet() {
        assertThrows(IllegalArgumentException.class, () -> MapJson.parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> MapJson.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> MapJson.parse(JSON.replace("\"markers\"", "\"x\"")));
        assertThrows(IllegalArgumentException.class, () -> MapJson.parse(JSON.replace("\"minPlayers\": 8",
                "\"minPlayers\": 20")));
    }

    @Test
    void ladepruefung() {
        MapMeta meta = MapJson.parse(JSON);
        assertEquals(List.of(), MapFeature.check(meta, "beispiel"));
        assertEquals(List.of("id 'beispiel' ≠ Ordner 'anders'"), MapFeature.check(meta, "anders"));
        MapMeta foreign = MapJson.parse(JSON.replace("\"own\"", "\"cc-by\""));
        assertEquals(List.of("license muss \"own\" sein"), MapFeature.check(foreign, "beispiel"));
    }

    @Test
    void weltKopieOhneSitzungsdaten(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("quelle");
        Files.createDirectories(source.resolve("region"));
        Files.createDirectories(source.resolve("playerdata"));
        Files.writeString(source.resolve("level.dat"), "x");
        Files.writeString(source.resolve("region/r.0.0.mca"), "r");
        Files.writeString(source.resolve("session.lock"), "s");
        Files.writeString(source.resolve("map.json"), JSON);
        Path target = dir.resolve("ziel");
        Files.createDirectories(target);
        Files.writeString(target.resolve("alt.txt"), "weg");
        MapFeature.copyWorld(source, target);
        assertTrue(Files.exists(target.resolve("level.dat")));
        assertTrue(Files.exists(target.resolve("region/r.0.0.mca")));
        assertTrue(Files.notExists(target.resolve("session.lock")));
        assertTrue(Files.notExists(target.resolve("playerdata")));
        assertTrue(Files.notExists(target.resolve("map.json")));
        assertTrue(Files.notExists(target.resolve("alt.txt")));
    }
}
