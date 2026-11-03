package net.retromc.core.data.social;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.retromc.core.api.event.ClanUpdate;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.Clan;
import net.retromc.core.api.social.ClanInvite;
import net.retromc.core.api.social.ClanJoinMode;
import net.retromc.core.api.social.ClanJoinRequest;
import net.retromc.core.api.social.ClanMember;
import net.retromc.core.api.social.ClanModeStats;
import net.retromc.core.api.social.ClanOutcome;
import net.retromc.core.api.social.ClanResult;
import net.retromc.core.api.social.ClanRole;
import net.retromc.core.api.social.ClanRules;
import net.retromc.core.api.social.ClanService;
import net.retromc.core.api.social.ClanWar;
import net.retromc.core.api.social.ClanWarStats;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.api.social.Presence;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.util.ClanName;
import net.retromc.core.api.util.ClanTag;
import net.retromc.core.api.util.EloCalculator;
import net.retromc.core.api.util.LevelCurve;
import net.retromc.core.api.util.NetworkTime;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.SqlFunction;
import net.retromc.core.data.ValkeyKeys;
import org.jspecify.annotations.Nullable;

/**
 * {@link ClanService} auf {@code social.clan*} (V1 + V1_1, Spec 01 §3.4, §6.6). Grenzen: 30 Mitglieder + Bonus des
 * Leiter-Rangs, Einladungen 7 Tage, Umbenennen 2×/Tag (Europe/Berlin, Zähler {@code cnt:clanrename:*}), Elo je Modus
 * (K = 50, Nullsumme). Neue Mitglieder beginnen als Testmitglied. Ereignisse: {@code CLAN_UPDATE} und
 * {@code PROFILE_INVALIDATE} (clanTag, clanId) für betroffene Spieler.
 */
public final class ClanRepository implements ClanService {

    private static final String CLAN_COLUMNS = "c.id, c.name, c.tag, c.owner, c.created_at, c.join_mode, c.show_roles,"
            + " c.xp, c.last_active, c.description";
    private static final List<String> CLAN_FIELDS = List.of(Profile.CLAN_TAG, Profile.CLAN_ID);

    private final AsyncDatabase database;
    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<RankService> ranks;
    private final Supplier<IgnoreService> ignores;
    private final EventSink events;
    private final Clock clock;

    public ClanRepository(AsyncDatabase database, RedisAsyncCommands<String, String> valkey,
            Supplier<RankService> ranks, Supplier<IgnoreService> ignores, EventSink events, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Mitgliedschaft in einer Transaktion. */
    private record Membership(long clanId, ClanRole role) {
    }

    /** Ergebnis einer Änderung mit den Spielern, deren Clan-Anzeige sich ändert. */
    private record Change(ClanResult result, long clanId, List<UUID> members, List<UUID> affected) {

        static Change of(ClanOutcome outcome) {
            return new Change(ClanResult.of(outcome), 0, List.of(), List.of());
        }
    }

    /** Ergebnis des Inaktivitäts-Jobs. */
    private record Removed(List<UUID> members, List<Long> clans) {
    }

    // ------------------------------------------------------------------ Abfragen

    @Override
    public CompletableFuture<Optional<Clan>> clanOf(UUID player) {
        return database.query(connection -> clanOf(connection, player));
    }

    @Override
    public CompletableFuture<Optional<Clan>> clan(long clanId) {
        return database.query(connection -> clanById(connection, clanId));
    }

    @Override
    public CompletableFuture<Optional<Clan>> byName(String name) {
        return database.query(connection -> Jdbc.one(connection, "SELECT " + CLAN_COLUMNS
                + " FROM social.clan c WHERE lower(c.name) = lower(?)", ClanRepository::map, name));
    }

    @Override
    public CompletableFuture<Optional<Clan>> byTag(String tag) {
        return database.query(connection -> Jdbc.one(connection, "SELECT " + CLAN_COLUMNS
                + " FROM social.clan c WHERE lower(c.tag) = lower(?)", ClanRepository::map, tag));
    }

    @Override
    public CompletableFuture<List<ClanMember>> members(long clanId) {
        return database.query(connection -> members(connection, clanId));
    }

    @Override
    public CompletableFuture<Optional<ClanRole>> role(UUID player) {
        return database.query(connection -> membership(connection, player, false).map(Membership::role));
    }

    @Override
    public CompletableFuture<OnlineCount> counts(long clanId) {
        return members(clanId).thenCompose(members -> {
            List<CompletableFuture<String>> hidden = members.stream()
                    .map(member -> Jdbc.future(valkey.hget(ValkeyKeys.presence(member.uuid()), Presence.HIDDEN)))
                    .toList();
            return CompletableFuture.allOf(hidden.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                int online = (int) hidden.stream().map(CompletableFuture::join)
                        .filter(value -> value != null && !"1".equals(value)).count();
                return new OnlineCount(online, members.size());
            });
        });
    }

    @Override
    public CompletableFuture<Optional<String>> tag(UUID player) {
        return clanOf(player).thenApply(clan -> clan.map(Clan::tag));
    }

    @Override
    public CompletableFuture<Integer> maxMembers(long clanId) {
        return database.query(connection -> Jdbc.one(connection, "SELECT owner FROM social.clan WHERE id = ?",
                row -> Jdbc.requireUuid(row, "owner"), clanId))
                .thenCompose(owner -> owner.isEmpty()
                        ? CompletableFuture.completedFuture(ClanRules.BASE_MEMBERS)
                        : ranks.get().rank(owner.get())
                                .thenApply(rank -> ClanRules.maxMembers(rank.limits().clanSlotBonus())));
    }

    @Override
    public CompletableFuture<List<ClanInvite>> invites(UUID player) {
        Instant valid = clock.instant().minus(ClanRules.INVITE_TTL);
        return database.query(connection -> Jdbc.list(connection, "SELECT i.clan_id, c.name, c.tag, i.uuid,"
                + " i.invited_by, i.created_at FROM social.clan_invite i JOIN social.clan c ON c.id = i.clan_id"
                + " WHERE i.uuid = ? AND i.created_at > ? ORDER BY i.created_at", row -> new ClanInvite(
                        row.getLong("clan_id"), row.getString("name"), row.getString("tag"),
                        Jdbc.requireUuid(row, "uuid"), Jdbc.uuid(row, "invited_by"),
                        Objects.requireNonNull(Jdbc.instant(row, "created_at"))), player, valid));
    }

    @Override
    public CompletableFuture<List<ClanJoinRequest>> requests(long clanId) {
        return database.query(connection -> requests(connection, clanId));
    }

    @Override
    public CompletableFuture<Set<ModeKey>> statsModes(long clanId) {
        return database.query(connection -> Set.copyOf(Jdbc.list(connection, "SELECT mode FROM"
                + " social.clan_stats_mode WHERE clan_id = ? AND enabled", row -> ModeKey.of(row.getString("mode")),
                clanId)));
    }

    @Override
    public CompletableFuture<Optional<ClanModeStats>> stats(long clanId, ModeKey mode) {
        return database.query(connection -> {
            if (!statsEnabled(connection, clanId, mode)) {
                return Optional.<ClanModeStats>empty();
            }
            return Jdbc.one(connection, "SELECT count(DISTINCT m.uuid) AS members, COALESCE(sum(a.kills), 0) AS k,"
                    + " COALESCE(sum(a.deaths), 0) AS d, COALESCE(sum(a.games), 0) AS g, COALESCE(sum(a.wins), 0) AS w,"
                    + " COALESCE(sum(a.points), 0) AS p FROM social.clan_member m LEFT JOIN stats.alltime a"
                    + " ON a.uuid = m.uuid AND a.mode_id = (SELECT id FROM stats.mode WHERE key = ?)"
                    + " WHERE m.clan_id = ?", row -> new ClanModeStats(clanId, mode, row.getInt("members"),
                            row.getLong("k"), row.getLong("d"), row.getLong("g"), row.getLong("w"), row.getLong("p")),
                    mode.key(), clanId);
        });
    }

    @Override
    public CompletableFuture<Optional<ClanWarStats>> warStats(long clanId, ModeKey mode) {
        return database.query(connection -> Jdbc.one(connection, "SELECT elo, games, wins, losses, place FROM"
                + " (SELECT clan_id, elo, games, wins, losses, ROW_NUMBER() OVER (PARTITION BY mode"
                + " ORDER BY elo DESC, clan_id) AS place FROM social.clan_elo WHERE mode = ?) ranked"
                + " WHERE clan_id = ?", row -> new ClanWarStats(clanId, mode, row.getInt("elo"), row.getInt("place"),
                        row.getInt("games"), row.getInt("wins"), row.getInt("losses")), mode.key(), clanId));
    }

    @Override
    public CompletableFuture<Integer> elo(long clanId, ModeKey mode) {
        return database.query(connection -> Jdbc.one(connection, "SELECT elo FROM social.clan_elo"
                + " WHERE clan_id = ? AND mode = ?", row -> row.getInt("elo"), clanId, mode.key())
                .orElse(EloCalculator.START_RATING));
    }

    @Override
    public CompletableFuture<Integer> warsToday(long clanId, ModeKey mode) {
        Instant dayStart = NetworkTime.startOfDay(NetworkTime.day(clock.instant()));
        return database.query(connection -> (int) Jdbc.count(connection, "SELECT count(*) FROM social.clan_war"
                + " WHERE mode = ? AND (clan_a = ? OR clan_b = ?) AND COALESCE(ended_at, started_at) >= ?",
                mode.key(), clanId, clanId, dayStart));
    }

    @Override
    public CompletableFuture<LevelCurve.Progress> level(long clanId) {
        return database.query(connection -> LevelCurve.progress(Jdbc.one(connection,
                "SELECT xp FROM social.clan WHERE id = ?", row -> row.getLong("xp"), clanId).orElse(0L)));
    }

    @Override
    public CompletableFuture<List<UUID>> statusRecipients(UUID player) {
        return database.query(connection -> {
            boolean hidden = Jdbc.one(connection, "SELECT hide_from_clan FROM social.privacy WHERE uuid = ?",
                    row -> row.getBoolean("hide_from_clan"), player).orElse(false);
            if (hidden) {
                return List.of();
            }
            return Jdbc.list(connection, "SELECT m.uuid FROM social.clan_member m"
                    + " LEFT JOIN social.privacy pr ON pr.uuid = m.uuid WHERE m.clan_id ="
                    + " (SELECT clan_id FROM social.clan_member WHERE uuid = ?) AND m.uuid <> ?"
                    + " AND COALESCE(pr.notify_clan, true)", row -> Jdbc.requireUuid(row, "uuid"), player, player);
        });
    }

    // ------------------------------------------------------------------ Gründen, Beitreten, Verlassen

    @Override
    public CompletableFuture<ClanResult> create(UUID owner, String name, String tag) {
        if (!ClanName.valid(name)) {
            return done(ClanOutcome.INVALID_NAME);
        }
        if (!ClanTag.valid(tag)) {
            return done(ClanOutcome.INVALID_TAG);
        }
        Instant now = clock.instant();
        return change(connection -> {
            if (membership(connection, owner, true).isPresent()) {
                return Change.of(ClanOutcome.IN_CLAN);
            }
            Optional<ClanOutcome> taken = taken(connection, name, tag, -1);
            if (taken.isPresent()) {
                return Change.of(taken.get());
            }
            long id = Jdbc.one(connection, "INSERT INTO social.clan (name, tag, owner, created_at, last_active)"
                    + " VALUES (?, ?, ?, ?, ?) RETURNING id", row -> row.getLong("id"), name, tag, owner, now, now)
                    .orElseThrow();
            Jdbc.update(connection, "INSERT INTO social.clan_member (clan_id, uuid, role, joined_at)"
                    + " VALUES (?, ?, ?, ?)", id, owner, (short) ClanRole.LEADER.code(), now);
            clearPending(connection, owner);
            return result(connection, ClanOutcome.CREATED, id, List.of(owner));
        });
    }

    @Override
    public CompletableFuture<ClanResult> invite(UUID actor, UUID target) {
        if (actor.equals(target)) {
            return done(ClanOutcome.SELF);
        }
        return withCapacity(actor, max -> ignores.get().ignores(target, actor).thenCompose(blocked -> blocked
                ? done(ClanOutcome.BLOCKED)
                : change(connection -> {
                    Optional<Membership> self = membership(connection, actor, false);
                    if (self.isEmpty()) {
                        return Change.of(ClanOutcome.NOT_IN_CLAN);
                    }
                    if (!self.get().role().canInvite()) {
                        return Change.of(ClanOutcome.NOT_ALLOWED);
                    }
                    long clanId = self.get().clanId();
                    if (membership(connection, target, false).isPresent()) {
                        return Change.of(ClanOutcome.TARGET_IN_CLAN);
                    }
                    if (memberCount(connection, clanId) >= max) {
                        return Change.of(ClanOutcome.FULL);
                    }
                    Instant now = clock.instant();
                    if (Jdbc.count(connection, "SELECT count(*) FROM social.clan_invite WHERE clan_id = ? AND uuid = ?"
                            + " AND created_at > ?", clanId, target, now.minus(ClanRules.INVITE_TTL)) > 0) {
                        return Change.of(ClanOutcome.ALREADY_INVITED);
                    }
                    Jdbc.update(connection, "INSERT INTO social.clan_invite (clan_id, uuid, invited_by, created_at)"
                            + " VALUES (?, ?, ?, ?) ON CONFLICT (clan_id, uuid) DO UPDATE SET invited_by ="
                            + " EXCLUDED.invited_by, created_at = EXCLUDED.created_at", clanId, target, actor, now);
                    return result(connection, ClanOutcome.INVITED, clanId, List.of());
                })));
    }

    @Override
    public CompletableFuture<ClanResult> join(UUID player, String clanName) {
        return byName(clanName).thenCompose(found -> {
            if (found.isEmpty()) {
                return done(ClanOutcome.UNKNOWN_CLAN);
            }
            long clanId = found.get().id();
            return maxMembers(clanId).thenCompose(max -> change(connection -> {
                if (membership(connection, player, true).isPresent()) {
                    return Change.of(ClanOutcome.IN_CLAN);
                }
                if (Jdbc.count(connection, "SELECT count(*) FROM social.clan_invite WHERE clan_id = ? AND uuid = ?"
                        + " AND created_at > ?", clanId, player, clock.instant().minus(ClanRules.INVITE_TTL)) == 0) {
                    return Change.of(ClanOutcome.NO_INVITE);
                }
                if (memberCount(connection, clanId) >= max) {
                    return Change.of(ClanOutcome.FULL);
                }
                addMember(connection, clanId, player);
                return result(connection, ClanOutcome.JOINED, clanId, List.of(player));
            }));
        });
    }

    @Override
    public CompletableFuture<ClanResult> request(UUID player, String tag) {
        return byTag(tag).thenCompose(found -> {
            if (found.isEmpty()) {
                return done(ClanOutcome.UNKNOWN_CLAN);
            }
            Clan clan = found.get();
            if (clan.joinMode() != ClanJoinMode.INVITE_AND_REQUEST) {
                return done(ClanOutcome.REQUESTS_DISABLED);
            }
            return maxMembers(clan.id()).thenCompose(max -> change(connection -> {
                if (membership(connection, player, false).isPresent()) {
                    return Change.of(ClanOutcome.IN_CLAN);
                }
                if (Jdbc.count(connection, "SELECT count(*) FROM social.clan_request WHERE clan_id = ? AND uuid = ?",
                        clan.id(), player) > 0) {
                    return Change.of(ClanOutcome.ALREADY_REQUESTED);
                }
                if (memberCount(connection, clan.id()) >= max) {
                    return Change.of(ClanOutcome.FULL);
                }
                Jdbc.update(connection, "INSERT INTO social.clan_request (clan_id, uuid, created_at) VALUES (?, ?, ?)",
                        clan.id(), player, clock.instant());
                return result(connection, ClanOutcome.REQUEST_SENT, clan.id(), List.of());
            }));
        });
    }

    @Override
    public CompletableFuture<ClanResult> acceptRequest(UUID actor, UUID requester) {
        return withCapacity(actor, max -> change(connection -> {
            Optional<Membership> self = membership(connection, actor, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (!self.get().role().canInvite()) {
                return Change.of(ClanOutcome.NOT_ALLOWED);
            }
            long clanId = self.get().clanId();
            if (Jdbc.update(connection, "DELETE FROM social.clan_request WHERE clan_id = ? AND uuid = ?", clanId,
                    requester) == 0) {
                return Change.of(ClanOutcome.NO_REQUEST);
            }
            if (membership(connection, requester, true).isPresent()) {
                return Change.of(ClanOutcome.TARGET_IN_CLAN);
            }
            if (memberCount(connection, clanId) >= max) {
                return Change.of(ClanOutcome.FULL);
            }
            addMember(connection, clanId, requester);
            return result(connection, ClanOutcome.JOINED, clanId, List.of(requester));
        }));
    }

    @Override
    public CompletableFuture<ClanResult> denyRequest(UUID actor, UUID requester) {
        return change(connection -> {
            Optional<Membership> self = membership(connection, actor, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (!self.get().role().canInvite()) {
                return Change.of(ClanOutcome.NOT_ALLOWED);
            }
            long clanId = self.get().clanId();
            if (Jdbc.update(connection, "DELETE FROM social.clan_request WHERE clan_id = ? AND uuid = ?", clanId,
                    requester) == 0) {
                return Change.of(ClanOutcome.NO_REQUEST);
            }
            return result(connection, ClanOutcome.REQUEST_DENIED, clanId, List.of());
        });
    }

    @Override
    public CompletableFuture<ClanResult> leave(UUID player) {
        return change(connection -> {
            Optional<Membership> self = membership(connection, player, true);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (self.get().role() == ClanRole.LEADER) {
                return Change.of(ClanOutcome.LEADER_CANNOT_LEAVE);
            }
            Jdbc.update(connection, "DELETE FROM social.clan_member WHERE uuid = ?", player);
            return result(connection, ClanOutcome.LEFT, self.get().clanId(), List.of(player));
        });
    }

    @Override
    public CompletableFuture<ClanResult> kick(UUID actor, UUID target) {
        if (actor.equals(target)) {
            return done(ClanOutcome.SELF);
        }
        return change(connection -> {
            Optional<Membership> self = membership(connection, actor, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            Optional<Membership> other = membership(connection, target, true);
            if (other.isEmpty() || other.get().clanId() != self.get().clanId()) {
                return Change.of(ClanOutcome.NOT_MEMBER);
            }
            if (!self.get().role().canKick(other.get().role())) {
                return Change.of(ClanOutcome.NOT_ALLOWED);
            }
            Jdbc.update(connection, "DELETE FROM social.clan_member WHERE uuid = ?", target);
            return result(connection, ClanOutcome.KICKED, self.get().clanId(), List.of(target));
        });
    }

    // ------------------------------------------------------------------ Rollen und Verwaltung (Leiter)

    @Override
    public CompletableFuture<ClanResult> promote(UUID leader, UUID target) {
        return roleChange(leader, target, true);
    }

    @Override
    public CompletableFuture<ClanResult> demote(UUID leader, UUID target) {
        return roleChange(leader, target, false);
    }

    private CompletableFuture<ClanResult> roleChange(UUID leader, UUID target, boolean up) {
        if (leader.equals(target)) {
            return done(ClanOutcome.SELF);
        }
        return change(connection -> {
            Optional<Membership> self = membership(connection, leader, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (self.get().role() != ClanRole.LEADER) {
                return Change.of(ClanOutcome.NOT_LEADER);
            }
            Optional<Membership> other = membership(connection, target, true);
            if (other.isEmpty() || other.get().clanId() != self.get().clanId()) {
                return Change.of(ClanOutcome.NOT_MEMBER);
            }
            Optional<ClanRole> next = up ? other.get().role().promoted() : other.get().role().demoted();
            if (next.isEmpty()) {
                return Change.of(ClanOutcome.NO_FURTHER_ROLE);
            }
            if (next.get() == ClanRole.LEADER) {
                return new Change(new ClanResult(ClanOutcome.CONFIRM_TRANSFER,
                        clanById(connection, self.get().clanId()).orElse(null)), 0, List.of(), List.of());
            }
            Jdbc.update(connection, "UPDATE social.clan_member SET role = ? WHERE uuid = ?",
                    (short) next.get().code(), target);
            return result(connection, up ? ClanOutcome.PROMOTED : ClanOutcome.DEMOTED, self.get().clanId(),
                    List.of(target));
        });
    }

    @Override
    public CompletableFuture<ClanResult> transferLeadership(UUID leader, UUID target) {
        if (leader.equals(target)) {
            return done(ClanOutcome.SELF);
        }
        return change(connection -> {
            Optional<Membership> self = membership(connection, leader, true);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (self.get().role() != ClanRole.LEADER) {
                return Change.of(ClanOutcome.NOT_LEADER);
            }
            Optional<Membership> other = membership(connection, target, true);
            if (other.isEmpty() || other.get().clanId() != self.get().clanId()) {
                return Change.of(ClanOutcome.NOT_MEMBER);
            }
            Jdbc.update(connection, "UPDATE social.clan_member SET role = ? WHERE uuid = ?",
                    (short) ClanRole.MODERATOR.code(), leader);
            Jdbc.update(connection, "UPDATE social.clan_member SET role = ? WHERE uuid = ?",
                    (short) ClanRole.LEADER.code(), target);
            Jdbc.update(connection, "UPDATE social.clan SET owner = ? WHERE id = ?", target, self.get().clanId());
            return result(connection, ClanOutcome.LEADER_TRANSFERRED, self.get().clanId(), List.of(leader, target));
        });
    }

    @Override
    public CompletableFuture<ClanResult> rename(UUID leader, String name, String tag) {
        if (!ClanName.valid(name)) {
            return done(ClanOutcome.INVALID_NAME);
        }
        if (!ClanTag.valid(tag)) {
            return done(ClanOutcome.INVALID_TAG);
        }
        return clanOf(leader).thenCompose(found -> {
            if (found.isEmpty()) {
                return done(ClanOutcome.NOT_IN_CLAN);
            }
            long clanId = found.get().id();
            LocalDate day = NetworkTime.day(clock.instant());
            String counter = ValkeyKeys.clanRenameCount(clanId, day);
            return Jdbc.future(valkey.get(counter)).thenCompose(used -> {
                if (!ClanRules.renameAllowed(used == null ? 0 : Integer.parseInt(used))) {
                    return done(ClanOutcome.RENAME_LIMIT);
                }
                return change(connection -> {
                    Optional<Membership> self = membership(connection, leader, false);
                    if (self.isEmpty() || self.get().clanId() != clanId) {
                        return Change.of(ClanOutcome.NOT_IN_CLAN);
                    }
                    if (self.get().role() != ClanRole.LEADER) {
                        return Change.of(ClanOutcome.NOT_LEADER);
                    }
                    Optional<ClanOutcome> taken = taken(connection, name, tag, clanId);
                    if (taken.isPresent()) {
                        return Change.of(taken.get());
                    }
                    Clan old = clanById(connection, clanId).orElseThrow();
                    Jdbc.update(connection, "INSERT INTO social.clan_rename_log (clan_id, at, old_name, old_tag)"
                            + " VALUES (?, clock_timestamp(), ?, ?)", clanId, old.name(), old.tag());
                    Jdbc.update(connection, "UPDATE social.clan SET name = ?, tag = ? WHERE id = ?", name, tag, clanId);
                    return result(connection, ClanOutcome.RENAMED, clanId, memberIds(connection, clanId));
                }).thenCompose(result -> result.outcome() != ClanOutcome.RENAMED
                        ? CompletableFuture.completedFuture(result)
                        : Jdbc.future(valkey.incr(counter))
                                .thenCompose(ignored -> Jdbc.future(valkey.expire(counter,
                                        ValkeyKeys.DAILY_COUNTER_TTL.toSeconds())))
                                .thenApply(ignored -> result));
            });
        });
    }

    @Override
    public CompletableFuture<ClanResult> delete(UUID leader) {
        return change(connection -> {
            Optional<Membership> self = membership(connection, leader, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (self.get().role() != ClanRole.LEADER) {
                return Change.of(ClanOutcome.NOT_LEADER);
            }
            long clanId = self.get().clanId();
            Clan clan = clanById(connection, clanId).orElseThrow();
            List<UUID> members = memberIds(connection, clanId);
            Jdbc.update(connection, "DELETE FROM social.clan WHERE id = ?", clanId);
            return new Change(new ClanResult(ClanOutcome.DELETED, clan), clanId, List.of(), members);
        });
    }

    @Override
    public CompletableFuture<ClanResult> setJoinMode(UUID leader, ClanJoinMode mode) {
        return leaderUpdate(leader, "UPDATE social.clan SET join_mode = ? WHERE id = ?", (short) mode.code());
    }

    @Override
    public CompletableFuture<ClanResult> setShowRoles(UUID leader, boolean showRoles) {
        return leaderUpdate(leader, "UPDATE social.clan SET show_roles = ? WHERE id = ?", showRoles);
    }

    private CompletableFuture<ClanResult> leaderUpdate(UUID leader, String sql, Object value) {
        return change(connection -> {
            Optional<Membership> self = membership(connection, leader, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (self.get().role() != ClanRole.LEADER) {
                return Change.of(ClanOutcome.NOT_LEADER);
            }
            Jdbc.update(connection, sql, value, self.get().clanId());
            return result(connection, ClanOutcome.UPDATED, self.get().clanId(), List.of());
        });
    }

    @Override
    public CompletableFuture<ClanResult> toggleStatsMode(UUID leader, ModeKey mode) {
        return change(connection -> {
            Optional<Membership> self = membership(connection, leader, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (self.get().role() != ClanRole.LEADER) {
                return Change.of(ClanOutcome.NOT_LEADER);
            }
            long clanId = self.get().clanId();
            boolean enabled = Jdbc.one(connection, "INSERT INTO social.clan_stats_mode (clan_id, mode, enabled)"
                    + " VALUES (?, ?, true) ON CONFLICT (clan_id, mode) DO UPDATE SET enabled ="
                    + " NOT social.clan_stats_mode.enabled RETURNING enabled", row -> row.getBoolean("enabled"),
                    clanId, mode.key()).orElse(false);
            return result(connection, enabled ? ClanOutcome.STATS_ENABLED : ClanOutcome.STATS_DISABLED, clanId,
                    List.of());
        });
    }

    @Override
    public CompletableFuture<ClanResult> resetStats(UUID leader, ModeKey mode) {
        return change(connection -> {
            Optional<Membership> self = membership(connection, leader, false);
            if (self.isEmpty()) {
                return Change.of(ClanOutcome.NOT_IN_CLAN);
            }
            if (self.get().role() != ClanRole.LEADER) {
                return Change.of(ClanOutcome.NOT_LEADER);
            }
            long clanId = self.get().clanId();
            if (Jdbc.update(connection, "UPDATE economy.wallet SET reset_tokens = reset_tokens - ?"
                    + " WHERE uuid = ? AND reset_tokens >= ?", ClanRules.RESET_TOKEN_COST, leader,
                    ClanRules.RESET_TOKEN_COST) == 0) {
                return Change.of(ClanOutcome.NO_RESET_TOKEN);
            }
            Jdbc.update(connection, "INSERT INTO economy.ledger (uuid, delta, reason, ref, currency)"
                    + " VALUES (?, ?, 'clanstatsreset', ?, 'RESET_TOKEN')", leader, -ClanRules.RESET_TOKEN_COST,
                    clanId + ":" + mode.key());
            Jdbc.update(connection, "UPDATE social.clan_elo SET elo = ?, games = 0, wins = 0, losses = 0,"
                    + " updated_at = now() WHERE clan_id = ? AND mode = ?", EloCalculator.START_RATING, clanId,
                    mode.key());
            Jdbc.update(connection, "UPDATE social.clan_war SET hidden = true WHERE mode = ?"
                    + " AND (clan_a = ? OR clan_b = ?)", mode.key(), clanId, clanId);
            return result(connection, ClanOutcome.STATS_RESET, clanId, List.of());
        });
    }

    // ------------------------------------------------------------------ Clan-Wars, XP, Aktivität

    @Override
    public CompletableFuture<ClanWar> recordWar(ModeKey mode, long clanA, long clanB, @Nullable Long winner,
            boolean ranked, @Nullable UUID matchId) {
        if (clanA == clanB) {
            throw new IllegalArgumentException("Ein Clan kann nicht gegen sich selbst spielen");
        }
        if (winner != null && winner != clanA && winner != clanB) {
            throw new IllegalArgumentException("Sieger muss einer der beiden Clans sein");
        }
        Instant now = clock.instant();
        return database.transaction(connection -> {
            int delta = 0;
            if (ranked) {
                for (long clan : new long[] {Math.min(clanA, clanB), Math.max(clanA, clanB)}) {
                    Jdbc.update(connection, "INSERT INTO social.clan_elo (clan_id, mode) VALUES (?, ?)"
                            + " ON CONFLICT DO NOTHING", clan, mode.key());
                }
                int ratingA = lockElo(connection, clanA, mode);
                int ratingB = lockElo(connection, clanB, mode);
                double scoreA = winner == null ? 0.5 : winner == clanA ? 1.0 : 0.0;
                EloCalculator.Update update = EloCalculator.apply(ratingA, ratingB, scoreA, EloCalculator.CLAN_K);
                delta = update.delta();
                updateElo(connection, clanA, mode, update.ratingA(), winner, clanA);
                updateElo(connection, clanB, mode, update.ratingB(), winner, clanB);
            }
            addXp(connection, clanA, ClanRules.warXp(ranked, winner != null && winner == clanA));
            addXp(connection, clanB, ClanRules.warXp(ranked, winner != null && winner == clanB));
            long id = Jdbc.one(connection, "INSERT INTO social.clan_war (mode, ranked, clan_a, clan_b, winner,"
                    + " elo_delta, match_id, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                    row -> row.getLong("id"), mode.key(), ranked, clanA, clanB, winner, Math.abs(delta), matchId, now,
                    now).orElseThrow();
            return new ClanWar(id, mode, ranked, clanA, clanB, winner, Math.abs(delta), matchId, false, now, now);
        });
    }

    private static int lockElo(Connection connection, long clanId, ModeKey mode) throws SQLException {
        return Jdbc.one(connection, "SELECT elo FROM social.clan_elo WHERE clan_id = ? AND mode = ? FOR UPDATE",
                row -> row.getInt("elo"), clanId, mode.key()).orElse(EloCalculator.START_RATING);
    }

    private static void updateElo(Connection connection, long clanId, ModeKey mode, int elo, @Nullable Long winner,
            long self) throws SQLException {
        int win = winner != null && winner == self ? 1 : 0;
        int loss = winner != null && winner != self ? 1 : 0;
        Jdbc.update(connection, "UPDATE social.clan_elo SET elo = ?, games = games + 1, wins = wins + ?,"
                + " losses = losses + ?, updated_at = now() WHERE clan_id = ? AND mode = ?", elo, win, loss, clanId,
                mode.key());
    }

    private static long addXp(Connection connection, long clanId, long xp) throws SQLException {
        return Jdbc.one(connection, "UPDATE social.clan SET xp = xp + ? WHERE id = ? RETURNING xp",
                row -> row.getLong("xp"), Math.max(0, xp), clanId).orElse(0L);
    }

    @Override
    public CompletableFuture<LevelCurve.Progress> addXp(long clanId, long xp) {
        return database.transaction(connection -> LevelCurve.progress(addXp(connection, clanId, xp)));
    }

    @Override
    public CompletableFuture<Void> markActive(UUID player) {
        return database.transaction(connection -> Jdbc.update(connection, "UPDATE social.clan SET last_active = ?"
                + " WHERE id = (SELECT clan_id FROM social.clan_member WHERE uuid = ?)", clock.instant(), player))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Integer> deleteInactive(Instant cutoff) {
        return database.transaction(connection -> {
            List<UUID> members = Jdbc.list(connection, "SELECT m.uuid FROM social.clan_member m"
                    + " JOIN social.clan c ON c.id = m.clan_id WHERE c.last_active < ?",
                    row -> Jdbc.requireUuid(row, "uuid"), cutoff);
            List<Long> deleted = Jdbc.list(connection, "DELETE FROM social.clan WHERE last_active < ? RETURNING id",
                    row -> row.getLong("id"), cutoff);
            return new Removed(members, deleted);
        }).thenApply(removed -> {
            removed.clans().forEach(clanId -> events.publish(new ClanUpdate(clanId, "", List.of())));
            removed.members().forEach(member -> events.publish(new ProfileInvalidate(member, CLAN_FIELDS)));
            return removed.clans().size();
        });
    }

    // ------------------------------------------------------------------ Hilfen

    /** Clan des Akteurs, dann Höchstzahl nach Leiter-Rang, dann die Aktion. */
    private CompletableFuture<ClanResult> withCapacity(UUID actor,
            Function<Integer, CompletableFuture<ClanResult>> action) {
        return clanOf(actor).thenCompose(clan -> clan.isEmpty()
                ? done(ClanOutcome.NOT_IN_CLAN)
                : maxMembers(clan.get().id()).thenCompose(action));
    }

    private CompletableFuture<ClanResult> change(SqlFunction<Connection, Change> work) {
        return database.transaction(work).thenApply(this::publish);
    }

    private ClanResult publish(Change change) {
        ClanOutcome outcome = change.result().outcome();
        if (change.clanId() == 0) {
            return change.result();
        }
        if (outcome == ClanOutcome.DELETED) {
            Clan clan = change.result().clan();
            events.publish(new ClanUpdate(change.clanId(), clan == null ? "" : clan.tag(), List.of()));
        } else if (change.result().clan() != null) {
            events.publish(new ClanUpdate(change.clanId(), change.result().clan().tag(), change.members()));
        }
        change.affected().forEach(player -> events.publish(new ProfileInvalidate(player, CLAN_FIELDS)));
        return change.result();
    }

    private static CompletableFuture<ClanResult> done(ClanOutcome outcome) {
        return CompletableFuture.completedFuture(ClanResult.of(outcome));
    }

    private static Change result(Connection connection, ClanOutcome outcome, long clanId, List<UUID> affected)
            throws SQLException {
        return new Change(new ClanResult(outcome, clanById(connection, clanId).orElse(null)), clanId,
                memberIds(connection, clanId), affected);
    }

    private static Optional<Clan> clanById(Connection connection, long clanId) throws SQLException {
        return Jdbc.one(connection, "SELECT " + CLAN_COLUMNS + " FROM social.clan c WHERE c.id = ?",
                ClanRepository::map, clanId);
    }

    private static Optional<Clan> clanOf(Connection connection, UUID player) throws SQLException {
        return Jdbc.one(connection, "SELECT " + CLAN_COLUMNS + " FROM social.clan c JOIN social.clan_member m"
                + " ON m.clan_id = c.id WHERE m.uuid = ?", ClanRepository::map, player);
    }

    private static Optional<Membership> membership(Connection connection, UUID player, boolean lock)
            throws SQLException {
        return Jdbc.one(connection, "SELECT clan_id, role FROM social.clan_member WHERE uuid = ?"
                + (lock ? " FOR UPDATE" : ""), row -> new Membership(row.getLong("clan_id"),
                        ClanRole.ofCode(row.getInt("role"))), player);
    }

    private static List<ClanMember> members(Connection connection, long clanId) throws SQLException {
        List<ClanMember> members = new ArrayList<>(Jdbc.list(connection, "SELECT m.uuid,"
                + " COALESCE(p.name, m.uuid::text) AS name, m.role, m.joined_at FROM social.clan_member m"
                + " LEFT JOIN core.player p ON p.uuid = m.uuid WHERE m.clan_id = ?", row -> new ClanMember(
                        Jdbc.requireUuid(row, "uuid"), row.getString("name"), ClanRole.ofCode(row.getInt("role")),
                        Jdbc.instant(row, "joined_at")), clanId));
        members.sort(ClanMember.ROLE_ORDER);
        return List.copyOf(members);
    }

    private static List<UUID> memberIds(Connection connection, long clanId) throws SQLException {
        return Jdbc.list(connection, "SELECT uuid FROM social.clan_member WHERE clan_id = ?",
                row -> Jdbc.requireUuid(row, "uuid"), clanId);
    }

    private static long memberCount(Connection connection, long clanId) throws SQLException {
        return Jdbc.count(connection, "SELECT count(*) FROM social.clan_member WHERE clan_id = ?", clanId);
    }

    private static List<ClanJoinRequest> requests(Connection connection, long clanId) throws SQLException {
        return Jdbc.list(connection, "SELECT r.clan_id, r.uuid, COALESCE(p.name, r.uuid::text) AS name, r.created_at"
                + " FROM social.clan_request r LEFT JOIN core.player p ON p.uuid = r.uuid WHERE r.clan_id = ?"
                + " ORDER BY r.created_at", row -> new ClanJoinRequest(row.getLong("clan_id"),
                        Jdbc.requireUuid(row, "uuid"), row.getString("name"),
                        Objects.requireNonNull(Jdbc.instant(row, "created_at"))), clanId);
    }

    private static boolean statsEnabled(Connection connection, long clanId, ModeKey mode) throws SQLException {
        return Jdbc.count(connection, "SELECT count(*) FROM social.clan_stats_mode WHERE clan_id = ? AND mode = ?"
                + " AND enabled", clanId, mode.key()) > 0;
    }

    /** Name/Tag ohne Groß-/Kleinschreibung schon vergeben (außer an {@code ownId})? */
    private static Optional<ClanOutcome> taken(Connection connection, String name, String tag, long ownId)
            throws SQLException {
        if (Jdbc.count(connection, "SELECT count(*) FROM social.clan WHERE lower(name) = lower(?) AND id <> ?", name,
                ownId) > 0) {
            return Optional.of(ClanOutcome.NAME_TAKEN);
        }
        if (Jdbc.count(connection, "SELECT count(*) FROM social.clan WHERE lower(tag) = lower(?) AND id <> ?", tag,
                ownId) > 0) {
            return Optional.of(ClanOutcome.TAG_TAKEN);
        }
        return Optional.empty();
    }

    private void addMember(Connection connection, long clanId, UUID player) throws SQLException {
        Jdbc.update(connection, "INSERT INTO social.clan_member (clan_id, uuid, role, joined_at) VALUES (?, ?, ?, ?)",
                clanId, player, (short) ClanRole.TRIAL.code(), clock.instant());
        clearPending(connection, player);
    }

    /** Offene Einladungen und Anfragen eines Spielers, der jetzt in einem Clan ist. */
    private static void clearPending(Connection connection, UUID player) throws SQLException {
        Jdbc.update(connection, "DELETE FROM social.clan_invite WHERE uuid = ?", player);
        Jdbc.update(connection, "DELETE FROM social.clan_request WHERE uuid = ?", player);
    }

    static Clan map(ResultSet row) throws SQLException {
        Instant created = Jdbc.instant(row, "created_at");
        Instant active = Jdbc.instant(row, "last_active");
        return new Clan(row.getLong("id"), row.getString("name"), row.getString("tag"),
                Jdbc.requireUuid(row, "owner"), created == null ? Instant.EPOCH : created,
                ClanJoinMode.ofCode(row.getInt("join_mode")), row.getBoolean("show_roles"), row.getLong("xp"),
                active == null ? Instant.EPOCH : active, row.getString("description"));
    }
}
