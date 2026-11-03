package net.retromc.paper.core.hotbar;

import org.bukkit.entity.Player;

/**
 * Hotbar-Framework (Spec 02 §4.10, §8.1 {@code HotbarService}): Items sind nicht verschiebbar, nicht ablegbar und
 * lösen beim Klick ihren Handler aus. Nur auf dem Haupt-Thread.
 */
public interface HotbarService {

    /** Setzt die Belegung; frühere Hotbar-Items des Spielers werden entfernt. */
    void apply(Player player, HotbarLayout layout);

    /** Entfernt die Hotbar-Items des Spielers. */
    void clear(Player player);
}
