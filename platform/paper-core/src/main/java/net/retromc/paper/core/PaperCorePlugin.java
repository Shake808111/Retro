package net.retromc.paper.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import net.retromc.core.api.BuildInfo;
import net.retromc.paper.core.bar.BarFeature;
import net.retromc.paper.core.board.BoardFeature;
import net.retromc.paper.core.bridge.BridgeFeature;
import net.retromc.paper.core.chat.ChatFormatFeature;
import net.retromc.paper.core.coins.CoinFeature;
import net.retromc.paper.core.command.CommandFeature;
import net.retromc.paper.core.config.ServerConfig;
import net.retromc.paper.core.gui.GuiFeature;
import net.retromc.paper.core.holo.HologramFeature;
import net.retromc.paper.core.holo.StatsWallFeature;
import net.retromc.paper.core.hotbar.HotbarFeature;
import net.retromc.paper.core.lang.TextFeature;
import net.retromc.paper.core.map.MapFeature;
import net.retromc.paper.core.npc.NpcFeature;
import net.retromc.paper.core.profile.ProfileFeature;
import net.retromc.paper.core.server.ServerFeature;
import net.retromc.paper.core.service.BukkitServiceMirror;
import net.retromc.paper.core.social.SocialFeature;
import net.retromc.paper.core.social.afk.AfkFeature;
import net.retromc.paper.core.social.chat.ChatGuardFeature;
import net.retromc.paper.core.social.nick.NickApplyFeature;
import net.retromc.paper.core.stats.StatsFeature;
import net.retromc.paper.core.stats.StatsServicesFeature;
import net.retromc.paper.core.tab.NametagFeature;
import net.retromc.paper.core.tab.TabFeature;
import net.retromc.paper.core.title.TitleFeature;
import net.retromc.platform.common.concurrent.AsyncExecutor;
import net.retromc.platform.common.concurrent.MainThreadExecutor;
import net.retromc.platform.common.config.ConfigException;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.connect.ConnectionException;
import net.retromc.platform.common.connect.NetworkConnections;
import net.retromc.platform.common.connect.Retry;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.feature.FeatureManager;
import net.retromc.platform.common.service.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Kern-Plugin {@code ${brandName}Core} auf jedem Paper-Server (Spec 02, Spec 08 §6.2).
 *
 * <ol>
 *   <li>{@code onLoad}: Umgebungsvariablen lesen; fehlt eine Pflichtangabe, Server herunterfahren.</li>
 *   <li>{@code onEnable}: PostgreSQL → Valkey → Nachrichtenbus (je 3 Versuche, sonst Herunterfahren), als
 *       Standard-Dienste registrieren, dann die {@link #features() Features} in Reihenfolge.</li>
 *   <li>{@code onDisable}: Features umgekehrt, Dienste abmelden, Executor und Verbindungen schließen.</li>
 * </ol>
 *
 * <p>Dienste holen andere Plugins über {@link #services()} oder den Bukkit-{@code ServicesManager}. Jeder Dienst
 * ist ersetzbar ({@code services().register(...)}), etwa durch einen Adapter an ein bestehendes Kern-Plugin.
 */
public final class PaperCorePlugin extends JavaPlugin {

    private static volatile @Nullable ServiceRegistry registry;

    private @Nullable ServerConfig config;
    private @Nullable NetworkConnections connections;
    private @Nullable AsyncExecutor async;
    private @Nullable PaperCore core;
    private @Nullable FeatureManager<PaperCore> features;

    /**
     * Features in Startreihenfolge; ein neues Teilsystem ist genau eine Zeile hier. Deaktiviert wird umgekehrt.
     */
    private static List<CoreFeature<PaperCore>> features() {
        return List.of(
                new TextFeature(),
                new CommandFeature(),
                new BridgeFeature(),
                new ServerFeature(),
                new MapFeature(),
                new GuiFeature(),
                new SocialFeature(),
                new StatsServicesFeature(),
                new ProfileFeature(),
                new ChatGuardFeature(),
                new ChatFormatFeature(),
                new NickApplyFeature(),
                new AfkFeature(),
                new TabFeature(),
                new NametagFeature(),
                new BoardFeature(),
                new BarFeature(),
                new TitleFeature(),
                new HotbarFeature(),
                new CoinFeature(),
                new StatsFeature(),
                new HologramFeature(),
                new StatsWallFeature(),
                new NpcFeature());
    }

    /**
     * Dienste des Kerns (Spec 02 §8.1), z. B. {@code services().get(GuiService.class)}.
     *
     * @throws IllegalStateException wenn das Plugin nicht aktiv ist
     */
    public static ServiceRegistry services() {
        ServiceRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException(BuildInfo.BRAND_NAME + "Core ist nicht aktiv");
        }
        return current;
    }

    @Override
    public void onLoad() {
        try {
            config = ServerConfig.read(EnvReader.system(), PaperCorePlugin::hostname);
        } catch (ConfigException e) {
            getSLF4JLogger().error("FEHLER: {} – Server wird heruntergefahren", e.getMessage());
            getServer().shutdown();
        }
    }

    @Override
    public void onEnable() {
        Logger log = getSLF4JLogger();
        ServerConfig serverConfig = config;
        if (serverConfig == null) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        NetworkConnections network;
        try {
            network = NetworkConnections.open(serverConfig.node(), Retry.STARTUP, log);
        } catch (ConnectionException e) {
            log.error("FEHLER: {} – Server wird heruntergefahren (kein Betrieb ohne Datenbank)", e.getMessage(), e);
            getServer().shutdown();
            return;
        }
        connections = network;
        ServiceRegistry services = new ServiceRegistry(new BukkitServiceMirror(getServer().getServicesManager(), this));
        network.registerDefaults(services);
        AsyncExecutor executor = new AsyncExecutor(BuildInfo.BRAND_ID + "-async-", log);
        async = executor;
        MainThreadExecutor mainThread = new MainThreadExecutor(getServer()::isPrimaryThread, task -> {
            if (isEnabled()) {
                getServer().getScheduler().runTask(this, task);
            }
        }, log);
        PaperCore context = new PaperCore(this, log, serverConfig, services, executor, mainThread);
        core = context;
        registry = services;
        FeatureManager<PaperCore> manager = new FeatureManager<>(features(), log);
        features = manager;
        manager.enableAll(context);
        log.info("{} Core {} aktiv: Server {} (Template {}, {}, Umgebung {})", BuildInfo.BRAND_NAME,
                BuildInfo.VERSION, serverConfig.name(), serverConfig.template(), serverConfig.category(),
                serverConfig.node().stage());
    }

    @Override
    public void onDisable() {
        Logger log = getSLF4JLogger();
        PaperCore context = core;
        FeatureManager<PaperCore> manager = features;
        if (context != null && manager != null) {
            manager.disableAll(context);
            context.services().clear();
        }
        registry = null;
        AsyncExecutor executor = async;
        if (executor != null) {
            executor.close();
        }
        NetworkConnections network = connections;
        if (network != null) {
            try {
                network.close();
            } catch (IllegalStateException e) {
                log.warn("Verbindungen nicht sauber geschlossen", e);
            }
        }
        core = null;
        features = null;
        async = null;
        connections = null;
    }

    private static String hostname() {
        Optional<String> fromEnv = Optional.ofNullable(System.getenv("HOSTNAME")).filter(name -> !name.isBlank());
        if (fromEnv.isPresent()) {
            return fromEnv.get();
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
}
