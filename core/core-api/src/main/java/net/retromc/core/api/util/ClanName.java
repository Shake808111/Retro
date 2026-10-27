package net.retromc.core.api.util;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Clan-Name: 3–16 Zeichen aus {@code [A-Za-z0-9_-]} (Spec 01 §6.6, Zeichensatz Annahme).
 * Eindeutig ohne Groß-/Kleinschreibung (Index {@code lower(name)} in V1).
 */
public final class ClanName {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;
    public static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_\\-]{3,16}");

    private ClanName() {
    }

    public static boolean valid(@Nullable String name) {
        return name != null && PATTERN.matcher(name).matches();
    }

    /** Vergleichsschlüssel für die Eindeutigkeit (wie {@code lower(name)}). */
    public static String normalized(String name) {
        return Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }
}
