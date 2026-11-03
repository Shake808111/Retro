package net.retromc.paper.core.chat;

import org.bukkit.entity.Player;

/**
 * Empfängerfilter des Chats (Spec 02 §6.1 Schritt 5), z. B. „Spieler verstecken“ mit Chat-Sync aus der Lobby.
 * Läuft im asynchronen Chat-Thread.
 */
@FunctionalInterface
public interface ChatAudienceFilter {

    /** {@code true}, wenn {@code viewer} die Nachricht von {@code sender} nicht sehen soll. */
    boolean hides(Player viewer, Player sender);
}
