package net.retromc.core.api.stats;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Werte eines Spielers am Rundenende (Spec 02 §6.4 Schritt 1: {@code uuid, kills, deaths, win, extra, xp, survived}).
 * In BedWars zählen nur Final-Kills und Final-Deaths (entscheidet das Modul).
 *
 * @param team Team-Nummer ({@code stats.match_player.team}) oder {@code null} ohne Teams
 * @param placement Platzierung oder {@code null}
 * @param points vom Modul gelieferte Punkte (metric ≠ Formel); {@code null} = core-stats rechnet nach
 *               {@code stats.mode.points_formula}
 * @param extra modusspezifische Zähler, z. B. {@code beds}, {@code cores}, {@code karma}
 */
public record PlayerMatchStats(
        UUID uuid,
        @Nullable Integer team,
        @Nullable Integer placement,
        int kills,
        int deaths,
        boolean win,
        boolean survived,
        int xp,
        @Nullable Long points,
        Map<String, Long> extra) {

    public PlayerMatchStats {
        Objects.requireNonNull(uuid, "uuid");
        extra = Map.copyOf(extra);
        if (kills < 0 || deaths < 0 || xp < 0) {
            throw new IllegalArgumentException("kills, deaths und xp dürfen nicht negativ sein");
        }
    }

    /** {@code win} als Zähler 0/1 wie in {@code stats.match_player.wins}. */
    public int wins() {
        return win ? 1 : 0;
    }
}
