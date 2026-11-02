package net.retromc.paper.core.gui;

import org.bukkit.entity.Player;

/**
 * Öffnet GUIs (Spec 02 §8.1 {@code GuiService}). Bauen über {@link ChestGui#builder}, {@link PagedGui#builder} und
 * {@link ConfirmGui#builder}. Darf von jedem Thread aufgerufen werden; geöffnet wird auf dem Haupt-Thread.
 */
public interface GuiService {

    /** Zeigt {@code gui} dem Spieler; ein schon offenes Inventar wird dabei geschlossen. */
    void open(Player player, Gui gui);
}
