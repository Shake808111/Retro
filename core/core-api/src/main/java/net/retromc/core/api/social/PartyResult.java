package net.retromc.core.api.social;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis einer Party-Aktion.
 *
 * @param party Zustand nach der Aktion; {@code null} bei Fehlern ohne Party und nach Auflösung
 */
public record PartyResult(PartyOutcome outcome, @Nullable Party party) {

    public PartyResult {
        Objects.requireNonNull(outcome, "outcome");
    }

    public static PartyResult of(PartyOutcome outcome) {
        return new PartyResult(outcome, null);
    }
}
