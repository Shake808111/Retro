package net.retromc.core.api.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RankTest {

    /** Spec 01 §6.1: Gewicht | Gruppe | Anzeigename | Präfix | Farbe (Ära 26) | Farbe Ära 18. */
    private static final List<Object[]> TABLE = List.of(
            new Object[] {1000, "spec", "Spectator", "Spec", "§7", "§7"},
            new Object[] {900, "admin", "Administrator", "Admin", "§4", "§4"},
            new Object[] {850, "srdev", "Senior-Developer", "SrDev", "§c", "§c"},
            new Object[] {800, "dev", "Developer", "Dev", "§c", "§c"},
            new Object[] {750, "srcontent", "Senior-Content", "SrContent", "§c", "§c"},
            new Object[] {700, "content", "Content", "Content", "§c", "§c"},
            new Object[] {650, "srmod", "Senior-Moderator", "SrMod", "§c", "§c"},
            new Object[] {600, "mod", "Moderator", "Mod", "§c", "§c"},
            new Object[] {550, "sup", "Supporter", "Sup", "§c", "§c"},
            new Object[] {500, "builder", "Builder", "Builder", "§2", "§2"},
            new Object[] {450, "vip", "VIP", "VIP", "§d", "§5"},
            new Object[] {400, "supremiumplus", "Supremium+", "Suprem+", "§d", "§d"},
            new Object[] {350, "supremium", "Supremium", "Suprem", "§b", "§b"},
            new Object[] {300, "premiumplus", "Premium Plus", "Premium+", "§e", "§e"},
            new Object[] {200, "premium", "Premium", null, "§6", "§6"},
            new Object[] {0, "default", "Spieler", null, "§a", "§8"});

    @Test
    void rangtabelleEntsprichtSpec01Abschnitt61() {
        Rank[] ranks = Rank.values();
        assertEquals(TABLE.size(), ranks.length);
        for (int i = 0; i < ranks.length; i++) {
            Object[] row = TABLE.get(i);
            Rank rank = ranks[i];
            assertEquals(row[0], rank.weight(), rank.name());
            assertEquals(row[1], rank.group(), rank.name());
            assertEquals(row[2], rank.displayName(), rank.name());
            assertEquals(row[3], rank.prefix(), rank.name());
            assertEquals(row[4], rank.color(), rank.name());
            assertEquals(row[5], rank.legacyStyleColor(), rank.name());
        }
    }

    @Test
    void verlaufNurFuerSupremiumPlus() {
        assertEquals("#FF55FF:#55FFFF", Rank.SUPREMIUM_PLUS.gradient());
        Arrays.stream(Rank.values()).filter(rank -> rank != Rank.SUPREMIUM_PLUS)
                .forEach(rank -> assertNull(rank.gradient(), rank.name()));
        assertEquals(" §7| ", Rank.SEPARATOR);
        assertEquals(" §8┃ ", Rank.LEGACY_SEPARATOR);
    }

    @Test
    void rangVorteileSpec01Abschnitt62() {
        assertEquals(new RankLimits(3, 250, 0, 0, 0), Rank.DEFAULT.defaultLimits());
        assertEquals(new RankLimits(10, 500, 2, 0, 0), Rank.PREMIUM.defaultLimits());
        assertEquals(new RankLimits(26, 500, 2, 0, 0), Rank.PREMIUM_PLUS.defaultLimits());
        assertEquals(new RankLimits(64, 1000, 5, 3, 10), Rank.SUPREMIUM.defaultLimits());
        assertEquals(new RankLimits(64, 1000, 5, 3, 30), Rank.SUPREMIUM_PLUS.defaultLimits());
        assertEquals(new RankLimits(64, 1000, 5, 3, RankLimits.UNLIMITED), Rank.VIP.defaultLimits());
        assertEquals(new RankLimits(64, 1000, 5, RankLimits.UNLIMITED, RankLimits.UNLIMITED),
                Rank.MOD.defaultLimits());

        assertEquals(LobbyMovement.NONE, Rank.DEFAULT.defaultFlags().lobbyMovement());
        assertEquals(LobbyMovement.DOUBLE_JUMP, Rank.PREMIUM.defaultFlags().lobbyMovement());
        assertEquals(LobbyMovement.FLY, Rank.PREMIUM_PLUS.defaultFlags().lobbyMovement());
        assertFalse(Rank.PREMIUM.defaultFlags().nick());
        assertTrue(Rank.PREMIUM_PLUS.defaultFlags().nick());
        assertTrue(Rank.PREMIUM.defaultFlags().publicParty());
        assertTrue(Rank.PREMIUM.defaultFlags().hideStatus());
        assertFalse(Rank.PREMIUM_PLUS.defaultFlags().silentLobby());
        assertTrue(Rank.SUPREMIUM.defaultFlags().silentLobby());
        assertTrue(Rank.SUPREMIUM.defaultFlags().fullServer());
        assertTrue(Rank.SUPREMIUM.defaultFlags().coloredGg());
        assertEquals(0, Rank.PREMIUM_PLUS.defaultFlags().joinPriority());
        assertEquals(1, Rank.SUPREMIUM.defaultFlags().joinPriority());
        assertEquals(2, Rank.SUPREMIUM_PLUS.defaultFlags().joinPriority());
        assertEquals(3, Rank.VIP.defaultFlags().joinPriority());
        assertEquals(3, Rank.ADMIN.defaultFlags().joinPriority());
    }

    @Test
    void coinBoostNurSupremiumUndSupremiumPlus() {
        for (Rank rank : Rank.values()) {
            boolean expected = rank == Rank.SUPREMIUM || rank == Rank.SUPREMIUM_PLUS;
            assertEquals(expected, rank.defaultFlags().booster(), rank.name());
        }
    }

    @Test
    void teamUndGewichtsvergleich() {
        assertTrue(Rank.BUILDER.isTeam());
        assertTrue(Rank.ADMIN.isTeam());
        assertFalse(Rank.VIP.isTeam());
        assertTrue(Rank.SUPREMIUM.isAtLeast(Rank.PREMIUM));
        assertFalse(Rank.PREMIUM.isAtLeast(Rank.PREMIUM_PLUS));
        assertEquals(Optional.of(Rank.SUPREMIUM_PLUS), Rank.byGroup("SupremiumPlus"));
        assertEquals(Optional.empty(), Rank.byGroup("elite"));
        assertEquals(Rank.PREMIUM, Rank.forWeight(250));
        assertEquals(Rank.DEFAULT, Rank.forWeight(-5));
        assertEquals(Rank.SPEC, Rank.forWeight(5000));
    }

    @Test
    void partyLimitSpec01A6() {
        assertEquals(3, RankInfo.of(Rank.DEFAULT).limits().partySize());
        assertEquals(10, RankInfo.of(Rank.PREMIUM).limits().partySize());
        assertEquals(64, RankInfo.of(Rank.SUPREMIUM).limits().partySize());
    }

    @Test
    void freundeLimitSpec01A7() {
        assertEquals(250, Rank.DEFAULT.defaultLimits().friends());
        assertEquals(500, Rank.PREMIUM.defaultLimits().friends());
        assertEquals(1000, Rank.SUPREMIUM.defaultLimits().friends());
    }

    @Test
    void tageslimits() {
        assertTrue(RankLimits.allows(3, 2));
        assertFalse(RankLimits.allows(3, 3));
        assertFalse(RankLimits.allows(0, 0));
        assertTrue(RankLimits.allows(RankLimits.UNLIMITED, 1_000_000));
    }
}
