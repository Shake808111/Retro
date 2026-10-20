# game/clanwar – ClanWar

**Gradle-Pfad:** `:game:clanwar` · **Paket:** `dev.netzwerk.clanwar` · **Einstieg:** `ClanWarPlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für den Modus ClanWar (ClanWar: Clan gegen Clan). Baut auf `game/game-framework` auf (Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke) und läuft zusammen mit `platform/paper-core` auf einem Spielserver; laut Notiz 07 (Abschnitt 3) eine Runde pro Server/Pod.

## Welches GommeHD-Feature wird nachgebaut?

Notiz 03, Abschnitt 13:
- Start im Spiel mit **`/cw <Spieler> <Spiel>`** zwischen Partys aus Mitgliedern jeweils eines Clans;
- offizieller Standard **4 gegen 4**, **60 Minuten** Limit, danach **5 Minuten DeathMatch**;
- `/fcw` („FunClanWar“) erlaubt gemischte Partys;
- es gab ein offizielles BedWars-CW-Ranking und ClanPages; CWBW, CWCores und CWSG wurden um 2022 eingefroren.

Clans selbst (Tag, Ränge, Elo, Clan-Level): Notiz 05, Abschnitt 3; Tabelle `social.clan` mit Spalte `elo`
in `db/migrations/V1__init.sql`.

## Kampfprofil

`LEGACY_1_8` nach Notiz 07, Abschnitt 1.


Quellen: `research_notes/GommeHD Analyse und Nachbau/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken von GommeHD übernehmen (Notiz 07, Abschnitt 9 B).
