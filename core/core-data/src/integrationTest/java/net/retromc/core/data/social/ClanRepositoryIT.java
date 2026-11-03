package net.retromc.core.data.social;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.retromc.core.api.event.ClanUpdate;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.social.ClanJoinMode;
import net.retromc.core.api.social.ClanMember;
import net.retromc.core.api.social.ClanOutcome;
import net.retromc.core.api.social.ClanRole;
import net.retromc.core.api.social.ClanWar;
import net.retromc.core.api.social.ClanWarStats;
import net.retromc.core.api.social.Setting;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.testing.TestTransactions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Clans gegen PostgreSQL und Valkey (Spec 01 §3.4, §6.6; Abnahme A2, A13, A14). */
@Tag("integration")
class ClanRepositoryIT {

    private static DataFixture data;
    private static ClanRepository clans;
    private static IgnoreRepository ignores;

    @BeforeAll
    static void start() {
        data = new DataFixture();
        ignores = new IgnoreRepository(data.async, data.valkey, () -> data.ranks);
        clans = new ClanRepository(data.async, data.valkey, () -> data.ranks, () -> ignores, data.events, data.clock);
    }

    @AfterAll
    static void stop() {
        data.close();
    }

    @Test
    void namenUndTagsSindOhneGrossKleinschreibungEindeutig() {
        UUID a = data.player("Alpha1");
        UUID b = data.player("Bravo1");
        assertEquals(ClanOutcome.CREATED, await(clans.create(a, "Retro_Kings", "RK1")).outcome());
        assertEquals(ClanOutcome.NAME_TAKEN, await(clans.create(b, "retro_kings", "XYZ")).outcome());
        assertEquals(ClanOutcome.TAG_TAKEN, await(clans.create(b, "Other_Kings", "rk1")).outcome());
        assertEquals(ClanOutcome.INVALID_NAME, await(clans.create(b, "ab", "XY")).outcome());
        assertEquals(ClanOutcome.INVALID_TAG, await(clans.create(b, "Valid_Name", "a b")).outcome());
        assertEquals(ClanOutcome.IN_CLAN, await(clans.create(a, "Second_One", "SEC")).outcome());
        assertTrue(await(clans.byName("RETRO_KINGS")).isPresent());
        assertTrue(await(clans.byTag("rK1")).isPresent());
        assertFalse(data.events.of(ClanUpdate.class).isEmpty());
        assertTrue(data.events.of(ProfileInvalidate.class).stream().anyMatch(event -> event.uuid().equals(a)));
    }

    @Test
    void einladungAnfrageRollenUndRauswurf() {
        UUID leader = data.player("Leader2");
        UUID invited = data.player("Invited2");
        UUID asker = data.player("Asker2");
        UUID outsider = data.player("Outsid2");
        long clanId = await(clans.create(leader, "Team_Two", "T2")).clan().id();

        assertEquals(ClanOutcome.INVITED, await(clans.invite(leader, invited)).outcome());
        assertEquals(ClanOutcome.ALREADY_INVITED, await(clans.invite(leader, invited)).outcome());
        assertEquals(ClanOutcome.NO_INVITE, await(clans.join(outsider, "Team_Two")).outcome());
        assertEquals(ClanOutcome.JOINED, await(clans.join(invited, "team_two")).outcome());
        assertEquals(ClanRole.TRIAL, await(clans.role(invited)).orElseThrow());
        assertEquals(ClanOutcome.NOT_ALLOWED, await(clans.invite(invited, outsider)).outcome());

        assertEquals(ClanOutcome.REQUEST_SENT, await(clans.request(asker, "t2")).outcome());
        assertEquals(ClanOutcome.ALREADY_REQUESTED, await(clans.request(asker, "T2")).outcome());
        assertEquals(1, await(clans.requests(clanId)).size());
        assertEquals(ClanOutcome.JOINED, await(clans.acceptRequest(leader, asker)).outcome());
        assertEquals(ClanRole.TRIAL, await(clans.role(asker)).orElseThrow());

        assertEquals(ClanOutcome.PROMOTED, await(clans.promote(leader, invited)).outcome());
        assertEquals(ClanOutcome.PROMOTED, await(clans.promote(leader, invited)).outcome());
        assertEquals(ClanRole.MODERATOR, await(clans.role(invited)).orElseThrow());
        assertEquals(ClanOutcome.CONFIRM_TRANSFER, await(clans.promote(leader, invited)).outcome());
        assertEquals(ClanOutcome.NOT_ALLOWED, await(clans.kick(asker, invited)).outcome());
        assertEquals(ClanOutcome.KICKED, await(clans.kick(invited, asker)).outcome());
        assertTrue(await(clans.role(asker)).isEmpty());

        assertEquals(ClanOutcome.LEADER_CANNOT_LEAVE, await(clans.leave(leader)).outcome());
        assertEquals(ClanOutcome.LEADER_TRANSFERRED, await(clans.transferLeadership(leader, invited)).outcome());
        assertEquals(ClanRole.LEADER, await(clans.role(invited)).orElseThrow());
        assertEquals(ClanRole.MODERATOR, await(clans.role(leader)).orElseThrow());
        assertEquals(invited, await(clans.clan(clanId)).orElseThrow().owner());
        assertEquals(ClanOutcome.LEFT, await(clans.leave(leader)).outcome());
        List<ClanMember> members = await(clans.members(clanId));
        assertEquals(List.of(invited), members.stream().map(ClanMember::uuid).toList());

        assertEquals(ClanOutcome.UPDATED, await(clans.setJoinMode(invited, ClanJoinMode.INVITE_ONLY)).outcome());
        assertEquals(ClanOutcome.REQUESTS_DISABLED, await(clans.request(outsider, "T2")).outcome());
    }

    @Test
    void einladungBlockiertWennZielIgnoriert() {
        UUID leader = data.player("Leader3");
        UUID target = data.player("Target3");
        await(clans.create(leader, "Team_Three", "T3"));
        await(ignores.add(target, leader));
        assertEquals(ClanOutcome.BLOCKED, await(clans.invite(leader, target)).outcome());
    }

    @Test
    void groesseHaengtAmRangDesLeiters() {
        UUID leader = data.player("Leader4");
        long clanId = await(clans.create(leader, "Team_Four", "T4")).clan().id();
        assertEquals(30, await(clans.maxMembers(clanId)));
        data.ranks.set(leader, Rank.PREMIUM);
        assertEquals(32, await(clans.maxMembers(clanId)));
        data.ranks.set(leader, Rank.SUPREMIUM);
        assertEquals(35, await(clans.maxMembers(clanId)));
        data.ranks.set(leader, Rank.DEFAULT);
        for (int i = 0; i < 29; i++) {
            UUID member = data.player("Filler" + i);
            TestTransactions.committed(data.database.dataSource(), connection -> Jdbc.update(connection,
                    "INSERT INTO social.clan_member (clan_id, uuid, role, joined_at) VALUES (?, ?, 0, now())",
                    clanId, member));
        }
        assertEquals(ClanOutcome.FULL, await(clans.invite(leader, data.player("Late4"))).outcome());
    }

    @Test
    void umbenennenHoechstensZweimalProTagEuropeBerlin() {
        UUID leader = data.player("Leader5");
        await(clans.create(leader, "Team_Five", "T5"));
        data.clock.set(Instant.parse("2026-11-03T08:00:00Z"));
        assertEquals(ClanOutcome.RENAMED, await(clans.rename(leader, "Team_Five_A", "T5A")).outcome());
        assertEquals(ClanOutcome.RENAMED, await(clans.rename(leader, "Team_Five_B", "T5B")).outcome());
        assertEquals(ClanOutcome.RENAME_LIMIT, await(clans.rename(leader, "Team_Five_C", "T5C")).outcome());
        // 23:30 UTC = 00:30 in Berlin am nächsten Tag: Zähler beginnt neu.
        data.clock.set(Instant.parse("2026-11-03T23:30:00Z"));
        assertEquals(ClanOutcome.RENAMED, await(clans.rename(leader, "Team_Five_C", "T5C")).outcome());
        assertEquals(3, TestTransactions.count(data.database.dataSource(),
                "SELECT count(*) FROM social.clan_rename_log l JOIN social.clan c ON c.id = l.clan_id"
                        + " WHERE c.tag = 'T5C'"));
        data.clock.set(Instant.parse("2026-11-02T10:00:00Z"));
    }

    @Test
    void inaktivitaetLoeschtNach60Tagen() {
        UUID old = data.player("Old6");
        UUID fresh = data.player("Fresh6");
        long oldId = await(clans.create(old, "Old_Clan", "OLD")).clan().id();
        long freshId = await(clans.create(fresh, "Fresh_Clan", "FRS")).clan().id();
        Instant now = data.clock.instant();
        TestTransactions.committed(data.database.dataSource(), connection -> {
            Jdbc.update(connection, "UPDATE social.clan SET last_active = ? WHERE id = ?",
                    now.minus(Duration.ofDays(61)), oldId);
            return Jdbc.update(connection, "UPDATE social.clan SET last_active = ? WHERE id = ?",
                    now.minus(Duration.ofDays(59)), freshId);
        });
        int deleted = await(clans.deleteInactive(now.minus(Duration.ofDays(60))));
        assertTrue(deleted >= 1);
        assertTrue(await(clans.clan(oldId)).isEmpty());
        assertTrue(await(clans.clan(freshId)).isPresent());
        assertTrue(await(clans.role(old)).isEmpty());
    }

    @Test
    void clanWarEloXpUndStatistikReset() {
        UUID a = data.player("WarA7");
        UUID b = data.player("WarB7");
        long clanA = await(clans.create(a, "War_Alpha", "WA")).clan().id();
        long clanB = await(clans.create(b, "War_Bravo", "WB")).clan().id();
        ClanWar war = await(clans.recordWar(ModeKey.BEDWARS, clanA, clanB, clanA, true, null));
        assertEquals(25, war.eloDelta());
        assertEquals(1025, await(clans.elo(clanA, ModeKey.BEDWARS)));
        assertEquals(975, await(clans.elo(clanB, ModeKey.BEDWARS)));
        ClanWarStats stats = await(clans.warStats(clanA, ModeKey.BEDWARS)).orElseThrow();
        assertEquals(1, stats.games());
        assertEquals(1, stats.wins());
        assertEquals("100,00 %", stats.winRateText());
        assertEquals(100, await(clans.level(clanA)).xp());
        assertEquals(25, await(clans.level(clanB)).xp());
        assertEquals(1, await(clans.warsToday(clanA, ModeKey.BEDWARS)));

        assertEquals(ClanOutcome.STATS_ENABLED, await(clans.toggleStatsMode(a, ModeKey.BEDWARS)).outcome());
        assertTrue(await(clans.statsModes(clanA)).contains(ModeKey.BEDWARS));
        assertTrue(await(clans.stats(clanA, ModeKey.BEDWARS)).isPresent());
        assertEquals(ClanOutcome.STATS_DISABLED, await(clans.toggleStatsMode(a, ModeKey.BEDWARS)).outcome());
        assertTrue(await(clans.stats(clanA, ModeKey.BEDWARS)).isEmpty());

        assertEquals(ClanOutcome.NO_RESET_TOKEN, await(clans.resetStats(a, ModeKey.BEDWARS)).outcome());
        TestTransactions.committed(data.database.dataSource(), connection -> Jdbc.update(connection,
                "INSERT INTO economy.wallet (uuid, reset_tokens) VALUES (?, 1)", a));
        assertEquals(ClanOutcome.STATS_RESET, await(clans.resetStats(a, ModeKey.BEDWARS)).outcome());
        assertEquals(1000, await(clans.elo(clanA, ModeKey.BEDWARS)));
        assertEquals(1, TestTransactions.count(data.database.dataSource(),
                "SELECT count(*) FROM social.clan_war WHERE clan_a = ? AND hidden", clanA));
    }

    @Test
    void statusEmpfaengerBeachtenEinstellungen() {
        UUID leader = data.player("Leader8");
        UUID member = data.player("Member8");
        UUID quiet = data.player("Quiet8");
        await(clans.create(leader, "Team_Eight", "T8"));
        await(clans.invite(leader, member));
        await(clans.join(member, "Team_Eight"));
        await(clans.invite(leader, quiet));
        await(clans.join(quiet, "Team_Eight"));
        PrivacyRepository privacy = new PrivacyRepository(data.async, data.valkey, data.events);
        await(privacy.set(quiet, Setting.NOTIFY_CLAN, 0));
        assertEquals(List.of(member), await(clans.statusRecipients(leader)));
        await(privacy.set(leader, Setting.HIDE_FROM_CLAN, 1));
        assertTrue(await(clans.statusRecipients(leader)).isEmpty());
    }
}
