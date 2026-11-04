package net.retromc.core.data.nick;

import java.util.Objects;

/**
 * Mojang-signierte Skin-Textur: Eigenschaft {@code textures} mit Base64-Wert und Signatur.
 *
 * @param sourceRef Herkunft (Quell-UUID ohne Bindestriche bzw. MineSkin-ID)
 */
public record SkinTexture(String sourceRef, String value, String signature) {

    public SkinTexture {
        Objects.requireNonNull(sourceRef, "sourceRef");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(signature, "signature");
        if (value.isBlank() || signature.isBlank()) {
            throw new IllegalArgumentException("Textur ohne Wert oder Signatur: " + sourceRef);
        }
    }
}
