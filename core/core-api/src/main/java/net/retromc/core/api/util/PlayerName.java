package net.retromc.core.api.util;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Minecraft-Spielername: 3–16 Zeichen aus {@code [A-Za-z0-9_]} (Java-Edition; Spalte {@code core.player.name}
 * {@code VARCHAR(16)}, ebenso {@code core.nick_pool.name}). Suche über {@code name_lower} bzw. {@code name:{lower}}.
 */
public final class PlayerName {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 16;
    public static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private PlayerName() {
    }

    public static boolean valid(@Nullable String name) {
        return name != null && PATTERN.matcher(name).matches();
    }

    /** Kleinschreibung wie {@code core.player.name_lower} und der Valkey-Schlüssel {@code name:{lower}}. */
    public static String normalized(String name) {
        return Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }
}
