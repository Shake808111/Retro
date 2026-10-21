# services/controller – Netzwerk-Controller

**Gradle-Pfad:** `:services:controller` · **Paket:** `net.retromc.controller` · **Einstieg:** `ControllerApplication` (Java-Anwendung) · **Status:** Gerüst (Stub)

## Verantwortung

Eigenständiger Dienst (Notiz 07, Abschnitt 3):
- Queue und Matchmaking (zunächst FIFO nach Partygröße), Partys bleiben zusammen;
- Server-Registry (Valkey `server:<name>`), später Agones-`GameServerAllocation`;
- weist Velocity an, Spieler zu verbinden;
- führt die Datenbank-Migrationen aus: `./gradlew :services:controller:run --args=migrate`.

## Welches GommeHD-Feature wird nachgebaut?

Spielersicht: das NPC-Joinsystem der Lobby („Rechtsklick tritt der nächsten Runde bei“, Notiz 05, Abschnitt 1) braucht im Hintergrund diese Queue. **LÜCKE:** GommeHDs eigene Orchestrierung ist nicht öffentlich dokumentiert (Notiz 07, Abschnitt 3, Gaps); schließen ließe sie sich nur über Aussagen des GommeHD-Teams.


Quellen: `research_notes/GommeHD Analyse und Nachbau/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken von GommeHD übernehmen (Notiz 07, Abschnitt 9 B).
