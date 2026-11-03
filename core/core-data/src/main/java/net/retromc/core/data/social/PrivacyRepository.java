package net.retromc.core.data.social;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import net.retromc.core.api.event.ProfileChanged;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.social.Presence;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link SettingsService} auf {@code social.privacy} (V1 + V1_1, Werte Spec 01 §6.13). Änderungen gehen sofort in
 * die Datenbank, senden {@code PROFILE_INVALIDATE} und spiegeln {@code show_online} in {@code presence.hidden}.
 */
public final class PrivacyRepository implements SettingsService {

    private static final String COLUMNS = "COALESCE(friend_requests, true) AS friend_requests, friend_req_from,"
            + " COALESCE(msg_from, 1) AS msg_from, COALESCE(party_from, 2) AS party_from,"
            + " COALESCE(show_online, true) AS show_online, jump_from, notify_friends, notify_clan, hide_from_clan,"
            + " global_chat, nick_on_join, nick_keep";

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final EventSink events;

    public PrivacyRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey, EventSink events) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public CompletableFuture<PrivacySettings> settings(UUID player) {
        return database.query(connection -> load(connection, player, false).orElse(PrivacySettings.DEFAULTS));
    }

    @Override
    public CompletableFuture<PrivacySettings> update(UUID player, UnaryOperator<PrivacySettings> change) {
        return database.transaction(connection -> {
            Jdbc.update(connection, "INSERT INTO social.privacy (uuid) VALUES (?) ON CONFLICT DO NOTHING", player);
            PrivacySettings current = load(connection, player, true).orElse(PrivacySettings.DEFAULTS);
            PrivacySettings next = Objects.requireNonNull(change.apply(current), "Einstellungen");
            Jdbc.update(connection, "UPDATE social.privacy SET friend_requests = ?, friend_req_from = ?,"
                    + " msg_from = ?, party_from = ?, show_online = ?, jump_from = ?, notify_friends = ?,"
                    + " notify_clan = ?, hide_from_clan = ?, global_chat = ?, nick_on_join = ?, nick_keep = ?,"
                    + " updated_at = now() WHERE uuid = ?", next.friendRequests(), (short) next.friendReqFrom(),
                    (short) next.msgFrom(), (short) next.partyFrom(), next.showOnline(), (short) next.jumpFrom(),
                    next.notifyFriends(), next.notifyClan(), next.hideFromClan(), next.globalChat(),
                    next.nickOnJoin(), next.nickKeep(), player);
            return next;
        }).thenCompose(next -> mirrorHidden(player, !next.showOnline()).thenApply(ignored -> {
            events.publish(new ProfileInvalidate(player, List.of()));
            return next;
        }));
    }

    @Override
    public CompletableFuture<Void> setLocale(UUID player, String locale) {
        String value = Objects.requireNonNull(locale, "locale").replace('-', '_');
        return database.transaction(connection -> Jdbc.update(connection,
                "UPDATE core.player SET locale = ? WHERE uuid = ?", value, player))
                .thenCompose(ignored -> Jdbc.future(valkey.exists(ValkeyKeys.profile(player))))
                .thenCompose(cached -> cached > 0
                        ? Jdbc.future(valkey.hset(ValkeyKeys.profile(player), Profile.LOCALE, value))
                        : CompletableFuture.completedFuture(false))
                .thenApply(ignored -> {
                    events.publish(new ProfileChanged(player, List.of(ProfileChanged.LOCALE)));
                    return null;
                });
    }

    private CompletableFuture<Void> mirrorHidden(UUID player, boolean hidden) {
        String key = ValkeyKeys.presence(player);
        return Jdbc.future(valkey.exists(key)).thenCompose(exists -> exists > 0
                ? Jdbc.future(valkey.hset(key, Presence.HIDDEN, hidden ? "1" : "0")).thenApply(ignored -> null)
                : CompletableFuture.completedFuture(null));
    }

    private static Optional<PrivacySettings> load(Connection connection, UUID player, boolean lock)
            throws SQLException {
        return Jdbc.one(connection, "SELECT " + COLUMNS + " FROM social.privacy WHERE uuid = ?"
                + (lock ? " FOR UPDATE" : ""), PrivacyRepository::map, player);
    }

    static PrivacySettings map(ResultSet row) throws SQLException {
        return new PrivacySettings(row.getBoolean("friend_requests"), row.getInt("friend_req_from"),
                row.getInt("msg_from"), row.getInt("party_from"), row.getBoolean("show_online"),
                row.getInt("jump_from"), row.getBoolean("notify_friends"), row.getBoolean("notify_clan"),
                row.getBoolean("hide_from_clan"), row.getBoolean("global_chat"), row.getBoolean("nick_on_join"),
                row.getBoolean("nick_keep"));
    }

    /** Normalisiert Client-Sprachen ({@code de_at} → {@code de_DE}, sonst {@code en_US}, Spec 01 §6.3 Nr. 4). */
    public static String initialLocale(String clientLocale) {
        String lower = clientLocale.toLowerCase(Locale.ROOT);
        return lower.startsWith("de") ? "de_DE" : "en_US";
    }
}
