package net.retromc.paper.core.gui;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Feste Slots der Listen-GUI mit 6 Reihen (Spec 02 §5.1 „Blättern“, „Zurück/Schließen“, §5.3).
 */
public final class PageLayout {

    /** Reihen der Listen-GUI. */
    public static final int ROWS = 6;

    /** Inhalt: Slots 10–16, 19–25, 28–34 (21 je Seite). */
    public static final List<Integer> CONTENT_SLOTS = IntStream.of(10, 19, 28)
            .flatMap(start -> IntStream.rangeClosed(start, start + 6))
            .boxed()
            .toList();

    /** Zurück-Pfeil bei Untermenüs. */
    public static final int BACK = 45;
    /** Vorherige Seite. */
    public static final int PREVIOUS = 48;
    /** Seitenanzeige {@code Seite p/n}. */
    public static final int INDICATOR = 49;
    /** Nächste Seite. */
    public static final int NEXT = 50;
    /** Schließen (optional). */
    public static final int CLOSE = 53;
    /** Hinweis bei leerer Liste. */
    public static final int EMPTY = 22;

    private PageLayout() {
    }

    /** Einträge je Seite. */
    public static int perPage() {
        return CONTENT_SLOTS.size();
    }
}
