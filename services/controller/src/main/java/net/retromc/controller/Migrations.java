package net.retromc.controller;

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
                // Versionen folgen der Spec-Nummer (V<Spec>_<n>): V3_1 … V7_1 kommen nach V8_1 hinzu.
                .outOfOrder(true)
                .load()
                .migrate()
                .migrationsExecuted;
    }
}
