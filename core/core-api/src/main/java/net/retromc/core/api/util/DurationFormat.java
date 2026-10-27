package net.retromc.core.api.util;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restzeit-Format der Strafen (Spec 01 §6.10): absteigend ab der größten Einheit ungleich 0 bis zu den Sekunden,
 * z. B. {@code 29d 13h 42m 34s}, ohne Tage {@code 13h 42m 34s}, nur Sekunden {@code 45s}.
 *
 * <p>{@link #parse(String)} liest Dauer-Angaben wie {@code 30d}, {@code 12h}, {@code 1d12h} oder
 * {@code 29d 13h 42m 34s} (Befehl {@code /ban <Spieler> <Grund> [Dauer]}, Spec 01 §3.6 #77).
 */
public final class DurationFormat {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    /** Teile in fester Reihenfolge d, h, m, s; jede Einheit höchstens einmal, Leerzeichen dazwischen erlaubt. */
    private static final Pattern INPUT = Pattern.compile(
            "(?:(\\d{1,9})d)?\\s*(?:(\\d{1,9})h)?\\s*(?:(\\d{1,9})m)?\\s*(?:(\\d{1,9})s)?");

    private DurationFormat() {
    }

    /** Formatiert eine Restzeit; negative Werte gelten als 0, Sekundenbruchteile werden abgeschnitten. */
    public static String format(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long total = Math.max(0, duration.getSeconds());
        long days = total / DAY;
        long hours = total % DAY / HOUR;
        long minutes = total % HOUR / MINUTE;
        long seconds = total % MINUTE;
        StringBuilder out = new StringBuilder();
        if (days > 0) {
            out.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            out.append(hours).append("h ");
        }
        if (days > 0 || hours > 0 || minutes > 0) {
            out.append(minutes).append("m ");
        }
        return out.append(seconds).append('s').toString();
    }

    /**
     * Liest eine Dauer (Groß-/Kleinschreibung egal). Leere Eingaben, unbekannte Einheiten, falsche Reihenfolge
     * oder Überlauf ergeben {@link Optional#empty()}.
     */
    public static Optional<Duration> parse(String text) {
        Objects.requireNonNull(text, "text");
        String input = text.strip().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = INPUT.matcher(input);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long[] factors = {DAY, HOUR, MINUTE, 1};
        long total = 0;
        boolean any = false;
        try {
            for (int group = 1; group <= 4; group++) {
                String part = matcher.group(group);
                if (part != null) {
                    any = true;
                    total = Math.addExact(total, Math.multiplyExact(Long.parseLong(part), factors[group - 1]));
                }
            }
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        return any ? Optional.of(Duration.ofSeconds(total)) : Optional.empty();
    }
}
