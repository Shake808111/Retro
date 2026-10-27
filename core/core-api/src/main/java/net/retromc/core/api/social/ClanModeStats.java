package net.retromc.core.api.social;

import java.util.Objects;
import net.retromc.core.api.stats.ModeKey;

/**
 * {@code /clan stats [Modus]} (Spec 01 §3.4 #55): Summe der Gesamt-Statistik ({@code stats.alltime}) aller
 * Mitglieder in einem freigeschalteten Modus. Inhalt der Zeilen ist eine Annahme (Spec 01 nennt nur die Keys).
 */
public record ClanModeStats(long clanId, ModeKey mode, int members, long kills, long deaths, long games, long wins,
        long points) {

    public ClanModeStats {
        Objects.requireNonNull(mode, "mode");
        if (members < 0 || kills < 0 || deaths < 0 || games < 0 || wins < 0) {
            throw new IllegalArgumentException("Negative Clan-Statistik");
        }
    }
}
