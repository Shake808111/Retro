package net.retromc.core.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.retromc.core.api.BuildInfo;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.junit.jupiter.api.Test;

/** Rendern mit echten Platzhaltern, wie Paper und Velocity es tun (Spec 01 §9, Spec 02 §9). */
class MessageRendererTest {

    private static final Locale DE = Locale.GERMANY;

    @Test
    void bannBildschirmExaktWieSpec() {
        // Spec 01 §9 A8: Zeilen und Farben wie §7 (Komponente -> Zeichen mit Farbe vergleichen).
        Component perm = MessageRenderer.render(DE, "screen.ban.perm", Placeholder.unparsed("reason", "Hacking"),
                Placeholder.unparsed("id", "Ab3dE6gH9kL"), Placeholder.unparsed("appeal_url", "https://example.org/a"));
        assertSameAsLegacy("§cDu wurdest §4PERMANENT §cvom Netzwerk gebannt.\n§3Grund: §cHacking [Ab3dE6gH9kL]\n\n"
                + "§aEinen Entbannungsantrag kannst du unter §ehttps://example.org/a §astellen.", perm);

        Component temp = MessageRenderer.render(DE, "screen.ban.temp", Placeholder.unparsed("duration", "30d 0h 0m 0s"),
                Placeholder.unparsed("reason", "Hacking"), Placeholder.unparsed("id", "R:4F7K2M9Q"),
                Placeholder.unparsed("remaining", "29d 13h 42m 34s"),
                Placeholder.unparsed("appeal_url", "https://example.org/a"));
        assertSameAsLegacy("§cDu wurdest für §e30d 0h 0m 0s §cvom Netzwerk gebannt!\n"
                + "§3Grund: §cHacking [R:4F7K2M9Q]\n\n"
                + "§3Verbleibende Zeit: §e29d 13h 42m 34s\n"
                + "§aEinen Entbannungsantrag kannst du unter §ehttps://example.org/a §astellen.", temp);
    }

    @Test
    void muteHinweisMitAuswahlDauerOderPermanent() {
        TagResolver[] common = {Placeholder.unparsed("duration", "3d 0h 0m 0s"),
            Placeholder.unparsed("reason", "Spam"), Placeholder.unparsed("id", "AB12CD34"),
            Placeholder.unparsed("remaining", "2d 23h 9m 58s")};
        Component temp = MessageRenderer.render(DE, "mute.notice",
                TagResolver.resolver(TagResolver.resolver(common), Formatter.booleanChoice("permanent", false)));
        assertSameAsLegacy("§cDu wurdest für 3d 0h 0m 0s aus dem Chat gebannt!\n§eMutegrund§7: §cSpam [AB12CD34]\n"
                + "§aVerbleibende Zeit§7: §e2d 23h 9m 58s", temp);
        Component perm = MessageRenderer.render(DE, "mute.notice",
                TagResolver.resolver(TagResolver.resolver(common), Formatter.booleanChoice("permanent", true)));
        assertSameAsLegacy("§cDu wurdest für §4PERMANENT§c aus dem Chat gebannt!\n"
                + "§eMutegrund§7: §cSpam [AB12CD34]\n"
                + "§aVerbleibende Zeit§7: §e2d 23h 9m 58s", perm);
    }

    @Test
    void statsBlockPasstAufAddonRegeln() {
        // Spec 02 §9 #5 und #6: Kopf/Zeilen/Fuß nach den Community-Regexen, 22 Striche, Name §6 ohne Rangfarbe.
        Formats formats = Formats.of(DE);
        String period = Messages.raw(DE, "stats.period.30d");
        Component header = MessageRenderer.render(DE, "stats.header", Placeholder.unparsed("name", "Steve"),
                Placeholder.unparsed("period", period));
        Component line = MessageRenderer.render(DE, "stats.line",
                Placeholder.unparsed("label", Messages.raw(DE, "stats.label.winrate")),
                Placeholder.unparsed("value", formats.statsWinrate(140, 165)));
        Component footer = MessageRenderer.render(DE, "stats.footer");

        Matcher head = Pattern.compile(".*statistiken von\\s+(?<p>[^(]+?)\\s*\\((?<period>[^)]+)\\).*",
                Pattern.CASE_INSENSITIVE).matcher(CatalogHarness.plain(header));
        assertTrue(head.matches(), CatalogHarness.plain(header));
        assertEquals("Steve", head.group("p"));
        assertTrue(head.group("period").contains("30"));
        assertTrue(Messages.raw(DE, "stats.period.all").contains("insgesamt"));

        String plainLine = CatalogHarness.plain(line);
        Matcher row = Pattern.compile("^\\s*(?<key>[^:]+)\\s*:\\s*(?<val>.+?)\\s*$").matcher(plainLine);
        assertTrue(row.matches(), plainLine);
        assertEquals("Siegesquote", row.group("key"));
        assertEquals("84.85 %", row.group("val"));
        assertTrue(plainLine.startsWith(" ") && !plainLine.startsWith("  "), "genau ein Leerzeichen am Anfang");

        assertEquals("-".repeat(22), CatalogHarness.plain(footer));
        assertTrue(CatalogHarness.plain(footer).matches("^-{5,}$"));
        assertSameAsLegacy("§7-= §eStatistiken von §6Steve §7(30 Tage) =-", header);
    }

    @Test
    void scoreboardWerteFuerClanUndFreunde() {
        // Spec 01 §9 A18: Clan "1/9 [3IX]", Freunde "32/495".
        Component clan = MessageRenderer.render(DE, "sb.value.clan", Placeholder.unparsed("online", "1"),
                Placeholder.unparsed("total", "9"), Placeholder.unparsed("tag", "3IX"));
        assertEquals("1/9 [3IX]", CatalogHarness.plain(clan));
        Component friends = MessageRenderer.render(DE, "sb.value.friends", Placeholder.unparsed("online", "32"),
                Placeholder.unparsed("total", "495"));
        assertEquals("32/495", CatalogHarness.plain(friends));
        Component hours = MessageRenderer.renderPlural(DE, "sb.value.playtime", 1, Placeholder.unparsed("n", "1"));
        assertEquals("1 Stunde", CatalogHarness.plain(hours));
    }

    @Test
    void namenslisteDerBooster() {
        // Spec 02 §9 #4: 1/2/3/7 Booster -> "A" / "A, B" / "A, B und 1 weiteren" / "A, B und 5 weiteren".
        TagResolver names = TagResolver.resolver(
                Placeholder.component("first", Component.text("A", NamedTextColor.AQUA)),
                Placeholder.component("second", Component.text("B", NamedTextColor.AQUA)));
        assertEquals("A", CatalogHarness.plain(MessageRenderer.render(DE, "coins.names.single", names)));
        assertEquals("A, B", CatalogHarness.plain(MessageRenderer.render(DE, "coins.names.pair", names)));
        assertEquals("A, B und 1 weiteren", CatalogHarness.plain(
                MessageRenderer.render(DE, "coins.names.more", names, Placeholder.unparsed("n", "1"))));
        assertEquals("A, B und 5 weiteren", CatalogHarness.plain(
                MessageRenderer.render(DE, "coins.names.more", names, Placeholder.unparsed("n", "5"))));
    }

    @Test
    void freundeslisteZeigtOnlineOderOffline() {
        TagResolver name = Placeholder.component("name", Component.text("Alex", NamedTextColor.GREEN));
        Component online = MessageRenderer.render(DE, "friend.list.line", name,
                Placeholder.unparsed("server", "lobby-3"), Formatter.booleanChoice("online", true));
        assertSameAsLegacy("§8» §aAlex §7– §aOnline auf §elobby-3", online);
        Component offline = MessageRenderer.render(DE, "friend.list.line", name,
                Formatter.booleanChoice("online", false));
        assertSameAsLegacy("§8» §aAlex §7– §cOffline", offline);
    }

    @Test
    void klickzeileMitMehrerenKnoepfen() {
        Component accept = MessageRenderer.render(DE, "click.accept")
                .clickEvent(ClickEvent.runCommand("/friend accept Alex"));
        Component deny = MessageRenderer.render(DE, "click.deny");
        Component line = MessageRenderer.render(DE, "click.line",
                Placeholder.component("prefix", MessageRenderer.deserialize(Messages.raw(DE, "prefix.friends"))),
                Formatter.joining("buttons", accept, deny));
        assertSameAsLegacy("§7[§cFreunde§7] §7Klicke hier zum: §a[ANNEHMEN] §c[ABLEHNEN]", line);
    }

    @Test
    void resetKnoepfeHabenBefehl() {
        Component yes = MessageRenderer.render(DE, "reset.button.yes");
        assertEquals("[Ja]", CatalogHarness.plain(yes));
        assertTrue(containsClick(yes, "/resetstats confirm"));
        assertTrue(containsClick(MessageRenderer.render(DE, "reset.button.no"), "/resetstats cancel"));
    }

    @Test
    void schildHatVierZeilen() {
        List<Component> lines = MessageRenderer.renderLines(DE, "sign.top", Placeholder.unparsed("pos", "2"),
                Placeholder.unparsed("name", "Steve"), Placeholder.unparsed("points", "1.000"),
                Placeholder.unparsed("wins", "37"));
        assertEquals(4, lines.size());
        assertSameAsLegacy("§l#2", lines.get(0));
        assertSameAsLegacy("Steve", lines.get(1));
        assertSameAsLegacy("§e1.000 Punkte", lines.get(2));
        assertSameAsLegacy("§737 Siege", lines.get(3));
    }

    @Test
    void respawnTitelInEinzahl() {
        Component one = MessageRenderer.renderPlural(DE, "title.respawn", 1, Placeholder.unparsed("n", "1"));
        assertSameAsLegacy("§cRespawn in §e1 §cSekunde", one);
        Component five = MessageRenderer.renderPlural(Locale.US, "title.respawn", 5, Placeholder.unparsed("n", "5"));
        assertSameAsLegacy("§cRespawn in §e5 §cseconds", five);
    }

    @Test
    void praefixUndFarbPlatzhalter() {
        Component tab = MessageRenderer.render(DE, "tab.header.mode",
                Placeholder.styling("mode_color", NamedTextColor.RED),
                Placeholder.unparsed("server_display", "BedWars"));
        assertEquals(BuildInfo.BRAND_NAME + ".net BedWars", CatalogHarness.plain(tab));
        Component legacy = MessageRenderer.render(DE, UiEra.LEGACY, "friend.online",
                Placeholder.component("name", Component.text("Alex", NamedTextColor.GREEN)));
        assertSameAsLegacy("§7[§4Freunde§7] §aAlex §7ist jetzt §aonline", legacy);
    }

    private static boolean containsClick(Component component, String command) {
        if (component.clickEvent() != null && component.clickEvent().toString().contains(command)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> containsClick(child, command));
    }

    private static void assertSameAsLegacy(String expected, Component actual) {
        String difference = CatalogHarness.difference(CatalogHarness.flatten(CatalogHarness.legacy(expected)),
                CatalogHarness.flatten(actual));
        assertEquals("", difference);
    }
}
