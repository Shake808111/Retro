package net.retromc.game.framework.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Wartelobby über der Map wird als abgegrenzte Plattform gefunden; mit der Map verbunden bleibt alles stehen. */
class WaitingPlatformTest {

    private static Set<WaitingPlatform.Pos> platform(int y) {
        Set<WaitingPlatform.Pos> blocks = new HashSet<>();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                blocks.add(new WaitingPlatform.Pos(x, y, z));
                if (Math.abs(x) == 5 || Math.abs(z) == 5) {
                    // Barriere-Rand zwei Blöcke hoch
                    blocks.add(new WaitingPlatform.Pos(x, y + 1, z));
                    blocks.add(new WaitingPlatform.Pos(x, y + 2, z));
                }
            }
        }
        return blocks;
    }

    @Test
    void plattformUeberDerMapWirdGefunden() {
        Set<WaitingPlatform.Pos> world = new HashSet<>(platform(117));
        // Map darunter (buildMaxY 92) bleibt unberührt
        for (int x = -60; x <= 60; x++) {
            world.add(new WaitingPlatform.Pos(x, 60, 0));
        }
        Optional<Set<WaitingPlatform.Pos>> found = WaitingPlatform.collect(0, 118, 0,
                (x, y, z) -> world.contains(new WaitingPlatform.Pos(x, y, z)));
        assertTrue(found.isPresent());
        assertEquals(platform(117), found.get());
    }

    @Test
    void mitDerMapVerbundenBleibtStehen() {
        Set<WaitingPlatform.Pos> world = new HashSet<>(platform(70));
        for (int y = 40; y < 70; y++) {
            world.add(new WaitingPlatform.Pos(0, y, 0));
        }
        assertTrue(WaitingPlatform.collect(0, 71, 0, (x, y, z) -> world.contains(new WaitingPlatform.Pos(x, y, z)))
                .isEmpty(), "Säule bis tief unter den Spawn = Teil der Map");
        assertTrue(WaitingPlatform.collect(0, 80, 0, (x, y, z) -> false).isEmpty(), "nichts unter dem Spawn");
    }
}
