package net.retromc.core.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
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
     * Mehrzeiliger Text als einzelne Zeilen (getrennt an {@code <newline>}), z. B. für Schilder ({@code sign.top}),
     * Lore oder Kick-Bildschirme.
     *
     * <p>Der Text wird erst vollständig gerendert und danach an den Zeilenumbrüchen der fertigen Komponente geteilt
     * ({@link #splitLines(Component)}). So dürfen {@code <newline>} auch in Auswahl-Tags ({@code <online:'…':'…'>}),
     * Platzhaltern und offenen Farb-Tags stehen; Formatierung gilt wie bei MiniMessage über den Umbruch hinweg.
     */
    public static List<Component> renderLines(Locale locale, String key, TagResolver... placeholders) {
        return splitLines(deserialize(Messages.raw(locale, key), placeholders));
    }

    /**
     * Teilt eine Komponente an jedem Zeilenumbruch ({@code \n} in Textinhalten, also auch
     * {@link Component#newline()}) in Zeilen. Jede Zeile trägt die vererbte Formatierung (Farbe, Dekoration,
     * Klick/Hover) ihrer Teile; nicht-textuelle Bausteine (Übersetzungen, Tastenbelegungen …) bleiben ganz.
     * {@code "a\nb\n"} ergibt drei Zeilen, die letzte leer.
     */
    public static List<Component> splitLines(Component component) {
        List<List<Component>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        collectLines(component, Style.empty(), lines);
        List<Component> out = new ArrayList<>(lines.size());
        for (List<Component> parts : lines) {
            if (parts.isEmpty()) {
                out.add(Component.empty());
            } else if (parts.size() == 1) {
                out.add(parts.getFirst());
            } else {
                out.add(Component.textOfChildren(parts.toArray(ComponentLike[]::new)).compact());
            }
        }
        return List.copyOf(out);
    }

    private static void collectLines(Component node, Style inherited, List<List<Component>> lines) {
        Style style = node.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (node instanceof TextComponent text) {
            String content = text.content();
            int start = 0;
            int lineBreak;
            while ((lineBreak = content.indexOf('\n', start)) >= 0) {
                addText(lines, content.substring(start, lineBreak), style);
                lines.add(new ArrayList<>());
                start = lineBreak + 1;
            }
            addText(lines, content.substring(start), style);
        } else {
            lines.getLast().add(node.children(List.of()).style(style));
        }
        for (Component child : node.children()) {
            collectLines(child, style, lines);
        }
    }

    private static void addText(List<List<Component>> lines, String content, Style style) {
        if (!content.isEmpty()) {
            lines.getLast().add(Component.text(content, style));
        }
    }

    /** MiniMessage-Text mit Platzhaltern zu einer Komponente. */
    public static Component deserialize(String miniMessage, TagResolver... placeholders) {
        return MiniMessage.miniMessage().deserialize(miniMessage, TagResolver.resolver(placeholders));
    }
}
