package net.retromc.platform.common.text;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Spielertexte und Sprache je Spieler (Spec 02 §8.1 {@code I18n}). Standard ist {@link CatalogI18n} mit dem
 * Katalog aus core-i18n; ein Adapter an ein fremdes Kern-Plugin kann eigene Texte oder eigene Sprachwahl über
 * {@code ServiceRegistry.register(I18n.class, …)} einsetzen. Alle Kern-Features holen Texte nur hierüber.
 */
public interface I18n {

    /** Text zu {@code key} in der Sprache {@code locale} (Rückfall: Deutsch). */
    Component render(Locale locale, String key, TagResolver... placeholders);

    /** Mehrzeiliger Text ({@code <newline>} trennt), z. B. für Lore oder Schilder. */
    List<Component> renderLines(Locale locale, String key, TagResolver... placeholders);

    /** {@code true}, wenn es den Schlüssel gibt. */
    boolean has(String key);

    /** Sprache des Spielers (Profil oder Client-Einstellung). */
    Locale locale(UUID player);
}
