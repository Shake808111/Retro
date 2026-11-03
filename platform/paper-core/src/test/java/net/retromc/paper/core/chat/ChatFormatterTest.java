package net.retromc.paper.core.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import org.junit.jupiter.api.Test;

/** Abnahme Spec 02 §9 #16 (Formatter ohne Server) und die übrigen Bausteine aus §6.1. */
class ChatFormatterTest {

    static final I18n I18N = new CatalogI18n(player -> Optional.empty());

    static ChatFormatter.Line line(ChatChannel channel, ChatFormatter.ClanPart clan, String tag, Rank rank,
            String message, boolean waiting) {
        return new ChatFormatter.Line(channel, null, null, clan, tag, "Name", RankDisplay.of(rank), null,
                Component.text(message), waiting);
    }

    static String legacy(ChatFormatter.Line line, String message) {
        return LegacyComponentSerializer.legacySection().serialize(ChatFormatter.format(I18N, Locale.GERMANY, line,
                message));
    }

    static String plain(ChatFormatter.Line line, String message) {
        return PlainTextComponentSerializer.plainText().serialize(ChatFormatter.format(I18N, Locale.GERMANY, line,
                message));
    }

    @Test
    void abnahme16ClanTagRangfarbeOhnePraefix() {
        assertEquals("§7[§eABC§7] §6Name§7: §fhi", legacy(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.TAG,
                "ABC", Rank.PREMIUM, "hi", false), "hi"));
        // Lobby: ohne Clan-Tag
        assertEquals("§6Name§7: §fhi", legacy(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.NONE, "ABC",
                Rank.PREMIUM, "hi", false), "hi"));
        // Spielserver ohne Clan
        assertEquals("§7[-] §aName§7: §fhi", legacy(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.DASH, null,
                Rank.DEFAULT, "hi", false), "hi"));
        // kein Rang-Präfix im Chat, Admin §4
        assertEquals("§4Name§7: §fhi", legacy(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.NONE, null,
                Rank.ADMIN, "hi", false), "hi"));
    }

    @Test
    void abnahme16VerschleiertesGgAbSupremium() {
        String gg = legacy(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.NONE, null, Rank.SUPREMIUM, "gg", false),
                "gg");
        assertTrue(gg.startsWith("§bName§7: §k--§f "), gg);
        assertTrue(gg.contains("§6GG"), gg);
        assertTrue(gg.endsWith("§7§k--"), gg);
        assertEquals("Name: -- GG --", plain(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.NONE, null,
                Rank.SUPREMIUM, "GgWp", false), "GgWp").replace("GGWP", "GG"));
        // Premium darf kein farbiges GG
        assertEquals("§6Name§7: §fgg", legacy(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.NONE, null,
                Rank.PREMIUM, "gg", false), "gg"));
        // gl nur in der Wartezeit, ganze Nachricht nötig
        assertEquals(Optional.empty(), ChatFormatter.coloredGg("gl", true, false));
        assertEquals(Optional.of("GL"), ChatFormatter.coloredGg("gl", true, true));
        assertEquals(Optional.empty(), ChatFormatter.coloredGg("gg ez", true, true));
        assertEquals(Optional.of("GEGE"), ChatFormatter.coloredGg(" GeGe ", true, false));
        assertEquals(Optional.of("ND"), ChatFormatter.coloredGg("nd", true, false));
    }

    @Test
    void kanalpraefixeTeamfarbeUndLevel() {
        assertEquals("[@all] Name: hi", plain(line(ChatChannel.ALL, ChatFormatter.ClanPart.NONE, null, Rank.DEFAULT,
                "hi", false), "hi"));
        assertEquals("[OFF] Name: hi", plain(line(ChatChannel.SPECTATOR, ChatFormatter.ClanPart.NONE, null,
                Rank.DEFAULT, "hi", false), "hi"));
        ChatFormatter.Line team = new ChatFormatter.Line(ChatChannel.TEAM, 12, Component.text("[7] "),
                ChatFormatter.ClanPart.TAG, "ABC", "Name", RankDisplay.of(Rank.ADMIN), NamedTextColor.BLUE,
                Component.text("hi"), false);
        String text = legacy(team, "hi");
        assertTrue(text.contains("§9Name"), text);
        assertEquals("12 [7] [ABC] Name: hi", plain(team, "hi"));
    }

    @Test
    void verlaufFuerSupremiumPlus() {
        Component name = ChatFormatter.name(line(ChatChannel.GLOBAL, ChatFormatter.ClanPart.NONE, null,
                Rank.SUPREMIUM_PLUS, "hi", false));
        assertEquals(4, name.children().size());
        assertEquals(0xFF55FF, name.children().getFirst().color().value());
        assertEquals(0x55FFFF, name.children().getLast().color().value());
    }

    @Test
    void nickErscheintAlsPremium() {
        assertEquals(RankDisplay.of(Rank.PREMIUM), RankDisplay.nicked());
        assertEquals("§6", RankDisplay.nicked().color());
    }
}
