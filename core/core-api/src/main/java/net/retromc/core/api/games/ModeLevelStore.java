package net.retromc.core.api.games;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Modus-Level ({@code games.mode_level}, Spec 04 §2.1, §8.2 {@code ModeLevelService}): XP je Modus, Level nach der
 * Kurve aus Spec 02 ({@code LevelCurve}).
 */
public interface ModeLevelStore {

    /**
     * Stand nach einer Buchung.
     *
     * @param previousLevel Level vor der Buchung (gleich {@code level}, wenn kein Aufstieg)
     */
    record ModeLevel(UUID player, String mode, long xp, int level, int previousLevel) {

        public ModeLevel {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(mode, "mode");
        }

        public boolean levelUp() {
            return level > previousLevel;
        }
    }

    CompletableFuture<ModeLevel> level(UUID player, String mode);

    /** Addiert XP (≥ 0) und zieht das Level nach. */
    CompletableFuture<ModeLevel> addXp(UUID player, String mode, long xp);
}
