package net.retromc.paper.core.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Eine Seite einer Liste, verteilt auf Inhalts-Slots (Spec 02 §5.1, §5.3). Blätter-Knöpfe nur bei Bedarf; die
 * Seitenanzeige erscheint nur bei mehr als einer Seite (Annahme: „bei Bedarf“ gilt auch für sie).
 *
 * @param page angezeigte Seite (ab 1, in den gültigen Bereich gezogen)
 * @param pages Anzahl der Seiten
 * @param entries Einträge dieser Seite mit Slot
 * @param previous Knopf „Vorherige Seite“ zeigen
 * @param next Knopf „Nächste Seite“ zeigen
 * @param <T> Typ der Einträge
 */
public record PageView<T>(int page, int pages, List<Entry<T>> entries, boolean previous, boolean next) {

    /**
     * Ein Eintrag auf seinem Slot.
     *
     * @param slot Slot in der GUI
     * @param value Eintrag
     * @param <T> Typ des Eintrags
     */
    public record Entry<T>(int slot, T value) {

        public Entry {
            Objects.requireNonNull(value, "value");
        }
    }

    public PageView {
        entries = List.copyOf(entries);
    }

    /** Seite {@code page} von {@code items} auf {@code slots} (je Seite so viele Einträge wie Slots). */
    public static <T> PageView<T> of(List<T> items, int page, List<Integer> slots) {
        Pagination pagination = new Pagination(items.size(), slots.size());
        int current = pagination.clamp(page);
        List<Entry<T>> entries = new ArrayList<>();
        int from = pagination.from(current);
        for (int index = from; index < pagination.to(current); index++) {
            entries.add(new Entry<>(slots.get(index - from), items.get(index)));
        }
        return new PageView<>(current, pagination.pages(), entries, pagination.hasPrevious(current),
                pagination.hasNext(current));
    }

    /** {@code true}, wenn die Liste leer ist (Hinweis „Keine Einträge“). */
    public boolean empty() {
        return entries.isEmpty();
    }

    /** {@code true}, wenn die Seitenanzeige erscheint. */
    public boolean indicator() {
        return pages > 1;
    }
}
