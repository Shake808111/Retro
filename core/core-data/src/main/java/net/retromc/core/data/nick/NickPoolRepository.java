package net.retromc.core.data.nick;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.social.NickPoolService;
import net.retromc.core.api.util.PlayerName;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.core.data.punish.SpamFilter;

/**
 * {@link NickPoolService} über {@code core.nick_pool}, {@code core.nick_skin} (Migration V1_3) und Valkey
 * ({@code nick:name:{lower}} = vergeben). Füllvorgänge laufen einzeln auf {@code executor} ({@link NickPoolFiller});
 * HTTP geht über die übergebene {@link HttpTransport}, damit Tests ohne Netz laufen.
 */
public final class NickPoolRepository implements NickPoolService, NickPoolFiller.Store {

    /** User-Agent der Abrufe (eigene Kennung, kein fremder Browser). */
    public static final String USER_AGENT = BuildInfo.BRAND_NAME + "-NickPool/" + BuildInfo.VERSION;

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final Predicate<String> onlineName;
    private final Executor executor;
    private final NickPoolFiller filler;
    private final AtomicBoolean filling = new AtomicBoolean();

    /**
     * @param onlineName ob ein echter Spieler dieses Namens online ist
     * @param sleeper Wartefunktion der Rate-Limit-Schonung (produktiv {@link Thread#sleep(java.time.Duration)})
     * @param executor Thread für Füllvorgänge (blockierend, nie ein Server-Hauptthread)
     */
    public NickPoolRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey,
            Predicate<String> onlineName, NickPoolSettings settings, HttpTransport http,
            RequestPacer.Sleeper sleeper, Clock clock, RandomGenerator random, Executor executor) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.onlineName = Objects.requireNonNull(onlineName, "onlineName");
        this.executor = Objects.requireNonNull(executor, "executor");
        MojangApi mojang = new MojangApi(http, new RequestPacer(settings.mojangInterval(), clock, sleeper),
                USER_AGENT);
        String key = settings.mineSkinKey();
        MineSkinApi mineSkin = key == null ? null : new MineSkinApi(http,
                new RequestPacer(NickPoolSettings.MINESKIN_INTERVAL, clock, sleeper), key, USER_AGENT);
        this.filler = new NickPoolFiller(this, new NickNameGenerator(random),
                new NickNameFilter(new SpamFilter(Set.of()), settings.blockedWords()), onlineName, mojang, mineSkin,
                settings, random);
    }

    // ------------------------------------------------------------------ NickPoolService

    @Override
    public CompletableFuture<Fill> fill(int count) {
        if (count < 1 || count > MAX_FILL) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Anzahl 1–" + MAX_FILL + ": "
                    + count));
        }
        if (!filling.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(Fill.of(FillStatus.BUSY, count));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return filler.fill(count);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                } finally {
                    filling.set(false);
                }
            }, executor);
        } catch (RuntimeException e) {
            filling.set(false);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<List<Entry>> list(int offset, int limit) {
        return database.query(connection -> Jdbc.list(connection, "SELECT name, enabled, source, created_at"
                + " FROM core.nick_pool ORDER BY lower(name) OFFSET ? LIMIT ?", row -> new Row(row.getString("name"),
                        row.getBoolean("enabled"), row.getString("source"),
                        Objects.requireNonNull(Jdbc.instant(row, "created_at"))), Math.max(0, offset),
                Math.max(0, limit)))
                .thenCompose(rows -> inUse(rows.stream().map(Row::name).toList()).thenApply(used -> rows.stream()
                        .map(row -> new Entry(row.name(), !row.enabled() ? State.DISABLED
                                : used.contains(row.name()) ? State.IN_USE : State.FREE, row.source(), row.created()))
                        .toList()));
    }

    /** Zeile aus {@code core.nick_pool}. */
    private record Row(String name, boolean enabled, String source, Instant created) {
    }

    @Override
    public CompletableFuture<Boolean> disable(String name) {
        if (!PlayerName.valid(name)) {
            return CompletableFuture.completedFuture(false);
        }
        return database.transaction(connection -> Jdbc.update(connection, "UPDATE core.nick_pool SET enabled = false"
                + " WHERE lower(name) = lower(?)", name) > 0);
    }

    @Override
    public CompletableFuture<Stats> stats() {
        return database.query(connection -> new Counts(Jdbc.count(connection, "SELECT count(*) FROM core.nick_pool"),
                Jdbc.count(connection, "SELECT count(*) FROM core.nick_skin WHERE enabled"),
                Jdbc.list(connection, "SELECT name FROM core.nick_pool WHERE enabled", row -> row.getString("name"))))
                .thenCompose(counts -> inUse(counts.enabled()).thenApply(used -> {
                    int free = 0;
                    for (String name : counts.enabled()) {
                        if (!used.contains(name) && !onlineName.test(name)) {
                            free++;
                        }
                    }
                    return new Stats((int) counts.total(), counts.enabled().size(), free, used.size(),
                            (int) counts.skins());
                }));
    }

    /** Zähler für {@link #stats()}. */
    private record Counts(long total, long skins, List<String> enabled) {
    }

    /** Namen mit {@code nick:name:{lower}} (gerade vergeben). */
    private CompletableFuture<Set<String>> inUse(List<String> names) {
        List<CompletableFuture<Long>> checks = names.stream()
                .map(name -> Jdbc.future(valkey.exists(ValkeyKeys.nickName(name)))).toList();
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Set<String> used = new HashSet<>();
            for (int i = 0; i < names.size(); i++) {
                if (checks.get(i).join() > 0) {
                    used.add(names.get(i));
                }
            }
            return Set.copyOf(used);
        });
    }

    // ------------------------------------------------------------------ Store (Füllvorgang, blockierend)

    @Override
    public Set<String> skinRefs(String source) {
        return Set.copyOf(database.query(connection -> Jdbc.list(connection, "SELECT source_ref FROM core.nick_skin"
                + " WHERE source = ?", row -> row.getString("source_ref"), source)).join());
    }

    @Override
    public void addSkins(String source, List<SkinTexture> skins) {
        database.transaction(connection -> {
            for (SkinTexture skin : skins) {
                Jdbc.update(connection, "INSERT INTO core.nick_skin (source, source_ref, texture_value,"
                        + " texture_signature) VALUES (?, ?, ?, ?) ON CONFLICT (source, source_ref) DO NOTHING",
                        source, skin.sourceRef(), skin.value(), skin.signature());
            }
            return null;
        }).join();
    }

    @Override
    public List<NickPoolFiller.StoredSkin> enabledSkins() {
        return database.query(connection -> Jdbc.list(connection, "SELECT id, texture_value, texture_signature"
                + " FROM core.nick_skin WHERE enabled ORDER BY id", row -> new NickPoolFiller.StoredSkin(
                        row.getLong("id"), row.getString("texture_value"), row.getString("texture_signature"))))
                .join();
    }

    @Override
    public Set<String> knownNames(Collection<String> lowerNames) {
        if (lowerNames.isEmpty()) {
            return Set.of();
        }
        List<String> names = new ArrayList<>(lowerNames);
        return Set.copyOf(database.query(connection -> {
            Array wanted = connection.createArrayOf("text", names.toArray());
            return Jdbc.list(connection, "SELECT name_lower AS known FROM core.player WHERE name_lower = ANY (?)"
                    + " UNION SELECT lower(name) FROM core.player_name_history WHERE lower(name) = ANY (?)"
                    + " UNION SELECT lower(name) FROM core.nick_pool WHERE lower(name) = ANY (?)",
                    row -> row.getString("known"), wanted, wanted, wanted);
        }).join());
    }

    @Override
    public boolean addName(String name, NickPoolFiller.StoredSkin skin) {
        return database.transaction(connection -> insert(connection, name, skin)).join();
    }

    private static boolean insert(Connection connection, String name, NickPoolFiller.StoredSkin skin)
            throws SQLException {
        return Jdbc.update(connection, "INSERT INTO core.nick_pool (name, texture_value, texture_signature, skin_id,"
                + " source) VALUES (?, ?, ?, ?, 'GENERATED') ON CONFLICT DO NOTHING", name, skin.value(),
                skin.signature(), skin.id()) == 1;
    }

    /** Laufender Füllvorgang? */
    public boolean filling() {
        return filling.get();
    }
}
