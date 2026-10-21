package net.retromc.map.exporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapValidatorTest {

    @Test
    void leererOrdnerHatZweiProbleme(@TempDir Path dir) {
        assertEquals(2, MapValidator.validate(dir).size());
    }

    @Test
    void vollstaendigeKarteIstGueltig(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(MapValidator.MAP_CONFIG), "{}");
        Files.createDirectory(dir.resolve(MapValidator.REGION_DIR));
        assertTrue(MapValidator.validate(dir).isEmpty());
    }
}
