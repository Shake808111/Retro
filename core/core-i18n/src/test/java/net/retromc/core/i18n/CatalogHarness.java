package net.retromc.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Prüfhilfe für den Katalog: rendert Katalogtexte so, dass jeder Platzhalter als Markierung {@code {name}} sichtbar
 * bleibt, und zerlegt Komponenten in Zeichen mit Farbe und Formatierung (Vergleich mit der Spec-Notation).
 */
final class CatalogHarness {

    /** Soll-Dateien je Spec: §7-Zeilen und Ergänzungen. */
    static final List<String> SPEC_FILES = List.of(
            "spec01", "spec01-ergaenzungen", "spec02", "spec02-ergaenzungen", "spec08", "spec08-ergaenzungen");

    private static final Pattern MARKER = Pattern.compile("\\{([a-z0-9_]+(?::[a-z0-9_-]+)?)}");
    private static final TagResolver STANDARD = StandardTags.defaults();

    private CatalogHarness() {
    }

    /** Ein Zeichen mit wirksamer Farbe und Formatierung. */
    record StyledChar(int codePoint, TextColor color, boolean bold, boolean italic, boolean underlined,
            boolean strikethrough, boolean obfuscated) {

        boolean whitespace() {
            return Character.isWhitespace(codePoint);
        }

        String styleKey() {
            return (color == null ? "none" : color.asHexString()) + (bold ? "+b" : "") + (italic ? "+i" : "")
                    + (underlined ? "+u" : "") + (strikethrough ? "+s" : "") + (obfuscated ? "+k" : "");
        }
    }

    /** Ein zusammenhängender Abschnitt gleicher Formatierung (Leerraum zählt nicht) mit seinen Platzhaltern. */
    record Run(String style, List<String> placeholders) {
    }

    /** Lädt eine Soll-Datei aus {@code src/test/resources/spec}. Schlüssel mit {@code #var} sind Auswahl-Fälle. */
    static Map<String, String> specFile(String name) {
        return properties("spec/" + name + ".properties");
    }

    /** Alle Soll-Einträge aller Spec-Dateien (Schlüssel inklusive {@code #var}-Endung). */
    static Map<String, String> allSpecEntries() {
        Map<String, String> all = new TreeMap<>();
        for (String file : SPEC_FILES) {
            all.putAll(specFile(file));
        }
        return all;
    }

    /** Katalog-Schlüssel einer Soll-Datei (ohne {@code #var}-Endung). */
    static List<String> specKeys(String file) {
        return specFile(file).keySet().stream().map(CatalogHarness::baseKey).distinct().sorted().toList();
    }

    static String baseKey(String specKey) {
        int hash = specKey.indexOf('#');
        return hash < 0 ? specKey : specKey.substring(0, hash);
    }

    /** Auswahl-Werte aus einem Soll-Schlüssel: {@code key#online} → online=true, {@code key#!online} → false. */
    static Map<String, Boolean> choices(String specKey) {
        int hash = specKey.indexOf('#');
        if (hash < 0) {
            return Map.of();
        }
        String variant = specKey.substring(hash + 1);
        boolean value = !variant.startsWith("!");
        return Map.of(value ? variant : variant.substring(1), value);
    }

    /** Alle Auswahl-Fälle eines Katalog-Schlüssels laut Soll-Dateien (mindestens der leere Fall). */
    static List<Map<String, Boolean>> choiceCases(String key) {
        List<Map<String, Boolean>> cases = new ArrayList<>();
        for (String specKey : allSpecEntries().keySet()) {
            if (baseKey(specKey).equals(key) && specKey.contains("#")) {
                cases.add(choices(specKey));
            }
        }
        if (cases.isEmpty()) {
            cases.add(Map.of());
        }
        return cases;
    }

    /** Rendert einen Katalogtext; Platzhalter erscheinen als {@code {name}}, Präfix-Verweise als {@code {p:x}}. */
    static Component render(String miniMessage, Map<String, Boolean> choices) {
        TagResolver markers = new TagResolver() {
            @Override
            public Tag resolve(String name, ArgumentQueue arguments, Context ctx) throws ParsingException {
                if (name.equals("p") && arguments.hasNext()) {
                    return Tag.selfClosingInserting(Component.text("{p:" + arguments.pop().value() + "}"));
                }
                if (choices.containsKey(name)) {
                    return Formatter.booleanChoice(name, choices.get(name)).resolve(name, arguments, ctx);
                }
                if (arguments.hasNext()) {
                    TagResolver joining = Formatter.joining(name,
                            Component.text("{" + name + "1}"), Component.text("{" + name + "2}"));
                    return joining.resolve(name, arguments, ctx);
                }
                return Tag.selfClosingInserting(Component.text("{" + name + "}"));
            }

            @Override
            public boolean has(String name) {
                return !STANDARD.has(name);
            }
        };
        return MiniMessage.miniMessage().deserialize(miniMessage, markers);
    }

    /** Liest Spec-Notation (§-Codes) als Komponente. */
    static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Zerlegt eine Komponente in Zeichen mit vererbter Formatierung. */
    static List<StyledChar> flatten(Component component) {
        List<StyledChar> out = new ArrayList<>();
        flatten(component, Style.empty(), out);
        return out;
    }

    private static void flatten(Component component, Style parent, List<StyledChar> out) {
        Style style = parent.merge(component.style());
        if (component instanceof TextComponent text) {
            text.content().codePoints().forEach(cp -> out.add(new StyledChar(cp, style.color(),
                    on(style, TextDecoration.BOLD), on(style, TextDecoration.ITALIC),
                    on(style, TextDecoration.UNDERLINED), on(style, TextDecoration.STRIKETHROUGH),
                    on(style, TextDecoration.OBFUSCATED))));
        }
        for (Component child : component.children()) {
            flatten(child, style, out);
        }
    }

    private static boolean on(Style style, TextDecoration decoration) {
        return style.decoration(decoration) == TextDecoration.State.TRUE;
    }

    /**
     * Unterschiede zweier Zeichenfolgen: Text muss gleich sein; sichtbare Zeichen gleiche Farbe und Formatierung,
     * Leerzeichen gleiches Fett/Durchgestrichen/Unterstrichen, Zeilenumbrüche nur als Zeichen. Leer = gleich.
     */
    static String difference(List<StyledChar> expected, List<StyledChar> actual) {
        String expectedText = text(expected);
        String actualText = text(actual);
        if (!expectedText.equals(actualText)) {
            return "Text: erwartet <" + expectedText + "> aber <" + actualText + ">";
        }
        for (int i = 0; i < expected.size(); i++) {
            StyledChar e = expected.get(i);
            StyledChar a = actual.get(i);
            boolean same;
            if (e.codePoint() == '\n') {
                same = true;
            } else if (e.whitespace()) {
                same = e.bold() == a.bold() && e.strikethrough() == a.strikethrough()
                        && e.underlined() == a.underlined();
            } else {
                same = e.styleKey().equals(a.styleKey());
            }
            if (!same) {
                return "Format an Stelle " + i + " ('" + Character.toString(e.codePoint()) + "' in <"
                        + expectedText + ">): erwartet " + e.styleKey() + " aber " + a.styleKey();
            }
        }
        return "";
    }

    /** Farbstruktur: Abschnitte gleicher Formatierung mit ihren Platzhaltern; Leerraum ist durchsichtig. */
    static List<Run> structure(List<StyledChar> chars) {
        String text = text(chars);
        Map<Integer, String> markerStarts = new LinkedHashMap<>();
        Matcher matcher = MARKER.matcher(text);
        while (matcher.find()) {
            markerStarts.put(codePointIndex(text, matcher.start()), matcher.group(1));
        }
        List<Run> runs = new ArrayList<>();
        String currentStyle = null;
        List<String> currentPlaceholders = new ArrayList<>();
        for (int i = 0; i < chars.size(); i++) {
            StyledChar c = chars.get(i);
            if (c.whitespace()) {
                continue;
            }
            if (!c.styleKey().equals(currentStyle)) {
                if (currentStyle != null) {
                    runs.add(new Run(currentStyle, sorted(currentPlaceholders)));
                }
                currentStyle = c.styleKey();
                currentPlaceholders = new ArrayList<>();
            }
            String marker = markerStarts.get(i);
            if (marker != null) {
                currentPlaceholders.add(marker);
            }
        }
        if (currentStyle != null) {
            runs.add(new Run(currentStyle, sorted(currentPlaceholders)));
        }
        return runs;
    }

    /** Platzhalter-Namen eines gerenderten Textes in Reihenfolge des Auftretens. */
    static List<String> markers(Component component) {
        List<String> names = new ArrayList<>();
        Matcher matcher = MARKER.matcher(plain(component));
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    static String text(List<StyledChar> chars) {
        StringBuilder sb = new StringBuilder();
        chars.forEach(c -> sb.appendCodePoint(c.codePoint()));
        return sb.toString();
    }

    private static int codePointIndex(String text, int charIndex) {
        return text.codePointCount(0, charIndex);
    }

    private static List<String> sorted(List<String> values) {
        return values.stream().sorted().toList();
    }

    static Map<String, String> properties(String resource) {
        try (InputStream in = Objects.requireNonNull(
                CatalogHarness.class.getClassLoader().getResourceAsStream(resource), resource)) {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> result = new TreeMap<>();
            properties.stringPropertyNames().forEach(key -> result.put(key, properties.getProperty(key)));
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Roher Dateiinhalt (inklusive Kommentaren) einer Klassenpfad-Ressource. */
    static String fileContent(String resource) {
        try (InputStream in = Objects.requireNonNull(
                CatalogHarness.class.getClassLoader().getResourceAsStream(resource), resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
