package net.retromc.core.data.social;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.social.Nick;
import net.retromc.core.api.social.NickService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link NickService} über {@code core.nick_pool} und Valkey ({@code nick:{uuid}} HASH, {@code nick:name:{lower}}
 * als Kollisionsschutz, Spec 01 §2.3, §6.11). Ein Name wird mit {@code SET NX} reserviert; der Nick-HASH eines
 * Spielers wird nur geschrieben, wenn er noch keinen hat (Lua, atomar). So erhalten zwei Spieler nie denselben Nick
 * (A23). {@code NICK_CHANGED} sendet der Aufrufer, weil nur er den Server kennt.
 */
public final class NickRepository implements NickService {

    /** Schreibt {@code nick:{uuid}} nur, wenn es den Schlüssel noch nicht gibt. */
    private static final String WRITE_IF_ABSENT = "if redis.call('exists', KEYS[1]) == 1 then return 0 end "
            + "redis.call('hset', KEYS[1], unpack(ARGV)) return 1";
    /** Löscht {@code nick:name:{lower}} nur, wenn er noch diesem Spieler gehört. */
    private static final String DELETE_IF_OWNER = "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) end return 0";

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final Predicate<String> onlineRealName;
    private final Clock clock;

    /**
     * @param onlineRealName ob ein echter Spieler dieses Namens online ist (Spec 01 §6.11 Nr. 3)
     */
    public NickRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey,
            Predicate<String> onlineRealName, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.onlineRealName = Objects.requireNonNull(onlineRealName, "onlineRealName");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Eintrag aus {@code core.nick_pool}. */
    record PoolEntry(String name, String texture, String signature) {
    }

    @Override
    public CompletableFuture<Optional<Nick>> nickOf(UUID player) {
        return Jdbc.future(valkey.hgetall(ValkeyKeys.nick(player))).thenApply(hash -> hash == null || hash.isEmpty()
                ? Optional.empty() : Optional.of(Nick.fromHash(player, hash)));
    }

    @Override
    public CompletableFuture<Optional<Nick>> assign(UUID player, ServerCategory category) {
        if (category != ServerCategory.GAME) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return nickOf(player).thenCompose(existing -> existing.isPresent()
                ? CompletableFuture.completedFuture(existing)
                : pool().thenCompose(entries -> {
                    List<PoolEntry> shuffled = new ArrayList<>(entries);
                    Collections.shuffle(shuffled);
                    return tryClaim(player, shuffled, 0);
                }));
    }

    private CompletableFuture<Optional<Nick>> tryClaim(UUID player, List<PoolEntry> candidates, int index) {
        if (index >= candidates.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        PoolEntry entry = candidates.get(index);
        if (onlineRealName.test(entry.name())) {
            return tryClaim(player, candidates, index + 1);
        }
        String nameKey = ValkeyKeys.nickName(entry.name());
        return Jdbc.future(valkey.set(nameKey, player.toString(), SetArgs.Builder.nx())).thenCompose(ok -> {
            if (!"OK".equals(ok)) {
                return tryClaim(player, candidates, index + 1);
            }
            Nick nick = new Nick(player, entry.name(), entry.texture(), entry.signature(), clock.instant());
            List<String> args = new ArrayList<>();
            nick.toHash().forEach((field, value) -> {
                args.add(field);
                args.add(value);
            });
            return Jdbc.future(valkey.<Long>eval(WRITE_IF_ABSENT, ScriptOutputType.INTEGER,
                    new String[] {ValkeyKeys.nick(player)}, args.toArray(String[]::new))).thenCompose(written -> {
                        if (written != null && written == 1L) {
                            return CompletableFuture.completedFuture(Optional.of(nick));
                        }
                        // Gleichzeitig schon ein Nick vergeben: reservierten Namen zurückgeben.
                        return releaseName(entry.name(), player).thenCompose(ignored -> nickOf(player));
                    });
        });
    }

    @Override
    public CompletableFuture<Void> release(UUID player) {
        String key = ValkeyKeys.nick(player);
        return Jdbc.future(valkey.hget(key, Nick.NAME)).thenCompose(name -> Jdbc.future(valkey.del(key))
                .thenCompose(ignored -> name == null
                        ? CompletableFuture.completedFuture(null) : releaseName(name, player)));
    }

    private CompletableFuture<Void> releaseName(String name, UUID player) {
        return Jdbc.future(valkey.<Long>eval(DELETE_IF_OWNER, ScriptOutputType.INTEGER,
                new String[] {ValkeyKeys.nickName(name)}, player.toString())).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Optional<UUID>> resolve(String nickName) {
        return Jdbc.future(valkey.get(ValkeyKeys.nickName(nickName)))
                .thenApply(value -> value == null ? Optional.empty() : Optional.of(UUID.fromString(value)));
    }

    @Override
    public CompletableFuture<Map<UUID, Nick>> nicks(Collection<UUID> players) {
        List<CompletableFuture<Optional<Nick>>> lookups = players.stream().distinct().map(this::nickOf).toList();
        return CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Map<UUID, Nick> result = new HashMap<>();
            lookups.forEach(lookup -> lookup.join().ifPresent(nick -> result.put(nick.uuid(), nick)));
            return Map.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<Integer> freeCount() {
        return pool().thenCompose(entries -> {
            List<CompletableFuture<Long>> checks = entries.stream()
                    .map(entry -> Jdbc.future(valkey.exists(ValkeyKeys.nickName(entry.name())))).toList();
            return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                int free = 0;
                for (int i = 0; i < entries.size(); i++) {
                    if (checks.get(i).join() == 0 && !onlineRealName.test(entries.get(i).name())) {
                        free++;
                    }
                }
                return free;
            });
        });
    }

    /** Aktive Namen aus {@code core.nick_pool}. */
    CompletableFuture<List<PoolEntry>> pool() {
        return database.query(connection -> Jdbc.list(connection, "SELECT name, texture_value, texture_signature"
                + " FROM core.nick_pool WHERE enabled ORDER BY name", row -> new PoolEntry(row.getString("name"),
                        row.getString("texture_value"), row.getString("texture_signature"))));
    }

    /** Fügt einen Namen hinzu oder aktualisiert ihn (Pflege des Pools, Tests). */
    public CompletableFuture<Void> addToPool(String name, String texture, String signature) {
        String clean = Objects.requireNonNull(name, "name").strip();
        if (clean.length() < 3 || clean.length() > 16 || !clean.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Ungültiger Nickname: " + name);
        }
        return database.transaction(connection -> Jdbc.update(connection, "INSERT INTO core.nick_pool"
                + " (name, texture_value, texture_signature) VALUES (?, ?, ?) ON CONFLICT (name) DO UPDATE"
                + " SET texture_value = EXCLUDED.texture_value, texture_signature = EXCLUDED.texture_signature,"
                + " enabled = true", clean, texture, signature)).thenApply(ignored -> null);
    }
}
