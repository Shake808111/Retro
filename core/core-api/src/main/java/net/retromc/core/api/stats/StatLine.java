package net.retromc.core.api.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Zählerzeile eines Spielers in einem Modus (eine Zeile aus {@code stats.daily}, {@code stats.alltime} oder deren
 * Summe über ein Zeitfenster, Spec 02 §2.1). Feste Spalten plus {@code extra} (z. B. {@code beds}, {@code karma}).
 */
public record StatLine(
        UUID uuid,
        ModeKey mode,
        long kills,
        long deaths,
        long games,
        long wins,
        long points,
        Map<String, Long> extra) {

    public static final String KILLS = "kills";
    public static final String DEATHS = "deaths";
    public static final String GAMES = "games";
    public static final String WINS = "wins";
    public static final String POINTS = "points";
    /** Abgeleitet: {@code games − wins} (MasterBuilders, Spec 02 §6.7). */
    public static final String LOSSES = "losses";

    public StatLine {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(mode, "mode");
        extra = Map.copyOf(extra);
        if (kills < 0 || deaths < 0 || games < 0 || wins < 0) {
            throw new IllegalArgumentException("Zähler dürfen nicht negativ sein");
        }
    }

    /** Leere Zeile (noch keine Runde gespielt). */
    public static StatLine empty(UUID uuid, ModeKey mode) {
        return new StatLine(uuid, mode, 0, 0, 0, 0, 0, Map.of());
    }

    /** Wert zu einem Stat-Schlüssel aus {@code stats.mode.fields}; abgeleitete Werte (kd, winrate) liefert core-stats. */
    public OptionalLong value(String statKey) {
        return switch (statKey) {
            case KILLS -> OptionalLong.of(kills);
            case DEATHS -> OptionalLong.of(deaths);
            case GAMES -> OptionalLong.of(games);
            case WINS -> OptionalLong.of(wins);
            case POINTS -> OptionalLong.of(points);
            case LOSSES -> OptionalLong.of(Math.max(0, games - wins));
            default -> {
                Long value = extra.get(statKey);
                yield value == null ? OptionalLong.empty() : OptionalLong.of(value);
            }
        };
    }

    /** Summe zweier Zeilen desselben Spielers und Modus (Tagesbuckets → Fenster). */
    public StatLine plus(StatLine other) {
        if (!uuid.equals(other.uuid) || !mode.equals(other.mode)) {
            throw new IllegalArgumentException("Zeilen verschiedener Spieler oder Modi");
        }
        Map<String, Long> sum = new HashMap<>(extra);
        other.extra.forEach((key, value) -> sum.merge(key, value, Long::sum));
        return new StatLine(uuid, mode, kills + other.kills, deaths + other.deaths, games + other.games,
                wins + other.wins, points + other.points, sum);
    }
}
