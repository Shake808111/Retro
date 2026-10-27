# game/ttt – TTT

**Gradle-Pfad:** `:game:ttt` · **Paket:** `net.retromc.ttt` · **Einstieg:** `TttPlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für den Modus TTT (TTT: Innocent, Detective, Traitor). Baut auf `game/game-framework` auf (Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke) und läuft zusammen mit `platform/paper-core` auf einem Spielserver; laut Notiz 07 (Abschnitt 3) eine Runde pro Server/Pod.

## Welches Vorbild-Feature wird nachgebaut?

Notiz 03, Abschnitt 2: Vorbild-TTT („Trouble in Minecraft“) ist ein Klon des Garry's-Mod-Modus:
- Runden mit 12 Spielern (eine 24er-Variante wird erwähnt); Rollen Innocent, Detective und Traitor
  werden ca. 30 s nach dem Start verteilt;
- Rundendauer 10 Minuten, bei Zeitablauf gewinnen die Innocents;
- Traitor und Detective kaufen mit Punkten aus Kills in getrennten Shops; Leichen tragen Punkte;
- Traitor-Tester mit Falle, Karma-System, Traitor-/Detective-Pässe in der Wartelobby.
- Seit ca. 2022 keine Feature-Updates; aktuelle Verfügbarkeit unbestätigt.

Rechtlicher Hinweis (Notiz 07, Abschnitt 9 B): „TTT“ stammt aus Garry's Mod – keine fremden Assets übernehmen.

## Kampfprofil

`MODERN` ist **vorläufig**: Notiz 07 legt für TTT kein Kampfprofil fest.


Quellen: `research_notes/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken vom Vorbild übernehmen (Notiz 07, Abschnitt 9 B).
