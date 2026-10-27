package net.retromc.core.api.util;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Clan-Tag: 2–5 Zeichen aus {@code [A-Za-z0-9_$%.,+\-#üÜäÄöÖ]} (Spec 01 §6.6, belegt ab 2020).
 * Eindeutig ohne Groß-/Kleinschreibung (Index {@code lower(tag)} in V1).
 */
public final class ClanTag {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 5;

    /** {@code \w} entspricht ohne UNICODE_CHARACTER_CLASS genau {@code [A-Za-z0-9_]}. */
    public static final Pattern PATTERN = Pattern.compile("[\\w$%.,+\\-#üÜäÄöÖ]{2,5}");

    private ClanTag() {
    }

    public static boolean valid(@Nullable String tag) {
        return tag != null && PATTERN.matcher(tag).matches();
    }

    /** Vergleichsschlüssel für die Eindeutigkeit (wie {@code lower(tag)}). */
    public static String normalized(String tag) {
        return Objects.requireNonNull(tag, "tag").toLowerCase(Locale.ROOT);
    }
}
