package dev.netzwerk.controller;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** Führt die Flyway-Migrationen aus {@code db/migrations} (im Jar unter {@code migrations/}) aus. */
public final class Migrations {

    public static final String LOCATION = "classpath:migrations";

    private Migrations() {
    }

    /** @return Anzahl der ausgeführten Migrationen */
    public static int migrate(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .load()
                .migrate()
                .migrationsExecuted;
    }
}
