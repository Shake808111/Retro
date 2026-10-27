package net.retromc.core.api.punish;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Chatzeile im Puffer {@code chatbuf:{server}} (JSON, letzte 200 Zeilen, 15 min) und im Chatlog. */
public record ChatLine(UUID uuid, String name, String text, Instant at) {

    public ChatLine {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(at, "at");
    }
}
