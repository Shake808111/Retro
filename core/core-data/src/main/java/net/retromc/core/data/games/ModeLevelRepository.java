package net.retromc.core.data.games;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.games.ModeLevelStore;
import net.retromc.core.api.util.LevelCurve;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/** Standard-{@link ModeLevelStore} auf {@code games.mode_level}; Level nach {@link LevelCurve} (Spec 02). */
public final class ModeLevelRepository implements ModeLevelStore {

    private final AsyncDatabase database;
    private final Clock clock;

    public ModeLevelRepository(AsyncDatabase database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<ModeLevel> level(UUID player, String mode) {
        return database.query(connection -> {
            long xp = Jdbc.one(connection, "SELECT xp FROM games.mode_level WHERE uuid = ? AND mode = ?",
                    row -> row.getLong("xp"), player, mode).orElse(0L);
            int level = LevelCurve.levelForXp(xp);
            return new ModeLevel(player, mode, xp, level, level);
        });
    }

    @Override
    public CompletableFuture<ModeLevel> addXp(UUID player, String mode, long xp) {
        Objects.requireNonNull(mode, "mode");
        if (xp < 0) {
            throw new IllegalArgumentException("XP dürfen nicht negativ sein: " + xp);
        }
        return database.transaction(connection -> {
            Jdbc.update(connection, "INSERT INTO games.mode_level (uuid, mode) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    player, mode);
            long old = Jdbc.one(connection, "SELECT xp FROM games.mode_level WHERE uuid = ? AND mode = ? FOR UPDATE",
                    row -> row.getLong("xp"), player, mode).orElse(0L);
            long total = Math.addExact(old, xp);
            int oldLevel = LevelCurve.levelForXp(old);
            int newLevel = LevelCurve.levelForXp(total);
            Jdbc.update(connection, "UPDATE games.mode_level SET xp = ?, level = ?, updated_at = ? "
                    + "WHERE uuid = ? AND mode = ?", total, newLevel, clock.instant(), player, mode);
            return new ModeLevel(player, mode, total, newLevel, oldLevel);
        });
    }
}
