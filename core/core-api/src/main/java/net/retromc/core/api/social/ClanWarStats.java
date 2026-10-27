package net.retromc.core.api.social;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import net.retromc.core.api.stats.ModeKey;

/**
 * {@code /clan cwstats} (Spec 01 §3.4 #57, §6.6): Elo, Ranking-Platz
 * ({@code ROW_NUMBER() OVER (PARTITION BY mode ORDER BY elo DESC)}), Spiele, Siege, Niederlagen.
 */
public record ClanWarStats(long clanId, ModeKey mode, int elo, int rank, int games, int wins, int losses) {

    public ClanWarStats {
        Objects.requireNonNull(mode, "mode");
        if (rank < 1 || games < 0 || wins < 0 || losses < 0) {
            throw new IllegalArgumentException("Ungültige ClanWar-Statistik");
        }
    }

    /** Siegesquote {@code wins/games·100} (0 ohne Spiele). */
    public double winRate() {
        return games == 0 ? 0.0 : 100.0 * wins / games;
    }

    /** Siegesquote mit 2 Nachkommastellen, deutschem Komma und {@code " %"}, z. B. {@code 84,85 %}. */
    public String winRateText() {
        BigDecimal rate = games == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(games), 2, RoundingMode.HALF_UP);
        return rate.toPlainString().replace('.', ',') + " %";
    }
}
