package net.retromc.skywars.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.games.KitStore;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameTexts;
import net.retromc.paper.core.coins.CoinService;
import net.retromc.paper.core.gui.ConfirmGui;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiItems;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import net.retromc.paper.core.item.LoreWrapper;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Kit-Menü (Spec 04 §5.9, Titel {@code Kits}, 5 Reihen): gelbes Glas in Reihe 1 und 5, Kits in Katalogreihenfolge
 * in den Slots 9–35. Tooltip Ausrüstung, Fähigkeit und Preis bzw. Besitz/Auswahl; Klick wählt ein besessenes Kit,
 * ein fremdes öffnet die Kaufbestätigung. Mehr als 27 Kits (frühere Kits an) blättern über die Pfeile in Slot 36/44.
 */
public final class KitGui extends Gui {

    /** Zeilenbreite der Fähigkeitsbeschreibung (~40 Zeichen, Spec 04 §5.9). */
    static final int WRAP = 40;
    static final int FIRST = 9;

    private final Game game;
    private final KitService kits;
    private int page;

    private KitGui(Game game, KitService kits, Component title) {
        super(title, 5);
        this.game = game;
        this.kits = kits;
    }

    public static void open(Game game, KitService kits, Player player) {
        Component title = game.texts().i18n().render(game.texts().locale(player), "sw.kit.gui.title");
        game.services().get(GuiService.class).open(player, new KitGui(game, kits, title));
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        GameTexts texts = game.texts();
        Locale locale = canvas.locale();
        Player viewer = canvas.viewer();
        UUID uuid = viewer.getUniqueId();
        ItemStack pane = GuiItems.border(DyeColor.YELLOW);
        for (int slot = 0; slot < 9; slot++) {
            canvas.set(slot, pane);
            canvas.set(36 + slot, pane);
        }
        List<KitDef> all = kits.menu(uuid);
        int pages = Math.max(1, (all.size() + KitCatalog.MENU_SLOTS - 1) / KitCatalog.MENU_SLOTS);
        page = Math.clamp(page, 0, pages - 1);
        long coins = game.services().find(CoinService.class).map(service -> service.balance(uuid)).orElse(0L);
        String selected = kits.selectedId(uuid);
        int from = page * KitCatalog.MENU_SLOTS;
        for (int i = from; i < Math.min(all.size(), from + KitCatalog.MENU_SLOTS); i++) {
            KitDef kit = all.get(i);
            boolean owned = kits.owns(uuid, kit);
            boolean active = kit.id().equals(selected);
            canvas.button(FIRST + i - from, icon(texts, locale, kit, owned, active, coins), click -> {
                long balance = game.services().find(CoinService.class).map(service -> service.balance(uuid))
                        .orElse(0L);
                if (kits.owns(uuid, kit)) {
                    kits.select(click.player(), kit.id());
                    refresh();
                } else if (balance < kit.price()) {
                    texts.send(click.player(), "sw.kit.nocoins", texts.number(locale, "n", kit.price() - balance));
                } else {
                    confirm(click.player(), kit);
                }
            });
        }
        if (page > 0) {
            canvas.button(36, GuiItems.previousPage(canvas), click -> {
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            canvas.button(44, GuiItems.nextPage(canvas), click -> {
                page++;
                refresh();
            });
        }
    }

    private void confirm(Player player, KitDef kit) {
        GameTexts texts = game.texts();
        Locale locale = texts.locale(player);
        ConfirmGui dialog = ConfirmGui.builder(texts.i18n().render(locale, "sw.kit.confirm.title"))
                .info(ItemBuilder.of(material(kit.icon())).name(texts.i18n().render(locale, "sw.kit.gui.name",
                        Placeholder.component("kit", texts.i18n().render(locale, kit.nameKey())))
                        .decoration(TextDecoration.ITALIC, false)).lore(texts.i18n().render(locale,
                                "sw.kit.gui.price", texts.number(locale, "price", kit.price()))
                                .decoration(TextDecoration.ITALIC, false)).hideAll().build())
                .onConfirm(confirmed -> kits.buy(confirmed, kit))
                .onCancel(cancelled -> open(game, kits, cancelled))
                .build();
        game.services().get(GuiService.class).open(player, dialog);
    }

    /** Menü-Item eines Kits mit Tooltip (Spec 04 §5.9). */
    ItemStack icon(GameTexts texts, Locale locale, KitDef kit, boolean owned, boolean active, long coins) {
        List<Component> lore = lore(texts, locale, kit, owned, active, coins);
        ItemBuilder item = ItemBuilder.of(material(kit.icon())).name(texts.i18n().render(locale, "sw.kit.gui.name",
                Placeholder.component("kit", texts.i18n().render(locale, kit.nameKey())))
                .decoration(TextDecoration.ITALIC, false)).lore(lore).hideAll();
        if (active) {
            item.glow();
        }
        return item.build();
    }

    static List<Component> lore(GameTexts texts, Locale locale, KitDef kit, boolean owned, boolean active,
            long coins) {
        List<Component> lore = new ArrayList<>();
        lore.add(line(texts, locale, "sw.kit.gui.equipment"));
        List<KitItem> items = new ArrayList<>(kit.items());
        if (kit.ability() != null && kit.ability().item() != null) {
            items.add(kit.ability().item());
        }
        for (KitItem item : items) {
            Component name = item.nameKey() != null ? texts.i18n().render(locale, item.nameKey())
                    : Component.translatable(material(item.material()).translationKey());
            lore.add(line(texts, locale, "sw.kit.gui.item", Placeholder.unparsed("n",
                    Integer.toString(item.amount())), Placeholder.component("item", name)));
        }
        lore.add(Component.empty());
        lore.add(line(texts, locale, "sw.kit.gui.ability"));
        Component description = texts.i18n().render(locale, kit.ability() == null ? "sw.kit.gui.ability.none"
                : kit.descKey()).decoration(TextDecoration.ITALIC, false);
        lore.addAll(LoreWrapper.wrap(description, WRAP));
        lore.add(Component.empty());
        if (active) {
            lore.add(line(texts, locale, "sw.kit.gui.selected"));
        } else if (owned) {
            lore.add(line(texts, locale, "sw.kit.gui.owned"));
        } else {
            lore.add(line(texts, locale, "sw.kit.gui.price", texts.number(locale, "price", kit.price())));
            lore.add(line(texts, locale, coins >= kit.price() ? "sw.kit.gui.buy" : "sw.kit.gui.nocoins"));
        }
        return lore;
    }

    private static Component line(GameTexts texts, Locale locale, String key,
            net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        return texts.i18n().render(locale, key, args).decoration(TextDecoration.ITALIC, false);
    }

    static Material material(String name) {
        Material material = Material.matchMaterial(name);
        return material == null || !material.isItem() ? Material.CHEST : material;
    }

    /** Anzeigename der Auswahl ({@code random} = „Zufällig“). */
    public static Component selectionName(GameTexts texts, Locale locale, KitService kits, UUID player) {
        String selected = kits.selectedId(player);
        if (KitStore.RANDOM.equals(selected)) {
            return texts.i18n().render(locale, "sw.kit.random");
        }
        return texts.i18n().render(locale, kits.resolveForDisplay(player).nameKey());
    }
}
