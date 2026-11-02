package net.retromc.paper.core.gui;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Ein angenommener Klick auf einen Knopf im oberen Inventar. Aktionen laufen einen Tick nach dem Klick auf dem
 * Haupt-Thread; Inventare dürfen darin geöffnet und geschlossen werden.
 *
 * @param player klickender Spieler
 * @param type Klickart (links, rechts, mit Umschalt …)
 * @param slot Slot im oberen Inventar
 */
public record GuiClick(Player player, ClickType type, int slot) {

    public GuiClick {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(type, "type");
    }

    /** Linksklick, auch mit Umschalt. */
    public boolean isLeft() {
        return type.isLeftClick();
    }

    /** Rechtsklick, auch mit Umschalt. */
    public boolean isRight() {
        return type.isRightClick();
    }

    /** Klick mit Umschalt. */
    public boolean isShift() {
        return type.isShiftClick();
    }
}
