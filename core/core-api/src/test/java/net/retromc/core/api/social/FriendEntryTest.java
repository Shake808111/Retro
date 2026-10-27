package net.retromc.core.api.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendEntryTest {

    private static final Instant SINCE = Instant.parse("2026-01-01T00:00:00Z");

    private static FriendEntry entry(String name, boolean favorite, boolean online) {
        return new FriendEntry(UUID.nameUUIDFromBytes(name.getBytes()), name, SINCE, favorite,
                online ? "lobby-1" : null, online ? null : SINCE);
    }

    @Test
    void guiReihenfolgeSpec01Abschnitt52() {
        List<FriendEntry> entries = new ArrayList<>(List.of(
                entry("zora", false, false), entry("Bert", true, false), entry("anna", false, true),
                entry("Carl", true, true), entry("dora", false, true), entry("Alex", true, true)));
        entries.sort(FriendEntry.GUI_ORDER);
        assertEquals(List.of("Alex", "Carl", "anna", "dora", "Bert", "zora"),
                entries.stream().map(FriendEntry::name).toList());
    }

    @Test
    void chatlisteOnlineZuerst() {
        List<FriendEntry> entries = new ArrayList<>(List.of(
                entry("zora", true, false), entry("Bert", false, true), entry("anna", false, false)));
        entries.sort(FriendEntry.LIST_ORDER);
        assertEquals(List.of("Bert", "anna", "zora"), entries.stream().map(FriendEntry::name).toList());
    }

    @Test
    void ergebnisseUndZaehler() {
        assertEquals(new FriendResult(FriendOutcome.LIMIT_SELF, 250), new FriendResult(FriendOutcome.LIMIT_SELF, 250));
        assertEquals(true, FriendResult.of(FriendOutcome.ADDED).success());
        assertEquals(false, FriendResult.of(FriendOutcome.BLOCKED).success());
        assertEquals(100, FriendService.MAX_INCOMING_REQUESTS);
        assertEquals(10, FriendService.LIST_PAGE_SIZE);
        assertThrows(IllegalArgumentException.class, () -> new OnlineCount(5, 3));
        assertThrows(IllegalArgumentException.class, () -> new JumpResult(JumpResult.Outcome.OK, null));
        assertEquals("lobby-2", new JumpResult(JumpResult.Outcome.OK, "lobby-2").server());
    }
}
