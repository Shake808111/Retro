package net.retromc.platform.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Liest Umgebungsvariablen und sammelt alle Probleme, statt beim ersten abzubrechen. Ablauf: alle Werte lesen, dann
 * {@link #check()} (wirft {@link ConfigException} mit allen Problemen), erst danach Konfigurations-Records bauen.
 * Leere Werte gelten als nicht gesetzt.
 */
public final class EnvReader {

    private final Map<String, String> env;
    private final List<String> problems = new ArrayList<>();

    public EnvReader(Map<String, String> env) {
        this.env = Map.copyOf(Objects.requireNonNull(env, "env"));
    }

    /** Die Umgebung des Prozesses. */
    public static EnvReader system() {
        return new EnvReader(System.getenv());
    }

    /** Pflichtwert; fehlt er, wird ein Problem vermerkt und {@code ""} geliefert. */
    public String require(String key) {
        Optional<String> value = optional(key);
        if (value.isEmpty()) {
            problems.add(key + " fehlt");
        }
        return value.orElse("");
    }

    /** Pflichtwert, der {@code pattern} vollständig erfüllen muss ({@code hint} beschreibt das Format). */
    public String require(String key, Pattern pattern, String hint) {
        String value = require(key);
        if (!value.isEmpty()) {
            validate(key, value, pattern, hint);
        }
        return value;
    }

    /** Optionaler Wert ohne Prüfung. */
    public Optional<String> optional(String key) {
        String value = env.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    /** Optionaler Wert, der {@code pattern} vollständig erfüllen muss. */
    public Optional<String> optional(String key, Pattern pattern, String hint) {
        Optional<String> value = optional(key);
        return value.filter(present -> validate(key, present, pattern, hint));
    }

    /** Ganze Zahl in {@code [min, max]}; fehlt sie, gilt {@code fallback}. */
    public int integer(String key, int fallback, int min, int max) {
        Optional<String> value = optional(key);
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.get());
            if (parsed >= min && parsed <= max) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // unten als Problem vermerkt
        }
        problems.add(key + " '" + value.get() + "' ist keine Zahl von " + min + " bis " + max);
        return fallback;
    }

    /** Aufzählungswert, Groß-/Kleinschreibung egal; fehlt er, ist das Ergebnis leer. */
    public <E extends Enum<E>> Optional<E> choice(String key, Class<E> type) {
        Optional<String> value = optional(key);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        String wanted = value.get().toUpperCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(wanted)) {
                return Optional.of(constant);
            }
        }
        List<String> allowed = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            allowed.add(constant.name().toLowerCase(Locale.ROOT));
        }
        problems.add(key + " '" + value.get() + "' ist ungültig (erlaubt: " + String.join("|", allowed) + ")");
        return Optional.empty();
    }

    /** Bisher vermerkte Probleme. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /**
     * Wirft eine {@link ConfigException} mit allen bisher vermerkten Problemen.
     *
     * @throws ConfigException wenn es Probleme gibt
     */
    public void check() {
        if (!problems.isEmpty()) {
            throw new ConfigException(problems);
        }
    }

    private boolean validate(String key, String value, Pattern pattern, String hint) {
        if (pattern.matcher(value).matches()) {
            return true;
        }
        problems.add(key + " '" + value + "' ist ungültig (" + hint + ")");
        return false;
    }
}
