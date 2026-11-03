package net.retromc.core.api.stats;

import java.util.Map;
import java.util.UUID;

/**
 * Ergebnis von {@code StatsStore.submit} (Spec 02 §6.4).
 *
 * @param applied {@code false}, wenn die {@code matchId} schon verbucht war (idempotent, nichts geändert)
 * @param deltas Wirkung je Spieler (leer, wenn nicht verbucht)
 */
public record SubmitResult(boolean applied, Map<UUID, StatDelta> deltas) {

    public SubmitResult {
        deltas = Map.copyOf(deltas);
    }

    public static SubmitResult duplicate() {
        return new SubmitResult(false, Map.of());
    }
}
