package net.retromc.game.framework.end;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Ruft eine Buchung genau einmal je {@code matchId} auf (Spec 03 §9 #16: {@code StatsService.submit} auch bei
 * doppeltem {@code finish} nur einmal). Weitere Aufrufe erhalten dieselbe Zukunft.
 *
 * @param <T> Ergebnis der Buchung
 */
public final class SubmitOnce<T> {

    private final Map<UUID, CompletableFuture<T>> calls = new ConcurrentHashMap<>();

    public CompletableFuture<T> run(UUID matchId, Supplier<CompletableFuture<T>> action) {
        return calls.computeIfAbsent(matchId, id -> {
            try {
                return action.get();
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    /** {@code true}, wenn für {@code matchId} schon gebucht wurde. */
    public boolean done(UUID matchId) {
        return calls.containsKey(matchId);
    }
}
