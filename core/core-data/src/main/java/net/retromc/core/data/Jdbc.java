package net.retromc.core.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/** Kleine Helfer für JDBC-Zugriffe der Repositories (PostgreSQL: {@code UUID}, {@code TIMESTAMPTZ}). */
public final class Jdbc {

    private Jdbc() {
    }

    /** Zeile eines {@link ResultSet} in ein Objekt umwandeln. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    /** Setzt die Parameter in Reihenfolge; {@link Instant} wird {@code TIMESTAMPTZ}, {@code null} bleibt typlos. */
    public static PreparedStatement bind(PreparedStatement statement, @Nullable Object... parameters)
            throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            Object value = parameters[i];
            if (value == null) {
                statement.setNull(i + 1, Types.NULL);
            } else if (value instanceof Instant instant) {
                statement.setObject(i + 1, timestamp(instant));
            } else {
                statement.setObject(i + 1, value);
            }
        }
        return statement;
    }

    public static <T> List<T> list(Connection connection, String sql, RowMapper<T> mapper,
            @Nullable Object... parameters) throws SQLException {
        try (PreparedStatement statement = bind(connection.prepareStatement(sql), parameters);
                ResultSet rows = statement.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rows.next()) {
                result.add(mapper.map(rows));
            }
            return result;
        }
    }

    public static <T> Optional<T> one(Connection connection, String sql, RowMapper<T> mapper,
            @Nullable Object... parameters) throws SQLException {
        List<T> rows = list(connection, sql, mapper, parameters);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public static int update(Connection connection, String sql, @Nullable Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = bind(connection.prepareStatement(sql), parameters)) {
            return statement.executeUpdate();
        }
    }

    public static long count(Connection connection, String sql, @Nullable Object... parameters)
            throws SQLException {
        return one(connection, sql, row -> row.getLong(1), parameters).orElse(0L);
    }

    public static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static @Nullable Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static @Nullable UUID uuid(ResultSet row, String column) throws SQLException {
        return row.getObject(column, UUID.class);
    }

    public static UUID requireUuid(ResultSet row, String column) throws SQLException {
        UUID value = uuid(row, column);
        if (value == null) {
            throw new SQLException("Spalte " + column + " ist leer");
        }
        return value;
    }

    /** Valkey-Ergebnis als {@link CompletableFuture}. */
    public static <T> CompletableFuture<T> future(CompletionStage<T> stage) {
        return stage.toCompletableFuture();
    }

    /** Entpackt {@link CompletionException} für Fehlerprüfungen. */
    public static Throwable cause(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
