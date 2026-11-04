package net.retromc.game.framework.map;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.rank.Rank;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameTexts;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * „Map-Abstimmung“ (Spec 03 §5.11, 3 Reihen): mit Abstimmung bis 5 Maps in Slots 11–15; ohne Abstimmung Slot 13
 * aktuelle Map und – ab Supremium bzw. für den Besitzer der Privatrunde – Slot 22 „Map festlegen“ mit Unterseite
 * aller Maps der Variante (Klick = {@code /forcemap}).
 */
public final class MapGui extends Gui {

    private final Game game;
    private final int used;
    private final int max;

    private MapGui(Game game, Component title, int used, int max) {
        super(title, 3);
        this.game = game;
        this.used = used;
        this.max = max;
    }

    /** Öffnet die GUI (Kontingent wird vorher gelesen). */
    public static void open(Game game, Player player) {
        Locale locale = game.texts().locale(player);
        Component title = game.texts().i18n().render(locale, "game.map.title");
        game.mainThread().accept(game.maps().quota(player).exceptionally(error -> new int[] {0, 0}), "Map-GUI",
                quota -> game.services().get(GuiService.class).open(player, new MapGui(game, title, quota[0],
                        quota[1])));
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        GameTexts texts = game.texts();
        Locale locale = canvas.locale();
        MapSelection maps = game.maps();
        if (game.definition().voting() && !maps.forced()) {
            List<GameMap> options = maps.voteOptions();
            for (int i = 0; i < options.size(); i++) {
                GameMap map = options.get(i);
                canvas.button(11 + i, mapItem(texts, locale, map).loreKey("game.map.vote.lore",
                        Placeholder.unparsed("votes", Integer.toString(maps.votes(map.id()))),
                        Placeholder.unparsed("builders", map.builders())).build(), click -> {
                            maps.vote(click.player(), map);
                            refresh();
                        });
            }
            return;
        }
        GameMap current = maps.current();
        canvas.set(13, mapItem(texts, locale, current).loreKey("game.map.current.lore").build());
        Player viewer = canvas.viewer();
        boolean allowed = max != 0 || game.privateRound().isOwner(viewer)
                || texts.rankWeight(viewer.getUniqueId()) >= Rank.SUPREMIUM.weight();
        if (allowed) {
            String left = max < 0 ? "∞" : Integer.toString(Math.max(0, max - used));
            String total = max < 0 ? "∞" : Integer.toString(max);
            canvas.button(22, ItemBuilder.of(Material.ENDER_PEARL).texts(texts.i18n(), locale).nameKey("game.map.force")
                    .loreKey("game.map.force.lore", Placeholder.unparsed("left", left),
                            Placeholder.unparsed("max", total)).build(),
                    click -> game.services().get(GuiService.class).open(click.player(), new ForcePage(game,
                            texts.i18n().render(locale, "game.map.force.title"))));
        }
    }

    private static ItemBuilder mapItem(GameTexts texts, Locale locale, GameMap map) {
        Material icon = Material.matchMaterial(map.icon());
        return ItemBuilder.of(icon == null || !icon.isItem() ? Material.PAPER : icon).texts(texts.i18n(), locale)
                .nameKey("game.map.name", Placeholder.unparsed("map", map.displayName())).hideAll();
    }

    /** Unterseite: alle Maps der Variante. */
    private static final class ForcePage extends Gui {

        private final Game game;

        ForcePage(Game game, Component title) {
            super(title, Math.max(1, Math.min(6, (game.maps().valid().size() + 8) / 9)));
            this.game = game;
        }

        @Override
        protected void draw(GuiCanvas canvas) {
            List<GameMap> maps = game.maps().valid();
            int slots = canvas.grid().size();
            for (int i = 0; i < maps.size() && i < slots; i++) {
                GameMap map = maps.get(i);
                canvas.button(i, mapItem(game.texts(), canvas.locale(), map).loreKey("game.map.force.item",
                        Placeholder.unparsed("builders", map.builders())).build(), click -> {
                            click.player().closeInventory();
                            game.maps().forcemap(click.player(), map.id(), false);
                        });
            }
        }
    }
}
