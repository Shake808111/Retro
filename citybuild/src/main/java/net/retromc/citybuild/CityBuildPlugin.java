package net.retromc.citybuild;

import org.bukkit.plugin.java.JavaPlugin;

/** Einstiegspunkt CityBuild. Vorbild und Quellen: README.md dieses Moduls. */
public final class CityBuildPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("CityBuild aktiv (Gerüst).");
    }
}
