package net.retromc.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EloCalculatorTest {

    @Test
    void zahlenbeispieleSpec01A3() {
        assertEquals(25, EloCalculator.delta(1000, 1000, 1.0, 50));
        assertEquals(12, EloCalculator.delta(1200, 1000, 1.0, 50));
        assertEquals(38, EloCalculator.delta(1000, 1200, 1.0, 50));
    }

    @Test
    void nullsummeBeiderSeiten() {
        int[][] cases = {{1000, 1000}, {1200, 1000}, {1000, 1200}, {1537, 911}, {800, 1400}};
        for (int[] ratings : cases) {
            for (double score : new double[] {0.0, 0.5, 1.0}) {
                EloCalculator.Update update = EloCalculator.apply(ratings[0], ratings[1], score, EloCalculator.CLAN_K);
                assertEquals(0, update.ratingA() - ratings[0] + update.ratingB() - ratings[1]);
                assertEquals(update.delta(), update.ratingA() - ratings[0]);
            }
        }
    }

    @Test
    void niederlageUndRemis() {
        assertEquals(-25, EloCalculator.delta(1000, 1000, 0.0, 50));
        assertEquals(0, EloCalculator.delta(1000, 1000, 0.5, 50));
        assertEquals(-38, EloCalculator.delta(1200, 1000, 0.0, 50));
    }

    @Test
    void konstantenUndUngueltigeWerte() {
        assertEquals(1000, EloCalculator.START_RATING);
        assertEquals(50, EloCalculator.CLAN_K);
        assertEquals(0.5, EloCalculator.expected(1000, 1000), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> EloCalculator.delta(1000, 1000, 1.5, 50));
        assertThrows(IllegalArgumentException.class, () -> EloCalculator.delta(1000, 1000, 1.0, 0));
    }
}
