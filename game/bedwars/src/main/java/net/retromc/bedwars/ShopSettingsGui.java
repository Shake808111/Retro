package net.retromc.bedwars;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.shop.ShopCatalog;
import net.retromc.core.api.games.BedWarsStore;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiItems;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * „Shop-Einstellungen“ (Spec 04 §5.4, 3 Reihen): Kategorie-Reihenfolge (Links-/Rechtsklick verschiebt) und
 * bevorzugte Hotbar-Slots für Waffe, Werkzeug, Blöcke, Bogen und Trank; Speichern sofort.
 */
final class ShopSettingsGui extends Gui {

    /** Hotbar-Art mit Slot und Symbol (Reihe 3). */
    record Kind(String id, int slot, Material icon) {
    }

    static final List<Kind> KINDS = List.of(new Kind("weapon", 18, Material.IRON_SWORD),
            new Kind("pickaxe", 19, Material.IRON_PICKAXE), new Kind("blocks", 20, Material.WHITE_WOOL),
            new Kind("bow", 21, Material.BOW), new Kind("potion", 22, Material.POTION));

    private final BedWarsMode mode;

    private ShopSettingsGui(BedWarsMode mode, Component title) {
        super(title, 3);
        this.mode = mode;
    }

    static void open(BedWarsMode mode, Player player) {
        Component title = mode.texts().i18n().render(mode.texts().locale(player), "bw.settings.title");
        mode.game().services().get(GuiService.class).open(player, new ShopSettingsGui(mode, title));
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        UUID uuid = canvas.viewer().getUniqueId();
        Locale locale = canvas.locale();
        BedWarsStore.ShopSettings settings = mode.shop().settings(uuid);
        List<Integer> order = settings.categoryOrder();
        for (int position = 0; position < 9; position++) {
            int index = position;
            String id = ShopCatalog.CATEGORIES.get(order.get(position));
            canvas.button(position, ItemBuilder.of(Material.valueOf(ShopCatalog.ICONS.get(order.get(position))))
                    .texts(mode.texts().i18n(), locale).nameKey("bw.shop.cat." + id)
                    .loreKey("bw.settings.category.lore").hideAll()
                    .build(), click -> {
                        int target = click.isLeft() ? index - 1 : index + 1;
                        if (target >= 0 && target < 9) {
                            List<Integer> changed = new ArrayList<>(order);
                            changed.set(index, order.get(target));
                            changed.set(target, order.get(index));
                            mode.shop().settings(uuid, new BedWarsStore.ShopSettings(changed, settings.hotbar()));
                            refresh();
                        }
                    });
        }
        for (int column = 0; column < 9; column++) {
            canvas.set(9 + column, GuiItems.border(DyeColor.GRAY));
        }
        for (Kind kind : KINDS) {
            canvas.button(kind.slot(), ItemBuilder.of(kind.icon()).texts(mode.texts().i18n(), locale)
                    .nameKey("bw.settings." + kind.id(), Placeholder.unparsed("n",
                            Integer.toString(settings.slot(kind.id()) + 1))).loreKey("bw.settings.slot.lore")
                    .hideAll().build(), click -> {
                        Map<String, Integer> hotbar = new LinkedHashMap<>(BedWarsStore.ShopSettings.DEFAULT.hotbar());
                        hotbar.putAll(settings.hotbar());
                        hotbar.put(kind.id(), (settings.slot(kind.id()) + 1) % 9);
                        mode.shop().settings(uuid, new BedWarsStore.ShopSettings(order, hotbar));
                        refresh();
                    });
        }
        canvas.button(26, ItemBuilder.of(Material.BARRIER).texts(mode.texts().i18n(), locale)
                .nameKey("bw.settings.reset").build(), click -> {
                    mode.shop().settings(uuid, BedWarsStore.ShopSettings.DEFAULT);
                    refresh();
                });
    }
}
