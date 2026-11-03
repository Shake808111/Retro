package net.retromc.paper.core.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.stats.StatsMode.Metric;
import net.retromc.core.api.stats.TopEntry;
import net.retromc.core.i18n.Formats;
import net.retromc.core.i18n.Messages;
import net.retromc.core.i18n.UiEra;
import net.retromc.core.stats.RankingScore;
import net.retromc.core.stats.StatsReport;
import net.retromc.core.stats.StatsReport.Value;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;

/**
 * Texte der Statistik ohne Server (Spec 02 §4.7, §4.9, §6.8): {@code /stats}-Block, Bestenliste, Rundenblock und
 * Hologramme. Werte je Oberfläche: Chat und Profil {@code 11.21}/{@code 84.85 %} (Punkt in allen Sprachen),
 * Hologramm {@code 1.33}/{@code 27,59%}; ganze Zahlen mit Tausendertrenner der Sprache, fehlende Werte {@code -}.
 */
public final class StatsTexts {

    /** Oberfläche eines Werts. */
    public enum Surface {
        /** {@code /stats}, {@code /top}, Statistik-Profil. */
        CHAT,
        /** Hologramme und Schilder. */
        HOLOGRAM
    }

    private StatsTexts() {
    }

    /** Wert als Text für die Oberfläche. */
    public static String value(Value value, Surface surface, Locale locale) {
        Formats formats = Formats.of(locale);
        boolean chat = surface == Surface.CHAT;
        return switch (value) {
            case Value.Count count -> chat ? formats.integer(count.value())
                    : formats.hologramInteger(count.value(), UiEra.MODERN);
            case Value.KillDeath kd -> chat ? formats.statsKd(kd.kills(), kd.deaths())
                    : formats.hologramKd(kd.kills(), kd.deaths());
            case Value.WinRate rate -> chat ? formats.statsWinrate(rate.wins(), rate.games())
                    : formats.hologramWinrate(rate.wins(), rate.games());
            case Value.Time time -> formats.countdown(time.millis() / 1_000);
            case Value.Missing missing -> Messages.plain(locale, "stats.value.none", Map.of());
        };
    }

    /** Zeitraum-Text: {@code 30 Tage}, {@code insgesamt}, {@code {n} Tage}, {@code 1 Tag}. */
    public static String period(Locale locale, StatWindow window) {
        if (window.isAll()) {
            return Messages.plain(locale, "stats.period.all", Map.of());
        }
        if (window.days() == StatPeriod.DAYS_30.days()) {
            return Messages.plain(locale, "stats.period.30d", Map.of());
        }
        if (window.days() == 1) {
            return Messages.plain(locale, "stats.period.day", Map.of());
        }
        return Messages.plain(locale, "stats.period.days", Map.of("n", window.days()));
    }

    /** Anzeigename eines Modus ({@code mode.<key>}). */
    public static String modeName(Locale locale, StatsMode mode) {
        return Messages.plain(locale, mode.i18nKey(), Map.of());
    }

    /** Modusname in Modusfarbe (MODERN mit Verlauf). */
    public static Component modeComponent(Locale locale, StatsMode mode) {
        return ColorText.name(modeName(locale, mode), mode.color(), mode.gradient(), true);
    }

    /**
     * {@code /stats}-Block (Spec 02 §4.7): Kopf {@code §7-= §eStatistiken von §6{name} §7({period}) =-}, je Feld
     * {@code §7 {label}: §e{value}}, Fuß {@code §7----------------------}. Der Name ist immer {@code §6}.
     */
    public static List<Component> statsBlock(I18n i18n, Locale locale, String name, StatWindow window,
            StatsReport report) {
        List<Component> lines = new ArrayList<>();
        lines.add(i18n.render(locale, "stats.header", Placeholder.unparsed("name", name),
                Placeholder.unparsed("period", period(locale, window))));
        for (StatsReport.Entry entry : report.entries()) {
            lines.add(i18n.render(locale, "stats.line", Placeholder.component("label",
                    i18n.render(locale, entry.labelKey())), Placeholder.unparsed("value",
                    value(entry.value(), Surface.CHAT, locale))));
        }
        lines.add(i18n.render(locale, "stats.footer"));
        return List.copyOf(lines);
    }

    /** {@code /top}: Kopf, 10 Zeilen, eigener Platz, Fuß (Spec 02 §3). */
    public static List<Component> topBlock(I18n i18n, Locale locale, StatsMode mode, StatPeriod period,
            List<TopEntry> entries, OptionalLong self) {
        List<Component> lines = new ArrayList<>();
        lines.add(i18n.render(locale, "top.header", ColorText.styling("mode_color", mode.color()),
                Placeholder.component("mode", modeComponent(locale, mode)),
                Placeholder.unparsed("period", period(locale, StatWindow.of(period)))));
        Formats formats = Formats.of(locale);
        for (TopEntry entry : entries) {
            Component name = ColorText.colored(entry.color(), entry.name());
            long shown = RankingScore.displayValue(mode.metric(), entry.score());
            if (mode.metric() == Metric.TIME) {
                lines.add(i18n.render(locale, "top.line.time", Placeholder.unparsed("pos",
                        Integer.toString(entry.position())), ColorText.styling("name_color", entry.color()),
                        Placeholder.component("name", name), Placeholder.unparsed("time",
                                formats.countdown(shown / 1_000))));
            } else {
                lines.add(i18n.render(locale, "top.line", Placeholder.unparsed("pos",
                        Integer.toString(entry.position())), ColorText.styling("name_color", entry.color()),
                        Placeholder.component("name", name), Placeholder.unparsed("points", formats.integer(shown)),
                        Placeholder.component("unit", i18n.render(locale, mode.metric() == Metric.KARMA
                                ? "top.unit.karma" : "top.unit.points"))));
            }
        }
        lines.add(i18n.render(locale, "top.self", Placeholder.unparsed("pos", self.isPresent()
                ? formats.integer(self.getAsLong()) : Messages.plain(locale, "stats.value.none", Map.of()))));
        lines.add(i18n.render(locale, "stats.footer"));
        return List.copyOf(lines);
    }

    /** Rundenblock (Spec 02 §4.7). */
    public static List<Component> roundBlock(I18n i18n, Locale locale, RoundSummary summary) {
        List<Component> lines = new ArrayList<>();
        lines.add(i18n.render(locale, "round.header"));
        for (RoundSummary.Line line : summary.lines()) {
            lines.add(i18n.render(locale, "stats.line", Placeholder.component("label",
                    i18n.render(locale, line.labelKey())), Placeholder.unparsed("value", line.value())));
        }
        Integer xp = summary.xp();
        if (xp != null) {
            lines.add(i18n.render(locale, "round.xp", Placeholder.unparsed("xp", Formats.of(locale).integer(xp))));
        }
        Boolean survived = summary.survived();
        Boolean won = summary.won();
        if (summary.blankBeforeMarks() && (survived != null || won != null)) {
            lines.add(Component.empty());
        }
        if (survived != null) {
            lines.add(i18n.render(locale, "round.survived", Placeholder.component("mark", mark(i18n, locale,
                    survived))));
        }
        if (won != null) {
            lines.add(i18n.render(locale, "round.won", Placeholder.component("mark", mark(i18n, locale, won))));
        }
        lines.add(i18n.render(locale, "round.footer"));
        return List.copyOf(lines);
    }

    private static Component mark(I18n i18n, Locale locale, boolean yes) {
        return i18n.render(locale, yes ? "round.mark.yes" : "round.mark.no");
    }

    /**
     * {@code HOLO_PERSONAL} (Spec 02 §4.9): Titel, Zeitraum, je Feld {@code §7{label} §e{value}} (ohne Doppelpunkt,
     * Hologramm-Format), letzte Zeile {@code §8▬▬▬▬▬▬▬▬▬▬}.
     */
    public static List<Component> personalHologram(I18n i18n, Locale locale, StatsMode mode, StatPeriod period,
            StatsReport report) {
        List<Component> lines = new ArrayList<>();
        lines.add(i18n.render(locale, "holo.personal.title", ColorText.styling("mode_color", mode.color()),
                Placeholder.component("mode", modeComponent(locale, mode))));
        lines.add(i18n.render(locale, period == StatPeriod.ALL ? "holo.personal.all" : "holo.personal.30d"));
        for (StatsReport.Entry entry : report.entries()) {
            lines.add(i18n.render(locale, "holo.line", Placeholder.component("label",
                    i18n.render(locale, entry.labelKey())), Placeholder.unparsed("value",
                    value(entry.value(), Surface.HOLOGRAM, locale))));
        }
        lines.add(i18n.render(locale, "holo.personal.end"));
        return List.copyOf(lines);
    }

    /** {@code HOLO_TOP} (Spec 02 §4.9): Titel, Zeitraum, {@code §e#{pos} {rankColor}{name} §7- §e{points}}. */
    public static List<Component> topHologram(I18n i18n, Locale locale, StatsMode mode, StatPeriod period,
            List<TopEntry> entries) {
        List<Component> lines = new ArrayList<>();
        lines.add(i18n.render(locale, "holo.top.title", ColorText.styling("mode_color", mode.color()),
                Placeholder.component("mode", modeComponent(locale, mode))));
        lines.add(i18n.render(locale, "holo.top.period", Placeholder.unparsed("period",
                period(locale, StatWindow.of(period)))));
        Formats formats = Formats.of(locale);
        for (TopEntry entry : entries) {
            long shown = RankingScore.displayValue(mode.metric(), entry.score());
            lines.add(i18n.render(locale, "holo.top.line", Placeholder.unparsed("pos",
                    Integer.toString(entry.position())), ColorText.styling("rank_color", entry.color()),
                    Placeholder.component("name", ColorText.colored(entry.color(), entry.name())),
                    Placeholder.unparsed("points", mode.metric() == Metric.TIME ? formats.countdown(shown / 1_000)
                            : formats.hologramInteger(shown, UiEra.MODERN))));
        }
        return List.copyOf(lines);
    }

    /** Schild der Wand (Spec 02 §4.9): {@code §l#{pos}}, Name, {@code §e{points} Punkte}, {@code §7{wins} Siege}. */
    public static List<Component> sign(I18n i18n, Locale locale, StatsMode mode, TopEntry entry) {
        Formats formats = Formats.of(locale);
        long points = RankingScore.displayValue(mode.metric(), entry.score());
        long wins = mode.metric() == Metric.POINTS ? RankingScore.winsOf(entry.score()) : 0;
        return i18n.renderLines(locale, "sign.top", Placeholder.unparsed("pos", Integer.toString(entry.position())),
                Placeholder.unparsed("name", entry.name()), Placeholder.unparsed("points",
                        formats.hologramInteger(points, UiEra.MODERN)),
                Placeholder.unparsed("wins", formats.hologramInteger(wins, UiEra.MODERN)));
    }
}
