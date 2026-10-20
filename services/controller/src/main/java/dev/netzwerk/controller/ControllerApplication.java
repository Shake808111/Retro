package dev.netzwerk.controller;

import dev.netzwerk.core.api.BuildInfo;
import dev.netzwerk.core.data.Database;
import dev.netzwerk.core.data.DatabaseConfig;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Einstiegspunkt des Controllers.
 *
 * <ul>
 *   <li>{@code migrate}: führt die Flyway-Migrationen aus {@code db/migrations} aus
 *       (Umgebungsvariablen NETWORK_DB_URL, NETWORK_DB_USER, NETWORK_DB_PASSWORD).</li>
 *   <li>ohne Argument: Platzhalter für Queue/Matchmaking (noch nicht umgesetzt).</li>
 * </ul>
 */
public final class ControllerApplication {

    private static final Logger LOG = System.getLogger(ControllerApplication.class.getName());

    private ControllerApplication() {
    }

    public static void main(String[] args) {
        LOG.log(Level.INFO, "{0}-Controller {1} startet.", BuildInfo.BRAND_NAME, BuildInfo.VERSION);
        if (args.length > 0 && "migrate".equals(args[0])) {
            try (Database database = new Database(DatabaseConfig.fromEnv())) {
                int executed = Migrations.migrate(database.dataSource());
                LOG.log(Level.INFO, "{0} Migration(en) ausgeführt.", executed);
            }
            return;
        }
        LOG.log(Level.INFO, "Queue/Matchmaking ist noch nicht umgesetzt (Gerüst).");
    }
}
