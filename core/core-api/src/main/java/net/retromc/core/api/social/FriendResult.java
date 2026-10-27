package net.retromc.core.api.social;

import java.util.Objects;

/**
 * Ergebnis von {@code request/accept/deny/remove}.
 *
 * @param limit bei {@link FriendOutcome#LIMIT_SELF}/{@link FriendOutcome#LIMIT_OTHER} das betroffene Limit, sonst 0
 */
public record FriendResult(FriendOutcome outcome, int limit) {

    public FriendResult {
        Objects.requireNonNull(outcome, "outcome");
        if (limit < 0) {
            throw new IllegalArgumentException("limit darf nicht negativ sein");
        }
    }

    public static FriendResult of(FriendOutcome outcome) {
        return new FriendResult(outcome, 0);
    }

    public boolean success() {
        return outcome == FriendOutcome.SENT || outcome == FriendOutcome.ADDED
                || outcome == FriendOutcome.DENIED || outcome == FriendOutcome.REMOVED;
    }
}
