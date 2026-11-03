package net.retromc.core.data.social;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.FriendEntry;
import net.retromc.core.api.social.FriendOutcome;
import net.retromc.core.api.social.FriendPair;
import net.retromc.core.api.social.FriendRequest;
import net.retromc.core.api.social.FriendResult;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.core.api.social.JumpResult;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.api.social.Presence;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Jdbc;

/**
 * {@link FriendService} auf {@code social.friendship}, {@code social.friend_request} und
 * {@code social.friend_favorite} (Spec 01 §3.1, §6.4). Limits aus dem Rang ({@code RankLimits.friends}: 250/500/1000),
 * höchstens 100 offene eingehende Anfragen, gegenseitige Anfrage → sofort befreundet. Online-Status aus
 * {@link PresenceService} (versteckte Spieler gelten als offline). Benachrichtigungen ({@code FRIEND_*}) sendet der
 * Proxy, der den Befehl ausführt.
 */
public final class FriendRepository implements FriendService {

    private static final String FRIENDS_OF = "SELECT CASE WHEN a = ? THEN b ELSE a END AS other, since"
            + " FROM social.friendship WHERE a = ? OR b = ?";

    private final AsyncDatabase database;
    private final Supplier<PresenceService> presences;
    private final Supplier<RankService> ranks;
    private final Supplier<IgnoreService> ignores;
    private final Supplier<SettingsService> settings;
    private final Clock clock;

    public FriendRepository(AsyncDatabase database, Supplier<PresenceService> presences, Supplier<RankService> ranks,
            Supplier<IgnoreService> ignores, Supplier<SettingsService> settings, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.presences = Objects.requireNonNull(presences, "presences");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Eintrag ohne Präsenz. */
    private record Row(UUID uuid, String name, Instant since, boolean favorite, Instant lastSeen) {
    }

    // ------------------------------------------------------------------ Lesen

    @Override
    public CompletableFuture<OnlineCount> counts(UUID player) {
        return friendIds(player).thenCompose(ids -> presences.get().presences(ids).thenApply(online ->
                new OnlineCount((int) online.values().stream().filter(Presence::visible).count(), ids.size())));
    }

    private CompletableFuture<List<UUID>> friendIds(UUID player) {
        return database.query(connection -> Jdbc.list(connection, FRIENDS_OF, row -> Jdbc.requireUuid(row, "other"),
                player, player, player));
    }

    @Override
    public CompletableFuture<List<FriendEntry>> list(UUID player) {
        return database.query(connection -> Jdbc.list(connection, "SELECT f.other, COALESCE(p.name, f.other::text)"
                + " AS name, f.since, fav.friend IS NOT NULL AS favorite, p.last_quit FROM (" + FRIENDS_OF + ") f"
                + " LEFT JOIN core.player p ON p.uuid = f.other"
                + " LEFT JOIN social.friend_favorite fav ON fav.owner = ? AND fav.friend = f.other",
                row -> new Row(Jdbc.requireUuid(row, "other"), row.getString("name"),
                        Objects.requireNonNullElse(Jdbc.instant(row, "since"), Instant.EPOCH),
                        row.getBoolean("favorite"), Jdbc.instant(row, "last_quit")),
                player, player, player, player))
                .thenCompose(rows -> presences.get().presences(rows.stream().map(Row::uuid).toList())
                        .thenApply(online -> entries(rows, online)));
    }

    private static List<FriendEntry> entries(List<Row> rows, Map<UUID, Presence> online) {
        List<FriendEntry> entries = new ArrayList<>();
        for (Row row : rows) {
            Presence presence = online.get(row.uuid());
            String server = presence != null && presence.visible() ? presence.server() : null;
            entries.add(new FriendEntry(row.uuid(), row.name(), row.since(), row.favorite(), server, row.lastSeen()));
        }
        entries.sort(FriendEntry.LIST_ORDER);
        return List.copyOf(entries);
    }

    @Override
    public CompletableFuture<Boolean> areFriends(UUID a, UUID b) {
        if (a.equals(b)) {
            return CompletableFuture.completedFuture(false);
        }
        FriendPair pair = FriendPair.of(a, b);
        return database.query(connection -> friends(connection, pair));
    }

    private static boolean friends(Connection connection, FriendPair pair) throws SQLException {
        return Jdbc.count(connection, "SELECT count(*) FROM social.friendship WHERE a = ? AND b = ?", pair.a(),
                pair.b()) > 0;
    }

    @Override
    public CompletableFuture<List<FriendRequest>> requests(UUID receiver) {
        return database.query(connection -> Jdbc.list(connection, "SELECT r.sender, COALESCE(p.name,"
                + " r.sender::text) AS name, r.created_at FROM social.friend_request r LEFT JOIN core.player p"
                + " ON p.uuid = r.sender WHERE r.receiver = ? ORDER BY r.created_at, r.sender",
                row -> new FriendRequest(Jdbc.requireUuid(row, "sender"), row.getString("name"), receiver,
                        Objects.requireNonNullElse(Jdbc.instant(row, "created_at"), Instant.EPOCH)), receiver));
    }

    // ------------------------------------------------------------------ Anfragen

    /** Daten für die Prüfungen aus Spec 01 §6.4 Nr. 1. */
    private record Check(RankInfo senderRank, RankInfo targetRank, boolean ignored, PrivacySettings privacy) {
    }

    @Override
    public CompletableFuture<FriendResult> request(UUID sender, UUID target) {
        if (sender.equals(target)) {
            return done(FriendOutcome.SELF);
        }
        CompletableFuture<RankInfo> senderRank = ranks.get().rank(sender);
        CompletableFuture<RankInfo> targetRank = ranks.get().rank(target);
        CompletableFuture<Boolean> ignored = ignores.get().ignores(target, sender);
        CompletableFuture<PrivacySettings> privacy = settings.get().settings(target);
        return CompletableFuture.allOf(senderRank, targetRank, ignored, privacy)
                .thenApply(ignoredResult -> new Check(senderRank.join(), targetRank.join(), ignored.join(),
                        privacy.join()))
                .thenCompose(check -> database.transaction(connection -> {
                    if (Jdbc.count(connection, "SELECT count(*) FROM core.player WHERE uuid = ?", target) == 0) {
                        return FriendResult.of(FriendOutcome.UNKNOWN_PLAYER);
                    }
                    FriendPair pair = FriendPair.of(sender, target);
                    if (friends(connection, pair)) {
                        return FriendResult.of(FriendOutcome.ALREADY_FRIENDS);
                    }
                    if (pending(connection, sender, target)) {
                        return FriendResult.of(FriendOutcome.ALREADY_PENDING);
                    }
                    if (pending(connection, target, sender)) {
                        return befriend(connection, sender, target, check.senderRank(), check.targetRank());
                    }
                    if (check.ignored() || !check.privacy().acceptsFriendRequestFrom(check.senderRank().weight())) {
                        return FriendResult.of(FriendOutcome.BLOCKED);
                    }
                    Optional<FriendResult> limit = limits(connection, sender, target, check.senderRank(),
                            check.targetRank());
                    if (limit.isPresent()) {
                        return limit.get();
                    }
                    if (Jdbc.count(connection, "SELECT count(*) FROM social.friend_request WHERE receiver = ?",
                            target) >= MAX_INCOMING_REQUESTS) {
                        return new FriendResult(FriendOutcome.LIMIT_OTHER, check.targetRank().limits().friends());
                    }
                    Jdbc.update(connection, "INSERT INTO social.friend_request (sender, receiver, created_at)"
                            + " VALUES (?, ?, ?)", sender, target, clock.instant());
                    return FriendResult.of(FriendOutcome.SENT);
                }));
    }

    @Override
    public CompletableFuture<FriendResult> accept(UUID receiver, UUID sender) {
        return ranks.get().rank(receiver).thenCombine(ranks.get().rank(sender), (receiverRank, senderRank) ->
                new Check(senderRank, receiverRank, false, PrivacySettings.DEFAULTS))
                .thenCompose(check -> database.transaction(connection -> {
                    if (!pending(connection, sender, receiver)) {
                        return FriendResult.of(FriendOutcome.NO_REQUEST);
                    }
                    return befriend(connection, receiver, sender, check.targetRank(), check.senderRank());
                }));
    }

    @Override
    public CompletableFuture<FriendResult> deny(UUID receiver, UUID sender) {
        return database.transaction(connection -> Jdbc.update(connection, "DELETE FROM social.friend_request"
                + " WHERE sender = ? AND receiver = ?", sender, receiver) > 0
                ? FriendResult.of(FriendOutcome.DENIED) : FriendResult.of(FriendOutcome.NO_REQUEST));
    }

    @Override
    public CompletableFuture<Integer> acceptAll(UUID receiver) {
        return requests(receiver).thenCompose(open -> acceptEach(receiver, open, 0, 0));
    }

    private CompletableFuture<Integer> acceptEach(UUID receiver, List<FriendRequest> open, int index, int accepted) {
        if (index >= open.size()) {
            return CompletableFuture.completedFuture(accepted);
        }
        return accept(receiver, open.get(index).sender()).thenCompose(result -> {
            if (result.outcome() == FriendOutcome.LIMIT_SELF) {
                return CompletableFuture.completedFuture(accepted);
            }
            return acceptEach(receiver, open, index + 1, accepted + (result.outcome() == FriendOutcome.ADDED ? 1 : 0));
        });
    }

    @Override
    public CompletableFuture<Integer> denyAll(UUID receiver) {
        return database.transaction(connection -> Jdbc.update(connection,
                "DELETE FROM social.friend_request WHERE receiver = ?", receiver));
    }

    /** Freundschaft anlegen, wenn beide unter dem Limit sind; Anfragen beider Richtungen löschen. */
    private FriendResult befriend(Connection connection, UUID self, UUID other, RankInfo selfRank,
            RankInfo otherRank) throws SQLException {
        Optional<FriendResult> limit = limits(connection, self, other, selfRank, otherRank);
        if (limit.isPresent()) {
            return limit.get();
        }
        FriendPair pair = FriendPair.of(self, other);
        Jdbc.update(connection, "INSERT INTO social.friendship (a, b, since) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                pair.a(), pair.b(), clock.instant());
        Jdbc.update(connection, "DELETE FROM social.friend_request WHERE (sender = ? AND receiver = ?)"
                + " OR (sender = ? AND receiver = ?)", self, other, other, self);
        return FriendResult.of(FriendOutcome.ADDED);
    }

    private static Optional<FriendResult> limits(Connection connection, UUID self, UUID other, RankInfo selfRank,
            RankInfo otherRank) throws SQLException {
        if (friendCount(connection, self) >= selfRank.limits().friends()) {
            return Optional.of(new FriendResult(FriendOutcome.LIMIT_SELF, selfRank.limits().friends()));
        }
        if (friendCount(connection, other) >= otherRank.limits().friends()) {
            return Optional.of(new FriendResult(FriendOutcome.LIMIT_OTHER, otherRank.limits().friends()));
        }
        return Optional.empty();
    }

    private static long friendCount(Connection connection, UUID player) throws SQLException {
        return Jdbc.count(connection, "SELECT count(*) FROM social.friendship WHERE a = ? OR b = ?", player, player);
    }

    private static boolean pending(Connection connection, UUID sender, UUID receiver) throws SQLException {
        return Jdbc.count(connection, "SELECT count(*) FROM social.friend_request WHERE sender = ? AND receiver = ?",
                sender, receiver) > 0;
    }

    // ------------------------------------------------------------------ Entfernen, Favoriten

    @Override
    public CompletableFuture<FriendResult> remove(UUID player, UUID friend) {
        FriendPair pair = FriendPair.of(player, friend);
        return database.transaction(connection -> {
            if (Jdbc.update(connection, "DELETE FROM social.friendship WHERE a = ? AND b = ?", pair.a(), pair.b())
                    == 0) {
                return FriendResult.of(FriendOutcome.NOT_FRIENDS);
            }
            Jdbc.update(connection, "DELETE FROM social.friend_favorite WHERE (owner = ? AND friend = ?)"
                    + " OR (owner = ? AND friend = ?)", player, friend, friend, player);
            return FriendResult.of(FriendOutcome.REMOVED);
        });
    }

    @Override
    public CompletableFuture<Integer> clear(UUID player) {
        return database.transaction(connection -> {
            Jdbc.update(connection, "DELETE FROM social.friend_favorite WHERE owner = ? OR friend = ?", player,
                    player);
            return Jdbc.update(connection, "DELETE FROM social.friendship WHERE a = ? OR b = ?", player, player);
        });
    }

    @Override
    public CompletableFuture<Boolean> favorite(UUID owner, UUID friend) {
        FriendPair pair = FriendPair.of(owner, friend);
        return database.transaction(connection -> {
            if (owner.equals(friend) || !friends(connection, pair)) {
                return false;
            }
            if (Jdbc.update(connection, "DELETE FROM social.friend_favorite WHERE owner = ? AND friend = ?", owner,
                    friend) > 0) {
                return false;
            }
            Jdbc.update(connection, "INSERT INTO social.friend_favorite (owner, friend) VALUES (?, ?)", owner, friend);
            return true;
        });
    }

    @Override
    public CompletableFuture<JumpResult> jump(UUID player, UUID target) {
        FriendPair pair = FriendPair.of(player, target);
        CompletableFuture<Boolean> favoriteJump = database.query(connection -> {
            if (!friends(connection, pair)) {
                return null;
            }
            return Jdbc.count(connection, "SELECT count(*) FROM social.friend_favorite WHERE owner = ? AND friend = ?"
                    + " AND allow_jump", target, player) > 0;
        });
        return favoriteJump.thenCompose(favorite -> {
            if (favorite == null) {
                return CompletableFuture.completedFuture(new JumpResult(JumpResult.Outcome.NOT_FRIENDS, null));
            }
            CompletableFuture<Optional<Presence>> there = presences.get().presence(target);
            CompletableFuture<Optional<Presence>> here = presences.get().presence(player);
            CompletableFuture<PrivacySettings> privacy = settings.get().settings(target);
            return CompletableFuture.allOf(there, here, privacy).thenApply(ignored -> {
                Optional<Presence> targetPresence = there.join().filter(Presence::visible);
                if (targetPresence.isEmpty()) {
                    return new JumpResult(JumpResult.Outcome.OFFLINE, null);
                }
                if (!privacy.join().allowsJumpFrom(true, favorite)) {
                    return new JumpResult(JumpResult.Outcome.DENIED, null);
                }
                String server = targetPresence.get().server();
                if (here.join().map(Presence::server).filter(server::equals).isPresent()) {
                    return new JumpResult(JumpResult.Outcome.SAME_SERVER, null);
                }
                return new JumpResult(JumpResult.Outcome.OK, server);
            });
        });
    }

    @Override
    public CompletableFuture<List<UUID>> statusRecipients(UUID player) {
        return database.query(connection -> {
            boolean visible = Jdbc.one(connection, "SELECT COALESCE(show_online, true) AS visible FROM social.privacy"
                    + " WHERE uuid = ?", row -> row.getBoolean("visible"), player).orElse(true);
            if (!visible) {
                return List.of();
            }
            return Jdbc.list(connection, "SELECT f.other FROM (" + FRIENDS_OF + ") f"
                    + " LEFT JOIN social.privacy pr ON pr.uuid = f.other"
                    + " LEFT JOIN social.friend_favorite fav ON fav.owner = f.other AND fav.friend = ?"
                    + " WHERE COALESCE(pr.notify_friends, true) OR COALESCE(fav.notify, false)",
                    row -> Jdbc.requireUuid(row, "other"), player, player, player, player);
        });
    }

    private static CompletableFuture<FriendResult> done(FriendOutcome outcome) {
        return CompletableFuture.completedFuture(FriendResult.of(outcome));
    }
}
