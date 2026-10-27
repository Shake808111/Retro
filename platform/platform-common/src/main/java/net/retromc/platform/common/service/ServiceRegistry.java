package net.retromc.platform.common.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.retromc.core.api.util.Registration;

/**
 * Dienste eines Kern-Plugins, je Schnittstelle genau eine Implementierung (Spec 02 §8.1). Features registrieren ihre
 * Dienste in {@code enable}, andere Plugins holen sie über den statischen Zugang des Plugins
 * ({@code PaperCorePlugin.services()}, {@code VelocityCorePlugin.services()}).
 *
 * <p>Ein {@link Listener} spiegelt An- und Abmeldungen, auf Paper in den Bukkit-{@code ServicesManager}.
 * Thread-sicher; der Listener wird unter der Sperre der Registry aufgerufen.
 */
public final class ServiceRegistry {

    /** Beobachter der Registry. */
    public interface Listener {

        <T> void registered(Class<T> type, T service);

        <T> void unregistered(Class<T> type, T service);
    }

    private static final Listener NONE = new Listener() {
        @Override
        public <T> void registered(Class<T> type, T service) {
        }

        @Override
        public <T> void unregistered(Class<T> type, T service) {
        }
    };

    private final Map<Class<?>, Object> services = new LinkedHashMap<>();
    private final Listener listener;

    public ServiceRegistry() {
        this(NONE);
    }

    public ServiceRegistry(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Registriert {@code service} unter {@code type}. {@link Registration#close()} meldet genau diese Implementierung
     * wieder ab.
     *
     * @throws IllegalStateException wenn für {@code type} schon ein Dienst registriert ist
     */
    public synchronized <T> Registration register(Class<T> type, T service) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(service, "service");
        if (!type.isInstance(service)) {
            throw new IllegalArgumentException(service.getClass().getName() + " implementiert " + type.getName()
                    + " nicht");
        }
        if (services.containsKey(type)) {
            throw new IllegalStateException("Dienst bereits registriert: " + type.getName());
        }
        services.put(type, service);
        listener.registered(type, service);
        return () -> unregister(type, service);
    }

    /** Meldet {@code service} ab, falls er unter {@code type} registriert ist; liefert {@code true} bei Erfolg. */
    public synchronized <T> boolean unregister(Class<T> type, T service) {
        if (services.get(type) != service) {
            return false;
        }
        services.remove(type);
        listener.unregistered(type, service);
        return true;
    }

    /** Der Dienst zu {@code type}, falls registriert. */
    public synchronized <T> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    /**
     * Der Dienst zu {@code type}.
     *
     * @throws IllegalStateException wenn er nicht registriert ist (Feature fehlgeschlagen oder noch nicht aktiv)
     */
    public <T> T get(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("Dienst nicht verfügbar: " + type.getName()));
    }

    /** Registrierte Schnittstellen in Registrierungsreihenfolge. */
    public synchronized List<Class<?>> types() {
        return List.copyOf(services.keySet());
    }

    /** Meldet alle Dienste in umgekehrter Registrierungsreihenfolge ab. */
    public synchronized void clear() {
        List<Map.Entry<Class<?>, Object>> entries = new ArrayList<>(services.entrySet());
        Collections.reverse(entries);
        for (Map.Entry<Class<?>, Object> entry : entries) {
            unregisterEntry(entry.getKey(), entry.getValue());
        }
    }

    private <T> void unregisterEntry(Class<T> type, Object service) {
        unregister(type, type.cast(service));
    }
}
