package net.retromc.core.api.util;

/**
 * Elo nach Schach-Formel (Spec 01 §6.6): {@code E_a = 1/(1+10^((R_b−R_a)/400))}, {@code Δ = round(K·(S_a−E_a))}.
 * Clan-Elo startet bei {@link #START_RATING} mit {@link #CLAN_K}; Nullsumme (Verlierer −Δ).
 */
public final class EloCalculator {

    /** Startwert der Clan-Elo je Modus (Spec 01 O15). */
    public static final int START_RATING = 1000;

    /** K-Faktor der Clan-Elo (Spec 00 §5 Nr. 13). */
    public static final int CLAN_K = 50;

    private EloCalculator() {
    }

    /** Erwartungswert von Spieler/Clan A gegen B. */
    public static double expected(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /**
     * Elo-Änderung für A.
     *
     * @param scoreA 1.0 Sieg, 0.5 Unentschieden, 0.0 Niederlage
     */
    public static int delta(int ratingA, int ratingB, double scoreA, int k) {
        if (scoreA < 0.0 || scoreA > 1.0 || Double.isNaN(scoreA)) {
            throw new IllegalArgumentException("scoreA muss zwischen 0 und 1 liegen: " + scoreA);
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k muss positiv sein: " + k);
        }
        return (int) Math.round(k * (scoreA - expected(ratingA, ratingB)));
    }

    /** Wendet das Ergebnis nullsummig an: A erhält Δ, B erhält −Δ. */
    public static Update apply(int ratingA, int ratingB, double scoreA, int k) {
        int delta = delta(ratingA, ratingB, scoreA, k);
        return new Update(ratingA + delta, ratingB - delta, delta);
    }

    /** Neue Werte beider Seiten und die Änderung für A. */
    public record Update(int ratingA, int ratingB, int delta) {
    }
}
