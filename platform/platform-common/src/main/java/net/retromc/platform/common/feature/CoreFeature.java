package net.retromc.platform.common.feature;

/**
 * Abgeschlossener Teil eines Kern-Plugins (Befehle, Bridge, GUIs, später Chat, Tablist, Scoreboard …).
 *
 * <p>Der {@link FeatureManager} aktiviert Features in der Reihenfolge ihrer Liste und deaktiviert nur die erfolgreich
 * aktivierten in umgekehrter Reihenfolge. Ein Fehler in {@link #enable} betrifft nur dieses Feature: er wird
 * protokolliert, das Plugin läuft weiter. Dienste deshalb erst am Ende von {@code enable} registrieren, wenn alle
 * Schritte erledigt sind, die fehlschlagen können.
 *
 * @param <C> Kontext der Plattform (Plugin, Konfiguration, Verbindungen, Dienste, Executor)
 */
public interface CoreFeature<C> {

    /** Kurzer, im Plugin eindeutiger Name für Logs, z. B. {@code Befehle}. */
    String name();

    /** Aktiviert das Feature. Jede Ausnahme gilt als Fehlschlag nur dieses Features. */
    void enable(C context) throws Exception;

    /** Gibt Ressourcen frei; wird nur nach erfolgreichem {@link #enable} aufgerufen. */
    default void disable(C context) throws Exception {
    }
}
