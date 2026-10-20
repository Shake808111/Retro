package dev.netzwerk.lobby;

import dev.netzwerk.core.i18n.Messages;
import org.bukkit.plugin.java.JavaPlugin;

/** Einstiegspunkt der Lobby. Vorbild und Quellen: README.md dieses Moduls. */
public final class LobbyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // Prüft zugleich, dass die core-*-Klassen aus paper-core erreichbar sind (join-classpath).
        getLogger().info("Lobby aktiv (Gerüst). Begrüßungstext: " + Messages.raw(Messages.DEFAULT_LOCALE, "lobby.welcome"));
    }
}
