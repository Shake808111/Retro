package net.retromc.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.util.IpHmac;
import org.junit.jupiter.api.Test;

class ValkeyKeysTest {

    private static final UUID PLAYER = UUID.fromString("0f2b6c1e-1111-4222-8333-944455556666");
    private static final UUID TARGET = UUID.fromString("ffffffff-0000-4000-8000-000000000001");
    private static final String PARTY = "01JB7Z3Y8Q4M5N6P7R8S9T0V1W";
    private static final LocalDate DAY = LocalDate.of(2026, 10, 27);

    @Test
    void schluesselSpec01Abschnitt23() {
        assertEquals("presence:" + PLAYER, ValkeyKeys.presence(PLAYER));
        assertEquals("profile:" + PLAYER, ValkeyKeys.profile(PLAYER));
        assertEquals("name:spieler_1", ValkeyKeys.playerName("Spieler_1"));
        assertEquals("party:" + PARTY, ValkeyKeys.party(PARTY));
        assertEquals("party:" + PARTY + ":members", ValkeyKeys.partyMembers(PARTY));
        assertEquals("party:" + PARTY + ":requests", ValkeyKeys.partyRequests(PARTY));
        assertEquals("player:" + PLAYER + ":party", ValkeyKeys.playerParty(PLAYER));
        assertEquals("party:invite:" + TARGET, ValkeyKeys.partyInvites(TARGET));
        assertEquals("party:public", ValkeyKeys.partyPublic());
        assertEquals("party:grace:" + PLAYER, ValkeyKeys.partyGrace(PLAYER));
        assertEquals("msg:last:" + PLAYER, ValkeyKeys.msgLast(PLAYER));
        assertEquals("msg:recent:" + PLAYER, ValkeyKeys.msgRecent(PLAYER));
        assertEquals("ignore:" + PLAYER, ValkeyKeys.ignore(PLAYER));
        assertEquals("mute:" + PLAYER, ValkeyKeys.mute(PLAYER));
        assertEquals("ban:none:" + PLAYER, ValkeyKeys.banNone(PLAYER));
        assertEquals("nick:" + PLAYER, ValkeyKeys.nick(PLAYER));
        assertEquals("nick:name:tarnname", ValkeyKeys.nickName("TarnName"));
        assertEquals("spam:" + PLAYER, ValkeyKeys.spam(PLAYER));
        assertEquals("cd:report:" + PLAYER + ":" + TARGET, ValkeyKeys.reportCooldown(PLAYER, TARGET));
        assertEquals("cd:report:" + PLAYER, ValkeyKeys.reportHourly(PLAYER));
        assertEquals("cnt:joinme:" + PLAYER + ":2026-10-27", ValkeyKeys.joinmeCount(PLAYER, DAY));
        assertEquals("cnt:clanrename:42:2026-10-27", ValkeyKeys.clanRenameCount(42, DAY));
        assertEquals("chatbuf:lobby-1", ValkeyKeys.chatBuffer("lobby-1"));
        assertEquals("maintenance", ValkeyKeys.maintenance());
    }

    @Test
    void schluesselSpec02Abschnitt22() {
        // ersetzt leaderboardAllTime/leaderboardMonthly (lb:{mode}:{key}:alltime|YYYY-MM)
        assertEquals("lb:bedwars:30d", ValkeyKeys.leaderboard(ModeKey.BEDWARS, StatPeriod.DAYS_30));
        assertEquals("lb:bedwars:all", ValkeyKeys.leaderboard(ModeKey.BEDWARS, StatPeriod.ALL));
        assertEquals("lb:skywars:30d:tmp", ValkeyKeys.leaderboardTmp(ModeKey.SKYWARS, StatPeriod.DAYS_30));
        assertEquals("st:bedwars:" + PLAYER + ":all", ValkeyKeys.statsCache(ModeKey.BEDWARS, PLAYER, StatPeriod.ALL));
        assertEquals("top:ttt:30d", ValkeyKeys.topCache(ModeKey.TTT, StatPeriod.DAYS_30));
        assertEquals("rl:stats:" + PLAYER, ValkeyKeys.statsRateLimit(PLAYER));
        assertEquals("lock:stats:rollover:2026-10-27", ValkeyKeys.statsRolloverLock(DAY));
        assertEquals("lock:stats:reconcile:2026-10-27", ValkeyKeys.statsReconcileLock(DAY));
        assertEquals("reset:confirm:" + PLAYER, ValkeyKeys.resetConfirm(PLAYER));
        assertEquals("ui:news", ValkeyKeys.uiNews());
    }

    @Test
    void schluesselSpec08Abschnitt22() {
        IpHmac ip = new IpHmac("ab".repeat(32));
        TemplateId template = TemplateId.of("bedwars-4x4");
        assertEquals("server:lobby-1", ValkeyKeys.server("lobby-1"));
        assertEquals("servers:all", ValkeyKeys.serversAll());
        assertEquals("servers:tpl:bedwars-4x4", ValkeyKeys.serversByTemplate(template));
        assertEquals("reserve:bedwars-4x4-12", ValkeyKeys.reserve("bedwars-4x4-12"));
        assertEquals("proxy:proxy-1", ValkeyKeys.proxy("proxy-1"));
        assertEquals("proxies", ValkeyKeys.proxies());
        assertEquals("count:online", ValkeyKeys.onlineCount());
        assertEquals("ctl:leader", ValkeyKeys.controllerLeader());
        assertEquals("ctl:names:bedwars-4x4", ValkeyKeys.controllerNames(template));
        assertEquals("ctl:override:bedwars-4x4", ValkeyKeys.controllerOverride(template));
        assertEquals("ticket:t-1", ValkeyKeys.ticket("t-1"));
        assertEquals("queue:bedwars-test", ValkeyKeys.queue("bedwars-test"));
        assertEquals("maps:bedwars", ValkeyKeys.maps("bedwars"));
        assertEquals("restart:cb-plot-1", ValkeyKeys.restart("cb-plot-1"));
        assertEquals("discord:code:ABC234", ValkeyKeys.discordCode("ABC234"));
        assertEquals("discord:pending:" + PLAYER, ValkeyKeys.discordPending(PLAYER));
        assertEquals("rl:discordcode:" + PLAYER, ValkeyKeys.discordCodeRateLimit(PLAYER));
        assertEquals("rl:api:" + ip.hex(), ValkeyKeys.apiRateLimit(ip));
        assertEquals("cache:api:/v1/status", ValkeyKeys.apiCache("/v1/status"));
    }

    @Test
    void ttlWerteAusDenSpecs() {
        assertEquals(Duration.ofSeconds(60), ValkeyKeys.PRESENCE_TTL);
        assertEquals(Duration.ofSeconds(20), ValkeyKeys.PRESENCE_HEARTBEAT);
        assertEquals(Duration.ofMinutes(10), ValkeyKeys.PROFILE_TTL);
        assertEquals(Duration.ofMinutes(30), ValkeyKeys.PARTY_TTL);
        assertEquals(Duration.ofSeconds(300), ValkeyKeys.PARTY_GRACE_TTL);
        assertEquals(Duration.ofHours(26), ValkeyKeys.DAILY_COUNTER_TTL);
        assertEquals(Duration.ofSeconds(15), ValkeyKeys.SERVER_TTL);
        assertEquals(Duration.ofSeconds(5), ValkeyKeys.SERVER_HEARTBEAT);
        assertEquals(Duration.ofSeconds(600), ValkeyKeys.DISCORD_CODE_TTL);
        assertEquals(Duration.ofMillis(1000), ValkeyKeys.STATS_RATE_LIMIT_TTL);
        assertEquals(Duration.ofSeconds(30), ValkeyKeys.RESET_CONFIRM_TTL);
    }

    @Test
    void ungueltigeSchluesselteile() {
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.server("a:b"));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.server("Lobby-1"));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.queue(" "));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.party("p1"));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.party("public"));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.playerName("a b"));
        assertThrows(IllegalArgumentException.class, () -> ValkeyKeys.maps("BedWars"));
        assertThrows(NullPointerException.class, () -> ValkeyKeys.presence(null));
    }
}
