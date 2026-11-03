package net.retromc.game.framework.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.retromc.game.framework.phase.CountdownPlan.Event;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Spec 03 §9 #7: Countdown 60 s, Ansagen, Singular, voll//start → 10, Abbruch, Schutz ab ≤ 5 s. */
class CountdownPlanTest {

    private final CountdownPlan plan = CountdownPlan.standard();

    @Test
    void sechzigSekundenUndAnsagenGenauImRaster() {
        assertEquals(60, plan.total());
        List<Integer> announced = new ArrayList<>();
        int rest = plan.total();
        while (true) {
            if (plan.announce(rest)) {
                announced.add(rest);
            }
            if (rest == 0) {
                break;
            }
            rest = plan.next(rest, Event.TICK);
        }
        assertEquals(List.of(60, 50, 40, 30, 20, 10, 5, 4, 3, 2, 1), announced);
    }

    @ParameterizedTest
    @CsvSource({"60,10", "11,10", "10,10", "7,7", "1,1"})
    void vollUndStartVerkuerzenNurUeberZehn(int rest, int expected) {
        assertEquals(expected, plan.next(rest, Event.FULL));
        assertEquals(expected, plan.next(rest, Event.START_CMD));
        assertEquals(rest > 10, plan.shortens(rest));
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 30, 11, 10, 6})
    void zuWenigeSpielerBrechenAb(int rest) {
        assertTrue(plan.cancels(rest));
        assertEquals(60, plan.next(rest, Event.BELOW_MIN));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 4, 3, 2, 1, 0})
    void abFuenfSekundenKeinAbbruch(int rest) {
        assertFalse(plan.cancels(rest));
        assertEquals(rest, plan.next(rest, Event.BELOW_MIN));
    }

    @Test
    void teamwahlUndForcemapAbZehnGesperrt() {
        assertTrue(plan.choicesOpen(11));
        assertFalse(plan.choicesOpen(10));
        assertFalse(plan.choicesOpen(0));
    }

    @Test
    void tickEndetBeiNullUndXpBalken() {
        assertEquals(0, plan.next(0, Event.TICK));
        assertEquals(1f, plan.progress(60));
        assertEquals(0.5f, plan.progress(30));
        assertEquals(0f, plan.progress(0));
    }

    @Test
    void bedwarsRasterMitFuenfzehn() {
        CountdownPlan bedwars = CountdownPlan.lobby(60, java.util.Set.of(60, 50, 40, 30, 20, 15, 10, 5, 4, 3, 2, 1));
        assertTrue(bedwars.announce(15));
        assertFalse(plan.announce(15));
    }
}
