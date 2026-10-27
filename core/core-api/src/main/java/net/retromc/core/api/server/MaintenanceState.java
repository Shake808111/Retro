package net.retromc.core.api.server;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Wartungsstatus, Valkey-HASH {@code maintenance} mit {@code enabled, reason, since} und
 * {@code core.network_setting['maintenance']} (Spec 01 §2.3, §6.15).
 */
public record MaintenanceState(boolean enabled, @Nullable String reason, @Nullable Instant since) {

    public static final MaintenanceState OFF = new MaintenanceState(false, null, null);

    public MaintenanceState {
        if (enabled && since == null) {
            throw new IllegalArgumentException("Aktive Wartung braucht einen Startzeitpunkt");
        }
    }
}
