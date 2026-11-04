package net.retromc.paper.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.retromc.core.api.map.MapMeta;
import org.junit.jupiter.api.Assumptions;
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
        assertEquals(List.of("license muss own, cc-by-4.0 oder cc-by-sa-4.0 sein"),
                MapFeature.check(foreign, "beispiel"));
        for (String free : List.of("cc-by-4.0", "cc-by-sa-4.0")) {
            MapMeta licensed = MapJson.parse(JSON.replace("\"own\"", "\"" + free + "\""));
            assertEquals(List.of(), MapFeature.check(licensed, "beispiel"), free);
            assertTrue(licensed.attribution());
        }
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

    @Test
    void weltKopieImFormat26(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("quelle");
        Path region = source.resolve("dimensions/minecraft/overworld/region");
        Files.createDirectories(region);
        Files.createDirectories(source.resolve("players/data"));
        Files.writeString(source.resolve("level.dat"), "x");
        Files.writeString(region.resolve("r.0.0.mca"), "r");
        Files.writeString(source.resolve("dimensions/minecraft/overworld/paper-world.yml"), "w");
        Files.writeString(source.resolve("players/data/a.dat"), "p");
        Files.writeString(source.resolve(MapFeature.CREDITS_FILE), "c");
        Path target = dir.resolve("ziel");
        MapFeature.copyWorld(source, target);
        assertTrue(Files.exists(target.resolve("dimensions/minecraft/overworld/region/r.0.0.mca")));
        assertTrue(Files.exists(target.resolve("dimensions/minecraft/overworld/paper-world.yml")));
        assertTrue(Files.notExists(target.resolve("players")));
        assertTrue(Files.notExists(target.resolve(MapFeature.CREDITS_FILE)));
    }

    @Test
    void freieLizenzBrauchtCredits(@TempDir Path dir) throws IOException {
        MapMeta licensed = MapJson.parse(JSON.replace("\"own\"", "\"cc-by-4.0\""));
        Path folder = Files.createDirectories(dir.resolve("beispiel"));
        assertEquals(List.of("CREDITS.md fehlt (Namensnennung für cc-by-4.0)"), MapFeature.check(licensed, folder));
        Files.writeString(folder.resolve(MapFeature.CREDITS_FILE), "Autor");
        assertEquals(List.of(), MapFeature.check(licensed, folder));
        assertEquals(List.of(), MapFeature.check(MapJson.parse(JSON), dir.resolve("beispiel")));
    }

    /** Alle mitgelieferten Maps unter {@code deploy/*}/maps laden ohne Fehler (Ladeprüfung, Credits, JSON). */
    @Test
    void mitgelieferteMapsSindLadbar() throws IOException {
        Path deploy = Path.of("../../deploy");
        int checked = 0;
        try (Stream<Path> files = Files.walk(deploy)) {
            for (Path file : files.filter(path -> path.getFileName().toString().equals(MapFeature.MAP_FILE)
                    && path.getParent().getParent().getFileName().toString().equals("maps")).toList()) {
                MapMeta meta = MapJson.parse(Files.readString(file, StandardCharsets.UTF_8));
                assertEquals(List.of(), MapFeature.check(meta, file.getParent()), file.toString());
                checked++;
            }
        }
        assertTrue(checked >= 9, "BedWars-Beispiel und 8 BedWars-Maps erwartet: " + checked);
    }

    /**
     * Echte Map aus einem Archiv (Weltformat 26.x): {@code RETROMC_MAP_ZIP=/pfad/kirschbluete.zip}. Entpacken, Kopie
     * wie beim Rundenstart, Ladeprüfung der {@code map.json}.
     */
    @Test
    void echteMapAusArchiv(@TempDir Path dir) throws IOException {
        String archive = System.getenv("RETROMC_MAP_ZIP");
        Assumptions.assumeTrue(archive != null && Files.isRegularFile(Path.of(archive)), "RETROMC_MAP_ZIP fehlt");
        Path maps = Files.createDirectories(dir.resolve("maps"));
        try (InputStream in = Files.newInputStream(Path.of(archive)); ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path out = maps.resolve(entry.getName()).normalize();
                assertTrue(out.startsWith(maps), entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zip, out);
                }
            }
        }
        try (Stream<Path> folders = Files.list(maps)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                MapMeta meta = MapJson.parse(Files.readString(folder.resolve(MapFeature.MAP_FILE),
                        StandardCharsets.UTF_8));
                assertEquals(List.of(), MapFeature.check(meta, folder));
                Path target = dir.resolve(MapFeature.WORLD_PREFIX + meta.id());
                MapFeature.copyWorld(folder, target);
                assertTrue(Files.isRegularFile(target.resolve("level.dat")));
                try (Stream<Path> regions = Files.list(target.resolve("dimensions/minecraft/overworld/region"))) {
                    assertTrue(regions.anyMatch(path -> path.toString().endsWith(".mca")));
                }
                assertTrue(Files.notExists(target.resolve(MapFeature.MAP_FILE)));
            }
        }
    }
}
