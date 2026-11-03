package net.retromc.game.framework;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.retromc.game.framework.phase.CountdownPlan;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Einstellungen des Rahmens aus {@code config.yml}, Abschnitt {@code game} (Spec 03 §6.5; Werte ohne Beleg sind
 * Annahmen und hier änderbar).
 *
 * @param defaultVariant  Variante, wenn das Template keine nennt ({@code bedwars-4x4} → {@code 4x4})
 * @param variants        bekannte Varianten
 * @param countdown       Lobby-Countdown in Sekunden (60)
 * @param tipInterval     Tipp-Abstand in Sekunden (30)
 * @param replay          Replay-Hinweis am Rundenende ({@code common.replay}); braucht einen Replay-Dienst
 * @param countdownTitles Countdown-Title {@code §e{n}} bei 3/2/1 (Standard aus)
 * @param fixedMap        feste Map statt Zufallsauswahl oder {@code null}
 */
public record GameSettings(String defaultVariant, Map<String, GameVariant> variants, int countdown, int tipInterval,
        boolean replay, boolean countdownTitles, @Nullable String fixedMap) {

    public GameSettings {
        Objects.requireNonNull(defaultVariant, "defaultVariant");
        variants = Map.copyOf(variants);
        if (countdown < 1 || tipInterval < 1) {
            throw new IllegalArgumentException("countdown und tip-interval müssen ≥ 1 sein");
        }
    }

    /** Liest {@code game.*}; fehlende Werte nehmen die Standards des Rahmens. */
    public static GameSettings read(@Nullable ConfigurationSection section, String fallbackVariant) {
        if (section == null) {
            return new GameSettings(fallbackVariant, Map.of(fallbackVariant, GameVariant.parse(fallbackVariant)),
                    CountdownPlan.DEFAULT_TOTAL, 30, false, false, null);
        }
        Map<String, GameVariant> variants = new LinkedHashMap<>();
        ConfigurationSection list = section.getConfigurationSection("variants");
        if (list != null) {
            for (String key : list.getKeys(false)) {
                GameVariant parsed = GameVariant.parse(key);
                ConfigurationSection values = list.getConfigurationSection(key);
                int min = values == null ? 0 : values.getInt("min", 0);
                int privateMax = values == null ? 0 : values.getInt("private-max", 0);
                variants.put(key, new GameVariant(key, parsed.teams(), parsed.teamSize(), min, privateMax));
            }
        }
        String variant = section.getString("variant", fallbackVariant);
        variants.computeIfAbsent(variant, GameVariant::parse);
        String map = section.getString("map", "");
        return new GameSettings(variant, variants, section.getInt("countdown", CountdownPlan.DEFAULT_TOTAL),
                section.getInt("tip-interval", 30), section.getBoolean("replay", false),
                section.getBoolean("countdown-titles", false), map == null || map.isBlank() ? null : map);
    }

    /** Variante zu einem Template-Rest ({@code 4x4}) oder die Standardvariante. */
    public GameVariant variant(@Nullable String fromTemplate) {
        if (fromTemplate != null) {
            GameVariant known = variants.get(fromTemplate);
            if (known != null) {
                return known;
            }
            try {
                return GameVariant.parse(fromTemplate);
            } catch (IllegalArgumentException e) {
                // Template ohne Variante (z. B. "default"): Standard
            }
        }
        return Optional.ofNullable(variants.get(defaultVariant)).orElseGet(() -> GameVariant.parse(defaultVariant));
    }
}
