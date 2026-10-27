package net.retromc.core.messaging.payload;

import java.util.List;

/** {@code CTL_REPLY {ok, lines[]}} (net:controller; Spec 08 §8.3): Controller → Velocity, Korrelation über {@code re}. */
public record CtlReply(boolean ok, List<CtlLine> lines) {

    public CtlReply {
        lines = List.copyOf(lines);
    }
}
