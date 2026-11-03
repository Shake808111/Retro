package net.retromc.paper.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;

/**
 * Bauplan einer Sidebar (Spec 02 §4.1, §8.1 {@code BoardSpec.builder().title().line(id, Supplier).blank()
 * .showScores(bool)}): höchstens 15 Zeilen von oben nach unten, Scores {@code n−1 … 0}. Werte kommen aus Suppliern
 * und werden bei {@code BoardService.update} neu gelesen.
 *
 * @param showScores Score-Zahlen sichtbar (Lobby ja, Duels-Match nein; ältere Clients zeigen sie immer)
 */
public record BoardSpec(Supplier<Component> title, List<Line> lines, boolean showScores) {

    /** Höchstzahl der Zeilen (Vanilla). */
    public static final int MAX_LINES = 15;

    public BoardSpec {
        Objects.requireNonNull(title, "title");
        lines = List.copyOf(lines);
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("Höchstens " + MAX_LINES + " Zeilen: " + lines.size());
        }
        if (lines.stream().map(Line::id).distinct().count() != lines.size()) {
            throw new IllegalArgumentException("Zeilen-IDs müssen eindeutig sein");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Index einer Zeile (0 = oben) oder −1. */
    public int index(String id) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Alle Zeilen ausgewertet. */
    public List<Component> render() {
        return lines.stream().map(line -> line.value().get()).toList();
    }

    /** Eine Zeile mit ID und Wert. */
    public record Line(String id, Supplier<Component> value) {

        public Line {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(value, "value");
        }
    }

    /** Builder. */
    public static final class Builder {

        private Supplier<Component> title = Component::empty;
        private final List<Line> lines = new ArrayList<>();
        private boolean showScores = true;
        private int blanks;

        private Builder() {
        }

        public Builder title(Supplier<Component> value) {
            this.title = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder line(String id, Supplier<Component> value) {
            lines.add(new Line(id, value));
            return this;
        }

        /** Leerzeile (FastBoard hält gleiche Zeilen auseinander). */
        public Builder blank() {
            lines.add(new Line("blank" + ++blanks, Component::empty));
            return this;
        }

        public Builder showScores(boolean value) {
            this.showScores = value;
            return this;
        }

        public BoardSpec build() {
            return new BoardSpec(title, lines, showScores);
        }
    }
}
