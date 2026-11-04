package net.retromc.bedwars;

/**
 * XP und Coins einer Runde (Spec 04 §6.1.5, Werte in {@code config.yml}): Kill +25 XP (auch nicht-finale), Bett
 * +10 XP, Sieg +50, Überlebt +20, +1 je 30 s Spielzeit; Basis-Coins = 2,5 × Runden-XP (gerundet). Ranking-Punkte
 * rechnet core-stats nach {@code stats.mode.points_formula} (5 × Final-Kill + 10 × Bett + 50 × Sieg).
 *
 * @param kill       XP je Kill
 * @param bed        XP je Bett
 * @param win        XP für den Sieg
 * @param survived   XP fürs Überleben
 * @param perSeconds Sekunden Spielzeit je XP
 * @param coinFactor Coins je XP
 */
public record BedWarsRewards(int kill, int bed, int win, int survived, int perSeconds, double coinFactor) {

    /** Standardwerte der Spec. */
    public static final BedWarsRewards DEFAULT = new BedWarsRewards(25, 10, 50, 20, 30, 2.5);

    public BedWarsRewards {
        if (kill < 0 || bed < 0 || win < 0 || survived < 0 || perSeconds < 1 || coinFactor < 0) {
            throw new IllegalArgumentException("Ungültige Belohnungswerte");
        }
    }

    /** XP ohne die laufend gutgeschriebenen Kill-/Bett-XP: Sieg, Überleben und Spielzeit. */
    public int endXp(boolean won, boolean alive, long playSeconds) {
        return (won ? win : 0) + (alive ? survived : 0) + (int) (Math.max(0, playSeconds) / perSeconds);
    }

    /** Gesamt-XP einer Runde. */
    public int totalXp(int kills, int beds, boolean won, boolean alive, long playSeconds) {
        return kills * kill + beds * bed + endXp(won, alive, playSeconds);
    }

    /** Basis-Coins für {@code CoinService.payoutRound}. */
    public long coins(int xp) {
        return Math.round(xp * coinFactor);
    }

    /** Ranking-Punkte (nur zur Anzeige im Rundenblock): 5 × Final-Kill + 10 × Bett + 50 × Sieg. */
    public static long points(int finalKills, int beds, boolean won) {
        return 5L * finalKills + 10L * beds + (won ? 50 : 0);
    }
}
