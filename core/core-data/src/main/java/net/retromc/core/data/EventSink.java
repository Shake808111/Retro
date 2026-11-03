package net.retromc.core.data;

/**
 * Ausgang für Netzwerk-Ereignisse der Repositories (Records aus {@code net.retromc.core.api.event}, z. B.
 * {@code PunishIssued}, {@code ClanUpdate}). core-data kennt den Nachrichtenbus nicht; die Plattform verbindet die
 * Senke mit {@code net:*} (platform-common {@code BusEventSink}). Fehler beim Senden dürfen den Aufrufer nicht
 * stören.
 */
@FunctionalInterface
public interface EventSink {

    /** Verwirft alle Ereignisse (Tests, Werkzeuge). */
    EventSink NONE = event -> {
    };

    void publish(Object event);
}
