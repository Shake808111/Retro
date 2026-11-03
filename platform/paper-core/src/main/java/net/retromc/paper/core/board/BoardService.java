package net.retromc.paper.core.board;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sidebar je Spieler (Spec 02 §4, §6.3, §8.1 {@code BoardService}) auf FastBoard: es werden nur geänderte Zeilen
 * gesendet (kein Flackern, §9 #14). Nur auf dem Haupt-Thread aufrufen.
 */
public interface BoardService {

    /** Zeigt {@code spec}; ein vorhandenes Board wird ersetzt. */
    void show(Player player, BoardSpec spec);

    /** Liest die Zeile {@code lineId} neu aus ihrem Supplier und sendet sie, falls sie sich geändert hat. */
    void update(Player player, String lineId);

    /** Setzt die Zeile {@code lineId} direkt (bis zum nächsten Neulesen). */
    void update(Player player, String lineId, Component value);

    /** Liest Titel und alle Zeilen neu. */
    void refresh(Player player);

    void hide(Player player);

    Optional<BoardSpec> current(Player player);
}
