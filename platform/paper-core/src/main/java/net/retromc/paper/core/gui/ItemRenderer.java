package net.retromc.paper.core.gui;

import org.bukkit.inventory.ItemStack;

/**
 * Item für einen Eintrag einer {@link PagedGui} (Spec 02 §5.3). Sprache und Texte liefert die {@link GuiCanvas}
 * (z. B. {@code canvas.item(Material.PLAYER_HEAD).nameKey(...)}).
 *
 * @param <T> Typ der Einträge
 */
@FunctionalInterface
public interface ItemRenderer<T> {

    ItemStack render(T value, GuiCanvas canvas);
}
