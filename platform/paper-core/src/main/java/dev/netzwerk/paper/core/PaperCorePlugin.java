package dev.netzwerk.paper.core;

import dev.netzwerk.core.api.BuildInfo;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Einstiegspunkt des Kern-Plugins auf Paper-Servern.
 *
 * <p>Geplante Aufgaben (Notiz 07, Abschnitt 6): Profil laden, Scoreboard/Tablist, GUIs,
 * Cosmetics, Kampfprofile, Anticheat-Hooks, Agones-SDK-Client. Bisher nur ein Gerüst.
 */
public final class PaperCorePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info(BuildInfo.BRAND_NAME + " Core " + BuildInfo.VERSION + " aktiv (Gerüst).");
    }

    @Override
    public void onDisable() {
        getLogger().info(BuildInfo.BRAND_NAME + " Core gestoppt.");
    }
}
