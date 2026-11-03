package net.retromc.paper.core.chat;

import java.util.Optional;
import org.bukkit.entity.Player;

/**
 * Liefert den Chat-Kontext (Spec 02 §6.1 Schritt 1, §8.4: Team, Zuschauer, Level); vom Spiel-Rahmen registriert.
 * Läuft im asynchronen Chat-Thread.
 */
@FunctionalInterface
public interface ChatContextProvider {

    /** Kontext der Nachricht oder leer, wenn dieser Anbieter nicht zuständig ist. */
    Optional<ChatContext> context(Player sender, String message);
}
