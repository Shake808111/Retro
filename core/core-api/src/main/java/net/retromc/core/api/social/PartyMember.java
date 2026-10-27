package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mitglied mit Beitrittszeit ({@code party:{id}:members} ZSET uuid → ms; Reihenfolge = Nachfolge). */
public record PartyMember(UUID uuid, Instant joinedAt) {

    public PartyMember {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
