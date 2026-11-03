package net.retromc.core.data.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.retromc.core.api.event.PrivateMessage;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.core.api.social.MessageResult;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.PrivateMessageService;
import net.retromc.core.api.social.ReceivedMessage;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link PrivateMessageService} (Spec 01 §3.2 #16–18, §6.7): prüft Online-Status, Ignorieren und {@code msg_from}
 * (Team darf immer), merkt {@code msg:last} beidseitig (30 min) und {@code msg:recent} beim Empfänger (10 Einträge,
 * 24 h) und stellt über {@code PRIVATE_MSG} zu – der Proxy des Empfängers zeigt {@code msg.in} (Multi-Proxy, O30).
 * Freunde, Einstellungen, Ignorieren und Ränge kommen aus austauschbaren Diensten.
 */
public final class ValkeyPrivateMessages implements PrivateMessageService {

    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<FriendService> friends;
    private final Supplier<SettingsService> settings;
    private final Supplier<IgnoreService> ignores;
    private final Supplier<RankService> ranks;
    private final Function<UUID, CompletableFuture<Boolean>> online;
    private final EventSink events;
    private final Clock clock;
    private final Duration replyWindow;

    /**
     * @param online ob der Spieler im Netzwerk online ist (lokaler Proxy oder Präsenz)
     */
    public ValkeyPrivateMessages(RedisAsyncCommands<String, String> valkey, Supplier<FriendService> friends,
            Supplier<SettingsService> settings, Supplier<IgnoreService> ignores, Supplier<RankService> ranks,
            Function<UUID, CompletableFuture<Boolean>> online, EventSink events, Clock clock) {
        this(valkey, friends, settings, ignores, ranks, online, events, clock, REPLY_WINDOW);
    }

    /** Mit eigener Gültigkeit von {@code msg:last} (Tests). */
    public ValkeyPrivateMessages(RedisAsyncCommands<String, String> valkey, Supplier<FriendService> friends,
            Supplier<SettingsService> settings, Supplier<IgnoreService> ignores, Supplier<RankService> ranks,
            Function<UUID, CompletableFuture<Boolean>> online, EventSink events, Clock clock, Duration replyWindow) {
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.friends = Objects.requireNonNull(friends, "friends");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.online = Objects.requireNonNull(online, "online");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.replyWindow = Objects.requireNonNull(replyWindow, "replyWindow");
    }

    @Override
    public CompletableFuture<MessageResult> send(UUID from, UUID to, String text) {
        if (from.equals(to)) {
            return result(MessageResult.Outcome.SELF);
        }
        return online.apply(to).thenCompose(isOnline -> {
            if (!isOnline) {
                return result(MessageResult.Outcome.OFFLINE);
            }
            return allowed(from, to).thenCompose(allowed -> allowed
                    ? deliver(from, to, text)
                    : result(MessageResult.Outcome.DENIED));
        });
    }

    /** Ignorieren und {@code msg_from} des Empfängers (Spec 01 §6.7 Nr. 1–2). */
    CompletableFuture<Boolean> allowed(UUID from, UUID to) {
        CompletableFuture<Boolean> ignored = ignores.get().ignores(to, from);
        CompletableFuture<PrivacySettings> privacy = settings.get().settings(to);
        CompletableFuture<Boolean> team = ranks.get().rank(from).thenApply(rank -> rank.isTeam());
        CompletableFuture<Boolean> friend = friends.get().areFriends(from, to).exceptionally(error -> false);
        return CompletableFuture.allOf(ignored, privacy, team, friend).thenApply(done -> !ignored.join()
                && privacy.join().acceptsMessageFrom(friend.join(), team.join()));
    }

    private CompletableFuture<MessageResult> deliver(UUID from, UUID to, String text) {
        Instant now = clock.instant();
        JsonObject json = new JsonObject();
        json.addProperty("from", from.toString());
        json.addProperty("text", text);
        json.addProperty("ts", now.toEpochMilli());
        String recentKey = ValkeyKeys.msgRecent(to);
        long window = replyWindow.toMillis();
        return Jdbc.future(valkey.set(ValkeyKeys.msgLast(from), to.toString(), SetArgs.Builder.px(window)))
                .thenCompose(ignored -> Jdbc.future(valkey.set(ValkeyKeys.msgLast(to), from.toString(),
                        SetArgs.Builder.px(window))))
                .thenCompose(ignored -> Jdbc.future(valkey.lpush(recentKey, json.toString())))
                .thenCompose(ignored -> Jdbc.future(valkey.ltrim(recentKey, 0, RECENT_LIMIT - 1)))
                .thenCompose(ignored -> Jdbc.future(valkey.expire(recentKey, RECENT_RETENTION.toSeconds())))
                .thenApply(ignored -> {
                    events.publish(new PrivateMessage(from, List.of(to), text));
                    return new MessageResult(MessageResult.Outcome.DELIVERED, to);
                });
    }

    @Override
    public CompletableFuture<MessageResult> reply(UUID from, String text) {
        return Jdbc.future(valkey.get(ValkeyKeys.msgLast(from))).thenCompose(partner -> partner == null
                ? result(MessageResult.Outcome.NO_REPLY_TARGET)
                : send(from, UUID.fromString(partner), text));
    }

    @Override
    public CompletableFuture<List<ReceivedMessage>> recent(UUID player) {
        Instant from = clock.instant().minus(RECENT_RETENTION);
        return Jdbc.future(valkey.lrange(ValkeyKeys.msgRecent(player), 0, RECENT_LIMIT - 1)).thenApply(raw -> {
            List<ReceivedMessage> result = new ArrayList<>();
            for (String entry : raw) {
                try {
                    JsonObject json = JsonParser.parseString(entry).getAsJsonObject();
                    ReceivedMessage message = new ReceivedMessage(UUID.fromString(json.get("from").getAsString()),
                            json.get("text").getAsString(), Instant.ofEpochMilli(json.get("ts").getAsLong()));
                    if (!message.ts().isBefore(from)) {
                        result.add(message);
                    }
                } catch (JsonParseException | IllegalStateException | IllegalArgumentException
                        | NullPointerException e) {
                    // Fremde oder beschädigte Einträge überspringen.
                }
            }
            return List.copyOf(result);
        });
    }

    private static CompletableFuture<MessageResult> result(MessageResult.Outcome outcome) {
        return CompletableFuture.completedFuture(new MessageResult(outcome, null));
    }
}
