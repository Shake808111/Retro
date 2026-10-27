package net.retromc.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValidatorsTest {

    @Test
    void clanTagWieSpec01A1() {
        for (String valid : List.of("ab", "3IX", "G$%.,", "ÄöÜ#", "a_b-+", "üü")) {
            assertTrue(ClanTag.valid(valid), valid);
        }
        for (String invalid : List.of("a", "abcdef", "a b", "<>", "", "ab!", "ßß")) {
            assertFalse(ClanTag.valid(invalid), invalid);
        }
        assertFalse(ClanTag.valid(null));
        assertEquals("äbc", ClanTag.normalized("ÄBC"));
    }

    @Test
    void clanNameDreiBisSechzehnZeichen() {
        assertTrue(ClanName.valid("abc"));
        assertTrue(ClanName.valid("Clan_Name-2026AB"));
        assertFalse(ClanName.valid("ab"));
        assertFalse(ClanName.valid("a".repeat(17)));
        assertFalse(ClanName.valid("mit leer"));
        assertFalse(ClanName.valid("Ümlaut"));
        assertFalse(ClanName.valid(null));
        assertEquals(ClanName.normalized("CoolClan"), ClanName.normalized("coolclan"));
    }

    @Test
    void spielernamen() {
        assertTrue(PlayerName.valid("Notch"));
        assertTrue(PlayerName.valid("a_b"));
        assertTrue(PlayerName.valid("x".repeat(16)));
        assertFalse(PlayerName.valid("ab"));
        assertFalse(PlayerName.valid("x".repeat(17)));
        assertFalse(PlayerName.valid("Name-1"));
        assertFalse(PlayerName.valid(".Bedrock"));
        assertFalse(PlayerName.valid(null));
        assertEquals("notch", PlayerName.normalized("NoTcH"));
    }
}
