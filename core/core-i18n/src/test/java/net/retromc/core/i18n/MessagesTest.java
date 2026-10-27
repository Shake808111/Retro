package net.retromc.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.retromc.core.api.BuildInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessagesTest {

    private static final Locale BAR = Locale.of("bar");
    private static final Locale GSW = Locale.of("gsw");

    @AfterEach
    void katalogZuruecksetzen() {
        Messages.reload();
    }

    @Test
    void deutschIstStandardUndMarkeWirdEingesetzt() {
        String text = Messages.raw(Locale.GERMANY, "lobby.welcome");
        assertTrue(text.startsWith("<gray>Willkommen"), text);
        assertTrue(text.contains(BuildInfo.BRAND_NAME), text);
        assertFalse(text.contains("<brand>"), text);
    }

    @Test
    void markeInGrossbuchstaben() {
        assertEquals("<white><bold>" + BuildInfo.BRAND_NAME.toUpperCase(Locale.ROOT) + ".NET",
                Messages.raw(Locale.GERMANY, "sb.title"));
    }

    @Test
    void englischUndRueckfallAufDeutsch() {
        assertTrue(Messages.raw(Locale.US, "lobby.welcome").startsWith("<gray>Welcome"));
        // Nicht unterstützte Sprache -> Deutsch (Basisbündel), unabhängig von der Systemsprache.
        assertEquals(Messages.raw(Locale.GERMANY, "lobby.welcome"), Messages.raw(Locale.FRANCE, "lobby.welcome"));
        assertEquals(Messages.raw(Locale.US, "lobby.welcome"), Messages.raw(Locale.UK, "lobby.welcome"));
    }

    @Test
    void fehlenderSchluesselImDialektKommtAusDeutsch() {
        // Spec 01 §9 A19: Team-Texte sind im Dialekt nicht übersetzt -> de_DE.
        assertTrue(Messages.catalog().findExact(Language.BAR, "admin.reloaded").isEmpty());
        assertEquals(Messages.raw(Locale.GERMANY, "admin.reloaded"), Messages.raw(BAR, "admin.reloaded"));
        assertEquals("<gray>Griaß di auf <gold>" + BuildInfo.BRAND_NAME + "</gold>!",
                Messages.raw(BAR, "lobby.welcome"));
        assertTrue(Messages.raw(GSW, "gui.back").contains("Zrugg"));
    }

    @Test
    void fehlenderSchluesselWirdMarkiert() {
        assertEquals("<red>[fehlender Text: gibt.es.nicht]", Messages.raw(Locale.GERMANY, "gibt.es.nicht"));
        assertFalse(Messages.has("gibt.es.nicht"));
        assertTrue(Messages.has("friend.online"));
    }

    @Test
    void praefixVerweisWirdEingesetzt() {
        assertEquals("<gray>[<red>Freunde<gray>] <name> <gray>ist jetzt <green>online",
                Messages.raw(Locale.GERMANY, "friend.online"));
        assertEquals("<gray>[<red>Friends<gray>] <name> <gray>is now <green>online",
                Messages.raw(Locale.US, "friend.online"));
    }

    @Test
    void legacyVarianteGiltNurInDerLegacyAera() {
        // Spec 01 O4/K8: MODERN §c/§d, LEGACY §4/§5.
        assertTrue(Messages.raw(Locale.GERMANY, "party.joined", UiEra.MODERN).startsWith("<gray>[<light_purple>Party"));
        assertTrue(Messages.raw(Locale.GERMANY, "party.joined", UiEra.LEGACY).startsWith("<gray>[<dark_purple>Party"));
        assertTrue(Messages.raw(Locale.GERMANY, "friend.offline", UiEra.LEGACY).startsWith("<gray>[<dark_red>Freunde"));
        // Ohne .legacy-Fassung bleibt der Text gleich.
        assertEquals(Messages.raw(Locale.GERMANY, "clan.chat"),
                Messages.raw(Locale.GERMANY, "clan.chat", UiEra.LEGACY));
    }

    @Test
    void einzahlBeiEins() {
        assertEquals("<red>Respawn in <yellow><n> <red>Sekunde", Messages.plural(Locale.GERMANY, "title.respawn", 1));
        assertEquals("<red>Respawn in <yellow><n> <red>Sekunden", Messages.plural(Locale.GERMANY, "title.respawn", 2));
        assertEquals("<red>Respawn in <yellow><n> <red>Sekunden", Messages.plural(Locale.GERMANY, "title.respawn", 0));
        assertEquals("<gold><n> Stunde", Messages.plural(Locale.GERMANY, "sb.value.playtime", 1));
        assertEquals("<gold><n> hours", Messages.plural(Locale.US, "sb.value.playtime", 455));
        // Ohne .one-Variante gilt der Grundtext.
        assertEquals(Messages.raw(Locale.GERMANY, "friend.acceptall"),
                Messages.plural(Locale.GERMANY, "friend.acceptall", 1));
    }

    @Test
    void neustartZeitNachSpec08() {
        // Spec 08 §7 server.restart.chat: {time} = "5 Minuten", "60 Sekunden", "1 Sekunde".
        assertEquals("5 Minuten", Messages.restartTime(Locale.GERMANY, 300));
        assertEquals("2 Minuten", Messages.restartTime(Locale.GERMANY, 120));
        assertEquals("60 Sekunden", Messages.restartTime(Locale.GERMANY, 60));
        assertEquals("45 Sekunden", Messages.restartTime(Locale.GERMANY, 45));
        assertEquals("1 Sekunde", Messages.restartTime(Locale.GERMANY, 1));
        assertEquals("5 minutes", Messages.restartTime(Locale.US, 300));
        assertEquals("1 second", Messages.restartTime(Locale.US, 1));
    }

    @Test
    void reinerTextFuerDiscord() {
        assertEquals("Verknüpft mit **Steve**.",
                Messages.plain(Locale.GERMANY, "bot.link.ok", Map.of("name", "Steve")));
        assertEquals(BuildInfo.BRAND_NAME + " – Status", Messages.plain(Locale.US, "bot.status.title", Map.of()));
        assertEquals("info <server>", Messages.plain(Locale.US, "cloud.help.info.usage", Map.of()));
    }

    @Test
    void sprachenNachSpec01() {
        assertEquals(List.of(Locale.GERMANY, Locale.US, BAR, GSW), Messages.SUPPORTED);
        assertEquals(Locale.GERMANY, Messages.DEFAULT_LOCALE);
        assertEquals(List.of(Language.BAR, Language.DE_DE), Language.BAR.fallbackChain());
        assertEquals(List.of(Language.EN_US, Language.DE_DE), Language.EN_US.fallbackChain());
        assertEquals(List.of(Language.DE_DE), Language.DE_DE.fallbackChain());
        assertEquals("Schwiizerdütsch", Language.GSW.displayName());
        assertEquals("bar", Language.BAR.code());
        assertEquals("en_US", Language.EN_US.code());
    }

    @Test
    void spracheAusCode() {
        // /language [Code] (Spec 02 §9 #18: "/language en") und core.player.locale.
        assertEquals(Optional.of(Language.EN_US), Language.byCode("en"));
        assertEquals(Optional.of(Language.EN_US), Language.byCode("en_US"));
        assertEquals(Optional.of(Language.DE_DE), Language.byCode("de-de"));
        assertEquals(Optional.of(Language.BAR), Language.byCode("BAR"));
        assertEquals(Optional.of(Language.GSW), Language.byCode("gsw"));
        assertEquals(Optional.empty(), Language.byCode("fr"));
        assertEquals(Optional.empty(), Language.byCode(" "));
    }

    @Test
    void spracheBeimErstenJoin() {
        // Spec 01 §6.3 Nr. 4: de* -> de_DE (auch de_AT/de_CH), sonst en_US.
        assertEquals(Language.DE_DE, Language.forClientLocale("de_de"));
        assertEquals(Language.DE_DE, Language.forClientLocale("de_at"));
        assertEquals(Language.DE_DE, Language.forClientLocale("de_CH"));
        assertEquals(Language.EN_US, Language.forClientLocale("en_us"));
        assertEquals(Language.EN_US, Language.forClientLocale("fr_fr"));
        assertEquals(Language.EN_US, Language.forClientLocale(Locale.JAPAN));
        assertEquals(Language.DE_DE, Language.forClientLocale((String) null));
    }

    @Test
    void spracheZuLocale() {
        assertEquals(Language.DE_DE, Language.of(Locale.of("de", "AT")));
        assertEquals(Language.EN_US, Language.of(Locale.UK));
        assertEquals(Language.GSW, Language.of(GSW));
        assertEquals(Language.DE_DE, Language.of(Locale.FRANCE));
    }

    @Test
    void ueberschreibungenAusVerzeichnis(@TempDir Path dir) throws IOException {
        // /netreload (Spec 01 #88): Texte aus einem Verzeichnis liegen über dem Klassenpfad.
        Files.writeString(dir.resolve("messages_en.properties"), "admin.reloaded=<green>Reloaded!\n",
                StandardCharsets.UTF_8);
        Messages.reload(dir);
        assertEquals("<green>Reloaded!", Messages.raw(Locale.US, "admin.reloaded"));
        assertEquals("<green>Konfiguration und Texte neu geladen.", Messages.raw(Locale.GERMANY, "admin.reloaded"));
        Messages.reload();
        assertEquals("<green>Configuration and texts reloaded.", Messages.raw(Locale.US, "admin.reloaded"));
    }
}
