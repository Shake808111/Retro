package net.retromc.game.framework.phase;

import java.util.Set;

/**
 * Restart-Phase nach dem Rundenende (Spec 03 §6.10): 20 s, Ansagen bei 20, 15, 10, 5, 4, 3, 2, 1, der XP-Balken
 * zählt mit; Abbruch (alle weg in INGAME) 5 s. Bei 0 werden die Spieler verschoben, 3 s später stoppt der Server.
 *
 * @param total Länge in Sekunden
 */
public record RestartPlan(int total) {

    /** Normales Rundenende. */
    public static final RestartPlan STANDARD = new RestartPlan(20);
    /** Runde ohne Spieler abgebrochen. */
    public static final RestartPlan ABORT = new RestartPlan(5);
    /** Ansagen (Spec 03 §6.10 Nr. 5). */
    public static final Set<Integer> ANNOUNCE = Set.of(20, 15, 10, 5, 4, 3, 2, 1);
    /** Abstand zwischen Verschieben in die Lobby und {@code requestShutdown} (Spec 03 §6.10 Nr. 9). */
    public static final int SHUTDOWN_DELAY_SECONDS = 3;

    public RestartPlan {
        if (total < 1) {
            throw new IllegalArgumentException("total muss ≥ 1 sein");
        }
    }

    /** Ob bei diesem Rest {@code common.restart} gesendet wird. */
    public boolean announce(int rest) {
        return rest <= total && ANNOUNCE.contains(rest);
    }

    /** XP-Balken {@code exp = Rest/Gesamt}. */
    public float progress(int rest) {
        return Math.clamp((float) rest / total, 0f, 1f);
    }

    /** Textschlüssel der Ansage (Einzahl bei 1). */
    public static String key(int rest) {
        return rest == 1 ? "common.restart.one" : "common.restart";
    }
}
