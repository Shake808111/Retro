package net.retromc.platform.common.connect;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/** Eine Pflichtverbindung (PostgreSQL, Valkey, Nachrichtenbus) ließ sich nicht aufbauen. */
public final class ConnectionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConnectionException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
