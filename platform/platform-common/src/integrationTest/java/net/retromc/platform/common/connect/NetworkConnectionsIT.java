package net.retromc.platform.common.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import net.retromc.core.api.event.MaintenanceChanged;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.DatabaseConfig;
import net.retromc.core.data.testing.TestDatabases;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.config.NetworkStage;
import net.retromc.platform.common.config.NodeConfig;
import net.retromc.platform.common.service.ServiceRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Pflichtverbindungen gegen echte Container (Spec 08 §6.2 Nr. 2). */
@Tag("integration")
class NetworkConnectionsIT {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkConnectionsIT.class);

    private final List<Duration> pauses = new ArrayList<>();
    private final Retry retry = new Retry(3, Duration.ofSeconds(2), pauses::add);

    private static NodeConfig config(String valkeyUrl) {
        PostgreSQLContainer pg = TestDatabases.postgres();
        return new NodeConfig("lobby-1", NetworkStage.DEV,
                new DatabaseConfig(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(), 2), valkeyUrl);
    }

    @Test
    void oeffnetDatenbankValkeyUndBus() throws Exception {
        try (NetworkConnections connections = NetworkConnections.open(config(TestDatabases.valkeyUrl()), retry,
                LOG)) {
            ServiceRegistry services = new ServiceRegistry();
            connections.registerDefaults(services);
            assertSame(connections.bus(), services.get(MessageBus.class));
            assertSame(connections.asyncDatabase(), services.get(AsyncDatabase.class));
            assertSame(connections.database().dataSource(), services.get(DataSource.class));
            assertTrue(connections.asyncDatabase().query(connection -> connection.isValid(2))
                    .get(10, TimeUnit.SECONDS));

            CompletableFuture<Envelope<MaintenanceChanged>> received = new CompletableFuture<>();
            Registration subscription = connections.bus().subscribe(MessageType.MAINTENANCE_CHANGED,
                    received::complete).get(10, TimeUnit.SECONDS);
            try {
                connections.bus().publish(MessageType.MAINTENANCE_CHANGED, new MaintenanceChanged(true))
                        .get(10, TimeUnit.SECONDS);
                Envelope<MaintenanceChanged> envelope = received.get(10, TimeUnit.SECONDS);
                assertEquals("lobby-1", envelope.src());
                assertTrue(envelope.d().enabled());
            } finally {
                subscription.close();
            }
        }
        assertTrue(pauses.isEmpty());
    }

    @Test
    void ohneValkeyScheitertDerStartNachDreiVersuchen() {
        // Port 1 ist nie belegt: Verbindung wird sofort abgelehnt.
        ConnectionException error = assertThrows(ConnectionException.class,
                () -> NetworkConnections.open(config("redis://127.0.0.1:1"), retry, LOG));

        assertTrue(error.getMessage().startsWith("Valkey nicht erreichbar nach 3 Versuchen"), error.getMessage());
        assertEquals(2, pauses.size());
    }
}
