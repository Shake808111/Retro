package net.retromc.core.api.stats;

import java.util.Objects;
import java.util.UUID;

/** Aufstieg im globalen Level ({@code NetworkLevelService.addXp}, Spec 02 §8.1). */
public record LevelUp(UUID uuid, int oldLevel, int newLevel, long xp) {

    public LevelUp {
        Objects.requireNonNull(uuid, "uuid");
        if (newLevel <= oldLevel) {
            throw new IllegalArgumentException("Kein Aufstieg: " + oldLevel + " → " + newLevel);
        }
    }
}
