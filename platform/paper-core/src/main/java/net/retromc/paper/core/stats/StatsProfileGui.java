package net.retromc.paper.core.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.i18n.Formats;
import net.retromc.core.stats.StatsReport;
import net.retromc.paper.core.gui.BorderShape;
import net.retromc.paper.core.gui.ChestGui;
import net.retromc.paper.core.item.ItemBuilder;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Statistik-Profil {@code StatsProfileGui} (Spec 02 §5.4, Layout Annahme): Titel {@code Statistiken von
 * {rankColor}{name}}, 5 Reihen, Rahmen {@code TOP_BOTTOM} hellblau. Slot 4 Spielerkopf, Slots 10–16, 19–25, 28–34 je
 * aktivem Modus (nach {@code id}), 40 Uhr (Zeitraum aller Items), 36 Zurück, 44 Hebel für die Sichtbarkeit (nur
 * eigenes Profil). Versteckte Fremd-Stats zeigen nur {@code §cNicht öffentlich}.
 */
public final class StatsProfileGui {

    public static final int ROWS = 5;
    public static final int HEAD = 4;
    public static final int BACK = 36;
    public static final int SWITCH = 40;
    public static final int VISIBILITY = 44;
    /** Slots der Modus-Items in Reihenfolge. */
    public static final List<Integer> MODE_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34);

    private StatsProfileGui() {
    }

    /**
     * Daten der Ansicht.
     *
     * @param modes aktive Modi nach {@code id}
     * @param reports Bericht je Modus im gewählten Zeitraum (fehlt bei versteckten Fremd-Stats)
     * @param periods gewählter Zeitraum je Modus
     * @param global Zeitraum der Uhr
     * @param own eigenes Profil (Hebel sichtbar)
     */
    public record View(UUID target, String name, RankDisplay rank, long playtimeSeconds, long coins, boolean hidden,
            boolean mayView, boolean own, List<StatsMode> modes, Map<ModeKey, StatsReport> reports,
            Map<ModeKey, StatPeriod> periods, StatPeriod global) {

        public View {
            Objects.requireNonNull(target, "target");
            modes = List.copyOf(modes);
            reports = Map.copyOf(reports);
            periods = Map.copyOf(periods);
        }

        public StatPeriod period(ModeKey mode) {
            return periods.getOrDefault(mode, global);
        }
    }

    /** Aktionen der Knöpfe. */
    public record Actions(Consumer<ModeKey> togglePeriod, Consumer<ModeKey> showInChat, Runnable toggleAll,
            Runnable toggleVisibility, @Nullable Runnable back) {
    }

    /** Baut die GUI für {@code viewer} (Haupt-Thread wegen der Items). */
    public static ChestGui build(I18n i18n, Locale locale, View view, Actions actions) {
        Component title = i18n.render(locale, "gui.stats.title", ColorText.styling("rank_color", view.rank().color()),
                Placeholder.component("name", ColorText.name(view.name(), view.rank().color(), view.rank().gradient(),
                        true)));
        ChestGui.Builder gui = ChestGui.builder(title, ROWS).border(BorderShape.TOP_BOTTOM);
        long hours = Formats.playtimeHours(view.playtimeSeconds());
        Formats formats = Formats.of(locale);
        gui.item(HEAD, ItemBuilder.of(Material.PLAYER_HEAD).texts(i18n, locale).skull(view.target())
                .name(ColorText.name(view.name(), view.rank().color(), view.rank().gradient(), true))
                .lore(i18n.render(locale, hours == 1 ? "gui.stats.playtime.one" : "gui.stats.playtime",
                        Placeholder.unparsed("hours", formats.integer(hours))),
                        i18n.render(locale, "gui.stats.coins", Placeholder.unparsed("coins",
                                formats.integer(view.coins()))))
                .hideAll().build());
        for (int i = 0; i < view.modes().size() && i < MODE_SLOTS.size(); i++) {
            StatsMode mode = view.modes().get(i);
            gui.button(MODE_SLOTS.get(i), modeItem(i18n, locale, view, mode), click -> {
                if (click.isRight()) {
                    click.player().closeInventory();
                    actions.showInChat().accept(mode.key());
                } else if (click.isLeft()) {
                    actions.togglePeriod().accept(mode.key());
                }
            });
        }
        String period = StatsTexts.period(locale, StatWindow.of(view.global()));
        gui.button(SWITCH, ItemBuilder.of(Material.CLOCK).texts(i18n, locale)
                .name(i18n.render(locale, "gui.stats.switch", Placeholder.unparsed("period", period)))
                .loreKey("gui.stats.switch.lore").hideAll().build(), click -> actions.toggleAll().run());
        Runnable back = actions.back();
        if (back != null) {
            gui.back(click -> back.run());
        }
        if (view.own()) {
            gui.button(VISIBILITY, ItemBuilder.of(Material.LEVER).texts(i18n, locale)
                    .name(i18n.render(locale, view.hidden() ? "gui.stats.hidden" : "gui.stats.visible"))
                    .loreKey("gui.stats.visibility.lore").hideAll().build(), click -> actions.toggleVisibility().run());
        }
        return gui.build();
    }

    private static ItemStack modeItem(I18n i18n, Locale locale, View view, StatsMode mode) {
        Material icon = Material.matchMaterial(mode.icon());
        ItemBuilder item = ItemBuilder.of(icon == null ? Material.PAPER : icon).texts(i18n, locale)
                .name(i18n.render(locale, "gui.stats.mode", ColorText.styling("mode_color", mode.color()),
                        Placeholder.component("mode", StatsTexts.modeComponent(locale, mode)))).hideAll();
        StatsReport report = view.reports().get(mode.key());
        if (!view.mayView() || report == null) {
            return item.lore(i18n.render(locale, "gui.stats.private")).build();
        }
        List<Component> lore = new ArrayList<>();
        lore.add(i18n.render(locale, "gui.stats.period", Placeholder.unparsed("period",
                StatsTexts.period(locale, StatWindow.of(view.period(mode.key()))))));
        lore.add(Component.empty());
        for (StatsReport.Entry entry : report.entries()) {
            lore.add(i18n.render(locale, "gui.stats.line", Placeholder.component("label",
                    i18n.render(locale, entry.labelKey())), Placeholder.unparsed("value",
                    StatsTexts.value(entry.value(), StatsTexts.Surface.CHAT, locale))));
        }
        lore.add(Component.empty());
        lore.addAll(i18n.renderLines(locale, "gui.stats.hint"));
        return item.lore(lore).build();
    }
}
