package net.retromc.core.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Rendert Katalogtexte mit MiniMessage zu Adventure-Komponenten (Paper und Velocity liefern Adventure zur
 * Laufzeit; core-i18n kompiliert nur dagegen).
 *
 * <p>Platzhalter sind typisiert (Spec 02 §6.13 Nr. 4): Namen und Spielertexte mit {@code Placeholder.unparsed},
 * farbige Bausteine ({@code <name>} = Name in Rangfarbe, {@code <prefix>}) mit {@code Placeholder.component},
 * Farb-Platzhalter ({@code <mode_color>}, {@code <rank_color>} …) mit {@code Placeholder.styling},
 * Auswahl-Platzhalter ({@code <online:'…':'…'>}) mit {@code Formatter.booleanChoice} und Namenslisten
 * ({@code <names:'<gray>, '>}) mit {@code Formatter.joining}.
 */
public final class MessageRenderer {

    private static final Pattern LINE_BREAK = Pattern.compile("(?<!\\\\)<(?:newline|br)>");

    private MessageRenderer() {
    }

    /** Text des Schlüssels als Komponente. */
    public static Component render(Locale locale, String key, TagResolver... placeholders) {
        return deserialize(Messages.raw(locale, key), placeholders);
    }

    /** Wie {@link #render(Locale, String, TagResolver...)} mit Ära ({@code .legacy}-Varianten, Spec 02 §1). */
    public static Component render(Locale locale, UiEra era, String key, TagResolver... placeholders) {
        return deserialize(Messages.raw(locale, key, era), placeholders);
    }

    /** Text mit Einzahl/Mehrzahl ({@code key.one} bei {@code count == 1}), z. B. {@code title.respawn}. */
    public static Component renderPlural(Locale locale, String key, long count, TagResolver... placeholders) {
        return deserialize(Messages.plural(locale, key, count), placeholders);
    }

    /**
     * Mehrzeiliger Text als einzelne Zeilen (getrennt an {@code <newline>}), z. B. für Schilder ({@code sign.top})
     * oder Kick-Bildschirme. Jede Zeile wird für sich gerendert.
     */
    public static List<Component> renderLines(Locale locale, String key, TagResolver... placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : LINE_BREAK.split(Messages.raw(locale, key), -1)) {
            lines.add(deserialize(line, placeholders));
        }
        return List.copyOf(lines);
    }

    /** MiniMessage-Text mit Platzhaltern zu einer Komponente. */
    public static Component deserialize(String miniMessage, TagResolver... placeholders) {
        return MiniMessage.miniMessage().deserialize(miniMessage, TagResolver.resolver(placeholders));
    }
}
