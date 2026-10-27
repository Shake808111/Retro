package net.retromc.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import net.retromc.core.api.BuildInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

/**
 * Katalogtest nach Spec 00 §4.5: jeder Key in de_DE und en_US, keine offenen {-Platzhalter, jeder Text mit
 * MiniMessage lesbar, keine hart codierte oder fremde Marke. Dazu: de_DE entspricht Zeichen für Zeichen der
 * Spec-Notation (src/test/resources/spec), Übersetzungen haben dieselbe Farbstruktur wie de_DE.
 */
class KatalogTest {

    /** Schlüssel ohne Spec, die bewusst im Katalog stehen (Gerüst, genutzt von lobby). */
    private static final Set<String> GERUEST = Set.of("lobby.welcome");

    private static final Pattern KEY_FORMAT = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final Pattern PREFIX_REFERENCE = Pattern.compile("(?<!\\\\)<p:([a-z0-9_-]+)>");
    /** Formatierungs-Tags (Farben, Deko, Klick …): in Rein-Text-Schlüsseln verboten. */
    private static final Pattern STYLE_TAG = Pattern.compile(
            "(?<!\\\\)</?(?:black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray"
                    + "|dark_grey|blue|green|aqua|red|light_purple|yellow|white|bold|b|italic|i|em|underlined|u"
                    + "|strikethrough|st|obfuscated|obf|reset|newline|br|click|hover|color|c|gradient|rainbow)\\b");

    private final MessageCatalog catalog = Messages.catalog();

    @Test
    void jederSpecKeyIstInDeutschUndEnglischVorhanden() {
        for (String file : CatalogHarness.SPEC_FILES) {
            for (String key : CatalogHarness.specKeys(file)) {
                assertTrue(catalog.findExact(Language.DE_DE, key).isPresent(), file + ": de_DE fehlt " + key);
                assertTrue(catalog.findExact(Language.EN_US, key).isPresent(), file + ": en_US fehlt " + key);
            }
        }
    }

    @Test
    void deutschUndEnglischHabenDieselbenSchluessel() {
        assertEquals(new TreeSet<>(catalog.keys(Language.DE_DE)), new TreeSet<>(catalog.keys(Language.EN_US)));
    }

    @Test
    void dialekteEnthaltenNurBekannteSchluessel() {
        for (Language dialect : List.of(Language.BAR, Language.GSW)) {
            Set<String> unknown = new TreeSet<>(catalog.keys(dialect));
            unknown.removeAll(catalog.keys(Language.DE_DE));
            assertTrue(unknown.isEmpty(), dialect + " hat unbekannte Schlüssel: " + unknown);
            assertFalse(catalog.keys(dialect).isEmpty(), dialect + " ist leer");
        }
    }

    @Test
    void jederSchluesselGehoertZuGenauEinerSpec() {
        Set<String> seen = new HashSet<>();
        for (String file : CatalogHarness.SPEC_FILES) {
            for (String key : CatalogHarness.specKeys(file)) {
                assertTrue(seen.add(key), "Schlüssel in mehreren Spec-Dateien: " + key);
            }
        }
        Set<String> withoutSpec = new TreeSet<>(catalog.keys(Language.DE_DE));
        withoutSpec.removeAll(seen);
        withoutSpec.removeAll(GERUEST);
        assertTrue(withoutSpec.isEmpty(), "Schlüssel ohne Spec: " + withoutSpec);
    }

    @Test
    void schluesselzahlJeSpec() {
        // Spec 01 §7: 130 Tabellenzeilen; die Spec schätzt "≈ 215 Schlüssel" (Sammelzeilen einzeln gezählt).
        assertEquals(232, CatalogHarness.specKeys("spec01").size());
        assertEquals(21, CatalogHarness.specKeys("spec01-ergaenzungen").size());
        assertEquals(124, CatalogHarness.specKeys("spec02").size());
        assertEquals(2, CatalogHarness.specKeys("spec02-ergaenzungen").size());
        assertEquals(109, CatalogHarness.specKeys("spec08").size());
        assertEquals(9, CatalogHarness.specKeys("spec08-ergaenzungen").size());
        assertEquals(232 + 21 + 124 + 2 + 109 + 9 + GERUEST.size(), catalog.keys(Language.DE_DE).size());
    }

    @Test
    void schluesselFolgenDerNamensregel() {
        for (Language language : Language.values()) {
            for (String key : catalog.keys(language)) {
                assertTrue(KEY_FORMAT.matcher(key).matches(), language + ": ungültiger Schlüssel " + key);
            }
        }
    }

    @Test
    void keineOffenenGeschweiftenPlatzhalter() {
        forEachText((language, key, text) -> assertFalse(text.contains("{") || text.contains("}"),
                language + " " + key + " enthält { oder }: " + text));
    }

    @Test
    void jederTextIstMitMiniMessageLesbar() {
        forEachText((language, key, text) -> {
            for (Map<String, Boolean> choices : CatalogHarness.choiceCases(key)) {
                Component rendered = CatalogHarness.render(text, choices);
                String plain = CatalogHarness.plain(rendered);
                // Jedes "<" im Ergebnis muss aus einem \< stammen; sonst war ein Tag unlesbar (z. B. Großbuchstaben).
                assertEquals(count(text, "\\<"), count(plain, "<"), language + " " + key + ": nicht gelesener Tag");
                assertFalse(plain.contains(">") && count(text, "\\<") == 0,
                        language + " " + key + ": nicht gelesener Tag: " + plain);
            }
            // Auch der fertige Rohtext (Präfixe und Marke eingesetzt) muss lesbar sein.
            Component full = MessageRenderer.deserialize(Messages.raw(language.locale(), key));
            assertFalse(CatalogHarness.plain(full).contains("fehlender Text"), language + " " + key);
        });
    }

    @Test
    void deutschEntsprichtDerSpecNotation() {
        List<String> errors = new ArrayList<>();
        CatalogHarness.allSpecEntries().forEach((specKey, expected) -> {
            String key = CatalogHarness.baseKey(specKey);
            String text = catalog.findExact(Language.DE_DE, key).orElse("");
            Component actual = CatalogHarness.render(text, CatalogHarness.choices(specKey));
            String difference = CatalogHarness.difference(
                    CatalogHarness.flatten(CatalogHarness.legacy(expected)), CatalogHarness.flatten(actual));
            if (!difference.isEmpty()) {
                errors.add(specKey + ": " + difference);
            }
        });
        if (!errors.isEmpty()) {
            fail(errors.size() + " Abweichung(en) von der Spec:\n" + String.join("\n", errors));
        }
    }

    @Test
    void uebersetzungenHabenDieselbeFarbstrukturWieDeutsch() {
        List<String> errors = new ArrayList<>();
        for (Language language : List.of(Language.EN_US, Language.BAR, Language.GSW)) {
            catalog.texts(language).forEach((key, text) -> {
                String german = catalog.findExact(Language.DE_DE, key).orElse("");
                for (Map<String, Boolean> choices : CatalogHarness.choiceCases(key)) {
                    var expected = CatalogHarness.flatten(CatalogHarness.render(german, choices));
                    var actual = CatalogHarness.flatten(CatalogHarness.render(text, choices));
                    if (!CatalogHarness.structure(expected).equals(CatalogHarness.structure(actual))) {
                        errors.add(language + " " + key + choices + ":\n  de " + CatalogHarness.structure(expected)
                                + "\n  -> " + CatalogHarness.structure(actual));
                    }
                    if (leadingOrTrailingSpace(german) != leadingOrTrailingSpace(text)) {
                        errors.add(language + " " + key + ": Leerzeichen am Anfang/Ende weicht von de_DE ab");
                    }
                    if (count(german, "<newline>") != count(text, "<newline>")) {
                        errors.add(language + " " + key + ": andere Zeilenzahl als de_DE");
                    }
                }
            });
        }
        if (!errors.isEmpty()) {
            fail(errors.size() + " Strukturabweichung(en):\n" + String.join("\n", errors));
        }
    }

    @Test
    void praefixVerweiseZeigenAufVorhandenePraefixe() {
        forEachText((language, key, text) -> {
            Matcher matcher = PREFIX_REFERENCE.matcher(text);
            while (matcher.find()) {
                String prefix = "prefix." + matcher.group(1);
                assertTrue(catalog.findExact(Language.DE_DE, prefix).isPresent(), key + " verweist auf " + prefix);
                assertFalse(key.startsWith("prefix."), "Präfixe dürfen keine Präfixe enthalten: " + key);
            }
        });
    }

    @Test
    void variantenHabenEinenGrundschluessel() {
        for (String key : catalog.keys(Language.DE_DE)) {
            for (String suffix : List.of(Messages.SINGULAR_SUFFIX, Messages.LEGACY_SUFFIX)) {
                if (key.endsWith(suffix)) {
                    String base = key.substring(0, key.length() - suffix.length());
                    assertTrue(catalog.findExact(Language.DE_DE, base).isPresent(), key + " ohne " + base);
                }
            }
        }
    }

    @Test
    void reinTextSchluesselEnthaltenKeineFormatierung() {
        // Diese Texte gehen ohne MiniMessage an Discord/API oder werden in andere Texte eingesetzt (Messages.plain).
        List<String> plainPrefixes = List.of("bot.", "disclaimer", "server.restart.time.", "stats.label.",
                "stats.period.", "clan.role.", "punish.type.", "punish.duration.", "cloud.help.list.",
                "cloud.help.info.", "cloud.help.gui.", "cloud.help.start.", "cloud.help.stop.", "cloud.help.drain.",
                "cloud.help.restart.", "cloud.help.restart-cancel.", "cloud.help.templates.", "cloud.help.scale.",
                "cloud.help.reload.", "cloud.help.maps.", "cloud.help.map.", "cloud.help.proxies.");
        forEachText((language, key, text) -> {
            if (plainPrefixes.stream().anyMatch(key::startsWith)) {
                assertFalse(STYLE_TAG.matcher(text).find(), language + " " + key + " ist kein reiner Text: " + text);
            }
        });
    }

    @Test
    void keineHartCodierteOderFremdeMarke() {
        // Die verbotenen Wörter werden zusammengesetzt, damit der Marken-Grep der CI (Spec 08 §6.19) diesen Test
        // selbst nicht trifft.
        List<String> forbidden = List.of("gom" + "me", "chee" + "tah", "octo" + "pus",
                BuildInfo.BRAND_NAME.toLowerCase(Locale.ROOT), "retro" + "mc");
        for (Language language : Language.values()) {
            String content = CatalogHarness.fileContent(language.resourceName()).toLowerCase(Locale.ROOT);
            for (String word : forbidden) {
                assertFalse(content.contains(word), language.fileName() + " enthält \"" + word + "\"");
            }
        }
    }

    @Test
    void praefixeEndenMitLeerzeichenUndAnhaengeBeginnenDamit() {
        // Leerzeichen stehen in den Dateien als Unicode-Escape, damit Editoren sie am Zeilenende nicht löschen.
        Set<String> leading = Set.of("tab.entry.clan", "item.hint.right", "cloud.tpl.override");
        Set<String> trailing = Set.of("chat.clan-tag", "chat.no-clan", "chat.channel.all", "chat.channel.spectator",
                "tab.entry.rank");
        forEachText((language, key, text) -> {
            if (key.startsWith("prefix.") || trailing.contains(key)) {
                assertTrue(text.endsWith(" "), language + " " + key + " endet nicht mit Leerzeichen");
            }
            if (leading.contains(key)) {
                assertTrue(text.startsWith(" "), language + " " + key + " beginnt nicht mit Leerzeichen");
            }
        });
    }

    @Test
    void basisBuendelIstGueltigesUtf8() {
        // MessageCatalog liest streng UTF-8; Umlaute müssen ankommen.
        assertEquals("<red>Zurück", catalog.findExact(Language.DE_DE, "gui.back").orElseThrow());
        assertEquals("Zerstörte Betten", catalog.findExact(Language.DE_DE, "stats.label.beds").orElseThrow());
    }

    private void forEachText(TextCheck check) {
        for (Language language : Language.values()) {
            catalog.texts(language).forEach((key, text) -> check.accept(language, key, text));
        }
    }

    private static boolean leadingOrTrailingSpace(String text) {
        return text.startsWith(" ") || text.endsWith(" ");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            count++;
        }
        return count;
    }

    @FunctionalInterface
    private interface TextCheck {
        void accept(Language language, String key, String text);
    }
}
