# game/survivalgames – SurvivalGames

**Gradle-Pfad:** `:game:survivalgames` · **Paket:** `net.retromc.survivalgames` · **Einstieg:** `SurvivalGamesPlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für den Modus SurvivalGames (SurvivalGames und QuickSG). Baut auf `game/game-framework` auf (Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke) und läuft zusammen mit `platform/paper-core` auf einem Spielserver; laut Notiz 07 (Abschnitt 3) eine Runde pro Server/Pod.

## Welches Vorbild-Feature wird nachgebaut?

Beim Vorbild seit **07.06.2024** im öffentlichen Netzwerk abgeschaltet (nur noch private Runden), Notiz 02, Q6.
Für den Nachbau gibt es nur historische Regeln:
- **SG**: 16 Spieler (16x1 / 8x2), Kreis-Spawn um Mitteltruhen, Truhen-Refill nach 10 Minuten,
  kein Abbauen/Bauen außer Pflanzen, danach Deathmatch-Arena.
- **QSG** (seit Juli 2014): 20 Spieler, verteilte Spawns, Kampf nach 10 s ohne Schutzzeit, kleine Karten,
  Truhen-Rüstung auf Gold-, Eisen- und Diamant-Brustplatten begrenzt.

## Kampfprofil

`LEGACY_1_8` nach Notiz 07, Abschnitt 1.


Quellen: `research_notes/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken vom Vorbild übernehmen (Notiz 07, Abschnitt 9 B).
