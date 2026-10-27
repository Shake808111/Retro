package net.retromc.core.api.social;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis von {@code /friend jump} (Spec 01 §3.1 #11): Ziel-Server oder Fehler {@code friend.jump.err.*}.
 *
 * @param server Ziel-Server bei {@link Outcome#OK}, sonst {@code null}
 */
public record JumpResult(Outcome outcome, @Nullable String server) {

    /** Mögliche Ergebnisse. */
    public enum Outcome {
        OK,
        /** {@code jump_from} des Ziels verbietet es ({@code friend.jump.err.denied}). */
        DENIED,
        /** Ziel offline oder versteckt ({@code friend.jump.err.offline}). */
        OFFLINE,
        /** Bereits auf dem Server ({@code friend.jump.err.same}). */
        SAME_SERVER,
        /** Nicht befreundet. */
        NOT_FRIENDS
    }

    public JumpResult {
        Objects.requireNonNull(outcome, "outcome");
        if ((outcome == Outcome.OK) != (server != null)) {
            throw new IllegalArgumentException("server genau bei OK angeben");
        }
    }
}
