package net.retromc.bedwars.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.BedWarsMode;
import net.retromc.core.api.games.BedWarsStore;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiClick;
import net.retromc.paper.core.gui.GuiItems;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import net.retromc.platform.common.text.ColorText;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item-Shop „Wähle eine Kategorie“ (Spec 04 §5.1, 6 Reihen) und Schnellkauf-Editor „Schnellkauf bearbeiten“ (§5.5):
 * Reihe 1 Kategorien in eigener Reihenfolge, Reihen 2 und 6 Glas (violett bei Schnellkauf, sonst hellblau, weiß
 * unter der aktiven Kategorie), Waren in 19–25, 28–34, 37–43.
 */
public final class ShopGui extends Gui {

    /** Waren-Slots (3 × 7). */
    public static final List<Integer> SLOTS = List.of(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38,
            39, 40, 41, 42, 43);
    /** Doppelklick-Fenster für „Doppelt-Rechtsklick zum Entfernen“. */
    static final long DOUBLE_CLICK_MILLIS = 400;

    /** Verwendung. */
    public enum Mode {
        /** Kaufen am Händler. */
        BUY,
        /** Schnellkauf bearbeiten (Wartelobby, {@code /quickshop}). */
        EDIT,
        /** Nur ansehen. */
        PREVIEW
    }

    private final BedWarsMode mode;
    private final Mode use;
    private int category;
    private @Nullable String picked;
    private int lastRightSlot = -1;
    private long lastRightAt;

    private ShopGui(BedWarsMode mode, Mode use, Component title) {
        super(title, 6);
        this.mode = mode;
        this.use = use;
    }

    public static void open(BedWarsMode mode, Player player, Mode use) {
        Locale locale = mode.texts().locale(player);
        Component title = mode.texts().i18n().render(locale, use == Mode.EDIT ? "bw.quickshop.title"
                : "bw.shop.title");
        mode.game().services().get(GuiService.class).open(player, new ShopGui(mode, use, title));
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        Player viewer = canvas.viewer();
        Locale locale = canvas.locale();
        BedWarsStore.ShopSettings settings = mode.shop().settings(viewer.getUniqueId());
        List<Integer> order = settings.categoryOrder();
        for (int position = 0; position < 9; position++) {
            int cat = order.get(position);
            String id = ShopCatalog.CATEGORIES.get(cat);
            ItemStack icon = ItemBuilder.of(Material.valueOf(ShopCatalog.ICONS.get(cat)))
                    .texts(mode.texts().i18n(), locale).nameKey("bw.shop.cat." + id)
                    .loreKey("bw.shop.cat." + id + ".lore").hideAll().build();
            canvas.button(position, icon, click -> {
                category = cat;
                refresh();
            });
        }
        int active = order.indexOf(category);
        DyeColor glass = category == 0 ? DyeColor.PURPLE : DyeColor.LIGHT_BLUE;
        for (int column = 0; column < 9; column++) {
            DyeColor color = column == active ? DyeColor.WHITE : glass;
            canvas.set(9 + column, GuiItems.border(color));
            canvas.set(45 + column, GuiItems.border(color));
        }
        if (category == 0) {
            Map<Integer, String> quick = mode.shop().quickshop(viewer.getUniqueId());
            for (int field = 0; field < SLOTS.size(); field++) {
                int index = field;
                String itemId = quick.get(field);
                ShopItem item = itemId == null ? null : mode.catalog().item(itemId).orElse(null);
                if (item == null) {
                    if (use == Mode.EDIT) {
                        canvas.button(SLOTS.get(field), ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                                .texts(mode.texts().i18n(), locale).nameKey("bw.shop.quick.free").build(),
                                click -> quickClick(click, index, null));
                    }
                    continue;
                }
                canvas.button(SLOTS.get(field), display(viewer, locale, item, true), click -> quickClick(click, index,
                        item));
            }
            return;
        }
        List<ShopItem> items = mode.catalog().category(category).stream().filter(item -> !item.free()).toList();
        for (int i = 0; i < items.size() && i < SLOTS.size(); i++) {
            ShopItem item = items.get(i);
            canvas.button(SLOTS.get(i), display(viewer, locale, item, false), click -> categoryClick(click, item));
        }
    }

    private void quickClick(GuiClick click, int field, @Nullable ShopItem item) {
        Player player = click.player();
        UUID uuid = player.getUniqueId();
        if (use == Mode.EDIT && picked != null) {
            mode.shop().quickshop(uuid, field, picked);
            picked = null;
            refresh();
            return;
        }
        if (click.isRight() && use != Mode.PREVIEW) {
            long now = System.currentTimeMillis();
            boolean twice = lastRightSlot == field && now - lastRightAt < DOUBLE_CLICK_MILLIS;
            lastRightSlot = field;
            lastRightAt = now;
            if (twice && item != null) {
                mode.shop().quickshop(uuid, field, null);
                lastRightSlot = -1;
                refresh();
                return;
            }
        }
        if (use == Mode.BUY && item != null) {
            if (mode.shop().buy(player, item)) {
                refresh();
            }
        }
    }

    private void categoryClick(GuiClick click, ShopItem item) {
        Player player = click.player();
        if (use == Mode.EDIT) {
            picked = item.id();
            category = 0;
            refresh();
            return;
        }
        if (click.isShift()) {
            if (use == Mode.PREVIEW) {
                return;
            }
            int free = mode.shop().freeQuickSlot(player.getUniqueId());
            if (free >= 0) {
                mode.shop().quickshop(player.getUniqueId(), free, item.id());
            }
            return;
        }
        if (use == Mode.BUY && mode.shop().buy(player, item)) {
            refresh();
        }
    }

    /** Waren-Tooltip (Spec 04 §5.1). */
    private ItemStack display(Player viewer, Locale locale, ShopItem item, boolean quick) {
        ItemStack stack = ShopItems.item(item, mode.shop().dye(viewer), mode.texts().i18n(), locale);
        List<Component> lore = new ArrayList<>();
        boolean affordable = mode.game().lifecycle().waiting()
                || BedWarsMode.count(viewer, item.currency()) >= item.price();
        Component resource = mode.shop().resourceName(locale, item.currency(), item.price());
        lore.add(mode.texts().i18n().render(locale, affordable ? "bw.shop.price" : "bw.shop.price.missing",
                ColorText.styling("res_color", item.currency().color()),
                Placeholder.unparsed("price", Integer.toString(item.price())), Placeholder.component("res", resource)));
        lore.add(Component.empty());
        boolean extra = false;
        if (item.lore()) {
            lore.addAll(mode.texts().i18n().renderLines(locale, item.loreKey()));
            extra = true;
        }
        if (item.permanent()) {
            lore.add(mode.texts().i18n().render(locale, "bw.shop.permanent"));
            extra = true;
        }
        if (extra) {
            lore.add(Component.empty());
        }
        if (use == Mode.EDIT) {
            lore.add(mode.texts().i18n().render(locale, quick ? "bw.shop.quick.remove" : "bw.quickshop.pick"));
        } else if (use == Mode.BUY) {
            lore.add(mode.texts().i18n().render(locale, quick ? "bw.shop.quick.remove" : "bw.shop.quick.add"));
        }
        stack.editMeta(meta -> {
            meta.customName(mode.texts().i18n().render(locale, item.nameKey()).decoration(TextDecoration.ITALIC,
                    false));
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        });
        return stack;
    }
}
