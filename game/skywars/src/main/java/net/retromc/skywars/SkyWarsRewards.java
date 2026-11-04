package net.retromc.skywars;

/** Ranking-Punkte SkyWars (Spec 04 §6.2.3, Spec 02 {@code points_formula {"kills":1,"wins":5}}). */
public final class SkyWarsRewards {

    /** Punkte je Kill. */
    public static final int KILL_POINTS = 1;
    /** Punkte für den Sieg. */
    public static final int WIN_POINTS = 5;

    private SkyWarsRewards() {
    }

    /** {@code 1 × Kill + 5 × Sieg}. */
    public static long points(int kills, boolean won) {
        return (long) KILL_POINTS * Math.max(0, kills) + (won ? WIN_POINTS : 0);
    }
}
