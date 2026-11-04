package net.retromc.skywars.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatLine;
import net.retromc.core.api.stats.StatsStore;
import net.retromc.core.i18n.Formats;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.map.MapSelection;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * „Map-Menü“ (Spec 04 §5.11, 3 Reihen): bis 7 Maps der Variante (zufällige Auswahl je Runde) in Slots 10–16 als
 * {@code FILLED_MAP} mit den eigenen Werten aus {@code stats.map_alltime} (Punkte, K/D, Siegesquote), Stimmen und
 * Klick zum Abstimmen; Wertung bei Countdown 10 s (Rahmen). Ist die Map festgelegt, zeigt Slot 13 nur sie.
 */
public final class MapMenu extends Gui {

    /** Erster Slot der Maps. */
    static final int FIRST = 10;

    private final Game game;
    private final Map<String, StatLine> stats;

    private MapMenu(Game game, Component title, Map<String, StatLine> stats) {
        super(title, 3);
        this.game = game;
        this.stats = Map.copyOf(stats);
    }

    /** Lädt die Map-Werte des Spielers und öffnet das Menü. */
    public static void open(Game game, Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture<Map<String, StatLine>> lines = game.services().find(StatsStore.class)
                .map(store -> store.mapLines(uuid, ModeKey.of(game.definition().modeId())))
                .orElseGet(() -> CompletableFuture.completedFuture(Map.of()));
        Component title = game.texts().i18n().render(game.texts().locale(player), "sw.mapmenu.title");
        game.mainThread().accept(lines.exceptionally(error -> Map.of()), "Map-Menü", loaded -> {
            if (player.isOnline()) {
                game.services().get(GuiService.class).open(player, new MapMenu(game, title, loaded));
            }
        });
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        GameTexts texts = game.texts();
        Locale locale = canvas.locale();
        Player viewer = canvas.viewer();
        MapSelection maps = game.maps();
        if (maps.forced() || maps.voteOptions().isEmpty()) {
            maps.currentIfSet().ifPresent(map -> canvas.set(13, item(texts, locale, viewer, map, false)));
            return;
        }
        List<GameMap> options = maps.voteOptions();
        for (int i = 0; i < options.size() && i < 7; i++) {
            GameMap map = options.get(i);
            canvas.button(FIRST + i, item(texts, locale, viewer, map, true), click -> {
                if (!game.waiting().choicesOpen()) {
                    click.player().playSound(click.player().getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                maps.vote(click.player(), map);
                refresh();
            });
        }
    }

    private org.bukkit.inventory.ItemStack item(GameTexts texts, Locale locale, Player viewer, GameMap map,
            boolean voting) {
        boolean own = game.maps().voteOf(viewer.getUniqueId()).map(map.id()::equals).orElse(false);
        Component name = texts.i18n().render(locale, "sw.mapmenu.name", Placeholder.unparsed("map",
                map.displayName()));
        if (own) {
            name = name.append(texts.i18n().render(locale, "sw.mapmenu.selected"));
        }
        StatLine line = stats.getOrDefault(map.id(), StatLine.empty(viewer.getUniqueId(),
                ModeKey.of(game.definition().modeId())));
        Formats formats = Formats.of(locale);
        List<Component> lore = new ArrayList<>();
        lore.add(texts.i18n().render(locale, "sw.mapmenu.points", Placeholder.unparsed("p",
                formats.integer(line.points()))));
        lore.add(texts.i18n().render(locale, "sw.mapmenu.kd", Placeholder.unparsed("kd",
                formats.statsKd(line.kills(), line.deaths()))));
        lore.add(texts.i18n().render(locale, "sw.mapmenu.winrate", Placeholder.unparsed("wr",
                formats.statsDecimal(Formats.winrate(line.wins(), line.games())))));
        if (voting) {
            lore.add(Component.empty());
            lore.add(texts.i18n().render(locale, "sw.mapmenu.votes", Placeholder.unparsed("votes",
                    Integer.toString(game.maps().votes(map.id())))));
            lore.add(texts.i18n().render(locale, "sw.mapmenu.click"));
        }
        ItemBuilder item = ItemBuilder.of(Material.FILLED_MAP).name(name.decoration(TextDecoration.ITALIC, false))
                .lore(lore.stream().map(component -> component.decoration(TextDecoration.ITALIC, false)).toList())
                .hideAll();
        if (own) {
            item.glow();
        }
        return item.build();
    }
}
