package net.retromc.core.messaging;

/** Eine empfangene Nachricht ist kein gültiger Envelope oder die Nutzlast passt nicht zum Typ. */
public class MessageFormatException extends RuntimeException {

    public MessageFormatException(String message) {
        super(message);
    }

    public MessageFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
