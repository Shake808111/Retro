package net.retromc.platform.common.connect;

import io.lettuce.core.api.StatefulRedisConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.Database;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.ValkeyMessageBus;
import net.retromc.platform.common.config.NodeConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Pflichtverbindungen eines Kern-Plugins in der Reihenfolge aus Spec 08 §6.2 Nr. 2: PostgreSQL-Pool → Valkey →
 * Nachrichtenbus. Jeder Schritt mit {@link Retry}; schlägt einer endgültig fehl, werden die bereits geöffneten
 * Verbindungen geschlossen und eine {@link ConnectionException} geworfen (kein Betrieb ohne Datenbank).
 *
 * <p>Keine Flyway-Migration: das Schema pflegt allein der Dienst {@code migrate} bzw. {@code controller migrate}.
 */
public final class NetworkConnections implements AutoCloseable {

    private final Database database;
    private final AsyncDatabase asyncDatabase;
    private final ValkeyConnector valkey;
    private final MessageBus bus;

    private NetworkConnections(Database database, AsyncDatabase asyncDatabase, ValkeyConnector valkey,
            MessageBus bus) {
        this.database = database;
        this.asyncDatabase = asyncDatabase;
        this.valkey = valkey;
        this.bus = bus;
    }

    /**
     * Baut alle Verbindungen auf (blockierend, nur beim Start aufrufen).
     *
     * @throws ConnectionException wenn eine Verbindung nach allen Versuchen fehlt
     */
    public static NetworkConnections open(NodeConfig config, Retry retry, Logger log) {
        Objects.requireNonNull(config, "config");
        @Nullable Database database = null;
        @Nullable ValkeyConnector valkey = null;
        try {
            database = retry.call("PostgreSQL", log, () -> new Database(config.database()));
            ValkeyConnector connector = new ValkeyConnector(config.valkeyUrl());
            valkey = connector;
            retry.run("Valkey", log, () -> ping(connector));
            MessageBus bus = retry.call("Nachrichtenbus", log, () -> new ValkeyMessageBus(connector, config.name()));
            log.info("Verbunden: PostgreSQL (Pool {}), Valkey, Nachrichtenbus als '{}'",
                    config.database().maxPoolSize(), config.name());
            return new NetworkConnections(database, AsyncDatabase.of(database), connector, bus);
        } catch (RuntimeException e) {
            List<Throwable> errors = new ArrayList<>();
            closeQuietly(valkey, errors);
            closeQuietly(database, errors);
            errors.forEach(e::addSuppressed);
            throw e;
        }
    }

    private static void ping(ValkeyConnector connector) {
        try (StatefulRedisConnection<String, String> connection = connector.connect()) {
            connection.sync().ping();
        }
    }

    /** Verbindungspool zu PostgreSQL. */
    public Database database() {
        return database;
    }

    /** Asynchroner Datenbankzugriff (virtuelle Threads, Spec 00 §4.1). */
    public AsyncDatabase asyncDatabase() {
        return asyncDatabase;
    }

    /** Valkey-Client für eigene Verbindungen (Schlüssel aus {@code ValkeyKeys}). */
    public ValkeyConnector valkey() {
        return valkey;
    }

    /** Nachrichtenbus über Valkey Pub/Sub ({@code net:*}). */
    public MessageBus bus() {
        return bus;
    }

    /** Schließt in umgekehrter Reihenfolge: Bus → Datenbank-Executor → Valkey → Pool. */
    @Override
    public void close() {
        List<Throwable> errors = new ArrayList<>();
        closeQuietly(bus, errors);
        closeQuietly(asyncDatabase, errors);
        closeQuietly(valkey, errors);
        closeQuietly(database, errors);
        if (!errors.isEmpty()) {
            IllegalStateException failure = new IllegalStateException("Verbindungen nicht sauber geschlossen");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void closeQuietly(@Nullable AutoCloseable resource, List<Throwable> errors) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            errors.add(e);
        }
    }
}
