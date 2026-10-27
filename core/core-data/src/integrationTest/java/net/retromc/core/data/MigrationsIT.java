package net.retromc.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import net.retromc.core.data.testing.TestDatabases;
import net.retromc.core.data.testing.TestExecutors;
import net.retromc.core.data.testing.TestTransactions;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Alle Migrationen laufen in Reihenfolge auf frischem PostgreSQL 18; der zweite Lauf ist „up to date“. */
@Tag("integration")
class MigrationsIT {

    private static final List<String> EXPECTED_VERSIONS = List.of("1", "1.1", "1.2", "2.1", "8.1");

    @Test
    void alleMigrationenLaufenInReihenfolge() {
        try (Database database = TestDatabases.freshDatabase()) {
            DataSource dataSource = database.dataSource();
            MigrateResult first = TestDatabases.migrate(dataSource);
            assertTrue(first.success);
            assertEquals(EXPECTED_VERSIONS.size(), first.migrationsExecuted);

            List<String> applied = new ArrayList<>();
            for (MigrationInfo info : TestDatabases.flyway(dataSource).info().applied()) {
                applied.add(info.getVersion().getVersion());
            }
            assertEquals(EXPECTED_VERSIONS, applied);

            MigrateResult second = TestDatabases.migrate(dataSource);
            assertEquals(0, second.migrationsExecuted, "zweiter Lauf muss up to date sein");
        }
    }

    @Test
    void tabellenUndSeedVorhanden() {
        try (Database database = TestDatabases.migratedDatabase()) {
            DataSource dataSource = database.dataSource();
            for (String table : List.of("social.ignore", "moderation.reason", "core.network_setting",
                    "stats.daily", "stats.alltime", "core.display_anchor", "infra.server_instance",
                    "infra.map_version", "integ.api_token")) {
                assertTrue(TestTransactions.exists(dataSource, table), table + " fehlt");
            }
            assertEquals(11, TestTransactions.count(dataSource, "SELECT count(*) FROM moderation.reason"));
            assertEquals(1, TestTransactions.count(dataSource,
                    "SELECT count(*) FROM moderation.reason WHERE code = ? AND type = 'BAN'", "hacking"));
            assertEquals(4, TestTransactions.count(dataSource,
                    "SELECT count(*) FROM moderation.reason WHERE type = 'MUTE'"));
        }
    }

    @Test
    void rollbackHelferUndAsyncDatenbank() {
        try (Database database = TestDatabases.migratedDatabase();
                AsyncDatabase async = TestExecutors.asyncDatabase(database.dataSource())) {
            DataSource dataSource = database.dataSource();
            TestTransactions.rollbackAfter(dataSource, connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM moderation.reason WHERE code = 'chat_spam'")) {
                    return statement.executeUpdate();
                }
            });
            assertEquals(11, TestTransactions.count(dataSource, "SELECT count(*) FROM moderation.reason"));

            long reasons = TestExecutors.await(async.query(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT count(*) FROM moderation.reason");
                        ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }));
            assertEquals(11, reasons);
            assertThrows(DataAccessException.class, () -> TestExecutors.await(async.query(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM fehlt.nicht_da")) {
                    return statement.execute();
                }
            })));
        }
    }
}
