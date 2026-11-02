package net.retromc.paper.core.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Slot- und Layout-Mathematik nach Spec 02 §5.1–5.3. */
class GuiLayoutTest {

    @Test
    void rasterRechnetZeilenweise() {
        GuiGrid grid = new GuiGrid(6);

        assertEquals(54, grid.size());
        assertEquals(5, grid.lastRow());
        assertEquals(49, grid.slot(5, 4));
        assertEquals(5, grid.row(49));
        assertEquals(4, grid.column(49));
        assertTrue(grid.contains(53));
        assertFalse(grid.contains(54));
        assertFalse(grid.contains(-1));
    }

    @Test
    void rasterPrueftGrenzen() {
        assertThrows(IllegalArgumentException.class, () -> new GuiGrid(0));
        assertThrows(IllegalArgumentException.class, () -> new GuiGrid(7));
        GuiGrid grid = new GuiGrid(3);
        assertThrows(IllegalArgumentException.class, () -> grid.slot(3, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.slot(0, 9));
        assertThrows(IllegalArgumentException.class, () -> grid.requireSlot(27));
    }

    @Test
    void vollerRahmenBeiDreiReihen() {
        List<Integer> expected = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26);

        assertEquals(expected, BorderShape.FULL_BORDER.slots(new GuiGrid(3)));
    }

    @Test
    void vollerRahmenBeiSechsReihenLaesstDenInhaltFrei() {
        List<Integer> border = BorderShape.FULL_BORDER.slots(new GuiGrid(6));

        assertEquals(26, border.size());
        for (int slot : PageLayout.CONTENT_SLOTS) {
            assertFalse(border.contains(slot), "Inhalts-Slot im Rahmen: " + slot);
        }
        for (int slot : List.of(PageLayout.BACK, PageLayout.PREVIOUS, PageLayout.INDICATOR, PageLayout.NEXT,
                PageLayout.CLOSE)) {
            assertTrue(border.contains(slot), "Knopf liegt nicht in der Rahmenreihe: " + slot);
        }
    }

    @Test
    void obenUndUntenSowieTrennreihe() {
        GuiGrid grid = new GuiGrid(5);

        assertEquals(IntStream.concat(IntStream.rangeClosed(0, 8), IntStream.rangeClosed(36, 44)).boxed().toList(),
                BorderShape.TOP_BOTTOM.slots(grid));
        assertEquals(IntStream.rangeClosed(9, 17).boxed().toList(), BorderShape.separatorRow(1).slots(grid));
        assertEquals(IntStream.rangeClosed(0, 8).boxed().toList(), BorderShape.TOP_BOTTOM.slots(new GuiGrid(1)));
    }

    @Test
    void trennreiheAusserhalbDesRasters() {
        assertThrows(IllegalArgumentException.class, () -> BorderShape.separatorRow(6));
        assertThrows(IllegalArgumentException.class, () -> BorderShape.separatorRow(4).slots(new GuiGrid(3)));
    }

    @Test
    void listenLayoutNachSpec() {
        assertEquals(List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                PageLayout.CONTENT_SLOTS);
        assertEquals(21, PageLayout.perPage());
        assertEquals(22, PageLayout.EMPTY);
    }

    @Test
    void bestaetigungsdialogNachSpec() {
        GuiGrid grid = new GuiGrid(ConfirmLayout.ROWS);
        List<Integer> border = BorderShape.FULL_BORDER.slots(grid);

        assertEquals(List.of(11, 13, 15), List.of(ConfirmLayout.CONFIRM, ConfirmLayout.INFO, ConfirmLayout.CANCEL));
        assertFalse(border.contains(ConfirmLayout.CONFIRM));
        assertFalse(border.contains(ConfirmLayout.INFO));
        assertFalse(border.contains(ConfirmLayout.CANCEL));
    }

    @Test
    void klaengeNachSpec() {
        assertEquals("minecraft:ui.button.click", GuiSounds.CLICK.name().asString());
        assertEquals(0.5f, GuiSounds.CLICK.volume());
        assertEquals(1.0f, GuiSounds.CLICK.pitch());
        assertEquals("minecraft:entity.villager.no", GuiSounds.ERROR.name().asString());
        assertEquals("minecraft:entity.experience_orb.pickup", GuiSounds.SUCCESS.name().asString());
    }
}
