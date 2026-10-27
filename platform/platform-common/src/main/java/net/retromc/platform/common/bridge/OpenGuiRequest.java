package net.retromc.platform.common.bridge;

import java.util.Objects;
import net.retromc.core.api.msg.BridgeMessage;
import net.retromc.core.api.msg.BridgeMessage.Action;

/**
 * Inhalt einer {@code OPEN_GUI}-Nachricht (Velocity → Paper, Spec 01 §2.4): GUI-ID und optionales Argument.
 *
 * @param guiId ID der GUI ohne Leerzeichen, z. B. {@code party}
 * @param argument Argument, leer wenn keines
 */
public record OpenGuiRequest(String guiId, String argument) {

    public OpenGuiRequest {
        Objects.requireNonNull(guiId, "guiId");
        Objects.requireNonNull(argument, "argument");
        if (guiId.isBlank() || guiId.contains(" ")) {
            throw new IllegalArgumentException("GUI-ID ohne Leerzeichen erwartet: '" + guiId + "'");
        }
    }

    /** Liest die Anfrage aus einer {@code OPEN_GUI}-Nachricht. */
    public static OpenGuiRequest of(BridgeMessage message) {
        if (message.action() != Action.OPEN_GUI) {
            throw new IllegalArgumentException("OPEN_GUI erwartet, erhalten: " + message.action());
        }
        String text = message.argument();
        int space = text.indexOf(' ');
        return space < 0
                ? new OpenGuiRequest(text, "")
                : new OpenGuiRequest(text.substring(0, space), text.substring(space + 1));
    }

    /** Die Nachricht zu dieser Anfrage. */
    public BridgeMessage toMessage() {
        return BridgeMessage.openGui(guiId, argument);
    }
}
