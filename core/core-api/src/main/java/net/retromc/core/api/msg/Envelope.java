package net.retromc.core.api.msg;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Hülle jeder Netzwerk-Nachricht (Spec 08 §6.12 Nr. 1):
 * {@code {"t":"SERVER_STATE","v":1,"id":"<uuid>","ts":<epochMillis>,"src":"<server|proxy-id|controller>",
 * "re":"<id>?","d":{…}}}. {@code re} steht nur in Antworten. Kodierung und Typ-Registry liegen in core-messaging.
 *
 * @param t Typ in GROSS_MIT_UNTERSTRICH (Spec 00 §4.2)
 * @param v Version des Typs (höhere Versionen ignoriert der Empfänger)
 * @param id eindeutige Nachrichten-ID
 * @param ts Sendezeit in Epoch-Millisekunden
 * @param src Absender (Servername, Proxy-ID oder {@code controller})
 * @param re ID der beantworteten Anfrage oder {@code null}
 * @param d Nutzlast
 */
public record Envelope<P>(String t, int v, UUID id, long ts, String src, @Nullable UUID re, P d) {

    /** Muster für Typnamen. */
    public static final Pattern TYPE = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*");

    public Envelope {
        Objects.requireNonNull(t, "t");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(d, "d");
        if (!TYPE.matcher(t).matches()) {
            throw new IllegalArgumentException("Typ muss GROSS_MIT_UNTERSTRICH sein: " + t);
        }
        if (v < 1) {
            throw new IllegalArgumentException("Version muss ≥ 1 sein: " + v);
        }
        if (src.isBlank()) {
            throw new IllegalArgumentException("src darf nicht leer sein");
        }
    }

    /** Neue Nachricht mit zufälliger ID. */
    public static <P> Envelope<P> create(String type, int version, String source, P payload, Clock clock) {
        return new Envelope<>(type, version, UUID.randomUUID(), clock.millis(), source, null, payload);
    }

    /** Antwort auf diese Nachricht ({@code re} = {@code id} dieser Nachricht). */
    public <R> Envelope<R> reply(String type, int version, String source, R payload, Clock clock) {
        return new Envelope<>(type, version, UUID.randomUUID(), clock.millis(), source, id, payload);
    }

    public boolean isReply() {
        return re != null;
    }
}
