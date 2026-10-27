package net.retromc.core.api.punish;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis von {@code ReportService.create}; Fehler entsprechen {@code report.err.*} (Spec 01 §7).
 *
 * @param report angelegte Meldung bei {@link Outcome#CREATED}
 */
public record ReportResult(Outcome outcome, @Nullable Report report) {

    /** Mögliche Ergebnisse. */
    public enum Outcome {
        CREATED,
        /** {@code report.err.self}. */
        SELF,
        /** gleiches Ziel innerhalb 300 s ({@code report.err.cooldown}). */
        COOLDOWN,
        /** mehr als 10 Meldungen pro Stunde ({@code report.err.limit}). */
        LIMIT,
        /** Grund auf diesem Server nicht zulässig ({@code report.err.reason}). */
        REASON_NOT_ALLOWED,
        /** Team kann nicht gemeldet werden ({@code report.err.staff}). */
        STAFF,
        UNKNOWN_PLAYER
    }

    public ReportResult {
        Objects.requireNonNull(outcome, "outcome");
        if ((outcome == Outcome.CREATED) != (report != null)) {
            throw new IllegalArgumentException("report genau bei CREATED angeben");
        }
    }
}
