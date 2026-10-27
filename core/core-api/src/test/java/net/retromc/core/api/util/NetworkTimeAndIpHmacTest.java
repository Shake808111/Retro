package net.retromc.core.api.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class NetworkTimeAndIpHmacTest {

    @Test
    void tageswechselInBerlin() {
        // 22:59:59 UTC im Sommer = 00:59:59 Berlin des Folgetags
        assertEquals(LocalDate.of(2026, 7, 2), NetworkTime.day(Instant.parse("2026-07-01T22:59:59Z")));
        assertEquals(LocalDate.of(2026, 7, 1), NetworkTime.day(Instant.parse("2026-07-01T21:59:59Z")));
        // Winterzeit: Mitternacht Berlin = 23:00 UTC
        assertEquals(Instant.parse("2026-12-31T23:00:00Z"), NetworkTime.startOfDay(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void ipNurAlsHmac() throws UnknownHostException {
        byte[] secret = "geheim".getBytes(StandardCharsets.UTF_8);
        InetAddress ip = InetAddress.getByName("192.0.2.7");
        IpHmac first = IpHmac.of(secret, ip);
        assertEquals(64, first.hex().length());
        assertEquals(first, IpHmac.of(secret, ip));
        assertNotEquals(first, IpHmac.of("anders".getBytes(StandardCharsets.UTF_8), ip));
        assertNotEquals(first, IpHmac.of(secret, InetAddress.getByName("192.0.2.8")));
        assertArrayEquals(first.bytes(), IpHmac.ofBytes(first.bytes()).bytes());
        assertFalse(first.toString().contains(first.hex()), "toString darf den Hash nicht vollständig zeigen");
        assertFalse(first.toString().contains("192.0.2.7"));
        assertThrows(IllegalArgumentException.class, () -> IpHmac.of(new byte[0], ip));
        assertThrows(IllegalArgumentException.class, () -> new IpHmac("ABC"));
    }
}
