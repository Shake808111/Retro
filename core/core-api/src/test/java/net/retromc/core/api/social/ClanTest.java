package net.retromc.core.api.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.util.NetworkTime;
import org.junit.jupiter.api.Test;

class ClanTest {

    @Test
    void rollenUndRechte() {
        assertEquals(-1, ClanRole.TRIAL.code());
        assertEquals(ClanRole.LEADER, ClanRole.ofCode(2));
        assertThrows(IllegalArgumentException.class, () -> ClanRole.ofCode(3));
        assertTrue(ClanRole.MODERATOR.canInvite());
        assertFalse(ClanRole.MEMBER.canInvite());
        assertTrue(ClanRole.MODERATOR.canKick(ClanRole.MEMBER));
        assertFalse(ClanRole.MODERATOR.canKick(ClanRole.MODERATOR));
        assertFalse(ClanRole.MEMBER.canKick(ClanRole.TRIAL));
        assertTrue(ClanRole.LEADER.canKick(ClanRole.MODERATOR));
        assertEquals(Optional.of(ClanRole.MEMBER), ClanRole.TRIAL.promoted());
        assertEquals(Optional.of(ClanRole.LEADER), ClanRole.MODERATOR.promoted());
        assertEquals(Optional.empty(), ClanRole.LEADER.promoted());
        assertEquals(Optional.of(ClanRole.TRIAL), ClanRole.MEMBER.demoted());
        assertEquals(Optional.empty(), ClanRole.LEADER.demoted());
        assertEquals("clan.role.moderator", ClanRole.MODERATOR.i18nKey());
        assertEquals(ClanJoinMode.INVITE_AND_REQUEST, ClanJoinMode.ofCode(1));
    }

    @Test
    void mitgliederNachRolleSortiert() {
        List<ClanMember> members = new ArrayList<>(List.of(
                new ClanMember(new UUID(0, 1), "zed", ClanRole.TRIAL, null),
                new ClanMember(new UUID(0, 2), "Bob", ClanRole.MEMBER, null),
                new ClanMember(new UUID(0, 3), "amy", ClanRole.MEMBER, null),
                new ClanMember(new UUID(0, 4), "Lea", ClanRole.LEADER, null),
                new ClanMember(new UUID(0, 5), "Mo", ClanRole.MODERATOR, null)));
        members.sort(ClanMember.ROLE_ORDER);
        assertEquals(List.of("Lea", "Mo", "amy", "Bob", "zed"), members.stream().map(ClanMember::name).toList());
    }

    @Test
    void clanRegelnSpec01Abschnitt66() {
        assertEquals(30, ClanRules.maxMembers(Rank.DEFAULT.defaultLimits().clanSlotBonus()));
        assertEquals(32, ClanRules.maxMembers(Rank.PREMIUM.defaultLimits().clanSlotBonus()));
        assertEquals(35, ClanRules.maxMembers(Rank.SUPREMIUM.defaultLimits().clanSlotBonus()));
        assertEquals(100, ClanRules.warXp(true, true));
        assertEquals(25, ClanRules.warXp(true, false));
        assertEquals(10, ClanRules.warXp(false, true));
        assertEquals(24, ClanRules.WARS_PER_DAY);
        assertEquals(60, ClanRules.INACTIVE_AFTER.toDays());
    }

    @Test
    void umbenennenZweimalProBerlinerTagA13() {
        assertTrue(ClanRules.renameAllowed(0));
        assertTrue(ClanRules.renameAllowed(1));
        assertFalse(ClanRules.renameAllowed(2), "3. Versuch am selben Tag");
        // 23:30 und 00:30 Berliner Zeit liegen auf verschiedenen Tagen → neuer Zähler
        LocalDate before = NetworkTime.day(Instant.parse("2026-10-27T22:30:00Z"));
        LocalDate after = NetworkTime.day(Instant.parse("2026-10-27T23:30:00Z"));
        assertEquals(LocalDate.of(2026, 10, 27), before);
        assertEquals(LocalDate.of(2026, 10, 28), after);
    }

    @Test
    void siegesquoteMitDeutschemKomma() {
        assertEquals("84,85 %", new ClanWarStats(1, ModeKey.BEDWARS, 1100, 3, 165, 140, 25).winRateText());
        assertEquals("25,00 %", new ClanWarStats(1, ModeKey.BEDWARS, 1000, 1, 132, 33, 99).winRateText());
        assertEquals("0,00 %", new ClanWarStats(1, ModeKey.BEDWARS, 1000, 1, 0, 0, 0).winRateText());
        assertEquals(84.848, new ClanWarStats(1, ModeKey.BEDWARS, 1100, 3, 165, 140, 25).winRate(), 0.001);
    }

    @Test
    void clanWarUndLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClanWar(1, ModeKey.BEDWARS, true, 5, 5, null, 0, null, false, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ClanWar(1, ModeKey.BEDWARS, true, 5, 6, 7L, 25, null, false, null, null));
        Instant now = Instant.parse("2026-10-27T00:00:00Z");
        Clan clan = new Clan(1, "Retro", "RTR", new UUID(0, 1), now, ClanJoinMode.INVITE_AND_REQUEST, false, 1700,
                now, null);
        assertEquals(2, clan.level().level());
        ClanInvite invite = new ClanInvite(1, "Retro", "RTR", new UUID(0, 2), null, now);
        assertFalse(invite.expired(now.plusSeconds(6 * 86400)));
        assertTrue(invite.expired(now.plusSeconds(7 * 86400)));
    }
}
