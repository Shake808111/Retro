package net.retromc.platform.common.social;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.retromc.core.data.nick.NickPoolSettings;
import net.retromc.platform.common.config.EnvKeys;
import net.retromc.platform.common.config.EnvReader;

/**
 * Liest die Nick-Pool-Einstellungen aus der Umgebung (Spec 01 §6.11, Spec 08 §6.15). Ungültige Werte landen in
 * {@link EnvReader#problems()}, der Pool läuft dann mit dem Rest weiter. Der MineSkin-Schlüssel kommt nur aus
 * {@link EnvKeys#MINESKIN_KEY} und erscheint in keinem Log ({@link NickPoolSettings#toString()}).
 */
public final class NickPoolConfig {

    private static final String UUID_PART = "[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?"
            + "[0-9a-fA-F]{12}";
    private static final Pattern UUID_LIST = Pattern.compile(UUID_PART + "(?:[,\\s]+" + UUID_PART + ")*,?");
    private static final Pattern BOOL = Pattern.compile("(?i)true|false|1|0|on|off|an|aus");
    private static final Pattern ID_LIST = Pattern.compile("[A-Za-z0-9_-]{1,64}(?:[,\\s]+[A-Za-z0-9_-]{1,64})*,?");
    private static final Pattern SPLIT = Pattern.compile("[,\\s]+");

    private NickPoolConfig() {
    }

    public static NickPoolSettings read(EnvReader env) {
        List<UUID> sources = new ArrayList<>();
        env.optional(EnvKeys.NICK_SKIN_UUIDS, UUID_LIST, "UUIDs, getrennt durch Komma")
                .ifPresent(list -> split(list).forEach(value -> sources.add(uuid(value))));
        boolean check = env.optional(EnvKeys.NICK_CHECK_MOJANG, BOOL, "true oder false")
                .map(value -> Set.of("true", "1", "on", "an").contains(value.toLowerCase(Locale.ROOT)))
                .orElse(true);
        int interval = env.integer(EnvKeys.NICK_MOJANG_INTERVAL_MS,
                (int) NickPoolSettings.DEFAULT_MOJANG_INTERVAL.toMillis(), 200, 60_000);
        String key = env.optional(EnvKeys.MINESKIN_KEY).orElse(null);
        List<String> ids = env.optional(EnvKeys.NICK_MINESKIN_IDS, ID_LIST, "MineSkin-IDs, getrennt durch Komma")
                .map(NickPoolConfig::split).orElse(List.of());
        Set<String> words = new LinkedHashSet<>();
        env.optional(EnvKeys.NICK_BLOCKED_WORDS).ifPresent(list -> Arrays.stream(list.split(","))
                .map(word -> word.strip().toLowerCase(Locale.ROOT)).filter(word -> !word.isEmpty())
                .forEach(words::add));
        return new NickPoolSettings(List.copyOf(new LinkedHashSet<>(sources)), check, Duration.ofMillis(interval),
                key, ids, NickPoolSettings.DEFAULT_MINESKIN_MAX, words);
    }

    private static List<String> split(String list) {
        return Arrays.stream(SPLIT.split(list.strip())).filter(part -> !part.isEmpty()).toList();
    }

    /** UUID mit oder ohne Bindestriche. */
    static UUID uuid(String value) {
        String hex = value.replace("-", "");
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20));
    }
}
