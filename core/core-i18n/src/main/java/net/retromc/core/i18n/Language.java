package net.retromc.core.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ausgelieferte Sprachen nach Spec 01 §6.14 (DEFAULT O29: de, en, bar, gsw; weitere erst mit Übersetzung).
 *
 * <p>Fallback-Kette: gewählte Sprache → (Dialekt → {@code de_DE}) → {@code de_DE}. Der {@link #code()} ist der
 * Wert in {@code core.player.locale}.
 */
public enum Language {

    DE_DE("de_DE", Locale.GERMANY, "Deutsch", "", false),
    EN_US("en_US", Locale.US, "English", "_en", false),
    BAR("bar", Locale.of("bar"), "Boarisch", "_bar", true),
    GSW("gsw", Locale.of("gsw"), "Schwiizerdütsch", "_gsw", true);

    /** Basis- und Rückfallsprache. */
    public static final Language BASE = DE_DE;

    private final String code;
    private final Locale locale;
    private final String displayName;
    private final String bundleSuffix;
    private final boolean dialect;

    Language(String code, Locale locale, String displayName, String bundleSuffix, boolean dialect) {
        this.code = code;
        this.locale = locale;
        this.displayName = displayName;
        this.bundleSuffix = bundleSuffix;
        this.dialect = dialect;
    }

    /** Code wie in {@code core.player.locale}, z. B. {@code de_DE} oder {@code bar}. */
    public String code() {
        return code;
    }

    public Locale locale() {
        return locale;
    }

    /** Name in der Sprachliste ({@code lang.list.line}), immer in der Sprache selbst. */
    public String displayName() {
        return displayName;
    }

    public boolean dialect() {
        return dialect;
    }

    /** Klassenpfad-Ressource des Bündels, z. B. {@code i18n/messages_en.properties}. */
    public String resourceName() {
        return "i18n/" + fileName();
    }

    /** Dateiname des Bündels, z. B. {@code messages_bar.properties}. */
    public String fileName() {
        return "messages" + bundleSuffix + ".properties";
    }

    /** Suchreihenfolge für Texte: diese Sprache, danach {@code de_DE} (Spec 01 §6.14). */
    public List<Language> fallbackChain() {
        List<Language> chain = new ArrayList<>(2);
        chain.add(this);
        if (this != BASE) {
            chain.add(BASE);
        }
        return List.copyOf(chain);
    }

    /**
     * Sprache zu einem beliebigen {@link Locale}: exakte Sprache, sonst passende Sprachfamilie
     * ({@code de_AT} → {@code de_DE}, {@code en_GB} → {@code en_US}), sonst {@code de_DE} (Basis).
     */
    public static Language of(Locale locale) {
        if (locale == null) {
            return BASE;
        }
        for (Language language : values()) {
            if (language.locale.equals(locale)) {
                return language;
            }
        }
        return byLanguageCode(locale.getLanguage()).orElse(BASE);
    }

    /**
     * Sprache zu einem Code aus {@code /language [Code]} oder der Datenbank. Erlaubt sind der volle Code und der
     * Sprachteil, Groß-/Kleinschreibung und {@code -}/{@code _} egal: {@code de}, {@code de_DE}, {@code de-de},
     * {@code en}, {@code en_US}, {@code bar}, {@code gsw}.
     */
    public static Optional<Language> byCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.strip().replace('-', '_').toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.code.toLowerCase(Locale.ROOT).equals(normalized)
                    || language.locale.getLanguage().equals(normalized)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * Sprache beim ersten Join aus der Client-Sprache (Spec 01 §6.3 Nr. 4): {@code de*} (auch {@code de_AT},
     * {@code de_CH}) → {@code de_DE}, jede andere Client-Sprache → {@code en_US}. Ohne Client-Sprache gilt die
     * Basis {@code de_DE}.
     */
    public static Language forClientLocale(String clientLocale) {
        if (clientLocale == null || clientLocale.isBlank()) {
            return BASE;
        }
        return clientLocale.strip().toLowerCase(Locale.ROOT).startsWith("de") ? DE_DE : EN_US;
    }

    /** Wie {@link #forClientLocale(String)} für ein {@link Locale} (z. B. aus der Velocity-API). */
    public static Language forClientLocale(Locale clientLocale) {
        return clientLocale == null ? BASE : forClientLocale(clientLocale.toString());
    }

    private static Optional<Language> byLanguageCode(String languageCode) {
        for (Language language : values()) {
            if (language.locale.getLanguage().equals(languageCode)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
