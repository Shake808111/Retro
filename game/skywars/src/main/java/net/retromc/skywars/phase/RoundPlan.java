package net.retromc.skywars.phase;

import java.util.OptionalInt;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Zeitplan einer SkyWars-Runde in Sekunden seit dem Teleport (Spec 04 §6.2.1, ohne Server testbar):
 * Käfig 3-2-1 → Aufbauphase/Schutzzeit 30 s (Ansagen 30, 20, 10, 3, 2, 1) → Kampf mit Border-Ansage 600 s → Border
 * bei Kampf + 600 s oder 60 s nach „nur noch 2 Teams“, was früher kommt (Ansagen 600, 300, 60, 30, 10, 5–1).
 *
 * @param cage       Käfig-Countdown (3)
 * @param protection Schutzzeit (30)
 * @param borderDelay Border nach Kampfbeginn (600)
 * @param twoTeams    Border nach „nur noch 2 Teams“ (60)
 */
public record RoundPlan(int cage, int protection, int borderDelay, int twoTeams) {

    /** Abschnitte der Runde. */
    public enum Phase {
        /** Spieler stehen im Käfig, kein Schaden außer Void. */
        CAGE,
        /** Aufbauphase: kein PvP-Schaden, Kit-Fähigkeiten gesperrt. */
        PROTECTION,
        /** Kampf. */
        FIGHT
    }

    /** Ansagen der Schutzzeit (B). */
    public static final Set<Integer> PROTECTION_ANNOUNCE = Set.of(30, 20, 10, 3, 2, 1);
    /** Ansagen bis zum Border-Start (600/300 … AN). */
    public static final Set<Integer> BORDER_ANNOUNCE = Set.of(600, 300, 60, 30, 10, 5, 4, 3, 2, 1);

    public RoundPlan {
        if (cage < 1 || protection < 0 || borderDelay < 1 || twoTeams < 1) {
            throw new IllegalArgumentException("Zeiten ungültig");
        }
    }

    /** Beginn der Schutzzeit (Käfig öffnet sich). */
    public int protectionStart() {
        return cage;
    }

    /** Beginn des Kampfes. */
    public int fightStart() {
        return cage + protection;
    }

    public Phase phase(int second) {
        if (second < cage) {
            return Phase.CAGE;
        }
        return second < fightStart() ? Phase.PROTECTION : Phase.FIGHT;
    }

    /** Käfig-Countdown {@code Die Runde beginnt in n} bei Sekunde 0, 1, 2 → 3, 2, 1. */
    public OptionalInt cageCountdown(int second) {
        return second >= 0 && second < cage ? OptionalInt.of(cage - second) : OptionalInt.empty();
    }

    /** Ansage {@code Die Schutzzeit endet in n} oder leer. */
    public OptionalInt protectionAnnounce(int second) {
        int rest = fightStart() - second;
        return second >= cage && PROTECTION_ANNOUNCE.contains(rest) && rest <= protection ? OptionalInt.of(rest)
                : OptionalInt.empty();
    }

    /**
     * Sekunde des Border-Starts.
     *
     * @param twoTeamsAt Sekunde, ab der nur noch 2 Teams leben, oder {@code null}
     */
    public int borderStart(@Nullable Integer twoTeamsAt) {
        int regular = fightStart() + borderDelay;
        if (twoTeamsAt == null) {
            return regular;
        }
        return Math.min(regular, Math.max(twoTeamsAt, fightStart()) + twoTeams);
    }

    /**
     * Ob „nur noch 2 Teams“ den Border-Start vorzieht (dann {@code sw.border.twoteams} mit 60 s).
     *
     * @param second Sekunde, in der nur noch 2 Teams leben
     */
    public boolean twoTeamsShortens(int second) {
        return borderStart(second) < borderStart(null);
    }

    /** Ansage {@code Die Border startet in n} oder leer (600 kommt mit dem Kampfbeginn). */
    public OptionalInt borderAnnounce(int second, @Nullable Integer twoTeamsAt) {
        int rest = borderStart(twoTeamsAt) - second;
        return second > fightStart() && BORDER_ANNOUNCE.contains(rest) ? OptionalInt.of(rest)
                : OptionalInt.empty();
    }
}
