package net.retromc.core.messaging.payload;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * {@code RESTART_SCHEDULE} / {@code RESTART_CANCEL {server, at?, reason?}} (net:controller; Spec 08 §8.3):
 * Controller → Server. {@code at} wird als Epoch-Millisekunden übertragen.
 */
public record RestartSchedule(String server, @Nullable Instant at, @Nullable String reason) {

    public RestartSchedule {
        Objects.requireNonNull(server, "server");
    }
}
