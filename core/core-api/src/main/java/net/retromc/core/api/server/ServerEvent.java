package net.retromc.core.api.server;

import java.util.Objects;

/**
 * Änderung der Server-Registry aus {@code net:server-state} ({@code SERVER_UP}, {@code SERVER_STATE},
 * {@code SERVER_DOWN}, Spec 08 §2.4) oder aus dem Abgleich alle 30 s (Spec 08 §6.3 Nr. 2).
 */
public sealed interface ServerEvent {

    /** Name des betroffenen Servers. */
    String server();

    /** Neuer Server (Registrierung beim Proxy). */
    record Registered(ServerInfo info) implements ServerEvent {

        public Registered {
            Objects.requireNonNull(info, "info");
        }

        @Override
        public String server() {
            return info.name();
        }
    }

    /** Zustands- oder Datenänderung eines bekannten Servers. */
    record Updated(ServerInfo previous, ServerInfo current) implements ServerEvent {

        public Updated {
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(current, "current");
        }

        @Override
        public String server() {
            return current.name();
        }

        public boolean stateChanged() {
            return previous.state() != current.state();
        }
    }

    /** Server abgemeldet (SERVER_DOWN oder Heartbeat abgelaufen). */
    record Unregistered(String server, String reason) implements ServerEvent {

        public Unregistered {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
