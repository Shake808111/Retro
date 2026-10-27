# game/game-framework – Grundlage aller Spielmodi

**Gradle-Pfad:** `:game:game-framework` · **Paket:** `net.retromc.game.framework` · **Einstieg:** `MiniGamePlugin` (Basisklasse), `GameState`, `GameLifecycle`, `GameDefinition`, `CombatProfile` · **Status:** Gerüst

## Verantwortung

Bibliothek, die in jedes Modus-Plugin eingebettet wird (Notiz 07, Abschnitt 5 „Game modes“):
- Rundenablauf WAITING → STARTING → INGAME → ENDING (`GameState`, `GameLifecycle`), danach wird der
  Server verworfen (eine Runde pro Pod, Notiz 07, Abschnitt 3);
- Teamzuteilung unter Beachtung von Partys, Kartenkonfiguration (Spawns, Betten, Spawner, Cores, Truhen, Regionen),
  Zuschauermodus, Kits und Shops, Belohnungen (Coins/XP);
- Statistik-Senke (Tabellen `stats.match`, `stats.match_player`, `stats.total`, `stats.monthly`), Anticheat-Hook;
- Kampfprofil (`CombatProfile`) als austauschbare Schnittstelle (Notiz 07, Abschnitt 1).

## Welches Vorbild-Feature wird nachgebaut?

Kein einzelnes Feature: die gemeinsame Mechanik hinter allen Rundenmodi (Wartelobby, Teamwahl, Map-Voting,
Zuschauer, Belohnungen; vgl. Notiz 02, Q4 und Q7, sowie Notiz 03, Abschnitte 14 und 15).

Quellen: `research_notes/`.
