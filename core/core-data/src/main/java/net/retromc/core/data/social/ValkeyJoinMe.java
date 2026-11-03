package net.retromc.core.data.social;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.rank.RankLimits;
import net.retromc.core.api.social.JoinMeResult;
import net.retromc.core.api.social.JoinMeService;
import net.retromc.core.api.util.NetworkTime;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link JoinMeService}: Tageszähler {@code cnt:joinme:{uuid}:{yyyy-mm-dd}} (Europe/Berlin, 26 h; Spec 01 §6.17).
 * Ein Versuch über dem Limit wird zurückgenommen.
 */
public final class ValkeyJoinMe implements JoinMeService {

    private final RedisAsyncCommands<String, String> valkey;
    private final Clock clock;

    public ValkeyJoinMe(RedisAsyncCommands<String, String> valkey, Clock clock) {
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private String key(UUID player) {
        return ValkeyKeys.joinmeCount(player, NetworkTime.day(clock.instant()));
    }

    @Override
    public CompletableFuture<JoinMeResult> use(UUID player, int perDay) {
        String key = key(player);
        if (perDay == 0) {
            return usedToday(player).thenApply(used -> new JoinMeResult(false, used, 0));
        }
        return Jdbc.future(valkey.incr(key)).thenCompose(value -> {
            CompletableFuture<?> ttl = value == 1
                    ? Jdbc.future(valkey.expire(key, ValkeyKeys.DAILY_COUNTER_TTL.toSeconds()))
                    : CompletableFuture.completedFuture(null);
            if (RankLimits.allows(perDay, (int) (value - 1))) {
                return ttl.thenApply(ignored -> new JoinMeResult(true, value.intValue(), perDay));
            }
            return ttl.thenCompose(ignored -> Jdbc.future(valkey.decr(key)))
                    .thenApply(used -> new JoinMeResult(false, used.intValue(), perDay));
        });
    }

    @Override
    public CompletableFuture<Integer> usedToday(UUID player) {
        return Jdbc.future(valkey.get(key(player))).thenApply(value -> value == null ? 0 : Integer.parseInt(value));
    }
}
