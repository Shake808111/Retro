package net.retromc.core.i18n;

/**
 * Darstellungs-Ära (Spec 02 {@code ui.era}, Spec 01 Option {@code legacyStyle}).
 *
 * <p>{@link #LEGACY} wählt Text-Varianten mit Schlüssel-Endung {@code .legacy} (z. B. {@code prefix.friends.legacy})
 * und die LEGACY-Zahlformate aus Spec 02 §6.8.
 */
public enum UiEra {
    MODERN,
    LEGACY
}
