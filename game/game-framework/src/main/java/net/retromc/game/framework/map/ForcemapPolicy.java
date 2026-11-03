package net.retromc.game.framework.map;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.rank.RankLimits;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.phase.CountdownPlan;

/**
 * Regeln von {@code /forcemap} (Spec 03 §3 #5, §9 #12): Limit je Tag aus dem Rang (Supremium 10, Supremium+ 30,
 * VIP/Team ∞, Spec 01 §6.2), Tageswechsel 00:00 Europe/Berlin, eine Forcemap je Runde, nur WAITING oder STARTING
 * mit Rest &gt; 10 s. Besitzer einer Privatrunde und {@code /game setmap} sind ohne Limit.
 */
public final class ForcemapPolicy {

    /** Zeitzone des Tageswechsels. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    /** TTL des Tageszählers {@code cnt:forcemap:{uuid}:{yyyy-mm-dd}}. */
    public static final Duration COUNTER_TTL = Duration.ofHours(26);

    /** Ergebnis der Prüfung; jeder Fehler hat einen Schlüssel aus Spec 03 §7.2. */
    public enum Outcome {
        OK(null),
        NO_PERMISSION("common.noperm"),
        TOO_LATE("common.forcemap.too_late"),
        UNKNOWN("common.forcemap.unknown"),
        ALREADY("common.forcemap.already"),
        SAME("common.forcemap.same"),
        LIMIT("common.forcemap.limit");

        private final String key;

        Outcome(String key) {
            this.key = key;
        }

        /** Textschlüssel der Fehlermeldung oder {@code null} bei {@link #OK}. */
        public String key() {
            return key;
        }
    }

    /**
     * Eine Anfrage.
     *
     * @param phase         Phase der Runde
     * @param rest          Restsekunden des Countdowns (in WAITING der volle Countdown)
     * @param mapKnown      Map gehört zur Variante
     * @param mapAllowed    Map erlaubt {@code /forcemap} ({@code forcemap_allowed})
     * @param sameAsCurrent Map ist schon ausgewählt
     * @param alreadyForced in dieser Runde wurde schon eine Map festgelegt
     * @param limitPerDay   Rang-Limit ({@link RankLimits#UNLIMITED} = ∞, 0 = kein Recht)
     * @param usedToday     heutige Nutzungen
     * @param unlimited     Besitzer der Privatrunde oder Admin ({@code /game setmap}): ohne Recht/Limit/Rundenregel
     */
    public record Request(GameState phase, int rest, boolean mapKnown, boolean mapAllowed, boolean sameAsCurrent,
            boolean alreadyForced, int limitPerDay, int usedToday, boolean unlimited) {

        public Request {
            Objects.requireNonNull(phase, "phase");
        }
    }

    private ForcemapPolicy() {
    }

    public static Outcome check(Request request) {
        if (!request.unlimited() && request.limitPerDay() == 0) {
            return Outcome.NO_PERMISSION;
        }
        boolean waiting = request.phase() == GameState.WAITING || request.phase() == GameState.STARTING;
        if (!waiting || request.rest() <= CountdownPlan.LOCK) {
            return Outcome.TOO_LATE;
        }
        if (!request.mapKnown() || !request.mapAllowed()) {
            return Outcome.UNKNOWN;
        }
        if (request.alreadyForced() && !request.unlimited()) {
            return Outcome.ALREADY;
        }
        if (request.sameAsCurrent()) {
            return Outcome.SAME;
        }
        if (!request.unlimited() && !RankLimits.allows(request.limitPerDay(), request.usedToday())) {
            return Outcome.LIMIT;
        }
        return Outcome.OK;
    }

    /** Kalendertag des Zählers (Europe/Berlin). */
    public static LocalDate day(Instant now) {
        return LocalDate.ofInstant(now, ZONE);
    }

    /** Valkey-Schlüssel des Tageszählers (Spec 03 §2.2). */
    public static String counterKey(UUID player, LocalDate day) {
        return "cnt:forcemap:" + player + ":" + day;
    }
}
