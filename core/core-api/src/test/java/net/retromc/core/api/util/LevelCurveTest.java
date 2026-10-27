package net.retromc.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LevelCurveTest {

    @Test
    void schwellenNachFormel() {
        assertEquals(0, LevelCurve.xpForLevel(0));
        assertEquals(500, LevelCurve.xpForLevel(1));
        assertEquals(1500, LevelCurve.xpForLevel(2));
        assertEquals(3000, LevelCurve.xpForLevel(3));
        // Level n kostet 500·n XP mehr als Level n−1
        for (int n = 1; n < 200; n++) {
            assertEquals(500L * n, LevelCurve.xpForLevel(n) - LevelCurve.xpForLevel(n - 1));
        }
    }

    @Test
    void levelAusXp() {
        assertEquals(0, LevelCurve.levelForXp(0));
        assertEquals(0, LevelCurve.levelForXp(499));
        assertEquals(1, LevelCurve.levelForXp(500));
        assertEquals(1, LevelCurve.levelForXp(1499));
        assertEquals(2, LevelCurve.levelForXp(1500));
        for (int n = 0; n < 5000; n += 7) {
            long xp = LevelCurve.xpForLevel(n);
            assertEquals(n, LevelCurve.levelForXp(xp));
            if (n > 0) {
                assertEquals(n - 1, LevelCurve.levelForXp(xp - 1));
            }
        }
        assertEquals(LevelCurve.MAX_LEVEL, LevelCurve.levelForXp(Long.MAX_VALUE));
    }

    @Test
    void fortschrittFuerClanLevel() {
        LevelCurve.Progress progress = LevelCurve.progress(1700);
        assertEquals(2, progress.level());
        assertEquals(1700, progress.xp());
        assertEquals(3000, progress.nextLevelXp());
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.levelForXp(-1));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.xpForLevel(-1));
    }
}
