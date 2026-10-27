package net.retromc.core.data.testing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;
import net.retromc.core.data.DataAccessException;
import net.retromc.core.data.SqlFunction;

/**
 * Transaktions-Helfer für Tests: Arbeit, die am Ende immer zurückgerollt wird ({@link #rollbackAfter}), Arbeit mit
 * Commit ({@link #committed}) und kurze Abfragen für Prüfungen ({@link #count}, {@link #exists}).
 */
public final class TestTransactions {

    private TestTransactions() {
    }

    /** Führt die Arbeit in einer Transaktion aus und rollt sie danach zurück; die Datenbank bleibt unverändert. */
    public static <T> T rollbackAfter(DataSource dataSource, SqlFunction<Connection, T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                return work.apply(connection);
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Testtransaktion fehlgeschlagen", e);
        }
    }

    /** Führt die Arbeit in einer Transaktion aus und schreibt sie fest (Rollback bei Fehlern). */
    public static <T> T committed(DataSource dataSource, SqlFunction<Connection, T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Testtransaktion fehlgeschlagen", e);
        }
    }

    /** Ergebnis von {@code SELECT count(*) ...} mit optionalen Parametern. */
    public static long count(DataSource dataSource, String countSql, Object... parameters) {
        return committed(dataSource, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        });
    }

    /** Ob die Tabelle {@code schema.table} existiert ({@code public}, wenn ohne Schema angegeben). */
    public static boolean exists(DataSource dataSource, String qualifiedTable) {
        return count(dataSource, "SELECT count(*) FROM pg_catalog.pg_class WHERE oid = to_regclass(?)",
                qualifiedTable) > 0;
    }
}
