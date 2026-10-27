package net.retromc.core.api.punish;

/** Spalte {@code moderation.report.status VARCHAR(12)}, Standard {@code OPEN} (V1). */
public enum ReportStatus {
    OPEN,
    /** Von einem Teammitglied übernommen ([ÜBERNEHMEN]). */
    CLAIMED,
    /** Berechtigt ({@code report.result.ok}). */
    ACCEPTED,
    /** Abgelehnt ({@code report.result.rejected}). */
    REJECTED;

    public boolean closed() {
        return this == ACCEPTED || this == REJECTED;
    }
}
