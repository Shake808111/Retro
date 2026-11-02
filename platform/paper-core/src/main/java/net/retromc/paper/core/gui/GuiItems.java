package net.retromc.paper.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.paper.core.item.DyedMaterials;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Standard-Items der GUIs (Spec 02 §5.1–5.3): Rahmenglas, Blättern, Zurück, Schließen, leere Liste, Bestätigen und
 * Abbrechen. Texte aus dem Katalog ({@code gui.*}).
 */
public final class GuiItems {

    /** Rahmenfarbe ohne besonderen Kontext. */
    public static final DyeColor DEFAULT_BORDER = DyeColor.LIGHT_BLUE;

    private GuiItems() {
    }

    /** Glasscheibe der Farbe, Name leer, Tooltip ausgeblendet. */
    public static ItemStack border(DyeColor color) {
        Material pane = Material.matchMaterial(DyedMaterials.glassPane(color.name()));
        if (pane == null) {
            throw new IllegalStateException("Keine Glasscheibe für " + color);
        }
        return ItemBuilder.of(pane).name(Component.space()).hideTooltip().build();
    }

    /** {@code §eVorherige Seite} (Pfeil). */
    public static ItemStack previousPage(GuiCanvas canvas) {
        return canvas.item(Material.ARROW).nameKey("gui.page.prev").build();
    }

    /** {@code §eNächste Seite} (Pfeil). */
    public static ItemStack nextPage(GuiCanvas canvas) {
        return canvas.item(Material.ARROW).nameKey("gui.page.next").build();
    }

    /** {@code §7Seite §e{p}§7/§e{n}} (Papier). */
    public static ItemStack pageIndicator(GuiCanvas canvas, int page, int pages) {
        return canvas.item(Material.PAPER).nameKey("gui.page.indicator",
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages))).build();
    }

    /** {@code §cZurück} (Pfeil). */
    public static ItemStack back(GuiCanvas canvas) {
        return canvas.item(Material.ARROW).nameKey("gui.back").build();
    }

    /** {@code §cSchließen} (Barriere). */
    public static ItemStack close(GuiCanvas canvas) {
        return canvas.item(Material.BARRIER).nameKey("gui.close").build();
    }

    /** {@code §cKeine Einträge} (Barriere). */
    public static ItemStack empty(GuiCanvas canvas) {
        return canvas.item(Material.BARRIER).nameKey("gui.empty").build();
    }

    /** {@code §aBestätigen} (grüner Farbstoff). */
    public static ItemStack confirm(GuiCanvas canvas) {
        return canvas.item(Material.LIME_DYE).nameKey("gui.confirm.yes").loreKey("gui.confirm.yes.lore").build();
    }

    /** {@code §cAbbrechen} (roter Farbstoff). */
    public static ItemStack cancel(GuiCanvas canvas) {
        return canvas.item(Material.RED_DYE).nameKey("gui.confirm.no").loreKey("gui.confirm.no.lore").build();
    }
}
