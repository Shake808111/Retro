package net.retromc.core.data.testing;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.retromc.core.data.DataAccessException;
import net.retromc.core.data.Database;
import net.retromc.core.data.DatabaseConfig;
import net.retromc.core.data.ValkeyConnector;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Geteilte Testcontainer für Integrationstests aller Module (Spec 08 §6.16): ein PostgreSQL- und ein Valkey-Container
 * pro Test-JVM, beim ersten Zugriff gestartet und von Testcontainers am Ende entfernt. Jeder Test bekommt über
 * {@link #freshDatabase()} bzw. {@link #migratedDatabase()} eine eigene, leere Datenbank im selben Container.
 *
 * <p>Die Migrationen liegen als Klassenpfad-Ressourcen unter {@code migrations/} (aus {@code db/migrations}).
 */
public final class TestDatabases {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18.6-alpine");
    public static final DockerImageName VALKEY_IMAGE = DockerImageName.parse("valkey/valkey:8.1.10-alpine");
    public static final String MIGRATIONS = "classpath:migrations";
    public static final int VALKEY_PORT = 6379;
    /** Kleiner Pool je Testdatenbank, damit viele Datenbanken in einen Container passen. */
    public static final int TEST_POOL_SIZE = 4;

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestDatabases() {
    }

    /** Der geteilte PostgreSQL-Container (gestartet). */
    public static PostgreSQLContainer postgres() {
        return PostgresHolder.CONTAINER;
    }

    /** Der geteilte Valkey-Container (gestartet). */
    public static GenericContainer<?> valkey() {
        return ValkeyHolder.CONTAINER;
    }

    /** {@code redis://host:port} des Valkey-Containers. */
    public static String valkeyUrl() {
        GenericContainer<?> valkey = valkey();
        return "redis://" + valkey.getHost() + ":" + valkey.getMappedPort(VALKEY_PORT);
    }

    /** Neue Verbindung zum Valkey-Container; der Aufrufer schließt sie. */
    public static ValkeyConnector valkeyConnector() {
        return new ValkeyConnector(valkeyUrl());
    }

    /** Leert alle Valkey-Datenbanken (zwischen Tests). */
    public static void flushValkey() {
        try {
            ExecResult result = valkey().execInContainer("valkey-cli", "FLUSHALL");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("FLUSHALL fehlgeschlagen: " + result.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("FLUSHALL fehlgeschlagen", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FLUSHALL unterbrochen", e);
        }
    }

    /** Neue, leere Datenbank (ohne Migrationen) mit eigenem Pool. */
    public static Database freshDatabase() {
        String name = "t_" + ProcessHandle.current().pid() + "_" + COUNTER.incrementAndGet();
        PostgreSQLContainer pg = postgres();
        try (Connection admin = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        } catch (SQLException e) {
            throw new DataAccessException("Testdatenbank " + name + " nicht angelegt", e);
        }
        String url = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
                + "/" + name;
        return new Database(new DatabaseConfig(url, pg.getUsername(), pg.getPassword(), TEST_POOL_SIZE));
    }

    /** Neue Datenbank mit allen Migrationen aus {@code db/migrations}. */
    public static Database migratedDatabase() {
        Database database = freshDatabase();
        migrate(database.dataSource());
        return database;
    }

    /** Flyway mit den Projektmigrationen für diese Datenquelle. */
    public static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATIONS)
                .load();
    }

    /** Führt alle ausstehenden Migrationen aus. */
    public static MigrateResult migrate(DataSource dataSource) {
        return flyway(dataSource).migrate();
    }

    /** Lazy-Holder: der Container startet erst beim ersten Zugriff, einmal pro JVM. */
    private static final class PostgresHolder {

        static final PostgreSQLContainer CONTAINER = start();

        private static PostgreSQLContainer start() {
            PostgreSQLContainer container = new PostgreSQLContainer(POSTGRES_IMAGE)
                    .withDatabaseName("network")
                    .withUsername("network")
                    .withPassword("network");
            container.start();
            return container;
        }
    }

    /** Lazy-Holder für Valkey. */
    private static final class ValkeyHolder {

        static final GenericContainer<?> CONTAINER = start();

        private static GenericContainer<?> start() {
            GenericContainer<?> container = new GenericContainer<>(VALKEY_IMAGE).withExposedPorts(VALKEY_PORT);
            container.start();
            return container;
        }
    }
}
