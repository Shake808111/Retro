package net.retromc.core.data.social;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.IgnoreOutcome;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link IgnoreService} auf {@code social.ignore} mit Cache {@code ignore:{uuid}} (SET, 10 min). Ein leerer Cache wird
 * mit dem Platzhalter {@value #EMPTY_MARKER} gespeichert, damit auch „ignoriert niemanden“ nicht jedes Mal die
 * Datenbank fragt. Teammitglieder (Ranggewicht ≥ 500) sind nicht ignorierbar.
 */
public final class IgnoreRepository implements IgnoreService {

    /** Platzhalter für einen gecachten leeren Satz. */
    static final String EMPTY_MARKER = "-";

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<RankService> ranks;

    public IgnoreRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey,
            Supplier<RankService> ranks) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
    }

    @Override
    public CompletableFuture<Boolean> ignores(UUID owner, UUID target) {
        return ignored(owner).thenApply(set -> set.contains(target));
    }

    @Override
    public CompletableFuture<Set<UUID>> ignored(UUID owner) {
        String key = ValkeyKeys.ignore(owner);
        return Jdbc.future(valkey.smembers(key)).thenCompose(cached -> {
            if (cached != null && !cached.isEmpty()) {
                Set<UUID> result = new HashSet<>();
                cached.stream().filter(member -> !EMPTY_MARKER.equals(member)).map(UUID::fromString)
                        .forEach(result::add);
                return CompletableFuture.completedFuture(Set.copyOf(result));
            }
            return database.query(connection -> Jdbc.list(connection,
                    "SELECT target FROM social.ignore WHERE owner = ?", row -> Jdbc.requireUuid(row, "target"),
                    owner)).thenCompose(targets -> {
                        String[] members = new String[targets.size() + 1];
                        members[0] = EMPTY_MARKER;
                        for (int i = 0; i < targets.size(); i++) {
                            members[i + 1] = targets.get(i).toString();
                        }
                        return Jdbc.future(valkey.sadd(key, members))
                                .thenCompose(ignored -> Jdbc.future(valkey.expire(key,
                                        ValkeyKeys.IGNORE_TTL.toSeconds())))
                                .thenApply(ignored -> Set.copyOf(targets));
                    });
        });
    }

    @Override
    public CompletableFuture<IgnoreOutcome> add(UUID owner, UUID target) {
        if (owner.equals(target)) {
            return CompletableFuture.completedFuture(IgnoreOutcome.SELF);
        }
        return ranks.get().rank(target).thenCompose(rank -> {
            if (rank.isTeam()) {
                return CompletableFuture.completedFuture(IgnoreOutcome.STAFF);
            }
            return database.transaction(connection -> {
                if (Jdbc.count(connection, "SELECT count(*) FROM social.ignore WHERE owner = ? AND target = ?",
                        owner, target) > 0) {
                    return IgnoreOutcome.ALREADY;
                }
                if (Jdbc.count(connection, "SELECT count(*) FROM social.ignore WHERE owner = ?", owner)
                        >= MAX_IGNORED) {
                    return IgnoreOutcome.LIMIT;
                }
                Jdbc.update(connection, "INSERT INTO social.ignore (owner, target) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING", owner, target);
                return IgnoreOutcome.ADDED;
            }).thenCompose(outcome -> invalidate(owner).thenApply(ignored -> outcome));
        });
    }

    @Override
    public CompletableFuture<Boolean> remove(UUID owner, UUID target) {
        return database.transaction(connection -> Jdbc.update(connection,
                "DELETE FROM social.ignore WHERE owner = ? AND target = ?", owner, target) > 0)
                .thenCompose(removed -> invalidate(owner).thenApply(ignored -> removed));
    }

    @Override
    public CompletableFuture<List<PlayerRef>> list(UUID owner) {
        return database.query(connection -> Jdbc.list(connection, "SELECT i.target,"
                + " COALESCE(p.name, i.target::text) AS name FROM social.ignore i"
                + " LEFT JOIN core.player p ON p.uuid = i.target WHERE i.owner = ? ORDER BY lower(COALESCE(p.name,"
                + " i.target::text))", row -> new PlayerRef(Jdbc.requireUuid(row, "target"), row.getString("name")),
                owner));
    }

    private CompletableFuture<Void> invalidate(UUID owner) {
        return Jdbc.future(valkey.del(ValkeyKeys.ignore(owner))).thenApply(ignored -> null);
    }
}
