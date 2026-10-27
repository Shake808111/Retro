package net.retromc.core.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import net.retromc.core.api.BuildInfo;

/**
 * Asynchroner Datenbankzugriff (Spec 00 §4.1: DB nur asynchron über {@link CompletableFuture}). Gemeinsame Basis der
 * Repositories: jede Arbeit läuft auf einem eigenen Executor (Standard: virtuelle Threads), bekommt eine Verbindung
 * aus dem Pool und gibt sie danach zurück. Fehler kommen als {@link DataAccessException} in der Zukunft an.
 *
 * <p>Auf Paper kehrt der Aufrufer mit {@code Bukkit.getScheduler().runTask} auf den Haupt-Thread zurück.
 */
public final class AsyncDatabase implements AutoCloseable {

    /** Versuche bei Serialisierungs- oder Deadlock-Konflikten ({@link DataAccessException#isRetryable()}). */
    public static final int MAX_ATTEMPTS = 3;

    private final DataSource dataSource;
    private final ExecutorService executor;
    private final boolean ownsExecutor;

    /** Mit eigenem Executor auf virtuellen Threads ({@code <brandId>-db-n}); wird mit {@link #close()} beendet. */
    public AsyncDatabase(DataSource dataSource) {
        this(dataSource, Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(BuildInfo.BRAND_ID + "-db-", 0).factory()), true);
    }

    /** Mit fremdem Executor (bleibt beim Schließen offen). */
    public AsyncDatabase(DataSource dataSource, ExecutorService executor) {
        this(dataSource, executor, false);
    }

    private AsyncDatabase(DataSource dataSource, ExecutorService executor, boolean ownsExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownsExecutor = ownsExecutor;
    }

    public static AsyncDatabase of(Database database) {
        return new AsyncDatabase(database.dataSource());
    }

    /** Arbeit im Autocommit-Modus (Lesen, einzelne Anweisungen). */
    public <T> CompletableFuture<T> query(SqlFunction<Connection, T> work) {
        Objects.requireNonNull(work, "work");
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return work.apply(connection);
            } catch (SQLException e) {
                throw new CompletionException(new DataAccessException("Datenbankabfrage fehlgeschlagen", e));
            }
        }, executor);
    }

    /** Transaktion mit READ COMMITTED (PostgreSQL-Standard). */
    public <T> CompletableFuture<T> transaction(SqlFunction<Connection, T> work) {
        return transaction(Connection.TRANSACTION_READ_COMMITTED, work);
    }

    /**
     * Transaktion mit Isolationsstufe ({@link Connection#TRANSACTION_SERIALIZABLE} usw.): Commit bei Erfolg,
     * Rollback bei jeder Ausnahme; Konflikte paralleler Transaktionen werden bis zu {@link #MAX_ATTEMPTS}-mal
     * wiederholt. Die Arbeit muss deshalb wiederholbar sein (keine Seiteneffekte außerhalb der Datenbank).
     */
    public <T> CompletableFuture<T> transaction(int isolation, SqlFunction<Connection, T> work) {
        Objects.requireNonNull(work, "work");
        return CompletableFuture.supplyAsync(() -> {
            for (int attempt = 1; ; attempt++) {
                try {
                    return inTransaction(isolation, work);
                } catch (DataAccessException e) {
                    if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
                        throw new CompletionException(e);
                    }
                }
            }
        }, executor);
    }

    private <T> T inTransaction(int isolation, SqlFunction<Connection, T> work) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            int previousIsolation = connection.getTransactionIsolation();
            connection.setTransactionIsolation(isolation);
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
                connection.setTransactionIsolation(previousIsolation);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Transaktion fehlgeschlagen", e);
        }
    }

    /** Executor der Datenbank-Arbeit (für Folgeschritte, die blockieren dürfen). */
    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        if (ownsExecutor) {
            executor.close();
        }
    }
}
