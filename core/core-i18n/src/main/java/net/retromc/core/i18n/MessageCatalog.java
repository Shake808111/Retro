package net.retromc.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Unveränderlicher Textkatalog aller {@link Language Sprachen}: je Sprache eine Datei
 * {@code i18n/messages[_xx].properties} (UTF-8, MiniMessage), optional überschrieben durch gleichnamige Dateien in
 * einem Verzeichnis (z. B. {@code plugins/RetroMCCore/lang}, neu geladen mit {@code /netreload}, Spec 01 #88).
 *
 * <p>Texte werden so geliefert, wie sie in der Datei stehen; Marke und Präfixe setzt erst {@link Messages} ein.
 */
public final class MessageCatalog {

    private final Map<Language, Map<String, String>> texts;

    private MessageCatalog(Map<Language, Map<String, String>> texts) {
        this.texts = texts;
    }

    /** Lädt die Bündel aus dem Klassenpfad. */
    public static MessageCatalog load(ClassLoader loader) {
        return load(loader, Optional.empty());
    }

    /**
     * Lädt die Bündel aus dem Klassenpfad und legt die Dateien aus {@code overrideDirectory} darüber (fehlende
     * Dateien oder ein fehlendes Verzeichnis sind erlaubt).
     */
    public static MessageCatalog load(ClassLoader loader, Path overrideDirectory) {
        return load(loader, Optional.of(overrideDirectory));
    }

    private static MessageCatalog load(ClassLoader loader, Optional<Path> overrideDirectory) {
        Objects.requireNonNull(loader, "loader");
        Map<Language, Map<String, String>> all = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            Map<String, String> bundle = new LinkedHashMap<>();
            try (InputStream in = loader.getResourceAsStream(language.resourceName())) {
                if (in == null) {
                    if (language == Language.BASE) {
                        throw new IllegalStateException("Basis-Bündel fehlt: " + language.resourceName());
                    }
                } else {
                    bundle.putAll(read(in, language.resourceName()));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Bündel nicht lesbar: " + language.resourceName(), e);
            }
            if (overrideDirectory.isPresent()) {
                Path file = overrideDirectory.get().resolve(language.fileName());
                if (Files.isRegularFile(file)) {
                    try (InputStream in = Files.newInputStream(file)) {
                        bundle.putAll(read(in, file.toString()));
                    } catch (IOException e) {
                        throw new UncheckedIOException("Datei nicht lesbar: " + file, e);
                    }
                }
            }
            all.put(language, Map.copyOf(bundle));
        }
        return new MessageCatalog(all);
    }

    /** Text über die Fallback-Kette der Sprache (Spec 01 §6.14). */
    public Optional<String> find(Language language, String key) {
        for (Language candidate : language.fallbackChain()) {
            String text = texts.get(candidate).get(key);
            if (text != null) {
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }

    /** Text nur aus dem Bündel dieser Sprache, ohne Rückfall. */
    public Optional<String> findExact(Language language, String key) {
        return Optional.ofNullable(texts.get(language).get(key));
    }

    /** Schlüssel, die das Bündel dieser Sprache selbst enthält. */
    public Set<String> keys(Language language) {
        return texts.get(language).keySet();
    }

    /** Alle Texte des Bündels dieser Sprache (unveränderlich, ohne Rückfall). */
    public Map<String, String> texts(Language language) {
        return texts.get(language);
    }

    private static Map<String, String> read(InputStream in, String source) throws IOException {
        // Streng UTF-8: ein falsch kodiertes Bündel soll laut scheitern statt Umlaute zu verlieren.
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT));
        Properties properties = new Properties();
        try {
            properties.load(reader);
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültige Escape-Sequenz in " + source, e);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return result;
    }
}
