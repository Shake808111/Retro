package net.retromc.core.data.nick;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Einstellungen des Nick-Pools (Umgebung, Spec 08 §6.15; gelesen von {@code NickPoolConfig} in platform-common).
 *
 * @param skinSources Quell-UUIDs für den Mojang-Sessionserver ({@code NETWORK_NICK_SKIN_UUIDS})
 * @param checkMojang Namen gegen die Mojang-Namens-API prüfen ({@code NETWORK_NICK_CHECK_MOJANG}, Standard an)
 * @param mojangInterval Mindestabstand der Mojang-Anfragen ({@code NETWORK_NICK_MOJANG_INTERVAL_MS}, 1100 ms)
 * @param mineSkinKey MineSkin-Schlüssel ({@code NETWORK_MINESKIN_KEY}); {@code null} = Quelle aus
 * @param mineSkinIds feste MineSkin-Skin-IDs ({@code NETWORK_NICK_MINESKIN_IDS}); leer = öffentliche Liste
 * @param mineSkinMax höchstens so viele MineSkin-Skins je Füllvorgang
 * @param blockedWords zusätzliche gesperrte Wörter ({@code NETWORK_NICK_BLOCKED_WORDS})
 */
public record NickPoolSettings(List<UUID> skinSources, boolean checkMojang, Duration mojangInterval,
        @Nullable String mineSkinKey, List<String> mineSkinIds, int mineSkinMax, Set<String> blockedWords) {

    public static final Duration DEFAULT_MOJANG_INTERVAL = Duration.ofMillis(1100);
    public static final int DEFAULT_MINESKIN_MAX = 20;
    /** Abstand der MineSkin-Anfragen. */
    public static final Duration MINESKIN_INTERVAL = Duration.ofMillis(500);

    public NickPoolSettings {
        skinSources = List.copyOf(skinSources);
        Objects.requireNonNull(mojangInterval, "mojangInterval");
        mineSkinIds = List.copyOf(mineSkinIds);
        blockedWords = Set.copyOf(blockedWords);
        if (mineSkinKey != null && mineSkinKey.isBlank()) {
            mineSkinKey = null;
        }
    }

    /** Ohne Skin-Quellen, mit Mojang-Prüfung. */
    public static NickPoolSettings defaults() {
        return new NickPoolSettings(List.of(), true, DEFAULT_MOJANG_INTERVAL, null, List.of(), DEFAULT_MINESKIN_MAX,
                Set.of());
    }

    public boolean mineSkinEnabled() {
        return mineSkinKey != null;
    }

    /** Ohne Schlüssel – für Logs und {@code toString}. */
    @Override
    public String toString() {
        return "NickPoolSettings[skinSources=" + skinSources.size() + ", checkMojang=" + checkMojang
                + ", mojangInterval=" + mojangInterval.toMillis() + "ms, mineSkin=" + mineSkinEnabled()
                + ", mineSkinIds=" + mineSkinIds.size() + ", blockedWords=" + blockedWords.size() + "]";
    }
}
