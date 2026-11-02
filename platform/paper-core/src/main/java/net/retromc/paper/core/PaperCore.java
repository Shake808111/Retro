package net.retromc.paper.core;

import java.util.Objects;
import net.retromc.paper.core.config.ServerConfig;
import net.retromc.platform.common.concurrent.AsyncExecutor;
import net.retromc.platform.common.concurrent.MainThreadExecutor;
import net.retromc.platform.common.service.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

/**
 * Kontext der Features von {@code ${brandName}Core}. Datenbank, Valkey, Nachrichtenbus, Texte und alle anderen
 * Dienste kommen ausschließlich aus {@link #services()}, damit sie austauschbar bleiben.
 *
 * @param plugin das Kern-Plugin
 * @param log Logger des Plugins
 * @param config Konfiguration aus den Umgebungsvariablen
 * @param services Service-Registry (Standard- und Ersatz-Implementierungen)
 * @param async Executor für blockierende Arbeit
 * @param mainThread Rückkehr auf den Haupt-Thread
 */
public record PaperCore(JavaPlugin plugin, Logger log, ServerConfig config, ServiceRegistry services,
        AsyncExecutor async, MainThreadExecutor mainThread) {

    public PaperCore {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(async, "async");
        Objects.requireNonNull(mainThread, "mainThread");
    }
}
