package net.retromc.core.messaging;

/** Nachricht über {@link EnvelopeCodec#MAX_BYTES}; wird beim Senden abgelehnt (Spec 08 §6.12 Nr. 2). */
public class MessageTooLargeException extends IllegalArgumentException {

    private final int bytes;

    public MessageTooLargeException(String type, int bytes) {
        super("Nachricht " + type + " ist " + bytes + " Byte groß (höchstens " + EnvelopeCodec.MAX_BYTES + ")");
        this.bytes = bytes;
    }

    public int bytes() {
        return bytes;
    }
}
