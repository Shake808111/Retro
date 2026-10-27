package net.retromc.core.i18n;

import net.retromc.core.api.BuildInfo;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liefert Texte im MiniMessage-Format (Spec 00 §4.3, Spec 02 §6.13). Deutsch ist Basis und Rückfallsprache;
 * Sprachen und Fallback-Kette nach Spec 01 §6.14 ({@link Language}).
 *
 * <p>Beim Abruf werden eingesetzt:
 * <ul>
 *   <li>{@code <p:name>} → Text des Präfixes {@code prefix.name} (Spec-Notation {@code {p:name}}),</li>
 *   <li>{@code <brand>} → {@link BuildInfo#BRAND_NAME}, {@code <brand_upper>} → Marke in Großbuchstaben.</li>
 * </ul>
 * Alle übrigen Platzhalter ({@code <name>}, {@code <n>} …) setzt der Aufrufer beim Rendern ein, siehe
 * {@link MessageRenderer} (Adventure) bzw. {@link #plain(Locale, String, Map)} (reiner Text, z. B. Discord).
 *
 * <p>Varianten: {@code <key>.one} ist die Einzahl zu {@code <key>} ({@link #plural}), {@code <key>.legacy} die
 * Fassung für {@link UiEra#LEGACY}. Diese Klasse braucht kein Adventure zur Laufzeit.
 */
public final class Messages {

    public static final Locale DEFAULT_LOCALE = Language.BASE.locale();
    public static final List<Locale> SUPPORTED = Arrays.stream(Language.values()).map(Language::locale).toList();

    /** Endung der Einzahl-Variante. */
    public static final String SINGULAR_SUFFIX = ".one";
    /** Endung der LEGACY-Variante. */
    public static final String LEGACY_SUFFIX = ".legacy";

    private static final Pattern PREFIX_REFERENCE = Pattern.compile("(?<!\\\\)<p:([a-z0-9_-]+)>");
    private static final Pattern BRAND = Pattern.compile("(?<!\\\\)<brand>");
    private static final Pattern BRAND_UPPER = Pattern.compile("(?<!\\\\)<brand_upper>");

    private static volatile MessageCatalog catalog;

    private Messages() {
    }

    /** Aktueller Katalog; beim ersten Zugriff aus dem Klassenpfad geladen. */
    public static MessageCatalog catalog() {
        MessageCatalog current = catalog;
        if (current == null) {
            synchronized (Messages.class) {
                current = catalog;
                if (current == null) {
                    current = MessageCatalog.load(Messages.class.getClassLoader());
                    catalog = current;
                }
            }
        }
        return current;
    }

    /** Lädt die Texte neu aus dem Klassenpfad. */
    public static void reload() {
        catalog = MessageCatalog.load(Messages.class.getClassLoader());
    }

    /** Lädt die Texte neu und legt die Dateien aus {@code overrideDirectory} darüber ({@code /netreload}). */
    public static void reload(Path overrideDirectory) {
        catalog = MessageCatalog.load(Messages.class.getClassLoader(), overrideDirectory);
    }

    /** Sprache zu einem Locale; nicht unterstützte Sprachen ergeben {@code de_DE}. */
    public static Language language(Locale locale) {
        return Language.of(locale);
    }

    /** {@code true}, wenn der Schlüssel im Basis-Bündel ({@code de_DE}) existiert. */
    public static boolean has(String key) {
        return catalog().findExact(Language.BASE, key).isPresent();
    }

    /** Rohtext (MiniMessage) für den Schlüssel; fehlt er, kommt {@code <red>[fehlender Text: key]}. */
    public static String raw(Locale locale, String key) {
        return raw(locale, key, UiEra.MODERN);
    }

    /** Wie {@link #raw(Locale, String)}; bei {@link UiEra#LEGACY} gewinnt eine vorhandene {@code .legacy}-Fassung. */
    public static String raw(Locale locale, String key, UiEra era) {
        Language language = Language.of(locale);
        return lookup(language, key, era).map(text -> expand(language, text, era)).orElseGet(() -> missing(key));
    }

    /** Rohtext mit Einzahl/Mehrzahl: bei {@code count == 1} die Variante {@code key.one}, falls vorhanden. */
    public static String plural(Locale locale, String key, long count) {
        return plural(locale, key, count, UiEra.MODERN);
    }

    /** Wie {@link #plural(Locale, String, long)} mit Ära. */
    public static String plural(Locale locale, String key, long count, UiEra era) {
        return raw(locale, pluralKey(Language.of(locale), key, count), era);
    }

    /** Schlüssel, den {@link #plural} für die Anzahl verwendet. */
    public static String pluralKey(Language language, String key, long count) {
        String singular = key + SINGULAR_SUFFIX;
        if (count == 1 && catalog().find(language, singular).isPresent()) {
            return singular;
        }
        return key;
    }

    /**
     * Reiner Text für Ziele ohne Adventure (Discord-Bot, API): setzt {@code <name>}-Platzhalter aus {@code values}
     * wörtlich ein und löst {@code \<} auf. Nur für Texte ohne Formatierungs-Tags (z. B. {@code bot.*},
     * {@code disclaimer}, {@code server.restart.time.*}).
     */
    public static String plain(Locale locale, String key, Map<String, ?> values) {
        String text = raw(locale, key);
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Pattern placeholder = Pattern.compile("(?<!\\\\)<" + Pattern.quote(entry.getKey()) + ">");
            text = placeholder.matcher(text).replaceAll(Matcher.quoteReplacement(String.valueOf(entry.getValue())));
        }
        return text.replace("\\<", "<");
    }

    /**
     * Zeitangabe für {@code server.restart.chat} ({@code <time>}, Spec 08 §7): volle Minuten über 60 s als Minuten
     * ({@code 5 Minuten}), sonst Sekunden ({@code 60 Sekunden}, {@code 1 Sekunde}).
     */
    public static String restartTime(Locale locale, long seconds) {
        Formats formats = Formats.of(locale);
        Language language = Language.of(locale);
        if (seconds > 60 && seconds % 60 == 0) {
            long minutes = seconds / 60;
            String key = pluralKey(language, "server.restart.time.minutes", minutes);
            return plain(locale, key, Map.of("minutes", formats.integer(minutes)));
        }
        String key = pluralKey(language, "server.restart.time.seconds", seconds);
        return plain(locale, key, Map.of("seconds", formats.integer(seconds)));
    }

    private static Optional<String> lookup(Language language, String key, UiEra era) {
        Objects.requireNonNull(key, "key");
        MessageCatalog current = catalog();
        for (Language candidate : language.fallbackChain()) {
            if (era == UiEra.LEGACY) {
                Optional<String> legacy = current.findExact(candidate, key + LEGACY_SUFFIX);
                if (legacy.isPresent()) {
                    return legacy;
                }
            }
            Optional<String> text = current.findExact(candidate, key);
            if (text.isPresent()) {
                return text;
            }
        }
        return Optional.empty();
    }

    private static String expand(Language language, String text, UiEra era) {
        Matcher matcher = PREFIX_REFERENCE.matcher(text);
        String withPrefixes = matcher.replaceAll(match -> {
            String prefixKey = "prefix." + match.group(1);
            String prefix = lookup(language, prefixKey, era).orElseGet(() -> missing(prefixKey));
            return Matcher.quoteReplacement(prefix);
        });
        String brandUpper = BuildInfo.BRAND_NAME.toUpperCase(Locale.ROOT);
        String withUpper = BRAND_UPPER.matcher(withPrefixes).replaceAll(Matcher.quoteReplacement(brandUpper));
        return BRAND.matcher(withUpper).replaceAll(Matcher.quoteReplacement(BuildInfo.BRAND_NAME));
    }

    private static String missing(String key) {
        return "<red>[fehlender Text: " + key + "]";
    }
}
