package net.retromc.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.retromc.core.api.BuildInfo;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void deutschIstStandardUndMarkeWirdEingesetzt() {
        String text = Messages.raw(Locale.GERMANY, "lobby.welcome");
        assertTrue(text.startsWith("<gray>Willkommen"), text);
        assertTrue(text.contains(BuildInfo.BRAND_NAME), text);
    }

    @Test
    void englischUndRueckfallAufDeutsch() {
        assertTrue(Messages.raw(Locale.US, "lobby.welcome").startsWith("<gray>Welcome"));
        // Nicht unterstützte Sprache -> Deutsch (Basisbündel), unabhängig von der Systemsprache.
        assertEquals(Messages.raw(Locale.GERMANY, "lobby.welcome"), Messages.raw(Locale.FRANCE, "lobby.welcome"));
    }

    @Test
    void fehlenderSchluesselWirdMarkiert() {
        assertTrue(Messages.raw(Locale.GERMANY, "gibt.es.nicht").contains("fehlender Text"));
    }
}
