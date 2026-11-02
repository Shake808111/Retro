package net.retromc.platform.common.config;

/** Betriebsumgebung aus {@code NETWORK_ENV} (Spec 08 §6.15). */
public enum NetworkStage {
    /** Lokale Entwicklung (Docker Compose). */
    DEV,
    /** Testsystem. */
    STAGING,
    /** Produktivbetrieb. */
    PROD
}
