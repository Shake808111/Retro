package net.retromc.core.api.server;

import java.time.Instant;
import java.util.Objects;

/**
 * Proxy-Eintrag {@code proxy:{id}} mit {@code online, startedAt, build} (Spec 08 §2.2), TTL 15 s, Heartbeat 5 s.
 * Genutzt von {@code /cloud proxies}, {@code /whereami} und der API.
 */
public record ProxyInfo(String id, int online, Instant startedAt, String build) {

    public ProxyInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(build, "build");
        if (online < 0) {
            throw new IllegalArgumentException("online darf nicht negativ sein");
        }
    }
}
