package net.retromc.core.messaging;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.msg.JsonName;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;

/**
 * JSON-Codec des Envelopes nach Spec 08 §6.12:
 * {@code {"t":"<TYP>","v":1,"id":"<uuid>","ts":<epochMillis>,"src":"<quelle>","re":"<id>?","d":{…}}}.
 *
 * <ul>
 *   <li>höchstens {@link #MAX_BYTES} (64 KiB) je Nachricht, größere werden beim Senden abgelehnt (Nr. 2)</li>
 *   <li>unbekannte {@code t} und höhere {@code v} werden ignoriert und auf DEBUG geloggt (Nr. 3)</li>
 *   <li>Zeitpunkte als Epoch-Millisekunden, {@code ModeKey} und {@code StatPeriod} als Text</li>
 * </ul>
 * Thread-sicher.
 */
public final class EnvelopeCodec {

    /** Höchstgröße einer kodierten Nachricht in Byte (UTF-8). */
    public static final int MAX_BYTES = 64 * 1024;

    private static final Logger LOG = System.getLogger(EnvelopeCodec.class.getName());

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .setFieldNamingStrategy(EnvelopeCodec::fieldName)
            .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
            .registerTypeAdapter(ModeKey.class, new ModeKeyAdapter().nullSafe())
            .registerTypeAdapter(StatPeriod.class, new StatPeriodAdapter().nullSafe())
            .create();

    /** Kodiert einen Envelope; der Typ muss registriert sein und die Nutzlast passen. */
    public String encode(Envelope<?> envelope) {
        MessageType<?> type = MessageType.byName(envelope.t())
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Nachrichtentyp: " + envelope.t()));
        if (!type.payloadType().isInstance(envelope.d())) {
            throw new IllegalArgumentException("Nutzlast " + envelope.d().getClass().getName() + " passt nicht zu "
                    + type.name());
        }
        JsonObject root = new JsonObject();
        root.addProperty("t", envelope.t());
        root.addProperty("v", envelope.v());
        root.addProperty("id", envelope.id().toString());
        root.addProperty("ts", envelope.ts());
        root.addProperty("src", envelope.src());
        if (envelope.re() != null) {
            root.addProperty("re", envelope.re().toString());
        }
        root.add("d", gson.toJsonTree(envelope.d()));
        String json = gson.toJson(root);
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) {
            throw new MessageTooLargeException(envelope.t(), bytes);
        }
        return json;
    }

    /**
     * Dekodiert eine Nachricht.
     *
     * @return leer bei unbekanntem Typ oder höherer Version (vorwärtskompatibel)
     * @throws MessageFormatException bei kaputtem JSON oder unpassender Nutzlast
     */
    public Optional<Envelope<?>> decode(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new MessageFormatException("Envelope ist kein JSON-Objekt");
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new MessageFormatException("Kein gültiges JSON", e);
        }
        String t = text(root, "t");
        int v = number(root, "v").intValue();
        Optional<MessageType<?>> type = MessageType.byName(t);
        if (type.isEmpty()) {
            LOG.log(Level.DEBUG, "Unbekannter Nachrichtentyp {0} ignoriert", t);
            return Optional.empty();
        }
        if (v > type.get().version()) {
            LOG.log(Level.DEBUG, "Nachricht {0} in Version {1} ignoriert (bekannt: {2})", t, v,
                    type.get().version());
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(text(root, "id"));
            long ts = number(root, "ts").longValue();
            String src = text(root, "src");
            UUID re = root.has("re") && !root.get("re").isJsonNull() ? UUID.fromString(text(root, "re")) : null;
            JsonElement data = root.get("d");
            if (data == null || !data.isJsonObject()) {
                throw new MessageFormatException("Feld d fehlt in " + t);
            }
            Object payload = gson.fromJson(data, type.get().payloadType());
            if (payload == null) {
                throw new MessageFormatException("Leere Nutzlast in " + t);
            }
            return Optional.of(new Envelope<>(t, v, id, ts, src, re, payload));
        } catch (MessageFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MessageFormatException("Ungültige Nachricht " + t + ": " + e.getMessage(), e);
        }
    }

    /** Dekodiert und typisiert; leer, wenn die Nachricht von anderem Typ, unbekannt oder neuer ist. */
    public <P> Optional<Envelope<P>> decode(String json, MessageType<P> expected) {
        return decode(json).filter(envelope -> envelope.t().equals(expected.name())).map(expected::typed);
    }

    private static String text(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new MessageFormatException("Feld " + field + " fehlt oder ist kein Text");
        }
        return element.getAsString();
    }

    private static Number number(JsonObject root, String field) {
        JsonElement element = root.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new MessageFormatException("Feld " + field + " fehlt oder ist keine Zahl");
        }
        return element.getAsNumber();
    }

    /** Feldname: {@link JsonName}, sonst der Java-Name. */
    private static String fieldName(Field field) {
        JsonName name = field.getAnnotation(JsonName.class);
        return name != null ? name.value() : FieldNamingPolicy.IDENTITY.translateName(field);
    }

    /** {@link Instant} als Epoch-Millisekunden. */
    private static final class InstantAdapter extends TypeAdapter<Instant> {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            out.value(value.toEpochMilli());
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            return Instant.ofEpochMilli(in.nextLong());
        }
    }

    /** {@link ModeKey} als Text. */
    private static final class ModeKeyAdapter extends TypeAdapter<ModeKey> {

        @Override
        public void write(JsonWriter out, ModeKey value) throws IOException {
            out.value(value.key());
        }

        @Override
        public ModeKey read(JsonReader in) throws IOException {
            return ModeKey.of(in.nextString());
        }
    }

    /** {@link StatPeriod} als Schlüssel {@code 30d}/{@code all}. */
    private static final class StatPeriodAdapter extends TypeAdapter<StatPeriod> {

        @Override
        public void write(JsonWriter out, StatPeriod value) throws IOException {
            out.value(value.key());
        }

        @Override
        public StatPeriod read(JsonReader in) throws IOException {
            String key = in.nextString();
            return StatPeriod.byKey(key).orElseThrow(() -> new JsonParseException("Unbekannter Zeitraum: " + key));
        }
    }
}
