package net.retromc.skywars;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.retromc.core.api.economy.WalletService;
import net.retromc.core.api.games.KitStore;
import net.retromc.core.api.games.ModeLevelStore;
import net.retromc.core.api.games.SkyWarsStore;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.games.KitRepository;
import net.retromc.core.data.games.ModeLevelRepository;
import net.retromc.core.data.games.SkyWarsRepository;
import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameDefinition;
import net.retromc.game.framework.GameMode;
import net.retromc.game.framework.GameSettings;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.MiniGamePlugin;
import net.retromc.game.framework.TeamMode;
import net.retromc.paper.core.PaperCorePlugin;
import net.retromc.paper.core.server.ServerLifecycle;
import net.retromc.platform.common.service.ServiceRegistry;
import net.retromc.skywars.challenge.ChallengeSet;
import net.retromc.skywars.chest.LootTables;
import net.retromc.skywars.event.GlobalEventSchedule;
import net.retromc.skywars.kit.KitCatalog;
import org.jspecify.annotations.Nullable;

/**
 * Einstiegspunkt SkyWars (Spec 04 §6.2). Preset aus {@code config.yml → skywars.preset}: {@code modern} (SkyWars,
 * Hit-Cooldown an, Stats-Modus {@code skywars}) oder {@code classic} ({@code skywars_classic}, 1.8-Kampf). Solo-
 * Varianten ({@code 16x1}, {@code 8x1}) laufen als Einzelspieler, Teamvarianten mit nummerierten Teams
 * ({@code Team #n}). Daten über die austauschbaren Dienste {@link KitStore}, {@link SkyWarsStore} und
 * {@link ModeLevelStore} (Standard: PostgreSQL aus core-data). Kataloge: {@code kits/skywars.yml},
 * {@code loot/*.yml}, {@code events.yml}, {@code challenges.yml} im Plugin-Ordner (sonst die mitgelieferten).
 */
public final class SkyWarsPlugin extends MiniGamePlugin {

    private final List<Registration> registrations = new ArrayList<>();
    private @Nullable SkyWarsConfig config;
    private @Nullable GameVariant variant;

    @Override
    protected GameDefinition definition() {
        SkyWarsConfig current = config();
        TeamMode teams = variant().teamSize() == 1 ? TeamMode.SOLO : TeamMode.NUMBERED;
        return current.classic()
                ? new GameDefinition("skywars_classic", "ClassicSW", CombatProfile.LEGACY_1_8, teams, true, false,
                        true, true)
                : new GameDefinition("skywars", "SkyWars", CombatProfile.MODERN, teams, true, false, true, true);
    }

    @Override
    protected GameMode createMode(Game game) {
        return new SkyWarsMode(this, game, config(), kits(), loot(), events(), challenges());
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String resource : List.of("kits/skywars.yml", "events.yml", "challenges.yml",
                "loot/skywars_t1.yml", "loot/skywars_t2.yml", "loot/skywars_refill1.yml",
                "loot/skywars_refill2.yml")) {
            if (!Files.exists(getDataFolder().toPath().resolve(resource))) {
                saveResource(resource, false);
            }
        }
        config = SkyWarsConfig.read(getConfig());
        ServiceRegistry services = PaperCorePlugin.services();
        String fromTemplate = services.find(ServerLifecycle.class).map(lifecycle -> lifecycle.self().variant())
                .orElse(null);
        variant = GameSettings.read(getConfig().getConfigurationSection("game"),
                getConfig().getString("game.variant", "8x1")).variant(fromTemplate);
        services.find(AsyncDatabase.class).ifPresent(database -> {
            if (services.find(KitStore.class).isEmpty()) {
                services.find(WalletService.class).ifPresent(wallets -> registrations.add(services.registerDefault(
                        KitStore.class, new KitRepository(database, wallets))));
            }
            if (services.find(SkyWarsStore.class).isEmpty()) {
                registrations.add(services.registerDefault(SkyWarsStore.class, new SkyWarsRepository(database)));
            }
            if (services.find(ModeLevelStore.class).isEmpty()) {
                registrations.add(services.registerDefault(ModeLevelStore.class, new ModeLevelRepository(database,
                        Clock.systemUTC())));
            }
        });
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        registrations.forEach(Registration::close);
        registrations.clear();
    }

    private SkyWarsConfig config() {
        SkyWarsConfig current = config;
        if (current == null) {
            current = SkyWarsConfig.read(getConfig());
            config = current;
        }
        return current;
    }

    private GameVariant variant() {
        GameVariant current = variant;
        return current != null ? current : GameVariant.parse(getConfig().getString("game.variant", "8x1"));
    }

    private Reader open(String name) throws IOException {
        Path file = getDataFolder().toPath().resolve(name);
        InputStream in = Files.isRegularFile(file) ? Files.newInputStream(file) : getResource(name);
        return new InputStreamReader(Objects.requireNonNull(in, name), StandardCharsets.UTF_8);
    }

    private KitCatalog kits() {
        try (Reader reader = open("kits/skywars.yml")) {
            return KitCatalog.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("kits/skywars.yml nicht lesbar", e);
        }
    }

    private LootTables loot() {
        return LootTables.load(name -> open("loot/" + name + ".yml"));
    }

    private GlobalEventSchedule events() {
        try (Reader reader = open("events.yml")) {
            return GlobalEventSchedule.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("events.yml nicht lesbar", e);
        }
    }

    private ChallengeSet challenges() {
        try (Reader reader = open("challenges.yml")) {
            return ChallengeSet.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("challenges.yml nicht lesbar", e);
        }
    }
}
