package net.retromc.core.messaging.payload;

import java.util.Objects;
import java.util.UUID;

/** {@code ROLE_SYNC_REQUEST {uuid}} (net:integ; Spec 08 §8.3): API/Admin → Bot. */
public record RoleSyncRequest(UUID uuid) {

    public RoleSyncRequest {
        Objects.requireNonNull(uuid, "uuid");
    }
}
