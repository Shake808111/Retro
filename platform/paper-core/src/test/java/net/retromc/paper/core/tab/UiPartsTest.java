package net.retromc.paper.core.tab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.i18n.Messages;
import net.retromc.core.i18n.UiEra;
import net.retromc.paper.core.bar.ActionbarQueue;
import net.retromc.paper.core.bar.ActionbarService;
import net.retromc.paper.core.bar.BarFeature;
import net.retromc.paper.core.bar.BossBarSpec;
import net.retromc.paper.core.bar.BossbarService;
import net.retromc.paper.core.bar.NewsItem;
import net.retromc.paper.core.holo.WallLayout;
import net.retromc.paper.core.hotbar.HotbarLayout;
import net.retromc.platform.common.text.CatalogI18n;
import net.retromc.platform.common.text.I18n;
import org.junit.jupiter.api.Test;

/**
 * Tablist (§4.4, §6.2, §9 #15), Bossbar/Actionbar (§4.5, §4.6), Schilderwand (§4.9), Hotbar-Namen (§4.10) und die
 * Abnahmen §9 #19 (Schlüssel im Code vorhanden) und #20 (Klicks in GUIs gesperrt) ohne Server.
 */
class UiPartsTest {

    static final I18n I18N = new CatalogI18n(player -> Optional.empty());

    /** Legacy-Text; {@code §r} zwischen Geschwistern ist für die Darstellung ohne Bedeutung. */
    static String legacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component).replace("§r", "");
    }

    static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void headerUndFooter() {
        ServerIdentity bedwars = new ServerIdentity("BedWars", "§c");
        String header = legacy(TabFormat.header(I18N, Locale.GERMANY, bedwars, false));
        assertEquals("§e" + BuildInfo.BRAND_NAME + ".net §cBedWars\n§7Regelverstoß gesehen? §c/report §7<Spieler>",
                header);
        assertTrue(plain(TabFormat.header(I18N, Locale.GERMANY, bedwars, true)).startsWith(TabFormat.BANNER_GLYPH
                + "\n"));
        assertEquals("§7Lade Freunde ein, starte Partys oder gründe\n§7deinen eigenen Clan: §a/friend§7, §d/party "
                + "§7und §e/clan", legacy(TabFormat.footer(I18N, Locale.GERMANY, null, "lobby-1")));
        assertEquals("Du bist auf Server cb-1\nBooster: 2", plain(TabFormat.footer(I18N, Locale.GERMANY,
                List.of(Component.text("Booster: 2")), "cb-1")));
    }

    @Test
    void eintraegeLobbyUndSpiel() {
        assertEquals("§4Admin §7| §4Name §7[§eABC§7]", legacy(TabFormat.lobbyEntry(I18N, Locale.GERMANY,
                RankDisplay.of(Rank.ADMIN), "Name", "ABC")));
        assertEquals("§6Name", legacy(TabFormat.lobbyEntry(I18N, Locale.GERMANY, RankDisplay.of(Rank.PREMIUM),
                "Name", null)));
        assertEquals("§bSuprem §7| §bName", legacy(TabFormat.lobbyEntry(I18N, Locale.GERMANY,
                RankDisplay.of(Rank.SUPREMIUM), "Name", null)));
        assertEquals("§aGrün §7| §fName §7[§eABC§7]", legacy(TabFormat.teamEntry(I18N, Locale.GERMANY,
                NamedTextColor.GREEN, "Grün", "Name", "ABC")));
    }

    @Test
    void abnahme15SortierungNachGewichtDannAlphabetisch() {
        record Entry(String name, Rank rank) {
        }
        List<Entry> entries = new ArrayList<>(List.of(new Entry("zed", Rank.DEFAULT), new Entry("Bob", Rank.PREMIUM),
                new Entry("anna", Rank.SUPREMIUM), new Entry("Mo", Rank.MOD), new Entry("Dev", Rank.DEV),
                new Entry("Root", Rank.ADMIN), new Entry("adam", Rank.DEFAULT)));
        // Client: höherer setPlayerListOrder oben, gleiches Gewicht alphabetisch
        entries.sort(Comparator.comparingInt((Entry entry) -> -RankDisplay.of(entry.rank()).weight())
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        assertEquals(List.of("Root", "Dev", "Mo", "anna", "Bob", "adam", "zed"), entries.stream().map(Entry::name)
                .toList());
        // ältere Clients: Teamname "%04d%s" mit 9999 − Gewicht sortiert genauso
        List<String> teams = entries.stream().map(entry -> NametagService.teamName(RankDisplay.of(entry.rank())
                .weight(), entry.name())).toList();
        assertEquals("9099Root", teams.getFirst());
        assertEquals("9999zed", teams.getLast());
        List<String> sorted = new ArrayList<>(teams);
        sorted.sort(Comparator.comparing((String team) -> team.substring(0, 4)));
        assertEquals(teams, sorted);
    }

    @Test
    void serverAnzeigeUndModusfarbe() {
        StatsMode bedwars = new StatsMode(1, ModeKey.BEDWARS, "mode.bedwars", "red", null, true,
                StatsMode.Metric.POINTS, Map.of(), List.of(), true, true, "RED_BED");
        StatsMode duels = new StatsMode(14, ModeKey.DUELS, "mode.duels", "blue", null, false,
                StatsMode.Metric.NONE, Map.of(), List.of(), true, true, "DIAMOND_SWORD");
        assertEquals(new ServerIdentity("Lobby-1", "§a"), ServerIdentity.of("lobby", TemplateId.LOBBY,
                ServerCategory.LOBBY, Optional.empty(), mode -> ""));
        assertEquals(new ServerIdentity("Lobby-3", "§a"), ServerIdentity.of("lobby-3", TemplateId.LOBBY,
                ServerCategory.LOBBY, Optional.empty(), mode -> ""));
        assertEquals(new ServerIdentity("Silent-Lobby-1", "§c"), ServerIdentity.of("silentlobby-1",
                TemplateId.SILENT_LOBBY, ServerCategory.LOBBY, Optional.empty(), mode -> ""));
        assertEquals(new ServerIdentity("BedWars", "§c"), ServerIdentity.of("bedwars-4x4-2",
                TemplateId.of("bedwars-4x4"), ServerCategory.GAME, Optional.of(bedwars), mode -> "BedWars"));
        assertEquals(new ServerIdentity("Duels", "§9"), ServerIdentity.of("duels-1", TemplateId.of("duels"),
                ServerCategory.GAME, Optional.of(duels), mode -> "Duels"));
        assertEquals(new ServerIdentity("CityBuild", "§6"), ServerIdentity.of("cb-1", TemplateId.of("cb"),
                ServerCategory.PERSISTENT, Optional.empty(), mode -> ""));
        assertEquals(new ServerIdentity("SMP", "§d"), ServerIdentity.of("smp-1", TemplateId.of("smp"),
                ServerCategory.PERSISTENT, Optional.empty(), mode -> ""));
    }

    @Test
    void actionbarPrioritaetUndAblauf() {
        ActionbarQueue queue = new ActionbarQueue();
        assertTrue(queue.current(0).isEmpty());
        queue.set("ticker", ActionbarService.Priority.TICKER, Component.text("news"), 0, 0);
        queue.set("team", ActionbarService.Priority.PERSISTENT, Component.text("Team Rot"), 0, 0);
        assertEquals("Team Rot", plain(queue.current(10).orElseThrow()));
        queue.set("kit", ActionbarService.Priority.COOLDOWN, Component.text("Kit"), 10, 0);
        queue.set("hit", ActionbarService.Priority.TEMPORARY, Component.text("Treffer"), 10, 40);
        assertEquals("Treffer", plain(queue.current(49).orElseThrow()));
        assertEquals("Kit", plain(queue.current(50).orElseThrow()));
        queue.clear("kit");
        queue.clear("team");
        assertEquals("news", plain(queue.current(60).orElseThrow()));
        assertEquals(40, ActionbarService.RESEND_TICKS);
    }

    @Test
    void bossbarKanaeleUndFormate() {
        assertEquals(Optional.of("game"), BossbarService.active(Set.of("news", "season", "game")));
        assertEquals(Optional.of("event"), BossbarService.active(Set.of("news", "event")));
        assertEquals(Optional.of("season"), BossbarService.active(Set.of("season", "news")));
        assertEquals(Optional.empty(), BossbarService.active(Set.of()));
        BossBarSpec season = BossBarSpec.season(I18N, Locale.GERMANY, YearMonth.of(2026, 7), YearMonth.of(2026, 9));
        assertEquals("§cSeason: §eJul-Sep 2026", legacy(season.title()));
        assertEquals(BossBar.Color.BLUE, season.color());
        assertEquals(1f, season.progress());
        BossBarSpec tutorial = BossBarSpec.tutorial(I18N, Locale.GERMANY, 2, 4, Component.text("Springe"));
        assertEquals("Tutorial (2/4): Springe /tutorial", plain(tutorial.title()));
        assertEquals(0.5f, tutorial.progress());
        assertEquals(BossBar.Color.GREEN, tutorial.color());
        assertEquals("§e§l2 NEW CORES MAPS", legacy(BossBarSpec.news(I18N, Locale.GERMANY, "2 new cores maps")
                .title()));
        // Spec 03 §4.4: Lobby Ära 18 zeigt die News als Bossbar, Ära 26 in der Actionbar.
        assertTrue(BarFeature.newsInBossbar(UiEra.LEGACY));
        assertFalse(BarFeature.newsInBossbar(UiEra.MODERN));
    }

    @Test
    void newstickerAusUiNews() {
        List<NewsItem> items = NewsItem.parseAll(List.of("{\"color\":\"§a\",\"title\":\"Neue Map\",\"url\":"
                + "\"retromc.net/news\"}", "kaputt", "{\"title\":\"ohne Farbe\"}"));
        assertEquals(1, items.size());
        assertEquals("§e+ §a§lNEUE MAP: §fretromc.net/news", legacy(items.getFirst().render(I18N, Locale.GERMANY)));
        assertEquals(160, NewsItem.ROTATE_TICKS);
    }

    @Test
    void schilderwandNachRechts() {
        assertEquals(new WallLayout.Offset(-1, 0, 0), WallLayout.step("NORTH"));
        assertEquals(new WallLayout.Offset(1, 0, 0), WallLayout.step("south"));
        assertEquals(new WallLayout.Offset(0, 0, -1), WallLayout.step("EAST"));
        assertEquals(new WallLayout.Offset(0, 0, 1), WallLayout.step("WEST"));
        List<WallLayout.Offset> signs = WallLayout.signs("SOUTH", 10);
        assertEquals(10, signs.size());
        assertEquals(new WallLayout.Offset(9, 0, 0), signs.getLast());
        assertEquals(new WallLayout.Offset(9, 1, 0), WallLayout.head(signs.getLast()));
        assertThrows(IllegalArgumentException.class, () -> WallLayout.step("UP"));
    }

    @Test
    void hotbarNamenskonvention() {
        assertEquals("§aNavigator §7(Rechtsklick)", legacy(HotbarLayout.name(I18N, Locale.GERMANY, "§a",
                "Navigator")));
    }

    /** Schlüssel-Literale im Code mit bekannten Präfixen müssen im Katalog stehen (§9 #19, „keine verwaisten“). */
    @Test
    void abnahme19JederSchluesselImCodeExistiert() throws IOException {
        Pattern literal = Pattern.compile("\"((?:stats|sb|tab|chat|coins|reset|top|holo|gui\\.stats|title|bar"
                + "|actionbar|togglestats|statsadmin|statswall|tokens|round|prefix|item|lang|sign|error\\.data)"
                + "\\.[a-z0-9_.-]*[a-z0-9])\"");
        Set<String> missing = new TreeSet<>();
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = literal.matcher(Files.readString(file));
                while (matcher.find()) {
                    found.add(matcher.group(1));
                    if (!Messages.has(matcher.group(1))) {
                        missing.add(file.getFileName() + ": " + matcher.group(1));
                    }
                }
            }
        }
        assertTrue(found.size() > 60, "zu wenige Schlüssel gefunden: " + found.size());
        assertEquals(Set.of(), missing);
    }

    /** GUIs sperren Shift-Klick und Ziehen für das ganze Inventar (§9 #20; MockBukkit für 26.3 nicht geprüft). */
    @Test
    void abnahme20GuiSperrtKlickUndZiehen() throws IOException {
        String session = Files.readString(Path.of("src/main/java/net/retromc/paper/core/gui/GuiSession.java"));
        assertTrue(session.contains("setOnGlobalClick(event -> event.setCancelled(true))"));
        assertTrue(session.contains("setOnGlobalDrag(event -> event.setCancelled(true))"));
        assertFalse(session.contains("setCancelled(false)"));
    }
}
