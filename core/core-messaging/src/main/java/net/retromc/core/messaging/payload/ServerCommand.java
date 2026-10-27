package net.retromc.core.messaging.payload;

import java.util.Objects;

/** {@code SHUTDOWN} / {@code DRAIN {server, reason}} (net:controller; Spec 08 §8.3): Controller → Server. */
public record ServerCommand(String server, String reason) {

    public ServerCommand {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reason, "reason");
    }
}
