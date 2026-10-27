package net.retromc.core.messaging.payload;

import java.util.Map;
import java.util.Objects;

/** Zeile einer Controller-Antwort: i18n-Schlüssel und Platzhalter (Spec 08 §8.3 {@code lines[]}). */
public record CtlLine(String key, Map<String, String> args) {

    public CtlLine {
        Objects.requireNonNull(key, "key");
        args = Map.copyOf(args);
    }

    public static CtlLine of(String key) {
        return new CtlLine(key, Map.of());
    }
}
