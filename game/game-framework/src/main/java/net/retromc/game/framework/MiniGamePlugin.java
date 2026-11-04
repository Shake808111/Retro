package net.retromc.game.framework;

import net.retromc.core.api.BuildInfo;
import net.retromc.game.framework.server.PrivateRoundContext;
import net.retromc.paper.core.PaperCorePlugin;
import net.retromc.paper.core.server.ServerLifecycle;
import net.retromc.platform.common.service.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Basisklasse aller Spielmodus-Plugins. Ein Spielserver führt genau einen Modus und eine Runde aus (Spec 08
 * §6.10, {@code oneMatch}). Das Modul liefert {@link #definition()} und seinen {@link GameMode}; Wartelobby,
 * Countdown, Teams, Map, Zuschauer, Kill-Feed, Rundenende und Server-Status kommen aus {@link Game}.
 *
 * <p>Die Variante kommt aus dem Template ({@code bedwars-4x4} → {@code 4x4}), sonst aus {@code game.variant} in
 * {@code config.yml}.
 */
public abstract class MiniGamePlugin extends JavaPlugin {

    private @Nullable Game game;

    /** Beschreibung des Modus, den dieses Plugin umsetzt. */
    protected abstract GameDefinition definition();

    /**
     * Modus-Logik; wird nach dem Aufbau des Rahmens erzeugt. {@code null} (Standard) = Gerüst ohne Spiellogik:
     * der Rahmen startet dann keine Runde.
     */
    protected @Nullable GameMode createMode(Game game) {
        return null;
    }

    /** Laufende Runde. */
    public final Game game() {
        Game current = game;
        if (current == null) {
            throw new IllegalStateException("Runde nicht aktiv");
        }
        return current;
    }

    public final GameLifecycle lifecycle() {
        return game().lifecycle();
    }

    @Override
    public void onEnable() {
        if (getResource("config.yml") != null) {
            saveDefaultConfig();
        }
        ServiceRegistry services = PaperCorePlugin.services();
        GameDefinition definition = definition();
        String variantKey = services.find(ServerLifecycle.class).map(lifecycle -> lifecycle.self().variant())
                .orElse(null);
        GameSettings settings = GameSettings.read(getConfig().getConfigurationSection("game"),
                getConfig().getString("game.variant", "4x4"));
        GameVariant variant = settings.variant(variantKey);
        Game created = new Game(this, definition, settings, services, variant,
                PrivateRoundContext.fromEnv(System::getenv));
        GameMode mode = createMode(created);
        if (mode == null) {
            getSLF4JLogger().info("{} {} ist ein Gerüst ohne Spiellogik – keine Runde.", BuildInfo.BRAND_NAME,
                    definition.displayName());
            return;
        }
        game = created;
        created.enable(mode);
        getSLF4JLogger().info("{} {} bereit: Variante {}, Kampfprofil {}", BuildInfo.BRAND_NAME,
                definition.displayName(), variant.key(), definition.defaultCombat());
    }

    @Override
    public void onDisable() {
        Game current = game;
        if (current != null) {
            current.disable();
        }
        game = null;
    }
}
