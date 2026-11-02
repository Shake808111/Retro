package net.retromc.platform.common.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.platform.common.text.I18n;

/**
 * Fehlermeldung eines Befehls als i18n-Schlüssel mit Platzhaltern.
 *
 * @param key Schlüssel im Textkatalog, z. B. {@code error.command.usage}
 * @param placeholders Platzhalter (Name → Text); Werte stammen oft aus Spielereingaben und werden deshalb nie als
 *     MiniMessage gelesen
 */
public record CommandError(String key, Map<String, String> placeholders) {

    public CommandError {
        Objects.requireNonNull(key, "key");
        placeholders = Map.copyOf(placeholders);
    }

    public CommandError(String key) {
        this(key, Map.of());
    }

    /** Text über den aktiven {@link I18n}-Dienst in der Sprache {@code locale}. */
    public Component render(I18n i18n, Locale locale) {
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        return i18n.render(locale, key, resolvers);
    }
}
