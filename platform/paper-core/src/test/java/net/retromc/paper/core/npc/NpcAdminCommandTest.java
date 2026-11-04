package net.retromc.paper.core.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.retromc.core.api.BuildInfo;
import org.junit.jupiter.api.Test;

/** {@code /npc remove} ohne Nummer: nächster NPC im Umkreis von 5 Blöcken. */
class NpcAdminCommandTest {

    @Test
    void naechsterImUmkreis() {
        Map<String, Double> distance = Map.of("a", 4.0, "b", 2.5, "c", 2.5, "d", 9.0);
        List<String> all = List.of("a", "b", "c", "d");
        assertEquals(Optional.of("b"), NpcAdminCommand.nearest(all, distance::get, NpcAdminCommand.REMOVE_RANGE));
        assertEquals(Optional.of("a"), NpcAdminCommand.nearest(List.of("a", "d"), distance::get,
                NpcAdminCommand.REMOVE_RANGE));
        assertEquals(Optional.empty(), NpcAdminCommand.nearest(List.of("d"), distance::get,
                NpcAdminCommand.REMOVE_RANGE));
        assertEquals(Optional.empty(), NpcAdminCommand.nearest(List.of("x"), name -> Double.POSITIVE_INFINITY,
                NpcAdminCommand.REMOVE_RANGE));
        assertEquals(BuildInfo.BRAND_ID + ".npc.admin", NpcAdminCommand.PERMISSION);
        // gespeicherte NPCs zeigen ihre Datenbank-ID, vorläufige (ohne Datenbank) ihre Zählnummer
        assertEquals("12", NpcAdminCommand.display(12));
        assertEquals("3", NpcAdminCommand.display(-3));
    }
}
