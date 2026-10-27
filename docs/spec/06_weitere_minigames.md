# Spec 06: Weitere Minigames

Gilt für alle Spielmodi außer BedWars, SkyWars (Spec 04), SurvivalGames/QuickSG, EnderGames, **TTT, Cores, MasterBuilders, ClanWar** (Spec 05 ist dort führend; die TTT-/MB-Abschnitte hier sind nur Ergänzung, bei Widerspruch gilt Spec 05).
Quellen: Notiz 03 (komplett), Notiz 14 §2, Notiz 17 (Minigame-Befehle), Video-Sichtung (batch2/thumbs, `[OTHER:…]`), OCR der 419 Clips und 6 selbst gesichtete Frames.
Belegkürzel: **B** = belegt (Quelle/Frame), **A** = abgeleitet (Schluss aus Belegen), **AN** = Annahme (frei festgelegt, konfigurierbar).
Platzhalter: `{brand}` = `brandName` wie konfiguriert (`RetroMC`), `{BRAND_UPPER}` = in Großbuchstaben (Scoreboard-Titel `{BRAND_UPPER}.NET`).
Ära-Kürzel: **1.8** (bis 2019), **20+** (1.20/1.21 bis ca. 04/2026), **26** (neue Lobby ab ca. 04/2026). Standard ist immer die neueste belegte Ära.

### Modus-Priorität (nach Beliebtheit 2025/26)

Grundlage: `/gameteleport`-Schlüssel 2026 (Notiz 17), Fokus-Modi (Notiz 03 §1), MOTD- und Actionbar-News 2025/26, Häufigkeit in Clips 2025/26.

| Prio | Modus | Status 10/2026 | Quickplay-Key | Kampf | Beleg |
|---|---|---|---|---|---|
| P1 | Duels (inkl. Game1vs1-Modi, SkillTraining, Turniere) | aktiv, Fokus | `duelsl` | MODERN (Kits); LEGACY_1_8 nur für 1.8-Kits (AN) | B |
| P1 | PartyGames (Brett + Minispiele, inkl. Quake, GuessIT, JumpDown) | aktiv, Fokus | `pg` | MODERN | B |
| P1 | ItemChaos (TheLab) | aktiv, Fokus 2025/26 | `ic` | MODERN | B |
| P1 | Bingo | aktiv (Rejoin-Update, Lobby-Logo 2026) | `bingo` | MODERN | B |
| P2 | JumpLeague (+ JumpLeague1vs1 in Duels) | aktiv, Update 2026 (Modulserver) | `jl` | MODERN | B |
| P2 | LavaEscape (TheLab) | aktiv | `le` | MODERN | B |
| P2 | TTT | aktiv, nur Wartung | `ttt` | MODERN (Hit-Cooldown aus, s. 6.10) | B |
| P2 | MasterBuilders (+ GuessIT-Ursprung) | aktiv, nur Wartung | `mb` | – | B |
| P2 | GunGame | aktiv, nur Wartung, Client 1.8.9 | `ffagungame` | LEGACY_1_8 | B |
| P2 | BuildFFA | aktiv, nur Wartung, Client 1.8.9 | `ffabuild` | LEGACY_1_8 | B |
| P3 | Hide'n'Seek | aktiv laut Key, sonst unbelegt | `hns` | – | B (nur Key) |
| H | UHC / SpeedUHC | **historisch**; UHC-Runde 03/2025 gesichtet (Event/Privat) | früher `speeduhc` | MODERN | B |
| H | RageMode | **historisch** (abgeschaltet 07.06.2024), Privat-Runde Supremium | früher `ragemode` | LEGACY_1_8 | B |
| H | FFA Classic, FFA Hardcore | **historisch** (Classic 07.06.2024; Hardcore-Key entfernt) | früher `ffaclassic`, `hardcore` | LEGACY_1_8 | B |
| H | Cores, Cookies | **historisch** (07.06.2024), Privat-Runde Supremium | früher `cores`, `cookies` | LEGACY_1_8 | B |
| H | Kit1vs1, Game1vs1 (eigenständig), Skill Training (eigenständig) | **historisch**, entfernt 21.07.2026, in Duels aufgegangen | früher `1vs1`, `game1vs1`, `training` | – | B |
| H | Conquest, MoleCraft, AuraPlus, ShoppingChaos, ColorIt, MineMafia | **historisch (vor 2020)**; AuraPlus/MoleCraft/ShoppingChaos als PartyGames-Minispiele | – | – | B |
| E | Escape Room, r/place (`place`), Turnier-Server | Event-Modi, nicht Teil dieser Spec (nur Schnittstelle) | – | – | B |

Bau-Reihenfolge (Default): Duels → PartyGames (Brett + 6 Minispiele) → Bingo → ItemChaos → JumpLeague → GunGame → BuildFFA → TTT → MasterBuilders → LavaEscape → historische Modi (nur für Private Runden).

---

## 1. Umfang & Module

Alle Spielmodule: Paper-Plugin, `compileOnly(:platform:paper-core)`, `implementation(:game:game-framework)`, `paper-plugin.yml` mit `name: ${brandName}<Modus>` und Abhängigkeit `${brandName}Core` (`load: BEFORE`, `join-classpath: true`), wie `:game:gungame`.

| Gradle-Modul | Status | Paket | Plugin-Name | Hauptklasse | Zusätzliche Abhängigkeiten |
|---|---|---|---|---|---|
| `:game:game-framework` | vorhanden (erweitern) | `net.retromc.game.framework` | (eingebettet) | – | neu: `minigame`-Unterpaket (Minispiel-API, s. 8.3), `ScoreboardLayout`, `ResultBlock` |
| `:game:duels` | **NEU** | `net.retromc.duels` | `${brandName}Duels` | `DuelsPlugin` | `:core:core-data` (Elo/Season), `:core:core-messaging` (Anfragen, Turniere) |
| `:game:duels-lobby` | **NEU** (alternativ Teil von `:lobby`; Default: eigenes Modul) | `net.retromc.duels.lobby` | `${brandName}DuelsLobby` | `DuelsLobbyPlugin` | `:game:duels` (gemeinsame Kit-/Elo-Klassen als `api`-Paket) |
| `:game:partygames` | **NEU** | `net.retromc.partygames` | `${brandName}PartyGames` | `PartyGamesPlugin` | Minispiel-API |
| `:game:bingo` | **NEU** | `net.retromc.bingo` | `${brandName}Bingo` | `BingoPlugin` | – |
| `:game:itemchaos` | **NEU** | `net.retromc.itemchaos` | `${brandName}ItemChaos` | `ItemChaosPlugin` | – |
| `:game:lavaescape` | **NEU** | `net.retromc.lavaescape` | `${brandName}LavaEscape` | `LavaEscapePlugin` | – |
| `:game:jumpleague` | **NEU** | `net.retromc.jumpleague` | `${brandName}JumpLeague` | `JumpLeaguePlugin` | `:tools:map-exporter` (Modul-Schematics) |
| `:game:gungame` | vorhanden (Stub) | `net.retromc.gungame` | `${brandName}GunGame` | `GunGamePlugin` | – |
| `:game:buildffa` | **NEU** | `net.retromc.buildffa` | `${brandName}BuildFFA` | `BuildFFAPlugin` | – |
| `:game:ttt` | vorhanden (Stub) | `net.retromc.ttt` | `${brandName}TTT` | `TttPlugin` | **Spec 05 führend** |
| `:game:masterbuilders` | vorhanden (Stub) | `net.retromc.masterbuilders` | `${brandName}MasterBuilders` | `MasterBuildersPlugin` | **Spec 05 führend** |
| `:game:hideandseek` | **NEU** (P3, nur Gerüst) | `net.retromc.hideandseek` | `${brandName}HideAndSeek` | `HideAndSeekPlugin` | – |
| `:game:legacy-modes` | **NEU** (historisch) | `net.retromc.legacy.{uhc,ragemode,ffa,cookies}` | `${brandName}LegacyModes` (ein Plugin, Modus per Server-Konfig `legacy.mode`) | `LegacyModesPlugin` | – |
| `:game:cores` | vorhanden (Stub, historisch) | `net.retromc.cores` | `${brandName}Cores` | – | nur Private Runden |
| `:game:clanwar` | vorhanden | – | – | – | nicht Teil dieser Spec (Querverweis 6.14) |

Server-Typen (Controller-Templates): `duels-lobby`, `duels-match` (mehrere Matches pro Server, AN: 8 Arenen-Instanzen), `pg`, `bingo`, `ic`, `le`, `jl`, `ffagungame` (Dauerserver), `ffabuild` (Dauerserver), `ttt`, `mb`, `legacy-*`.

---

## 2. Datenmodell

### 2.1 Allgemein (bestehend, `db/migrations/V1__init.sql`)

Alle Zähler über Spec 02 §2.1: Spalten `kills`, `deaths`, `games`, `wins`, `points` in `stats.daily`/`stats.alltime`, alle weiteren Schlüssel in `extra` JSONB (`stats.total`/`stats.monthly` aus V1 werden von `V2_1` gelöscht). Matches über `stats.match`/`stats.match_player` (`extra` JSONB für modusspezifische Werte). `mode` = Quickplay-Key ohne `l`-Suffix: `duels`, `pg`, `bingo`, `ic`, `le`, `jl`, `gungame`, `buildffa`, `ttt`, `mb`, `hns`, `uhc`, `ragemode`, `ffaclassic`, `ffahardcore`, `cores`, `cookies` (Seed `stats.mode`, Spec 02 §6.7).

### 2.2 Stats-Keys je Modus (Spalte bzw. `extra`-Schlüssel, Spec 02)

| Modus | Keys | Quelle |
|---|---|---|
| duels | `matches`, `wins`, `losses`, `kills`, `deaths`, `xp`, `level`, `hits`, `hits_landed`, `crits`, `damage_dealt`, `tournaments_won`; je Kit `matches.<kit>`, `wins.<kit>` | Scoreboard „Level/Gespielte Matches/Siegesquote“ (B), Match-Statistik (B) |
| pg | `games`, `wins`, `points`, `minigames_played`, `minigames_won`, `xp`; je Minispiel `mg_won.<id>` | Hologramm „Deine Statistiken der letzten 30 Tage“ (B) |
| bingo | `games`, `wins`, `items_found`, `mobs_found`, `kills`, `deaths`, `best_time_ms` | Chat/Bestzeiten-Wand (B), Rest AN |
| ic | `games`, `wins`, `draws`, `kills`, `deaths`, `items_received` | AN (Gewinn-/Kill-Tiebreak B) |
| le | `games`, `wins`, `kills`, `deaths`, `blocks_mined` | AN |
| jl | `games`, `wins`, `kills`, `deaths`, `points`, `modules_done`, `modules_failed`, `goal_reached` | Punkte-System (B) |
| gungame | `kills`, `deaths`, `level_record`, `kills_per_level` (nicht nötig) | Scoreboard „Kills / Level“, „Stufenrekord“ (B) |
| buildffa | `kills`, `deaths`, `best_streak`, `blocks_placed` | AN |
| ttt | `games`, `wins`, `wins_traitor`, `wins_innocent`, `wins_detective`, `kills`, `deaths`, `karma`, `traitor_tests`, `corpses_found`, `passes_traitor`, `passes_detective` | Rollen/Karma (B) |
| mb | `games`, `wins`, `points`, `ratings_given` | AN |
| uhc / ragemode / ffa* / cores / cookies | `games`, `wins`, `kills`, `deaths` (+ `emeralds` RageMode) | AN |

### 2.3 Neue Tabellen (Migration `V6_1__minigames.sql`, Schemas `duels`, `pg`, `bingo`, `jl`, `ic`; TTT/MB-Tabellen liegen in Spec 05 `games.*`)

| Tabelle | Spalten (Typ) | Schlüssel / Indizes | Zweck |
|---|---|---|---|
| `duels.season` | `id SMALLINT`, `label VARCHAR(24)` (z. B. `Oct-Dec 2026`), `starts_at TIMESTAMPTZ`, `ends_at TIMESTAMPTZ`, `beta BOOLEAN DEFAULT false`, `season_kit VARCHAR(32)` | PK `id`; UNIQUE `starts_at` | Saison = Kalenderquartal (B) |
| `duels.kit` | `id VARCHAR(32)`, `display VARCHAR(32)`, `type VARCHAR(12)` (`KIT`,`GAME`,`SEASON`,`EVENT`,`CUSTOM`), `ranked BOOLEAN`, `active BOOLEAN`, `combat VARCHAR(10)`, `icon VARCHAR(48)`, `loadout JSONB`, `rules JSONB`, `sort SMALLINT` | PK `id` | Kit-Registry |
| `duels.rating` | `uuid UUID`, `season_id SMALLINT`, `kit VARCHAR(32)`, `elo INT NOT NULL DEFAULT 1000`, `placements SMALLINT DEFAULT 0`, `wins INT`, `losses INT`, `peak_elo INT`, `updated_at TIMESTAMPTZ` | PK `(uuid, season_id, kit)`; IDX `(season_id, kit, elo DESC)` | Elo je Kit und Saison |
| `duels.match` | `id UUID`, `kit VARCHAR(32)`, `ranked BOOLEAN`, `tournament_id BIGINT NULL`, `round SMALLINT NULL`, `p1 UUID`, `p2 UUID`, `winner UUID NULL`, `elo_delta SMALLINT`, `duration_s INT`, `stats JSONB` (je Spieler: Schläge, Treffer, Krits, Schaden, Rest-HP), `ended_at TIMESTAMPTZ` | PK `id`; IDX `(p1, ended_at DESC)`, `(p2, ended_at DESC)` | Match-Statistik-Link, Revanche |
| `duels.tournament` | `id BIGSERIAL`, `owner UUID`, `kit VARCHAR(32)`, `size SMALLINT` (8/16/32), `state VARCHAR(10)` (`OPEN`,`RUNNING`,`DONE`,`CANCELLED`), `created_at`, `started_at`, `winner UUID` | PK `id`; IDX `(owner, created_at)` | Spieler-Turniere |
| `duels.tournament_entry` | `tournament_id BIGINT`, `uuid UUID`, `seed SMALLINT`, `eliminated_round SMALLINT NULL` | PK `(tournament_id, uuid)` | Teilnehmer |
| `duels.custom_kit` | `id BIGSERIAL`, `owner UUID`, `name VARCHAR(24)`, `base_kit VARCHAR(32)`, `loadout JSONB`, `created_at` | PK `id`; IDX `owner`; Limit je Rang (AN: 3/5/10) | „Eigenes Kit erstellen“ (B, MOTD) |
| `duels.layout` | `uuid UUID`, `kit VARCHAR(32)`, `slots JSONB` | PK `(uuid, kit)` | gespeicherte Hotbar-Anordnung (AN) |
| `pg.minigame_rating` | `uuid UUID`, `minigame VARCHAR(24)`, `rating SMALLINT` (1–5), `rated_at` | PK `(uuid, minigame)` | „Minispiele bewerten“ (B) |
| `bingo.best_time` | `uuid UUID`, `team_members UUID[]`, `time_ms INT`, `achieved_at TIMESTAMPTZ` | PK `uuid`; IDX `time_ms ASC` | Bestzeiten-Wand (B) |
| `jl.module` | `id VARCHAR(32)`, `difficulty SMALLINT` (1 leicht, 2 normal, 3 schwer), `schematic VARCHAR(64)`, `author UUID`, `approved BOOLEAN`, `created_at` | PK `id`; IDX `(difficulty, approved)` | Modulpool (~907, B) |
| `jl.module_stat` | `module_id VARCHAR(32)`, `attempts INT`, `fails INT`, `avg_ms INT` | PK `module_id` | Balancing (AN) |
| `ic.modifier_token` | `uuid UUID`, `day DATE`, `used SMALLINT` | PK `(uuid, day)` | 3/10 Tokens pro Tag (B) |
| ~~`ttt.pass`~~ | – | – | entfällt → Spec 05 `games.ttt_profile` / `games.ttt_pass_ledger` |
| ~~`mb.theme`~~ | – | – | entfällt → Spec 05 `games.mb_theme` / `games.mb_theme_rating` |

### 2.4 Valkey-Keys und Kanäle

| Key / Kanal | Typ | TTL | Inhalt |
|---|---|---|---|
| `queue:duels-<kit>-ranked` / `queue:duels-<kit>-unranked` | ZSET (Score = Einreihzeit ms; Member = Ticket-ID) | – (Ticket-TTL 300 s) | Matchmaking (bestehende `ValkeyKeys.queue`) |
| `duels:ticket:<id>` | HASH {uuid/party, kit, ranked, elo, since} | 300 s | Ticket |
| `duels:req:<from>:<to>` | HASH {kit, rounds, map, ranked=false, created} | **60 s** (AN) | Herausforderung |
| `duels:rematch:<matchId>` | SET {uuid…} | **30 s** (AN) | Revanche-Zusagen |
| `duels:tournament:<id>` | HASH {owner, kit, size, state, round} | 6 h | Live-Turnier |
| `duels:tournament:<id>:players` | ZSET (Seed) | 6 h | |
| `duels:tournament:daily:<uuid>:<yyyy-mm-dd>` | STRING Zähler | 26 h | Limit „bis 10/Tag“ (B, Supremium) |
| `duels:season:current` | HASH {id, label, ends_at} | – | Bossbar |
| `duels:online` | STRING | 10 s | „Spieler (Duels)“ |
| `pg:server:<name>:board` | HASH {uuid→feld} | Rundenende + 60 s | Live-Brett für Übersicht |
| `ic:modifier:<server>` | LIST | Rundenende | gewählte Modifier |
| `gungame:level:<uuid>` | STRING | 0 (AN: Level geht bei Leave verloren) | nicht persistiert |
| `lb:{mode}:30d` / `lb:{mode}:all` | ZSET | – | Spec 02 §2.2 (kein Monatsreset) |
| `lb:pg:30d` | ZSET | Spec 02 (rollierend) | 30-Tage-Ranking (B) |
| `lb:bingo_x{n}:all` | ZSET (Score = −ms, Spec 02 `metric=time`) | – | Top 10 Wand |
| Kanal `net:duels` | Pub/Sub | – | `REQUEST`, `REQUEST_ACCEPT`, `REQUEST_DENY`, `MATCH_READY`, `TOURNAMENT_ANNOUNCE`, `TOURNAMENT_UPDATE`, `REMATCH` |
| Kanal `net:queue` | bestehend | – | Queue-Join/-Leave aller Modi |
| Kanal `net:server-state` | bestehend | – | Zustand/Spielerzahl für Navigator und „Private Runden“ |

---

## 3. Befehle

Präfix-Konvention: `§f[§<Modusfarbe><Modus>§f] ` (Spec 02 `prefix.mode`, einheitlich für Rundenmodi; Systempräfixe wie CoinSystem/Turnier `§7[…§7]`). Fehler immer `§c`, Zahlen `§e`, Namen in Rangfarbe.

| Syntax | Aliase | Recht/Rang | Verhalten | Ausgaben & Fehler | Beleg |
|---|---|---|---|---|---|
| `/duel <Spieler> [Kit]` | `/duell`, `/1vs1` | alle, Duels-Lobby | ohne Kit: öffnet GUI 5.4 „Kit wählen (Herausforderung)“; mit Kit: sendet Anfrage (Unranked) | Empfänger: Meldung `duels.request.received` + `duels.request.click`; Absender `duels.request.sent`; Fehler `duels.err.offline`, `duels.err.self`, `duels.err.pending`, `duels.err.notInLobby`, `duels.err.inQueue` | Anfrage-Text B; Syntax AN (Original: Linksklick mit Schwert „Herausfordern“ B) |
| `/duel accept <Spieler>` | – | Empfänger | Anfrage annehmen → Match-Server | `duels.err.noRequest` | Klickkomponente `[ANNEHMEN]` B |
| `/duel deny <Spieler>` | – | Empfänger | ablehnen | `duels.request.denied` an Absender | AN |
| `/duel rematch <MatchId>` | – | Match-Teilnehmer, ≤30 s nach Ende | Revanche-Zusage; beide → neues Match gleiches Kit/Map | `duels.rematch.waiting`, `duels.err.rematchExpired` | `[REVANCHE]` B |
| `/duel stats <MatchId>` | – | alle | öffnet GUI 5.6 Match-Statistik | `duels.err.matchUnknown` | `[ANZEIGEN]` B |
| `/duel queue <Kit> <ranked\|unranked>` | – | alle | Warteschlange betreten (auch per GUI) | `duels.queue.joined`, `duels.queue.noServer`, `duels.err.queueLocked` | B |
| `/duel leave` | `/duel cancel` | in Queue | Warteschlange verlassen | `duels.queue.left` | AN |
| `/turnier` | `/tournament` | alle | GUI 5.7 „Turniere“ | – | B (Menü) |
| `/turnier create <Kit> [Größe]` | – | Supremium+ (≤10/Tag), Staff unbegrenzt | erstellt Turnier, Broadcast an Lobby | `tournament.announce` + Klick; `tournament.err.limit`, `tournament.err.rank` | B |
| `/turnier join <Id>` | – | alle | Teilnahme | `tournament.joined`, `tournament.err.full`, `tournament.err.started` | `[MITSPIELEN]` B |
| `/turnier kit <Id>` | – | alle | Kit-Vorschau GUI 5.5 | – | `[KIT ANZEIGEN]` B |
| `/turnier start <Id>` / `/turnier cancel <Id>` | – | Ersteller/Staff | starten (≥4 Spieler, AN) / abbrechen | `tournament.err.tooFew` | AN |
| `/bingo` | – | Spieler in Bingo-Runde | zeigt Bingokarte (GUI 5.10) | Actionbar-Hinweis 4.3 | B |
| `/finish` | – | → **Spec 05 §3** (MasterBuilders) | – | – | B |
| `/traitor`, `/detective`, `/innocent` | – | → **Spec 05 §3** | – | – | B |
| `/shop [Nr]` | `/s` | → **Spec 05 §3** | – | – | B |
| `/t <Text>` | `@t <Text>` | → **Spec 05 §3** | – | – | B |
| `/top` | – | Spec 02 §3 (Wartelobbys aller Rundenmodi) | Top 10 des Modus | `top.*` (Spec 02) | B |
| `/stats [Spieler]` | – | Spec 02 §3 | Statistik-Chatblock des aktuellen Modus | `stats.*` (Spec 02) | B (Format Spec 02) |
| `/spec <Spieler>` | – | Zuschauer | zu Spieler teleportieren (sonst Kompass-GUI 5.15) | `common.err.notSpectator` | AN |
| `/leave` | `/l`, `/hub`, `/lobby` | alle | zurück zur Lobby (Proxy-Befehl, nicht hier implementiert) | – | B (Notiz 17) |
| `/pg rate` | – | PartyGames-Endphase | GUI 5.9 „Minispiele bewerten“ (auch Item) | – | Item B, Befehl AN |
| `/ic modifier` | – | ItemChaos-Wartelobby, Premium+ | GUI 5.11 „Modifier“ | `ic.mod.noTokens` | B (Tokens), Befehl AN |
| `/mb theme <Begriff>` | – | MasterBuilders-Wartelobby | Thema einreichen (auch NPC) | `mb.theme.submitted`, `mb.err.themeExists` | NPC B, Befehl AN |
| `/forcemap <Map>` | `/fm` | Spiel-Rahmen **Spec 03 #5** (Supremium 10/Tag, Supremium+ 30/Tag, VIP/Team ∞) | Map setzen | `common.forcemap.*` (Spec 03) | B |
| `/start` | – | Spiel-Rahmen **Spec 03 #4** (Premium aufwärts) | Countdown auf 10 s kürzen | `common.start.*` (Spec 03) | AN |

Anzahl: **26** Befehlszeilen (32 Befehle inkl. Unterbefehle und Aliase-Varianten).

---

## 4. Anzeige-Elemente

### 4.0 Gemeinsame Konventionen (alle Modi)

| Element | Format | Ära | Beleg |
|---|---|---|---|
| Scoreboard-Titel | `§f§l{BRAND_UPPER}.NET` (fett, weiß; Original `VORBILD.NET`) | 20+/26 | B |
| Scoreboard-Muster | Label `§f` in eigener Zeile, Wert `§e`/`§a`/`§6` in nächster Zeile, Leerzeile zwischen Blöcken; rote Zeilennummern rechts (Vanilla-Score `§c`) – AN: Nummern per `numberFormat blank` ausblenden ist **nicht** Original; Original zeigt sie → Default: anzeigen | 20+ | B |
| Labels mit Doppelpunkt | 26-Ära teils `Level:`, `Map:`, `Feld:`, `Rang:`; 20+ ohne Doppelpunkt | 26 vs 20+ | B (Widerspruch → Default 26 mit Doppelpunkt, Konfig `scoreboard.colon`) |
| Chat-Präfix | `§f[§<c><Modus>§f] §7<Text>` (Spec 02 `prefix.mode`) | alle | B (Klammerfarbe nach Spec 02 §10 #4) |
| Präfixfarben | Duels `§9`, PartyGames `§d`, Minispiel-Präfix `§b` (z. B. `[Quake]`), Bingo `§c` (unsicher), GunGame `§6`, JumpLeague `§c`, TTT `§c`, MasterBuilders `§9` (unsicher), RageMode `§c` (AN), CoinSystem `§e`, Turnier `§6` (AN), GuessIT `§a` | 20+/26 | B/AN wie markiert. Widerspruch Duels: Notiz 17 „aqua“, Frames 2025/26 blau → **`§9`** |
| Beitritt Wartelobby | Spec 03 `common.join`/`common.quit` (`§7» {nameColor}{name} §7hat die Runde betreten` / `§7« …`) | 20+ | B |
| Wartelobby-Info | Spec 03 `common.online`, `common.map` (`{p}§7Map: §e{map} §7von §e{brand}-Buildteam §7({builders})`) | 20+ | B |
| Countdown | Spec 03 `common.countdown` (Raster 60, 50, 40, 30, 20, 10, 5–1; `1 Sekunde`) | alle | B |
| Rundenende | Spec 03 `common.restart` (20 s) | 20+ | B |
| Ergebnisblock | Spec 02/03 Rundenblock (`§8----------------------`, 22 Zeichen); Platzierungs-Zeilen `common.result.line` dazwischen | 20+ | B |
| Coins | Spec 02 `coins.round` / `coins.boost` (Boost-Ränge Supremium/Supremium+) | 20+ | B |
| Tablist | Spec 02 §4.4: Header `§e{brand}.net {modeColor}{serverDisplay}`, `tab.header.report`, Footer `tab.footer.1/.2` | 20+ | B |
| Nametag | `{name} §7[{Level}]`; Partymitglieder ` §7[§dParty§7]`; Zuschauer `[OFF]` `§e` | 20+ | B |
| Zuschauer-Hotbar | Spec 03 §4.11 (1 Teleporter, 7 Einstellungen, 8 Nächste Runde, 9 Zur Lobby); Dauerserver ohne Zuschauer | 20+ | B (Items) |
| Wartelobby-Scoreboard Rundenmodi | `Map`/`{map}` §e · `Spieler`/`{n}/{max}` §a (TTT) · `Teams`/`Erlaubt §a`\|`Verboten §c` · `Hit-Cooldown`/`Aktiviert §a`\|`Deaktiviert §c` | 20+ | B |

### 4.1 Duels

**Lobby (Ära 26, Standard):**

| Element | Inhalt | Beleg |
|---|---|---|
| Bossbar | `§cSeason: §e{Mon}-{Mon} {YYYY}` (z. B. `Oct-Dec 2026`), Farbe BLUE, Fortschritt 1.0 konstant; Beta: `§cBeta Season: §eSept 2023` | B |
| Scoreboard | 1 `§f§l{BRAND_UPPER}.NET` / 2 leer / 3 `§fLevel:` / 4 `§6{level}✪` / 5 leer / 6 `§fGespielte Matches:` / 7 `§e{matches}` / 8 leer / 9 `§fSiegesquote:` / 10 `§e{quote} %` | B (Werte-Format `66,59 %`, Komma, Leerzeichen vor %) |
| Scoreboard 20+ | wie oben ohne Doppelpunkt, Level ohne ✪ | B (Option) |
| Hotbar (26) | 1 Eisenschwert `§6Herausfordern` · 2 Brustplatte `§bEigene Kits` · 3 Diamant `§bRanked` · 5 grüner Block (Limettenwolle) `§aUnranked` · 7 Netherstern `§dTurniere` · 8 Redstone-Fackel `§cEinstellungen` · 9 Kiste/Block `§7Kosmetik` | Items B (2025: Slot 1 Schwert, 2 Diamant, 4 grüner Block, 6 Netherstern, 7 ?, 8 Block); Namen außer „Herausfordern“ AN |
| Hologramme/NPCs | `§c§lTurniere` + `§7(Rechtsklick)`; `§a§lTeams`; `§a§lRANKED`; `§a§lUNRANKED`; `§b§lSkillTraining`; `§6Duels Shop` + `§eExklusives Angebot`; Level-Treppe (s. 6.1.8) | B (Texte), Farben teils AN |
| Nametag | `{name} §7[{level}✪]`; Rangpräfix `Suprem §7\|` bei Supremium | B |
| Chat-Werbung (Intervall AN 10 min) | `duels.ad.boost` / `duels.ad.shop` | B |

**Match:**

| Element | Inhalt | Beleg |
|---|---|---|
| Countdown | Chat `[Duels] Das Match beginnt in §e{n} §7Sekunden` (3,2,1), Title `§a§lLOS!` | B |
| Start | Chat `duels.match.start` | B |
| Actionbar (Match-Timer) | `§7Das Match endet in §e{mm:ss} §7Minuten` (Dauer 5:00, Turnier 3:00) | B |
| Actionbar Turnier | `§6{Rundenname}` (Achtelfinale/Viertelfinale/Halbfinale/Finale) vor dem Timer | B |
| Scoreboard Match | 1 Titel / 2 leer / 3 `§fKit:` / 4 `§e{kit}` / 5 leer / 6 `§fGegner:` / 7 `{rangfarbe}{gegner}` / 8 leer / 9 `§fZeit:` / 10 `§e{mm:ss}` | „Only Sword“, „Gegner“ B; Rest AN |
| Ende Title | `§a{sieger}` / Sub `§7hat das Match gewonnen` | B |
| Ende Chat | Statistikblock (7: `duels.end.*`) | B |

### 4.2 PartyGames

| Phase | Scoreboard Zeile für Zeile | Beleg |
|---|---|---|
| Wartelobby | Titel / leer / `§fMap:` / `§e{map}` / leer / `§fTeams:` / `§cVerboten` / leer / `§fHit-Cooldown:` / `§cDeaktiviert` | B |
| Brett | Titel / leer / `§fMap:` / `§e{map}` / leer / `§fFeld:` / `§6{feld}/{felder}` / leer / `§fRang:` / `§6#{rang}` (Platz 1 `§6`, sonst `§a`) | B |
| Minispiel | Titel / leer / `§fMinispiel` / `§b{name}` / leer / `§fRanking:` / bis 7 Zeilen `{platzfarbe}#{p} {namefarbe}{name} §7({wert})` / leer / `§fZeit` / `§e{mm:ss}` | B |
| Ende | Titel / leer / `§fRanking:` / 6 Zeilen `#{p} {name}: §6Feld {n}` | B |

Platzfarben: #1 `§6`, #2 `§9`, #3 `§6` (bronze → `§6`), ab #4 `§a` (B, ungenau). Eigener Name `§c` (B: eigener Spieler rot hervorgehoben → AN: `§c§l`). Gleichstand = gleicher Platz, nächster Platz zählt weiter (#2,#2,#2 → #3? B: „#2,#2,#2,#3“ → **dichte Platzierung**, B).
Wert-Formate: `({n} Kills)`, `({n} Punkte)`, `(mm:ss.SSS)` für Zeitrennen (B).

| Element | Inhalt | Beleg |
|---|---|---|
| Start-Title | `§dPartyGames` / Sub `§f{map}` mit Ausblenden (Blindness 1 s) | B |
| Hotbar Brett | 1 Kompass `§eÜbersicht` (GUI 5.8) · 5 Würfel (Player-Head-Würfel) `§dWürfel §7(Rechtsklick)` · 9 Papier `§7Regeln` (AN) | B (Items), Namen teils AN |
| Hotbar Ende | 1 Item `§6Minispiele bewerten §7(Rechtsklick)` · 2 Kompass · 3 grüner Block `§aNächste Runde` (AN) | B |
| Hotbar Wartelobby | 9 Diamant `§bKosmetik` (AN) | B (Item) |
| Würfel-Title | `§e{n}` / Sub `§7Du hast eine §e{n} §7gewürfelt` | B (OCR „Du hast eine 1 gewürfelt“) |
| Ereignis-Title | `§a{Ereignis}` / Sub `§7Du hast eine §a{Ereignis} §7freigeschaltet` (Abkürzung) | B |
| Minispiel-Sieger-Title | `{farbe}{name}` / Sub `§7hat das Minispiel gewonnen` | B |
| Spiel-Sieger-Title | `§a{name}` / Sub `§fhat die PartyGames gewonnen` | B |
| Nametag | `{name} §7[§e{feld}§7]` | B |
| Lobby-Hologramm Statistik | `§dPartyGames §eStatistiken` / `§7Deine §eStatistiken §7der letzten §e30 Tage` / `§7Position im Ranking §e{n}` / `§7Punkte §e{n}` / `§7Gewonnene Spiele §e{n}` / `§7Gespielte Spiele §e{n}` / `§7Siegesquote §e{n}%` / `§7Gespielte Minispiele §e{n}` / `§7Gewonnene Minispiele §e{n}` / Trennzeile | B |

### 4.3 Bingo

| Element | Inhalt | Beleg |
|---|---|---|
| Scoreboard | Titel / leer / `§fRanking` / je Team `{teamfarbe}Team {Farbe} §7{best}/5` (sortiert) / leer / `§fVerstrichene Zeit` / `§e{mm:ss}` (zählt hoch) | B |
| Actionbar dauerhaft | `§aNutze /bingo, um dir die Items der Bingokarte anzeigen zu lassen` | B |
| Hotbar | 1 Karte (Filled Map mit gerenderter Bingokarte), sonst Start-Kit Holzwerkzeuge (AN: Holzschwert, -spitzhacke, -axt, -schaufel) | B (Karte, Holzwerkzeuge) |
| Karte (Map-Renderer) | Pergament-Rand, Kopf `BINGO`, 5×5 Raster, Zellen #E6D5A0, Gitter dunkelgrau 2 px; gefundene Items mit Team-Glow-Hintergrund | B |
| Lobby | Bestzeiten-Wand: 2 Reihen à 5 Köpfe, #1 auf Goldblock, #2 Eisenblock, #3 Kupferblock; Schild: `#{p}` / `{name}` / `Schnellste Runde:` / `{mm:ss} Minuten` (Original englisch „Quickest game:“ → **DE-Default**, Konfig `bingo.wall.lang`) | B |

### 4.4 GunGame (Dauerserver)

| Element | Inhalt | Beleg |
|---|---|---|
| Scoreboard 20+ | Titel / leer / `§fStufe` / `§6{stufe}` / leer / `§fKills / Level` / `§e{kills, Tausenderpunkt} §7(Lvl {netzwerklevel})` / leer / `§fStufenrekord` / `§6{rekord}` / leer / `§6#1 {name} §c{stufe}` / `§9#2 …` / `§6#3 …` (Top-3 nach aktueller Stufe) | B (Frame 2024) |
| Scoreboard 1.8 (Option) | `Level` / `Kills` / `Level-Rekord` | B (2017) |
| Actionbar | `§aTeams erlaubt` dauerhaft | B |
| Tod | Vanilla-Todesbildschirm `Du bist gestorben!` / `Punktestand: 0`; Respawn manuell | B |
| Hotbar | Waffe Slot 1, sonst leer (Rüstung angelegt) | B |
| Nametag | `{name} §c{stufe} ❤`-Lebensanzeige unter Name (`10 ❤`) | B (Frame: „10 ♥“ unter Namen) |

### 4.5 JumpLeague

| Phase | Scoreboard | Beleg |
|---|---|---|
| Jump | Titel / leer / `§fZeit` / `§e{mm:ss}` (11:00 abwärts) / leer / `§fModul` / `§e{n}/10` / leer / `§fCheckpoint` / `§e#{n}` | Zeit B (Format), Rest AN |
| Deathmatch | Titel / `§fZeit §e{mm:ss}` (5:00 abwärts) / je Spieler `§c❤§c❤§7❤ §a{name}` (verbleibende Leben rot, verlorene grau) | B (2026) |
| Hotbar DM-Zuschauer | 1 Kompass, 8 grüner Block, 9 Papier | B |
| Chat | `[JumpLeague] Leben von {name}: §c❤❤❤❤…` (Herzen des Killers nach Kill) | B |

### 4.6 TTT

| Element | Inhalt | Beleg |
|---|---|---|
| Scoreboard Runde | Titel / leer / `§fSpieler` / `§a{lebend}/{start}` / leer / `§fEnde` / `§e{mm:ss}` (10:00 abwärts) / leer / `§fHit-Cooldown` / `§cDeaktiviert`; Detective zusätzlich `§fD-Punkte` / `§9{n}`; Traitor `§fT-Punkte` / `§c{n}` (AN analog) | B |
| Title Rolle | `§aInnocent` / Sub `§7Hilf den Detectives, die Traitor zu entlarven` (eigene Formulierung); Traitor `§4Traitor`, Detective `§9Detective` | B (Struktur) |
| Actionbar | Rolle dauerhaft (`§aInnocent`/`§9Detective`/`§4Traitor`); Zuschauer `§7Zuschauer §7(Rechtsklick)` | B |
| Leichen-Hologramm (3 Zeilen) | `§7Leiche von §e{name} {rollenfarbe}({Rolle})` / `§7Tot seit §e{mm:ss}` / `§7Getötet von §e{killer\|Ermittlung läuft}` | B |
| Tablist-Suffix | `§7[Karma]`/Clan-Tag | B |
| Hotbar | 1 Schwert/Brecheisen, 2 Bogen, 4 Smaragd `§aShop` (Detective/Traitor), 9 Pfeile 32; Wartelobby Slot 1 Pass-Item | B |

### 4.7 MasterBuilders

| Element | Inhalt | Beleg |
|---|---|---|
| Scoreboard | Titel / `§fZeit §e{mm:ss}` / leer / `§fThema` / `§a{thema}` | B |
| Plot-Hologramm | Baumeistername `§6{name}` groß über Plot (Bewertung anonym → Name erst nach Bewertung; AN) | B |
| Hotbar Bewertung | 6 farbige Wolle/Terrakotta Slots 1–6: `§4Grauenvoll`, `§cSchlecht`, `§eOkay`, `§aGut`, `§2Super`, `§5Legendär`; Slot 9 Buch | B (Stufen, Itemname „Okay“), Farben AN |

### 4.8 Weitere Modi

| Modus | Scoreboard / UI | Beleg |
|---|---|---|
| ItemChaos | Titel / leer / `§fNächstes Item` / `§e{s}s` / leer / `§fSpieler` / `§a{n}` / leer / `§fKills` / `§e{n}` / leer / `§fZeit` / `§e{mm:ss}`; Bossbar Event-Ansage `§d{Event}` 5 s | AN (Struktur nach Rahmen) |
| LavaEscape | Titel / `§fLava steigt in` / `§e{s}s` / `§fHöhe` / `§6Y {y}` / `§fSpieler` / `§a{n}` / Kampfphase `§fEnde §e{mm:ss}` | AN |
| UHC (hist.) | Titel / leer / `§fSpieler:` / `§a{n}` / leer / `§fTeleport in:` / `§b{mm:ss}` / leer / `§fBorder:` / `§e+{r} -{r}` / leer / `§fKills / Teamkills:` / `§a{k} §7({tk})` / leer / `§fSzenario:` / `§e{szenario}`; Actionbar `§cTeaming mit anderen Teams ist verboten` | B (03/2025) |
| RageMode (hist., 1.8) | Titel / `§fEmeralds:` / bis 10 Zeilen `§e{nn} §f{name}` (zweistellig, absteigend) / `§fZiel: §e25`; Actionbar `§cTeaming ist verboten` | B (2017) |
| BuildFFA | Titel / leer / `§fKills` / `§e{n}` / leer / `§fKillstreak` / `§e{n}` / leer / `§fKit` / `§e{kit}` | AN |
| Hide'n'Seek | – | offener Punkt |

---

## 5. GUIs

Konvention: Rahmen aus Glasscheiben ohne Namen (`§r`), Klick auf Rahmen = nichts. Slots 0-basiert (Reihe×9+Spalte).

### 5.1 Duels – „Ranked“ (Hotbar Diamant)
Titel `§8Wähle ein Kit §7(Ranked)` · 3 Reihen (B: Titel teilweise lesbar, „Kit (Ranked)“).

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–7, 18–26 | Limetten-Glasscheibe | – | – |
| 8 | rote Glasscheibe / Barriere | `§cWarteschlange verlassen` (nur wenn in Queue, sonst Limette) | `/duel leave` (AN) |
| 9–17 | Kit-Icon je Kit (Reihenfolge `duels.kit.sort`) | `§a{Kit}` / `§b{Beschreibung, 2 Zeilen}` / leer / `§7Liga: §e{liga\|Nicht platziert}` / `§7Elo: §e{elo\|0}` / leer / `§7Aktuell §e{n} §7Spieler` / leer / `§a<Klicke zum Betreten>` | Queue ranked betreten; Rechtsklick: Kit-Vorschau 5.5 (AN) |

Kit-Icons (B Frame 2025): Goldapfel (UHC), Amethyst/Endkristall (Crystal), Diamantschwert (Sword/Only Sword), Eimer (Potions/Soup?), Diamantaxt (Axe), Streitkolben (Mace), Dreizack/Feuerkugel (Season-Kit).

### 5.2 Duels – „Unranked“ (Hotbar grüner Block)
Wie 5.1, Titel `§8Wähle ein Kit §7(Unranked)`, Lore ohne Liga/Elo, zusätzlich Game-Modi (Rush BedWars, Sumo, Survival Games, JumpLeague1vs1, SkyWars, Aura, Helden-Event) in Reihe 2, dann 4 Reihen (AN).

### 5.3 Duels – „Eigene Kits“ (Hotbar Brustplatte)
Titel `§8Eigene Kits` · 3 Reihen · Slots 10–16 eigene Kits (`§e{name}` / `§7Basis: §e{basiskit}` / leer / `§aLinksklick: Bearbeiten` / `§cShift-Rechtsklick: Löschen`), Slot 22 Amboss `§aNeues Kit erstellen` → Kit-Editor (Spielerinventar wird Editor, Bestätigen per Smaragd Slot 26). Limit 3/5/10 (Spieler/Premium/Supremium, AN). Beleg: MOTD „DUELS EIGENES-KIT-ERSTELLEN UPDATE“ (B), Layout AN.

### 5.4 Duels – „Herausfordern“ (Schwert-Linksklick auf Spieler oder `/duel <Spieler>`)
Titel `§8{Gegner} herausfordern` · 4 Reihen · Reihe 1–2: alle Unranked-Kits + eigene Kits (Lore `§7Klicke, um §e{Gegner} §7mit diesem Kit herauszufordern`), Slot 31 Uhr `§eRunden: §6{1|3|5}` (Klick zyklisch; B: Anfrage „1 Runden“ nur bei Fremdserver gesehen → AN), Slot 35 Barriere `§cAbbrechen`.

### 5.5 Duels – Kit-Vorschau
Titel `§8Kit: {Kit}` · 6 Reihen · Reihe 0: Rüstung (Slots 0–3) + Offhand (4); Reihen 2–5: Inventar wie Loadout (Hotbar = Reihe 5); Slot 8 Pfeil `§7Zurück`. AN.

### 5.6 Duels – Match-Statistik (`[ANZEIGEN]`)
Titel `§8Match-Statistiken` · 6 Reihen · Slot 4 Kopf Spieler A `§a{name}` / `§7Schläge: §e{n}` / `§7Treffer: §e{n} §7(davon §e{k} §7kritisch)` / `§7Schaden: §e{x}` / `§7Leben: §c{hp} ❤`; Slot 6 Kopf Spieler B analog; Reihen 2–5 Endinventar des angeklickten Spielers. Original zeigt Inventar (B: „Match-Statistiken von {name}“), Details AN.

### 5.7 Duels – „Turniere“ (Hotbar Netherstern / NPC)
Titel `§8Turniere` · 5 Reihen (B Frame 2026).

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–3, 5–8, 36–39, 41–44 | lila Glasscheibe | – | – |
| 4 | Kopf (Info) | `§6Turniere` / `§7Hier siehst du alle offenen Turniere.` | – (AN) |
| 9–35 | Kopf des Erstellers je offenes Turnier | `§e{owner}s Turnier` / `§7Kit: §e{kit}` / `§7Spieler: §a{n}§7/{size}` / leer / `§a<Klicke zum Mitspielen>` / `§7Rechtsklick: Kit anzeigen` | join / Vorschau |
| 40 | Kopf (grün, „+“) | `§aTurnier erstellen` / `§7Klicke hier, um dein eigenes` / `§7Turnier zu erstellen` | GUI 5.7a (Recht prüfen) |

5.7a „Turnier erstellen“: Titel `§8Turnier erstellen` · 3 Reihen · Slots 10–16 Kits, Slot 22 Größe `§e{8|16|32} Spieler` (zyklisch; B: „Achtelfinale“ ⇒ ≥16), Slot 26 Smaragd `§aErstellen` / `§7Heute noch §e{rest} §7von §e10`.

### 5.8 PartyGames – „Übersicht“ (Hotbar Kompass)
Titel `§8Übersicht` · 3 Reihen (B).

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–8, 18–26, 9 (außer Belegung) | hellblaue Glasscheibe | – | – |
| 9 | gelbes Item (Würfel-Kopf) | `§eDein Feld: §6{feld}/{felder}` (AN) | – |
| 10–17 | Spielerkopf je Spieler nach Platz | `{rangpräfix}{name}` / `§7Platz: §9#{p}` / `§7Feld: §6{feld}/{felder}` | Teleport zu Figur (AN) |
| leere Plätze | grauer Farbstoff/Kopf ausgegraut | – | – |

Bis 12 Spieler: Köpfe in 10–16 und 19–25 (AN, B zeigt 6 Spieler in Reihe 1).

### 5.9 PartyGames – „Minispiele bewerten“
Titel `§8Minispiele bewerten` · 3 Reihen · Slots 10–16 die gespielten Minispiele (Icon je Minispiel, `§b{Minispiel}` / `§7Deine Bewertung: §e{★★★☆☆|keine}` / `§7Linksklick: +1 Stern, Rechtsklick: −1 Stern`). Speichert `pg.minigame_rating`. B (Item), Layout AN.

### 5.10 Bingo – Karte (`/bingo`)
Titel `§8Bingokarte` · 5 Reihen · Spalten 2–6 (Slots r×9+2 … r×9+6) = 5×5 Items; gefunden = Item mit Verzauberungsglanz + Lore `§aGefunden von {teamfarbe}Team {Farbe}`; nicht gefunden Lore `§7Noch nicht gefunden`; Slots 0,9,18,27,36 und 8,17,… Team-Fortschritt als Wolle in Teamfarbe `{teamfarbe}Team {Farbe}: §7{best}/5`. Beleg: Karte als Map B; Inventar-Ansicht AN (Map-Item bleibt zusätzlich).

### 5.11 ItemChaos – „Modifier“
Titel `§8Modifier` · 3 Reihen · Slots 10–16 Modifier (`§d{Name}` / `§7{Wirkung}` / leer / `§7Kosten: §e1 Token` / `§7Tokens heute: §e{rest}`), Slot 22 Buch `§eAktive Modifier: {liste}`. Kauf nur Premium+ (3/10 pro Tag, B). Modifier-Liste: 6.4 (AN).

### 5.12 TTT – Shop (`/shop`, Smaragd)
Titel `§8Traitor-Shop` bzw. `§8Detective-Shop` · 3 Reihen · Slots 10.. Items in Preisreihenfolge (`§c{Item}` / `§7{Wirkung}` / leer / `§7Preis: §e{n} Punkte` / `§7Nummer: §e{nr}` für `/s <nr>`), Slot 22 `§7Deine Punkte: §e{n}`. Preise 6.10. B (Shop, Nummern), Layout AN.

### 5.13 TTT – Pass (Wartelobby Slot 1)
Titel `§8Rollen-Pässe` · 1 Reihe · Slot 2 rote Wolle `§4Traitor-Pass §7({n})`, Slot 4 blaue Wolle `§9Detective-Pass §7({n})`, Slot 6 grüne Wolle `§aInnocent-Pass §7({n})`; Klick = `/traitor` etc. B (Pässe), Layout AN.

### 5.14 MasterBuilders – Themen-NPC
Titel `§8Themen` · 3 Reihen · Slot 11 Feder `§aThema einreichen` (Chat-Eingabe), Slot 15 Buch `§eThemen bewerten` (zeigt Vorschlag, Slot 12 grün `§aGut`, Slot 14 rot `§cSchlecht`). B (NPC), Layout AN.

### 5.15 Zuschauer – „Wähle einen Spieler“ (alle Rundenmodi)
Titel `§8Wähle einen Spieler` · Reihen = ⌈lebende/9⌉ (min 1) · Spielerköpfe `§b{name}` / `§7Leben: §c{hp} ❤` (AN) · Klick = Teleport. B.

### 5.16 Private Runden (nur Schnittstelle, Spec Kern/Lobby)
Eintrag-Lore B: `{Modus} ({Version})` / `§7Besitzer: §e{name}` / leer / `§7Map: §e{map}` / `§7Spieler: §a{n}/{max}` / leer / `§a<Klicke zum Betreten>`. Hier nur: jeder Modus meldet `privateAllowed`, `versionLabel`, `maxPlayers`.

Anzahl: **16** GUIs (inkl. 5.7a; 5.16 nur Schnittstelle).

---

## 6. Mechaniken & Zahlen

### 6.1 Duels

**6.1.1 Kits (Standard Ära 26; Kit-Registry, alles konfigurierbar)**

| Kit-ID | Typ | Ranked | Inhalt | Beleg |
|---|---|---|---|---|
| `uhc` | KIT | ja | AN: Diamanthelm/-brust Schutz II, Eisenbeine/-stiefel Schutz II, Diamantschwert Schärfe I, Bogen Stärke I + 16 Pfeile, 3 Goldäpfel, 1 Wasser-, 1 Lavaeimer, 64 Bretter, Angel; kein natürliche Regeneration | Kit B, Inhalt AN |
| `crystal` | KIT | ja | AN: Netherite-Rüstung Schutz IV, Netheritschwert, 16 Endkristalle, 16 Obsidian, 8 Goldäpfel, 2 Totems, Respawnanker+Glowstone | Kit B |
| `axe` | KIT | ja | **B:** Diamantrüstung, Schild, Diamantaxt, Diamantschwert | B |
| `potions` | KIT | ja | AN: Diamantrüstung Schutz II, Diamantschwert Schärfe II, 32 Heiltränke II (Wurf), 2 Schnelligkeit II, Steak | B (Kit) |
| `mace` | KIT | ja | AN: Diamantrüstung, Streitkolben, 8 Windkugeln, Diamantschwert, 4 Goldäpfel | B (seit 04/2025) |
| `onlysword` | KIT | nein | AN: Eisenrüstung, Diamantschwert, sonst nichts | B (Name) |
| `tactic` | KIT | – | entfernt 04/2025 (inaktiv) | B |
| Saison-Kit (`trident` 2023, `pyromane` 04/2025, aktuell konfigurierbar) | SEASON | ja | Pyromane AN: Feuerschutz-Rüstung, Feuerkugeln, Flammenbogen | B (Namen) |
| `helden` | EVENT | nein | AN: zufällige Helden-Fähigkeit | B (10/2024) |
| Game-Modi `rushbw`, `sumo`, `sg`, `bedwars`, `skywars`, `aura`, `jumpleague`, `ragemode` | GAME | nein | Mini-Varianten der Hauptmodi (6.1.9) | B (Namen) |
| `custom:<id>` | CUSTOM | nein | Spieler-Loadout | B |

**6.1.2 Matchmaking**
1. Spieler wählt Kit in GUI → Ticket in `queue:duels-<kit>-<ranked|unranked>`; Chat `duels.queue.joined`.
2. Matcher (Controller, Takt 1 s): Unranked = FIFO-Paare; Ranked = Elo-Fenster ±50, +25 je 5 s Wartezeit, max ±400 (AN). Platzierungsspieler (placements < 10) nur gegeneinander oder gegen Fenster ±200 (AN).
3. Paar gefunden → freie Arena auf `duels-match`-Server; kein Server frei → `duels.queue.noServer` und Ticket bleibt (B).
4. Teleport, Freeze 3 s, Countdown 3-2-1 (Chat), Title „LOS!“, `duels.match.start` (B).
5. Zeitlimit 5:00 (B; Turnier 3:00, B). Bei Ablauf: Sieger = mehr Rest-HP, Gleichstand = Unentschieden (AN; Elo ±0).
6. Ende: Title, Chat-Statistik, XP, `[ANZEIGEN]`, `[REVANCHE]` (30 s gültig, AN); Rücksprung in die Lobby nach 5 s (AN).
7. Verlassen während Match = Niederlage (Ranked: volle Elo-Strafe).

**6.1.3 Elo (Ranked)**
- Startwert intern 1000 je Kit und Saison; Anzeige bis 10 Platzierungsmatches: `Liga: Nicht platziert`, `Elo: 0` (B).
- Formel (AN, Standard-Elo): `E = 1 / (1 + 10^((Rb − Ra)/400))`, `Ra' = Ra + K × (S − E)`, `K = 40` in Platzierung, `K = 24` danach, `K = 16` ab 2000 Elo. Rundung kaufmännisch, Mindest-Delta ±1.
- Platzierung: **10 Matches** (B, „Du hast 1 von 10 Platzierungsmatches absolviert“), Meldung nach jedem Platzierungsmatch.
- Saisonwechsel: Elo und Platzierung je Kit zurück auf „Nicht platziert“ (A: im Mai 2025 Elo 0 sichtbar); interner Startwert neue Saison = `1000 + (alt − 1000) / 2` (AN, verhindert Neuanfang bei Null).

**6.1.4 Ligen (AN außer Endpunkte)**

| Liga | Elo | Beleg |
|---|---|---|
| Bronze 1 / 2 / 3 | 0–899 / 900–999 / 1000–1099 | „Bronze 1“ B, Grenzen AN |
| Silber 1 / 2 / 3 | 1100–1199 / 1200–1299 / 1300–1399 | AN |
| Gold 1 / 2 / 3 | 1400–1499 / 1500–1599 / 1600–1699 | AN |
| Diamant 1 / 2 / 3 | 1700–1799 / 1800–1899 / 1900–1999 | AN |
| Supreme | ≥ 2000, eigene Queue (nur Supreme vs Supreme, Fallback nach 60 s ±400) | B (Name, eigene Queue) |
Nummerierung: 1 = niedrigste Stufe (A aus „Bronze 1“ als Einstieg). Hinweis: „Kupfer IV“-Elo-Nametags im Clip 2026-09-24 stammen von einem **Fremdserver** (HeldenInsel) und werden **nicht** übernommen.

**6.1.5 Saison**
- Kalenderquartal: `Jan-Mar`, `Apr-Jun`, `Jul-Sep`, `Oct-Dec` (B, vier Sichtungen; Notiz 03 „meist 3 Monate“). Wechsel 00:00 Europe/Berlin am Quartalsersten (AN).
- Saison-Kit je Saison (B: Season-Kit-Slot), Belohnung nach Endliga (AN: Kosmetik-Titel je Liga, Supreme-Banner).

**6.1.6 Level/XP**
- XP je Match (AN): Sieg 30, Niederlage 10, +1 je 10 Schaden (max +10), Turnier-Rundensieg +20, Turniersieg +100. XP-Boost Premium ×1,25, Supremium ×1,5 (B: Boost existiert; Faktor AN).
- Level-Kurve (AN): XP für Level n→n+1 = `100 + 25 × (n − 1)`.
- Level-Farbstufen (Hologramm-Treppe, B, Zuordnung zu Level A): 0–29 `§7Stein`(AN), 30–59 `§6Bronze`(AN), 60–89 `§fSilber`, 90–119 `§eGold`, 120–149 `§aSmaragd`, 150–179 `§bDiamant`, 180+ `§dAmethyst`; Farbe färbt `[{level}✪]` im Nametag.

**6.1.7 Anfrage/Revanche**
- Anfrage gültig 60 s (AN), max. 1 offene Anfrage pro Paar, Anfragen-Filter über Kern-Einstellungen (alle/Freunde/niemand, AN).
- Party (2 Spieler) → Teams-Queue 2v2 (Hologramm „Teams“ B; Größen 2v2, AN).
- Revanche: beide klicken `[REVANCHE]` innerhalb 30 s → gleiches Kit, gleiche Map, Unranked.

**6.1.8 Turnier**
1. Ersteller (Supremium+, max. 10/Tag B; Staff unbegrenzt) wählt Kit + Größe (8/16/32, AN Default 16).
2. Broadcast in allen Duels-Lobbys: `tournament.announce` + Klickzeile (B).
3. Anmeldung bis voll oder 120 s (AN); < 4 Teilnehmer → abgebrochen.
4. K.-o.-System, Freilos bei ungerader Zahl; Runden heißen (von hinten) Finale, Halbfinale, Viertelfinale, Achtelfinale, „Runde {n}“ (B: „Achtelfinale“).
5. Matchdauer 3:00 (B); Sieger warten: `tournament.waitOthers` (B).
6. Sieger: Broadcast `tournament.won` (B).

**6.1.9 Game-Modi in Duels (Game1vs1)**

| Modus | Regeln | Beleg |
|---|---|---|
| Sumo | Plattform, Rückstoß-Treffer, wer runterfällt verliert; Best of 1; nur Faust | B (Beschreibung) |
| Rush BedWars | 1 Bett je Seite, schnelle Ressourcen, Respawn bis Bett weg | B (Name) / AN |
| Survival Games | Kisten looten, 3 min Loot, dann Kampf | B (Beschreibung) |
| JumpLeague1vs1 | halbe Module (5), Deathmatch max. 5 min, 2 Leben | B |
| BedWars/SkyWars/Aura/RageMode (Game1vs1 alt) | Mini-Varianten | B (Namen), Regeln AN |

**6.1.10 SkillTraining** (in Duels seit „DUELS SKILLTRAINING UPDATE“, B): Solo-Übungen (AN: Bridging, Clutch, Aim) mit Zeit-Bestenliste; eigenes Teilmodul, Prio P3.

### 6.2 PartyGames

1. 12 Spieler (B), Minimum 4 (AN), Teams verboten, Hit-Cooldown aus (B).
2. Wartelobby-Countdown 60 s, bei voll 10 s (AN; B: Chat ab 10 s).
3. Start: Title PartyGames/Map, Regelblock im Chat (eigene Formulierung, B Inhalt): Brett, würfeln bis zum letzten Feld, Minispiele bringen Extrawürfel, Platz 1/2/3 bekommen einen Zusatzwürfel mit eingeschränkten Zahlen.
4. **Zug-Schleife:** Würfelphase (alle gleichzeitig, 15 s, AN; Rechtsklick Würfel, sonst Auto-Wurf) → Figuren ziehen → Ereignisfelder auslösen → Minispiel → Würfelphase …
5. Würfel (AN, Werte aus „eingeschränkte Zahlen“ B): Normal 1–6; Bonus Platz 1: 4–6; Platz 2: 3–5; Platz 3: 2–4 (je einmal, wird zusätzlich geworfen).
6. Ereignisfelder (B: Abkürzung, Teleportation; Rest AN): Abkürzung (+5 Felder über Abzweig), Teleportation (Tausch mit zufälligem Spieler), Zurück (−3), Bonuswürfel (+1 Normalwurf nächste Runde). Anteil ~20 % der Felder (AN).
7. Brettgröße je Map: **36** (Landsby), **45** (Luonto) (B); Endranking zeigt Feld 47–49 → Felder über Ziel möglich? Entscheidung: **Ziel = letztes Feld, Überschuss verfällt**, Endanzeige zeigt Feldnummer; Maps mit 49 Feldern zulässig (A).
8. Siegbedingung: erster auf letztem Feld; bei mehreren gleichzeitig höherer Gesamtwurf, dann mehr gewonnene Minispiele (AN). Rundenlimit 20 Minispiele (AN) → höchstes Feld gewinnt.
9. Minispiel-Ablauf: Ansage `pg.minigame.announce` + Regeltext (B) → Teleport → 5 s Countdown → Spielzeit (je Minispiel, Default 60–120 s) → Countdown-Meldungen 15/10/5/4/3/2/1 (B) → `Das Minispiel ist beendet!` → Title Sieger → Ergebnisblock (#1, #2, #3 mit Gleichständen) → zurück aufs Brett.
10. Ende: Ranking-Scoreboard, Title, Chat-Statistik, Coins, `Der Server startet in 20 Sekunden neu` (B).
11. Belohnung (AN, B nur Beispiele 106–193 Coins und 98–132 XP): Coins = 50 + 10 × gewonnene Minispiele + Platzbonus (1.: 100, 2.: 60, 3.: 40); XP = 60 + 15 × gewonnene Minispiele.
12. Punkte (Statistik „Punkte“): Platz 1 = 10, 2 = 7, 3 = 5, 4 = 3, Rest 1 (AN). Ranking über rollierende 30 Tage (B).
13. Variante **Arcade** (B: „NEUE PARTYGAMES ARCADE“-News 2024): nur Minispiele ohne Brett, Sieger = meiste Minispielsiege nach 10 Minispielen (AN).

**Minispiel-Katalog (Implementierungsreihenfolge = Spalte „Prio“)**

| ID | Name | Prio | Regel (kurz) | Wertung | Beleg |
|---|---|---|---|---|---|
| quake | Quake | 1 | Railgun-One-Shot (Hacke, Partikelstrahl), Respawn mit 2 s Unverwundbarkeit, 60 s | Kills | B |
| tntdemolition | TNT-Demolition | 1 | Struktur mit wenig TNT zerstören; nach 60 s zünden alle TNT; Restblöcke = raus | wenigste TNT | B |
| jumpdown | JumpDown | 1 | Sprung-/Fallparcours auf Zeit | Zeit mm:ss.SSS | B |
| guessit | GuessIT | 1 | ein Spieler baut Begriff (Bossbar `Struktur: n/5`), andere raten im Chat; Actionbar `Thema: _ _ _`; Lösung als Title; Nah-dran-Hinweis | Punkte | B |
| tntrun | TNT Run | 1 | Blöcke fallen unter Füßen | Überleben | B |
| colorjump | ColorJump | 1 | auf richtige Farbe | Überleben | B |
| redlight | Red Light | 2 | Rotes Licht = stehen | Zeit | B |
| elytrarace | Elytra Race | 2 | Ringe-Flug | Zeit | B |
| beefight, bowbash, cactusclimb, colorrun, colorfight, kingofthehill, layers, lobbyjump, oneinthechamber, smash | Launch-Set | 2 | Standardregeln des Genres | je Spiel | B (Namen) |
| waterjump, craftingrace, weihnachtsschmuecken, warehouse, thedropper, golddigger, crystalcave, boattrip, pferderennen, wettrennen, knockout, striderrennen, snake | Erweiterungen | 3 | dto. | je Spiel | B (Namen) |
| auraplus, molecraft, shoppingchaos | Jubiläums-Minispiele | 3 | verkürzte Altmodi (Notiz 14 §2.7) | je Spiel | B |

### 6.3 Bingo

1. Spieler: max 16, Teams à 2 (AN; B nur „Team Rot/Blau“). Teamwahl im Wartelobby-Item (Rahmen).
2. Welt: frische Vanilla-Overworld pro Runde, Spawn-Radius 0, Border AN ±1000.
3. Karte: 5×5 aus Item-Pool (B: auch „Kreatur gefunden“ ⇒ Mob-Aufgaben) nach Schwierigkeitsgewichtung; alle Teams gleiche Karte.
4. Start-Kit Holzwerkzeuge (B), Schutzzeit **AN 5 min**, Meldung `bingo.protection.end` (B).
5. Fund = Item im Inventar (einmal je Team) bzw. Mob getötet → Chat + XP (B), Scoreboard `best/5` = Fortschritt der besten Linie (A: „fehlen noch 3 Items zum Bingo“ bei 2/5).
6. Sieg: erste vollständige Reihe/Spalte/Diagonale (5) → `bingo.win` (B). Zeitlimit AN 60 min → meiste Funde.
7. Deathmatch am Ende: **nicht belegt** (nur Zuschauerfrage) → nicht bauen (Offener Punkt).
8. Rejoin: Verbindungsabbruch → Rejoin 5 min möglich, Inventar bleibt (B: „BINGO REJOIN-SYSTEM UPDATE“, Dauer AN).
9. Bestzeit = Rundenzeit des Siegerteams, Wand Top 10 (B).
10. XP: Item `+5 XP`, Kreatur `+10 XP`, Sieg `+100 XP` (Werte AN, Format B).

### 6.4 ItemChaos (TheLab)

1. 8 Spieler (Map-Richtlinie B), Solo oder Teams (B); Void-Map, je Spieler/Team eigener Turm/Plattform (B).
2. Zufallsitem-Intervall (AN): Start 10 s, alle 60 s −1 s, min. 3 s; Teams: Items abwechselnd an Teammitglieder (B).
3. Events (B Namen, Zahlen AN), alle 45–90 s zufällig: **Shuffle** (Inventare mischen), **Full Life** (volle Herzen), **Itemboost** (Intervall halbiert für 20 s); weitere Events konfigurierbar.
4. Rundenzeit AN 10 min; Ende: letzter Spieler/Team; bei Zeitablauf mehr Kills; Gleichstand = Unentschieden (B).
5. Modifier (B: Tokens Premium 3/Tag, Supremium 10/Tag): AN-Liste `nur_bloecke`, `doppelte_items`, `kein_fallschaden`, `schnelle_items`, `nur_waffen`; je Runde max. 3 Modifier, jeder Spieler 1.
6. Map-Woche (B: „ITEMCHAOS MAPWEEK“): Map-Rotation per Konfig-Liste.

### 6.5 LavaEscape (TheLab)

1. Spieler AN 8, jeder eigener Chunk mit Border (B).
2. Lava steigt alle AN 10 s um 1 Block, Start Y = −60, Stopp Y = +60 (AN).
3. Loot in der Chunk-Mitte (B), Inhalt AN.
4. Nach Lava-Stopp: Border fallen, **2 min** Kampf, Ziel meiste Kills (B).
5. Sieg: meiste Kills; Gleichstand: längste Überlebenszeit (AN).

### 6.6 JumpLeague

1. 12 Spieler (B), Solo.
2. Jump-Phase **11:00** (B): 10 Module (4 leicht, 3 normal, 3 schwer, B) aus Pool, jeder Spieler eigene Strecke (gleiche Module, AN).
3. Je Modul Checkpoint + Kiste; Loot steigt mit Schwierigkeit; Eisenrüstung erst in schweren Modulen (B). Start: volle Lederrüstung + Holzschwert (B).
4. Fehler = Fall → zurück zum Checkpoint (`jl.fail`), zählt `modules_failed`.
5. Ziel erreicht oder Zeit abgelaufen → **Deathmatch**: Arena (1.20-Arenen-Typen B; eigene Maps), **5:00** (B 2026; alt 7:00 → Option), **3 Leben** (B 2026), schrumpfende WorldBorder statt Leuchtfeuer (B): Start Radius AN 50, schrumpft linear auf 5 bis 0:30 vor Ende.
6. Punkte (B): Ziel ohne Fehler 40, Rundensieg 50, Kill im DM 10; Modul abgeschlossen AN 2.
7. Ende DM-Zeit: meiste Leben, dann meiste Kills (AN).
8. Modulserver (B: „JUMPLEAGUE MODULSERVER RELEASE“ 2026): Spieler bauen Module auf eigenem Server, Freigabe durch Team → `jl.module.approved` (Prio P3).

### 6.7 GunGame

1. Dauerserver, max **40** Spieler (B), kein Rundenende (B), Teams beliebiger Größe erlaubt (B 2017, Actionbar 2024).
2. Kill: +1 Stufe, neue Ausrüstung, volle Heilung (B). Tod: −1 Stufe (min 0) (B). Wasser/Lava = sofortiger Tod (B). Respawn in PvP-freier Mittelzone (B).
3. Leave: Stufe geht verloren (B, umstritten → Default **ja**).
4. Stufenrekord persistent (`level_record`).
5. Stufentabelle (B bis 12, Rest AN, Cap bei 45 B-Nutzeraussage):

| Stufe | Ausrüstung |
|---|---|
| 0 | Holzaxt |
| 1 | Holzschwert |
| 2–5 | je +1 Lederteil (Helm, Brust, Hose, Schuhe) |
| 6 | Steinaxt (AN) |
| 7 | Holzschwert Schärfe I |
| 8–11 | Lederrüstung je Teil Schutz I |
| 12 | Goldschwert |
| 13–16 | Kettenrüstung je Teil (AN) |
| 17 | Steinschwert (AN) |
| 18–21 | Kettenrüstung Schutz I (AN) |
| 22 | Steinschwert Schärfe I (AN) |
| 23–26 | Eisenrüstung je Teil (AN) |
| 27 | Eisenschwert (AN) |
| 28–31 | Eisenrüstung Schutz I (AN) |
| 32 | Eisenschwert Schärfe I (AN) |
| 33–36 | Diamantrüstung je Teil (AN) |
| 37 | Diamantschwert (AN) |
| 38–41 | Diamantrüstung Schutz I (AN) |
| 42–44 | Bogen + 8 Pfeile, Angel, Goldapfel (AN) |
| 45+ | Diamantschwert Schärfe I, volle Diamant Schutz I – keine weitere Verbesserung (B) |

6. Kill-Meldung mit Gegnerstufe in Klammern (B). Netzwerk-Level: 15 Kills = 1 Punkt (B, alt; Schnittstelle 8.2).

### 6.8 BuildFFA

1. Dauerserver, 16 oder 32 Slots je Server (B), BedWars-artige Maps (B), Client 1.8.9 (B).
2. Kit-Wahl (B), Kits AN: `rusher` (Steinschwert, 64 Sandstein, Spitzhacke), `bogen` (Bogen+32 Pfeile, 32 Sandstein), `tank` (Eisenrüstung, Holzschwert, 32 Sandstein).
3. Platzierte Blöcke zerfallen nach AN 10 s (Sandstein → rot → weg).
4. Killstreak-Belohnungen (B, Tabelle AN): 3 = Goldapfel, 5 = Enderperle, 10 = Rettungsplattform, 15 = TNT.
5. Respawn sofort im Spawnbereich oberhalb (PvP-frei), Void = Tod.

### 6.9 FFA Hardcore / Classic (historisch)
Hardcore: starke Ausrüstung, +2 Goldäpfel je Kill (B). Classic: Diamantrüstung + Goldäpfel (A). Nur `:game:legacy-modes`, Private Runden.

### 6.10 TTT

1. 12 Spieler (B; 24 als Variante `ttt24`).
2. Rollenvergabe ~30 s nach Start = Schutzzeit; Chat-Countdown `Die Schutzzeit endet in …` (B).
3. Verteilung (AN): Traitor = ⌊n/4⌋ (12 → 3), Detective = ⌊n/8⌋ (12 → 1), Rest Innocent.
4. Rundenzeit **10:00** (B), Zeitablauf = Innocents gewinnen (B).
5. Hit-Cooldown deaktiviert (B) → CombatProfile MODERN mit Cooldown aus (`attackSpeed 1024`).
6. Leichen: Detective identifiziert sofort, andere nach 10 s (AN; B „nach Verzögerung“); Leiche trägt Rest-Punkte → Finder erhält sie (B).
7. Traitor-Tester: schließt 5 s, Glas grün/rot (B); Traitor-Falle (Knopf, Boden weg, B); Meldung „{name} hat den Traitor-Tester betreten“ (B).
8. Shop (B): Traitor: Medipack 1, Radar 1, Creeper-Pfeile 3, Disguiser 3, Tester-Spoofer 4, Snowman 4, Märtyrer 1; Detective: Random-Tester 2 (enthüllt nach 15 s; Addon nennt 40 s → **15 s**), Super-Identifier 2, Traitor-Detector (60 s) 2 (AN Preis). Punkte: Kill 1 Punkt (AN), Detective-Kill Traitor +2 D-Punkte (B).
9. Karma (B): Tabelle Notiz 03; Start 1000 (AN), Max +100 pro Runde (B), < 500 → nur Innocent (AN).
10. Pässe: Traitor-Pass 3 Vote-Keys (B), Einlösen bis „beginnt in 1 Sekunde“.

### 6.11 MasterBuilders

1. Spieler AN 12, je eigener Plot AN 20×20.
2. Themenwahl: 3 zufällige Begriffe, Abstimmung 15 s (AN).
3. Bauzeit **8:00** (7 + 1 Min., B), Kreativ, Fliegen; `/finish` alle → sofort Bewertung (B).
4. Bewertung anonym in zufälliger Reihenfolge, je Plot 15 s (AN), 6 Stufen Grauenvoll…Legendär = 1…6 Punkte (A).
5. Sieger = höchste Summe; Gleichstand = mehr „Legendär“ (AN).

### 6.12 UHC / SpeedUHC (historisch, Event)
1. Teams (Kills/Teamkills-Anzeige B), 12+ Spieler.
2. Abbauphase mit Effekten Gunst des Delfins II, Resistenz III, Eile (B), Erze als Barren (A Cutclean).
3. `Teleport in` Countdown (B, Start AN 15:00) → Teleport in Arena, Schutzzeit (B, AN 60 s).
4. Border ±450 (B), schrumpft danach (AN auf ±50 in 10 min).
5. Szenario pro Runde (B: „Enchantless“), weitere AN.

### 6.13 RageMode (historisch, 1.8)
1. FFA, Ziel **25** Kills/Emeralds (B 2017; Achievement deutet 50 → Option).
2. Items RageBow (One-Shot, AN), RageKnife (Wurf), CombatAxt (Nahkampf), Airstrike (Killstreak).
3. Tod durch Axt: −25 % Kills (B).
4. Teams verboten → Bann (B).
5. Shop, Nemesis, Killstreaks 5/10/20/30 (B Namen).

### 6.14 Cores, Cookies, Conquest, ClanWar (Kurz)
Cores: 2 Teams Rot/Blau, Beacon-Cores, Abbauschwäche nahe Gegner-Core, Kit-Upgrade per Erzblock, +4 Pfeile pro Bogen-Kill, Variante „Cores 16“ (B) – nur Private Runden. Cookies: Notiz 03 §8. Conquest: nicht bauen. ClanWar: eigener Spec-Bereich; Schnittstelle 8.4.

### 6.15 Gemeinsame Zahlen

| Wert | Zahl | Beleg |
|---|---|---|
| Neustart nach Rundenende | 20 s | B |
| Countdown-Meldungen | 60, 30, 20, 10, 5, 4, 3, 2, 1 | B (Teilmenge) |
| Minispiel-Ende-Meldungen | 15, 10, 5, 4, 3, 2, 1 | B |
| Coins Beispiel TTT | +15 bis +57 | B |
| Supremium-Boost | +30 % der Coins je Supremium in der Runde (AN, B nur Existenz) | B/AN |

---

## 7. Nachrichten-Katalog

Spalte „Original-Referenz“ = belegter Original-Wortlaut (gekürzt) oder `–`. Präfix `{p}` = Modus-Präfix aus 4.0.

### 7.1 Gemeinsam

**Hinweis (Integration):** Verbindlich für `common.*` ist **Spec 03 §7.2**. Gleichnamige Zeilen unten (`common.join`, `.quit`, `.online`, `.map`, `.countdown`, `.restart`, `.spectator`, `.remaining`) sind nur Referenz; `common.killed`/`common.youKilled` = Spec 03 `common.kill`/`common.killed_by`; `common.top.*`/`common.stats.*` = Spec 02 `top.*`/`stats.*`. Neue Keys (`common.protection.*`, `common.teaming.*`, `common.result.*`, `common.err.notSpectator`) übernimmt der Rahmen-Katalog.

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| common.join | `§8» {name} §7hat die Runde betreten` | wie Format | „» X hat die Runde betreten“ | belegt |
| common.quit | `§8« {name} §7hat die Runde verlassen` | wie Format | „« X hat die Runde verlassen“ | belegt |
| common.online | `{p}§7Spieler online §b{n}/{max}` | wie Format | gleich | belegt |
| common.map | `{p}§7Map: §a{map} §7von §e{brand}-Buildteam §7(§e{builders}§7)` | wie Format | „…von Vorbild.net-Buildteam (…)“ | belegt |
| common.countdown | `{p}§7Die Runde beginnt in §e{n} §7{Sekunden\|Sekunde}` | funktional gleich | gleich | belegt |
| common.restart | `{p}§cDer Server startet in §e{n} §cSekunden neu` | wie Format | gleich | belegt |
| common.spectator | `{p}§7Du bist nun Zuschauer!` | wie Format | gleich | belegt |
| common.killed | `{p}§7{victim} §7wurde von {killer} §7getötet` | wie Format | gleich | belegt |
| common.youKilled | `{p}§cDu wurdest von {killer} §cgetötet` | wie Format | gleich | belegt |
| common.remaining | `{p}§e{n} §7verbleibende Spieler` | wie Format | gleich | belegt |
| common.protection.end | `{p}§7Die Schutzzeit endet in §e{n} §7Sekunden` | funktional | gleich | belegt |
| common.protection.over | `{p}§7Die Schutzzeit ist vorbei. §cJetzt wird gekämpft!` | eigene Formulierung | „Die Schutzzeit ist vorbei! Lasst die Kämpfe beginnen!“ | belegt |
| common.teaming.forbidden | Actionbar `§cTeams sind hier verboten` | eigene Formulierung | „Teaming ist verboten“ | belegt |
| common.teaming.allowed | Actionbar `§aTeams erlaubt` | gleich | gleich | belegt |
| common.top.header | `{p}§7Die besten Spieler des Monats:` | eigen | – | Annahme |
| common.top.line | `§e#{p} {name} §7- §e{wert}` | eigen | – | Annahme |
| common.stats.header | `§e=- §7Statistiken von §e{name} §7({tage} Tage) §e-=` | Struktur gleich | „-= Statistiken von X (30 Tage) =-“ | belegt |
| common.stats.line | `§7{label}: §e{wert}` | – | – | belegt |
| common.result.line | `{platzfarbe}#{p} §8- {namen}` | gleich | „#1 - Name“ | belegt |
| common.result.sep | `§8-------------------------` | – | – | belegt |
| common.err.notSpectator | `{p}§cDu bist kein Zuschauer.` | eigen | – | Annahme |
| common.tip | `{p}§7{tipp}` (rotierend alle 30 s in Wartelobby) | eigene Tipps | „Wenn man die Rüstung verbessert…“ | belegt |

### 7.2 Duels / Turnier

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| duels.request.received | `§f[§9Duels§f] §a{name} §7fordert dich zu einem Match mit dem Kit §e§l{kit} §7({Ranked\|Unranked}) heraus` | eigene Satzstellung | „X hat dich zu einem Match mit dem Kit UHC (Unranked) herausgefordert“ | belegt |
| duels.request.click | `§f[§9Duels§f] §7Klicke hier zum: §a[ANNEHMEN]` | gleich (Netzwerk-Komponente) | gleich | belegt |
| duels.request.sent | `§f[§9Duels§f] §7Du hast §a{name} §7mit dem Kit §e{kit} §7herausgefordert` | eigen | – | Annahme |
| duels.request.denied | `§f[§9Duels§f] §a{name} §7hat deine Herausforderung abgelehnt` | eigen | – | Annahme |
| duels.request.expired | `§f[§9Duels§f] §7Die Herausforderung von §a{name} §7ist abgelaufen` | eigen | – | Annahme |
| duels.err.offline | `§f[§9Duels§f] §cDieser Spieler ist nicht in deiner Lobby.` | eigen | – | Annahme |
| duels.err.self | `§f[§9Duels§f] §cDu kannst dich nicht selbst herausfordern.` | eigen | – | Annahme |
| duels.err.pending | `§f[§9Duels§f] §cDu hast diesen Spieler bereits herausgefordert.` | eigen | – | Annahme |
| duels.err.noRequest | `§f[§9Duels§f] §cDu hast keine Herausforderung von diesem Spieler.` | eigen | – | Annahme |
| duels.err.notInLobby | `§f[§9Duels§f] §cDas geht nur in der Duels-Lobby.` | eigen | – | Annahme |
| duels.err.inQueue | `§f[§9Duels§f] §cDas geht nicht, während du auf ein Match wartest.` | eigen | „…da du aktuell auf ein Match wartest“ | belegt |
| duels.err.queueLocked | `§f[§9Duels§f] §cDu bist bereits in einer Warteschlange.` | eigen | – | Annahme |
| duels.queue.joined | `§f[§9Duels§f] §7Du bist der Warteschlange für das Kit §e{kit} §7beigetreten` | eigen | „Du hast die Warteschlange für das Kit X betreten“ | belegt |
| duels.queue.left | `§f[§9Duels§f] §7Du hast die Warteschlange verlassen` | eigen | – | Annahme |
| duels.queue.noServer | `§f[§9Duels§f] §7Gerade ist kein Server frei. Dein Match wurde eingereiht, einen Moment bitte.` | eigen | „Da aktuell keine Server verfügbar sind, wurde dein Match in die Warteschlange eingereiht…“ | belegt |
| duels.match.countdown | `§f[§9Duels§f] §7Das Match beginnt in §e{n} §7{Sekunden\|Sekunde}` | funktional | gleich | belegt |
| duels.match.go | Title `§a§lLOS!` | gleich | gleich | belegt |
| duels.match.start | `§f[§9Duels§f] §aDas Match läuft. Viel Glück!` | eigen | „Das Match kann beginnen. Viel Erfolg!“ | belegt |
| duels.match.endsIn | `§f[§9Duels§f] §7Das Match endet in §e{n} §7Minuten` / Actionbar `§7Das Match endet in §e{mm:ss} §7Minuten` | funktional | gleich | belegt |
| duels.match.won.title | Title `§a{name}` / Sub `§7hat das Match gewonnen` | funktional | gleich | belegt |
| duels.match.draw | `§f[§9Duels§f] §eUnentschieden – die Zeit ist abgelaufen.` | eigen | – | Annahme |
| duels.end.damage | `§f[§9Duels§f] §7Schaden zugefügt: §e{x}` | gleich | „Schaden zugefügt …“ | belegt |
| duels.end.hits | `§f[§9Duels§f] §7Schläge: §e{a} §7- §e{b} §7Treffer, davon §e{c} §7kritisch` | gleich | gleich | belegt |
| duels.end.hp | `§f[§9Duels§f] §7Verbleibende Leben: §e{hp} §c❤` | gleich | gleich | belegt |
| duels.end.xp | `§f[§9Duels§f] §7Erhaltene Erfahrung: §e{xp} XP` | gleich | gleich | belegt |
| duels.end.sep | `§f[§9Duels§f] §8------------------------` | – | – | belegt |
| duels.end.stats | `§f[§9Duels§f] §7Match-Statistiken von §e{name}` + `§f[§9Duels§f] §7Klicke hier zum: §a[ANZEIGEN]` | gleich | gleich | belegt |
| duels.end.rematch | `§f[§9Duels§f] §7Klicke hier für eine: §a[REVANCHE]` | gleich | gleich | belegt |
| duels.rematch.waiting | `§f[§9Duels§f] §7Warte auf die Zusage von §a{name}§7…` | eigen | – | Annahme |
| duels.err.rematchExpired | `§f[§9Duels§f] §cDie Revanche ist nicht mehr möglich.` | eigen | – | Annahme |
| duels.err.matchUnknown | `§f[§9Duels§f] §cDieses Match wurde nicht gefunden.` | eigen | – | Annahme |
| duels.placement.progress | `§f[§9Duels§f] §7Platzierungsmatches: §e{n} §7von §e10 §7gespielt` | eigen | „Du hast 1 von 10 Platzierungsmatches absolviert“ | belegt |
| duels.placement.done | `§f[§9Duels§f] §7Du wurdest in die Liga §e{liga} §7eingestuft (§e{elo} Elo§7)` | eigen | – | Annahme |
| duels.elo.change | `§f[§9Duels§f] §7Elo: §e{elo} §7({+\|-}{delta})` | eigen | – | Annahme |
| duels.league.up | `§f[§9Duels§f] §aAufstieg! §7Du bist jetzt in §e{liga}` | eigen | „Liga“-Chathinweis (Inhalt unlesbar) | abgeleitet |
| duels.league.down | `§f[§9Duels§f] §cAbstieg. §7Du bist jetzt in §e{liga}` | eigen | – | Annahme |
| duels.level.up | `§f[§9Duels§f] §7Level-Aufstieg: §6{level}✪` | eigen | – | Annahme |
| duels.season.new | `§f[§9Duels§f] §7Die Season §e{label} §7hat begonnen!` | eigen | – | Annahme |
| duels.update | `§f[§9Duels§f] §7{updatetext}` (Konfig, einmal je Spieler) | eigen | „Ein neues Update ist erschienen…“ | belegt |
| duels.ad.boost | `§f[§9Duels§f] §7Ab jetzt bekommen §6Premium§7- und §bSupremium§7-Spieler mehr Erfahrung!` | eigen | „Ab sofort erhalten Premium- und Supremium-Spieler einen XP-Boost!“ | belegt |
| duels.ad.shop | `§f[§9Duels§f] §7Mehr Vorteile gibt es unter: §e{shopUrl}` | eigen | „Sichere dir jetzt deinen Vorteil unter: shop…“ | belegt |
| tournament.announce | `§7[§6Turnier§7] §a{name} §7startet ein Turnier mit dem Kit §e{kit}` + `§7[§6Turnier§7] §7Klicke hier zum: §a[MITSPIELEN] §a[KIT ANZEIGEN]` | eigen (Zeile 1) | „X spielt nun ein Turnier mit dem Kit Y“ | belegt |
| tournament.joined | `§7[§6Turnier§7] §7Du nimmst am Turnier von §a{owner} §7teil (§e{n}/{size}§7)` | eigen | – | Annahme |
| tournament.round | Actionbar `§6{runde}` + `§7Match in §e{mm:ss} §7Minuten` | gleich | „Achtelfinale“, „Match in 02:57 Minuten“ | belegt |
| tournament.waitOthers | `§7[§6Turnier§7] §7Warte, bis alle anderen Spiele vorbei sind, dann geht es weiter.` | eigen | „Bitte warte bis die anderen Spiele beendet sind, um in die nächste Phase zu kommen“ | belegt |
| tournament.won | `§7[§6Turnier§7] §a{name} §7gewinnt das Turnier von §e{owner}§7. Glückwunsch!` | eigen | „X hat das Turnier von Y gewonnen. Herzlichen Glückwunsch!“ | belegt |
| tournament.err.limit | `§7[§6Turnier§7] §cDu hast heute schon §e10 §cTurniere erstellt.` | eigen | – | abgeleitet (Limit B) |
| tournament.err.rank | `§7[§6Turnier§7] §cTurniere erstellen kann nur §bSupremium§c.` | eigen | – | abgeleitet |
| tournament.err.full | `§7[§6Turnier§7] §cDieses Turnier ist voll.` | eigen | – | Annahme |
| tournament.err.started | `§7[§6Turnier§7] §cDieses Turnier läuft bereits.` | eigen | – | Annahme |
| tournament.err.tooFew | `§7[§6Turnier§7] §cZu wenige Teilnehmer – das Turnier fällt aus.` | eigen | – | Annahme |

### 7.3 PartyGames / Minispiele

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| pg.rules | Kopf `§d✦ PartyGames ✦`, Text `§7` (3 Zeilen) | eigene Regelbeschreibung, gleicher Inhalt | „In PartyGames startest du auf einem Spielbrett…“ | belegt |
| pg.dice.title | Title `§e{n}` / Sub `§7Gewürfelt: §e{n}` | eigen | „Du hast eine 1 gewürfelt“ | belegt |
| pg.event | `§f[§dPartyGames§f] §7Ereignis ausgelöst: §a{ereignis}` | eigen | „Das Ereignis Abkürzung wurde ausgelöst“ | belegt |
| pg.event.title | Title `§a{ereignis}` / Sub `§7Du hast eine §a{ereignis} §7freigeschaltet` | eigen gleiche Struktur | gleich | belegt |
| pg.minigame.announce | `§6Jetzt gespielt: §7✦ §a{minispiel} §7✦` + Regeltext `§7` | eigen | „Es wird gespielt: ✦ TNT-Demolition ✦“ | belegt |
| pg.minigame.endsIn | `§7[§b{minispiel}§7] §7Das Minispiel endet in §e{n} §7{Sekunden\|Sekunde}` | funktional | gleich | belegt |
| pg.minigame.ended | `§7[§b{minispiel}§7] §eDas Minispiel ist vorbei!` | eigen | „Das Minispiel ist beendet!“ | belegt |
| pg.minigame.winner.title | Title `{farbe}{name}` / Sub `§7hat das Minispiel gewonnen` | funktional | gleich | belegt |
| pg.quake.vulnerable | `§f[§bQuake§f] §cDu bist jetzt wieder verwundbar` | eigen | „Du bist nun verwundbar“ | belegt |
| pg.win.chat | `§f[§dPartyGames§f] §a{name} §ahat die PartyGames gewonnen!` | funktional | gleich | belegt |
| pg.win.title | Title `§a{name}` / Sub `§fhat die PartyGames gewonnen` | funktional | gleich | belegt |
| pg.end.header | `§e=- Statistiken dieser Runde -=` | gleich | gleich | belegt |
| pg.end.minigames | `§7Gewonnene Minispiele: §e{n}` | gleich | gleich | belegt |
| pg.end.xp | `§7Gesammelte Erfahrung: §e{n} XP` | gleich | gleich | belegt |
| pg.end.won | `§7Gewonnen: {§a✔\|§c✗}` | gleich | gleich | belegt |
| pg.guessit.correct | `§f[§aGuessIT§f] {name} §7hat das Thema §aerfolgreich §7erraten` | funktional | gleich | belegt |
| pg.guessit.close | `§f[§aGuessIT§f] §eDein Begriff liegt nah am Thema` | eigen | „Dein Begriff ist nahe am Thema“ | belegt |
| pg.guessit.timeUp | `§f[§aGuessIT§f] §7Die Ratezeit ist §cvorbei` | eigen | „Die Ratezeit ist beendet“ | belegt |
| pg.guessit.solution | `§f[§aGuessIT§f] §7Gesucht war: §b{thema}` | eigen | „Das zu erratende Thema war: …“ | belegt |
| pg.guessit.actionbar | `§3Thema: §7{_ _ _}` | gleich | gleich | belegt |
| pg.guessit.bossbar | `§fStruktur: {n}/{max}` | gleich | gleich | belegt |
| pg.tnt.rules | `§7Zerstöre die Struktur mit möglichst wenig TNT. Nach 60 Sekunden explodiert alles; bleibt etwas stehen, scheidest du aus. Wer am wenigsten TNT braucht, gewinnt.` | eigen | Regeltext TNT-Demolition | belegt |

### 7.4 Bingo

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| bingo.found.item | `§f[§cBingo§f] {teamfarbe}{name} §7hat §e{item} §7gefunden §8(+{xp} XP)` | eigen | „X hat das Item Eisenschwert gefunden (+n XP)“ | belegt |
| bingo.found.mob | `§f[§cBingo§f] {teamfarbe}{name} §7hat die Kreatur §e{mob} §7erledigt §8(+{xp} XP)` | eigen | „…hat die Kreatur X gefunden…“ | belegt |
| bingo.missing | `§f[§cBingo§f] {teamfarbe}Team {farbe} §7fehlen noch §e{n} §7Items zum Bingo` | gleich | gleich | belegt |
| bingo.win | `§f[§cBingo§f] {teamfarbe}{name} §7hat Bingo gewonnen §8(+{xp} XP)` | gleich | gleich | belegt |
| bingo.protection.end | `§f[§cBingo§f] §7Die Schutzzeit ist vorbei. §cJetzt wird gekämpft!` | eigen | „Die Schutzzeit ist vorbei! Lasst die Kämpfe beginnen!“ | belegt |
| bingo.hint | Actionbar `§aNutze /bingo, um die Items deiner Bingokarte zu sehen` | eigen | „Nutze /bingo, um dir die Items der Bingokarte anzeigen zu lassen“ | belegt |
| bingo.rejoin | `§f[§cBingo§f] §a{name} §7ist wieder im Spiel` | eigen | – | Annahme |

### 7.5 GunGame, JumpLeague, TTT, MasterBuilders, ItemChaos, LavaEscape, historische Modi

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| gg.kill | `§f[§6GunGame§f] §7Du hast §a{name} §7({stufe}) getötet` | funktional | „Du hast X (4) getötet“ | belegt |
| gg.killed | `§f[§6GunGame§f] §7Du wurdest von §c{name} §7({stufe}) getötet` | funktional | gleich | belegt |
| gg.died | `§f[§6GunGame§f] §7Du bist gestorben` | funktional | gleich | belegt |
| gg.record | `§f[§6GunGame§f] §aNeuer Stufenrekord: §6{n}` | eigen | – | Annahme |
| gg.teams | `§f[§6GunGame§f] §aTeams beliebiger Größe sind auf FFA-Servern erlaubt!` | eigen | „Teams sind auf FFA-Servern in beliebiger Größe ERLAUBT!“ | belegt |
| jl.checkpoint | `§f[§cJumpLeague§f] §7Checkpoint §e#{n} §7erreicht` | eigen | „Du hast Checkpoint #2 erreicht“ | belegt (Jump&Run) |
| jl.bestTime | `§f[§cJumpLeague§f] §aNeue persönliche Bestzeit: §e{zeit}` | gleich | gleich | belegt (Jump&Run) |
| jl.fail | `§f[§cJumpLeague§f] §7Zurück zum Checkpoint §e#{n}` | eigen | – | Annahme |
| jl.dm.start | `§f[§cJumpLeague§f] §cDas Deathmatch beginnt! §7Du hast §e3 §7Leben.` | eigen | – | Annahme |
| jl.lives | `§f[§cJumpLeague§f] §7Leben von §a{name}§7: §c{herzen}` | gleich | „Leben von X: ❤…“ | belegt |
| jl.goal | `§f[§cJumpLeague§f] §a{name} §7hat das Ziel erreicht §8(+{p} Punkte)` | eigen | – | Annahme |
| ttt.role | `§f[§cTTT§f] §eDu bist ein {rollenfarbe}{rolle}` | funktional | gleich | belegt |
| ttt.role.hint.innocent | `§f[§cTTT§f] §eBeobachte genau und schalte alle Traitor aus` | eigen | „Finde und eliminiere durch Beobachten alle Traitor“ | belegt |
| ttt.startedWith | `§f[§cTTT§f] §eDiese Runde begann mit §b{n}/{max} §eSpielern` | eigen | „Diese Runde startete mit 12/12 Spielern“ | belegt |
| ttt.corpse.found | `§f[§cTTT§f] §7Die Leiche von {rollenfarbe}{name} ({rolle}) §7wurde gefunden` | funktional | gleich | belegt |
| ttt.tester.enter | `§f[§cTTT§f] §a{name} §7hat den Traitor-Tester betreten` | funktional | gleich | belegt |
| ttt.randomTester.hit | `§f[§cTTT§f] §e{detective} §7hat einen Random-Tester auf dich gerichtet` | eigen | „…hat einen Random-Tester auf dich angesetzt“ | belegt |
| ttt.randomTester.reveal | `§f[§cTTT§f] {rollenfarbe}{name} §7wurde vom Random-Tester entlarvt` | eigen | „…wurde von einem Random-Tester enttarnt“ | belegt |
| ttt.end.traitor | `§f[§cTTT§f] §4Die Traitor haben gewonnen.` | eigen | „Die Traitor haben alle Innocents eliminiert“ | belegt |
| ttt.end.innocent | `§f[§cTTT§f] §aDie Innocents haben gewonnen.` | eigen | „Die Innocents haben den Anschlag der Terroristen überlebt“ | belegt |
| ttt.chat.traitor | `§4[T] {name}§7: §f{text}` | eigen | – | Annahme |
| ttt.pass.redeemed | `§f[§cTTT§f] §aDein {rolle}-Pass ist eingelöst.` | eigen | – | Annahme |
| ttt.pass.none | `§f[§cTTT§f] §cDu hast keinen {rolle}-Pass.` | eigen | – | Annahme |
| ttt.pass.tooLate | `§f[§cTTT§f] §cPässe kann man nur vor Rundenbeginn einlösen.` | eigen | – | Annahme |
| ttt.shop.bought | `§f[§cTTT§f] §7Gekauft: §e{item} §7(§e-{n} §7Punkte)` | eigen | – | Annahme |
| ttt.shop.noPoints | `§f[§cTTT§f] §cDafür hast du nicht genug Punkte.` | eigen | – | Annahme |
| ttt.shop.wrongRole | `§f[§cTTT§f] §cDer Shop ist nur für Traitor und Detectives.` | eigen | – | Annahme |
| mb.left | `§f[§9MasterBuilders§f] §a{name} §7hat die Runde verlassen` | funktional | gleich | belegt |
| mb.finish.voted | `§f[§9MasterBuilders§f] §a{name} §7ist fertig (§e{n}/{max}§7)` | eigen | – | Annahme |
| mb.finish.all | `§f[§9MasterBuilders§f] §7Alle sind fertig – die Bewertung startet.` | eigen | – | Annahme |
| mb.theme.submitted | `§f[§9MasterBuilders§f] §7Danke! Dein Thema §a{wort} §7wird geprüft.` | eigen | – | Annahme |
| mb.err.themeExists | `§f[§9MasterBuilders§f] §cDieses Thema gibt es schon.` | eigen | – | Annahme |
| ic.event | `§f[§dItemChaos§f] §7Ereignis: §d{event}§7!` | eigen | – | Annahme |
| ic.mod.noTokens | `§f[§dItemChaos§f] §cDu hast heute keine Modifier-Tokens mehr.` | eigen | – | Annahme |
| le.lavaStop | `§f[§6LavaEscape§f] §cDie Lava steht! §7Ihr habt §e2 Minuten§7.` | eigen | – | abgeleitet |
| uhc.teaming | Actionbar `§cTeams mit anderen Teams sind verboten` | eigen | „Teaming mit anderen Teams ist verboten“ | belegt |
| rage.teleport | `§f[§cRageMode§f] §7Alle werden in die Arena teleportiert` | funktional | gleich | belegt |
| rage.noTeams | `§f[§cRageMode§f] §cTeams sind hier verboten und werden mit einem Bann bestraft!` | eigen | „Teams sind in diesem Spiel verboten und werden mit Bann bestraft!“ | belegt |
| rage.started | `§f[§cRageMode§f] §aDas Spiel hat begonnen!` | funktional | gleich | belegt |
| rage.axeDeath | `§f[§cRageMode§f] §cDu wurdest von {name} mit der Axt getötet. Deine Kills: §e-25 %` | eigen | „…mit der Axt getötet“, „Deine Kills -25 Prozent“ | belegt |
| rage.toWin | `§f[§cRageMode§f] §e{name} §7fehlen noch §e{n} §7Kills zum Sieg!` | eigen | „X noch 4 Kills zum Sieg!“ | belegt |
| rage.won | `§f[§cRageMode§f] §a{name} §7hat gewonnen!` | funktional | gleich | belegt |

Anzahl: **144** Meldungs-Keys (7.1: 22, 7.2: 51, 7.3: 22, 7.4: 7, 7.5: 42).

---

## 8. Schnittstellen

### 8.1 Spiel-Rahmen (`:game:game-framework`)

| Schnittstelle | Signatur (Java) | Nutzer |
|---|---|---|
| `GameDefinition` | bestehend; neu Feld `boolean persistent` (GunGame/BuildFFA: kein ENDING) | alle |
| `GameLifecycle` | bestehend; Dauerserver bleiben in `INGAME` | GunGame, BuildFFA |
| `BoardService` (Spec 02) | `show(Player, BoardSpec)`, `update(Player, lineId, Component)`; Diff-Update ≤ 1×/s je Zeile (früher hier `ScoreboardLayout`) | alle |
| `ResultBlock` | `send(Audience, List<Placement>)` mit dichter Platzierung (`#1,#2,#2,#3`) | PartyGames, Minispiele, Bingo |
| `ClickAction` | `Component clickAction(String prefixKey, String label, String command)` (Muster „Klicke hier zum: [X]“) | Duels, Turnier, Kern |
| `SpectatorService` | `makeSpectator(Player)`, Hotbar + GUI 5.15 | alle Rundenmodi |
| `PhaseTimer` (Spec 03) | `start(String id, Duration d, IntConsumer tick, Runnable done)`, `announceAt(int...)` (früher hier `CountdownService`) | alle |
| `minigame.Minigame` (neu) | `String id(); Duration duration(); ScoreType scoreType(); void start(MinigameContext); List<Placement> result();` | PartyGames (Arcade, GuessIT, Quake…) |
| `MatchArena` (neu, Duels) | mehrere Arenen-Instanzen pro Server (Weltkopien `duels_<map>_<n>`) | Duels |

### 8.2 Kern-UI / Stats / Economy (`:core:*`, Spec Kern)

| Dienst | Methode | Aufruf durch |
|---|---|---|
| `StatsService` (Spec 02) | `submit(MatchResult)` (alle Zähler in einem Aufruf), `query`, `top`, `byRank` | alle |
| (Bestenlisten) | `StatsService.top(mode, period, n)` / `byRank` (Spec 02; `period` = `30d`/`all`) | PartyGames-Hologramm, `/top` |
| `CoinService` (Spec 02) | `payoutRound(matchId, base, players)` → `[CoinSystem]`-Zeilen inkl. Supremium-Boost; `credit/debit` | alle |
| `NetworkLevelService` (Spec 02 §8.1, NEU) | `addXp(UUID, source, xp)` (Bingo/PartyGames-„XP“, GunGame 15 Kills = 1 Punkt) | alle |
| `RankService` (Spec 01 §8.1) | `rank(UUID)` → `RankInfo` (Gewicht ≥ `supremium` usw.), Limits aus LuckPerms-Meta | Turnier, ItemChaos, Custom-Kits |
| `I18n` | Keys aus Abschnitt 7 in `core-i18n/messages_de.properties`, Präfixe als `prefix.<mode>` | alle |
| `HologramService`, `NpcService` | Lobby-Hologramme (Duels, PartyGames, Bingo-Wand) | Lobby / Duels-Lobby |

### 8.3 Proxy / Messaging

| Kanal / Event | Payload (JSON) | Sender → Empfänger |
|---|---|---|
| `net:queue` `QUEUE_JOIN` / `QUEUE_LEAVE` | `{ticket, players[], mode, variant, elo?}` | Lobby → Controller |
| `net:queue` `MATCH_ASSIGNED` | `{ticket, server, arena?, matchId}` | Controller → Proxy (verschiebt Spieler) |
| `net:duels` `REQUEST` | `{from, to, kit, rounds, ttl}` | Duels-Lobby → Duels-Lobby (andere Instanz) |
| `net:duels` `REQUEST_ACCEPT` / `REQUEST_DENY` | `{from, to}` | dto. |
| `net:duels` `TOURNAMENT_ANNOUNCE` / `TOURNAMENT_UPDATE` | `{id, owner, kit, size, n, state, round}` | Duels → alle Duels-Lobbys |
| `net:duels` `REMATCH` | `{matchId, uuid}` | Match-Server → Controller |
| `net:server-state` | `SERVER_STATE` (Spec 08): `ServerInfo` mit `online`, `privateRound`, `versionLabel` | Spielserver (über `ServerLifecycle`) → Controller/Lobby |
| Proxy-Befehl `/gameteleport <key>` | Keys aus Prio-Tabelle | Proxy-Spec |
| Party | Party-Leiter zieht Mitglieder mit (bestehendes `party:*`), Duels: Party 2 → Teams-Queue | Proxy-Spec |

### 8.4 Domain-Events (Bukkit-Events, Paket `net.retromc.game.framework.event`)
`GamePlayerKillEvent(killer, victim, mode)`, `GameEndEvent(matchId, placements)`, `MinigameEndEvent(id, placements)`, `DuelEndEvent(matchId, winner, eloDelta)`, `TournamentFinishedEvent(id, winner)`, `BingoCellFoundEvent(team, cell)`, `GunGameLevelChangeEvent(player, old, new)`. Achievements/Erfolge (Spec Kern) hören darauf.

---

## 9. Abnahmekriterien

### 9.1 Duels
1. Kit-GUI „Ranked“: 3 Reihen, Limetten-Rahmen, Kit-Lore exakt 9 Zeilen in der Reihenfolge aus 5.1; bei placements < 10 steht `Liga: Nicht platziert` und `Elo: 0`.
2. Nach jedem der ersten 10 Ranked-Matches erscheint `duels.placement.progress` mit korrekter Zahl; ab Match 10 wird eine Liga angezeigt.
3. Bossbar zeigt am 2026-10-21 `Season: Oct-Dec 2026`; am 2027-01-01 00:00 Berlin `Season: Jan-Mar 2027`, Ratings der neuen Saison „Nicht platziert“.
4. Herausforderung: Empfänger sieht genau 2 Zeilen, `[ANNEHMEN]` klickbar; nach 60 s `duels.request.expired`.
5. Match-Timer: Actionbar zählt von 05:00, Chat bei 5/1 Minuten (AN), Ende bei 00:00 mit Unentschieden-Regel.
6. Endblock: Reihenfolge damage → hits → hp → xp → sep → stats + `[ANZEIGEN]` → sep → `[REVANCHE]`.
7. Turnier mit 16 Spielern: Rundennamen Achtelfinale, Viertelfinale, Halbfinale, Finale; 11. Turnier eines Supremium am selben Tag → `tournament.err.limit`.
- Unit-Tests: `EloCalculatorTest` (gleiche Elo → ±K/2; K-Wechsel bei Platzierung/2000), `LeagueResolverTest` (Grenzen 899/900, 1999/2000), `SeasonClockTest` (Quartalsgrenzen, Zeitzone Berlin, Sommerzeit), `TournamentBracketTest` (Freilose bei 13 Spielern, Rundennamen), `RequestExpiryTest`.

### 9.2 PartyGames / Minispiele
1. Scoreboard wechselt Wartelobby → Brett → Minispiel → Brett → Ende mit exakt den Zeilen aus 4.2.
2. Ranking mit Gleichstand: Werte 21,15,15,15,14 ergeben `#1,#2,#2,#2,#3`.
3. Minispiel-Countdown sendet genau bei 15,10,5,4,3,2,1 (Singular bei 1).
4. Bonuswürfel Platz 1 liefert nur 4–6 (10 000 Würfe).
5. Spieler auf letztem Feld beendet Spiel sofort; Endblock + Neustart in 20 s.
- Unit-Tests: `DensePlacementTest`, `DiceTest`, `BoardMoveTest` (Abkürzung, Teleportation, Überschuss verfällt), `MinigameRegistryTest` (jede ID hat Name, Dauer, ScoreType).

### 9.3 Bingo
1. 5×5-Karte identisch für alle Teams; Scoreboard `x/5` = beste Linie.
2. Bingo bei Reihe, Spalte, beiden Diagonalen erkannt; nicht bei 5 verstreuten Funden.
3. Rejoin innerhalb 5 min erhält Inventar und Team.
4. Bestzeiten-Wand zeigt Top 10 aufsteigend, Format `mm:ss`.
- Unit-Tests: `BingoLineTest` (12 Linien), `CardGeneratorTest` (keine Duplikate, Seed reproduzierbar).

### 9.4 GunGame / BuildFFA / JumpLeague
1. GunGame: Kill +1, Tod −1 (nicht unter 0), Wasserkontakt = Tod, Stufenrekord persistiert, Stufe nach Rejoin 0.
2. Kill-Meldung enthält Gegnerstufe in Klammern; Kills mit Tausenderpunkt (`3.873`).
3. JumpLeague: Jump-Phase 11:00, 10 Module 4/3/3, DM 5:00 mit 3 Leben, Border schrumpft; Punkte 40/50/10.
- Unit-Tests: `GunGameLadderTest` (Stufe 0..60 liefert definierte Ausrüstung, ab 45 konstant), `JumpLeagueCourseTest` (Schwierigkeitsverteilung), `NumberFormatTest` (de_DE).

### 9.5 TTT / MasterBuilders / ItemChaos / LavaEscape
1. TTT 12 Spieler → 3 Traitor, 1 Detective; Rundenende 10:00 → Innocents gewinnen; Karma-Tabelle exakt.
2. MasterBuilders: `/finish` aller Spieler startet Bewertung sofort; 6 Bewertungsitems.
3. ItemChaos: Token-Abzug pro Tag, Reset um 00:00 Berlin; Premium 3, Supremium 10.
4. LavaEscape: Kampfphase genau 120 s nach Lava-Stopp.
- Unit-Tests: `RoleDistributionTest`, `KarmaTest`, `ModifierTokenTest`, `ItemIntervalTest` (10 s → min 3 s).

### 9.6 Allgemein
1. Kein Text enthält den Namen des Vorbilds (Test über alle `messages_*.properties`).
2. Alle Keys aus Abschnitt 7 existieren in `messages_de.properties`, alle `{platzhalter}` werden ersetzt (Test mit Dummy-Werten, kein `{` im Ergebnis).
3. Jede GUI aus Abschnitt 5 hat Titel, Reihenzahl und keine klickbaren Rahmenslots (MockBukkit-Test).

---

## 10. Offene Punkte

| # | Offener Punkt | DEFAULT-ENTSCHEIDUNG |
|---|---|---|
| 1 | Elo-Formel und Liga-Grenzen unbekannt (nur „Bronze 1“ bis „Supreme“, 10 Platzierungen B) | Standard-Elo K=40/24/16, Ligen Bronze/Silber/Gold/Diamant je 1–3 + Supreme ≥ 2000 (6.1.4); alles in `duels.yml` |
| 2 | Kit-Inhalte außer „Axe“ unbekannt | Inhalte aus 6.1.1 (AN), in DB `duels.kit.loadout` änderbar ohne Release |
| 3 | Befehl zum Herausfordern unbekannt | Linksklick mit Schwert auf Spieler (Original-Item B) **und** `/duel <Spieler>` |
| 4 | Hotbar-Funktionen Duels-Lobby außer Slot 1 unbekannt | Belegung 4.1 (Ranked/Unranked/Eigene Kits/Turniere/Einstellungen/Kosmetik) |
| 5 | Level-Treppe (Silber…Amethyst) = Level oder Liga? | Level-Farbstufen (A, Zahlenbereiche passen zu Level 13–164) |
| 6 | Duels Seasonbelohnungen unbekannt | Kosmetik-Titel je Endliga, keine Coins |
| 7 | PartyGames: Felderzahl, Ereignisfelder, Rundenlimit, Bonuswürfel-Werte | Map-abhängig 36/45/49; Ereignisse 6.2; Limit 20 Minispiele; Bonuswürfel 4–6/3–5/2–4 |
| 8 | PartyGames-Minispielregeln im Detail nur für Quake, TNT-Demolition, GuessIT, JumpDown belegt | Genre-Standardregeln, je Minispiel 60–120 s, Implementierung in Prio-Reihenfolge |
| 9 | Bingo: Teamgröße, Schutzzeit, Zeitlimit, Deathmatch | 16 Spieler, Teams à 2, Schutzzeit 5 min, Limit 60 min, **kein** Deathmatch |
| 10 | ItemChaos-/LavaEscape-Zahlen (Intervalle, Lava-Tempo, Rundenzeit, Modifier) | Werte 6.4/6.5 als Konfig |
| 11 | GunGame-Stufen 13–44 | Tabelle 6.7 (AN), Cap 45 |
| 12 | BuildFFA-Kits, Killstreaks, Block-Zerfall | 3 Kits, Streaks 3/5/10/15, Zerfall 10 s |
| 13 | JumpLeague-Scoreboard in Jump-Phase | Zeit/Modul/Checkpoint (4.5) |
| 14 | Hide'n'Seek: nur Quickplay-Key `hns` belegt | nur Modul-Gerüst + Platzhalter in Navigator; Regeln in späterer Spec |
| 15 | TTT-Traitorquote, Karma-Start/Untergrenze | → Spec 05 §10 #16/#17 (führend) |
| 16 | MasterBuilders Spieleranzahl, Plotgröße, Punkte | → Spec 05 §10 #23 (führend: 12 Spieler, 32×32, Noten 0–5) |
| 17 | UHC/RageMode/Cores/Cookies (historisch) Detailregeln | nur Private Runden, Prio letzte; Zahlen 6.12–6.14 |
| 18 | Präfixfarbe Duels (aqua vs. blau) | `§9` (Frames 2025/26) |
| 19 | Scoreboard-Labels mit/ohne Doppelpunkt (Ära-Wechsel) | Ära 26 mit Doppelpunkt; Schalter `scoreboard.colon=false` für 20+ |
| 20 | Bingo-Wand englisch („Quickest game“) im Original | deutsche Schilder, Sprache konfigurierbar |
| 21 | Rewards (Coins/XP) je Modus unbekannt | Formeln 6.2/6.3/6.1.6; einheitlich in `rewards.yml` |
| 22 | Duels-Arenen pro Server | 8 Arenen-Instanzen je `duels-match`-Server, Weltkopie pro Match |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | Pakete `dev.netzwerk.*`, Platzhalter `{brand}`/`{Brand}` mit vertauschter Bedeutung | `net.retromc.*`; `{brand}` = Schreibweise wie konfiguriert, `{BRAND_UPPER}` = Großbuchstaben |
| K2 | Migration `V6__minigames` | `V6_1__minigames.sql` |
| K3 | TTT und MasterBuilders doppelt in Spec 05 und hier (Tabellen `ttt.pass`, `mb.theme`, abweichende Zahlen, Kampfprofil TTT MODERN vs. LEGACY_1_8) | Spec 05 führend; Tabellen hier gestrichen |
| K4 | `stats.total`/`stats.monthly`, Monats-Bestenlisten | Spec 02-Modell (`stats.daily`/`alltime` + `extra`, `lb:{mode}:30d|all`) |
| K5 | Modus-Präfix `§7[…§7]` (Spec 02: `§f`, Spec 05: `§8`) | einheitlich `§f[…§f]`; Systempräfixe `§7` |
| K6 | Rahmen-Texte doppelt/abweichend (`§8»`, Countdown-Raster ohne 50/40, Trennlinie 25 Zeichen, eigener `common.*`-Katalog) | Spec 03 §7.2 / Spec 02 Rundenblock (22 Zeichen) |
| K7 | Tablist/Scoreboard-Titel mit Original-Wortlaut bzw. ohne `.NET` | Spec-02-Keys; `§f§l{BRAND_UPPER}.NET` |
| K8 | `/forcemap` „Premium+“, `/start` „Staff/YouTuber“ | Spec 03 #4/#5 |
| K9 | Schnittstellen `CountdownService`, `ScoreboardLayout`, `StatsService.increment`, `LeaderboardService`, `EconomyService`, `NetworkLevelService` ohne Anbieter | `PhaseTimer` (03), `BoardService`/`StatsService.submit`/`CoinService`/`NetworkLevelService` (02) |
| K10 | Zuschauer-Hotbar abweichend | Spec 03 §4.11 |
