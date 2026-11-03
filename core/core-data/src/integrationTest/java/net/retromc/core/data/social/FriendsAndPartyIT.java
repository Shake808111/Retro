package net.retromc.core.data.social;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.event.PartyDisband;
import net.retromc.core.api.event.PartyUpdate;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.social.FriendEntry;
import net.retromc.core.api.social.FriendOutcome;
import net.retromc.core.api.social.FriendPair;
import net.retromc.core.api.social.FriendResult;
import net.retromc.core.api.social.JoinMeResult;
import net.retromc.core.api.social.JumpResult;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.api.social.Party;
import net.retromc.core.api.social.PartyInvite;
import net.retromc.core.api.social.PartyOutcome;
import net.retromc.core.api.social.Presence;
import net.retromc.core.api.social.PublicFilter;
import net.retromc.core.api.social.Setting;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.testing.TestTransactions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Freunde, Präsenz, Party und JoinMe gegen PostgreSQL und Valkey (Spec 01 §3.1, §3.3, §6.4, §6.5; A6, A7, A15). */
@Tag("integration")
class FriendsAndPartyIT {

    private static DataFixture data;
    private static ValkeyPresenceService presences;
    private static PrivacyRepository privacy;
    private static IgnoreRepository ignores;
    private static FriendRepository friends;
    private static ValkeyPartyService parties;
    private static final Set<UUID> ONLINE = ConcurrentHashMap.newKeySet();

    @BeforeAll
    static void start() {
        data = new DataFixture();
        presences = new ValkeyPresenceService(data.valkey, data.events);
        privacy = new PrivacyRepository(data.async, data.valkey, data.events);
        ignores = new IgnoreRepository(data.async, data.valkey, () -> data.ranks);
        friends = new FriendRepository(data.async, () -> presences, () -> data.ranks, () -> ignores, () -> privacy,
                data.clock);
        parties = new ValkeyPartyService(data.valkey, () -> data.ranks, () -> privacy, () -> friends, () -> ignores,
                player -> CompletableFuture.completedFuture(ONLINE.contains(player)), data.events, data.clock);
    }

    @AfterAll
    static void stop() {
        data.close();
    }

    private static UUID online(String name) {
        UUID uuid = data.player(name);
        ONLINE.add(uuid);
        return uuid;
    }

    private static void present(UUID player, String server, boolean hidden) {
        await(presences.update(new Presence(player, "proxy-1", server, data.clock.instant(), false, null, hidden)));
    }

    // ------------------------------------------------------------------ Freunde

    @Test
    void anfrageAnnehmenGegenseitigUndPruefungen() {
        UUID a = data.player("FrA1");
        UUID b = data.player("FrB1");
        UUID c = data.player("FrC1");
        assertEquals(FriendOutcome.SELF, await(friends.request(a, a)).outcome());
        assertEquals(FriendOutcome.UNKNOWN_PLAYER, await(friends.request(a, UUID.randomUUID())).outcome());
        assertEquals(FriendOutcome.SENT, await(friends.request(a, b)).outcome());
        assertEquals(FriendOutcome.ALREADY_PENDING, await(friends.request(a, b)).outcome());
        // A7: gegenseitige Anfrage → sofort befreundet
        assertEquals(FriendOutcome.ADDED, await(friends.request(b, a)).outcome());
        assertTrue(await(friends.areFriends(a, b)));
        assertTrue(await(friends.requests(a)).isEmpty());
        assertEquals(FriendOutcome.ALREADY_FRIENDS, await(friends.request(a, b)).outcome());

        await(ignores.add(c, a));
        assertEquals(FriendOutcome.BLOCKED, await(friends.request(a, c)).outcome());
        await(ignores.remove(c, a));
        await(privacy.set(c, Setting.FRIEND_REQUESTS, 1));
        assertEquals(FriendOutcome.BLOCKED, await(friends.request(a, c)).outcome());
        data.ranks.set(a, Rank.PREMIUM);
        assertEquals(FriendOutcome.SENT, await(friends.request(a, c)).outcome());
        assertEquals(1, await(friends.requests(c)).size());
        assertEquals(FriendOutcome.DENIED, await(friends.deny(c, a)).outcome());
        assertEquals(FriendOutcome.NO_REQUEST, await(friends.accept(c, a)).outcome());
        await(friends.request(a, c));
        assertEquals(FriendOutcome.ADDED, await(friends.accept(c, a)).outcome());
        assertTrue(await(friends.favorite(a, c)));
        assertFalse(await(friends.favorite(a, c)));
        assertEquals(FriendOutcome.REMOVED, await(friends.remove(c, a)).outcome());
        assertEquals(FriendOutcome.NOT_FRIENDS, await(friends.remove(c, a)).outcome());
        assertEquals(1, await(friends.clear(a)));
    }

    @Test
    void limitsJeRang() {
        UUID player = data.player("Limit2");
        UUID premium = data.player("Limit2P");
        data.ranks.set(premium, Rank.PREMIUM);
        UUID supremium = data.player("Limit2S");
        data.ranks.set(supremium, Rank.SUPREMIUM);
        fill(player, 250);
        fill(premium, 500);
        fill(supremium, 999);
        UUID newcomer = data.player("New2");
        FriendResult self = await(friends.request(player, newcomer));
        assertEquals(FriendOutcome.LIMIT_SELF, self.outcome());
        assertEquals(250, self.limit());
        FriendResult other = await(friends.request(newcomer, premium));
        assertEquals(FriendOutcome.LIMIT_OTHER, other.outcome());
        assertEquals(500, other.limit());
        assertEquals(FriendOutcome.SENT, await(friends.request(newcomer, supremium)).outcome());
        assertEquals(FriendOutcome.ADDED, await(friends.accept(supremium, newcomer)).outcome());
        assertEquals(FriendOutcome.LIMIT_SELF, await(friends.request(supremium, data.player("New2b"))).outcome());
        assertEquals(1000, await(friends.counts(supremium)).total());
    }

    private static void fill(UUID player, int count) {
        TestTransactions.committed(data.database.dataSource(), connection -> {
            for (int i = 0; i < count; i++) {
                UUID other = UUID.randomUUID();
                FriendPair pair = FriendPair.of(player, other);
                Jdbc.update(connection, "INSERT INTO social.friendship (a, b) VALUES (?, ?)", pair.a(), pair.b());
            }
            return null;
        });
    }

    @Test
    void listeZaehlerNachspringenUndStatusEmpfaenger() {
        UUID a = data.player("ListA3");
        UUID b = data.player("ListB3");
        UUID c = data.player("ListC3");
        await(friends.request(a, b));
        await(friends.accept(b, a));
        await(friends.request(a, c));
        await(friends.accept(c, a));
        present(a, "lobby-1", false);
        present(b, "bedwars-2", false);
        present(c, "lobby-2", true);
        // A18/A7: versteckte Freunde zählen nicht als online
        assertEquals(new OnlineCount(1, 2), await(friends.counts(a)));
        List<FriendEntry> list = await(friends.list(a));
        assertEquals(List.of("ListB3", "ListC3"), list.stream().map(FriendEntry::name).toList());
        assertEquals("bedwars-2", list.getFirst().server());
        assertEquals(JumpResult.Outcome.OK, await(friends.jump(a, b)).outcome());
        assertEquals(JumpResult.Outcome.OFFLINE, await(friends.jump(a, c)).outcome());
        await(privacy.set(b, Setting.JUMP_FROM, 0));
        assertEquals(JumpResult.Outcome.DENIED, await(friends.jump(a, b)).outcome());
        assertEquals(JumpResult.Outcome.NOT_FRIENDS, await(friends.jump(b, data.player("Stranger3"))).outcome());
        await(privacy.set(c, Setting.NOTIFY_FRIENDS, 0));
        assertEquals(List.of(b), await(friends.statusRecipients(a)));
        await(privacy.set(a, Setting.SHOW_ONLINE, 0));
        assertTrue(await(friends.statusRecipients(a)).isEmpty());
        await(presences.setAfk(a, true));
        assertTrue(await(presences.presence(a)).orElseThrow().afk());
        assertTrue(data.events.of(AfkChanged.class).stream().anyMatch(event -> event.uuid().equals(a)));
        await(presences.remove(a));
        assertTrue(await(presences.serverOf(a)).isEmpty());
    }

    // ------------------------------------------------------------------ Party

    @Test
    void groesseJeLeiterRangUndEinladungen() {
        UUID leader = online("PtL4");
        UUID one = online("PtA4");
        UUID two = online("PtB4");
        UUID three = online("PtC4");
        // A6: Spieler → 3 inkl. Leiter, die 4. Person ist zu viel
        assertEquals(PartyOutcome.INVITED, await(parties.invite(leader, one)).outcome());
        assertEquals(PartyOutcome.ALREADY_INVITED, await(parties.invite(leader, one)).outcome());
        assertEquals(PartyOutcome.INVITED, await(parties.invite(leader, two)).outcome());
        assertEquals(PartyOutcome.FULL, await(parties.invite(leader, three)).outcome());
        Party party = await(parties.partyOf(leader)).orElseThrow();
        assertEquals(3, party.maxSize());
        assertEquals(1, await(parties.invites(one)).size());
        assertEquals(PartyOutcome.JOINED, await(parties.accept(one, leader)).outcome());
        assertEquals(PartyOutcome.NO_INVITE, await(parties.accept(three, leader)).outcome());
        assertEquals(PartyOutcome.DENIED, await(parties.deny(two, leader)).outcome());
        assertEquals(PartyOutcome.NOT_LEADER, await(parties.invite(one, three)).outcome());
        assertEquals(PartyOutcome.TARGET_OFFLINE, await(parties.invite(leader, UUID.randomUUID())).outcome());
        assertTrue(data.events.of(PartyInvite.class).stream().anyMatch(invite -> invite.target().equals(one)));
        assertTrue(data.events.of(PartyUpdate.class).stream().anyMatch(update -> update.members().contains(one)));

        UUID premium = online("PtP4");
        data.ranks.set(premium, Rank.PREMIUM);
        UUID supremium = online("PtS4");
        data.ranks.set(supremium, Rank.SUPREMIUM);
        await(parties.invite(premium, online("PtX4")));
        await(parties.invite(supremium, online("PtY4")));
        assertEquals(10, await(parties.partyOf(premium)).orElseThrow().maxSize());
        assertEquals(64, await(parties.partyOf(supremium)).orElseThrow().maxSize());

        await(privacy.set(three, Setting.PARTY_FROM, 0));
        assertEquals(PartyOutcome.INVITE_BLOCKED, await(parties.invite(supremium, three)).outcome());
    }

    @Test
    void leiterwechselKarenzUndAufloesen() {
        UUID leader = online("PtL5");
        UUID oldest = online("PtA5");
        UUID younger = online("PtB5");
        await(parties.invite(leader, oldest));
        await(parties.accept(oldest, leader));
        data.clock.advance(Duration.ofSeconds(1));
        await(parties.invite(leader, younger));
        await(parties.accept(younger, leader));
        // A15: Leiter offline → nach Karenz neuer Leiter = ältestes Mitglied
        await(parties.markOffline(leader));
        Party back = await(parties.rejoin(leader)).orElseThrow();
        assertEquals(leader, back.leader());
        await(parties.markOffline(leader));
        assertEquals(PartyOutcome.LEFT, await(parties.expireOffline(leader)).outcome());
        Party after = await(parties.partyOf(oldest)).orElseThrow();
        assertEquals(oldest, after.leader());
        assertTrue(await(parties.partyOf(leader)).isEmpty());
        assertTrue(await(parties.rejoin(leader)).isEmpty());

        assertEquals(PartyOutcome.PROMOTED, await(parties.promote(oldest, younger)).outcome());
        assertEquals(PartyOutcome.KICKED, await(parties.kick(younger, oldest)).outcome());
        Party alone = await(parties.partyOf(younger)).orElseThrow();
        assertTrue(await(parties.disbandIfAbandoned(alone.id())));
        assertTrue(await(parties.partyOf(younger)).isEmpty());
        assertTrue(data.events.of(PartyDisband.class).stream().anyMatch(event -> event.partyId().equals(alone.id())));
        data.clock.set(Instant.parse("2026-11-02T10:00:00Z"));
    }

    @Test
    void oeffentlicheParty() {
        UUID leader = online("PtL6");
        UUID joiner = online("PtJ6");
        UUID asker = online("PtQ6");
        await(parties.invite(leader, online("PtI6")));
        assertEquals(PartyOutcome.RANK_REQUIRED, await(parties.setPublic(leader, true)).outcome());
        data.ranks.set(leader, Rank.PREMIUM);
        assertEquals(PartyOutcome.UPDATED, await(parties.setPublic(leader, true)).outcome());
        assertEquals(PartyOutcome.UPDATED, await(parties.setPublicFilter(leader, PublicFilter.PREMIUM)).outcome());
        assertEquals(PartyOutcome.FILTERED, await(parties.join(joiner, leader)).outcome());
        await(parties.setPublicFilter(leader, PublicFilter.ALL));
        assertEquals(PartyOutcome.JOINED, await(parties.joinRandom(joiner)).outcome());
        await(parties.setRequireRequest(leader, true));
        assertEquals(PartyOutcome.REQUEST_SENT, await(parties.join(asker, leader)).outcome());
        assertEquals(List.of(asker), await(parties.joinRequests(leader)));
        assertEquals(PartyOutcome.JOINED, await(parties.acceptRequest(leader, asker)).outcome());
        assertEquals(PartyOutcome.NOT_PUBLIC, await(parties.join(online("PtZ6"), online("PtN6"))).outcome());
        assertEquals(1, await(parties.publicParties()).stream().filter(party -> party.isLeader(leader)).count());
        assertEquals(PartyOutcome.DISBANDED, await(parties.disband(leader)).outcome());
        assertTrue(await(parties.publicParties()).stream().noneMatch(party -> party.isLeader(leader)));
    }

    @Test
    void joinMeTageslimit() {
        ValkeyJoinMe joinMe = new ValkeyJoinMe(data.valkey, data.clock);
        UUID player = UUID.randomUUID();
        for (int i = 1; i <= 3; i++) {
            JoinMeResult result = await(joinMe.use(player, 3));
            assertTrue(result.allowed());
            assertEquals(i, result.used());
        }
        assertFalse(await(joinMe.use(player, 3)).allowed());
        assertEquals(3, await(joinMe.usedToday(player)));
        data.clock.advance(Duration.ofDays(1));
        assertTrue(await(joinMe.use(player, 3)).allowed());
        assertTrue(await(joinMe.use(player, -1)).allowed());
        data.clock.set(Instant.parse("2026-11-02T10:00:00Z"));
    }
}
