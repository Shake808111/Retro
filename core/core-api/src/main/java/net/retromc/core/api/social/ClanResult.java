package net.retromc.core.api.social;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis einer Clan-Aktion.
 *
 * @param clan Clan nach der Aktion (bei {@link ClanOutcome#DELETED} der gelöschte Stand), sonst {@code null}
 */
public record ClanResult(ClanOutcome outcome, @Nullable Clan clan) {

    public ClanResult {
        Objects.requireNonNull(outcome, "outcome");
    }

    public static ClanResult of(ClanOutcome outcome) {
        return new ClanResult(outcome, null);
    }
}
