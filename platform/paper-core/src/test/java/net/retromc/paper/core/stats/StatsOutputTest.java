package net.retromc.paper.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.StatsMode.Metric;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.stats.RankingScore;
import net.retromc.core.stats.StatsReport;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import org.junit.jupiter.api.Test;

/** Abnahme Spec 02 §9 #5, #6 (Golden-File {@code /stats}) sowie Bestenliste, Rundenblock, Hologramm und Argumente. */
class StatsOutputTest {

    static final I18n I18N = new CatalogI18n(player -> Optional.empty());
    static final Locale DE = Locale.GERMANY;
    static final StatsMode BEDWARS = new StatsMode(1, ModeKey.BEDWARS, "mode.bedwars", "red", null, true,
            Metric.POINTS, Map.of("kills", 5L, "beds", 10L, "wins", 50L),
            List.of("rank", "points", "kills", "deaths", "kd", "games", "wins", "winrate", "beds"), true, true,
            "RED_BED");
    static final StatsMode BINGO = new StatsMode(11, ModeKey.BINGO_X1, "mode.bingo_x1", "red", null, true,
            Metric.TIME, Map.of(), List.of("rank", "best_time", "games", "wins"), false, true, "FILLED_MAP");
    static final StatLine LINE = new StatLine(UUID.randomUUID(), ModeKey.BEDWARS, 314, 28, 165, 140, 10_560,
            Map.of("beds", 199L));

    /** Community-Regexe aus §9 #5. */
    static final Pattern HEADER = Pattern.compile(".*statistiken von\\s+(?<p>[^\\(]+?)\\s*\\((?<period>[^\\)]+)\\).*",
            Pattern.CASE_INSENSITIVE);
    static final Pattern ROW = Pattern.compile("^\\s*(?<key>[^:]+)\\s*:\\s*(?<val>.+?)\\s*$");
    static final Pattern FOOTER = Pattern.compile("^-{5,}$");

    static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    @Test
    void abnahme5StatsBlockPasstAufDieCommunityRegexe() {
        List<Component> block = StatsTexts.statsBlock(I18N, DE, "Alpha", StatWindow.THIRTY_DAYS,
                StatsReport.of(BEDWARS, LINE, OptionalLong.of(3)));
        List<String> lines = block.stream().map(StatsOutputTest::plain).toList();
        assertEquals(List.of(
                "-= Statistiken von Alpha (30 Tage) =-",
                " Position im Ranking: 3",
                " Punkte: 10.560",
                " Kills: 314",
                " Deaths: 28",
                " K/D: 11.21",
                " Gespielte Spiele: 165",
                " Gewonnene Spiele: 140",
                " Siegesquote: 84.85 %",
                " Zerstörte Betten: 199",
                "----------------------"), lines);
        Matcher header = HEADER.matcher(lines.getFirst());
        assertTrue(header.matches());
        assertEquals("Alpha", header.group("p"));
        assertTrue(header.group("period").contains("30"));
        for (String row : lines.subList(1, lines.size() - 1)) {
            assertTrue(ROW.matcher(row).matches(), row);
        }
        assertTrue(FOOTER.matcher(lines.getLast()).matches());

        List<String> all = StatsTexts.statsBlock(I18N, DE, "Alpha", StatWindow.ALL,
                StatsReport.of(BEDWARS, LINE, OptionalLong.empty())).stream().map(StatsOutputTest::plain).toList();
        Matcher allHeader = HEADER.matcher(all.getFirst());
        assertTrue(allHeader.matches());
        assertTrue(allHeader.group("period").contains("insgesamt"));
        assertEquals(" Position im Ranking: -", all.get(1));
    }

    @Test
    void abnahme6FussZeilenanfangUndNamensfarbe() {
        List<Component> block = StatsTexts.statsBlock(I18N, DE, "Alpha", StatWindow.THIRTY_DAYS,
                StatsReport.of(BEDWARS, LINE, OptionalLong.of(3)));
        assertEquals("-".repeat(22), plain(block.getLast()));
        assertEquals("§7----------------------", legacy(block.getLast()));
        for (Component row : block.subList(1, block.size() - 1)) {
            String text = plain(row);
            assertTrue(text.startsWith(" ") && !text.startsWith("  "), text);
        }
        String header = legacy(block.getFirst());
        assertTrue(header.contains("§6Alpha"), header);
        assertTrue(header.startsWith("§7-= §eStatistiken von §6Alpha §7(30 Tage) =-"), header);
        assertEquals("§7 Punkte: §e10.560", legacy(block.get(2)));
    }

    @Test
    void zeitraeumeVonStatsd() {
        assertEquals("3 Tage", StatsTexts.period(DE, StatWindow.lastDays(3)));
        assertEquals("1 Tag", StatsTexts.period(DE, StatWindow.lastDays(1)));
        assertEquals("30 Tage", StatsTexts.period(DE, StatWindow.lastDays(30)));
        assertEquals("insgesamt", StatsTexts.period(DE, StatWindow.ALL));
        assertEquals("total", StatsTexts.period(Locale.US, StatWindow.ALL));
    }

    @Test
    void englischeLabels() {
        List<String> lines = StatsTexts.statsBlock(I18N, Locale.US, "Alpha", StatWindow.THIRTY_DAYS,
                StatsReport.of(BEDWARS, LINE, OptionalLong.of(3))).stream().map(StatsOutputTest::plain).toList();
        assertTrue(HEADER.matcher(lines.getFirst().replace("Statistics of", "Statistiken von")).matches());
        assertEquals(" Siegesquote: 84.85 %".replace("Siegesquote", "Win rate"), lines.get(8));
    }

    @Test
    void bestenlisteMitEigenemPlatz() {
        List<TopEntry> top = List.of(new TopEntry(1, UUID.randomUUID(), "Erster", "§6",
                RankingScore.points(10_560, 140)), new TopEntry(2, UUID.randomUUID(), "Zweiter", "§a",
                RankingScore.points(351, 33)));
        List<String> lines = StatsTexts.topBlock(I18N, DE, BEDWARS, StatPeriod.DAYS_30, top, OptionalLong.empty())
                .stream().map(StatsOutputTest::plain).toList();
        assertEquals(List.of("-= Top 10 in BedWars (30 Tage) =-", " #1 Erster - 10.560 Punkte",
                " #2 Zweiter - 351 Punkte", " Dein Platz: -", "----------------------"), lines);
        List<String> times = StatsTexts.topBlock(I18N, DE, BINGO, StatPeriod.ALL, List.of(new TopEntry(1,
                UUID.randomUUID(), "Schnell", "§a", -754_000)), OptionalLong.of(1)).stream()
                .map(StatsOutputTest::plain).toList();
        assertEquals(" #1 Schnell - 12:34", times.get(1));
        assertEquals(" Dein Platz: 1", times.get(2));
    }

    @Test
    void rundenblock() {
        RoundSummary summary = new RoundSummary(List.of(new RoundSummary.Line("stats.label.kills", "3"),
                new RoundSummary.Line("stats.label.beds", "1")), 120, true, false, true);
        List<Component> block = StatsTexts.roundBlock(I18N, DE, summary);
        assertEquals(List.of("-= Statistiken dieser Runde =-", " Kills: 3", " Zerstörte Betten: 1",
                " Gesammelte Erfahrung: 120 XP", "", " Überlebt: ✓", " Gewonnen: ✗", "----------------------"),
                block.stream().map(StatsOutputTest::plain).toList());
        assertEquals("§8----------------------", legacy(block.getLast()));
        assertEquals("§7 Überlebt: §a✓", legacy(block.get(5)));
    }

    @Test
    void hologrammeImHologrammFormat() {
        StatLine line = new StatLine(UUID.randomUUID(), ModeKey.BEDWARS, 4, 3, 58, 16, 1_000, Map.of());
        List<String> personal = StatsTexts.personalHologram(I18N, DE, BEDWARS, StatPeriod.ALL,
                StatsReport.of(BEDWARS, line, OptionalLong.empty())).stream().map(StatsOutputTest::plain).toList();
        assertEquals("BedWars Statistiken", personal.getFirst());
        assertEquals("Deine All-Time Statistiken", personal.get(1));
        assertTrue(personal.contains("Siegesquote 27,59%"));
        assertTrue(personal.contains("K/D 1.33"));
        assertEquals("▬▬▬▬▬▬▬▬▬▬", personal.getLast());
        List<String> top = StatsTexts.topHologram(I18N, DE, BEDWARS, StatPeriod.DAYS_30, List.of(new TopEntry(1,
                UUID.randomUUID(), "Erster", "§6", RankingScore.points(10_560, 1)))).stream()
                .map(StatsOutputTest::plain).toList();
        assertEquals(List.of("BedWars Bestenliste", "30 Tage", "#1 Erster - 10.560"), top);
        List<String> sign = StatsTexts.sign(I18N, DE, BEDWARS, new TopEntry(2, UUID.randomUUID(), "Name", "§a",
                RankingScore.points(1_000, 12))).stream().map(StatsOutputTest::plain).toList();
        assertEquals(List.of("#2", "Name", "1.000 Punkte", "12 Siege"), sign);
    }

    @Test
    void argumenteMitUndOhneServerModus() {
        ModeCatalog catalog = ModeCatalog.of(List.of(BEDWARS, BINGO));
        StatsArgs.Parsed own = StatsArgs.stats("", StatWindow.THIRTY_DAYS, Optional.of(ModeKey.BEDWARS),
                catalog::find);
        assertEquals(new StatsArgs.Request(ModeKey.BEDWARS, StatWindow.THIRTY_DAYS, ""), own.request());
        StatsArgs.Parsed rank = StatsArgs.stats("#3", StatWindow.ALL, Optional.of(ModeKey.BEDWARS), catalog::find);
        assertEquals(3, rank.request().rank().orElseThrow());
        // Ohne Server-Modus und ohne Argument: Profil-GUI (Spieler), Konsole bekommt stats.need-mode.
        StatsArgs.Parsed profile = StatsArgs.stats("", StatWindow.ALL, Optional.empty(), catalog::find);
        assertTrue(profile.profile());
        assertNull(profile.request());
        assertEquals("stats.need-mode", profile.error());
        assertFalse(own.profile());
        assertTrue(StatsArgs.days("", Optional.empty(), catalog::find).error().equals("stats.need-mode"));
        assertFalse(StatsArgs.days("", Optional.empty(), catalog::find).profile());
        StatsArgs.Parsed unknown = StatsArgs.stats("quake Alpha", StatWindow.ALL, Optional.empty(), catalog::find);
        assertEquals("stats.unknown-mode", unknown.error());
        assertEquals("quake", unknown.argument());
        StatsArgs.Parsed byName = StatsArgs.stats("BedWars Alpha", StatWindow.ALL, Optional.empty(), catalog::find);
        assertEquals(new StatsArgs.Request(ModeKey.BEDWARS, StatWindow.ALL, "Alpha"), byName.request());
        assertEquals(StatWindow.lastDays(5), StatsArgs.days("5 Alpha", Optional.of(ModeKey.BEDWARS), catalog::find)
                .request().window());
        assertEquals("stats.days-invalid", StatsArgs.days("31", Optional.of(ModeKey.BEDWARS), catalog::find).error());
        assertEquals("stats.days-invalid", StatsArgs.days("0", Optional.of(ModeKey.BEDWARS), catalog::find).error());
        assertEquals("stats.days-invalid", StatsArgs.days("x", Optional.of(ModeKey.BEDWARS), catalog::find).error());
        assertEquals(new StatsArgs.Request(ModeKey.BINGO_X1, StatWindow.lastDays(3), "Beta"),
                StatsArgs.days("bingo_x1 3 Beta", Optional.empty(), catalog::find).request());
        assertNull(StatsArgs.stats("#0", StatWindow.ALL, Optional.of(ModeKey.BEDWARS), catalog::find).error());
        assertFalse(new StatsArgs.Request(ModeKey.BEDWARS, StatWindow.ALL, "#0").rank().isPresent());
    }

    @Test
    void serverModusAusTemplate() {
        ModeCatalog catalog = ModeCatalog.of(List.of(BEDWARS, BINGO));
        assertEquals(new ServerMode(Optional.of(ModeKey.BEDWARS), false), ServerMode.resolve(
                TemplateId.of("bedwars-4x4"), ServerCategory.GAME, key -> catalog.mode(key).isPresent()));
        assertEquals(new ServerMode(Optional.of(ModeKey.BINGO_X1), false), ServerMode.resolve(
                TemplateId.of("bingo-x1"), ServerCategory.GAME, key -> catalog.mode(key).isPresent()));
        assertEquals(new ServerMode(Optional.of(ModeKey.BEDWARS), true), ServerMode.resolve(
                TemplateId.of("lobby-bedwars"), ServerCategory.LOBBY, key -> catalog.mode(key).isPresent()));
        assertEquals(ServerMode.NONE, ServerMode.resolve(TemplateId.LOBBY, ServerCategory.LOBBY,
                key -> catalog.mode(key).isPresent()));
        assertEquals(ServerMode.NONE, ServerMode.resolve(TemplateId.of("cb-1"), ServerCategory.PERSISTENT,
                key -> true));
    }
}
