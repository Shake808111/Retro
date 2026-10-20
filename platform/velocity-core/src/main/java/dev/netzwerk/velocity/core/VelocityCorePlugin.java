package dev.netzwerk.velocity.core;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.netzwerk.core.api.BuildInfo;
import org.slf4j.Logger;

/**
 * Einstiegspunkt auf dem Velocity-Proxy.
 *
 * <p>Geplante Aufgaben (Notiz 07, Abschnitte 4 und 6): Login-Prüfung (Banns), Freunde/Party/Clan-
 * Befehle, Routing in Lobby und Spiele, MOTD/Wartungsmodus, Chat, Profil-Laden nach Valkey.
 * Bisher nur ein Gerüst. Plugin-ID und Name kommen aus {@link BuildInfo} (gradle.properties).
 */
@Plugin(
        id = BuildInfo.BRAND_ID + "-core",
        name = BuildInfo.BRAND_NAME + "-Core",
        version = BuildInfo.VERSION,
        description = "Netzwerk-Kern für den Velocity-Proxy")
public final class VelocityCorePlugin {

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public VelocityCorePlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("{} Core {} aktiv (Gerüst), {} Backend-Server registriert.",
                BuildInfo.BRAND_NAME, BuildInfo.VERSION, proxy.getAllServers().size());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("{} Core gestoppt.", BuildInfo.BRAND_NAME);
    }
}
