package net.retromc.core.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UlidTest {

    @Test
    void formatUndZeitstempel() {
        String ulid = Ulid.generate(1_700_000_000_123L, new Random(1));
        assertEquals(26, ulid.length());
        assertTrue(Ulid.isValid(ulid), ulid);
        assertEquals(Instant.ofEpochMilli(1_700_000_000_123L), Ulid.timestamp(ulid));
        assertEquals("00000000000000000000000000".length(), Ulid.generate(0, new Random(2)).length());
        assertTrue(Ulid.generate(0, new Random(2)).startsWith("0000000000"));
    }

    @Test
    void sortierungFolgtDerZeit() {
        Random random = new Random(3);
        String earlier = Ulid.generate(1000, random);
        String later = Ulid.generate(1001, random);
        assertTrue(earlier.compareTo(later) < 0);
    }

    @Test
    void zufallsUlidsSindEindeutig() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertTrue(seen.add(Ulid.next()));
        }
    }

    @Test
    void ungueltigeWerte() {
        assertFalse(Ulid.isValid(null));
        assertFalse(Ulid.isValid("p1"));
        assertFalse(Ulid.isValid("8ZZZZZZZZZZZZZZZZZZZZZZZZZ"), "Zeitstempel über 48 Bit");
        assertFalse(Ulid.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAI"), "I ist kein Crockford-Zeichen");
        assertThrows(IllegalArgumentException.class, () -> Ulid.generate(-1, new Random()));
        assertThrows(IllegalArgumentException.class, () -> Ulid.timestamp("kaputt"));
    }
}
