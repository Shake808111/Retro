package net.retromc.paper.core.gui;

import java.util.Locale;
import net.retromc.paper.core.item.ItemBuilder;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Zeichenfläche einer {@link Gui} für einen Betrachter. Spätere Aufrufe für denselben Slot überschreiben frühere;
 * Rahmen deshalb zuerst zeichnen.
 */
public interface GuiCanvas {

    /** Betrachter. */
    Player viewer();

    /** Sprache des Betrachters. */
    Locale locale();

    /** Aktiver Textdienst. */
    I18n i18n();

    /** Raster der GUI. */
    GuiGrid grid();

    /** Item-Builder mit Textdienst und Sprache des Betrachters. */
    default ItemBuilder item(Material material) {
        return ItemBuilder.of(material).texts(i18n(), locale());
    }

    /** Item ohne Aktion. */
    void set(int slot, ItemStack item);

    /** Knopf mit Aktion. */
    void button(int slot, ItemStack item, GuiAction action);

    /** Füllt die Slots der Form mit {@code pane}. */
    default void border(BorderShape shape, ItemStack pane) {
        for (int slot : shape.slots(grid())) {
            set(slot, pane);
        }
    }
}
