package net.retromc.platform.common.feature;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Zustand eines Features im {@link FeatureManager}.
 *
 * @param name Name des Features
 * @param state aktueller Zustand
 * @param error Ursache bei {@link State#FAILED} oder bei einem Fehler beim Deaktivieren, sonst {@code null}
 */
public record FeatureStatus(String name, State state, @Nullable Throwable error) {

    /** Lebenszyklus eines Features. */
    public enum State {
        /** Noch nicht aktiviert. */
        PENDING,
        /** Aktiv. */
        ENABLED,
        /** Aktivierung fehlgeschlagen; das Feature bleibt aus. */
        FAILED,
        /** Nach erfolgreicher Aktivierung wieder deaktiviert. */
        DISABLED
    }

    public FeatureStatus {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
    }
}
