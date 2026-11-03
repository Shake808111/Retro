package net.retromc.core.data.stats;

import io.lettuce.core.Range;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.SetArgs;
import io.lettuce.core.ZAddArgs;
import io.lettuce.core.ZRange;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.retromc.core.api.event.LeaderboardChanged;
import net.retromc.core.api.event.StatsUpdated;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.stats.MatchResult;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.PlayerMatchStats;
import net.retromc.core.api.stats.ResetResult;
import net.retromc.core.api.stats.ResetScope;
import net.retromc.core.api.stats.StatDelta;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.StatsMode.Metric;
import net.retromc.core.api.stats.StatsStore;
import net.retromc.core.api.stats.SubmitResult;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.api.util.NetworkTime;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.core.stats.PointsFormula;
import net.retromc.core.stats.RankingScore;
import net.retromc.core.stats.RollingWindow;
import net.retromc.core.stats.StatKeys;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link StatsStore} auf PostgreSQL ({@code stats.*}, Migration V2_1/V2_2) und Valkey
 * ({@code lb:*}, {@code st:*}, {@code top:*}, {@code rl:stats:*}, {@code reset:confirm:*}, Sperren; Spec 02 §2.2).
 *
 * <ul>
 *   <li>Rundenende (§6.4): eine Transaktion, idempotent über {@code stats.match}; danach Bestenlisten, Caches und
 *       {@code STATS_UPDATED}. Fehler in Valkey werden nur gemeldet, der nächtliche Neuaufbau korrigiert.</li>
 *   <li>Fenster (§6.5): Summe der Tagesbuckets {@code day > heute − N} (Europe/Berlin, Uhr injiziert).</li>
 *   <li>Reset (§6.9): Token, Snapshot und Löschen in einer Transaktion; Bestenlisten danach.</li>
 * </ul>
 */
public final class StatsRepository implements StatsStore {

    private static final System.Logger LOG = System.getLogger(StatsRepository.class.getName());
    private static final DateTimeFormatter PARTITION = DateTimeFormatter.ofPattern("'daily_'yyyyMM");
    private static final int ZADD_BATCH = 500;
    /** Größe des Top-Caches (Spec 02 §2.2: Top 10). */
    private static final int TOP_CACHED = 10;
    private static final String COUNTER_COLUMNS = "kills, deaths, games, wins, points, extra::text AS extra";

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<RankService> ranks;
    private final EventSink events;
    private final Clock clock;
    private final AtomicReference<@Nullable CompletableFuture<List<StatsMode>>> modes = new AtomicReference<>();

    public StatsRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey,
            Supplier<RankService> ranks, EventSink events, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ------------------------------------------------------------------------------------------ Modus-Katalog

    @Override
    public CompletableFuture<List<StatsMode>> modes() {
        CompletableFuture<List<StatsMode>> current = modes.get();
        if (current != null && !current.isCompletedExceptionally()) {
            return current;
        }
        CompletableFuture<List<StatsMode>> loading = database.query(connection -> Jdbc.list(connection,
                "SELECT id, key, i18n_key, color, gradient, ranked, metric, points_formula::text AS formula,"
                        + " fields::text AS fields, resettable, active, icon FROM stats.mode ORDER BY id",
                StatsRepository::mode)).thenApply(List::copyOf);
        modes.set(loading);
        return loading;
    }

    /** Lädt den Katalog beim nächsten Zugriff neu (nach Änderungen an {@code stats.mode}). */
    public void reloadModes() {
        modes.set(null);
    }

    private static StatsMode mode(ResultSet row) throws SQLException {
        Map<String, Long> formula = StatsJson.counters(row.getString("formula"));
        return new StatsMode(row.getInt("id"), ModeKey.of(row.getString("key")), row.getString("i18n_key"),
                row.getString("color"), row.getString("gradient"), row.getBoolean("ranked"),
                Metric.byKey(row.getString("metric")).orElse(Metric.NONE), formula,
                StatsJson.strings(row.getString("fields")), row.getBoolean("resettable"), row.getBoolean("active"),
                row.getString("icon"));
    }

    private CompletableFuture<StatsMode> requireMode(ModeKey key) {
        return mode(key).thenApply(found -> found.orElseThrow(() -> new IllegalArgumentException(
                "Unbekannter Spielmodus: " + key)));
    }

    // ------------------------------------------------------------------------------------------ Rundenende

    @Override
    public CompletableFuture<SubmitResult> submit(MatchResult result) {
        return requireMode(result.mode()).thenCompose(mode -> {
            Map<UUID, StatDelta> deltas = new LinkedHashMap<>();
            for (PlayerMatchStats player : result.players()) {
                deltas.put(player.uuid(), new StatDelta(player.uuid(), PointsFormula.points(mode, player),
                        player.wins(), player.extra().getOrDefault(StatKeys.KARMA, 0L),
                        Math.max(0, player.extra().getOrDefault(StatKeys.BEST_TIME_MS, 0L))));
            }
            Instant now = clock.instant();
            LocalDate day = NetworkTime.day(now);
            return database.transaction(connection -> book(connection, mode, result, deltas, day, now))
                    .thenCompose(applied -> applied
                            ? afterSubmit(mode, deltas).thenApply(ignored -> new SubmitResult(true, deltas))
                            : CompletableFuture.completedFuture(SubmitResult.duplicate()));
        });
    }

    private boolean book(Connection connection, StatsMode mode, MatchResult result, Map<UUID, StatDelta> deltas,
            LocalDate day, Instant now) throws SQLException {
        int inserted = Jdbc.update(connection, "INSERT INTO stats.match (id, mode, variant, map, server, started_at,"
                + " ended_at, winner_team, mode_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                result.matchId(), mode.key().key(), result.variant(), result.map(), result.server(),
                result.startedAt(), result.endedAt(), result.winnerTeam(), (short) mode.id());
        if (inserted == 0) {
            return false;
        }
        for (PlayerMatchStats player : result.players()) {
            StatDelta delta = deltas.get(player.uuid());
            Jdbc.update(connection, "INSERT INTO stats.match_player (match_id, uuid, team, placement, kills, deaths,"
                    + " extra, wins, points, xp) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)", result.matchId(),
                    player.uuid(), player.team(), player.placement(), player.kills(), player.deaths(),
                    StatsJson.write(player.extra()), (short) player.wins(), Math.toIntExact(delta.points()),
                    player.xp());
            addCounters(connection, "stats.daily", "uuid = ? AND mode_id = ? AND day = ?", "",
                    new Object[] {player.uuid(), (short) mode.id(), day}, player, delta);
            addCounters(connection, "stats.alltime", "uuid = ? AND mode_id = ?",
                    ", first_game_at = COALESCE(first_game_at, ?), updated_at = ?",
                    new Object[] {player.uuid(), (short) mode.id()}, player, delta, now, now);
            Jdbc.update(connection, "INSERT INTO stats.map_alltime (uuid, mode_id, map, kills, deaths, games, wins,"
                    + " points) VALUES (?, ?, ?, ?, ?, 1, ?, ?) ON CONFLICT (uuid, mode_id, map) DO UPDATE SET"
                    + " kills = stats.map_alltime.kills + EXCLUDED.kills, deaths = stats.map_alltime.deaths"
                    + " + EXCLUDED.deaths, games = stats.map_alltime.games + 1, wins = stats.map_alltime.wins"
                    + " + EXCLUDED.wins, points = stats.map_alltime.points + EXCLUDED.points", player.uuid(),
                    (short) mode.id(), result.map(), player.kills(), player.deaths(), player.wins(), delta.points());
        }
        Jdbc.update(connection, "UPDATE stats.match SET applied_at = ? WHERE id = ?", now, result.matchId());
        return true;
    }

    /**
     * Addiert eine Runde auf eine Zählerzeile: anlegen falls nötig, Zeile sperren, {@code extra} nach
     * {@link StatKeys#merge} verdichten (Summe, Bestzeit = Minimum, Level-Rekord = Maximum).
     */
    private static void addCounters(Connection connection, String table, String where, String extraSet,
            Object[] key, PlayerMatchStats player, StatDelta delta, Object... extraArgs) throws SQLException {
        String columns = table.equals("stats.daily") ? "uuid, mode_id, day" : "uuid, mode_id";
        String marks = table.equals("stats.daily") ? "?, ?, ?" : "?, ?";
        Jdbc.update(connection, "INSERT INTO " + table + " (" + columns + ") VALUES (" + marks
                + ") ON CONFLICT DO NOTHING", key);
        String oldExtra = Jdbc.one(connection, "SELECT extra::text AS extra FROM " + table + " WHERE " + where
                + " FOR UPDATE", row -> row.getString("extra"), key).orElse("{}");
        Map<String, Long> extra = StatKeys.merge(StatsJson.counters(oldExtra), player.extra());
        List<Object> args = new ArrayList<>(List.of(player.kills(), player.deaths(), player.wins(), delta.points(),
                StatsJson.write(extra)));
        args.addAll(List.of(extraArgs));
        args.addAll(List.of(key));
        Jdbc.update(connection, "UPDATE " + table + " SET kills = kills + ?, deaths = deaths + ?, games = games + 1,"
                + " wins = wins + ?, points = points + ?, extra = ?::jsonb" + extraSet + " WHERE " + where,
                args.toArray());
    }

    /** §6.4 Schritt 4: Bestenlisten, Caches, {@code STATS_UPDATED}; Valkey-Fehler nur melden (Schritt 6). */
    private CompletableFuture<Void> afterSubmit(StatsMode mode, Map<UUID, StatDelta> deltas) {
        List<CompletableFuture<?>> steps = new ArrayList<>();
        for (StatDelta delta : deltas.values()) {
            if (mode.hasLeaderboard()) {
                OptionalDouble change = RankingScore.change(mode.metric(), delta);
                if (change.isPresent() && change.getAsDouble() != 0) {
                    for (StatPeriod period : StatPeriod.values()) {
                        String key = ValkeyKeys.leaderboard(mode.key(), period);
                        String member = delta.uuid().toString();
                        steps.add(RankingScore.additive(mode.metric())
                                ? Jdbc.future(valkey.zincrby(key, change.getAsDouble(), member))
                                : Jdbc.future(valkey.zadd(key, ZAddArgs.Builder.gt(), change.getAsDouble(), member)));
                    }
                }
            }
            steps.add(Jdbc.future(valkey.del(ValkeyKeys.statsCache(mode.key(), delta.uuid(), StatPeriod.DAYS_30),
                    ValkeyKeys.statsCache(mode.key(), delta.uuid(), StatPeriod.ALL))));
        }
        steps.add(Jdbc.future(valkey.del(ValkeyKeys.topCache(mode.key(), StatPeriod.DAYS_30),
                ValkeyKeys.topCache(mode.key(), StatPeriod.ALL))));
        return CompletableFuture.allOf(steps.toArray(CompletableFuture[]::new)).handle((ignored, error) -> {
            if (error != null) {
                LOG.log(System.Logger.Level.WARNING, "Valkey nach Rundenende " + mode.key()
                        + " fehlgeschlagen, Neuaufbau korrigiert nachts", Jdbc.cause(error));
            }
            events.publish(new StatsUpdated(mode.key(), List.copyOf(deltas.keySet())));
            return null;
        });
    }

    // ------------------------------------------------------------------------------------------ Abfragen

    @Override
    public CompletableFuture<StatLine> line(UUID player, ModeKey mode, StatWindow window) {
        Optional<StatPeriod> cached = window.period();
        if (cached.isEmpty()) {
            return loadLine(player, mode, window);
        }
        String key = ValkeyKeys.statsCache(mode, player, cached.get());
        return Jdbc.future(valkey.hgetall(key)).thenCompose(hash -> {
            if (hash != null && !hash.isEmpty()) {
                return CompletableFuture.completedFuture(fromHash(player, mode, hash));
            }
            return loadLine(player, mode, window).thenCompose(line -> Jdbc.future(valkey.hset(key, toHash(line)))
                    .thenCompose(ignored -> Jdbc.future(valkey.expire(key, ValkeyKeys.STATS_CACHE_TTL)))
                    .handle((ignored, error) -> line));
        });
    }

    private CompletableFuture<StatLine> loadLine(UUID player, ModeKey modeKey, StatWindow window) {
        return requireMode(modeKey).thenCompose(mode -> database.query(connection -> {
            if (window.isAll()) {
                return Jdbc.one(connection, "SELECT " + COUNTER_COLUMNS + " FROM stats.alltime WHERE uuid = ?"
                        + " AND mode_id = ?", row -> counters(player, modeKey, row), player, (short) mode.id())
                        .orElse(StatLine.empty(player, modeKey));
            }
            LocalDate today = NetworkTime.day(clock.instant());
            List<StatLine> days = Jdbc.list(connection, "SELECT " + COUNTER_COLUMNS + " FROM stats.daily"
                    + " WHERE uuid = ? AND mode_id = ? AND day > ? AND day <= ?",
                    row -> counters(player, modeKey, row), player, (short) mode.id(),
                    today.minusDays(window.days()), today);
            return days.stream().reduce(StatLine.empty(player, modeKey), StatKeys::add);
        }));
    }

    private static StatLine counters(UUID player, ModeKey mode, ResultSet row) throws SQLException {
        return new StatLine(player, mode, row.getLong("kills"), row.getLong("deaths"), row.getLong("games"),
                row.getLong("wins"), row.getLong("points"), StatsJson.counters(row.getString("extra")));
    }

    private static Map<String, String> toHash(StatLine line) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(StatLine.KILLS, Long.toString(line.kills()));
        hash.put(StatLine.DEATHS, Long.toString(line.deaths()));
        hash.put(StatLine.GAMES, Long.toString(line.games()));
        hash.put(StatLine.WINS, Long.toString(line.wins()));
        hash.put(StatLine.POINTS, Long.toString(line.points()));
        hash.put("extra", StatsJson.write(line.extra()));
        return hash;
    }

    private static StatLine fromHash(UUID player, ModeKey mode, Map<String, String> hash) {
        return new StatLine(player, mode, number(hash, StatLine.KILLS), number(hash, StatLine.DEATHS),
                number(hash, StatLine.GAMES), number(hash, StatLine.WINS), number(hash, StatLine.POINTS),
                StatsJson.counters(hash.get("extra")));
    }

    private static long number(Map<String, String> hash, String field) {
        String value = hash.get(field);
        return value == null ? 0 : Long.parseLong(value);
    }

    @Override
    public CompletableFuture<Map<String, StatLine>> mapLines(UUID player, ModeKey modeKey) {
        return requireMode(modeKey).thenCompose(mode -> database.query(connection -> {
            Map<String, StatLine> result = new LinkedHashMap<>();
            Jdbc.list(connection, "SELECT map, kills, deaths, games, wins, points FROM stats.map_alltime"
                    + " WHERE uuid = ? AND mode_id = ? ORDER BY map", row -> {
                        result.put(row.getString("map"), new StatLine(player, modeKey, row.getLong("kills"),
                                row.getLong("deaths"), row.getLong("games"), row.getLong("wins"),
                                row.getLong("points"), Map.of()));
                        return null;
                    }, player, (short) mode.id());
            return Map.copyOf(result);
        }));
    }

    @Override
    public CompletableFuture<OptionalLong> position(UUID player, ModeKey mode, StatPeriod period) {
        return Jdbc.future(valkey.zrevrank(ValkeyKeys.leaderboard(mode, period), player.toString()))
                .thenApply(rank -> rank == null ? OptionalLong.empty() : OptionalLong.of(rank + 1));
    }

    @Override
    public CompletableFuture<Optional<UUID>> byRank(ModeKey mode, StatPeriod period, int position) {
        if (position < 1) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return Jdbc.future(valkey.zrange(ValkeyKeys.leaderboard(mode, period),
                ZRange.byIndex(position - 1L, position - 1L).rev()))
                .thenApply(members -> members.isEmpty() ? Optional.empty()
                        : Optional.of(UUID.fromString(members.getFirst())));
    }

    @Override
    public CompletableFuture<List<TopEntry>> top(ModeKey mode, StatPeriod period, int count) {
        if (count < 1) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (count > TOP_CACHED) {
            return loadTop(mode, period, count);
        }
        String key = ValkeyKeys.topCache(mode, period);
        return Jdbc.future(valkey.get(key)).thenCompose(json -> {
            List<TopEntry> cached = json == null ? null : StatsJson.readTop(json);
            if (cached != null) {
                return CompletableFuture.completedFuture(List.copyOf(cached.subList(0, Math.min(count,
                        cached.size()))));
            }
            return loadTop(mode, period, TOP_CACHED).thenCompose(entries -> Jdbc.future(valkey.set(key,
                    StatsJson.writeTop(entries), SetArgs.Builder.ex(ValkeyKeys.TOP_CACHE_TTL)))
                    .handle((ignored, error) -> List.copyOf(entries.subList(0, Math.min(count, entries.size())))));
        });
    }

    private CompletableFuture<List<TopEntry>> loadTop(ModeKey mode, StatPeriod period, int count) {
        return Jdbc.future(valkey.zrangeWithScores(ValkeyKeys.leaderboard(mode, period),
                ZRange.byIndex(0, count - 1L).rev()))
                .thenCompose(scored -> {
                    List<UUID> uuids = scored.stream().map(value -> UUID.fromString(value.getValue())).toList();
                    if (uuids.isEmpty()) {
                        return CompletableFuture.completedFuture(List.of());
                    }
                    return names(uuids).thenCombine(ranks.get().ranks(uuids), (names, rankMap) -> {
                        List<TopEntry> entries = new ArrayList<>();
                        for (int i = 0; i < scored.size(); i++) {
                            UUID uuid = uuids.get(i);
                            RankInfo rank = rankMap.get(uuid);
                            entries.add(new TopEntry(i + 1, uuid, names.getOrDefault(uuid, "?"),
                                    rank == null ? "§a" : rank.legacyColor(), scored.get(i).getScore()));
                        }
                        return List.copyOf(entries);
                    });
                });
    }

    private CompletableFuture<Map<UUID, String>> names(List<UUID> uuids) {
        return database.query(connection -> {
            Map<UUID, String> names = new HashMap<>();
            Jdbc.list(connection, "SELECT uuid, name FROM core.player WHERE uuid = ANY (?)", row -> {
                names.put(Jdbc.requireUuid(row, "uuid"), row.getString("name"));
                return null;
            }, connection.createArrayOf("uuid", uuids.toArray()));
            return names;
        });
    }

    // ------------------------------------------------------------------------------------------ /togglestats

    @Override
    public CompletableFuture<Boolean> hidden(UUID player) {
        return database.query(connection -> Jdbc.one(connection, "SELECT hidden FROM stats.player_settings"
                + " WHERE uuid = ?", row -> row.getBoolean("hidden"), player).orElse(false));
    }

    @Override
    public CompletableFuture<Boolean> setHidden(UUID player, boolean hidden) {
        Instant now = clock.instant();
        return database.transaction(connection -> Jdbc.update(connection, "INSERT INTO stats.player_settings"
                + " (uuid, hidden, hidden_since) VALUES (?, ?, ?) ON CONFLICT (uuid) DO UPDATE SET hidden ="
                + " EXCLUDED.hidden, hidden_since = EXCLUDED.hidden_since", player, hidden, hidden ? now : null))
                .thenCompose(ignored -> Jdbc.future(valkey.exists(ValkeyKeys.profile(player))))
                .thenCompose(cached -> cached != null && cached > 0
                        ? Jdbc.future(valkey.hset(ValkeyKeys.profile(player), Profile.STATS_HIDDEN, hidden ? "1" : "0"))
                        : CompletableFuture.completedFuture(false))
                .thenApply(ignored -> hidden);
    }

    // ------------------------------------------------------------------------------------------ Ratenlimit, Dialog

    @Override
    public CompletableFuture<Boolean> tryQuery(UUID player) {
        String key = ValkeyKeys.statsRateLimit(player);
        return Jdbc.future(valkey.incr(key)).thenCompose(count -> {
            if (count == 1) {
                return Jdbc.future(valkey.pexpire(key, ValkeyKeys.STATS_RATE_LIMIT_TTL)).thenApply(ok -> true);
            }
            // Schutz gegen einen Zähler ohne Ablauf (PEXPIRE nach INCR verloren)
            return Jdbc.future(valkey.pttl(key)).thenCompose(ttl -> ttl != null && ttl == -1
                    ? Jdbc.future(valkey.pexpire(key, ValkeyKeys.STATS_RATE_LIMIT_TTL)).thenApply(ok -> false)
                    : CompletableFuture.completedFuture(false));
        });
    }

    @Override
    public CompletableFuture<Boolean> openReset(UUID player, ModeKey mode) {
        return Jdbc.future(valkey.set(ValkeyKeys.resetConfirm(player), mode.key(),
                SetArgs.Builder.nx().ex(ValkeyKeys.RESET_CONFIRM_TTL))).thenApply("OK"::equals);
    }

    @Override
    public CompletableFuture<Optional<ModeKey>> takeReset(UUID player) {
        return Jdbc.future(valkey.getdel(ValkeyKeys.resetConfirm(player)))
                .thenApply(value -> Optional.ofNullable(value).map(ModeKey::of));
    }

    // ------------------------------------------------------------------------------------------ Reset

    @Override
    public CompletableFuture<ResetResult> reset(UUID player, ModeKey modeKey, ResetScope scope, @Nullable UUID actor) {
        return mode(modeKey).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(ResetResult.UNKNOWN_MODE);
            }
            StatsMode mode = found.get();
            if (scope.consumesToken() && !mode.resettable()) {
                return CompletableFuture.completedFuture(ResetResult.NOT_RESETTABLE);
            }
            Instant now = clock.instant();
            return database.transaction(connection -> deleteStats(connection, mode, player, scope, actor, now))
                    .thenCompose(result -> result == ResetResult.DONE
                            ? afterReset(mode, player).thenApply(ignored -> result)
                            : CompletableFuture.completedFuture(result));
        });
    }

    private static ResetResult deleteStats(Connection connection, StatsMode mode, UUID player, ResetScope scope,
            @Nullable UUID actor, Instant now) throws SQLException {
        Long ledgerId = null;
        if (scope.consumesToken()) {
            int taken = Jdbc.update(connection, "UPDATE economy.wallet SET reset_tokens = reset_tokens - 1"
                    + " WHERE uuid = ? AND reset_tokens >= 1", player);
            if (taken == 0) {
                return ResetResult.NO_TOKEN;
            }
            ledgerId = Jdbc.one(connection, "INSERT INTO economy.ledger (uuid, delta, reason, ref, idem_key, currency,"
                    + " created_at) VALUES (?, -1, 'STATS_RESET', ?, ?, 'RESET_TOKEN', ?) RETURNING id",
                    row -> row.getLong("id"), player, mode.key().key(), "reset:" + player + ":" + mode.key()
                            + ":" + now.getEpochSecond() / 60, now).orElseThrow();
        }
        short modeId = (short) mode.id();
        Jdbc.update(connection, "INSERT INTO stats.reset_log (uuid, mode_id, scope, ledger_id, actor, snapshot,"
                + " created_at) SELECT ?, ?, ?, ?, ?, jsonb_build_object("
                + "'alltime', (SELECT to_jsonb(a) - 'uuid' - 'mode_id' FROM stats.alltime a"
                + " WHERE a.uuid = ? AND a.mode_id = ?),"
                + " 'daily', COALESCE((SELECT jsonb_agg(to_jsonb(d) - 'uuid' - 'mode_id' ORDER BY d.day)"
                + " FROM stats.daily d WHERE d.uuid = ? AND d.mode_id = ?), '[]'::jsonb),"
                + " 'maps', COALESCE((SELECT jsonb_agg(to_jsonb(m) - 'uuid' - 'mode_id' ORDER BY m.map)"
                + " FROM stats.map_alltime m WHERE m.uuid = ? AND m.mode_id = ?), '[]'::jsonb)), ?",
                player, modeId, scope.key(), ledgerId, actor, player, modeId, player, modeId, player, modeId, now);
        for (String table : List.of("stats.daily", "stats.alltime", "stats.map_alltime")) {
            Jdbc.update(connection, "DELETE FROM " + table + " WHERE uuid = ? AND mode_id = ?", player, modeId);
        }
        return ResetResult.DONE;
    }

    /** §6.9 Schritt 5: aus beiden Bestenlisten entfernen, Caches löschen. */
    private CompletableFuture<Void> afterReset(StatsMode mode, UUID player) {
        String member = player.toString();
        return CompletableFuture.allOf(
                Jdbc.future(valkey.zrem(ValkeyKeys.leaderboard(mode.key(), StatPeriod.DAYS_30), member)),
                Jdbc.future(valkey.zrem(ValkeyKeys.leaderboard(mode.key(), StatPeriod.ALL), member)),
                Jdbc.future(valkey.del(ValkeyKeys.statsCache(mode.key(), player, StatPeriod.DAYS_30),
                        ValkeyKeys.statsCache(mode.key(), player, StatPeriod.ALL),
                        ValkeyKeys.topCache(mode.key(), StatPeriod.DAYS_30),
                        ValkeyKeys.topCache(mode.key(), StatPeriod.ALL))));
    }

    // ------------------------------------------------------------------------------------------ Jobs

    @Override
    public CompletableFuture<Long> rebuild(ModeKey modeKey) {
        return requireMode(modeKey).thenCompose(mode -> {
            if (!mode.hasLeaderboard()) {
                return CompletableFuture.completedFuture(0L);
            }
            LocalDate today = NetworkTime.day(clock.instant());
            return rebuild(mode, StatPeriod.DAYS_30, today).thenCompose(ignored -> rebuild(mode, StatPeriod.ALL,
                    today));
        });
    }

    /** Baut {@code lb:{mode}:{period}:tmp} aus SQL und benennt ihn um (§6.5 Schritt 3). */
    private CompletableFuture<Long> rebuild(StatsMode mode, StatPeriod period, LocalDate today) {
        return database.query(connection -> scores(connection, mode, period, today)).thenCompose(scores -> {
            String target = ValkeyKeys.leaderboard(mode.key(), period);
            String tmp = ValkeyKeys.leaderboardTmp(mode.key(), period);
            CompletableFuture<?> chain = Jdbc.future(valkey.del(tmp));
            List<ScoredValue<String>> values = new ArrayList<>();
            scores.forEach((uuid, score) -> values.add(ScoredValue.just(score, uuid.toString())));
            for (int start = 0; start < values.size(); start += ZADD_BATCH) {
                List<ScoredValue<String>> batch = values.subList(start, Math.min(values.size(), start + ZADD_BATCH));
                @SuppressWarnings({"unchecked", "rawtypes"})
                ScoredValue<String>[] array = batch.toArray(new ScoredValue[0]);
                chain = chain.thenCompose(ignored -> Jdbc.future(valkey.zadd(tmp, array)));
            }
            if (values.isEmpty()) {
                chain = chain.thenCompose(ignored -> Jdbc.future(valkey.del(target)));
            } else {
                chain = chain.thenCompose(ignored -> Jdbc.future(valkey.expire(tmp, ValkeyKeys.LEADERBOARD_TMP_TTL)))
                        .thenCompose(ignored -> Jdbc.future(valkey.rename(tmp, target)));
            }
            return chain.thenCompose(ignored -> Jdbc.future(valkey.del(ValkeyKeys.topCache(mode.key(), period))))
                    .thenApply(ignored -> (long) values.size());
        });
    }

    /** Ranking-Scores aus SQL: 30 Tage = Summe der Tagesbuckets, gesamt = {@code stats.alltime}. */
    private static Map<UUID, Double> scores(Connection connection, StatsMode mode, StatPeriod period,
            LocalDate today) throws SQLException {
        List<StatLine> lines;
        if (period == StatPeriod.ALL) {
            lines = Jdbc.list(connection, "SELECT uuid, " + COUNTER_COLUMNS + " FROM stats.alltime WHERE mode_id = ?",
                    row -> counters(Jdbc.requireUuid(row, "uuid"), mode.key(), row), (short) mode.id());
        } else {
            Map<UUID, StatLine> sums = new HashMap<>();
            Jdbc.list(connection, "SELECT uuid, " + COUNTER_COLUMNS + " FROM stats.daily WHERE mode_id = ?"
                    + " AND day > ? AND day <= ?", row -> {
                        UUID uuid = Jdbc.requireUuid(row, "uuid");
                        sums.merge(uuid, counters(uuid, mode.key(), row), StatKeys::add);
                        return null;
                    }, (short) mode.id(), today.minusDays(StatPeriod.DAYS_30.days()), today);
            lines = List.copyOf(sums.values());
        }
        Map<UUID, Double> scores = new LinkedHashMap<>();
        for (StatLine line : lines) {
            OptionalDouble score = RankingScore.of(mode.metric(), line);
            if (score.isPresent() && (!RankingScore.additive(mode.metric()) || score.getAsDouble() > 0)) {
                scores.put(line.uuid(), score.getAsDouble());
            }
        }
        return scores;
    }

    @Override
    public CompletableFuture<Boolean> rollover(LocalDate today) {
        return lock(ValkeyKeys.statsRolloverLock(today), ValkeyKeys.ROLLOVER_LOCK_TTL.toMillis()).thenCompose(
                locked -> {
                    if (!locked) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return modes().thenCompose(all -> {
                        CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
                        for (StatsMode mode : all) {
                            if (mode.hasLeaderboard()) {
                                chain = chain.thenCompose(ignored -> rollover(mode, today));
                            }
                        }
                        return chain.thenApply(ignored -> true);
                    });
                });
    }

    /**
     * §6.5 Schritt 2: Tag {@code D = heute − 30} fällt heraus. Additive Metriken: {@code ZINCRBY −score} je Spieler
     * des Tages, danach {@code ZREMRANGEBYSCORE -inf 0}; Bestzeiten werden für die betroffenen Spieler neu
     * berechnet.
     */
    private CompletableFuture<Void> rollover(StatsMode mode, LocalDate today) {
        LocalDate day = RollingWindow.rolloverDay(today);
        String key = ValkeyKeys.leaderboard(mode.key(), StatPeriod.DAYS_30);
        return database.query(connection -> Jdbc.list(connection, "SELECT uuid, " + COUNTER_COLUMNS
                + " FROM stats.daily WHERE mode_id = ? AND day = ?",
                row -> counters(Jdbc.requireUuid(row, "uuid"), mode.key(), row), (short) mode.id(), day))
                .thenCompose(lines -> CompletableFuture.allOf(lines.stream().map(line -> Jdbc.future(valkey.del(
                        ValkeyKeys.statsCache(mode.key(), line.uuid(), StatPeriod.DAYS_30))))
                        .toArray(CompletableFuture[]::new)).thenApply(ignored -> lines))
                .thenCompose(lines -> {
                    if (RankingScore.additive(mode.metric())) {
                        List<CompletableFuture<?>> steps = new ArrayList<>();
                        for (StatLine line : lines) {
                            double score = RankingScore.of(mode.metric(), line).orElse(0);
                            if (score != 0) {
                                steps.add(Jdbc.future(valkey.zincrby(key, -score, line.uuid().toString())));
                            }
                        }
                        return CompletableFuture.allOf(steps.toArray(CompletableFuture[]::new))
                                .thenCompose(ignored -> Jdbc.future(valkey.zremrangebyscore(key,
                                        Range.from(Range.Boundary.unbounded(), Range.Boundary.including(0)))))
                                .thenApply(ignored -> (Void) null);
                    }
                    return database.query(connection -> recompute(connection, mode, lines, today))
                            .thenCompose(scores -> {
                                List<CompletableFuture<?>> steps = new ArrayList<>();
                                scores.forEach((uuid, score) -> steps.add(score.isPresent()
                                        ? Jdbc.future(valkey.zadd(key, score.getAsDouble(), uuid.toString()))
                                        : Jdbc.future(valkey.zrem(key, uuid.toString()))));
                                return CompletableFuture.allOf(steps.toArray(CompletableFuture[]::new));
                            });
                })
                .thenCompose(ignored -> Jdbc.future(valkey.del(ValkeyKeys.topCache(mode.key(), StatPeriod.DAYS_30))))
                .thenApply(ignored -> {
                    events.publish(new LeaderboardChanged(mode.key(), StatPeriod.DAYS_30));
                    return null;
                });
    }

    private static Map<UUID, OptionalDouble> recompute(Connection connection, StatsMode mode, List<StatLine> lines,
            LocalDate today) throws SQLException {
        Map<UUID, OptionalDouble> result = new LinkedHashMap<>();
        for (StatLine dropped : lines) {
            StatLine window = Jdbc.list(connection, "SELECT " + COUNTER_COLUMNS + " FROM stats.daily WHERE uuid = ?"
                    + " AND mode_id = ? AND day > ? AND day <= ?", row -> counters(dropped.uuid(), mode.key(), row),
                    dropped.uuid(), (short) mode.id(), today.minusDays(StatPeriod.DAYS_30.days()), today).stream()
                    .reduce(StatLine.empty(dropped.uuid(), mode.key()), StatKeys::add);
            result.put(dropped.uuid(), RankingScore.of(mode.metric(), window));
        }
        return result;
    }

    @Override
    public CompletableFuture<Boolean> reconcile(LocalDate today) {
        return lock(ValkeyKeys.statsReconcileLock(today), ValkeyKeys.RECONCILE_LOCK_TTL.toMillis()).thenCompose(
                locked -> {
                    if (!locked) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return modes().thenCompose(all -> {
                        CompletableFuture<?> chain = CompletableFuture.completedFuture(null);
                        for (StatsMode mode : all) {
                            if (!mode.hasLeaderboard()) {
                                continue;
                            }
                            for (StatPeriod period : StatPeriod.values()) {
                                chain = chain.thenCompose(ignored -> rebuild(mode, period, today)).thenApply(
                                        ignored -> {
                                            events.publish(new LeaderboardChanged(mode.key(), period));
                                            return null;
                                        });
                            }
                        }
                        return chain.thenApply(ignored -> true);
                    });
                });
    }

    private CompletableFuture<Boolean> lock(String key, long millis) {
        return Jdbc.future(valkey.set(key, "1", SetArgs.Builder.nx().px(millis))).thenApply("OK"::equals);
    }

    @Override
    public CompletableFuture<Void> housekeeping(LocalDate today) {
        return database.transaction(connection -> {
            for (YearMonth month : RollingWindow.partitionsToCreate(today)) {
                Jdbc.one(connection, "SELECT stats.create_daily_partition(?)", row -> row.getString(1),
                        month.atDay(1));
            }
            List<String> partitions = Jdbc.list(connection, "SELECT c.relname FROM pg_inherits i"
                    + " JOIN pg_class c ON c.oid = i.inhrelid JOIN pg_class p ON p.oid = i.inhparent"
                    + " JOIN pg_namespace n ON n.oid = p.relnamespace WHERE n.nspname = 'stats'"
                    + " AND p.relname = 'daily'", row -> row.getString(1));
            for (String partition : partitions) {
                Optional<YearMonth> month = partitionMonth(partition);
                if (month.isPresent() && RollingWindow.partitionExpired(month.get(), today)) {
                    Jdbc.update(connection, "DROP TABLE stats." + partition);
                }
            }
            Jdbc.update(connection, "DELETE FROM stats.match_player mp USING stats.match m WHERE mp.match_id = m.id"
                    + " AND m.ended_at < ?", NetworkTime.startOfDay(today.minusDays(
                    RollingWindow.MATCH_RETENTION_DAYS)));
            return null;
        });
    }

    /** Monat einer Partition {@code daily_YYYYMM}; andere Namen werden nie gelöscht. */
    static Optional<YearMonth> partitionMonth(String name) {
        try {
            return Optional.of(YearMonth.parse(name, PARTITION));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
