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

    private final HikariDataSource dataSource;

    public Database(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
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
