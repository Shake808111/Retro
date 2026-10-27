package net.retromc.core.data;

import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/** Unchecked-Hülle für {@link SQLException}; die SQLState-Prüfungen helfen bei Kollisionen und Wiederholungen. */
public class DataAccessException extends RuntimeException {

    /** PostgreSQL: unique_violation. */
    public static final String UNIQUE_VIOLATION = "23505";
    /** PostgreSQL: serialization_failure. */
    public static final String SERIALIZATION_FAILURE = "40001";
    /** PostgreSQL: deadlock_detected. */
    public static final String DEADLOCK_DETECTED = "40P01";

    public DataAccessException(String message, SQLException cause) {
        super(message, cause);
    }

    /** SQLState der ursächlichen {@link SQLException} oder {@code null}. */
    public @Nullable String sqlState() {
        return getCause() instanceof SQLException sql ? sql.getSQLState() : null;
    }

    /** Eindeutigkeit verletzt, z. B. Kollision einer Anzeige-ID ({@code PunishmentId}) → neue ID erzeugen. */
    public boolean isUniqueViolation() {
        return UNIQUE_VIOLATION.equals(sqlState());
    }

    /** Konflikt paralleler Transaktionen, die Wiederholung kann gelingen. */
    public boolean isRetryable() {
        String state = sqlState();
        return SERIALIZATION_FAILURE.equals(state) || DEADLOCK_DETECTED.equals(state);
    }
}
