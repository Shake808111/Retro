# game/masterbuilders – MasterBuilders

**Gradle-Pfad:** `:game:masterbuilders` · **Paket:** `net.retromc.masterbuilders` · **Einstieg:** `MasterBuildersPlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für den Modus MasterBuilders (MasterBuilders: Thema wählen, bauen, bewerten). Baut auf `game/game-framework` auf (Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke) und läuft zusammen mit `platform/paper-core` auf einem Spielserver; laut Notiz 07 (Abschnitt 3) eine Runde pro Server/Pod.

## Welches Vorbild-Feature wird nachgebaut?

Notiz 03, Abschnitt 7 (beim Vorbild aktiv, nur Wartungs-Updates):
- Phase 1: Abstimmung über 1 von 3 zufälligen Themen;
- Phase 2: 7 Minuten Bauen im Kreativmodus (+1 Minute seit Juli 2020), vorzeitiges Ende per `/finish`-Abstimmung;
- Phase 3: anonyme Bewertung mit sechs Noten in zufälliger Reihenfolge;
- Themenpool: 300+ Begriffe zum Start, +100 mit Version 2.0; Spieler können Themen über einen NPC einreichen.
- GuessIt (Dezember 2020, Raten per Chat) ist **nicht** Teil dieses Moduls; seine Regeln wurden nicht gefunden.

## Kampfprofil

`MODERN` (kein Kampf im Modus; Notiz 07 legt nichts fest).


Quellen: `research_notes/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken vom Vorbild übernehmen (Notiz 07, Abschnitt 9 B).
