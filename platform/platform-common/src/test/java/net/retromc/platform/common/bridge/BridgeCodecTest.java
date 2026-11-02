package net.retromc.platform.common.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.msg.BridgeMessage;
import net.retromc.core.api.msg.BridgeMessage.Action;
import org.junit.jupiter.api.Test;

/** Rundlauf und Prüfungen der Plugin-Message {@code <brandId>:bridge} (Spec 01 §2.4). */
class BridgeCodecTest {

    @Test
    void kanalTraegtDieMarke() {
        assertEquals(BuildInfo.BRAND_ID + ":bridge", BridgeCodec.CHANNEL);
    }

    @Test
    void runCommandRundlaufVonPaperZumProxy() {
        BridgeMessage sent = BridgeMessage.runCommand("/party promote Spieler");

        BridgeMessage received = BridgeCodec.decode(BridgeCodec.encode(sent), BridgeCodec.TO_PROXY);

        assertEquals(new BridgeMessage(Action.RUN_COMMAND, "party promote Spieler"), received);
    }

    @Test
    void openGuiRundlaufVomProxyZuPaper() {
        OpenGuiRequest request = new OpenGuiRequest("clan", "Bäckerei Größe");

        BridgeMessage received = BridgeCodec.decode(BridgeCodec.encode(request.toMessage()), BridgeCodec.TO_SERVER);

        assertEquals(request, OpenGuiRequest.of(received));
    }

    @Test
    void openGuiOhneArgument() {
        OpenGuiRequest request = new OpenGuiRequest("party", "");

        byte[] data = BridgeCodec.encode(request.toMessage());

        assertArrayEquals("OPEN_GUI party".getBytes(StandardCharsets.UTF_8), data);
        assertEquals(request, OpenGuiRequest.of(BridgeCodec.decode(data, BridgeCodec.TO_SERVER)));
    }

    @Test
    void richtungWirdGeprueft() {
        byte[] runCommand = BridgeCodec.encode(BridgeMessage.runCommand("hub"));
        byte[] openGui = BridgeCodec.encode(BridgeMessage.openGui("party", ""));

        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(runCommand, BridgeCodec.TO_SERVER));
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(openGui, BridgeCodec.TO_PROXY));
    }

    @Test
    void ungueltigeDatenWerdenAbgelehnt() {
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(new byte[0], BridgeCodec.TO_PROXY));
        assertThrows(BridgeFormatException.class, () -> decode("RUN_COMMAND"));
        assertThrows(BridgeFormatException.class, () -> decode("DELETE_ALL x"));
        assertThrows(BridgeFormatException.class, () -> decode("RUN_COMMAND  "));
        assertThrows(BridgeFormatException.class, () -> decode("RUN_COMMAND op x\nstop"));
    }

    @Test
    void steuerzeichenWerdenNichtKodiert() {
        BridgeMessage message = BridgeMessage.runCommand("msg x hallo\rstop");

        assertThrows(BridgeFormatException.class, () -> BridgeCodec.encode(message));
    }

    @Test
    void groesseIstBegrenzt() {
        String limit = "a".repeat(BridgeCodec.MAX_BYTES - "RUN_COMMAND ".length());
        BridgeMessage largest = BridgeMessage.runCommand(limit);
        BridgeMessage tooLarge = BridgeMessage.runCommand(limit + "a");

        assertEquals(BridgeCodec.MAX_BYTES, BridgeCodec.encode(largest).length);
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.encode(tooLarge));
        byte[] oversized = ("RUN_COMMAND " + limit + "a").getBytes(StandardCharsets.UTF_8);
        assertThrows(BridgeFormatException.class, () -> BridgeCodec.decode(oversized, BridgeCodec.TO_PROXY));
    }

    @Test
    void openGuiAnfrageVerlangtOpenGui() {
        assertThrows(IllegalArgumentException.class, () -> OpenGuiRequest.of(BridgeMessage.runCommand("hub")));
        assertThrows(IllegalArgumentException.class, () -> new OpenGuiRequest("zwei teile", ""));
    }

    private static BridgeMessage decode(String text) {
        return BridgeCodec.decode(text.getBytes(StandardCharsets.UTF_8), BridgeCodec.TO_PROXY);
    }
}
