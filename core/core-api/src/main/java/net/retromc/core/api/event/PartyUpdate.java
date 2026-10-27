package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.msg.JsonName;

/**
 * Party geändert ({@code PARTY_UPDATE}, net:social; Spec 01 §8.2): Velocity → Controller (Queue), Spiel-Rahmen.
 * Das JSON-Feld heißt laut Spec {@code public}.
 */
public record PartyUpdate(String partyId, UUID leader, List<UUID> members, @JsonName("public") boolean publicParty) {

    public PartyUpdate {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(leader, "leader");
        members = List.copyOf(members);
    }
}
