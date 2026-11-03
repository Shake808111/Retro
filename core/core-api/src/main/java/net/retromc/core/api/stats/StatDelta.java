package net.retromc.core.api.stats;

import java.util.Objects;
import java.util.UUID;

/**
 * Wirkung einer Runde auf einen Spieler (Spec 02 §6.4 Schritt 2–4, {@code StatsSubmittedEvent}).
 *
 * @param points berechnete oder vom Modul gelieferte Punkte
 * @param wins 0 oder 1
 * @param karma Karma-Änderung ({@code metric=karma}), sonst 0
 * @param bestTimeMillis Zeit dieser Runde ({@code metric=time}) oder 0
 */
public record StatDelta(UUID uuid, long points, int wins, long karma, long bestTimeMillis) {

    public StatDelta {
        Objects.requireNonNull(uuid, "uuid");
        if (wins < 0 || wins > 1 || bestTimeMillis < 0) {
            throw new IllegalArgumentException("Ungültige Rundenwerte");
        }
    }
}
