package net.retromc.core.i18n;

import net.retromc.core.api.BuildInfo;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Liefert Texte im MiniMessage-Format. Deutsch ist Basis und Rückfallsprache, Englisch optional
 * (Notiz 07, Abschnitt 5 "Text and i18n"; Sprachwahl DE/EN vgl. {@code /language} in Notiz 05, Abschnitt 7).
 *
 * <p>Der Platzhalter {@code <brand>} wird durch {@link BuildInfo#BRAND_NAME} ersetzt. Das
 * Rendern zu Adventure-Komponenten (MiniMessage) übernehmen paper-core und velocity-core.
 */
public final class Messages {

    public static final Locale DEFAULT_LOCALE = Locale.GERMANY;
    public static final List<Locale> SUPPORTED = List.of(Locale.GERMANY, Locale.US);

    private static final String BUNDLE = "i18n.messages";
    private static final ResourceBundle.Control NO_SYSTEM_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private Messages() {
    }

    /** Rohtext (MiniMessage) für den Schlüssel; unbekannte Sprachen fallen auf Deutsch zurück. */
    public static String raw(Locale locale, String key) {
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, locale, Messages.class.getClassLoader(), NO_SYSTEM_FALLBACK);
        try {
            return bundle.getString(key).replace("<brand>", BuildInfo.BRAND_NAME);
        } catch (MissingResourceException e) {
            return "<red>[fehlender Text: " + key + "]";
        }
    }
}
