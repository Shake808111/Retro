package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Offene Party-Einladung ({@code party:invite:{target}} HASH {@code partyId → inviter;expiresMs}, 60 s; Spec 01 §2.3).
 * Zugleich Nutzlast von {@code PARTY_INVITE} (net:social).
 */
public record PartyInvite(String partyId, UUID inviter, UUID target, Instant expiresAt) {

    public PartyInvite {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(inviter, "inviter");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Wert im HASH: {@code inviter;expiresMs}. */
    public String hashValue() {
        return inviter + ";" + expiresAt.toEpochMilli();
    }

    public static PartyInvite fromHash(UUID target, String partyId, String value) {
        int separator = value.indexOf(';');
        if (separator < 0) {
            throw new IllegalArgumentException("Ungültige Einladung: " + value);
        }
        return new PartyInvite(partyId, UUID.fromString(value.substring(0, separator)), target,
                Instant.ofEpochMilli(Long.parseLong(value.substring(separator + 1))));
    }
}
