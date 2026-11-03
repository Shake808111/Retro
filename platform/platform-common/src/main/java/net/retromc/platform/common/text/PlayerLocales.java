package net.retromc.platform.common.text;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.retromc.core.i18n.Language;

/**
 * Gewählte Sprache verbundener Spieler aus {@code core.player.locale} (Spec 02 §6.13): Nach dem ersten Join gilt das
 * Profil, nicht mehr die Client-Sprache. Die Kern-Plugins füllen die Tabelle beim Laden des Profils und bei
 * {@code PROFILE_CHANGED} mit Feld {@code locale}; {@link CatalogI18n} liest sie vor der Client-Sprache.
 */
public final class PlayerLocales {

    private final Map<UUID, Locale> locales = new ConcurrentHashMap<>();

    /** Sprache aus dem Profil; leer, wenn unbekannt. */
    public Optional<Locale> get(UUID player) {
        return Optional.ofNullable(locales.get(player));
    }

    /**
     * Merkt die Profil-Sprache ({@code de_DE}, {@code en_US}, {@code bar}, {@code gsw}).
     *
     * @return {@code true}, wenn sich die Sprache geändert hat
     */
    public boolean put(UUID player, String code) {
        Locale locale = Language.byCode(code).orElse(Language.BASE).locale();
        return !locale.equals(locales.put(player, locale));
    }

    public void remove(UUID player) {
        locales.remove(player);
    }
}
