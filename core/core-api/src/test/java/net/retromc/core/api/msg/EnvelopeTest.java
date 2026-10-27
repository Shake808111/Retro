package net.retromc.core.api.msg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import net.retromc.core.api.BuildInfo;
import org.junit.jupiter.api.Test;

class EnvelopeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_761_000_000_000L), ZoneOffset.UTC);

    @Test
    void anfrageUndAntwortKorrelieren() {
        Envelope<String> request = Envelope.create("CTL_REQUEST", 1, "proxy-1", "list", CLOCK);
        assertEquals(1_761_000_000_000L, request.ts());
        assertNull(request.re());
        assertFalse(request.isReply());
        Envelope<String> reply = request.reply("CTL_REPLY", 1, "controller", "ok", CLOCK);
        assertEquals(request.id(), reply.re());
        assertTrue(reply.isReply());
        assertEquals("controller", reply.src());
    }

    @Test
    void typnamenUndVersion() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Envelope<>("serverState", 1, id, 0, "s", null, "d"));
        assertThrows(IllegalArgumentException.class, () -> new Envelope<>("SERVER_STATE", 0, id, 0, "s", null, "d"));
        assertThrows(IllegalArgumentException.class, () -> new Envelope<>("SERVER_STATE", 1, id, 0, " ", null, "d"));
        assertTrue(Envelope.TYPE.matcher("CTL_REQUEST").matches());
        assertFalse(Envelope.TYPE.matcher("CTL__REQUEST").matches());
    }

    @Test
    void bridgeNachrichten() {
        assertEquals(BuildInfo.BRAND_ID + ":bridge", BridgeMessage.CHANNEL);
        BridgeMessage run = BridgeMessage.runCommand("/party promote Name");
        assertEquals("party promote Name", run.argument());
        assertArrayEquals("RUN_COMMAND party promote Name".getBytes(StandardCharsets.UTF_8), run.encode());
        assertEquals(run, BridgeMessage.decode(run.encode()));
        BridgeMessage open = BridgeMessage.openGui("friends", "2");
        assertEquals("friends 2", open.argument());
        assertEquals(open, BridgeMessage.decode(open.encode()));
        assertEquals("party", BridgeMessage.openGui("party", "").argument());
        assertThrows(IllegalArgumentException.class, () -> BridgeMessage.decode("KAPUTT".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> BridgeMessage.openGui("a b", ""));
    }
}
