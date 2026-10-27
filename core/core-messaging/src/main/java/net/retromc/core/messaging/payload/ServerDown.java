package net.retromc.core.messaging.payload;

import java.util.Objects;

/**
 * {@code SERVER_DOWN {server, reason}} (net:server-state; Spec 08 §8.3): Server/Controller → alle.
 *
 * @param reason Stoppgrund wie {@code infra.server_instance.stop_reason} (MATCH_END, IDLE, DRAIN, ADMIN, …)
 */
public record ServerDown(String server, String reason) {

    public ServerDown {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reason, "reason");
    }
}
