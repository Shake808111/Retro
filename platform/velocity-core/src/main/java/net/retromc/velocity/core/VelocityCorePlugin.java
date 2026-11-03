package net.retromc.velocity.core;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.data.DatabaseConfig;
import net.retromc.platform.common.concurrent.AsyncExecutor;
import net.retromc.platform.common.config.ConfigException;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.config.NodeConfig;
import net.retromc.platform.common.connect.ConnectionException;
import net.retromc.platform.common.connect.NetworkConnections;
import net.retromc.platform.common.connect.Retry;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.feature.FeatureManager;
import net.retromc.platform.common.service.ServiceRegistry;
import net.retromc.velocity.core.bridge.BridgeFeature;
import net.retromc.velocity.core.clan.ClanFeature;
import net.retromc.velocity.core.friend.FriendFeature;
import net.retromc.velocity.core.joinme.JoinMeFeature;
import net.retromc.velocity.core.command.CommandFeature;
import net.retromc.velocity.core.lang.TextFeature;
import net.retromc.velocity.core.maintenance.MaintenanceFeature;
import net.retromc.velocity.core.motd.MotdFeature;
import net.retromc.velocity.core.msg.MsgFeature;
import net.retromc.velocity.core.nick.NickFeature;
import net.retromc.velocity.core.party.PartyFeature;
import net.retromc.velocity.core.punish.PunishFeature;
import net.retromc.velocity.core.report.ReportFeature;
import net.retromc.velocity.core.social.SocialServicesFeature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Kern-Plugin auf dem Velocity-Proxy, ID {@code <brandId>-core} (Spec 01 §1, Spec 08 §6.3). LuckPerms ist Pflicht.
 *
 * <ol>
 *   <li>{@code ProxyInitializeEvent}: Umgebungsvariablen lesen ({@code NETWORK_SERVER_NAME} = Proxy-ID), dann
 *       PostgreSQL → Valkey → Nachrichtenbus (je 3 Versuche); scheitert etwas, fährt der Proxy herunter. Danach die
 *       {@link #features() Features} in Reihenfolge.</li>
 *   <li>{@code ProxyShutdownEvent}: Features umgekehrt, Dienste abmelden, Executor und Verbindungen schließen.</li>
 * </ol>
 *
 * <p>Jeder Dienst ist über {@link #services()} ersetzbar, etwa durch einen Adapter an ein bestehendes Kern-Plugin.
 */
@Plugin(
        id = BuildInfo.BRAND_ID + "-core",
        name = BuildInfo.BRAND_NAME + "Core",
        version = BuildInfo.VERSION,
        description = "Netzwerk-Kern für den Velocity-Proxy",
        dependencies = {@Dependency(id = "luckperms")})
public final class VelocityCorePlugin {

    private static volatile @Nullable ServiceRegistry registry;

    private final ProxyServer proxy;
    private final PluginContainer container;
    private final Logger log;
    private @Nullable NetworkConnections connections;
    private @Nullable AsyncExecutor async;
    private @Nullable VelocityCore core;
    private @Nullable FeatureManager<VelocityCore> features;

    @Inject
    public VelocityCorePlugin(ProxyServer proxy, PluginContainer container, Logger log) {
        this.proxy = proxy;
        this.container = container;
        this.log = log;
    }

    /**
     * Features in Startreihenfolge; ein neues Teilsystem ist genau eine Zeile hier. Deaktiviert wird umgekehrt.
     */
    private static List<CoreFeature<VelocityCore>> features() {
        return List.of(
                new TextFeature(),
                new CommandFeature(),
                new BridgeFeature(),
                new SocialServicesFeature(),
                new PunishFeature(),
                new MaintenanceFeature(),
                new MotdFeature(),
                new MsgFeature(),
                new FriendFeature(),
                new PartyFeature(),
                new JoinMeFeature(),
                new ClanFeature(),
                new ReportFeature(),
                new NickFeature());
    }

    /**
     * Dienste des Proxy-Kerns, z. B. {@code services().get(ServerBridge.class)}.
     *
     * @throws IllegalStateException wenn das Plugin nicht aktiv ist
     */
    public static ServiceRegistry services() {
        ServiceRegistry current = registry;
        if (current == null) {
            throw new IllegalStateException(BuildInfo.BRAND_ID + "-core ist nicht aktiv");
        }
        return current;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        NodeConfig config;
        NetworkConnections network;
        try {
            config = NodeConfig.read(EnvReader.system(), DatabaseConfig.HIKARI_DEFAULT_POOL_SIZE);
            network = NetworkConnections.open(config, Retry.STARTUP, log);
        } catch (ConfigException | ConnectionException e) {
            log.error("FEHLER: {} – Proxy wird heruntergefahren", e.getMessage(), e);
            proxy.shutdown();
            return;
        }
        connections = network;
        ServiceRegistry services = new ServiceRegistry();
        network.registerDefaults(services);
        AsyncExecutor executor = new AsyncExecutor(BuildInfo.BRAND_ID + "-async-", log);
        async = executor;
        VelocityCore context = new VelocityCore(this, container, proxy, log, config, services, executor);
        core = context;
        registry = services;
        FeatureManager<VelocityCore> manager = new FeatureManager<>(features(), log);
        features = manager;
        manager.enableAll(context);
        log.info("{} Core {} aktiv: Proxy {} (Umgebung {}), {} Backend-Server registriert", BuildInfo.BRAND_NAME,
                BuildInfo.VERSION, config.name(), config.stage(), proxy.getAllServers().size());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        VelocityCore context = core;
        FeatureManager<VelocityCore> manager = features;
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
}
