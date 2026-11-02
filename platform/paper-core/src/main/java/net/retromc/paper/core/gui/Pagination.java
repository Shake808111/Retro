package net.retromc.paper.core.gui;

/**
 * Seitenrechnung mit Seiten ab 1 (Anzeige {@code Seite p/n}). Eine leere Liste hat eine (leere) Seite.
 *
 * @param items Anzahl der Einträge
 * @param perPage Einträge je Seite
 */
public record Pagination(int items, int perPage) {

    public Pagination {
        if (items < 0) {
            throw new IllegalArgumentException("Anzahl darf nicht negativ sein: " + items);
        }
        if (perPage < 1) {
            throw new IllegalArgumentException("Mindestens ein Eintrag je Seite: " + perPage);
        }
    }

    /** Anzahl der Seiten, mindestens 1. */
    public int pages() {
        return Math.max(1, (items + perPage - 1) / perPage);
    }

    /** Seite in den gültigen Bereich 1–{@link #pages()} gezogen. */
    public int clamp(int page) {
        return Math.clamp(page, 1, pages());
    }

    /** Index des ersten Eintrags der Seite. */
    public int from(int page) {
        return (clamp(page) - 1) * perPage;
    }

    /** Index hinter dem letzten Eintrag der Seite. */
    public int to(int page) {
        return Math.min(items, from(page) + perPage);
    }

    /** {@code true}, wenn es vor der Seite eine weitere gibt. */
    public boolean hasPrevious(int page) {
        return clamp(page) > 1;
    }

    /** {@code true}, wenn es nach der Seite eine weitere gibt. */
    public boolean hasNext(int page) {
        return clamp(page) < pages();
    }
}
