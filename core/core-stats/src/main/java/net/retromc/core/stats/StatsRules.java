package net.retromc.core.stats;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.stats.StatsMode;

/**
 * Regeln rund um Abfragen, {@code /togglestats} und {@code /resetstats} (Spec 02 §3, §6.9, §6.10, §6.14).
 */
public final class StatsRules {

    /** {@code /togglestats}: mindestens 100 Spielstunden ({@code playtime_s ≥ 360000}). */
    public static final long TOGGLE_MIN_PLAYTIME_S = 360_000;
    /** Anti-Spam von {@code /togglestats} (Spec 02 O17). */
    public static final Duration TOGGLE_COOLDOWN = Duration.ofSeconds(3);
    /** Größe von {@code /top}. */
    public static final int TOP_SIZE = 10;
    /** Recht: versteckte Stats trotzdem sehen. */
    public static final String BYPASS_PERMISSION = BuildInfo.BRAND_ID + ".stats.bypass";

    private StatsRules() {
    }

    /** Darf der Spieler seine Stats verbergen? */
    public static boolean mayToggle(long playtimeSeconds) {
        return playtimeSeconds >= TOGGLE_MIN_PLAYTIME_S;
    }

    /**
     * Darf {@code viewer} die Stats von {@code target} sehen (Spec 02 §6.10)? Versteckte Stats sehen nur der Spieler
     * selbst und Team mit {@link #BYPASS_PERMISSION}.
     */
    public static boolean mayView(UUID viewer, UUID target, boolean hidden, boolean bypass) {
        return !hidden || viewer.equals(target) || bypass;
    }

    /**
     * Prüft {@code /resetstats} (Spec 02 §6.9 Schritt 1) und liefert den Fehlerschlüssel: zuerst der Ort (nur die
     * Lobby des Modus), dann ob der Modus zurücksetzbar ist, dann der Token.
     *
     * @param mode Modus der Lobby oder leer, wenn der Server keine Modus-Lobby ist
     */
    public static Optional<String> resetError(Optional<StatsMode> mode, boolean modeLobby, long resetTokens) {
        if (mode.isEmpty() || !modeLobby) {
            return Optional.of("reset.wrong-place");
        }
        if (!mode.get().resettable()) {
            return Optional.of("reset.not-resettable");
        }
        if (resetTokens < 1) {
            return Optional.of("reset.no-token");
        }
        return Optional.empty();
    }

    /** Antwort „Ja“/„Nein“ im Chat (Groß-/Kleinschreibung egal); leer bei anderem Text. */
    public static Optional<Boolean> chatAnswer(String message) {
        String text = message.strip();
        if (text.equalsIgnoreCase("ja")) {
            return Optional.of(true);
        }
        if (text.equalsIgnoreCase("nein")) {
            return Optional.of(false);
        }
        return Optional.empty();
    }
}
