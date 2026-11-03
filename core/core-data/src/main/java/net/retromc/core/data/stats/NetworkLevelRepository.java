package net.retromc.core.data.stats;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.stats.LevelUp;
import net.retromc.core.api.stats.NetworkLevelService;
import net.retromc.core.api.util.LevelCurve;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;

/**
 * Standard-{@link NetworkLevelService} auf {@code stats.network_level} (Spec 02 §2.1, §8.1): XP addieren und Level
 * nach {@link LevelCurve} ({@code 250·n·(n+1)}) in derselben Transaktion nachziehen. Ein Aufstieg ändert das
 * Profilfeld {@code level} und wird als {@code PROFILE_INVALIDATE} mit Feld {@code level} gemeldet.
 */
public final class NetworkLevelRepository implements NetworkLevelService {

    private final AsyncDatabase database;
    private final EventSink events;
    private final Clock clock;

    public NetworkLevelRepository(AsyncDatabase database, EventSink events, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<LevelCurve.Progress> level(UUID player) {
        return database.query(connection -> LevelCurve.progress(Jdbc.one(connection,
                "SELECT xp FROM stats.network_level WHERE uuid = ?", row -> row.getLong("xp"), player).orElse(0L)));
    }

    @Override
    public CompletableFuture<Optional<LevelUp>> addXp(UUID player, String source, long xp) {
        Objects.requireNonNull(source, "source");
        if (xp < 0) {
            throw new IllegalArgumentException("XP dürfen nicht negativ sein: " + xp);
        }
        return database.transaction(connection -> {
            Jdbc.update(connection, "INSERT INTO stats.network_level (uuid) VALUES (?) ON CONFLICT DO NOTHING",
                    player);
            long old = Jdbc.one(connection, "SELECT xp FROM stats.network_level WHERE uuid = ? FOR UPDATE",
                    row -> row.getLong("xp"), player).orElse(0L);
            long total = Math.addExact(old, xp);
            int oldLevel = LevelCurve.levelForXp(old);
            int newLevel = LevelCurve.levelForXp(total);
            Jdbc.update(connection, "UPDATE stats.network_level SET xp = ?, level = ?, updated_at = ? WHERE uuid = ?",
                    total, newLevel, clock.instant(), player);
            return newLevel > oldLevel ? Optional.of(new LevelUp(player, oldLevel, newLevel, total))
                    : Optional.<LevelUp>empty();
        }).thenApply(levelUp -> {
            levelUp.ifPresent(up -> events.publish(new ProfileInvalidate(player, List.of(Profile.LEVEL))));
            return levelUp;
        });
    }
}
