package net.retromc.platform.common.bridge;

import java.util.Objects;
import java.util.Set;
import net.retromc.core.api.msg.BridgeMessage;
import net.retromc.core.api.msg.BridgeMessage.Action;

/**
 * Kodierung der Plugin-Message {@code <brandId>:bridge} (Spec 01 §2.4) mit den Prüfungen, die beide Seiten brauchen.
 * Das Format selbst ({@code <AKTION> <argument>} als UTF-8) gehört zu {@link BridgeMessage}.
 *
 * <ul>
 *   <li>Größe höchstens {@link #MAX_BYTES} (Vanilla-Grenze für Plugin-Messages an den Server).</li>
 *   <li>Keine Steuerzeichen im Argument (keine Zeilenumbrüche in Befehlszeilen).</li>
 *   <li>Richtung: der Proxy nimmt nur {@code RUN_COMMAND} an ({@link #TO_PROXY}), Paper nur {@code OPEN_GUI}
 *       ({@link #TO_SERVER}).</li>
 * </ul>
 *
 * <p>Sicherheit: Paper kann nicht unterscheiden, ob eine Plugin-Message vom Proxy oder vom Client stammt. Der Proxy
 * leitet den Kanal deshalb nie weiter und verarbeitet ihn nur, wenn er von einer Server-Verbindung kommt.
 */
public final class BridgeCodec {

    /** Kanalname, heute {@code retromc:bridge}. */
    public static final String CHANNEL = BridgeMessage.CHANNEL;

    /** Größte erlaubte Nachricht in Bytes. */
    public static final int MAX_BYTES = 32_767;

    /** Aktionen, die der Proxy von einem Server annimmt. */
    public static final Set<Action> TO_PROXY = Set.of(Action.RUN_COMMAND);

    /** Aktionen, die ein Server vom Proxy annimmt. */
    public static final Set<Action> TO_SERVER = Set.of(Action.OPEN_GUI);

    private BridgeCodec() {
    }

    /**
     * Kodiert die Nachricht.
     *
     * @throws BridgeFormatException bei Steuerzeichen oder Überlänge
     */
    public static byte[] encode(BridgeMessage message) {
        Objects.requireNonNull(message, "message");
        requireNoControlCharacters(message.argument());
        byte[] data = message.encode();
        if (data.length > MAX_BYTES) {
            throw new BridgeFormatException("Bridge-Nachricht zu groß: " + data.length + " Bytes (höchstens "
                    + MAX_BYTES + ")");
        }
        return data;
    }

    /**
     * Dekodiert empfangene Bytes und prüft die erlaubte Richtung.
     *
     * @param accepted {@link #TO_PROXY} oder {@link #TO_SERVER}
     * @throws BridgeFormatException bei ungültigen Daten oder nicht erlaubter Aktion
     */
    public static BridgeMessage decode(byte[] data, Set<Action> accepted) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw new BridgeFormatException("Bridge-Nachricht mit ungültiger Länge: " + data.length + " Bytes");
        }
        BridgeMessage message;
        try {
            message = BridgeMessage.decode(data);
        } catch (IllegalArgumentException e) {
            throw new BridgeFormatException("Ungültige Bridge-Nachricht: " + e.getMessage(), e);
        }
        if (!accepted.contains(message.action())) {
            throw new BridgeFormatException("Aktion " + message.action() + " ist in dieser Richtung nicht erlaubt");
        }
        requireNoControlCharacters(message.argument());
        return message;
    }

    private static void requireNoControlCharacters(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                throw new BridgeFormatException("Steuerzeichen in Bridge-Nachricht an Position " + i);
            }
        }
    }
}
