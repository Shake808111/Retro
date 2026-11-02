package net.retromc.platform.common.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.i18n.Language;
import org.junit.jupiter.api.Test;

/** Standard-Texte aus core-i18n und Sprache aus der Client-Einstellung. */
class CatalogI18nTest {

    private static final UUID AUSTRIAN = UUID.randomUUID();
    private static final UUID BRITISH = UUID.randomUUID();

    private final I18n i18n = new CatalogI18n(player -> Optional.ofNullable(
            Map.of(AUSTRIAN, Locale.forLanguageTag("de-AT"), BRITISH, Locale.UK).get(player)));

    @Test
    void clientSpracheWirdAufUnterstuetzteSpracheAbgebildet() {
        assertEquals(Language.DE_DE.locale(), i18n.locale(AUSTRIAN));
        assertEquals(Language.EN_US.locale(), i18n.locale(BRITISH));
        assertEquals(Language.BASE.locale(), i18n.locale(UUID.randomUUID()));
    }

    @Test
    void rendertTexteAusDemKatalog() {
        assertTrue(i18n.has("gui.back"));
        assertFalse(i18n.has("gibt.es.nicht"));
        assertEquals("Zurück",
                PlainTextComponentSerializer.plainText().serialize(i18n.render(Locale.GERMANY, "gui.back")));
        assertEquals(1, i18n.renderLines(Locale.US, "gui.back").size());
    }
}
