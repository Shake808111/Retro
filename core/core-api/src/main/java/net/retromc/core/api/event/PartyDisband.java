package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Party aufgelöst ({@code PARTY_DISBAND}, net:social; Spec 01 §2.4) mit den bisherigen Mitgliedern. */
public record PartyDisband(String partyId, List<UUID> members) {

    public PartyDisband {
        Objects.requireNonNull(partyId, "partyId");
        members = List.copyOf(members);
    }
}
