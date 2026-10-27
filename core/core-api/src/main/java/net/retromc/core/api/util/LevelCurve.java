package net.retromc.core.api.util;

/**
 * Level-Kurve {@code xpFür(n) = 250·n·(n+1)} (kumulativ; Level n kostet 500·n XP mehr als Level n−1).
 * Gilt für das Clan-Level (Spec 01 §6.6) und das globale Netzwerk-Level (Spec 02 §8.1, {@code NetworkLevelService}).
 */
public final class LevelCurve {

    /** Obergrenze der Kurve; die Schwelle {@code 250·n·(n+1)} bleibt damit sicher im {@code long}-Bereich. */
    public static final int MAX_LEVEL = 100_000_000;

    private LevelCurve() {
    }

    /** Gesamt-XP, ab der Level {@code level} erreicht ist. */
    public static long xpForLevel(int level) {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Level außerhalb 0.." + MAX_LEVEL + ": " + level);
        }
        return 250L * level * (level + 1L);
    }

    /** Höchstes Level, dessen Schwelle {@code xp} nicht übersteigt. */
    public static int levelForXp(long xp) {
        if (xp < 0) {
            throw new IllegalArgumentException("xp darf nicht negativ sein: " + xp);
        }
        long estimate = (long) Math.floor((Math.sqrt(1.0 + xp / 62.5) - 1.0) / 2.0);
        int level = (int) Math.min(MAX_LEVEL, Math.max(0, estimate));
        while (level > 0 && xpForLevel(level) > xp) {
            level--;
        }
        while (level < MAX_LEVEL && xpForLevel(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    /** Fortschritt für Anzeigen wie {@code clan.level} „Level {lvl} ({xp}/{need} XP)“. */
    public static Progress progress(long xp) {
        int level = levelForXp(xp);
        long next = level < MAX_LEVEL ? xpForLevel(level + 1) : xpForLevel(MAX_LEVEL);
        return new Progress(level, xp, next);
    }

    /**
     * Stand auf der Kurve.
     *
     * @param xp gesamte XP
     * @param nextLevelXp Gesamt-XP, ab der das nächste Level erreicht ist
     */
    public record Progress(int level, long xp, long nextLevelXp) {

        public Progress {
            if (level < 0 || xp < 0 || nextLevelXp < 0) {
                throw new IllegalArgumentException("Negative Werte im Level-Fortschritt");
            }
        }
    }
}
