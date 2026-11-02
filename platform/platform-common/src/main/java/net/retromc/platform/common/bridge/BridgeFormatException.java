package net.retromc.platform.common.bridge;

import java.io.Serial;

/** Ungültige Bridge-Nachricht (Format, Größe, Richtung). */
public final class BridgeFormatException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BridgeFormatException(String message) {
        super(message);
    }

    public BridgeFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
