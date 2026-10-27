package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Beitrittsanfrage {@code social.clan_request} (Spec 01 §3.4 #44, GUI „Clan-Anfragen“). */
public record ClanJoinRequest(long clanId, UUID uuid, String name, Instant createdAt) {

    public ClanJoinRequest {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
