package net.retromc.paper.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import org.junit.jupiter.api.Test;

/** Abnahme Spec 02 §9 #13 (Layout), #17 (Zeilenlänge) und #18 (englische Labels) ohne Server. */
class LobbyBoardTest {

    static final I18n I18N = new CatalogI18n(player -> Optional.empty());
    static final LobbyBoard.State STATE = new LobbyBoard.State(true, RankDisplay.of(Rank.ADMIN), 455 * 3_600 + 59,
            1_355_470, 4_950, new OnlineCount(7, 29), "CLAN", new OnlineCount(2, 75));

    static List<String> plain(List<Component> lines) {
        return lines.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    static String legacy(Component line) {
        return LegacyComponentSerializer.legacySection().serialize(line);
    }

    @Test
    void abnahme13FuenfzehnZeilenInReihenfolge() {
        AtomicBoolean rank = new AtomicBoolean(true);
        BoardSpec spec = LobbyBoard.spec(I18N, () -> Locale.GERMANY, () -> STATE, rank::get);
        assertEquals(15, spec.lines().size());
        assertEquals(LobbyBoard.LINE_IDS, spec.lines().stream().map(BoardSpec.Line::id).toList());
        assertTrue(spec.showScores());
        List<Component> lines = spec.render();
        assertEquals(List.of("", "Rang", "Administrator", "", "Coins", "1.355.470", "", "Gold", "4.950", "", "Clan",
                "7/29 [CLAN]", "", "Freunde", "2/75"), plain(lines));
        assertEquals("§f§l" + BuildInfo.BRAND_NAME.toUpperCase(Locale.ROOT) + ".NET", legacy(spec.title().get()));
        assertEquals("§4Administrator", legacy(lines.get(2)));
        assertEquals("§e1.355.470", legacy(lines.get(5)));
        assertEquals("§64.950", legacy(lines.get(8)));
        // §7 vor "[" entfällt, weil das Leerzeichen schon grau ist
        assertEquals("§b7§7/29 [§bCLAN§7]", legacy(lines.get(11)));
        assertEquals("§a2§7/75", legacy(lines.get(14)));
        assertEquals("§fCoins", legacy(lines.get(4)));
        rank.set(false);
        List<String> playtime = plain(spec.render());
        assertEquals("Spielzeit", playtime.get(1));
        assertEquals("455 Stunden", playtime.get(2));
        assertEquals("§6455 Stunden", legacy(spec.render().get(2)));
        assertEquals(LobbyBoard.TOGGLE_TICKS, 100);
    }

    @Test
    void ohneClanEineStundeSpielerUndLadezustand() {
        LobbyBoard.State fresh = new LobbyBoard.State(true, RankDisplay.of(Rank.DEFAULT), 3_600, 0, 0, null, null,
                new OnlineCount(0, 0));
        List<Component> lines = LobbyBoard.spec(I18N, () -> Locale.GERMANY, () -> fresh, () -> true).render();
        assertEquals("§aSpieler", legacy(lines.get(2)));
        assertEquals("§7-", legacy(lines.get(11)));
        assertEquals("1 Stunde", plain(LobbyBoard.spec(I18N, () -> Locale.GERMANY, () -> fresh, () -> false)
                .render()).get(2));
        LobbyBoard.State loading = new LobbyBoard.State(false, RankDisplay.of(Rank.DEFAULT), 0, 0, 0, null, null,
                new OnlineCount(0, 0));
        List<Component> waiting = LobbyBoard.spec(I18N, () -> Locale.GERMANY, () -> loading, () -> true).render();
        for (int index : List.of(2, 5, 8, 11, 14)) {
            assertEquals("§70", legacy(waiting.get(index)));
        }
    }

    @Test
    void abnahme17KeineZeileUeber40Zeichen() {
        List<String> lines = plain(LobbyBoard.spec(I18N, () -> Locale.GERMANY, () -> STATE, () -> true).render());
        lines.forEach(line -> assertTrue(line.length() <= 40, line));
        String title = PlainTextComponentSerializer.plainText().serialize(LobbyBoard.title(I18N, Locale.GERMANY));
        assertTrue(title.length() <= 32, title);
    }

    @Test
    void abnahme18EnglischeLabels() {
        List<String> lines = plain(LobbyBoard.spec(I18N, () -> Locale.US, () -> STATE, () -> true).render());
        assertEquals("Rank", lines.get(1));
        assertEquals("Friends", lines.get(13));
        assertEquals("1,355,470", lines.get(5));
    }

    @Test
    void boardSpecPrueftGrenzen() {
        BoardSpec.Builder builder = BoardSpec.builder();
        for (int i = 0; i < 16; i++) {
            builder.blank();
        }
        assertThrows(IllegalArgumentException.class, builder::build);
        assertThrows(IllegalArgumentException.class, () -> BoardSpec.builder().line("a", Component::empty)
                .line("a", Component::empty).build());
        BoardSpec spec = BoardSpec.builder().line("map", () -> Component.text("Map")).showScores(false).build();
        assertEquals(0, spec.index("map"));
        assertEquals(-1, spec.index("x"));
    }
}
