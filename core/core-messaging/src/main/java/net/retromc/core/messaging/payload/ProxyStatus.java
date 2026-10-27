package net.retromc.core.messaging.payload;

import java.util.Objects;

/** {@code PROXY_UP} / {@code PROXY_DOWN {proxy}} (net:server-state; Spec 08 §8.3): Velocity → alle. */
public record ProxyStatus(String proxy) {

    public ProxyStatus {
        Objects.requireNonNull(proxy, "proxy");
    }
}
