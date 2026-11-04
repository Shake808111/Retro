package net.retromc.skywars.kit;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.games.SkyWarsStore.KitSettings;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameTexts;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiItems;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * „Kit-Einstellungen“ (Spec 04 §5.10, 3 Reihen): 11 Uhr „Frühere Kits“ (an/aus), 13 Enderauge „Kit-Fähigkeit
 * ankündigen“ (nur Info), 15 Trichter „Kits ausblenden“ → Unter-GUI mit Schalter je Kit.
 */
public final class KitSettingsGui extends Gui {

    private final Game game;
    private final KitService kits;

    private KitSettingsGui(Game game, KitService kits, Component title) {
        super(title, 3);
        this.game = game;
        this.kits = kits;
    }

    public static void open(Game game, KitService kits, Player player) {
        Component title = game.texts().i18n().render(game.texts().locale(player), "sw.kitsettings.title");
        game.services().get(GuiService.class).open(player, new KitSettingsGui(game, kits, title));
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        GameTexts texts = game.texts();
        Locale locale = canvas.locale();
        UUID uuid = canvas.viewer().getUniqueId();
        KitSettings settings = kits.profile(uuid).settings();
        canvas.button(11, ItemBuilder.of(Material.CLOCK).texts(texts.i18n(), locale).nameKey("sw.kitsettings.former")
                .loreKey("sw.kitsettings.former.lore", Placeholder.component("status", texts.i18n().render(locale,
                        settings.legacyEnabled() ? "sw.kitsettings.on" : "sw.kitsettings.off"))).hideAll().build(),
                click -> {
                    kits.settings(click.player(), settings.withLegacy(!settings.legacyEnabled()));
                    refresh();
                });
        canvas.set(13, ItemBuilder.of(Material.ENDER_EYE).texts(texts.i18n(), locale)
                .nameKey("sw.kitsettings.announce").loreKey("sw.kitsettings.announce.lore").hideAll().build());
        canvas.button(15, ItemBuilder.of(Material.HOPPER).texts(texts.i18n(), locale).nameKey("sw.kitsettings.hide")
                .loreKey("sw.kitsettings.hide.lore", Placeholder.unparsed("n",
                        Integer.toString(settings.hiddenKits().size()))).hideAll().build(),
                click -> HidePage.open(game, kits, click.player()));
    }

    /** Unter-GUI „Kits ausblenden“: ein Schalter je wählbarem Kit. */
    static final class HidePage extends Gui {

        private final Game game;
        private final KitService kits;

        private HidePage(Game game, KitService kits, Component title, int rows) {
            super(title, rows);
            this.game = game;
            this.kits = kits;
        }

        static void open(Game game, KitService kits, Player player) {
            int count = kits.selectable(player.getUniqueId()).size();
            int rows = Math.clamp((count + 8) / 9 + 1, 2, 6);
            Component title = game.texts().i18n().render(game.texts().locale(player), "sw.kitsettings.hide.title");
            game.services().get(GuiService.class).open(player, new HidePage(game, kits, title, rows));
        }

        @Override
        protected void draw(GuiCanvas canvas) {
            GameTexts texts = game.texts();
            Locale locale = canvas.locale();
            UUID uuid = canvas.viewer().getUniqueId();
            KitSettings settings = kits.profile(uuid).settings();
            List<KitDef> list = kits.selectable(uuid);
            int slots = canvas.grid().size() - 9;
            for (int i = 0; i < list.size() && i < slots; i++) {
                KitDef kit = list.get(i);
                boolean hidden = settings.hiddenKits().contains(kit.id());
                canvas.button(i, ItemBuilder.of(KitGui.material(kit.icon())).name(texts.i18n().render(locale,
                        "sw.kit.gui.name", Placeholder.component("kit", texts.i18n().render(locale, kit.nameKey())))
                        .decoration(TextDecoration.ITALIC, false)).texts(texts.i18n(), locale)
                        .loreKey(hidden ? "sw.kitsettings.hidden" : "sw.kitsettings.visible").hideAll().build(),
                        click -> {
                            kits.settings(click.player(), kits.profile(uuid).settings().toggleHidden(kit.id()));
                            refresh();
                        });
            }
            canvas.button(canvas.grid().size() - 5, GuiItems.back(canvas),
                    click -> KitSettingsGui.open(game, kits, click.player()));
        }
    }
}
