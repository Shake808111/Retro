# game/cores – Cores

**Gradle-Pfad:** `:game:cores` · **Paket:** `net.retromc.cores` · **Einstieg:** `CoresPlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für den Modus Cores (Cores: zwei Teams verteidigen Beacon-Cores). Baut auf `game/game-framework` auf (Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke) und läuft zusammen mit `platform/paper-core` auf einem Spielserver; laut Notiz 07 (Abschnitt 3) eine Runde pro Server/Pod.

## Welches GommeHD-Feature wird nachgebaut?

Notiz 03, Abschnitt 3 (bei GommeHD am **07.06.2024** abgeschaltet, nur noch private Supremium-Runden):
- 2 Teams (Rot gegen Blau) auf einer Sky-Map; jedes Team verteidigt mehrere Beacon-„Cores“;
  wer zuerst alle gegnerischen Cores zerstört, gewinnt;
- Angriffsalarm, Abbaulähmung nahe gegnerischer Cores, Respawn mit Starter-Kit (aufwertbar über
  Eisen-, Gold- und Diamantblöcke), Kartentruhen, +4 Pfeile pro Bogen-Kill, Cores-Level, ClanWar-Unterstützung.

## Kampfprofil

`LEGACY_1_8` nach Notiz 07, Abschnitt 1.


Quellen: `research_notes/GommeHD Analyse und Nachbau/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken von GommeHD übernehmen (Notiz 07, Abschnitt 9 B).
