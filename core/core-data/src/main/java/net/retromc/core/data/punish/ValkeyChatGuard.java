package net.retromc.core.data.punish;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.retromc.core.api.punish.ChatCheck;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.punish.ChatGuard;
import net.retromc.core.api.punish.ChatVerdict;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link ChatGuard}: erst Mute ({@link PunishmentService#activeMute}, gilt auch für das Team), dann die Spam-Regeln
 * aus {@link SpamFilter} mit Zustand in {@code spam:{uuid}} (HASH, TTL 30 s).
 */
public final class ValkeyChatGuard implements ChatGuard {

    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<PunishmentService> punishments;
    private final SpamFilter filter;
    private final Clock clock;

    public ValkeyChatGuard(RedisAsyncCommands<String, String> valkey, Supplier<PunishmentService> punishments,
            SpamFilter filter, Clock clock) {
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.punishments = Objects.requireNonNull(punishments, "punishments");
        this.filter = Objects.requireNonNull(filter, "filter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<ChatCheck> check(UUID player, String text, ChatContext context) {
        return punishments.get().activeMute(player).thenCompose(mute -> {
            if (mute.isPresent()) {
                return CompletableFuture.completedFuture(new ChatCheck(ChatVerdict.MUTED, text, Duration.ZERO));
            }
            if (context.team()) {
                return CompletableFuture.completedFuture(ChatCheck.ok(text));
            }
            String key = ValkeyKeys.spam(player);
            return Jdbc.future(valkey.hgetall(key)).thenCompose(hash -> {
                SpamFilter.Decision decision = filter.check(SpamFilter.State.fromHash(hash), text, context,
                        clock.instant());
                return Jdbc.future(valkey.del(key))
                        .thenCompose(ignored -> Jdbc.future(valkey.hset(key, decision.state().toHash())))
                        .thenCompose(ignored -> Jdbc.future(valkey.expire(key, ValkeyKeys.SPAM_TTL.toSeconds())))
                        .thenApply(ignored -> decision.check());
            });
        });
    }
}
