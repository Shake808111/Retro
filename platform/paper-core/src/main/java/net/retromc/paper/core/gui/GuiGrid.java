package net.retromc.paper.core.gui;

/**
 * Slot-Raster einer Truhen-GUI: 9 Spalten, 1–6 Reihen (Spec 02 §5.1). Slots zählen zeilenweise ab 0 oben links.
 *
 * @param rows Anzahl der Reihen
 */
public record GuiGrid(int rows) {

    /** Spalten je Reihe. */
    public static final int COLUMNS = 9;
    /** Kleinste Reihenzahl. */
    public static final int MIN_ROWS = 1;
    /** Größte Reihenzahl. */
    public static final int MAX_ROWS = 6;

    public GuiGrid {
        if (rows < MIN_ROWS || rows > MAX_ROWS) {
            throw new IllegalArgumentException("Reihen müssen zwischen 1 und 6 liegen: " + rows);
        }
    }

    /** Anzahl der Slots. */
    public int size() {
        return rows * COLUMNS;
    }

    /** Index der letzten Reihe. */
    public int lastRow() {
        return rows - 1;
    }

    /** Slot zu Reihe und Spalte (beide ab 0). */
    public int slot(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= COLUMNS) {
            throw new IllegalArgumentException("Reihe " + row + ", Spalte " + column + " liegt außerhalb von "
                    + rows + "×" + COLUMNS);
        }
        return row * COLUMNS + column;
    }

    /** Reihe eines Slots. */
    public int row(int slot) {
        return requireSlot(slot) / COLUMNS;
    }

    /** Spalte eines Slots. */
    public int column(int slot) {
        return requireSlot(slot) % COLUMNS;
    }

    /** {@code true}, wenn der Slot im Raster liegt. */
    public boolean contains(int slot) {
        return slot >= 0 && slot < size();
    }

    /** Prüft den Slot und gibt ihn zurück. */
    public int requireSlot(int slot) {
        if (!contains(slot)) {
            throw new IllegalArgumentException("Slot " + slot + " liegt außerhalb von 0–" + (size() - 1));
        }
        return slot;
    }
}
