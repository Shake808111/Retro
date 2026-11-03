package net.retromc.paper.core.social.gui;

import org.bukkit.entity.Player;

/**
 * Öffnet die Social-GUIs (Spec 01 §5, §8.3 {@code SocialGuiApi.open}); IDs aus {@link SocialGuiPlans}
 * ({@code party}, {@code friends}, {@code friends.requests}, {@code clan}, {@code clan.settings}, {@code settings},
 * {@code nick} …). Die Lobby öffnet sie über das Profil-Item, der Proxy über {@code OPEN_GUI}.
 */
public interface SocialGuiService {

    /** Öffnet die GUI {@code guiId} mit Argument (z. B. UUID eines Freundes), leer wenn keines. */
    void open(Player player, String guiId, String argument);
}
