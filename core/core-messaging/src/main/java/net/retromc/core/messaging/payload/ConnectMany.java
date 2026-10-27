package net.retromc.core.messaging.payload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** {@code CONNECT_MANY {uuids[], server}} (net:proxy; Spec 08 §8.3): Controller/Server → Velocity. */
public record ConnectMany(List<UUID> uuids, String server) {

    public ConnectMany {
        Objects.requireNonNull(server, "server");
        uuids = List.copyOf(uuids);
    }
}
