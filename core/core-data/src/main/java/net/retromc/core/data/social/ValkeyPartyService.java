package net.retromc.core.data.social;

import io.lettuce.core.Range;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import net.retromc.core.api.event.PartyDisband;
import net.retromc.core.api.event.PartyUpdate;
import net.retromc.core.api.rank.RankInfo;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.core.api.social.Party;
import net.retromc.core.api.social.PartyInvite;
import net.retromc.core.api.social.PartyMember;
import net.retromc.core.api.social.PartyOutcome;
import net.retromc.core.api.social.PartyResult;
import net.retromc.core.api.social.PartyService;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.PublicFilter;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.api.util.Ulid;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;

/**
 * {@link PartyService} nur in Valkey (Spec 01 §2.3, §3.3, §6.5): {@code party:{id}} (HASH), {@code party:{id}:members}
 * (ZSET Beitritt), {@code party:{id}:requests} (ZSET, 60 s), {@code player:{uuid}:party}, {@code party:invite:{target}}
 * (HASH partyId → inviter;expiresMs, 60 s), {@code party:public}, {@code party:grace:{uuid}} (300 s). Ergänzung:
 * {@code party:{id}:invites} (ZSET Ziel → Ablauf) für „keine offene Einladung“ (§6.5 Nr. 7). Alle Schlüssel einer
 * Party leben 30 min ab der letzten Aktivität. Größe aus dem Rang des Leiters (inkl. Leiter: 3/10/26/64).
 * Ereignisse: {@code PARTY_UPDATE}, {@code PARTY_DISBAND}, {@code PARTY_INVITE}.
 */
public final class ValkeyPartyService implements PartyService {

    /** Mitglied atomar hinzufügen: -1 schon in einer Party, 0 voll, 1 aufgenommen. */
    private static final String ADD_MEMBER = "if redis.call('exists', KEYS[2]) == 1 then return -1 end "
            + "if redis.call('zcard', KEYS[1]) >= tonumber(ARGV[3]) then return 0 end "
            + "redis.call('zadd', KEYS[1], ARGV[2], ARGV[1]) "
            + "redis.call('set', KEYS[2], ARGV[4], 'EX', ARGV[5]) return 1";

    private final RedisAsyncCommands<String, String> valkey;
    private final Supplier<RankService> ranks;
    private final Supplier<SettingsService> settings;
    private final Supplier<FriendService> friends;
    private final Supplier<IgnoreService> ignores;
    private final Function<UUID, CompletableFuture<Boolean>> online;
    private final EventSink events;
    private final Clock clock;

    public ValkeyPartyService(RedisAsyncCommands<String, String> valkey, Supplier<RankService> ranks,
            Supplier<SettingsService> settings, Supplier<FriendService> friends, Supplier<IgnoreService> ignores,
            Function<UUID, CompletableFuture<Boolean>> online, EventSink events, Clock clock) {
        this.valkey = Objects.requireNonNull(valkey, "valkey");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.friends = Objects.requireNonNull(friends, "friends");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.online = Objects.requireNonNull(online, "online");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    private static String invitesOf(String partyId) {
        return ValkeyKeys.party(partyId) + ":invites";
    }

    private static CompletableFuture<PartyResult> done(PartyOutcome outcome) {
        return CompletableFuture.completedFuture(PartyResult.of(outcome));
    }

    // ------------------------------------------------------------------ Lesen

    @Override
    public CompletableFuture<Optional<Party>> partyOf(UUID player) {
        return Jdbc.future(valkey.get(ValkeyKeys.playerParty(player))).thenCompose(id -> id == null
                ? CompletableFuture.completedFuture(Optional.<Party>empty()) : party(id));
    }

    @Override
    public CompletableFuture<Optional<Party>> party(String partyId) {
        CompletableFuture<Map<String, String>> hash = Jdbc.future(valkey.hgetall(ValkeyKeys.party(partyId)));
        CompletableFuture<List<ScoredValue<String>>> members = Jdbc.future(valkey.zrangeWithScores(
                ValkeyKeys.partyMembers(partyId), 0, -1));
        return hash.thenCombine(members, (fields, scored) -> {
            if (fields == null || !fields.containsKey("leader") || scored.isEmpty()) {
                return Optional.<Party>empty();
            }
            List<PartyMember> list = scored.stream().map(value -> new PartyMember(UUID.fromString(value.getValue()),
                    Instant.ofEpochMilli((long) value.getScore()))).toList();
            UUID leader = UUID.fromString(fields.get("leader"));
            if (list.stream().noneMatch(member -> member.uuid().equals(leader))) {
                return Optional.<Party>empty();
            }
            return Optional.of(new Party(partyId, leader, Instant.ofEpochMilli(Long.parseLong(
                    fields.getOrDefault("created", "0"))), "1".equals(fields.get("public")),
                    PublicFilter.valueOf(fields.getOrDefault("publicFilter", PublicFilter.ALL.name())),
                    "1".equals(fields.get("requireRequest")), Integer.parseInt(fields.getOrDefault("maxSize", "1")),
                    list));
        });
    }

    @Override
    public CompletableFuture<List<PartyInvite>> invites(UUID target) {
        Instant now = clock.instant();
        return Jdbc.future(valkey.hgetall(ValkeyKeys.partyInvites(target))).thenApply(hash -> {
            List<PartyInvite> result = new ArrayList<>();
            if (hash != null) {
                hash.forEach((partyId, value) -> {
                    PartyInvite invite = PartyInvite.fromHash(target, partyId, value);
                    if (!invite.expired(now)) {
                        result.add(invite);
                    }
                });
            }
            return List.copyOf(result);
        });
    }

    @Override
    public CompletableFuture<List<UUID>> joinRequests(UUID leader) {
        long since = clock.millis() - REQUEST_TTL.toMillis();
        return partyOf(leader).thenCompose(party -> party.isEmpty() || !party.get().isLeader(leader)
                ? CompletableFuture.completedFuture(List.<UUID>of())
                : Jdbc.future(valkey.zrangeWithScores(ValkeyKeys.partyRequests(party.get().id()), 0, -1))
                        .thenApply(ids -> ids.stream().filter(value -> value.getScore() >= since)
                                .map(value -> UUID.fromString(value.getValue())).toList()));
    }

    @Override
    public CompletableFuture<List<Party>> publicParties() {
        return Jdbc.future(valkey.zrange(ValkeyKeys.partyPublic(), 0, -1)).thenCompose(ids -> {
            List<CompletableFuture<Optional<Party>>> parties = ids.stream().map(this::party).toList();
            return CompletableFuture.allOf(parties.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> parties.stream().map(CompletableFuture::join).flatMap(Optional::stream)
                            .filter(Party::publicParty).toList());
        });
    }

    // ------------------------------------------------------------------ Einladen, Annehmen

    @Override
    public CompletableFuture<PartyResult> invite(UUID inviter, UUID target) {
        if (inviter.equals(target)) {
            return done(PartyOutcome.SELF);
        }
        CompletableFuture<Boolean> targetOnline = online.apply(target);
        CompletableFuture<Boolean> ignored = ignores.get().ignores(target, inviter);
        CompletableFuture<Boolean> friend = friends.get().areFriends(inviter, target).exceptionally(error -> false);
        CompletableFuture<PrivacySettings> privacy = settings.get().settings(target);
        CompletableFuture<Optional<Party>> current = partyOf(inviter);
        return CompletableFuture.allOf(targetOnline, ignored, friend, privacy, current).thenCompose(all -> {
            if (!targetOnline.join()) {
                return done(PartyOutcome.TARGET_OFFLINE);
            }
            Optional<Party> party = current.join();
            if (party.isPresent() && !party.get().isLeader(inviter)) {
                return done(PartyOutcome.NOT_LEADER);
            }
            if (party.isPresent() && party.get().isMember(target)) {
                return done(PartyOutcome.ALREADY_MEMBER);
            }
            if (ignored.join() || !privacy.join().acceptsPartyInviteFrom(friend.join())) {
                return done(PartyOutcome.INVITE_BLOCKED);
            }
            CompletableFuture<Optional<Party>> ensured = party.isPresent()
                    ? CompletableFuture.completedFuture(party) : create(inviter);
            return ensured.thenCompose(created -> created.isEmpty() ? done(PartyOutcome.IN_PARTY)
                    : openInvites(created.get().id()).thenCompose(open -> created.get().size() + open
                            >= created.get().maxSize()
                            ? CompletableFuture.completedFuture(new PartyResult(PartyOutcome.FULL, created.get()))
                            : storeInvite(created.get(), inviter, target)));
        });
    }

    /**
     * Offene, nicht abgelaufene Einladungen einer Party. Sie zählen beim Einladen mit: Leiter, Mitglieder und
     * Eingeladene zusammen höchstens {@code maxSize} (A6: Spieler-Leiter → 4. Person {@code party.err.full}).
     */
    private CompletableFuture<Long> openInvites(String partyId) {
        return Jdbc.future(valkey.zcount(invitesOf(partyId), Range.create((double) clock.millis(),
                Double.MAX_VALUE)));
    }

    private CompletableFuture<PartyResult> storeInvite(Party party, UUID inviter, UUID target) {
        String key = ValkeyKeys.partyInvites(target);
        Instant now = clock.instant();
        return Jdbc.future(valkey.hget(key, party.id())).thenCompose(existing -> {
            if (existing != null && !PartyInvite.fromHash(target, party.id(), existing).expired(now)) {
                return CompletableFuture.completedFuture(new PartyResult(PartyOutcome.ALREADY_INVITED, party));
            }
            PartyInvite invite = new PartyInvite(party.id(), inviter, target, now.plus(INVITE_TTL));
            return Jdbc.future(valkey.hset(key, party.id(), invite.hashValue()))
                    .thenCompose(ignored -> Jdbc.future(valkey.expire(key, INVITE_TTL.toSeconds())))
                    .thenCompose(ignored -> Jdbc.future(valkey.zadd(invitesOf(party.id()),
                            (double) invite.expiresAt().toEpochMilli(), target.toString())))
                    .thenCompose(ignored -> touch(party))
                    .thenApply(ignored -> {
                        events.publish(invite);
                        return new PartyResult(PartyOutcome.INVITED, party);
                    });
        });
    }

    /** Neue Party mit dem Spieler als Leiter (Größe aus seinem Rang). */
    private CompletableFuture<Optional<Party>> create(UUID leader) {
        String id = Ulid.next();
        Instant now = clock.instant();
        return ranks.get().rank(leader).thenCompose(rank -> Jdbc.future(valkey.set(ValkeyKeys.playerParty(leader), id,
                SetArgs.Builder.nx().ex(IDLE_TTL.toSeconds()))).thenCompose(ok -> {
                    if (!"OK".equals(ok)) {
                        return CompletableFuture.completedFuture(Optional.<Party>empty());
                    }
                    Map<String, String> hash = new LinkedHashMap<>();
                    hash.put("leader", leader.toString());
                    hash.put("created", Long.toString(now.toEpochMilli()));
                    hash.put("public", "0");
                    hash.put("publicFilter", PublicFilter.ALL.name());
                    hash.put("requireRequest", "0");
                    hash.put("maxSize", Integer.toString(rank.limits().partySize()));
                    return Jdbc.future(valkey.hset(ValkeyKeys.party(id), hash))
                            .thenCompose(ignored -> Jdbc.future(valkey.zadd(ValkeyKeys.partyMembers(id),
                                    (double) now.toEpochMilli(), leader.toString())))
                            .thenCompose(ignored -> party(id))
                            .thenCompose(party -> party.isEmpty() ? CompletableFuture.completedFuture(party)
                                    : touch(party.get()).thenApply(ignored -> party));
                }));
    }

    @Override
    public CompletableFuture<PartyResult> accept(UUID target, UUID inviter) {
        return invites(target).thenCompose(open -> {
            Optional<PartyInvite> invite = open.stream().filter(value -> value.inviter().equals(inviter)).findFirst();
            if (invite.isEmpty()) {
                return done(PartyOutcome.NO_INVITE);
            }
            return removeInvite(invite.get().partyId(), target).thenCompose(ignored -> party(invite.get().partyId()))
                    .thenCompose(party -> party.isEmpty() ? done(PartyOutcome.NO_INVITE)
                            : addMember(party.get(), target));
        });
    }

    private CompletableFuture<Void> removeInvite(String partyId, UUID target) {
        return Jdbc.future(valkey.hdel(ValkeyKeys.partyInvites(target), partyId))
                .thenCompose(ignored -> Jdbc.future(valkey.zrem(invitesOf(partyId), target.toString())))
                .thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<PartyResult> deny(UUID target, UUID inviter) {
        return invites(target).thenCompose(open -> {
            Optional<PartyInvite> invite = open.stream().filter(value -> value.inviter().equals(inviter)).findFirst();
            return invite.isEmpty() ? done(PartyOutcome.NO_INVITE)
                    : removeInvite(invite.get().partyId(), target).thenApply(ignored ->
                            PartyResult.of(PartyOutcome.DENIED));
        });
    }

    private CompletableFuture<PartyResult> addMember(Party party, UUID player) {
        return Jdbc.future(valkey.<Long>eval(ADD_MEMBER, ScriptOutputType.INTEGER,
                new String[] {ValkeyKeys.partyMembers(party.id()), ValkeyKeys.playerParty(player)}, player.toString(),
                Long.toString(clock.millis()), Integer.toString(party.maxSize()), party.id(),
                Long.toString(IDLE_TTL.toSeconds()))).thenCompose(added -> {
                    if (added == null || added < 0) {
                        return done(PartyOutcome.IN_PARTY);
                    }
                    if (added == 0) {
                        return CompletableFuture.completedFuture(new PartyResult(PartyOutcome.FULL, party));
                    }
                    return Jdbc.future(valkey.zrem(ValkeyKeys.partyRequests(party.id()), player.toString()))
                            .thenCompose(ignored -> changed(party.id(), PartyOutcome.JOINED));
                });
    }

    // ------------------------------------------------------------------ Verlassen, Leitung

    @Override
    public CompletableFuture<PartyResult> leave(UUID player) {
        return partyOf(player).thenCompose(party -> party.isEmpty() ? done(PartyOutcome.NOT_IN_PARTY)
                : removeMember(party.get(), player, PartyOutcome.LEFT));
    }

    @Override
    public CompletableFuture<PartyResult> kick(UUID leader, UUID target) {
        if (leader.equals(target)) {
            return done(PartyOutcome.SELF);
        }
        return partyOf(leader).thenCompose(party -> {
            if (party.isEmpty()) {
                return done(PartyOutcome.NOT_IN_PARTY);
            }
            if (!party.get().isLeader(leader)) {
                return done(PartyOutcome.NOT_LEADER);
            }
            if (!party.get().isMember(target)) {
                return done(PartyOutcome.NOT_MEMBER);
            }
            return removeMember(party.get(), target, PartyOutcome.KICKED);
        });
    }

    /** Mitglied entfernen; der Leiter übergibt an das älteste Mitglied, das letzte Mitglied löst auf. */
    private CompletableFuture<PartyResult> removeMember(Party party, UUID player, PartyOutcome outcome) {
        if (party.size() <= 1) {
            return disbandParty(party).thenApply(ignored -> new PartyResult(outcome, null));
        }
        CompletableFuture<?> removed = Jdbc.future(valkey.zrem(ValkeyKeys.partyMembers(party.id()), player.toString()))
                .thenCompose(ignored -> Jdbc.future(valkey.del(ValkeyKeys.playerParty(player),
                        ValkeyKeys.partyGrace(player))));
        if (party.isLeader(player)) {
            UUID successor = party.successor().orElseThrow();
            removed = removed.thenCompose(ignored -> setLeader(party.id(), successor));
        }
        return removed.thenCompose(ignored -> changed(party.id(), outcome));
    }

    private CompletableFuture<Void> setLeader(String partyId, UUID leader) {
        return ranks.get().rank(leader).thenCompose(rank -> {
            Map<String, String> hash = new LinkedHashMap<>();
            hash.put("leader", leader.toString());
            hash.put("maxSize", Integer.toString(rank.limits().partySize()));
            return Jdbc.future(valkey.hset(ValkeyKeys.party(partyId), hash)).thenApply(ignored -> null);
        });
    }

    @Override
    public CompletableFuture<PartyResult> promote(UUID leader, UUID target) {
        if (leader.equals(target)) {
            return done(PartyOutcome.SELF);
        }
        return partyOf(leader).thenCompose(party -> {
            if (party.isEmpty()) {
                return done(PartyOutcome.NOT_IN_PARTY);
            }
            if (!party.get().isLeader(leader)) {
                return done(PartyOutcome.NOT_LEADER);
            }
            if (!party.get().isMember(target)) {
                return done(PartyOutcome.NOT_MEMBER);
            }
            return setLeader(party.get().id(), target).thenCompose(ignored -> changed(party.get().id(),
                    PartyOutcome.PROMOTED));
        });
    }

    @Override
    public CompletableFuture<PartyResult> disband(UUID leader) {
        return partyOf(leader).thenCompose(party -> {
            if (party.isEmpty()) {
                return done(PartyOutcome.NOT_IN_PARTY);
            }
            if (!party.get().isLeader(leader)) {
                return done(PartyOutcome.NOT_LEADER);
            }
            return disbandParty(party.get()).thenApply(ignored -> new PartyResult(PartyOutcome.DISBANDED,
                    party.get()));
        });
    }

    private CompletableFuture<Void> disbandParty(Party party) {
        List<String> keys = new ArrayList<>(List.of(ValkeyKeys.party(party.id()), ValkeyKeys.partyMembers(party.id()),
                ValkeyKeys.partyRequests(party.id()), invitesOf(party.id())));
        party.memberIds().forEach(member -> {
            keys.add(ValkeyKeys.playerParty(member));
            keys.add(ValkeyKeys.partyGrace(member));
        });
        return Jdbc.future(valkey.zrange(invitesOf(party.id()), 0, -1))
                .thenCompose(targets -> {
                    List<CompletableFuture<Long>> cleanup = targets.stream().map(target -> Jdbc.future(valkey.hdel(
                            ValkeyKeys.partyInvites(UUID.fromString(target)), party.id()))).toList();
                    return CompletableFuture.allOf(cleanup.toArray(CompletableFuture[]::new));
                })
                .thenCompose(ignored -> Jdbc.future(valkey.del(keys.toArray(String[]::new))))
                .thenCompose(ignored -> Jdbc.future(valkey.zrem(ValkeyKeys.partyPublic(), party.id())))
                .thenApply(ignored -> {
                    events.publish(new PartyDisband(party.id(), party.memberIds()));
                    return null;
                });
    }

    // ------------------------------------------------------------------ Öffentlich, Anfragen

    private CompletableFuture<PartyResult> leaderChange(UUID leader, boolean needsPublicRank,
            Function<Party, CompletableFuture<?>> change) {
        return partyOf(leader).thenCompose(party -> {
            if (party.isEmpty()) {
                return done(PartyOutcome.NOT_IN_PARTY);
            }
            if (!party.get().isLeader(leader)) {
                return done(PartyOutcome.NOT_LEADER);
            }
            CompletableFuture<Boolean> allowed = needsPublicRank
                    ? ranks.get().rank(leader).thenApply(rank -> rank.flags().publicParty())
                    : CompletableFuture.completedFuture(true);
            return allowed.thenCompose(ok -> !ok ? done(PartyOutcome.RANK_REQUIRED)
                    : change.apply(party.get()).thenCompose(ignored -> changed(party.get().id(),
                            PartyOutcome.UPDATED)));
        });
    }

    @Override
    public CompletableFuture<PartyResult> setPublic(UUID leader, boolean publicParty) {
        return leaderChange(leader, true, party -> Jdbc.future(valkey.hset(ValkeyKeys.party(party.id()), "public",
                publicParty ? "1" : "0")).thenCompose(ignored -> publicParty
                        ? Jdbc.future(valkey.zadd(ValkeyKeys.partyPublic(), (double) party.created().toEpochMilli(),
                                party.id()))
                        : Jdbc.future(valkey.zrem(ValkeyKeys.partyPublic(), party.id()))));
    }

    @Override
    public CompletableFuture<PartyResult> setPublicFilter(UUID leader, PublicFilter filter) {
        return leaderChange(leader, true, party -> Jdbc.future(valkey.hset(ValkeyKeys.party(party.id()),
                "publicFilter", filter.name())));
    }

    @Override
    public CompletableFuture<PartyResult> setRequireRequest(UUID leader, boolean requireRequest) {
        return leaderChange(leader, false, party -> Jdbc.future(valkey.hset(ValkeyKeys.party(party.id()),
                "requireRequest", requireRequest ? "1" : "0")));
    }

    @Override
    public CompletableFuture<PartyResult> join(UUID player, UUID member) {
        return partyOf(member).thenCompose(party -> party.isEmpty() || !party.get().publicParty()
                ? done(PartyOutcome.NOT_PUBLIC) : joinPublic(player, party.get()));
    }

    private CompletableFuture<PartyResult> joinPublic(UUID player, Party party) {
        if (party.isMember(player)) {
            return done(PartyOutcome.ALREADY_MEMBER);
        }
        return partyOf(player).thenCombine(ranks.get().rank(player), (own, rank) -> new Joiner(own, rank))
                .thenCompose(joiner -> {
                    if (joiner.own().isPresent()) {
                        return done(PartyOutcome.IN_PARTY);
                    }
                    if (!party.publicFilter().admits(joiner.rank().weight())) {
                        return done(PartyOutcome.FILTERED);
                    }
                    if (party.isFull()) {
                        return CompletableFuture.completedFuture(new PartyResult(PartyOutcome.FULL, party));
                    }
                    if (party.requireRequest()) {
                        return Jdbc.future(valkey.zadd(ValkeyKeys.partyRequests(party.id()), (double) clock.millis(),
                                player.toString())).thenCompose(ignored -> touch(party))
                                .thenApply(ignored -> new PartyResult(PartyOutcome.REQUEST_SENT, party));
                    }
                    return addMember(party, player);
                });
    }

    /** Eigene Party und Rang des Beitretenden. */
    private record Joiner(Optional<Party> own, RankInfo rank) {
    }

    @Override
    public CompletableFuture<PartyResult> joinRandom(UUID player) {
        return ranks.get().rank(player).thenCompose(rank -> publicParties().thenCompose(parties -> {
            Optional<Party> match = parties.stream().filter(party -> !party.isFull() && !party.requireRequest()
                    && party.publicFilter().admits(rank.weight()) && !party.isMember(player)).findFirst();
            return match.isEmpty() ? done(PartyOutcome.NO_PUBLIC_PARTY) : joinPublic(player, match.get());
        }));
    }

    @Override
    public CompletableFuture<PartyResult> acceptRequest(UUID leader, UUID requester) {
        return partyOf(leader).thenCompose(party -> {
            if (party.isEmpty()) {
                return done(PartyOutcome.NOT_IN_PARTY);
            }
            if (!party.get().isLeader(leader)) {
                return done(PartyOutcome.NOT_LEADER);
            }
            return Jdbc.future(valkey.zscore(ValkeyKeys.partyRequests(party.get().id()), requester.toString()))
                    .thenCompose(score -> score == null || score < clock.millis() - REQUEST_TTL.toMillis()
                            ? done(PartyOutcome.NO_REQUEST) : addMember(party.get(), requester));
        });
    }

    // ------------------------------------------------------------------ Karenz und Aufräumen

    @Override
    public CompletableFuture<Void> markOffline(UUID player) {
        return Jdbc.future(valkey.get(ValkeyKeys.playerParty(player))).thenCompose(id -> id == null
                ? CompletableFuture.completedFuture(null)
                : Jdbc.future(valkey.set(ValkeyKeys.partyGrace(player), id,
                        SetArgs.Builder.ex(OFFLINE_GRACE.toSeconds()))).thenApply(ignored -> null));
    }

    @Override
    public CompletableFuture<Optional<Party>> rejoin(UUID player) {
        return Jdbc.future(valkey.get(ValkeyKeys.partyGrace(player))).thenCompose(id -> id == null
                ? CompletableFuture.completedFuture(Optional.<Party>empty())
                : Jdbc.future(valkey.del(ValkeyKeys.partyGrace(player))).thenCompose(ignored -> party(id))
                        .thenApply(party -> party.filter(value -> value.isMember(player))));
    }

    @Override
    public CompletableFuture<PartyResult> expireOffline(UUID player) {
        return Jdbc.future(valkey.del(ValkeyKeys.partyGrace(player))).thenCompose(ignored -> leave(player));
    }

    @Override
    public CompletableFuture<Boolean> disbandIfAbandoned(String partyId) {
        long now = clock.millis();
        return party(partyId).thenCompose(party -> {
            if (party.isEmpty() || party.get().size() > 1) {
                return CompletableFuture.completedFuture(false);
            }
            return Jdbc.future(valkey.zremrangebyscore(invitesOf(partyId), Range.create(0.0, (double) now)))
                    .thenCompose(ignored -> Jdbc.future(valkey.zcard(invitesOf(partyId))))
                    .thenCompose(open -> open > 0 ? CompletableFuture.completedFuture(false)
                            : disbandParty(party.get()).thenApply(ignored -> true));
        });
    }

    // ------------------------------------------------------------------ Hilfen

    /** Neuer Stand nach einer Änderung: TTL erneuern und {@code PARTY_UPDATE} senden. */
    private CompletableFuture<PartyResult> changed(String partyId, PartyOutcome outcome) {
        return party(partyId).thenCompose(party -> {
            if (party.isEmpty()) {
                return CompletableFuture.completedFuture(new PartyResult(outcome, null));
            }
            return touch(party.get()).thenApply(ignored -> {
                events.publish(new PartyUpdate(partyId, party.get().leader(), party.get().memberIds(),
                        party.get().publicParty()));
                return new PartyResult(outcome, party.get());
            });
        });
    }

    /** Alle Schlüssel der Party leben 30 min ab der letzten Aktivität. */
    private CompletableFuture<Void> touch(Party party) {
        long ttl = IDLE_TTL.toSeconds();
        List<CompletableFuture<Boolean>> renewals = new ArrayList<>();
        for (String key : List.of(ValkeyKeys.party(party.id()), ValkeyKeys.partyMembers(party.id()),
                ValkeyKeys.partyRequests(party.id()), invitesOf(party.id()))) {
            renewals.add(Jdbc.future(valkey.expire(key, ttl)));
        }
        party.memberIds().forEach(member -> renewals.add(Jdbc.future(valkey.expire(ValkeyKeys.playerParty(member),
                ttl))));
        return CompletableFuture.allOf(renewals.toArray(CompletableFuture[]::new));
    }
}
