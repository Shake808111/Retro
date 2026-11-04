package net.retromc.bedwars;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import net.retromc.bedwars.shop.ShopCatalog;
import net.retromc.core.api.games.BedWarsStore;
import net.retromc.core.api.games.ModeLevelStore;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.games.BedWarsRepository;
import net.retromc.core.data.games.ModeLevelRepository;
import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.GameMode;
import net.retromc.game.framework.MiniGamePlugin;
import net.retromc.game.framework.TeamMode;
import net.retromc.paper.core.PaperCorePlugin;
import net.retromc.platform.common.service.ServiceRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Einstiegspunkt BedWars (Spec 04). Preset aus {@code config.yml → bedwars.preset}: {@code modern} (BedWars,
 * Hit-Cooldown an, Stats-Modus {@code bedwars}) oder {@code classic} (Classic-Preset, 1.8-Kampf,
 * {@code bedwars_classic}). Datenzugriff über die austauschbaren Dienste {@link BedWarsStore} und
 * {@link ModeLevelStore} (Standard: PostgreSQL aus core-data).
 */
public final class BedWarsPlugin extends MiniGamePlugin {

    private @Nullable BedWarsConfig config;
    private @Nullable Registration store;
    private @Nullable Registration levels;

    @Override
    protected GameDefinition definition() {
        BedWarsConfig current = config();
        return current.classic()
                ? new GameDefinition("bedwars_classic", "ClassicBW", CombatProfile.LEGACY_1_8, TeamMode.TEAMS, true,
                        false, false, false)
                : new GameDefinition("bedwars", "BedWars", CombatProfile.MODERN, TeamMode.TEAMS, true, false, false,
                        false);
    }

    @Override
    protected GameMode createMode(Game game) {
        return new BedWarsMode(this, game, config(), catalog(config().classic()));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("shop.yml", false);
        config = BedWarsConfig.read(getConfig());
        ServiceRegistry services = PaperCorePlugin.services();
        services.find(AsyncDatabase.class).ifPresent(database -> {
            if (services.find(BedWarsStore.class).isEmpty()) {
                store = services.registerDefault(BedWarsStore.class, new BedWarsRepository(database));
            }
            if (services.find(ModeLevelStore.class).isEmpty()) {
                levels = services.registerDefault(ModeLevelStore.class, new ModeLevelRepository(database,
                        Clock.systemUTC()));
            }
        });
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        for (Registration registration : new Registration[] {store, levels}) {
            if (registration != null) {
                registration.close();
            }
        }
        store = null;
        levels = null;
    }

    private BedWarsConfig config() {
        BedWarsConfig current = config;
        if (current == null) {
            current = BedWarsConfig.read(getConfig());
            config = current;
        }
        return current;
    }

    /** {@code shop.yml} aus dem Plugin-Ordner (sonst die mitgelieferte). */
    private ShopCatalog catalog(boolean classic) {
        Path file = getDataFolder().toPath().resolve("shop.yml");
        try (InputStream in = Files.isRegularFile(file) ? Files.newInputStream(file) : getResource("shop.yml");
                Reader reader = new InputStreamReader(java.util.Objects.requireNonNull(in, "shop.yml"),
                        StandardCharsets.UTF_8)) {
            return ShopCatalog.load(reader, classic);
        } catch (IOException e) {
            throw new IllegalStateException("shop.yml nicht lesbar", e);
        }
    }
}
