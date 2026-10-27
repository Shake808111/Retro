package net.retromc.core.api.event;

import java.util.Objects;

/** Leiter zieht die Party auf einen Server ({@code PARTY_WARP}, net:social; Spec 01 §8.2): Velocity → Controller. */
public record PartyWarp(String partyId, String server) {

    public PartyWarp {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(server, "server");
    }
}
