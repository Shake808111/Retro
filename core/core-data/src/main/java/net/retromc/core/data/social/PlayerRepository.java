package net.retromc.core.data.social;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.Nick;
import net.retromc.core.api.social.PlayerLogin;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link ProfileService} auf {@code core.player} (Spec 01 §2.1, §6.3 Nr. 4/7). Ein bestehendes Kern-Plugin
 * ersetzt ihn per Adapter. Profile werden aus Datenbank, Rang ({@link RankService}) und Clan zusammengesetzt; die
 * Spec-02-Felder kommen aus {@code stats.network_level} (Level), {@code economy.wallet} (Coins, Gold) und
 * {@code stats.player_settings} (Stats verborgen), fehlende Zeilen gelten als 0 bzw. {@code false}.
 * Namensauflösung über {@code name:{lower}} (24 h).
 */
public final class PlayerRepository implements ProfileService {

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<RankService> ranks;
    private final EventSink events;

    public PlayerRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey,
            Supplier<RankService> ranks, EventSink events) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.events = Objects.requireNonNull(events, "events");
    }

    /**
     * Spalten aus {@code core.player}, Clan und den Spec-02-Feldern ({@code economy.wallet.coins/gold},
     * {@code stats.network_level.level}, {@code stats.player_settings.hidden}).
     */
    private record Row(UUID uuid, String name, String locale, long playtime, @Nullable String clanTag,
            @Nullable Long clanId, int level, long coins, long gold, boolean statsHidden) {
    }

    @Override
    public CompletableFuture<Optional<Profile>> profile(UUID player) {
        return profiles(List.of(player)).thenApply(map -> Optional.ofNullable(map.get(player)));
    }

    @Override
    public CompletableFuture<Map<UUID, Profile>> profiles(Collection<UUID> players) {
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        UUID[] ids = players.stream().distinct().toArray(UUID[]::new);
        return database.query(connection -> Jdbc.list(connection, "SELECT p.uuid, p.name, p.locale, p.playtime_s,"
                + " c.tag, c.id AS clan_id, COALESCE(l.level, 0) AS level, COALESCE(w.coins, 0) AS coins,"
                + " COALESCE(w.gold, 0) AS gold, COALESCE(s.hidden, false) AS stats_hidden"
                + " FROM core.player p LEFT JOIN social.clan_member m ON m.uuid = p.uuid"
                + " LEFT JOIN social.clan c ON c.id = m.clan_id LEFT JOIN economy.wallet w ON w.uuid = p.uuid"
                + " LEFT JOIN stats.network_level l ON l.uuid = p.uuid"
                + " LEFT JOIN stats.player_settings s ON s.uuid = p.uuid WHERE p.uuid = ANY (?)", row -> {
                    long clanId = row.getLong("clan_id");
                    boolean noClan = row.wasNull();
                    return new Row(Jdbc.requireUuid(row, "uuid"), row.getString("name"), row.getString("locale"),
                            row.getLong("playtime_s"), row.getString("tag"), noClan ? null : clanId,
                            row.getInt("level"), row.getLong("coins"), row.getLong("gold"),
                            row.getBoolean("stats_hidden"));
                }, connection.createArrayOf("uuid", ids)))
                .thenCompose(rows -> ranks.get().ranks(rows.stream().map(Row::uuid).toList())
                        .thenCombine(nickNames(rows), (rankMap, nicks) -> {
                    Map<UUID, Profile> result = new HashMap<>();
                    for (Row row : rows) {
                        RankInfo rank = rankMap.get(row.uuid());
                        result.put(row.uuid(), new Profile(row.uuid(), row.name(),
                                rank == null ? "default" : rank.id(), rank == null ? "§a" : rank.legacyColor(),
                                rank == null ? null : rank.prefix(), row.locale(), row.clanTag(), row.clanId(),
                                row.level(), nicks.get(row.uuid()), row.coins(), row.gold(), row.playtime(),
                                row.statsHidden()));
                    }
                    return Map.copyOf(result);
                }));
    }

    /** Aktive Nicks ({@code nick:{uuid}.name}) für {@code profile.nickName}. */
    private CompletableFuture<Map<UUID, String>> nickNames(List<Row> rows) {
        List<CompletableFuture<String>> names = rows.stream()
                .map(row -> Jdbc.future(valkey.hget(ValkeyKeys.nick(row.uuid()), Nick.NAME))).toList();
        return CompletableFuture.allOf(names.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Map<UUID, String> result = new HashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                String nick = names.get(i).join();
                if (nick != null) {
                    result.put(rows.get(i).uuid(), nick);
                }
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Optional<UUID>> uuidOf(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String key = ValkeyKeys.playerName(lower);
        return Jdbc.future(valkey.get(key)).thenCompose(cached -> {
            if (cached != null) {
                return CompletableFuture.completedFuture(Optional.of(UUID.fromString(cached)));
            }
            return database.query(connection -> Jdbc.one(connection,
                    "SELECT uuid FROM core.player WHERE name_lower = ?", row -> Jdbc.requireUuid(row, "uuid"), lower))
                    .thenCompose(found -> found.isEmpty()
                            ? CompletableFuture.completedFuture(found)
                            : Jdbc.future(valkey.set(key, found.get().toString(),
                                    SetArgs.Builder.ex(ValkeyKeys.NAME_TTL.toSeconds())))
                                    .thenApply(ok -> found));
        });
    }

    @Override
    public CompletableFuture<Optional<String>> nameOf(UUID player) {
        return database.query(connection -> Jdbc.one(connection, "SELECT name FROM core.player WHERE uuid = ?",
                row -> row.getString("name"), player));
    }

    @Override
    public CompletableFuture<Profile> recordLogin(PlayerLogin login) {
        String lower = login.name().toLowerCase(Locale.ROOT);
        return database.transaction(connection -> {
            // Ein anderer Spieler, der den Namen früher trug, gibt name_lower frei (eindeutiger Index).
            Jdbc.update(connection, "UPDATE core.player SET name_lower = '#' || substr(replace(uuid::text, '-', ''),"
                    + " 1, 15) WHERE name_lower = ? AND uuid <> ?", lower, login.uuid());
            Jdbc.update(connection, "INSERT INTO core.player (uuid, name, name_lower, first_join, last_join, locale)"
                    + " VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (uuid) DO UPDATE SET name = EXCLUDED.name,"
                    + " name_lower = EXCLUDED.name_lower, last_join = EXCLUDED.last_join", login.uuid(),
                    login.name(), lower, login.at(), login.at(), PrivacyRepository.initialLocale(login.clientLocale()));
            Jdbc.update(connection, "INSERT INTO core.player_name_history (uuid, name, seen_at) VALUES (?, ?, ?)"
                    + " ON CONFLICT (uuid, name) DO UPDATE SET seen_at = EXCLUDED.seen_at", login.uuid(),
                    login.name(), login.at());
            if (login.ip() != null) {
                Jdbc.update(connection, "INSERT INTO core.player_ip (uuid, ip_hmac, first_seen, last_seen)"
                        + " VALUES (?, ?, ?, ?) ON CONFLICT (uuid, ip_hmac)"
                        + " DO UPDATE SET last_seen = EXCLUDED.last_seen",
                        login.uuid(), login.ip().bytes(), login.at(), login.at());
            }
            return null;
        }).thenCompose(ignored -> Jdbc.future(valkey.set(ValkeyKeys.playerName(lower), login.uuid().toString(),
                SetArgs.Builder.ex(ValkeyKeys.NAME_TTL.toSeconds()))))
                .thenCompose(ignored -> profile(login.uuid()))
                .thenApply(profile -> profile.orElseThrow(() -> new IllegalStateException(
                        "Spieler nach Login nicht gefunden: " + login.uuid())));
    }

    @Override
    public CompletableFuture<Void> recordQuit(UUID player, Instant quitAt, long sessionSeconds) {
        return database.transaction(connection -> Jdbc.update(connection, "UPDATE core.player SET last_quit = ?,"
                + " playtime_s = playtime_s + ? WHERE uuid = ?", quitAt, Math.max(0, sessionSeconds), player))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Void> addPlaytime(UUID player, long seconds) {
        return database.transaction(connection -> Jdbc.update(connection,
                "UPDATE core.player SET playtime_s = playtime_s + ? WHERE uuid = ?", Math.max(0, seconds), player))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Void> invalidate(UUID player, Collection<String> fields) {
        return Jdbc.future(valkey.del(ValkeyKeys.profile(player))).thenApply(ignored -> {
            events.publish(new ProfileInvalidate(player, List.copyOf(fields)));
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<Instant>> lastQuit(UUID player) {
        return database.query(connection -> Jdbc.one(connection, "SELECT last_quit FROM core.player WHERE uuid = ?",
                row -> Optional.ofNullable(Jdbc.instant(row, "last_quit")), player).flatMap(value -> value));
    }
}
