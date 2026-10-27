package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** Spieler verlässt das Netzwerk ({@code PLAYER_NETWORK_QUIT}, net:social; Spec 01 §8.2). */
public record PlayerNetworkQuit(UUID uuid, String name, String proxy) {

    public PlayerNetworkQuit {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(proxy, "proxy");
    }
}
