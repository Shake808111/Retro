package net.retromc.game.framework.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 03 §9 #15: Restart 20 s mit Ansagen 20/15/10/5/4/3/2/1, Abbruch 5 s, Server-Stopp 3 s danach. */
class RestartPlanTest {

    @Test
    void ansagenStandard() {
        assertEquals(List.of(20, 15, 10, 5, 4, 3, 2, 1), announced(RestartPlan.STANDARD));
        assertEquals("common.restart.one", RestartPlan.key(1));
        assertEquals("common.restart", RestartPlan.key(20));
        assertEquals(3, RestartPlan.SHUTDOWN_DELAY_SECONDS);
    }

    @Test
    void abbruchFuenfSekunden() {
        assertEquals(5, RestartPlan.ABORT.total());
        assertEquals(List.of(5, 4, 3, 2, 1), announced(RestartPlan.ABORT));
        assertEquals(1f, RestartPlan.ABORT.progress(5));
    }

    private static List<Integer> announced(RestartPlan plan) {
        List<Integer> result = new ArrayList<>();
        for (int rest = plan.total(); rest >= 0; rest--) {
            if (plan.announce(rest)) {
                result.add(rest);
            }
        }
        return result;
    }
}
