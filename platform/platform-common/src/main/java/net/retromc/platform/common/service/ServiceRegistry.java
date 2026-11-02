package net.retromc.platform.common.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.retromc.core.api.util.Registration;
import org.jspecify.annotations.Nullable;

/**
 * Dienste eines Kern-Plugins (Spec 02 §8.1), je Schnittstelle höchstens eine Standard- und eine
 * Ersatz-Implementierung. Aktiv ist der Ersatz, sonst der Standard.
 *
 * <ul>
 *   <li>{@link #registerDefault}: die eigene Implementierung des Kerns (z. B. Ränge aus LuckPerms, Texte aus
 *       core-i18n, Datenbank aus {@code NETWORK_DB_*}).</li>
 *   <li>{@link #register}: Ersatz durch ein anderes Plugin, z. B. einen Adapter an ein bestehendes Kern-Plugin mit
 *       eigenen Rängen, Profilen, Texten oder eigener Datenbank. Wird der Ersatz abgemeldet, gilt wieder der
 *       Standard.</li>
 * </ul>
 *
 * <p>Nutzer halten deshalb keine Implementierung fest, sondern holen den Dienst bei jedem Gebrauch ({@link #get})
 * oder über {@link #supplier}. Ein {@link Listener} spiegelt Änderungen, auf Paper in den Bukkit-
 * {@code ServicesManager}. Thread-sicher; der Listener wird unter der Sperre der Registry aufgerufen.
 */
public final class ServiceRegistry {

    /** Art einer Registrierung. */
    public enum Kind {
        /** Implementierung des Kerns. */
        DEFAULT,
        /** Ersatz durch ein anderes Plugin; hat Vorrang. */
        OVERRIDE
    }

    /** Beobachter der Registry. */
    public interface Listener {

        <T> void registered(Class<T> type, T service, Kind kind);

        <T> void unregistered(Class<T> type, T service, Kind kind);
    }

    private static final Listener NONE = new Listener() {
        @Override
        public <T> void registered(Class<T> type, T service, Kind kind) {
        }

        @Override
        public <T> void unregistered(Class<T> type, T service, Kind kind) {
        }
    };

    /** Registrierungen je Schnittstelle. */
    private static final class Slot {

        @Nullable Object defaultService;
        @Nullable Object override;

        @Nullable Object active() {
            return override != null ? override : defaultService;
        }
    }

    private final Map<Class<?>, Slot> slots = new LinkedHashMap<>();
    private final Listener listener;

    public ServiceRegistry() {
        this(NONE);
    }

    public ServiceRegistry(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Registriert die Standard-Implementierung des Kerns.
     *
     * @throws IllegalStateException wenn es für {@code type} schon einen Standard gibt
     */
    public <T> Registration registerDefault(Class<T> type, T service) {
        return add(type, service, Kind.DEFAULT);
    }

    /**
     * Ersetzt den Standard für {@code type}; {@link Registration#close()} stellt ihn wieder her.
     *
     * @throws IllegalStateException wenn {@code type} schon ersetzt ist
     */
    public <T> Registration register(Class<T> type, T service) {
        return add(type, service, Kind.OVERRIDE);
    }

    private synchronized <T> Registration add(Class<T> type, T service, Kind kind) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(service, "service");
        if (!type.isInstance(service)) {
            throw new IllegalArgumentException(service.getClass().getName() + " implementiert " + type.getName()
                    + " nicht");
        }
        Slot slot = slots.computeIfAbsent(type, key -> new Slot());
        if (kind == Kind.DEFAULT ? slot.defaultService != null : slot.override != null) {
            throw new IllegalStateException((kind == Kind.DEFAULT ? "Standard" : "Ersatz")
                    + " bereits registriert: " + type.getName());
        }
        if (kind == Kind.DEFAULT) {
            slot.defaultService = service;
        } else {
            slot.override = service;
        }
        listener.registered(type, service, kind);
        return () -> unregister(type, service);
    }

    /** Meldet {@code service} ab (Standard oder Ersatz); liefert {@code true}, wenn er registriert war. */
    public synchronized <T> boolean unregister(Class<T> type, T service) {
        Slot slot = slots.get(type);
        if (slot == null) {
            return false;
        }
        Kind kind;
        if (slot.override == service) {
            slot.override = null;
            kind = Kind.OVERRIDE;
        } else if (slot.defaultService == service) {
            slot.defaultService = null;
            kind = Kind.DEFAULT;
        } else {
            return false;
        }
        if (slot.active() == null) {
            slots.remove(type);
        }
        listener.unregistered(type, service, kind);
        return true;
    }

    /** Die aktive Implementierung zu {@code type}, falls vorhanden. */
    public synchronized <T> Optional<T> find(Class<T> type) {
        Slot slot = slots.get(type);
        return slot == null ? Optional.empty() : Optional.ofNullable(type.cast(slot.active()));
    }

    /**
     * Die aktive Implementierung zu {@code type}.
     *
     * @throws IllegalStateException wenn keine registriert ist (Feature fehlgeschlagen oder noch nicht aktiv)
     */
    public <T> T get(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException("Dienst nicht verfügbar: " + type.getName()));
    }

    /** Liefert bei jedem Aufruf die dann aktive Implementierung (für Komponenten, die länger leben). */
    public <T> Supplier<T> supplier(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return () -> get(type);
    }

    /** {@code true}, wenn {@code type} gerade durch ein anderes Plugin ersetzt ist. */
    public synchronized boolean isOverridden(Class<?> type) {
        Slot slot = slots.get(type);
        return slot != null && slot.override != null;
    }

    /** Schnittstellen mit aktiver Implementierung in Registrierungsreihenfolge. */
    public synchronized List<Class<?>> types() {
        return List.copyOf(slots.keySet());
    }

    /** Meldet alle Implementierungen in umgekehrter Registrierungsreihenfolge ab (Ersatz vor Standard). */
    public synchronized void clear() {
        List<Map.Entry<Class<?>, Slot>> entries = new ArrayList<>(slots.entrySet());
        Collections.reverse(entries);
        for (Map.Entry<Class<?>, Slot> entry : entries) {
            Slot slot = entry.getValue();
            Object override = slot.override;
            Object defaultService = slot.defaultService;
            if (override != null) {
                remove(entry.getKey(), override);
            }
            if (defaultService != null) {
                remove(entry.getKey(), defaultService);
            }
        }
    }

    private <T> void remove(Class<T> type, Object service) {
        unregister(type, type.cast(service));
    }
}
