package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** Spieler vom Netzwerk werfen ({@code KICK}, net:moderation; Spec 01 §2.4): der Proxy des Spielers zeigt {@code screen.kick}. */
public record Kick(UUID uuid, String reason) {

    public Kick {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(reason, "reason");
    }
}
