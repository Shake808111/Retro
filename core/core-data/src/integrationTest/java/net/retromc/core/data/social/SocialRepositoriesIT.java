package net.retromc.core.data.social;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.retromc.core.api.event.PrivateMessage;
import net.retromc.core.api.event.ProfileChanged;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.social.FriendPair;
import net.retromc.core.api.social.IgnoreOutcome;
import net.retromc.core.api.social.MessageResult;
import net.retromc.core.api.social.Nick;
import net.retromc.core.api.social.PlayerLogin;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.PrivateMessageService;
import net.retromc.core.api.social.ReceivedMessage;
import net.retromc.core.api.social.Setting;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.core.data.testing.TestTransactions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Ignorieren, Einstellungen, Spielerdaten, private Nachrichten und Nicks gegen PostgreSQL und Valkey
 * (Spec 01 §3.2, §6.7, §6.11, §6.13; Abnahme A11, A12, A23).
 */
@Tag("integration")
class SocialRepositoriesIT {

    private static DataFixture data;
    private static IgnoreRepository ignores;
    private static PrivacyRepository privacy;
    private static PlayerRepository players;
    private static FriendRepository friends;
    private static final Set<UUID> ONLINE = ConcurrentHashMap.newKeySet();

    @BeforeAll
    static void start() {
        data = new DataFixture();
        ignores = new IgnoreRepository(data.async, data.valkey, () -> data.ranks);
        privacy = new PrivacyRepository(data.async, data.valkey, data.events);
        players = new PlayerRepository(data.async, data.valkey, () -> data.ranks, data.events);
        friends = new FriendRepository(data.async, () -> new ValkeyPresenceService(data.valkey, data.events),
                () -> data.ranks, () -> ignores, () -> privacy, data.clock);
    }

    @AfterAll
    static void stop() {
        data.close();
    }

    private static ValkeyPrivateMessages messages(Duration replyWindow) {
        return new ValkeyPrivateMessages(data.valkey, () -> friends, () -> privacy, () -> ignores, () -> data.ranks,
                player -> CompletableFuture.completedFuture(ONLINE.contains(player)), data.events, data.clock,
                replyWindow);
    }

    private static void befriend(UUID a, UUID b) {
        FriendPair pair = FriendPair.of(a, b);
        TestTransactions.committed(data.database.dataSource(), connection -> Jdbc.update(connection,
                "INSERT INTO social.friendship (a, b) VALUES (?, ?)", pair.a(), pair.b()));
    }

    @Test
    void ignorierenMitGrenzenUndCache() {
        UUID owner = data.player("Owner1");
        UUID target = data.player("Target1");
        UUID staff = data.player("Staff1");
        data.ranks.set(staff, Rank.MOD);
        assertEquals(IgnoreOutcome.SELF, await(ignores.add(owner, owner)));
        assertEquals(IgnoreOutcome.STAFF, await(ignores.add(owner, staff)));
        assertFalse(await(ignores.ignores(owner, target)));
        assertEquals(IgnoreOutcome.ADDED, await(ignores.add(owner, target)));
        assertEquals(IgnoreOutcome.ALREADY, await(ignores.add(owner, target)));
        assertTrue(await(ignores.ignores(owner, target)));
        assertEquals("Target1", await(ignores.list(owner)).getFirst().name());
        assertTrue(await(ignores.remove(owner, target)));
        assertFalse(await(ignores.remove(owner, target)));
        assertFalse(await(ignores.ignores(owner, target)));

        for (int i = 0; i < 100; i++) {
            UUID other = UUID.randomUUID();
            TestTransactions.committed(data.database.dataSource(), connection -> Jdbc.update(connection,
                    "INSERT INTO social.ignore (owner, target) VALUES (?, ?)", owner, other));
        }
        assertEquals(IgnoreOutcome.LIMIT, await(ignores.add(owner, target)));
    }

    @Test
    void einstellungenStandardAenderungUndEreignis() {
        UUID player = data.player("Settings2");
        assertEquals(PrivacySettings.DEFAULTS, await(privacy.settings(player)));
        PrivacySettings changed = await(privacy.cycle(player, Setting.MSG_FROM));
        assertEquals(0, changed.msgFrom());
        assertEquals(0, await(privacy.settings(player)).msgFrom());
        await(privacy.set(player, Setting.NICK_ON_JOIN, 1));
        assertTrue(await(privacy.settings(player)).nickOnJoin());
        assertTrue(data.events.of(ProfileInvalidate.class).stream().anyMatch(event -> event.uuid().equals(player)));
    }

    @Test
    void spielerdatenLoginNamenswechselUndLetzterLogout() {
        UUID uuid = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-02T09:00:00Z");
        await(players.recordLogin(new PlayerLogin(uuid, "Wanderer", null, "de_at", "proxy-1", at)));
        assertEquals(Optional.of(uuid), await(players.uuidOf("wanderer")));
        assertEquals("de_DE", await(players.profile(uuid)).orElseThrow().locale());
        // Ein neuer Spieler übernimmt den Namen: der alte Eintrag gibt name_lower frei.
        UUID other = UUID.randomUUID();
        await(data.valkey.del(ValkeyKeys.playerName("wanderer")).toCompletableFuture());
        await(players.recordLogin(new PlayerLogin(other, "Wanderer", null, "en_us", "proxy-1", at)));
        assertEquals(Optional.of(other), await(players.uuidOf("Wanderer")));
        assertEquals("en_US", await(players.profile(other)).orElseThrow().locale());
        assertTrue(await(players.lastQuit(uuid)).isEmpty());
        await(players.recordQuit(uuid, at.plusSeconds(60), 60));
        assertEquals(Optional.of(at.plusSeconds(60)), await(players.lastQuit(uuid)));
        assertEquals(60, await(players.profile(uuid)).orElseThrow().playtimeS());
    }

    @Test
    void ersteSpracheKommtMitDenClientEinstellungen() {
        // Server-Test: Client-Sprache beim Login oft noch unbekannt → erst Basis, dann aus den Client-Einstellungen.
        UUID uuid = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-03T18:39:00Z");
        await(players.recordLogin(new PlayerLogin(uuid, "SpaetSprache", null, null, "proxy-1", at)));
        assertEquals("de_DE", await(players.profile(uuid)).orElseThrow().locale());
        assertTrue(await(privacy.initialLocale(uuid, "en_us")));
        assertEquals("en_US", await(players.profile(uuid)).orElseThrow().locale());
        assertTrue(data.events.of(ProfileChanged.class).stream().anyMatch(event -> event.uuid().equals(uuid)
                && event.fields().contains(ProfileChanged.LOCALE)));
        // Gleiche Sprache: nichts zu tun.
        assertFalse(await(privacy.initialLocale(uuid, "en_GB")));
        // Ab der zweiten Sitzung zählt nur noch die gewählte Sprache (/language), nicht der Client.
        await(players.recordLogin(new PlayerLogin(uuid, "SpaetSprache", null, null, "proxy-1", at.plusSeconds(600))));
        assertFalse(await(privacy.initialLocale(uuid, "de_de")));
        assertEquals("en_US", await(players.profile(uuid)).orElseThrow().locale());
    }

    @Test
    void nachrichtenErlaubnisIgnorierenUndAntwort() {
        UUID alice = data.player("Alice3");
        UUID bob = data.player("Bob3");
        UUID carl = data.player("Carl3");
        ONLINE.addAll(List.of(alice, bob, carl));
        ValkeyPrivateMessages pm = messages(Duration.ofMinutes(30));

        assertEquals(MessageResult.Outcome.SELF, await(pm.send(alice, alice, "x")).outcome());
        // Standard msg_from = nur Freunde
        assertEquals(MessageResult.Outcome.DENIED, await(pm.send(alice, bob, "Hallo")).outcome());
        befriend(alice, bob);
        assertEquals(MessageResult.Outcome.DELIVERED, await(pm.send(alice, bob, "Hallo")).outcome());
        assertTrue(data.events.of(PrivateMessage.class).stream()
                .anyMatch(event -> event.from().equals(alice) && event.to().equals(List.of(bob))));
        // Team darf immer
        data.ranks.set(carl, Rank.SUP);
        assertEquals(MessageResult.Outcome.DELIVERED, await(pm.send(carl, bob, "Team")).outcome());
        // A11: ignorierter Absender bekommt nur DENIED, auch als Freund
        await(ignores.add(bob, alice));
        assertEquals(MessageResult.Outcome.DENIED, await(pm.send(alice, bob, "Hallo?")).outcome());
        await(ignores.remove(bob, alice));

        // A12: /r an den letzten Partner (bob hat zuletzt von carl erhalten)
        MessageResult reply = await(pm.reply(bob, "Antwort"));
        assertEquals(MessageResult.Outcome.DENIED, reply.outcome()); // carl: msg_from Freunde, bob kein Freund/Team
        await(privacy.set(carl, Setting.MSG_FROM, 2));
        assertEquals(carl, await(pm.reply(bob, "Antwort")).recipient());
        List<ReceivedMessage> recent = await(pm.recent(bob));
        assertEquals(List.of(carl, alice), recent.stream().map(ReceivedMessage::from).toList());

        ONLINE.remove(carl);
        assertEquals(MessageResult.Outcome.OFFLINE, await(pm.reply(bob, "weg?")).outcome());
    }

    @Test
    void antwortZielLaeuftAb() throws InterruptedException {
        UUID a = data.player("Expire4a");
        UUID b = data.player("Expire4b");
        ONLINE.addAll(List.of(a, b));
        befriend(a, b);
        ValkeyPrivateMessages pm = messages(Duration.ofMillis(400));
        assertEquals(MessageResult.Outcome.DELIVERED, await(pm.send(a, b, "kurz")).outcome());
        assertEquals(MessageResult.Outcome.DELIVERED, await(pm.reply(b, "noch da")).outcome());
        Thread.sleep(700);
        assertEquals(MessageResult.Outcome.NO_REPLY_TARGET, await(pm.reply(b, "zu spät")).outcome());
        assertEquals(MessageResult.Outcome.NO_REPLY_TARGET, await(pm.reply(UUID.randomUUID(), "nie")).outcome());
        long ttl = await(data.valkey.pttl(ValkeyKeys.msgLast(a)).toCompletableFuture());
        assertTrue(ttl < 0, "msg:last ist abgelaufen");
        assertEquals(30, PrivateMessageService.REPLY_WINDOW.toMinutes());
    }

    @Test
    void nickNieDoppeltUndNurAufSpielservern() {
        Set<String> online = ConcurrentHashMap.newKeySet();
        NickRepository nicks = new NickRepository(data.async, data.valkey, online::contains, data.clock);
        for (int i = 0; i < 60; i++) {
            await(nicks.addToPool("Nick" + i, "tex" + i, "sig" + i));
        }
        online.add("Nick0");
        UUID lobbyPlayer = UUID.randomUUID();
        assertTrue(await(nicks.assign(lobbyPlayer, ServerCategory.LOBBY)).isEmpty());
        assertTrue(await(nicks.assign(lobbyPlayer, ServerCategory.PERSISTENT)).isEmpty());

        List<UUID> nicked = new ArrayList<>();
        List<CompletableFuture<Optional<Nick>>> parallel = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID player = UUID.randomUUID();
            nicked.add(player);
            parallel.add(nicks.assign(player, ServerCategory.GAME));
        }
        Set<String> names = new HashSet<>();
        for (CompletableFuture<Optional<Nick>> future : parallel) {
            Nick nick = await(future).orElseThrow();
            assertTrue(names.add(nick.name()), "doppelter Nick " + nick.name());
            assertNotEquals("Nick0", nick.name());
        }
        assertEquals(50, names.size());
        assertEquals(9, await(nicks.freeCount()));
        Nick first = await(nicks.nickOf(nicked.getFirst())).orElseThrow();
        assertEquals(first, await(nicks.assign(nicked.getFirst(), ServerCategory.GAME)).orElseThrow());
        assertEquals(Optional.of(nicked.getFirst()), await(nicks.resolve(first.name().toUpperCase())));
        await(nicks.release(nicked.getFirst()));
        assertTrue(await(nicks.nickOf(nicked.getFirst())).isEmpty());
        assertTrue(await(nicks.resolve(first.name())).isEmpty());
        assertEquals(10, await(nicks.freeCount()));
        assertEquals(49, await(nicks.nicks(nicked)).size());
        nicked.forEach(player -> await(nicks.release(player)));
    }
}
