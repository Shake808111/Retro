package net.retromc.core.data.social;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.social.Presence;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link PresenceService} auf {@code presence:{uuid}} (HASH, TTL 60 s, Heartbeat 20 s; Spec 01 §2.3, §6.3).
 * {@link #setAfk} sendet {@code AFK_CHANGED}.
 */
public final class ValkeyPresenceService implements PresenceService {

    private final RedisAsyncCommands<String, String> valkey;
    private final EventSink events;

    public ValkeyPresenceService(RedisAsyncCommands<String, String> valkey, EventSink events) {
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public CompletableFuture<Optional<Presence>> presence(UUID player) {
        return Jdbc.future(valkey.hgetall(ValkeyKeys.presence(player))).thenApply(hash -> read(player, hash));
    }

    private static Optional<Presence> read(UUID player, Map<String, String> hash) {
        if (hash == null || !hash.containsKey(Presence.SERVER)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Presence.fromHash(player, hash));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public CompletableFuture<Map<UUID, Presence>> presences(Collection<UUID> players) {
        List<UUID> distinct = players.stream().distinct().toList();
        List<CompletableFuture<Map<String, String>>> lookups = distinct.stream()
                .map(player -> Jdbc.future(valkey.hgetall(ValkeyKeys.presence(player)))).toList();
        return CompletableFuture.allOf(lookups.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Map<UUID, Presence> result = new HashMap<>();
            for (int i = 0; i < distinct.size(); i++) {
                UUID player = distinct.get(i);
                read(player, lookups.get(i).join()).ifPresent(presence -> result.put(player, presence));
            }
            return Map.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<Optional<String>> serverOf(UUID player) {
        return presence(player).thenApply(presence -> presence.map(Presence::server));
    }

    @Override
    public CompletableFuture<Void> update(Presence presence) {
        String key = ValkeyKeys.presence(presence.uuid());
        return Jdbc.future(valkey.hset(key, presence.toHash()))
                .thenCompose(ignored -> Jdbc.future(valkey.expire(key, ValkeyKeys.PRESENCE_TTL.toSeconds())))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Void> refresh(Collection<UUID> players) {
        List<CompletableFuture<Boolean>> renewals = players.stream()
                .map(player -> Jdbc.future(valkey.expire(ValkeyKeys.presence(player),
                        ValkeyKeys.PRESENCE_TTL.toSeconds()))).toList();
        return CompletableFuture.allOf(renewals.toArray(CompletableFuture[]::new));
    }

    @Override
    public CompletableFuture<Void> setAfk(UUID player, boolean afk) {
        String key = ValkeyKeys.presence(player);
        return Jdbc.future(valkey.exists(key)).thenCompose(exists -> exists > 0
                ? Jdbc.future(valkey.hset(key, Presence.AFK, afk ? "1" : "0")).thenApply(ignored -> (Void) null)
                : CompletableFuture.completedFuture((Void) null))
                .thenApply(ignored -> {
                    events.publish(new AfkChanged(player, afk));
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> remove(UUID player) {
        return Jdbc.future(valkey.del(ValkeyKeys.presence(player))).thenApply(ignored -> null);
    }
}
