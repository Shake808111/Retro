package net.retromc.core.api.server;

/** Kategorie eines Server-Templates (Spec 08 §6.5, Feld {@code category}). */
public enum ServerCategory {
    /** Lobbys: Hauptlobby, Premium-/Silent-Lobby, Modus-Lobbys {@code lobby-{mode}}. */
    LOBBY,
    /** Spielserver, eine Runde pro Server ({@code oneMatch}). */
    GAME,
    /** Dauerhafte Server (CityBuild, SMP). */
    PERSISTENT
}
