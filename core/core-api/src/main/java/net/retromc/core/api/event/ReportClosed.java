package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Meldung abgeschlossen ({@code REPORT_CLOSED}, net:moderation; Spec 01 §8.2). {@code justified} trägt das Ergebnis
 * für {@code report.result.ok/.rejected} (Ergänzung zur Spec-Nutzlast, §6.9 Nr. 5).
 */
public record ReportClosed(String id, UUID reporter, UUID target, String reason, String server, boolean justified) {

    public ReportClosed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reporter, "reporter");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(server, "server");
    }
}
