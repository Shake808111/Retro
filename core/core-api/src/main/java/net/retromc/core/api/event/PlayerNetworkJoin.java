package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** Spieler betritt das Netzwerk ({@code PLAYER_NETWORK_JOIN}, net:social; Spec 01 §8.2): Velocity → Lobby/Kern-UI, Stats. */
public record PlayerNetworkJoin(UUID uuid, String name, String proxy) {

    public PlayerNetworkJoin {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(proxy, "proxy");
    }
}
