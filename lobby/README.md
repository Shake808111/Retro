# lobby – Lobby/Hub

**Gradle-Pfad:** `:lobby` · **Paket:** `dev.netzwerk.lobby` · **Einstieg:** `LobbyPlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für Lobby-Server: Navigator, NPC-Joinsystem, Hologramme, tägliche Belohnung, Jump & Run (Aufteilung nach Notiz 07, Abschnitt 6). Läuft zusammen mit `platform/paper-core`; NPCs/Hologramme laut Notiz 07, Abschnitt 5 über FancyNpcs bzw. eigene `TextDisplay`-Schicht.

## Welches GommeHD-Feature wird nachgebaut?

Notiz 05, Abschnitt 1 (Stand Oktober 2026):
- komplett neu gebaute 1.21-Lobby mit **NPC-basiertem Joinsystem** statt Join-Schildern:
  Rechtsklick tritt der nächsten Runde bei, Linksklick zeigt die kommenden Runden
  (vermutlich Frühjahr 2026 zusammen mit dem Global Level System veröffentlicht);
- Hotbar laut Belegen mindestens: Navigator (Kompass), Profil (Kopf), Lobbywechsler, Spieler verstecken;
- Silent Lobby und Auto-Nick wurden im März 2026 aus der Hotbar ins Lobbywechsler- bzw. Profilmenü verschoben;
- seit 18.10.2026 ein neues Cosmetic-Menü.
Exakte Slot-Belegung, Texte und Layouts sind laut Notiz 05 nicht verifiziert (LÜCKE).


Quellen: `research_notes/GommeHD Analyse und Nachbau/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken von GommeHD übernehmen (Notiz 07, Abschnitt 9 B).
