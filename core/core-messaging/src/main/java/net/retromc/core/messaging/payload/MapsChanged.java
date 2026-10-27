package net.retromc.core.messaging.payload;

import java.util.Objects;

/** {@code MAPS_CHANGED {mode}} (net:controller; Spec 08 §8.3): Controller/CI → Spielserver. */
public record MapsChanged(String mode) {

    public MapsChanged {
        Objects.requireNonNull(mode, "mode");
    }
}
