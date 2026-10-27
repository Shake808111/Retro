package net.retromc.core.data;

import java.sql.SQLException;

/** Funktion über einer JDBC-Ressource, die {@link SQLException} werfen darf. */
@FunctionalInterface
public interface SqlFunction<I, O> {

    O apply(I input) throws SQLException;
}
