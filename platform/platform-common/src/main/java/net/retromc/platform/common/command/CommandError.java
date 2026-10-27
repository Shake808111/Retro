package net.retromc.platform.common.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.i18n.MessageRenderer;

/**
 * Fehlermeldung eines Befehls als i18n-Schlüssel mit Platzhaltern (core-i18n).
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

    /** Text in der Sprache {@code locale}. */
    public Component render(Locale locale) {
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
                .toArray(TagResolver[]::new);
        return MessageRenderer.render(locale, key, resolvers);
    }
}
