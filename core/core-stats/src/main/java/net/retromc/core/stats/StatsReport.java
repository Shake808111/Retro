package net.retromc.core.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatsMode;

/**
 * Ausgabe-Baustein für {@code /stats}, {@code /statsall}, {@code /statsd}, Hologramme und das Statistik-Profil
 * (Spec 02 §4.7, §6.7, §6.8): die Felder aus {@code stats.mode.fields} in Reihenfolge mit typisiertem Wert. Die
 * Formatierung je Oberfläche (Chat Punkt, Hologramm/Scoreboard Komma) übernimmt die Plattform mit {@code Formats}.
 *
 * <p>Die Zeile „Position im Ranking“ entfällt bei Modi ohne Bestenliste ({@code metric=none}); ein fehlender Platz
 * ist {@code -}.
 *
 * @param entries Zeilen in Anzeigereihenfolge
 */
public record StatsReport(List<Entry> entries) {

    public StatsReport {
        entries = List.copyOf(entries);
    }

    /**
     * Baut den Bericht.
     *
     * @param position Platz in der Bestenliste des Zeitraums; leer = nicht gelistet oder Fenster ohne Platz
     */
    public static StatsReport of(StatsMode mode, StatLine line, OptionalLong position) {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(line, "line");
        List<Entry> entries = new ArrayList<>();
        for (String field : mode.fields()) {
            if (StatsMode.RANK_FIELD.equals(field)) {
                if (mode.hasLeaderboard()) {
                    entries.add(new Entry(field, position.isPresent() ? new Value.Count(position.getAsLong())
                            : new Value.Missing()));
                }
                continue;
            }
            entries.add(new Entry(field, value(field, line)));
        }
        return new StatsReport(entries);
    }

    /** Wert eines Feldes aus der Zählerzeile. */
    public static Value value(String field, StatLine line) {
        return switch (field) {
            case StatKeys.KD -> new Value.KillDeath(line.kills(), line.deaths());
            case StatKeys.WINRATE -> new Value.WinRate(line.wins(), line.games());
            case StatKeys.BEST_TIME -> {
                long best = line.extra().getOrDefault(StatKeys.BEST_TIME_MS, 0L);
                yield best > 0 ? new Value.Time(best) : new Value.Missing();
            }
            default -> new Value.Count(line.value(field).orElse(0));
        };
    }

    /** Textschlüssel des Labels ({@code stats.label.<feld>}). */
    public static String labelKey(String field) {
        return "stats.label." + field;
    }

    /** Eine Zeile. */
    public record Entry(String field, Value value) {

        public Entry {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(value, "value");
        }

        public String labelKey() {
            return StatsReport.labelKey(field);
        }
    }

    /** Typisierter Wert; die Plattform formatiert je Oberfläche. */
    public sealed interface Value {

        /** Ganze Zahl (Zähler, Platz). */
        record Count(long value) implements Value {
        }

        /** K/D aus Kills und Deaths. */
        record KillDeath(long kills, long deaths) implements Value {
        }

        /** Siegesquote aus Siegen und Spielen. */
        record WinRate(long wins, long games) implements Value {
        }

        /** Zeit in Millisekunden (Bestzeit). */
        record Time(long millis) implements Value {
        }

        /** Kein Wert ({@code -}). */
        record Missing() implements Value {
        }
    }
}
