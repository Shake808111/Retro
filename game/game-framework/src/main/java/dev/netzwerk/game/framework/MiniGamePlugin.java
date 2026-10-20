package dev.netzwerk.game.framework;

import dev.netzwerk.core.api.BuildInfo;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Basisklasse für alle Spielmodus-Plugins. Ein Spielserver führt genau einen Modus und eine
 * Runde aus (Notiz 07, Abschnitt 3: "one match per pod").
 */
public abstract class MiniGamePlugin extends JavaPlugin {

    private final GameLifecycle lifecycle = new GameLifecycle();

    /** Beschreibung des Modus, den dieses Plugin umsetzt. */
    protected abstract GameDefinition definition();

    public final GameLifecycle lifecycle() {
        return lifecycle;
    }

    @Override
    public void onEnable() {
        GameDefinition definition = definition();
        getLogger().info(BuildInfo.BRAND_NAME + " " + definition.displayName() + " bereit (Gerüst), Zustand "
                + lifecycle.state() + ", Kampfprofil " + definition.defaultCombat() + ".");
    }
}
