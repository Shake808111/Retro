# game/gungame – GunGame

**Gradle-Pfad:** `:game:gungame` · **Paket:** `net.retromc.gungame` · **Einstieg:** `GunGamePlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für den Modus GunGame (GunGame: dauerhaftes FFA mit Level-Aufstieg pro Kill). Baut auf `game/game-framework` auf (Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke) und läuft zusammen mit `platform/paper-core` auf einem Spielserver; laut Notiz 07 (Abschnitt 3) eine Runde pro Server/Pod.

## Welches GommeHD-Feature wird nachgebaut?

Notiz 03, Abschnitt 5: dauerhafter, lobbyartiger Free-for-all ohne Rundenende, bis zu 40 Spieler pro Server:
- jeder Kill +1 Level (bessere Ausrüstung und volle Heilung), jeder Tod −1 Level;
- Wasser oder Lava tötet sofort; Respawn in einer PvP-geschützten Mittelzone;
- Ausrüstung endet bei etwa voller Diamantrüstung mit Schutz I und Schärfe I.

**Sonderfall:** kein Rundenende – passt nicht zum Ablauf „eine Runde pro Pod“; der Server bleibt dauerhaft im Zustand INGAME.

## Kampfprofil

`LEGACY_1_8` nach Notiz 07, Abschnitt 1.


Quellen: `research_notes/GommeHD Analyse und Nachbau/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken von GommeHD übernehmen (Notiz 07, Abschnitt 9 B).
