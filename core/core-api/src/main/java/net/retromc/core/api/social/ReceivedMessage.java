package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Empfangene PN in {@code msg:recent:{uuid}} (JSON {@code {from,text,ts}}, Spec 01 §2.3; {@code /msg list}). */
public record ReceivedMessage(UUID from, String text, Instant ts) {

    public ReceivedMessage {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(ts, "ts");
    }
}
