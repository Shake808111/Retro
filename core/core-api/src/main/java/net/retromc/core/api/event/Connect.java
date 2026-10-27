package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** Spieler auf einen Server verbinden ({@code CONNECT}, net:proxy; Spec 01 §2.4). */
public record Connect(UUID uuid, String server) {

    public Connect {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(server, "server");
    }
}
