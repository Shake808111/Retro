package net.retromc.core.api.util;

import java.util.List;
import java.util.Objects;

/**
 * Eine Seite einer Liste (Seiten ab 1), z. B. {@code /friend list [Seite]} mit 10 Einträgen je Seite
 * (Spec 01 §3.1 #10) oder Listen-GUIs. Eine leere Liste hat genau eine (leere) Seite.
 */
public record Page<T>(List<T> items, int page, int pages, int total) {

    public Page {
        items = List.copyOf(items);
        if (page < 1 || pages < 1 || page > pages || total < 0) {
            throw new IllegalArgumentException("Ungültige Seite " + page + "/" + pages + " bei " + total + " Einträgen");
        }
    }

    /** Schneidet Seite {@code page} aus; Seitenzahlen außerhalb werden auf 1 bzw. die letzte Seite begrenzt. */
    public static <T> Page<T> of(List<T> all, int page, int size) {
        Objects.requireNonNull(all, "all");
        if (size < 1) {
            throw new IllegalArgumentException("size muss mindestens 1 sein: " + size);
        }
        int total = all.size();
        int pages = Math.max(1, (total + size - 1) / size);
        int current = Math.min(Math.max(page, 1), pages);
        int from = (current - 1) * size;
        int to = Math.min(total, from + size);
        return new Page<>(all.subList(from, to), current, pages, total);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < pages;
    }
}
