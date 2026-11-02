package net.retromc.paper.core.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Rahmenformen aus Glasscheiben (Spec 02 §5.1): {@link #FULL_BORDER} (äußerer Ring), {@link #TOP_BOTTOM} (erste und
 * letzte Reihe) und {@link #separatorRow(int)} (eine ganze Reihe, z. B. Reihe 2 unter einer Kategorieleiste).
 */
public sealed interface BorderShape permits BorderShape.FullBorder, BorderShape.TopBottom, BorderShape.SeparatorRow {

    /** Äußerer Ring. */
    BorderShape FULL_BORDER = new FullBorder();

    /** Erste und letzte Reihe. */
    BorderShape TOP_BOTTOM = new TopBottom();

    /** Ganze Reihe {@code row} (ab 0). */
    static BorderShape separatorRow(int row) {
        return new SeparatorRow(row);
    }

    /** Slots der Form im Raster, aufsteigend. */
    List<Integer> slots(GuiGrid grid);

    /** Äußerer Ring. */
    record FullBorder() implements BorderShape {

        @Override
        public List<Integer> slots(GuiGrid grid) {
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < grid.size(); slot++) {
                int row = grid.row(slot);
                int column = grid.column(slot);
                if (row == 0 || row == grid.lastRow() || column == 0 || column == GuiGrid.COLUMNS - 1) {
                    slots.add(slot);
                }
            }
            return List.copyOf(slots);
        }
    }

    /** Erste und letzte Reihe. */
    record TopBottom() implements BorderShape {

        @Override
        public List<Integer> slots(GuiGrid grid) {
            List<Integer> slots = new ArrayList<>(rowSlots(grid, 0));
            if (grid.lastRow() > 0) {
                slots.addAll(rowSlots(grid, grid.lastRow()));
            }
            return List.copyOf(slots);
        }
    }

    /**
     * Ganze Reihe.
     *
     * @param row Reihe ab 0
     */
    record SeparatorRow(int row) implements BorderShape {

        public SeparatorRow {
            if (row < 0 || row >= GuiGrid.MAX_ROWS) {
                throw new IllegalArgumentException("Reihe muss zwischen 0 und 5 liegen: " + row);
            }
        }

        @Override
        public List<Integer> slots(GuiGrid grid) {
            return rowSlots(grid, row);
        }
    }

    private static List<Integer> rowSlots(GuiGrid grid, int row) {
        List<Integer> slots = new ArrayList<>(GuiGrid.COLUMNS);
        for (int column = 0; column < GuiGrid.COLUMNS; column++) {
            slots.add(grid.slot(row, column));
        }
        return List.copyOf(slots);
    }
}
