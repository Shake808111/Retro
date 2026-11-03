package net.retromc.core.data.social;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Database;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.data.testing.TestDatabases;
import net.retromc.core.data.testing.TestTransactions;

/**
 * Gemeinsame Umgebung der Repository-Tests: migrierte Testdatenbank (PostgreSQL 18.6), Valkey 8.1.10, Ränge zum
 * Einstellen, aufzeichnende Ereignis-Senke und verstellbare Uhr. Schlüssel hängen an Zufalls-UUIDs, daher kein
 * {@code FLUSHALL} nötig.
 */
public final class DataFixture implements AutoCloseable {

    public final Database database = TestDatabases.migratedDatabase();
    public final AsyncDatabase async = new AsyncDatabase(database.dataSource());
    private final ValkeyConnector connector = TestDatabases.valkeyConnector();
    private final StatefulRedisConnection<String, String> connection = connector.connect();
    public final RedisAsyncCommands<String, String> valkey = connection.async();
    public final Ranks ranks = new Ranks();
    public final Events events = new Events();
    public final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T10:00:00Z"));

    /** Spieler in {@code core.player} anlegen. */
    public UUID player(String name) {
        UUID uuid = UUID.randomUUID();
        TestTransactions.committed(database.dataSource(), connection -> Jdbc.update(connection,
                "INSERT INTO core.player (uuid, name, name_lower) VALUES (?, ?, lower(?))", uuid, name, name));
        return uuid;
    }

    @Override
    public void close() {
        connection.close();
        connector.close();
        async.close();
        database.close();
    }

    /** Ränge je Spieler, Standard {@link Rank#DEFAULT}. */
    public static final class Ranks implements RankService {

        private final Map<UUID, Rank> ranks = new ConcurrentHashMap<>();

        public void set(UUID player, Rank rank) {
            ranks.put(player, rank);
        }

        @Override
        public CompletableFuture<RankInfo> rank(UUID player) {
            return CompletableFuture.completedFuture(RankInfo.of(ranks.getOrDefault(player, Rank.DEFAULT)));
        }

        @Override
        public CompletableFuture<Map<UUID, RankInfo>> ranks(Collection<UUID> players) {
            return CompletableFuture.completedFuture(players.stream().distinct().collect(Collectors.toMap(
                    Function.identity(), player -> RankInfo.of(ranks.getOrDefault(player, Rank.DEFAULT)))));
        }
    }

    /** Zeichnet alle Ereignisse auf. */
    public static final class Events implements EventSink {

        public final List<Object> published = new CopyOnWriteArrayList<>();

        @Override
        public void publish(Object event) {
            published.add(event);
        }

        public <T> List<T> of(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    /** Uhr zum Vorstellen. */
    public static final class MutableClock extends Clock {

        private volatile Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void set(Instant instant) {
            now = instant;
        }

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
