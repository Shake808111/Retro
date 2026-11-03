package net.retromc.core.data.punish;

import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.punish.ReportReason;
import net.retromc.core.api.punish.ReportResult;
import net.retromc.core.api.punish.ReportService;

/** Prüfungen von {@code /report} ohne Datenzugriff (Spec 01 §6.9 Nr. 1–2; A21). */
public final class ReportRules {

    private ReportRules() {
    }

    /** Selbstmeldung und Grund am Server ({@code randomkilling} nur TTT/Replay). */
    public static Optional<ReportResult.Outcome> precheck(UUID reporter, UUID target, ReportReason reason,
            String serverMode) {
        if (reporter.equals(target)) {
            return Optional.of(ReportResult.Outcome.SELF);
        }
        if (!reason.allowedOn(serverMode)) {
            return Optional.of(ReportResult.Outcome.REASON_NOT_ALLOWED);
        }
        return Optional.empty();
    }

    /**
     * Sperren: dasselbe Ziel innerhalb 300 s ({@code COOLDOWN}), höchstens 10 Meldungen je Stunde ({@code LIMIT}).
     *
     * @param targetCooldownActive {@code cd:report:{reporter}:{target}} existiert
     * @param reportsThisHour Stand von {@code cd:report:{reporter}}
     */
    public static Optional<ReportResult.Outcome> limits(boolean targetCooldownActive, int reportsThisHour) {
        if (targetCooldownActive) {
            return Optional.of(ReportResult.Outcome.COOLDOWN);
        }
        if (reportsThisHour >= ReportService.MAX_PER_HOUR) {
            return Optional.of(ReportResult.Outcome.LIMIT);
        }
        return Optional.empty();
    }
}
