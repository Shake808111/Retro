package net.retromc.core.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.retromc.core.api.BuildInfo;
import javax.sql.DataSource;

/**
 * Verbindungspool zu PostgreSQL. Ein Exemplar pro Prozess (Plugin oder Dienst).
 * Schema-Änderungen laufen ausschließlich über Flyway-Migrationen in {@code db/migrations}.
 */
public final class Database implements AutoCloseable {

    /** JDBC-Treiber von PostgreSQL. */
    public static final String DRIVER_CLASS = "org.postgresql.Driver";

    private final HikariDataSource dataSource;

    public Database(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        // Treiber ausdrücklich setzen: In Plugin-Jars sieht DriverManager den Treiber aus dem Plugin-Classloader
        // nicht ("No suitable driver"), daher lädt Hikari ihn über den Klassennamen.
        hikari.setDriverClassName(DRIVER_CLASS);
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.maxPoolSize());
        hikari.setPoolName(BuildInfo.BRAND_ID + "-db");
        this.dataSource = new HikariDataSource(hikari);
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
