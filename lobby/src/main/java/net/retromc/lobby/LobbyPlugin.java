package net.retromc.lobby;

import net.retromc.core.i18n.Messages;
import net.retromc.paper.core.PaperCorePlugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Einstiegspunkt der Lobby. Vorbild und Quellen: README.md dieses Moduls. Bisher gebaut: Profil-Item in Slot 9
 * (Spec 03 §4.9, öffnet das Profil-Menü mit Freunden, Party, Clan, Statistiken, Nicknamen und Einstellungen).
 */
public final class LobbyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Prüft zugleich, dass die core-*-Klassen aus paper-core erreichbar sind (join-classpath).
        getLogger().info("Lobby aktiv. Begrüßungstext: " + Messages.raw(Messages.DEFAULT_LOCALE, "lobby.welcome"));
        getServer().getPluginManager().registerEvents(new ProfileItem(this, PaperCorePlugin.services()), this);
    }
}
