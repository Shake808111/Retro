package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Neue Meldung ({@code REPORT_NEW}, net:moderation; Spec 01 §8.2): Velocity → Team-Proxy-Zustellung, Web.
 *
 * @param id Anzeige-ID der Meldung
 * @param reason Grund-Schlüssel ({@code ReportReason.key()})
 */
public record ReportNew(String id, UUID reporter, UUID target, String reason, String server) {

    public ReportNew {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reporter, "reporter");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(server, "server");
    }
}
