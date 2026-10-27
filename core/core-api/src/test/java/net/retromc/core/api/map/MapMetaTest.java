package net.retromc.core.api.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapMetaTest {

    private static final MapMeta.Bounds BOUNDS =
            new MapMeta.Bounds(new MapMeta.Position(-100, 0, -100), new MapMeta.Position(100, 128, 100));
    private static final MapMeta.Spawn SPECTATOR = new MapMeta.Spawn(0, 100, 0, 0f, 90f);

    private static MapMeta meta(List<String> variants, int min, int max) {
        return new MapMeta("farmland", "Farmland", "bedwars", variants, 3, List.of("Baumeister"), "own", min, max,
                BOUNDS, SPECTATOR, null, "{\"teams\":[]}");
    }

    @Test
    void variantenUndSpielerzahl() {
        MapMeta all = meta(List.of(), 2, 16);
        assertTrue(all.supportsVariant("4x4"));
        MapMeta only = meta(List.of("4x4"), 2, 16);
        assertTrue(only.supportsVariant("4x4"));
        assertFalse(only.supportsVariant("8x1"));
        assertTrue(only.fits(16));
        assertFalse(only.fits(17));
        assertFalse(only.fits(1));
    }

    @Test
    void grenzenUndGueltigkeit() {
        assertTrue(BOUNDS.contains(SPECTATOR.position()));
        assertFalse(BOUNDS.contains(new MapMeta.Position(0, 200, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new MapMeta.Bounds(new MapMeta.Position(0, 0, 0), new MapMeta.Position(0, 10, 10)));
        assertThrows(IllegalArgumentException.class, () -> meta(List.of(), 5, 4));
        assertThrows(IllegalArgumentException.class, () -> meta(List.of(), 1, 201));
        assertThrows(IllegalArgumentException.class, () -> new MapMeta("Farm Land", "x", "bedwars", List.of(), 1,
                List.of("b"), "own", 1, 2, BOUNDS, SPECTATOR, null, "{}"));
    }
}
