package net.retromc.platform.common.text;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.i18n.Language;
import net.retromc.core.i18n.MessageRenderer;
import net.retromc.core.i18n.Messages;

/**
 * Standard-{@link I18n} der Kern-Plugins: Texte aus dem Katalog von core-i18n, Sprache aus dem Profil
 * ({@link PlayerLocales}, gesetzt mit {@code /language}, Spec 02 §6.13), sonst aus der Client-Einstellung des Spielers
 * (auf die unterstützten Sprachen abgebildet, Spec 01 §6.14).
 */
public final class CatalogI18n implements I18n {

    private final Function<UUID, Optional<Locale>> clientLocale;

    /**
     * @param clientLocale Client-Sprache eines verbundenen Spielers, leer wenn unbekannt oder offline
     */
    public CatalogI18n(Function<UUID, Optional<Locale>> clientLocale) {
        this.clientLocale = Objects.requireNonNull(clientLocale, "clientLocale");
    }

    /** Profil-Sprache vor Client-Sprache. */
    public CatalogI18n(PlayerLocales profileLocales, Function<UUID, Optional<Locale>> clientLocale) {
        this(player -> profileLocales.get(player).or(() -> clientLocale.apply(player)));
    }

    @Override
    public Component render(Locale locale, String key, TagResolver... placeholders) {
        return MessageRenderer.render(locale, key, placeholders);
    }

    @Override
    public List<Component> renderLines(Locale locale, String key, TagResolver... placeholders) {
        return MessageRenderer.renderLines(locale, key, placeholders);
    }

    @Override
    public boolean has(String key) {
        return Messages.has(key);
    }

    @Override
    public Locale locale(UUID player) {
        return clientLocale.apply(player)
                .map(locale -> Language.forClientLocale(locale).locale())
                .orElse(Messages.DEFAULT_LOCALE);
    }
}
