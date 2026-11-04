package net.retromc.paper.core.stats;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Rundenblock (Spec 02 §4.7): {@code §7-= §eStatistiken dieser Runde §7=-}, Zeilen {@code §7 {label}: §e{value}},
 * optional {@code Gesammelte Erfahrung}, eine Leerzeile, {@code Überlebt}/{@code Gewonnen} mit {@code §a✓}/{@code §c✗}
 * und der Fuß {@code §8----------------------}. Die Zeilen liefert der Spiel-Rahmen.
 *
 * @param lines Zeilen in Reihenfolge
 * @param xp gesammelte Erfahrung oder {@code null}
 * @param survived Überlebt oder {@code null} (Zeile entfällt)
 * @param won Gewonnen oder {@code null}
 * @param blankBeforeMarks Leerzeile vor Überlebt/Gewonnen
 * @param afterXp Zeilen mit {@code §a✓}/{@code §c✗} direkt nach der Erfahrung (SkyWars {@code Challenge}, Spec 04
 *                §6.2.3)
 */
public record RoundSummary(List<Line> lines, @Nullable Integer xp, @Nullable Boolean survived, @Nullable Boolean won,
        boolean blankBeforeMarks, List<Mark> afterXp) {

    public RoundSummary {
        lines = List.copyOf(lines);
        afterXp = List.copyOf(afterXp);
    }

    /** Ohne Zeilen nach der Erfahrung. */
    public RoundSummary(List<Line> lines, @Nullable Integer xp, @Nullable Boolean survived, @Nullable Boolean won,
            boolean blankBeforeMarks) {
        this(lines, xp, survived, won, blankBeforeMarks, List.of());
    }

    /**
     * Zeile {@code §7 {label}: §a✓|§c✗}.
     *
     * @param labelKey Textschlüssel des Labels
     * @param yes ✓ oder ✗
     */
    public record Mark(String labelKey, boolean yes) {

        public Mark {
            Objects.requireNonNull(labelKey, "labelKey");
        }
    }

    /**
     * Eine Zeile.
     *
     * @param labelKey Textschlüssel des Labels, z. B. {@code stats.label.kills}
     * @param value fertig formatierter Wert
     */
    public record Line(String labelKey, String value) {

        public Line {
            Objects.requireNonNull(labelKey, "labelKey");
            Objects.requireNonNull(value, "value");
        }
    }
}
