package net.retromc.core.api.social;

import java.util.Objects;
import java.util.UUID;

/** Spieler mit zuletzt bekanntem Namen (Listen wie {@code /ignore list}). */
public record PlayerRef(UUID uuid, String name) {

    public PlayerRef {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }
}
