package net.retromc.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.event.GlobalChat;
import net.retromc.core.api.event.PartyUpdate;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.server.ServerState;
import org.junit.jupiter.api.Test;

class EnvelopeCodecTest {

    private static final Clock CLOCK = Clock.fixed(Samples.TS, ZoneOffset.UTC);
    private final EnvelopeCodec codec = new EnvelopeCodec();

    private static <P> Envelope<P> envelope(MessageType<P> type, Object payload) {
        return Envelope.create(type.name(), type.version(), "lobby-1", type.cast(payload), CLOCK);
    }

    @Test
    void rundlaufFuerJedenNachrichtentyp() {
        Map<MessageType<?>, Object> samples = Samples.all();
        assertEquals(MessageType.all().size(), samples.size(), "jeder Typ braucht ein Beispiel");
        for (Map.Entry<MessageType<?>, Object> entry : samples.entrySet()) {
            Envelope<?> sent = envelope(entry.getKey(), entry.getValue());
            String json = codec.encode(sent);
            Envelope<?> received = codec.decode(json).orElseThrow();
            assertEquals(sent, received, entry.getKey().name());
        }
    }

    @Test
    void felderNachSpec08Abschnitt612() {
        Envelope<AfkChanged> request = envelope(MessageType.AFK_CHANGED, new AfkChanged(Samples.A, true));
        JsonObject json = JsonParser.parseString(codec.encode(request)).getAsJsonObject();
        assertEquals(List.of("t", "v", "id", "ts", "src", "d"), List.copyOf(json.keySet()));
        assertEquals("AFK_CHANGED", json.get("t").getAsString());
        assertEquals(1, json.get("v").getAsInt());
        assertEquals(request.id().toString(), json.get("id").getAsString());
        assertEquals(Samples.TS.toEpochMilli(), json.get("ts").getAsLong());
        assertEquals("lobby-1", json.get("src").getAsString());
        assertFalse(json.has("re"), "re nur bei Antworten");
        assertEquals(Samples.A.toString(), json.getAsJsonObject("d").get("uuid").getAsString());

        Envelope<AfkChanged> reply = request.reply("AFK_CHANGED", 1, "proxy-1", new AfkChanged(Samples.A, false),
                CLOCK);
        JsonObject replyJson = JsonParser.parseString(codec.encode(reply)).getAsJsonObject();
        assertEquals(request.id().toString(), replyJson.get("re").getAsString());
    }

    @Test
    void sonderfelderImJson() {
        JsonObject party = JsonParser.parseString(codec.encode(envelope(MessageType.PARTY_UPDATE,
                new PartyUpdate(Samples.PARTY, Samples.A, List.of(Samples.A), true)))).getAsJsonObject()
                .getAsJsonObject("d");
        assertTrue(party.get("public").getAsBoolean(), "Feldname laut Spec: public");
        assertFalse(party.has("publicParty"));
        JsonObject server = JsonParser.parseString(codec.encode(envelope(MessageType.SERVER_UP,
                Samples.server(ServerState.BOOTING)))).getAsJsonObject()
                .getAsJsonObject("d");
        assertEquals(Samples.TS.toEpochMilli(), server.get("startedAt").getAsLong());
        assertEquals("BOOTING", server.get("state").getAsString());
        JsonObject stats = JsonParser.parseString(codec.encode(envelope(MessageType.LEADERBOARD_CHANGED,
                Samples.all().get(MessageType.LEADERBOARD_CHANGED)))).getAsJsonObject().getAsJsonObject("d");
        assertEquals("skywars", stats.get("mode").getAsString());
        assertEquals("30d", stats.get("period").getAsString());
    }

    @Test
    void unbekannterTypUndHoehereVersionWerdenIgnoriert() {
        String unknown = "{\"t\":\"TABLIST_OVERRIDE\",\"v\":1,\"id\":\"" + UUID.randomUUID()
                + "\",\"ts\":1,\"src\":\"x\",\"d\":{}}";
        assertEquals(Optional.empty(), codec.decode(unknown));
        String newer = codec.encode(envelope(MessageType.AFK_CHANGED, new AfkChanged(Samples.A, true)))
                .replace("\"v\":1", "\"v\":2");
        assertEquals(Optional.empty(), codec.decode(newer));
        String extraField = codec.encode(envelope(MessageType.AFK_CHANGED, new AfkChanged(Samples.A, true)))
                .replace("\"afk\":true", "\"afk\":true,\"neu\":5");
        assertTrue(codec.decode(extraField).isPresent(), "unbekannte Felder in d sind erlaubt");
    }

    @Test
    void nachrichtUeber64KiBWirdBeimSendenAbgelehnt() {
        String text = "x".repeat(EnvelopeCodec.MAX_BYTES);
        Envelope<GlobalChat> big = envelope(MessageType.GLOBAL_CHAT, new GlobalChat(Samples.A, "lobby-1", text, 1));
        MessageTooLargeException error = assertThrows(MessageTooLargeException.class, () -> codec.encode(big));
        assertTrue(error.bytes() > EnvelopeCodec.MAX_BYTES);
        String fits = "x".repeat(EnvelopeCodec.MAX_BYTES - 400);
        assertTrue(codec.encode(envelope(MessageType.GLOBAL_CHAT, new GlobalChat(Samples.A, "lobby-1", fits, 1)))
                .length() <= EnvelopeCodec.MAX_BYTES);
    }

    @Test
    void kaputteNachrichten() {
        assertThrows(MessageFormatException.class, () -> codec.decode("kein json"));
        assertThrows(MessageFormatException.class, () -> codec.decode("[1,2]"));
        assertThrows(MessageFormatException.class, () -> codec.decode("{\"t\":\"AFK_CHANGED\"}"));
        String noUuid = "{\"t\":\"AFK_CHANGED\",\"v\":1,\"id\":\"" + UUID.randomUUID()
                + "\",\"ts\":1,\"src\":\"x\",\"d\":{\"afk\":true}}";
        assertThrows(MessageFormatException.class, () -> codec.decode(noUuid));
        Envelope<String> wrong = Envelope.create("AFK_CHANGED", 1, "x", "kein Record", CLOCK);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(wrong));
    }

    @Test
    void typisiertesDekodieren() {
        String json = codec.encode(envelope(MessageType.AFK_CHANGED, new AfkChanged(Samples.A, true)));
        assertTrue(codec.decode(json, MessageType.AFK_CHANGED).orElseThrow().d().afk());
        assertEquals(Optional.empty(), codec.decode(json, MessageType.NICK_CHANGED));
    }
}
