package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Einladung {@code social.clan_invite}, 7 Tage gültig (Spec 01 §3.4 #42). */
public record ClanInvite(long clanId, String clanName, String clanTag, UUID uuid, @Nullable UUID invitedBy,
        Instant createdAt) {

    public ClanInvite {
        Objects.requireNonNull(clanName, "clanName");
        Objects.requireNonNull(clanTag, "clanTag");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public boolean expired(Instant now) {
        return !now.isBefore(createdAt.plus(ClanRules.INVITE_TTL));
    }
}
