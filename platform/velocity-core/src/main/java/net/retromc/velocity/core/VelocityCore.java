package net.retromc.velocity.core;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Objects;
import net.retromc.platform.common.concurrent.AsyncExecutor;
import net.retromc.platform.common.config.NodeConfig;
import net.retromc.platform.common.service.ServiceRegistry;
import org.slf4j.Logger;

/**
 * Kontext der Features von {@code <brandId>-core}. Datenbank, Valkey, Nachrichtenbus, Texte und alle anderen Dienste
 * kommen ausschließlich aus {@link #services()}, damit sie austauschbar bleiben.
 *
 * @param plugin Plugin-Instanz (für Events und Scheduler)
 * @param container Plugin-Container (für Cloud)
 * @param proxy der Proxy
 * @param log Logger des Plugins
 * @param config Konfiguration aus den Umgebungsvariablen ({@code name} = Proxy-ID)
 * @param services Service-Registry (Standard- und Ersatz-Implementierungen)
 * @param async Executor für blockierende Arbeit
 */
public record VelocityCore(VelocityCorePlugin plugin, PluginContainer container, ProxyServer proxy, Logger log,
        NodeConfig config, ServiceRegistry services, AsyncExecutor async) {

    public VelocityCore {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(async, "async");
    }
}
