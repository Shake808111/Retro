package net.retromc.paper.core.service;

import java.util.Objects;
import net.retromc.platform.common.service.ServiceRegistry;
import net.retromc.platform.common.service.ServiceRegistry.Kind;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/**
 * Spiegelt die Service-Registry in den Bukkit-{@code ServicesManager} (Spec 02 §8.1): Standard-Implementierungen mit
 * {@link ServicePriority#Low}, Ersatz mit {@link ServicePriority#Highest}; {@code ServicesManager.load} liefert so
 * immer die aktive Implementierung.
 */
public final class BukkitServiceMirror implements ServiceRegistry.Listener {

    private final ServicesManager manager;
    private final Plugin plugin;

    public BukkitServiceMirror(ServicesManager manager, Plugin plugin) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public <T> void registered(Class<T> type, T service, Kind kind) {
        ServicePriority priority = kind == Kind.OVERRIDE ? ServicePriority.Highest : ServicePriority.Low;
        manager.register(type, service, plugin, priority);
    }

    @Override
    public <T> void unregistered(Class<T> type, T service, Kind kind) {
        manager.unregister(type, service);
    }
}
