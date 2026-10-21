# Spec 03 – Lobby & Spiel-Rahmen (`:lobby` + `:game:game-framework`)

Stand 2026-10-21 · Bereich: Hauptlobby, Modus-Lobbys, Silent-Lobby, NPC-/Schilder-Join, Belohnungen, Private Runden; Rundenrahmen aller Minigames (Wartelobby, Countdown, Teams, Zuschauer, Kill-Meldungen, Rundenende, Rückkehr).

**Kürzel Belege:** N02/N03/N05/N09/N17 = Notizen `research_notes/GommeHD Analyse und Nachbau/NN_*.md` (N09 = Wiki-GUI G6 „Wähle ein Ziel“) · V = Video-Sweep (`scratchpad/sweep/batch2_*.md`, `thumbs.md`) · OCR = `sweep/review/*.md` · F = eigene Bildsichtung `videos/tw_gommehd_clip_20260728_1361148519` t08 s/t10 s (Lobby-Hotbar 2026, Menü „Private Runden“, Scoreboard).
**Beleg-Stufen:** *B* = belegt · *A* = abgeleitet · *AN* = Annahme (festgelegt, ohne Beleg).
**Ära:** **26** = Standard (neue 1.21-Lobby ab ca. 05/2026) · **20+** = 2023 bis 04/2026 (Option) · **18** = 1.8-Ära (Option `legacyStyle`, Schilder-Join).
**Textregel:** §-Notation, Umsetzung in `core-i18n` als MiniMessage (`§7`→`<gray>`, `{x}`→`<x>`). `{prefix}` = `prefix.<mode>` (Spec 02 §7), `{p:lobby}` = `§6[Lobby] `, `{p:coins}` = CoinSystem-Präfix (Spec 02). Marke: `{brand}`, `{BRAND_UPPER}`. Ränge nach **Spec 01 §6.1** (LuckPerms-Gruppen `default`, `premium`, `premiumplus`, `supremium`, `supremiumplus`, `vip`, Team); „X+“ = Gewicht ≥ X (z. B. `supremium`+ = Supremium, Supremium+, VIP, Team).

---

## 1. Umfang & Module

| Gradle-Modul | Status | Paket | Plugin-Name / Art | Inhalt |
|---|---|---|---|---|
| `:lobby` | vorhanden (Gerüst) | `net.retromc.lobby` (Unterpakete `hotbar`, `nav`, `npc`, `sign`, `hide`, `move`, `reward`, `privateround`, `switcher`, `news`, `parkour`, `setup`) | Paper-Plugin `${brandName}Lobby`, `main: net.retromc.lobby.LobbyPlugin` | Hauptlobby, Silent-Lobby, Premium-Lobby, Modus-Lobbys (eine Codebasis, Profil per `lobby.yml` → `lobby.type`) |
| `:game:game-framework` | vorhanden (Gerüst `GameState`, `GameLifecycle`, `GameDefinition`, `MiniGamePlugin`, `CombatProfile`) | `net.retromc.game.framework` (Unterpakete `phase`, `waiting`, `team`, `map`, `spectate`, `kill`, `end`, `privateround`, `chat`, `event`) | Bibliothek (`java-library`), in jedes Modus-Jar eingebettet (shadow) | Rundenrahmen für alle Minigames |
| `:services:controller` | vorhanden | `net.retromc.controller` | Dienst | nur Schnittstelle (Queue, Server-Start, Private Runden), **Spec 08** |

**Abhängigkeiten (Ergänzung `build.gradle.kts`):**

| Modul | compileOnly | implementation | Test |
|---|---|---|---|
| `:lobby` | `libs.paper.api`, `:platform:paper-core`, `:core:core-api`, `:core:core-data`, `:core:core-messaging`, `:core:core-i18n` (Laufzeit über `join-classpath` von `${brandName}Core`) | – | JUnit 6, MockBukkit (AN) |
| `:game:game-framework` | `libs.paper.api`, `:core:core-api`, **neu** `:platform:paper-core`, `:core:core-messaging`, `:core:core-i18n` | – | JUnit 6 (reine Logikklassen ohne Bukkit: `CountdownPlan`, `TeamAllocator`, `RestartPlan`, `HeartLine`, `ForcemapPolicy`) |

`paper-plugin.yml` der Lobby: Abhängigkeit `${brandName}Core` (`load: BEFORE`, `required: true`, `join-classpath: true`) – vorhanden. Fremd-Plugins: keine. NPCs als eigene `Mannequin`-/Spieler-Fake-Entitäten über paper-core `NpcService` (Spec 02 §8.1 nennt `HologramService`; `NpcService` hier **NEU** in paper-core, §8.3).

---

## 2. Datenmodell

### 2.1 PostgreSQL – Migration `db/migrations/V3_1__lobby_game.sql` (NEU)

| Tabelle | Spalten (Typ) | Schlüssel / Indizes | Zweck / Beleg |
|---|---|---|---|
| `lobby.daily_reward` | `uuid UUID NOT NULL`, `streak INT NOT NULL DEFAULT 0`, `last_claim DATE NULL` (Europe/Berlin), `total_claims INT NOT NULL DEFAULT 0`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `uuid` | Tagesbelohnung + Coin-Streak (B N05: Streak existiert) |
| `lobby.daily_claim_log` | `id BIGSERIAL`, `uuid UUID NOT NULL`, `day DATE NOT NULL`, `rank VARCHAR(16) NOT NULL`, `coins INT NOT NULL`, `tickets SMALLINT NOT NULL`, `extra JSONB NOT NULL DEFAULT '{}'` (Gratiskiste, Coinbombe) | PK `id`; UNIQUE `(uuid, day)` | Idempotenz (ein Claim pro Tag) |
| `lobby.lottery_ticket` | `uuid UUID NOT NULL`, `tickets INT NOT NULL DEFAULT 0 CHECK (tickets >= 0)` | PK `uuid` | Lose-Bestand (B N05: 1/2/4 täglich) |
| `lobby.lottery_draw` | `id BIGSERIAL`, `uuid UUID NOT NULL`, `prize_coins INT NOT NULL`, `tier VARCHAR(12) NOT NULL`, `drawn_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `id`; IDX `(uuid, drawn_at DESC)` | Audit, Broadcast ab Tier `BIG` |
| `lobby.vote_key` | `uuid UUID NOT NULL`, `keys INT NOT NULL DEFAULT 0 CHECK (keys >= 0)`, `total_votes INT NOT NULL DEFAULT 0` | PK `uuid` | Vote-Keys (B N05) – Gutschrift durch `services:api` (Vote-Webhook) |
| `lobby.anchor` | `id BIGSERIAL`, `server_group VARCHAR(32) NOT NULL` (z. B. `lobby`, `lobby-bedwars`), `type VARCHAR(16) NOT NULL CHECK (type IN ('NPC_JOIN','NPC_DAILY','NPC_PRIVATE','NPC_REPLAY','NPC_COMMUNITY','NPC_DIALOG','SIGN_JOIN','HOLO_TEXT','SPAWN','WARP'))`, `key VARCHAR(32) NULL` (Modus-/Varianten-Key, Warp-Name), `world VARCHAR(64) NOT NULL`, `x/y/z DOUBLE PRECISION NOT NULL`, `yaw REAL NOT NULL DEFAULT 0`, `pitch REAL NOT NULL DEFAULT 0`, `data JSONB NOT NULL DEFAULT '{}'` (Skin, Hologrammzeilen-Keys, Dialogzeilen, Schild-Zeilenvorlage), `created_by UUID`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `id`; IDX `(server_group, type)` | Platzierung über `/lobbysetup` (Stats-Wände/-Hologramme bleiben in `core.display_anchor`, Spec 02) |
| `lobby.parkour_progress` | `uuid UUID NOT NULL`, `parkour_id VARCHAR(32) NOT NULL` (Lobby-JnR oder Wartelobby-Parkour je Modus), `completions INT NOT NULL DEFAULT 0`, `best_ms INT NULL`, `rewarded SMALLINT NOT NULL DEFAULT 0` | PK `(uuid, parkour_id)` | erste 3 Abschlüsse belohnt (B N05 News #17) |
| ~~`game.map`~~ | **entfällt** (Integration): Map-Registry ist `infra.map`/`infra.map_version` (Spec 08 §2.1). Diese Migration ergänzt nur `ALTER TABLE infra.map ADD COLUMN weight SMALLINT NOT NULL DEFAULT 10, ADD COLUMN forcemap_allowed BOOLEAN NOT NULL DEFAULT true, ADD COLUMN icon VARCHAR(48) NOT NULL DEFAULT 'paper'` | – | Map-Registry für Controller (Auswahl) und `/forcemap` |
| `games.private_round` | `id UUID NOT NULL`, `owner UUID NOT NULL`, `mode VARCHAR(24) NOT NULL`, `variant VARCHAR(16) NULL`, `map VARCHAR(48) NOT NULL`, `version_label VARCHAR(16) NOT NULL` (z. B. `1.21.11+`), `max_players SMALLINT NOT NULL`, `server VARCHAR(48) NULL`, `state VARCHAR(10) NOT NULL CHECK (state IN ('REQUESTED','STARTING','OPEN','RUNNING','CLOSED'))`, `settings JSONB NOT NULL DEFAULT '{}'`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `closed_at TIMESTAMPTZ NULL` | PK `id`; partieller UNIQUE `(owner) WHERE state <> 'CLOSED'`; IDX `(state, created_at)` | 1 aktive Runde pro Besitzer (AN) |
| `games.replay` | `id CHAR(8) NOT NULL` (Base62), `match_id UUID NOT NULL`, `mode VARCHAR(24) NOT NULL`, `map VARCHAR(48) NOT NULL`, `server VARCHAR(48) NOT NULL`, `started_at TIMESTAMPTZ NOT NULL`, `ended_at TIMESTAMPTZ NULL`, `storage_uri TEXT NULL`, `players UUID[] NOT NULL` | PK `id`; UNIQUE `match_id`; GIN `(players)` | Replay-Hinweise/NPC „REPLAYS“ (Existenz B N17, Ablauf AN) |

Spieler-Einstellungen der Lobby liegen **nicht** in eigenen Tabellen, sondern in `core.player.settings` (JSONB) über `SettingsService` (Spec 01): Schlüssel `lobby.hide` (`ALL`/`VIP`/`NONE`), `lobby.hide.vipGroups` (Bitmaske: 1 Premium/Premium+, 2 Supremium/Supremium+, 4 VIP, 8 Freunde, 16 Team, 32 Clan), `lobby.hide.chatSync` (bool), `lobby.move` (`WALK`/`DOUBLE_JUMP`/`FLY`), `lobby.time` (`DAY`/`SUNSET`/`NIGHT`/`REAL`), `lobby.join.preferLast` (bool).

### 2.2 Valkey – Schlüssel und Kanäle

| Schlüssel / Kanal | Typ | TTL | Inhalt / Regel |
|---|---|---|---|
| `server:{name}` | HASH | 15 s, Heartbeat 5 s (Spec 08 §2.2, einzige Definition) | Felder nach Spec 08 (`state` = WAITING/STARTING/INGAME/ENDING, `online`, `max`, `joinable`, `privateRound`, `versionLabel` …). Zusatzfelder des Spiel-Rahmens (in Spec 08 §2.2 eingetragen): `countdown` (s, −1), `spectatable` (0/1), `teams`, `teamSize`, `privateId`, `owner`. Der Rahmen schreibt nur über `ServerLifecycle` (Spec 08 §8.1); bei Zustands-/Spielerzahlwechsel sofort, in STARTING `countdown` jede Sekunde. Lobby-Nummer = Zahl im Servernamen |
| `servers:all`, `servers:tpl:{template}` (Spec 08) | SET | keine | Index; Abfrage nur über `ServerRegistry.byCategory(LOBBY)` / `byMode(mode, variant)` (Spec 08 §8.1) für Lobbywechsler, NPC-GUI, Schilder |
| `queue:{variant}` (vorh.) | ZSET | keine | Tickets für Rechtsklick-NPC / „Nächste Runde“ / `/gameteleport` auf Spielvarianten (Score = Zeit − Join-Priorität × 10⁶) |
| `ticket:{id}` (Spec 08, Eigentümer Controller) | HASH | 60 s | `players`, `mode`, `variant`, `partyId`, `createdAt`, `state`; hier zusätzlich `source` (`NPC`/`SPECTATOR_NEXT`/`SIGN`) |
| `player:{uuid}:ticket` | STRING | 60 s | offenes Ticket (Doppelklick-Schutz → `common.spectator.next.already`) |
| `cnt:forcemap:{uuid}:{yyyy-mm-dd}` | STRING (INCR) | 26 h | Tageszähler `/forcemap` (Reset 00:00 Europe/Berlin) |
| `cnt:start:{uuid}:{yyyy-mm-dd}` | STRING (INCR) | 26 h | nur Statistik (kein Limit, AN) |
| `lock:daily:{uuid}` | STRING `SET NX PX 5000` | 5 s | paralleler Claim auf zwei Lobbys |
| `cd:lobby:hide:{uuid}` | STRING `SET NX PX 3000` | 3 s | Umschalt-Cooldown Spieler verstecken (AN) |
| `private:{id}` | HASH | 30 s, Heartbeat 5 s | Live-Stand einer Privatrunde (`online`, `max`, `map`, `state`) für das Menü |
| `privates:open` | ZSET (Score = created_at) | keine | Index offener Privatrunden |
| `lobby:npcstats:{mode}` | HASH (`online`, `waiting`, `running`) | 5 s | Summen für NPC-Hologramme, schreibt der Controller (AN) |
| `ui:news` (Spec 02) | LIST | keine | Newsticker; gepflegt mit `/lobbynews` |
| `replay:last:{uuid}` | LIST (max 10 IDs) | 30 d | „Meine Replays“ im NPC-Menü |
| Kanal `net:server-state` (vorh.) | Pub/Sub | – | `SERVER_STATE` (Spec 08 §8.3, Payload = `ServerInfo` + Zusatzfelder) bei jedem Zustands-/Spielerzahlwechsel |
| Kanal `net:queue` (vorh. lt. Spec 06) | Pub/Sub | – | `QUEUE_JOIN`, `QUEUE_LEAVE`, `MATCH_ASSIGNED` |
| Kanal `net:private` (**NEU**) | Pub/Sub | – | `PRIVATE_CREATE`, `PRIVATE_READY`, `PRIVATE_CLOSED` |
| Kanal `net:game` (**NEU**) | Pub/Sub | – | `GAME_ENDED{matchId, mode, variant, map, winners[], durationS, replayId?, players[]}` (Rückkehr in die Lobby über `SEND_LOBBY`, Spec 08) |

---

## 3. Befehle

| # | Syntax | Aliase | Recht / Rang | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 1 | `/l` | `/hub`, `/lobby`, `/leave` | alle | **Proxy (Spec 01 #63).** Auf Spielserver zusätzlich: Quit-Meldung `common.quit`, Abmeldung aus Team/Queue | `lobby.already`, `screen.nolobby` (Spec 01) |
| 2 | `/gameteleport <key>` | – | alle | **Proxy (Spec 01 #64).** Keys §6.12; Ziel = Modus-Lobby (Template `lobby-{mode}`, Kategorie `LOBBY`, Spec 08 §6.5) oder Dauerserver | `gameteleport.unknown` (Spec 01) |
| 3 | `/spawn` | – | alle, nur Lobby | Teleport zum `SPAWN`-Anker (wie Kompass „Spawn“) | – (AN) |
| 4 | `/start` | – | `retromc.game.start` = `premium` und höher (AN, wie Spec 05); in Privatrunde Besitzer immer | nur Phase WAITING/STARTING, Spieler ≥ `minPlayers`; setzt Countdown auf **10 s** (nur wenn Rest > 10) | `common.start.done` (Broadcast `common.start.broadcast`), Fehler `common.start.too_few` (§c), `common.start.already` (§c, Rest ≤ 10 s), `common.not_in_lobby`, `common.noperm` |
| 5 | `/forcemap <map>` | `/fm` (AN) | `supremium` 10/Tag, `supremiumplus` 30/Tag, VIP/Team ∞ (Spec 01 §6.2); Besitzer Privatrunde ∞ | Phase WAITING oder STARTING mit Rest > 10 s; Map muss Variante unterstützen, `forcemap_allowed`; 1 Forcemap pro Runde; lädt Map asynchron, sendet `common.map` neu | `common.forcemap.done` (Broadcast), Fehler `common.forcemap.limit` (`{used}/{max}`), `.unknown`, `.too_late`, `.already`, `.same`, `common.noperm`; Tab-Vervollständigung: Map-Namen der Variante |
| 6 | `/top [statsall]` | `/top 30d` | alle | **Spec 02**, in Wartelobby und ganzer Runde | Spec 02 |
| 7 | `/privaterunde` | `/pr`, `/privat` | Erstellen: `supremium` aufwärts; Menü öffnen: alle | ohne Argument: GUI 5.5 „Private Runden“ | `private.err.rank` |
| 8 | `/pr kick <Spieler>` | – | Besitzer der aktuellen Privatrunde | Spieler → Lobby | `private.kick.done`, `.err.notowner`, `.err.notfound` |
| 9 | `/pr close` | – | Besitzer | Runde schließen: alle → Lobby, Server stoppt | `private.closed` (an alle) |
| 10 | `/pr max <n>` | – | Besitzer, WAITING | 2 ≤ n ≤ `mode.privateMax` | `private.max.done`, `.err.range` |
| 11 | `/replay <ID>` | – | alle | verbindet mit Replay-Server (`category=REPLAY`) und lädt ID | `replay.connect`, `replay.err.unknown` (AN, Bedienung unbelegt) |
| 12 | `/lobbysetup npc <type> [key]` · `sign <variant>` · `holo <i18nKey…>` · `spawn` · `warp <name>` · `remove` · `list` | `/ls` | `retromc.lobby.setup` (Team Builder/Admin) | legt `lobby.anchor` an der Blickposition an / entfernt nächsten Anker (Radius 3) | `setup.saved #{id}`, `setup.removed`, `setup.err.type` |
| 13 | `/lobbynews add <farbe> <url> <titel…>` · `remove <n>` · `list` | – | `retromc.lobby.news` | pflegt `ui:news` | `news.added`, `news.removed`, `news.list` |
| 14 | `/game forcestart` · `/game stop` · `/game setmap <map>` · `/game state` | – | `retromc.game.admin` | Admin: Countdown sofort 5 s ohne Mindestspieler / Runde sofort ENDING / Map ohne Limit / Statusausgabe | `game.admin.*` (Klartext §7/§e, AN) |
| 15 | `/team <farbe>` | – | alle, WAITING/STARTING, Teammodi | wie Klick im Team-GUI | wie GUI 5.10 (AN, Komfort) |

Befehls-Fehlerformat: Präfix des Servers + `§c`-Text (Spec 02). Unbekannter Befehl: Paper-Standard.

---

## 4. Anzeige-Elemente

### 4.1 Lobby-Scoreboard (Ära 26, `MODERN`, 15 Zeilen) – Rendering Spec 02 §4.2, hier verbindlich übernommen
| Score | Text | Farbe / Wert | Beleg |
|---|---|---|---|
| Titel | `§f§l{BRAND_UPPER}.NET` (Marke + `.NET`, AN statt Domain) | – | B (V „GOMMEHD.NET“, F) |
| 14 | (leer) | – | B |
| 13 | `Rang` ⇄ `Spielzeit` (Wechsel alle 5 s) | `§f` | B (V, F zeigt beide Zustände) |
| 12 | `{rankColor}{rankName}` ⇄ `§6{hours} Stunden` | Stunden mit Tausenderpunkt (`2.079 Stunden`, F) | B |
| 11 | (leer) | – | B |
| 10 | `Coins` | `§f` | B |
| 9 | `§e{coins}` | Tausenderpunkt (`1.413.636`, F) | B |
| 8 | (leer) | – | B |
| 7 | `Gold` | `§f` | B |
| 6 | `§6{gold}` | – | B |
| 5 | (leer) | – | B |
| 4 | `Clan` | `§f` | B |
| 3 | `§b{online}§7/{members} §7[§b{tag}§7]`, ohne Clan `§7-` | `6/26 [GOMME]` (F) | B |
| 2 | (leer) | – | B |
| 1 | `Freunde` | `§f` | B |
| 0 | `§a{online}§7/{total}` | `1/83` (F) | B |

Silent-Lobby, Premium-Lobby: identisch (B V 2025 Silent-Lobby). Ära 18 (Option): `Coins` / `Dein Clan` / `Freunde` (B N17). Modus-Lobbys: gleiches Board; Duels-Lobby eigenes Board (Spec 06).

### 4.2 Wartelobby-Scoreboard (Spiel-Rahmen, alle Modi; Zeilen über `WaitingBoardProvider`)
| Score | Text | Farbe | Beleg |
|---|---|---|---|
| Titel | `§f§l{BRAND_UPPER}.NET` | – | B |
| 8 | (leer) | – | B (Nummern 8..0) |
| 7 | `Map` | `§f` | B |
| 6 | `§e{map}` | – | B (Dragons, Fairy, Luonto) |
| 5 | (leer) | – | B |
| 4 | Block 2 (Modus wählt): `Spieler` → `§e{teams}x{teamSize}` (BedWars `8x4`, B) · `Teams` → `§aErlaubt` / `§cVerboten` (PartyGames, EG, B) · `Kit` → `§a{kit}` (SkyWars, B) | `§f` / Wert | B |
| 3 | Wert Block 2 | s. o. | B |
| 2 | (leer) | – | B |
| 1 | `Hit-Cooldown` | `§f` | B |
| 0 | `§aAktiviert` / `§cDeaktiviert` (aus `CombatProfile`: `MODERN` = Aktiviert, `LEGACY_1_8` = Deaktiviert) | – | B |

Optional 3. Block (Modus-Konfig, max. 2 zusätzliche Blöcke): z. B. TTT `Karma`, SG `Teams`. Im INGAME-Board ersetzt das Modul alle Zeilen (Modus-Specs). Countdown erscheint **nicht** im Scoreboard, sondern im XP-Balken (§4.6).

### 4.3 Tablist
Rendering Spec 02 §4.4. Header Zeile 1: `§e{brand}.net {modeColor}{serverDisplay}` (Spec 02 §4.4), z. B. Lobby `§e{brand}.net §aLobby-3`, Silent-Lobby `§e{brand}.net §cSilent-Lobby-1` (B), Modus-Lobby/Spiel `§e{brand}.net {modeColor}{displayName}` (B). Einträge in Runden: `{teamColor}{teamName} §7| §f{name}` (Teammodi, B); Solo-Modi: Lobby-Format mit Rangfarbe. Spectator-Einträge: `§7{name}` kursiv, ans Ende sortiert (AN). Party-Mitglieder: Nametag-Suffix ` §7[§dParty§7]` (B V BedWars/SkyWars).

### 4.4 Bossbar
| Ort | Inhalt | Beleg |
|---|---|---|
| Lobby Ära 26 | keine (News über Actionbar) | B (F) |
| Lobby Ära 18 | `§e§l{TITLE}` News, Kanal `news` (Spec 02 §4.5) | B |
| Wartelobby | keine | B (keine Sichtung) |

### 4.5 Actionbar
| Ort | Inhalt | Beleg |
|---|---|---|
| Lobby | Newsticker `§e+ {color}§l{TITLE}: §f{url}` aus `ui:news`, Wechsel 8 s (Spec 02) | B (F: „+ DUELS SKILLTRAINING UPDATE: …/news/161“) |
| Wartelobby, Teammodus, ohne Team | `§fWähle dein Team` dauerhaft | B (V BedWars/SkyWars) |
| Wartelobby, mit Team | `{teamColor}Team {teamName}` dauerhaft (bleibt INGAME) | B |
| Wartelobby, zu wenige Spieler | `§7Warten auf Spieler §8(§e{n}§8/§e{min}§8)` | AN |
| Zuschauer | `§7Zuschauer` dauerhaft | B |

### 4.6 XP-Balken & Titles
- **Countdown im XP-Balken:** `level = Restsekunden`, `exp = Rest/Gesamt` während STARTING und der Restart-Phase; WAITING `level 0, exp 0` (B V „XP-Level 10 → 2“). Der echte XP-Stand wird beim Rundenstart nicht angetastet (Lobby-XP sind Anzeige).
- **Titles:** Modus-Intro beim Teleport in die Map, Start, Sieger: Spec 02 §4.8. In der Wartelobby keine Titles (AN). Countdown-Title `§e{n}` bei 3/2/1 als Option `titles.countdown` (Standard aus, AN; PartyGames „Countdown-Titel“ B nur für Minispiele).
- **Sounds (AN):** `block.note_block.hat` bei Ansagen ≥ 10 s, `block.note_block.pling` (Pitch 1.0) bei 5..1, `entity.player.levelup` bei 0.

### 4.7 Hologramme (Lobby)
| Anker | Zeile 1 | Zeile 2 | Zeile 3 | Beleg |
|---|---|---|---|---|
| `NPC_DAILY` | `§b§lTÄGLICHE BELOHNUNG` | `§fDeine Tagesbelohnung wartet!` | – | Struktur/Farben B (V 2024), Text eigen |
| Item-Truhe (Cosmetics-Spec) | `§a§lITEM-TRUHE` | `§7Öffne sie für ein seltenes Item` | – | B (Struktur), Text eigen |
| Coin-Truhe (Cosmetics-Spec) | `§6§lCOIN-TRUHE` | `§7Bis zu §e1.000.000 §7Coins drin` | – | B (Betrag), Text eigen |
| `NPC_PRIVATE` | `§c§lPRIVATE RUNDE` (groß, Scale 2.0) | `§aStarte deine §lEIGENE§a Runde!` (Scale 1.5) | – | B (F: rot fett / grün, „EIGENE“ hervorgehoben), Text eigen |
| `NPC_REPLAY` | `§e§lREPLAYS` | `§7Schau dir deine Runden an` | – | Titel B (N17), Zeile 2 AN |
| `NPC_COMMUNITY` | `§6§lCOMMUNITY` | `§7{n} Spieler online` | – | B (V „GOMMEUNITY“ / „91 Spieler online“), Name eigen |
| `NPC_JOIN` (Ära 26) | `{modeColor}§l{MODE}` | `§7{online} Spieler online` | `§eRechtsklick §7Spielen §8┃ §eLinksklick §7Runden` | Z1/Z2 Muster B, Z3 AN |
| `NPC_DIALOG` | `§e§l{NAME}` | `§7(Rechtsklick)` | – | Muster B (CityBuild-NPCs „(Rechtsklick)“ §7) |
| Schilder-Spalte (Ära 18/20+) | `{modeColor}{MODE}` über Schildreihe, darunter `{variantColor}{Variante}` je Spalte | – | – | B (V BuildFFA „Classic/Rod/Bow“, BedWars „❄ UPDATE ❄“) |
| Update-Hinweis (optional) | `{modeColor}{MODE}` | `§b❄ UPDATE ❄` (frei konfigurierbar) | – | B |

Technik: `TextDisplay` (Billboard CENTER, Hintergrund `0x40000000` = Vanilla-Nametag-Optik, B), Zeilenabstand 0,28 Blöcke, Aktualisierung dynamischer Zeilen alle 5 s aus `lobby:npcstats:{mode}`. Stats-Hologramme/Wände: Spec 02 §4.9.

### 4.8 NPCs (Lobby)
| Typ | Erscheinung | Rechtsklick | Linksklick | Beleg |
|---|---|---|---|---|
| `NPC_JOIN` (pro Modus/Variante) | Spieler-NPC mit Skin pro Modus, Blick folgt Spieler im Radius 8 | Ticket in `queue:{variant}` → nächste startende Runde (§6.4) | GUI 5.4 „Kommende Runden“ | B (N05 News #155) |
| `NPC_DAILY` | Dorfbewohner (Profession `LIBRARIAN`, AN) | GUI 5.7 „Tägliche Belohnung“ | = Rechtsklick | B (N05: Villager) |
| `NPC_PRIVATE` | Spieler-NPC | GUI 5.5 | = Rechtsklick | B (F) |
| `NPC_REPLAY` | Spieler-NPC | GUI 5.9 „Replays“ | = Rechtsklick | Existenz B, Menü AN |
| `NPC_COMMUNITY` | Spieler-NPC am Zelt/Portal | `/gameteleport community` | – | B (V) |
| `NPC_DIALOG` (versteckt) | beliebig | nächste Dialogzeile `§e{npc}§7: §f{line}` (Zeilen aus `anchor.data.lines`, Index pro Spieler rotiert) | – | B (N05: „versteckte NPCs mit Dialogen“), Format AN |

### 4.9 Lobby-Hotbar (Ära 26; Items gesperrt, kein Drop/Verschieben, Rechtsklick und Linksklick in Luft/Block)
| Slot | Item | Name | Aktion | Beleg |
|---|---|---|---|---|
| 1 | `compass` | `§cNavigator §7(Rechtsklick)` | GUI 5.1 | B (F Icon; Name V; Farbe s. §10 #4) |
| 2 | `lime_dye` / `purple_dye` / `gray_dye` je Zustand | `§aAlle Spieler sichtbar §7(Rechtsklick)` / `§5Nur VIPs sichtbar §7(Rechtsklick)` / `§7Keine Spieler sichtbar §7(Rechtsklick)` | Rechtsklick: Zustand weiterschalten; Linksklick: GUI 5.3 | B (F grünes Item Slot 2; Texte V/OCR „Alle Spieler sichtbar“, N17 2017 „Keine Spieler sichtbar (Rechtsklick)“), Farbstoffe AN |
| 3 | aktives Gadget (z. B. `lantern`) oder leer | Name aus Cosmetics-Spec | Gadget auslösen | B (F Slot 3 belegt), Funktion A |
| 4 | leer | – | – | B |
| 5 | `chest` | `§6Cosmetics §7(Rechtsklick)` | Cosmetic-Menü (Cosmetics-Spec, `CosmeticsApi.openMenu`) | B (F Icon), Funktion A (N05 Cosmetic-Menü 18.10.2026) |
| 6 | leer | – | – | B |
| 7 | leer (Ära 20+: unbekanntes Papier-/Schlüssel-Item, nicht nachgebaut) | – | – | B (leer in F) |
| 8 | `nether_star` | `§bLobby wechseln §7(Rechtsklick)` | GUI 5.2 | B (N05 Netherstern = Lobbywechsel; F Icon), Name AN |
| 9 | `player_head` (eigener Skin) | `§eProfil §7(Rechtsklick)` | Profil-GUI (Spec 01 `SocialGuiApi.open(PROFILE)`); Linksklick auf Spieler: Freundschaftsanfrage (Option `lobby.headFriendRequest`, Standard aus; B N05 ältere Mechanik) | B |

Ausgewählter Slot nach Join: 1 (AN). Silent-Lobby: Slot 2 ausgegraut `§7Spieler verstecken §8(Silent-Lobby)` ohne Funktion (AN).

### 4.10 Wartelobby-Hotbar (einheitlich in allen Modi, B N05 News #17: „einheitlich sortiert“)
| Slot | Item | Name | Aktion | Beleg |
|---|---|---|---|---|
| 1 | Teammodi: Modus-Icon (BedWars `red_bed`); sonst modusspezifisch (SkyWars `chest` = Kits, TTT Pässe, EG Kits) | `§fWähle dein Team §7(Rechtsklick)` bzw. Modus-Name | GUI 5.10 bzw. Modus-GUI | B (V Bett/Truhe Slot 1, Actionbar) |
| 2 | `paper` | `§eMap-Abstimmung §7(Rechtsklick)` | GUI 5.11 (Voting/Forcemap) | Item B (V weißes Item/Papier Slot 2), Funktion AN |
| 3–6 | modusspezifisch (z. B. BedWars rote Tasche = Shop-Einstellungen, N02) | – | Modul | B (N02) |
| 7 | `nether_star` | `§bErfolge & Level §7(Rechtsklick)` | Modus-Statistik-GUI (Spec 02 `StatsProfileGui`, Tab Modus) | Item B (V), Funktion AN |
| 8 | `written_book` (Einband §-Farbe Modus) | `§6Anleitung §7(Rechtsklick)` | öffnet Buch der Modus-Anleitung (i18n `guide.<mode>.page.N`) | B (N05 News #17 „Anleitungsbuch pro Spielmodus“; V rotes/lila Buch) |
| 9 | `diamond` | `§bCosmetics §7(Rechtsklick)` | Cosmetic-Auswahl für die Runde (Cosmetics-Spec) | B (N05 News #17 „Diamant-Item“; V Slot 9) |

Kein „Verlassen“-Item in der Wartelobby (B V: Slot 9 Diamant) – Verlassen per `/l`.

### 4.11 Zuschauer-Hotbar (Spiel-Rahmen)
| Slot | Item | Name | Aktion | Beleg |
|---|---|---|---|---|
| 1 | `compass` | `§fTeleporter §7(Rechtsklick)` | GUI 5.12 | B (V „Teleporter (Rechtsklick)“, Kompass Slot 1) |
| 7 | `nether_star` | `§bEinstellungen §7(Rechtsklick)` | GUI 5.13 | B (V TTT Slot 7), Funktion A (Spec 05) |
| 8 | `lime_dye` | `§aNächste Runde §7(Rechtsklick)` | Ticket gleiche Variante (§6.4) | B (V „Nächste Runde (Rechtsklick)“ §a) |
| 9 | `white_dye` | `§cZurück zur Lobby §7(Rechtsklick)` | wie `/l` | Item B (V weißes Item Slot 9), Funktion AN |

---

## 5. GUIs

Konventionen: Titel in Vanilla-Farbe (dunkelgrau), Füll-Glas ohne Tooltip (Name ` `), Klick-Sound `ui.button.click`, Aktionen nur bei Linksklick/Rechtsklick ohne Shift (AN). Slot-Index 0-basiert (Zeile × 9 + Spalte).

### 5.1 Navigator „Wähle ein Ziel“ (6 Reihen, B N09 G6 + V 2024/25)
Füllung: Slots ohne Eintrag leer (keine Glasscheiben, B G6/V „keine Glasrahmen“). Name `{farbe}§l{Modus} §7({Versionen})`, Lore 2–3 Zeilen `§7` mit `§e`-Hervorhebung, von Hand auf ≤ 40 Zeichen umbrochen (B), Lore-Texte **eigen**. Letzte Lore-Zeile (AN): Leerzeile + `§7{n} Spieler online` (aus `lobby:npcstats`).

| Slot | Item | Name (Ära 20+, belegt) | Lore (eigen, gleiche Struktur) | Klick |
|---|---|---|---|---|
| 1 | `diamond` | `§b§lPrivate Runden` | `§7Spiele §everschiedene Modi §7in Runden,` / `§7die §eSpieler §7selbst erstellt haben` | GUI 5.5 |
| 3 | `player_head` (Community-Logo-Textur) | `§6§lCommunity §7({ver})` | `§7Meistere §eJump 'n' Runs§7, triff` / `§7Freunde und spiel §eCommunity-Spiele` | `/gameteleport community` |
| 5 | `magma_cream` | `§a§lSpawn` | `§7Zurück zum Spawn` | Teleport `SPAWN` |
| 7 | `sunflower` | `§6§lCoinSystem` | `§7Hol dir deine §eBelohnung §7und öffne §eItem-` / `§7und §eCoin-Truhen` | Teleport `WARP coinsystem` |
| 21 | `iron_pickaxe` | `§b§lMasterBuilders §7({ver})` | `§7Bau in §ebegrenzter Zeit §7ein Werk` / `§7zu einem vorgegebenen §eThema` | `/gameteleport mb` |
| 22 | `crafting_table` | `§e§lCityBuild §7({ver})` | `§7Bau dir ein §eHandelsimperium §7auf,` / `§7tritt einer §eGilde §7bei und lass beim` / `§eBauen §7deiner Fantasie freien Lauf` | `/gameteleport citybuild` |
| 23 | `diamond_boots` | `§c§lJumpLeague §7({ver})` | `§7Spring so schnell wie möglich durchs` / `§eJump 'n' Run §7und miss dich danach im` / `§eKampf §7mit deinen Gegnern` | `/gameteleport jl` |
| 28 | `grass_block` | `§e§lSkyWars §7({ver})` | `§7Such dir ein §eKit §7aus, sammle §eItems` / `§7und besiege deine Gegner auf` / `§eschwebenden Inseln` | `/gameteleport classicsw` |
| 29 | `cake` | `§d§lPartyGames §7({ver})` | `§7Tritt in vielen §eMinispielen §7gegen` / `§7andere an und kämpf dich bis an` / `§7die §eSpitze` | `/gameteleport pg` |
| 30 | `wooden_axe` | `§b§lGunGame §7({ver})` | `§7Kämpf in diesem §eendlosen Modus` / `§7gegen andere und verbessere deine` / `§eAusrüstung §7mit jedem Kill` | `/gameteleport ffagungame` |
| 31 | `gold_ingot` | `§6§lMoneyMaker §7({ver})` | `§7Bau dir ein §eMinenimperium §7auf, stell` / `§eArbeiter §7ein, werte sie auf und` / `§7werde der §ereichste Spieler` | `/gameteleport idlemine` |
| 32 | `red_bed` | `§c§lBedWars §7({ver})` | `§7Kauf §eItems§7, zerstör gegnerische` / `§eBetten §7und verteidige dein eigenes` | `/gameteleport classicbw` |
| 33 | `stick` | `§4§lTTT §7({ver})` | `§7In diesem §etaktischen Teamspiel §7kannst` / `§7du kaum jemandem trauen. §eEntlarve` / `§eden Verräter§7, bevor er dich erwischt` | `/gameteleport ttt` |
| 34 | `golden_helmet` | `§9§lGame1vs1 §7({ver})` (nur Ära 20+) | `§7Spiel einen von §e6 Modi §7im §e1vs1` / `§7und klettere im §eRanking §7nach oben` | – (Ära 26 ersetzt, s. u.) |
| 37 | `filled_map` | `§e§lBingo §7({ver})` | `§7Finde die §egesuchten Items §7deiner` / `§eBingokarte §7und schließ als Erster` / `§7eine Reihe aus §e5 Feldern` | `/gameteleport bingo` |
| 38 | `golden_sword` | `§9§lDuels §7({ver})` | `§7Kämpf in §eDuellen §7mit §ewechselnden` / `§eKits §7oder starte §eTurniere §7und` / `§7steig im §eRanking §7auf` | `/gameteleport duelsl` |
| 39 | `flower_pot` | `§c§lHide'n'Seek §7({ver})` | `§7Versteck dich als §eHider§7, ärgere die` / `§7anderen mit §eSticheleien §7und lass dich` / `§7nicht von den §eSeekern §7finden` | `/gameteleport hns` |
| 41 | `diamond_sword` | `§9§lKit1vs1 §7({ver})` (nur Ära 20+) | `§7Spiel §ewechselnde Kits §7im §e1vs1` / `§7und kämpf dich im §eRanking §7hoch` | – |
| 42 | `sandstone` | `§a§lBuildFFA §7({ver})` | `§7Kämpf im §eendlosen Modus §7mit` / `§eBedWars-Ausrüstung §7und hol dir` / `§7Vorteile durch §eKillstreaks` | `/gameteleport ffabuild` |
| 43 | `diamond_chestplate` | `§d§lFFA Hardcore §7({ver})` (nur Ära 20+) | `§7Kämpf im §eendlosen Modus §7mit` / `§eDiamant-Ausrüstung §7gegen andere` | – |

**Ära 26 (Standard, A aus `/gameteleport`-Keys 2026 + Abschaltungen 21.07.2026):** Slots 34/41/43 werden ersetzt: 34 `chest` `§6§lItemChaos` (Key `ic`), 41 `lava_bucket` `§c§lLavaEscape` (Key `le`), 43 `campfire` `§2§lSMP` (Key `smp`, AN). Item/Farbe der drei Ersatzslots AN. Versionen `{ver}`: Ära 26 `1.21.11+` für alle, `1.8+` für GunGame/BuildFFA (B N17); Ära 20+ wie G6 (`1.8+, 1.20+` usw.). Einträge sind Konfiguration (`navigator.yml`: slot, key, item, color, name, loreKey, versions, action), sodass Modus-Wechsel ohne Code gehen.
Ära 18 (Option): zusätzlicher Eintrag `§7Zu den Lobbyschildern` (B OCR) → Teleport `WARP signs`.

### 5.2 Lobbywechsler „Lobby wählen“ (AN Layout; Inhalt Silent-Lobby B N05 03.03.2026)
4 Reihen. Reihe 0 und 3: `light_blue_stained_glass_pane`. Slots 10–16, 19–25: Lobbys aus `ServerRegistry.byCategory(LOBBY)` (Spec 08 §5.4) sortiert nach Nummer im Namen (max. 14, danach Seiten über Slots 27/35 `arrow`).
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| Lobby-Eintrag | `quartz_block` (aktuelle Lobby: `emerald_block` mit Glanz; voll: `redstone_block`) | `§aLobby #{n}` / `§7Spieler: §e{online}§7/§e{max}` / leer / `§a<Klicke zum Betreten>` bzw. `§7Du bist hier` bzw. `§cVoll` | Connect über Bridge (`RunProxyCommand server lobby-{n}`) |
| 30 | `gold_block` | `§6Premium-Lobby` / `§7Ab §6Premium` (B N05 Lobbylounge-Idee, AN) | Connect, sonst `lobby.switch.rank` |
| 32 | `ender_eye` | `§cSilent-Lobby` / `§7Keine Spieler, kein Chat.` / `§7Ab §bSupremium` | Connect, sonst `lobby.silent.noperm` |

### 5.3 Spieler-Sichtbarkeit „Spieler verstecken“ (3 Reihen, AN Layout; Optionen B N05)
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 11 | `lime_dye` | `§aAlle Spieler sichtbar` (+ `§a✔ Aktiv`) | Zustand ALL |
| 13 | `purple_dye` | `§5Nur VIPs sichtbar` / `§7Sichtbar bleiben die Gruppen unten` | Zustand VIP |
| 15 | `gray_dye` | `§7Keine Spieler sichtbar` | Zustand NONE |
| 18–23 | `gold_ingot` Premium · `diamond` Supremium · `ender_eye` VIP · `player_head` Freunde · `redstone` Team · `name_tag` Clan | `{farbe}{Gruppe}` / `§a✔ sichtbar` bzw. `§c✘ versteckt` | Bit in `lobby.hide.vipGroups` umschalten |
| 25 | `oak_sign` | `§eChat mitverstecken` / `§7Versteckte Spieler sind auch im Chat` / `§7nicht zu sehen. §6Ab Premium` / `§a✔ an` bzw. `§c✘ aus` | `lobby.hide.chatSync` (nur `premium`+, B N05 27.03.2026 „unverified“) |

### 5.4 NPC-Linksklick „Kommende Runden – {Modus}“ (6 Reihen, AN Layout; Funktion B N05 News #155)
Reihe 0 `{Modusfarbe}_stained_glass_pane`; Slots 9–44: Server aus `ServerRegistry.byMode(mode, *)`, sortiert: STARTING (Countdown aufsteigend) → WAITING (Spieler absteigend) → INGAME.
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| Runde | Map-Icon (`infra.map.icon`); Countdown läuft: Glanz | `{modeColor}{map} §7({variant})` / `§7Status: §aWartet` · `§6Startet in {mm:ss}` · `§cLäuft` / `§7Spieler: §e{online}/{max}` / `§7Version: §e{versionLabel}` / leer / `§a<Klicke zum Betreten>` bzw. `§e<Klicke zum Zuschauen>` | joinbar → Connect; INGAME + spectatable → Connect als Zuschauer; sonst `npc.join.full` |
| 47 | `hopper` | `§eVariante: §f{alle\|8x1\|…}` | Filter durchschalten |
| 49 | `clock` | `§eNur joinbare Runden: §a✔`/`§c✘` | Filter |
| 51 | `comparator` | `§eSortierung: §fStart` / `§fSpieler` | Umschalten |
| 53 | `lime_dye` | `§aNächste Runde beitreten` | = NPC-Rechtsklick |
Live-Update alle 1 s solange offen.

### 5.5 „Private Runden“ (6 Reihen, B F-Bild)
| Slot | Item | Name / Lore | Klick | Beleg |
|---|---|---|---|---|
| 0–8 | `light_blue_stained_glass_pane` | ` ` | – | B |
| 9–35 | Runde: Navigator-Icon des Modus; Modus „Vanilla“ `skull_banner_pattern` | `{modeColor}{Modus} §7({versionLabel})` / `§7Besitzer: §b{owner}` / leer / `§7Map: §e{map}` / `§7Spieler: §a{online}/{max}` / leer / `§a<Klicke zum Betreten>` | Connect (voll: `private.err.full`) | B (Tooltip wörtlich F: „Vanilla (1.21.11+)“, „Besitzer“, „Map: Void“, „Spieler: 3/250“, „<Klicke zum Betreten>“) |
| 36–44 | `light_blue_stained_glass_pane` | ` ` | – | B |
| 49 | `lime_glazed_terracotta` (AN, Bild: grüner Block) | `§aPrivate Runde erstellen` / `§7Ab §bSupremium` | GUI 5.6 (oder `private.err.rank`) | Item B, Funktion AN |
| 52 / 53 | `cyan_glazed_terracotta` (AN) | `§eVorherige Seite` / `§eNächste Seite` | Blättern (27 je Seite) | Item B, Funktion AN |
Sortierung: eigene Runde zuerst, dann Spieler absteigend. Update alle 2 s aus `private:*`.

### 5.6 „Private Runde erstellen“ (Schritt 1 Modus, Schritt 2 Map; AN)
Schritt 1 (5 Reihen): alle öffentlichen Modi aus `navigator.yml` + abgeschaltete Modi (B N03: Cookies, Cores, EnderGames, FFA Classic, RageMode, SurvivalGames, QuickSG, SpeedUHC) + `Vanilla` (B F), je Name `{color}{Modus}`, Lore `§7Max. Spieler: §e{privateMax}` / `§a<Klicke zum Auswählen>`. Slot 40 `barrier` `§cZurück`.
Schritt 2 (dynamisch): Maps des Modus (`infra.map` enabled) + `§eZufällige Map` (Slot 4); Klick → `PRIVATE_CREATE`; Meldung `private.create.start`; bei `PRIVATE_READY` automatisch Connect.

### 5.7 „Tägliche Belohnung“ (3 Reihen, AN Layout; Inhalte B N05)
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 10 | `sunflower` (abgeholt: `gray_dye`) | `§aTägliche Belohnung` / `§7Heute: §e{coins} Coins§7, §e{tickets} §7Lose` / `§7Serie: §e{streak} Tage §7(§a+{pct} %§7)` / leer / `§a<Klicke zum Abholen>` bzw. `§cWieder verfügbar in {hh}h {mm}m` | Claim (§6.7) |
| 12 | `paper` | `§6Lotterie` / `§7Deine Lose: §e{tickets}` / `§7Ø Gewinn pro Los: §e1.000 Coins` / leer / `§a<Klicke zum Einlösen>` | GUI 5.8 |
| 14 | `tripwire_hook` | `§bVote-Keys eintauschen` / `§7Deine Keys: §e{keys}` | Unterseite 3 Reihen: Slot 11 `gold_nugget` `§e1 Key §7→ §e1.000 Coins`, 13 `chest` `§e7 Keys §7→ §e1 Truhe`, 15 `gold_ingot` `§e20 Keys §7→ §64 Tage Premium` (B N05) |
| 16 | `fire_charge` (nur `supremium`+) | `§bSupremium-Extras` / `§7Gratis-Truhe: §a✔`/`§c✘` / `§7Coinbombe: §a✔`/`§c✘` | Auszahlung der täglichen Extras (B N05 Shop) |

### 5.8 „Lotterie“ (3 Reihen, AN)
Slot 13 `paper` `§6Los ziehen` (`§7Lose: §e{n}`); Klick: 2-s-Animation (Slots 9–17 wechseln Items je 2 Ticks), Ergebnis Slot 13 `§e+{prize} Coins`; Slot 22 `§6Alle Lose ziehen` (Shift-Klick-Ersatz, max. 64 auf einmal).

### 5.9 „Replays“ (3 Reihen, AN)
Slots 10–16: letzte 7 Replays aus `replay:last:{uuid}`: `filled_map` `{modeColor}{Modus} §7– §e{map}` / `§7Datum: §e{dd.MM.yyyy HH:mm}` / `§7ID: §e{id}` / leer / `§a<Klicke zum Ansehen>` → `/replay {id}`. Slot 22 `name_tag` `§eReplay-ID eingeben` → Chat-Eingabe 30 s.

### 5.10 Team-Wahl „Wähle dein Team“ (Wartelobby; Titel = Item-Name, AN; 3–6 Reihen)
Reihen = 2 + ceil(teams/7), max. 6. Positionen: 2 Teams → 11, 15 · 3 → 11, 13, 15 · 4 → 10, 12, 14, 16 · 5–7 → ab 10 zentriert · 8 → 9, 10, 11, 12, 14, 15, 16, 17 · > 8 → Reihen à 7 ab 10, 19, 28, 37.
| Element | Item | Name / Lore | Klick |
|---|---|---|---|
| Team | `{color}_wool` (SkyWars nummeriert: `white_wool` mit Menge = Nummer) | `{teamColor}Team {teamName} §7({n}/{size})` / Mitglieder `§7- {nameColor}{name}`, freie Plätze `§8- frei` / leer / `§a<Klicke zum Beitreten>` · `§7Du bist in diesem Team` · `§cDieses Team ist voll` | Beitreten (§6.5) |
| letzte Reihe Mitte | `barrier` | `§cTeam verlassen` / `§7Du wirst beim Start zugeteilt` | Team verlassen |

### 5.11 „Map-Abstimmung“ (Wartelobby, Hotbar Slot 2; AN Layout)
3 Reihen. Modi mit `voting=true`: bis 5 Maps (Zufallsauswahl beim Serverstart, Slots 11–15), `{icon}` `§e{map}` / `§7Stimmen: §e{votes}` / `§7Von: §e{builders}` / leer / `§a<Klicke zum Abstimmen>`. Modi ohne Voting (Standard Ära 26): Slot 13 aktuelle Map `§e{map}` (`§7Aktuelle Map`), darunter (nur `supremium`+) Slot 22 `ender_pearl` `§bMap festlegen` / `§7Noch §e{left}§7/§e{max} §7heute` → Unterseite: alle Maps der Variante (Klick = `/forcemap`).

### 5.12 Zuschauer „Wähle einen Spieler“ (B V TTT/SG)
Reihen = ceil(lebende/9), max. 6 (danach Seiten). Slots ab 0: `player_head` der lebenden Spieler, Name `{teamOrRankColor}{name}`, Lore `§7Leben: §c{hearts}` / `§a<Klicke zum Teleportieren>` (Lore AN). Klick: Teleport + `spectate.tp`.

### 5.13 Zuschauer-„Einstellungen“ (3 Reihen, AN)
Slot 10–13 `leather_boots` Geschwindigkeit `§eKein Speed`/`§eSpeed I`/`II`/`III`; Slot 15 `ender_eye` `§eNachtsicht: §a✔/§c✘`; Slot 16 `glass` `§eAndere Zuschauer anzeigen: §a✔/§c✘`.

### 5.14 „Lobby-Einstellungen“ (aus Profil-Menü Spec 01, 3 Reihen, AN Layout; Optionen B N05)
Slot 11 `feather` `§eFortbewegung: §f{Laufen|Doppelsprung|Fliegen}` (nur erlaubte Stufen schaltbar); Slot 13 `clock` `§eTageszeit: §f{Tag|Abend|Nacht|Echtzeit}`; Slot 15 `lime_dye` `§eSpieler verstecken` → GUI 5.3; Slot 22 `barrier` `§cZurück`.

---

## 6. Mechaniken & Zahlen

### 6.1 Lobby-Aufbau (Map-Anforderungen, Inhalte eigen)
| Bereich | Anker / Regel | Beleg |
|---|---|---|
| Spawn | `SPAWN` (Join-Punkt, `/spawn`, Void-Rückholung bei y < `world.minY + 10`) | B (Kompass „Spawn“) |
| GameZone / Modus-Ring | je öffentlichem Modus ein `NPC_JOIN` mit großem Block-Schriftzug des Modus (Map-Inhalt) | B (V 2026 „GAMEZONE“, Modus-Logos) |
| CoinSystem-Bereich | `WARP coinsystem`, `NPC_DAILY`, Item-Truhe, Coin-Truhe | B (V 2024, N09 Slot 7) |
| Private Runden | `NPC_PRIVATE` mit Groß-Hologramm | B (F) |
| Replays | `NPC_REPLAY` | B (N17) |
| Community | `NPC_COMMUNITY` | B |
| Jump & Run | Start-/Checkpoint-/Ziel-Druckplatten (`lobby.yml`) | B (N05 Existenz) |
| versteckte NPCs | ≥ 3 `NPC_DIALOG` | B (Existenz) |
| Lobby-Lounge (optional) | Bereich nur `premium`+ (Barriere per WorldGuard-freiem Regionstest) | B N05 2019, Ära 18/20+ Option |
| Saison-Deko | Welt-Austausch per `lobby.world` (z. B. `lobby_winter`) | B N05 |
| Grenzen | Max. Spieler je Lobby **100** (AN), Silent-Lobby **50** (AN); Welt: Wetter aus, Mobs aus, kein Schaden, kein Hunger, Blockänderungen gesperrt | AN |

### 6.2 Join in die Lobby
1. Spieler betritt Lobby → Gamemode ADVENTURE, Gesundheit/Hunger voll, Inventar leeren, Hotbar §4.9, Slot 1 gewählt.
2. Teleport `SPAWN`; Spielzeit-Welt nach `lobby.time` (`DAY`=6000, `SUNSET`=12500, `NIGHT`=18000, `REAL`=Europe/Berlin-Uhrzeit → Ticks; AN), pro Spieler per `Player#setPlayerTime`.
3. Board §4.1, Tab, Newsticker starten. Supremium-Join-Partikel `TOTEM_OF_UNDYING` 1,5 s (B N05 „Partikel beim Lobby-Spawn“, Partikelart AN), `supremiumplus` zusätzlich `FIREWORK` (AN).
4. Sichtbarkeit anwenden (§6.3) für neuen Spieler und für alle bestehenden Betrachter.
5. Silent-Lobby: Meldung `lobby.silent.welcome`; alle anderen Spieler unsichtbar, kein Chat (Senden: `lobby.silent.nochat`), kein Global-Chat-Empfang (Spec 01).
6. Kam der Spieler aus einer Runde, entfällt jede Begrüßung (AN).

### 6.3 Spieler verstecken
| Zustand | sichtbar | Beleg |
|---|---|---|
| `ALL` (Standard) | alle | B |
| `VIP` | Spieler, deren Gruppe in `vipGroups` gesetzt ist (Standard: Premium, Supremium, VIP, Freunde, Team = 31) + Party-Mitglieder immer | B (N05 Gruppenauswahl), Default AN |
| `NONE` | niemand außer Party-Mitglieder | B (Item), Party-Ausnahme AN |
- Rechtsklick: ALL → VIP → NONE → ALL; Cooldown **3 s** (`cd:lobby:hide`), Meldung `lobby.hide.{state}`, Sound `entity.chicken.egg` (AN).
- Chat-Sync (`premium`+): Nachrichten unsichtbarer Spieler werden über `ChatAudienceFilter` (Spec 02 §6) ausgeblendet; Team-Mitglieder nie (AN).
- Umsetzung: `Player#hidePlayer(plugin, other)`; Zustand bei Lobbywechsel aus Settings übernommen.

### 6.4 NPC-Join, „Nächste Runde“, Schilder
1. Rechtsklick NPC (Cooldown 1 s): offenes Ticket? → `npc.join.already`. Sonst `QueueClient.join(players, mode, variant, partyId)` (paper-core, Spec 08 §8.1) → `QUEUE_JOIN{ticket, players=[Spieler oder ganze Party, wenn Leiter], mode, variant=npc.key, source=NPC}`.
2. Party: Nur der Leiter kann für die Party ziehen; Mitglied → `npc.join.partyleader` (AN). Party größer als freie Plätze aller Server → Controller startet neuen Server.
3. Controller wählt Server: Phase WAITING/STARTING, `joinable=1`, `online+n ≤ max`, Priorität: STARTING mit kleinstem Countdown > WAITING mit meisten Spielern > neuer Server (B N05 „tritt der nächsten Runde bei, sobald sie startet“, Reihenfolge A).
4. `MATCH_ASSIGNED` → Proxy verschiebt; Meldung vorher `npc.join.searching` (Actionbar `§7Suche Runde …`, AN). Timeout 60 s → `npc.join.timeout`, Ticket gelöscht.
5. Volle Server: `supremium`+ dürfen volle Wartelobbys betreten (Spec 01 Flag `fullServer`) – dann wird der zuletzt gejointe Spieler ohne Rang in eine andere Runde verschoben (AN, mit `common.kicked.priority`).
6. Zuschauer „Nächste Runde“: identisch mit `source=SPECTATOR_NEXT`; Mehrfachklick → `common.spectator.next.already` (B §c).
7. **Schilder (Ära 18/20+):** `SIGN_JOIN` mit Variante; Zeilen alle 1 s aus `ServerRegistry.byMode`:
   Z1 `§l{MODE_SHORT} {variant}` (z. B. `BW 8x1`), Z2 Status, Z3 `§0{map}`, Z4 `§0{online}/{max}` (Struktur B V „BW 12x1 / Lobby / Lotus 12x1 / 0/32“, Status-Wörter AN außer `[Joinbar]` laut Auftrag).
   Status: WAITING `§a[Joinbar]` · STARTING `§6[Startet]` · INGAME `§c[Läuft]` · voll `§4[Voll]` · kein Server `§8[Suche …]` → Klick = Ticket wie NPC. Schild wählt den Server mit Map-Pool-Filter (B N05 2017: konfigurierbare Map-Pools, Teamgrößen-Filter): `anchor.data.mapPool`.

### 6.5 Spiel-Rahmen: Phasen & Countdown (`GameLifecycle` WAITING → STARTING → INGAME → ENDING)
| Parameter | Standard | Beleg |
|---|---|---|
| `maxPlayers` | `teams × teamSize` aus Variante | B (8x4=32, 8x1=8, 16/16, 12/12) |
| `minPlayers` | `ceil(maxPlayers / 2)` („halb voll“), Modus-Override erlaubt (Spec 05/06), Privatrunde 1 (Besitzer kann `/start`) bzw. 2 | B (N02 BedWars-Changelog), Rest AN |
| Lobby-Countdown | **60 s** | AN (B: Werte 40/30/10/5…1 gesehen; „68“ OCR = 60) |
| Ansagen (Broadcast) | 60, 50, 40, 30, 20, 10, 5, 4, 3, 2, 1 | B (Werte), Menge A |
| Ansage beim Join | dem neuen Spieler einmal der aktuelle Rest (`common.countdown`) | A (OCR-Werte 46/39/38/18 außerhalb des Rasters) |
| Voll | Rest > 10 → 10 s | AN (wie Spec 05) |
| `/start` | Rest > 10 → 10 s | AN |
| Abbruch | `online < minPlayers` in STARTING → WAITING, Countdown zurück auf 60, `common.countdown.cancel` | AN |
| Schutz | Ab Rest ≤ 5 s kein Abbruch mehr, Teamwahl/Forcemap gesperrt ab ≤ 10 s | AN |
| Countdown-Einheit | „1 Sekunde“ Singular | B |

**Ablauf WAITING → INGAME:**
1. Join: Wartelobby-Spawn (`map.json waitingSpawn`; BedWars: Miniatur der Map, Map-Inhalt), ADVENTURE, Hotbar §4.10, Board §4.2, Actionbar.
2. An alle: `common.join`. An den Neuen in dieser Reihenfolge: ggf. `common.teaming.warn` (Modi mit `teams=forbidden`), Nick-Info (Spec 01), `common.online`, `common.map`, ein Tipp (`common.tip`) (B Reihenfolge V SkyWars/BedWars).
3. `online ≥ minPlayers` → STARTING, Countdown läuft (XP-Balken §4.6).
4. Tipps: alle **30 s** ein zufälliger Tipp des Modus ohne Wiederholung bis Liste durch (`tips.<mode>.N`, ≥ 5 je Modus); nicht in den letzten 10 s (Intervall AN, Existenz B).
5. Bei 0: Teams auffüllen (§6.6), Map-Welt fertig (Laden ab STARTING asynchron; nicht fertig → Countdown hält bei 1 s, max. 15 s, dann Abbruch → Server `ENDING` + alle in die Lobby, `common.map.loadfail`, AN).
6. Teleport auf Team-/Spieler-Spawns, Inventar leeren, Modus-Start (`GameStartEvent`), INGAME. Server-State `joinable=0`, `spectatable` laut Modus.
7. Verlassen in WAITING/STARTING: `common.quit` an alle, Platz frei. In INGAME: Modus entscheidet (Tod/Disconnect-Regel), Quit-Meldung `common.quit` bleibt.
- Chat in der Wartelobby: alle sehen alle; farbiges `GL` für `supremium`+ (B N05: „gl“/„gege“), Format `§7§k--§{farbe} GL §7§k--` wie farbiges GG (Spec 02).

### 6.6 Teams & Farben
| Team-ID | Name | Chat/Tab | Wolle | Reihenfolge |
|---|---|---|---|---|
| `red` | Rot | `§c` | `red_wool` | 1 |
| `blue` | Blau | `§9` | `blue_wool` | 2 |
| `green` | Grün | `§a` | `lime_wool` | 3 |
| `yellow` | Gelb | `§e` | `yellow_wool` | 4 |
| `aqua` | Hellblau | `§b` | `light_blue_wool` | 5 |
| `purple` | Violett | `§5` | `purple_wool` | 6 |
| `pink` | Rosa | `§d` | `pink_wool` | 7 |
| `orange` | Orange | `§6` | `orange_wool` | 8 |
| `white`/`gray` | Weiß / Grau | `§f` / `§7` | – | 9/10 (Reserve, AN) |
| `#n` | Team #n (SkyWars-Stil, `numberedTeams=true`) | `§f` | `white_wool` ×n | – |
Belege: Namen/Farben Rot…Rosa B (V BedWars-Scoreboard/Nametags), Orange B (OCR 1.8 „Team Orange“), Reihenfolge A, `Team #4` B (SkyWars).
- **Varianten:** `{teams}x{teamSize}`; belegt: BedWars 8x1, 8x2 (A), 8x4, 4x4 (A), 12x1/4x3 (Schilder); SkyWars Solo/2/3/4; SG 8x2, 16x1 (N02). Pro Modus in `mode.yml`.
- **Teamwahl:** nur WAITING/STARTING bis Rest > 10 s; Team voll → `common.team.full`; Teamwechsel frei; `teamSize=1` → keine Teamwahl (Slot 1 = Modus-Item).
- **Zuteilung beim Start (`TeamAllocator`, rein deterministisch, testbar):**
  1. Gewählte Teams bleiben.
  2. Party-Gruppen ohne Team (größte zuerst) in das Team mit den meisten freien Plätzen, das die ganze Gruppe fasst; passt keine → aufteilen auf die freiesten Teams + `common.team.party_split` an die Party (B Original-Meldung N17).
  3. Einzelspieler ins Team mit den wenigsten Spielern (Gleichstand: Reihenfolge-Nr.).
  4. Ziel: Differenz ≤ 1 zwischen nicht leeren Teams; leere Teams erlaubt (Runde startet mit weniger Teams).
- Anzeigen: Actionbar `{teamColor}Team {name}`, Nametag `{teamColor}{name} §7| §f{player}` (B V „Rot | Emma_biss“), Tab §4.3, Rüstungsfarbe Modul.
- Teaming-Verbot (Solo-Modi): Hinweis `common.teaming.warn` beim Join (B), Melden über `/report` (Spec 01).

### 6.7 Tägliche Belohnung, Lose, Vote-Keys, Fortbewegung
| Punkt | Wert | Beleg |
|---|---|---|
| Reset | 00:00 Europe/Berlin | AN (wie `/joinme`) |
| Coins pro Tag | Spieler **1.000**, Premium/Premium+ **2.000**, Supremium **4.000**, Supremium+/VIP **8.000** | 8.000 B (N05 „höherer Rang … 8000“), Rest AN |
| Lose pro Tag | Spieler 1, Premium/Premium+ 2, Supremium 4, Supremium+/VIP 4 | B (N05) |
| Streak | +10 % Coins pro Folgetag, max. +70 % (Tag 8+); Lücke ≥ 1 Tag → Streak 1 | Existenz B, Zahlen AN |
| Supremium-Extras | 1 Gratis-Truhe + 1 Coinbombe pro Tag (Supremium+: 2 Coinbomben) | B (N05 Shop / „zwei Coinbomben“) |
| Lotterie | Ø ≈ 1.000 Coins je Los; Tabelle: 50 % 100 · 30 % 400 · 15 % 2.000 · 4 % 5.000 · 0,9 % 25.000 · 0,1 % 100.000 (EV = 995) | Ø B (N05), Tabelle AN |
| Broadcast | ab 25.000 Coins an alle Lobbys: `lottery.win.broadcast` | B (N17 2017 Muster), Schwelle AN |
| Vote-Keys | 1 Key = 1.000 Coins · 7 Keys = 1 Truhe · 20 Keys = 4 Tage Premium | B (N05) |
| Fortbewegung | Spieler: Laufen · Premium: Doppelsprung · Premium+/Supremium/Supremium+/VIP/Team: Fliegen (+ Doppelsprung wählbar) | B N05/N17 unsicher → wie Spec 01 §6.2 |
| Doppelsprung | Boden → `allowFlight=true`; Flug-Toggle → `velocity = Blickrichtung × 1,2`, `y = 0,9`; erneut erst nach Bodenkontakt; aus während Jump & Run und mit Elytra | B (N05: JnR/Elytra-Ausschluss), Werte AN |
| Fliegen | `allowFlight` + `flySpeed 0,1` (Vanilla); im JnR aus | AN |

**Claim-Ablauf:** 1. `lock:daily:{uuid}` NX. 2. Transaktion: `INSERT lobby.daily_claim_log … ON CONFLICT (uuid, day) DO NOTHING` → 0 Zeilen = `lobby.daily.already`. 3. Streak berechnen, `lobby.daily_reward` UPSERT, `lobby.lottery_ticket += n`. 4. `CoinService.credit(uuid, coins, DAILY, "daily:{uuid}:{day}")`. 5. Meldungen `lobby.daily.claimed` + `lobby.daily.streak`, Sound `entity.player.levelup`.

### 6.8 Spectator (Zuschauer)
1. Auslöser: endgültiger Tod (Modus), Join in INGAME bei `spectatable=1` (Freunde folgen, Spec 01 `FriendStatus SPECTATE`), Rundenende (§6.10).
2. Zustand: ADVENTURE + `allowFlight`/`flying`, unsichtbar für Lebende (`hidePlayer`), keine Kollision (Team-Option `collisionRule NEVER`), unverwundbar, kein Item-Aufheben/Interagieren, Herzen/Hunger sichtbar (B V: Lebensleiste sichtbar → kein Vanilla-Spectator-Modus).
3. Meldung `common.spectator` (B), Actionbar `§7Zuschauer`, Hotbar §4.11, Tab-Eintrag §4.3.
4. Zuschauer sehen sich gegenseitig halbtransparent (Option 5.13). Chat: Zuschauer schreiben nur an Zuschauer mit `§7[Zuschauer] ` Präfix (AN); Lebende sehen das nicht.
5. Map-Grenze: Verlassen der `bounds` → zurück auf `spectatorSpawn` (AN).

### 6.9 Kill-/Tod-Meldungen (Baukasten `KillFeed`)
| Baustein | Format (Key) | Wann | Beleg |
|---|---|---|---|
| Kill | `common.kill` + optional Suffix `common.kill.xp` | Spieler tötet Spieler (letzter Treffer ≤ 10 s, AN) | B |
| Tod ohne Killer | `common.death` | Fallschaden, Void, Lava | B (BedWars „ist gestorben“) |
| Eigene Kill-Info | `common.kill.self` (`+{points} Punkte`) | an den Killer, Modi mit Punkten | B |
| Killer-Leben | `common.killer_health` mit Herzzeile | an das Opfer, Modi mit `showKillerHealth` (SW, SG) | B |
| Kit des Killers | `common.kit_reveal` | an das Opfer, Kit-Modi | B |
| Rest | `common.remaining` / `common.remaining.one` | an alle nach Tod, Solo-Modi | B |
| Team raus | `common.team.eliminated` | letztes Mitglied tot | B |
| Getötet von | `common.killed_by` | an das Opfer (TTT-Stil, statt Broadcast) | B (N17 TTT) |
**Herzzeile (`HeartLine`, testbar):** immer 10 Zeichen: `full = floor(hp / 2)` × `§c♥`, danach `10 − full` × `§f♡` (19 HP → 9 rot + 1 leer, passt zum Beleg „♥♥♥♥♥♥♥♥♥♡“ bei 9,5 Herzen; A).
Farben: Opfer/Killer in Teamfarbe (Teammodi) bzw. Rangfarbe (Solo), Rest `§7` (B). XP-Suffix nur Ära 26 (Global Level, B „(+20 XP)“).

### 6.10 Rundenende
1. Modus meldet Sieg (`GameEndEvent{matchId, placements, winnerTeam|winner}`) → Phase ENDING; Schaden/PvP aus, alle Lebenden → Zuschauer-Zustand ohne Meldung (AN).
2. Broadcast Sieg: `common.win.team` / `common.win.player` (B); Title Sieger (Spec 02 §4.8); Feuerwerk 5 s am Sieger in Teamfarbe (AN).
3. Je Spieler Rundenblock (`StatsService.roundSummary`, Spec 02 §4.7): `§7-= §eStatistiken dieser Runde §7=-`, Zeilen `§7 {label}: §e{value}` (Modus liefert Labels, z. B. Punkte, Kills, Deaths, Zerstörte Betten, Geöffnete Truhen, Platzierte Blöcke, Gewonnene Minispiele, Gesammelte Erfahrung `{n} XP`), Leerzeile, `§7 Überlebt: §a✔|§c✘`, `§7 Gewonnen: §a✔|§c✘`, optional `§7 Challenge: …`, Abschluss `§8----------------------` (B Reihenfolge V BedWars/SkyWars/PartyGames).
4. Danach `common.restart` mit 20 (B, 3 Modi), dann Coins: `CoinService.payoutRound` → `[CoinSystem] +{n} Coins` und Boost-Zeile (Spec 02 §6.11, B).
   *Widerspruch:* SkyWars zeigt Restart-Zeile vor dem Block, BedWars/PartyGames danach → **Standard: Block → Restart → Coins** (2 von 3).
5. Restart-Ansagen bei **20, 15, 10, 5, 4, 3, 2, 1** (B OCR 20/15/10/5/4/3), XP-Balken zählt mit.
6. Farbiges GG in ENDING für `supremium`+ (Spec 02).
7. Replay-Hinweis (AN, Option `replay.enabled`, Standard an): `common.replay` mit Klick `[ANSEHEN]` (Klick-Muster N17), Replay-ID 8 Zeichen Base62.
8. Bei 0: `PlayerTransfer.toLobby(uuids, "game.ended", mode)` (paper-core, Spec 08 §8.1) → `SEND_LOBBY{uuids, reasonKey, mode}` → Proxy (`LobbyBalancer`) verbindet jeden Spieler (Party geschlossen) mit einer **Modus-Lobby** des Modus (Kategorie `LOBBY`, `mode` = Modus, z. B. BedWars-Lobby; B V „BedWars-Lobby nach Runde“), sonst Hauptlobby (wenigste Spieler). Spieler mit offenem Ticket („Nächste Runde“) gehen direkt in die neue Runde.
9. Nach 3 s `ServerLifecycle.requestShutdown("MATCH_END")` (Spec 08 §6.10, `oneMatch`: eine Runde pro Server, der Controller startet Ersatz nach §6.9).
10. Stats/Coins werden vor Schritt 4 committed (`StatsService.submit` idempotent per `matchId`).
- Unentschieden (Zeitlimit ohne Sieger, Modus): `common.draw`, kein Sieger-Title (AN).
- Abbruch (alle weg in INGAME): sofort ENDING ohne Block, Restart 5 s (AN).

### 6.11 Private Runden
| Punkt | Wert | Beleg |
|---|---|---|
| Erstellen | `supremium`, `supremiumplus`, Team | B (N03/N17) |
| Beitreten | alle über GUI 5.5 | B (F) |
| Modi | alle öffentlichen + abgeschaltete (Cookies, Cores, EnderGames, FFA Classic, RageMode, SG, QSG, SpeedUHC) + Vanilla | B |
| Max. Spieler | Modus-Standard; Vanilla **250** | B (F 3/250) |
| Version | Label aus Server-Mindestprotokoll, z. B. `1.21.11+` | B (F) |
| Aktive Runden je Besitzer | 1 | AN |
| Leerlauf | ohne Spieler 5 min → CLOSED, Server stoppt | AN |
| Besitzerrechte | `/start` ohne Mindestspieler, `/forcemap` ohne Limit, `/pr kick`, `/pr max`, `/pr close` | AN |
| Stats/Coins/XP | keine (Rundenblock zeigt `§7 Private Runde – keine Wertung`) | AN |
| Ablauf | GUI 5.6 → `PRIVATE_CREATE{id, owner, mode, map}` → Controller startet Pod (Template des Modus, Env `PRIVATE_ID`) → Server meldet `PRIVATE_READY` → Besitzer verbunden; Eintrag in `privates:open` | AN (Existenz B) |

### 6.12 `/gameteleport`-Schlüssel (Registry `gameteleport.yml`, Ziel-Kategorie)
Ära 26 (B N17/LabyMod 2026): `bingo`, `classicbw` (BedWars), `citybuild`, `gommunity` → bei uns `community`, `ffagungame` (GunGame), `duelsl` (Duels-Lobby), `idlemine` (MoneyMaker), `classicsw` (SkyWars), `jl`, `ffabuild`, `hns`, `mb`, `pg`, `ttt`, `ic`, `le`; NEU eigen `smp`. Aliase (Ära 20+, B): `bw`→`classicbw`, `skywars`→`classicsw`. Entfernt (nur Private Runden): `cookies`, `game1vs1`, `1vs1`, `hardcore`, `ffaclassic`, `ragemode`, `eg`, `speeduhc`, `sg24`, `qsg`, `cores`, `training` → `gameteleport.removed` (AN).

### 6.13 Map-Anforderungen (Spiel-Rahmen, `map.json` je Map-Archiv, Format Spec 08 §6.11; modusspezifische Felder unter `markers`, geprüft gegen `mapschema/<mode>.json`)
Pflicht: `id`, `name`, `builders` (Liste), `mode`, `variants`, `waitingSpawn` (eigener Bereich oder Welt `waiting_{mode}`), `spectatorSpawn`, `spawns.{team}` bzw. `spawns.solo[]` (≥ maxPlayers), `bounds` (minX…maxZ, minY für Void-Tod), `icon`, `combat` (`MODERN`/`LEGACY_1_8`). Prüfung beim Laden (`MapValidator`): fehlende Spawns → Map deaktiviert + Log `ERROR`. Map-Name-Zeile: `{brand}-Buildteam` + Builder in Klammern (B Struktur).

---

## 7. Nachrichten-Katalog

Spalten: Key | Format | Standardtext (eigen bzw. funktional gleich) | Original-Referenz | Beleg. `{p}` = `{prefix}` des Modus.

### 7.1 Lobby
| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| `item.navigator` | `§cNavigator §7(Rechtsklick)` | Navigator (Rechtsklick) | „Navigator (Rechtsklick)“ | belegt (Farbe Widerspruch §10) |
| `item.hide.all/vip/none` | `§aAlle Spieler sichtbar §7(Rechtsklick)` / `§5Nur VIPs sichtbar …` / `§7Keine Spieler sichtbar …` | wie Format | „Alle Spieler sichtbar“, „Keine Spieler sichtbar (Rechtsklick)“ | belegt (all/none), abgeleitet (vip) |
| `item.cosmetics` / `item.switcher` / `item.profile` | `§6Cosmetics §7(Rechtsklick)` / `§bLobby wechseln §7(Rechtsklick)` / `§eProfil §7(Rechtsklick)` | wie Format | – | Annahme |
| `lobby.silent.welcome` | `{p:lobby}§eDu bist in der Silent-Lobby. Hier hast du deine Ruhe!` | – | „[Lobby] Du bist in der Silent-Lobby. Hier bist du völlig ungestört!“ | belegt (Struktur §6/§e) |
| `lobby.silent.noperm` | `{p:lobby}§cDie Silent-Lobby gibt es ab dem Rang §bSupremium§c.` | – | – | Annahme |
| `lobby.silent.nochat` | `{p:lobby}§cIn der Silent-Lobby ist der Chat aus.` | – | – | Annahme |
| `lobby.switch.connect` | `{p:lobby}§7Verbinde mit §eLobby #{n}§7 …` | – | – | Annahme |
| `lobby.switch.already` | `{p:lobby}§cDu bist schon auf dieser Lobby.` | – | – | Annahme |
| `lobby.switch.full` | `{p:lobby}§cDiese Lobby ist voll.` | – | – | Annahme |
| `lobby.switch.rank` | `{p:lobby}§cDiese Lobby ist ab §6Premium §cfreigeschaltet.` | – | – | Annahme |
| `lobby.hide.all` / `.vip` / `.none` | `{p:lobby}§7Sichtbar: §aalle Spieler` / `§5nur VIPs` / `§cniemand` | – | – | Annahme |
| `lobby.hide.cooldown` | `{p:lobby}§cWarte kurz, bevor du erneut umschaltest.` | – | – | Annahme |
| `lobby.move.set` | `{p:lobby}§7Fortbewegung: §e{mode}` | – | – | Annahme |
| `lobby.move.jnr` | `§cIm Jump & Run kannst du nicht fliegen.` (Actionbar) | – | – | Annahme |
| `lobby.daily.claimed` | `{p:coins}§aTagesbelohnung abgeholt: §e+{coins} Coins §aund §e{tickets} §aLos(e)` | – | – | Annahme |
| `lobby.daily.streak` | `{p:coins}§7Serie: §e{days} Tage §7(§a+{pct} %§7)` | – | – | Annahme |
| `lobby.daily.already` | `{p:coins}§cHeute schon abgeholt. Wieder in §e{h}h {m}m§c.` | – | – | Annahme |
| `lobby.daily.extras` | `{p:coins}§bSupremium-Extras: §e{chests} Truhe(n)§b, §e{bombs} Coinbombe(n)` | – | – | Annahme |
| `lottery.result` | `{p:coins}§7Dein Los: §e+{coins} Coins` | – | – | Annahme |
| `lottery.win.broadcast` | `{p:coins}§e{name} §7hat §e{prize} §7gewonnen!` | – | „[CoinSystem] <Name> hat <Preis> gewonnen!“ | belegt (Struktur) |
| `lottery.none` | `{p:coins}§cDu hast keine Lose.` | – | – | Annahme |
| `vote.exchange.done` | `{p:coins}§aEingetauscht: §e{keys} Keys §a→ §e{reward}` | – | – | Annahme |
| `vote.exchange.few` | `{p:coins}§cDafür brauchst du §e{need} §cVote-Keys.` | – | – | Annahme |
| `npc.join.searching` | `§7Suche eine Runde …` (Actionbar) | – | – | Annahme |
| `npc.join.already` | `{p}§cDu wirst bereits einer Runde zugeteilt.` | – | – | Annahme |
| `npc.join.partyleader` | `{p}§cNur dein Party-Leiter kann eine Runde wählen.` | – | – | Annahme |
| `npc.join.timeout` | `{p}§cGerade ist keine Runde frei. Versuch es gleich noch einmal.` | – | – | Annahme |
| `npc.join.full` | `{p}§cDiese Runde ist voll.` | – | – | Annahme |
| `npc.dialog` | `§e{npc}§7: §f{line}` | – | – | Annahme |
| `sign.status.waiting/starting/ingame/full/none` | `§a[Joinbar]` / `§6[Startet]` / `§c[Läuft]` / `§4[Voll]` / `§8[Suche …]` | – | „[Joinbar]“ (Auftrag) | Annahme/Auftrag |
| `private.err.rank` | `§7[§bPrivat§7] §cPrivate Runden erstellen kannst du ab §bSupremium§c.` | – | – | Annahme |
| `private.create.start` | `§7[§bPrivat§7] §7Deine Runde (§e{mode}§7, §e{map}§7) wird gestartet …` | – | – | Annahme |
| `private.create.ready` | `§7[§bPrivat§7] §aDeine Runde ist bereit. Verbinde …` | – | – | Annahme |
| `private.err.active` | `§7[§bPrivat§7] §cDu hast schon eine aktive Runde.` | – | – | Annahme |
| `private.err.full` | `§7[§bPrivat§7] §cDiese Runde ist voll.` | – | – | Annahme |
| `private.kick.done` / `.kicked` | `§7[§bPrivat§7] §e{name} §7wurde entfernt.` / `§cDu wurdest aus der privaten Runde entfernt.` | – | – | Annahme |
| `private.closed` | `§7[§bPrivat§7] §cDie Runde wurde vom Besitzer geschlossen.` | – | – | Annahme |
| `private.max.done` / `.err.range` | `§7[§bPrivat§7] §7Max. Spieler: §e{n}` / `§cErlaubt: §e2§c–§e{max}` | – | – | Annahme |
| `replay.connect` / `replay.err.unknown` | `§7[§eReplay§7] §7Lade Replay §e{id}§7 …` / `§cUnbekannte Replay-ID: §e{id}` | – | – | Annahme |
| `gameteleport.removed` | `§cDieser Modus ist nur noch als §bPrivate Runde §cspielbar.` | – | – | Annahme |
| `setup.saved` / `setup.removed` / `setup.err.type` | `§aAnker #{id} gespeichert.` / `§aAnker entfernt.` / `§cUnbekannter Typ: §e{type}` | – | – | Annahme |
| `news.added` / `news.removed` | `§aNews hinzugefügt (#{n}).` / `§aNews #{n} entfernt.` | – | – | Annahme |

### 7.2 Spiel-Rahmen (`common.*`, ersetzt gleichnamige Keys aus Spec 05 verbindlich)
| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| `common.join` | `§7» {nameColor}{name} §7hat die Runde betreten` | wie Original (funktional) | „» X hat die Runde betreten“ | belegt |
| `common.quit` | `§7« {nameColor}{name} §7hat die Runde verlassen` | wie Original (funktional) | „« X hat die Runde verlassen“ | belegt |
| `common.online` | `{p}§7Spieler online: §b{n}§7/§b{max}` | – | „[BedWars] Spieler online 14/32“ | belegt (Zahl §b) |
| `common.map` | `{p}§7Map: §e{map} §7von §e{brand}-Buildteam §7({builders})` | – | „Map: Dragons von GommeHD.net-Buildteam (BitConi)“ | belegt (Struktur) |
| `common.countdown` | `{p}§7Die Runde beginnt in §e{n} §7Sekunden` | wie Original (funktional) | „Die Runde beginnt in 5 Sekunden“ | belegt |
| `common.countdown.one` | `{p}§7Die Runde beginnt in §e1 §7Sekunde` | wie Original | „… in 1 Sekunde“ | belegt |
| `common.countdown.cancel` | `{p}§cZu wenige Spieler – der Countdown wurde angehalten.` | – | – | Annahme |
| `common.waiting` | `§7Warten auf Spieler §8(§e{n}§8/§e{min}§8)` (Actionbar) | – | – | Annahme |
| `common.not_in_lobby` | `{p}§cDas geht nur in der Wartelobby.` | – | – | Annahme |
| `common.noperm` | `{p}§cDafür fehlt dir die Berechtigung.` | – | – | Annahme |
| `common.start.done` | `{p}§aDer Countdown wurde verkürzt.` | – | – | Annahme |
| `common.start.broadcast` | `{p}§e{name} §7hat den Start beschleunigt.` | – | – | Annahme |
| `common.start.too_few` | `{p}§cEs sind noch zu wenige Spieler da (§e{n}§c/§e{min}§c).` | – | – | Annahme |
| `common.start.already` | `{p}§cDie Runde startet bereits.` | – | „Das Spiel startet bereits“ (rot, 2017) | belegt (Sinn) |
| `common.forcemap.done` | `{p}§e{name} §7hat die Map §e{map} §7festgelegt.` | – | – | Annahme |
| `common.forcemap.limit` | `{p}§cDu hast heute schon §e{used}§c/§e{max} §cMaps festgelegt.` | – | – | Annahme |
| `common.forcemap.unknown` | `{p}§cDiese Map gibt es hier nicht: §e{map}` | – | – | Annahme |
| `common.forcemap.too_late` | `{p}§cDie Map kann jetzt nicht mehr geändert werden.` | – | – | Annahme |
| `common.forcemap.already` | `{p}§cFür diese Runde wurde die Map schon festgelegt.` | – | – | Annahme |
| `common.forcemap.same` | `{p}§cDiese Map ist bereits ausgewählt.` | – | – | Annahme |
| `common.vote.done` | `{p}§7Deine Stimme ging an §e{map}§7.` | – | – | Annahme |
| `common.vote.result` | `{p}§7Gewählte Map: §e{map} §7(§e{votes} §7Stimmen)` | – | – | Annahme |
| `common.map.loadfail` | `{p}§cDie Map konnte nicht geladen werden. Du kommst zurück in die Lobby.` | – | – | Annahme |
| `common.team.chosen` | `{p}§7Du bist jetzt in {teamColor}Team {team}§7.` | – | – | Annahme |
| `common.team.full` | `{p}§cDieses Team ist schon voll.` | – | – | Annahme |
| `common.team.locked` | `{p}§cTeams können jetzt nicht mehr gewechselt werden.` | – | – | Annahme |
| `common.team.party_split` | `{p}§cFür deine Party war kein gemeinsames Team frei – ihr spielt §lgetrennt§c.` | – | „Es konnte kein passendes Team gefunden werden. Die Party ist NICHT im selben Team.“ | belegt (Sinn, Hervorhebung) |
| `common.teaming.warn` | `§cTeamen ist auf diesem Server §4§lVERBOTEN §cund führt zum Bann!` | – | „Teams sind auf diesem Server VERBOTEN und werden mit einem Ban bestraft!“ | belegt (Struktur §c/§4) |
| `common.tip` | `{p}§7{tip}` (Tipp-Texte mit `§e`-Hervorhebung, eigen) | z. B. `Eine bessere §eRüstung §7schützt zuerst deinen Brustpanzer.` | „Wenn man die Rüstung verbessert, dann ändert sich nur dein Brustpanzer“ | belegt (Struktur) |
| `common.kicked.priority` | `{p}§cDu hast Platz für einen Spieler mit Rang gemacht und wirst verschoben.` | – | – | Annahme |
| `common.spectator` | `{p}§7Du bist nun Zuschauer!` | wie Original (funktional) | „Du bist nun Zuschauer!“ | belegt |
| `common.spectator.next.already` | `§cDu wirst bereits in die nächste Runde geschickt` | wie Original (funktional) | „Du wirst bereits in die nächste Runde gesendet“ | belegt (§c) |
| `common.spectator.tp` | `§7Du beobachtest jetzt {nameColor}{name}§7.` | – | – | Annahme |
| `common.spectator.chat` | `§7[Zuschauer] {nameColor}{name}§7: §f{msg}` | – | – | Annahme |
| `common.kill` | `{p}{victimColor}{victim} §7wurde von {killerColor}{killer} §7getötet{xp}` | wie Original (funktional) | „X wurde von Y getötet“ | belegt |
| `common.kill.xp` | ` §7(§e+{xp} XP§7)` | – | „(+20 XP)“ | belegt |
| `common.death` | `{p}{victimColor}{victim} §7ist gestorben` | wie Original | „Dreix31 ist gestorben“ | belegt |
| `common.kill.self` | `{p}§7Du hast {victimColor}{victim} §7getötet §7(§e+{points} Punkte§7)` | wie Original (funktional) | „Du hast X getötet (+5 Punkte)“ | belegt |
| `common.killed_by` | `{p}§cDu wurdest von {killerColor}{killer} §cgetötet` | wie Original (funktional) | „[TTT] Du wurdest von X getötet“ | belegt |
| `common.killer_health` | `§eLeben von {killerColor}{killer}§e: {hearts}` | wie Original (funktional) | „Leben von X ♥♥♥♥♡…“ | belegt |
| `common.kit_reveal` | `{p}{killerColor}{killer} §7hatte das §e[{kit}]§7-Kit` | – | „X hatte das [Engel]-Kit“ | belegt |
| `common.remaining` / `.one` | `{p}§e{n} §7verbleibende Spieler` / `{p}§e1 §7verbleibender Spieler` | wie Original | „5 verbleibende Spieler“ | belegt / abgeleitet |
| `common.team.eliminated` | `{p}{teamColor}Team {team} §cwurde vernichtet!` | wie Original (funktional) | „Team Rosa wurde vernichtet!“ | belegt |
| `common.win.team` | `{p}{teamColor}Team {team} §7hat gewonnen!` | wie Original (funktional) | „Team Violett hat gewonnen!“ | belegt |
| `common.win.player` | `{p}§a{name} hat {mode} gewonnen` | wie Original (funktional) | „X hat SkyWars gewonnen“ | belegt |
| `common.draw` | `{p}§7Die Runde endet ohne Sieger.` | – | – | Annahme |
| `round.private` | `§7 Private Runde – keine Wertung` | – | – | Annahme |
| `common.restart` | `{p}§cDer Server startet in §e{n} §cSekunden neu` | wie Original (funktional) | „Der Server startet in 20 Sekunden neu“ | belegt (§c, Zahl §e) |
| `common.restart.one` | `{p}§cDer Server startet in §e1 §cSekunde neu` | – | – | abgeleitet |
| `common.replay` | `{p}§7Replay dieser Runde: §e{id} ` + `[ANSEHEN]` (`§a`, klickbar → `/replay {id}`) | – | Klick-Muster „Klicke hier zum: [AKTION]“ | Annahme (Muster belegt) |
| `common.gl` / `common.gg` | `§7§k--§{color} GL §7§k--` / `§7§k--§{color} GG §7§k--` | – | farbiges GG/GL | belegt (Format 1.8) |
| `guide.<mode>.title` | `§6Anleitung: {modeColor}{mode}` | – | Anleitungsbuch | Existenz belegt, Text eigen |
| `tips.<mode>.N` | je Modus ≥ 5 eigene Tipps | – | – | Annahme |

Rundenblock-Labels (`round.label.*`): `points` Punkte, `kills` Kills, `deaths` Deaths, `beds` Zerstörte Betten, `chests` Geöffnete Truhen, `blocks` Platzierte Blöcke, `minigames` Gewonnene Minispiele, `xp` Gesammelte Erfahrung (`{n} XP`), `survived` Überlebt, `won` Gewonnen, `challenge` Challenge – alle belegt (V).

---

## 8. Schnittstellen

### 8.1 Spiel-Rahmen-API (`net.retromc.game.framework`, für alle Modus-Module)
| Typ | Signatur (Kern) | Hinweis |
|---|---|---|
| `GameDefinition` (erweitert) | `record GameDefinition(String modeId, String displayName, CombatProfile defaultCombat, TeamMode teamMode /*SOLO,TEAMS,NUMBERED*/, boolean spectatable, boolean persistent, boolean voting, boolean showKillerHealth)` | bestehende Validierung `modeId` bleibt |
| `GameVariant` | `record GameVariant(String key, int teams, int teamSize, int minPlayers /*0 = ceil(max/2)*/, int privateMax)` | aus `mode.yml` |
| `GameLifecycle` | vorhanden; neu `addListener(PhaseListener)` | Zustände unverändert |
| `CountdownPlan` (rein) | `static CountdownPlan lobby(int total, IntSet announce)`; `int next(int rest, Event e /*TICK, FULL, START_CMD, BELOW_MIN*/)`; `boolean announce(int rest)` | Unit-testbar |
| `PhaseTimer` | `start(String id, Duration d, IntConsumer tick, Runnable done)`, `announceAt(int... seconds)`, `set(int seconds)`, `cancel()` | einzige Timer-API (Spec 04/05/06 nutzen sie; `CountdownService` aus Spec 06 = dieser Typ) |
| `WaitingRoom` | `join(Player)`, `quit(Player)`, `online()`, `tipScheduler()`, `board(WaitingBoardProvider)` | |
| `TeamRegistry` / `TeamAllocator` (rein) | `allocate(List<Group> groups, List<TeamSlot> teams) → Allocation` | Party-Gruppen aus `PartyService`; einzige Team-API (`TeamService.assign` in Spec 04/05 = dieser Typ) |
| `MapSelection` (Rahmen) | `MapInfo current()`, `List<MapInfo> candidates(variant)`, `forcemap(Player, String) → Result`; lädt über paper-core `MapService.prepare` (Spec 08 §8.1) | `ForcemapPolicy` (rein): Limits aus `RankService`. Name geändert, weil `MapService` paper-core gehört |
| `SpectatorService` | `makeSpectator(Player, Reason)`, `isSpectator(Player)`, `openTeleporter(Player)` | GUI 5.12/5.13 |
| `KillFeed` | `kill(Player victim, @Nullable Player killer, KillOptions opts)`; `KillOptions(points, xp, kit, toVictimOnly)`; `HeartLine.of(double hp)` | §6.9 |
| `RoundEnd` | `finish(GameResult result)`; `GameResult(matchId, List<Placement>, @Nullable TeamId winnerTeam, @Nullable UUID winner, Map<UUID, RoundSummary> summaries, Map<UUID, Long> baseCoins)` | §6.10, ruft `StatsService.submit`, `roundSummary`, `CoinService.payoutRound` |
| `ServerStatePublisher` | `publish()` (automatisch bei Phasen-/Spielerwechsel, max. 1/s) | ruft nur `ServerLifecycle.setState/…` (Spec 08); schreibt `server:{name}` nicht selbst |
| `PrivateRoundContext` | `Optional<PrivateRound> current()`; `isOwner(Player)` | Env `PRIVATE_ID` |

**Bukkit-Events (`net.retromc.game.framework.event`):** `GamePhaseChangeEvent(old, new)`, `GameStartEvent(matchId, map, teams)`, `PlayerJoinGameEvent(player, asSpectator)`, `PlayerQuitGameEvent(player, phase)`, `TeamJoinEvent(player, team)` (cancellable), `GamePlayerKillEvent(victim, killer?, mode)` (vorh. lt. Spec 06), `PlayerEliminatedEvent(victim, killer?, remaining)` (aus Spec 05), `SpectatorEvent(player, reason)`, `GameEndEvent(matchId, placements)` (= `GameWinEvent` aus Spec 05), `ForcemapEvent(player, map)` (cancellable).

### 8.2 Lobby-API (`net.retromc.lobby.api`, Paper-ServicesManager)
| Service | Methoden | Nutzer |
|---|---|---|
| `LobbyHotbar` | `refresh(Player)`, `setGadgetSlot(Player, ItemStack, ClickHandler)` | Cosmetics-Spec (Slot 3) |
| `VisibilityService` | `HideMode mode(UUID)`, `boolean canSee(Player viewer, Player target)` | paper-core `ChatAudienceFilter` (Chat-Sync) |
| `MovementService` | `allowed(UUID) → Set<MoveMode>`, `suspend(Player, String reason)` / `resume` | Jump & Run, Cosmetics (Elytra-Gadget) |
| `RewardService` | `claimDaily(Player)`, `drawLottery(Player, int n)`, `exchangeVoteKeys(Player, Exchange)` | GUIs; `services:api` schreibt Vote-Keys direkt in `lobby.vote_key` + publiziert `ProfileChanged` |
| `NavigatorRegistry` | `entries()`, `reload()` | Private-Runden-GUI (Modusliste) |

### 8.3 Erwartete Services anderer Bereiche (Kern-UI/Stats Spec 02, Proxy Spec 01)
`BoardService`, `TabService`, `ActionbarService`, `TitleService`, `GuiService`, `HotbarService`, `HologramService`, `CoinService.credit/payoutRound`, `StatsService.submit/roundSummary`, `I18n`, `ChatService` (Kontext Team/Zuschauer), `RankService` (Flags `silentLobby`, `fullServer`, `joinPriority`, Limits `forcemapPerDay`, Bewegung), `PartyService`, `FriendService`, `SettingsService`, `NickService.release` (Rundenende), `SocialGuiApi.open(PROFILE)`. **NEU in paper-core (Lieferpflicht Spec 02):** `NpcService.spawn(NpcSpec{location, skin, name, lookAtPlayers, onRightClick, onLeftClick}) → Npc` (Paket-Entitäten, keine Fremd-Plugins). Bis geliefert: Stub mit `Villager` + `ArmorStand`-Namen (nur Tests).

### 8.4 Netzwerk-Nachrichten (JSON, `core-messaging`)
| Kanal | Typ | Payload | Sender → Empfänger |
|---|---|---|---|
| `net:server-state` | `SERVER_STATE` (Spec 08) | `ServerInfo` + `{countdown, spectatable, teams, teamSize, privateId?, owner?}` | jeder Paper-Server über `ServerLifecycle` → Controller, Lobbys (NPC-GUI, Schilder, Wechsler) |
| `net:queue` | `QUEUE_JOIN` / `QUEUE_LEAVE` | `{ticket, players[], variant, source, priority}` | Lobby / Spielserver (Zuschauer) → Controller |
| `net:queue` | `MATCH_ASSIGNED` | `{ticket, server, matchId?}` | Controller → Proxy (verschiebt), Absender (Meldung) |
| `net:private` | `PRIVATE_CREATE` | `{id, owner, mode, variant?, map, maxPlayers}` | Lobby → Controller |
| `net:private` | `PRIVATE_READY` / `PRIVATE_CLOSED` | `{id, server}` / `{id, reason}` | Spielserver/Controller → Lobbys (Besitzer verbinden, GUI) |
| `net:game` | `GAME_ENDED` | `{matchId, mode, variant, map, winners[], durationS, replayId?, players[]}` | Spielserver → Stats-Jobs, Replay-Dienst, Lobby (`replay:last`), Discord |
| `net:proxy` | `SEND_LOBBY` (Spec 08) | `{uuids[], reasonKey, mode?}` | Spielserver → Proxy (Ziel Modus-Lobby, Party geschlossen) |
| Plugin-Message `retromc:bridge` | `RunProxyCommand` | `{uuid, commandLine}` | Navigator/Wechsler → Velocity (`/gameteleport`, `/server`) – Spec 01 |
| `net:social` | `FriendStatus SPECTATE` | `{uuid, server}` | Spiel-Rahmen → Proxy (Spec 01) |

### 8.5 Pflichten / Entkopplung
- **Modus-Module** liefern nur `GameDefinition`, Varianten, Board-Zeilen INGAME, Siegbedingung, Labels und Basis-Coins; Wartelobby, Countdown, Teams, Zuschauer, Kill-Feed, Rundenende kommen aus dem Rahmen.
- **Lobby** läuft ohne Controller (NPC-Rechtsklick zeigt dann `npc.join.timeout`) und ohne Cosmetics-Modul (Slots 3/5 leer bzw. `§cBald verfügbar`).
- **Controller** (Spec 08) besitzt Queue-Matching, Pod-Start, `servers:*`-Indizes, `lobby:npcstats`.
- **Proxy** (Spec 01) besitzt `/l`, `/gameteleport`, Lobby-Auswahl (`LobbyBalancer`, Spec 08 §6.7), `SEND_LOBBY`-Umsetzung.

---

## 9. Abnahmekriterien

| # | Kriterium | Test |
|---|---|---|
| 1 | Navigator öffnet 6×9 mit genau den 20 Einträgen auf den Slots aus §5.1 (Ära 26: 34/41/43 ersetzt), leere Slots ohne Item; Namen `§x§l…` + Version | Unit: Snapshot von `navigator.yml` → Slotliste; MockBukkit-Inventar |
| 2 | Lobby-Hotbar exakt §4.9 (Slots 1,2,5,8,9 belegt, Items nicht verschiebbar/droppbar) | MockBukkit |
| 3 | Spieler verstecken: Zyklus ALL→VIP→NONE→ALL, 3-s-Cooldown, VIP zeigt nur gesetzte Gruppen + Party | Unit `VisibilityRules.canSee(viewer, target, settings)` (Tabellen-Test aller 6 Gruppen) |
| 4 | Silent-Lobby: Meldung beim Join, niemand sichtbar, Chat gesperrt, Board identisch | Integration |
| 5 | Lobby-Scoreboard: 15 Scores, Rang⇄Spielzeit alle 5 s, Werte mit Tausenderpunkt | Unit Formatierung; Integration |
| 6 | NPC-Rechtsklick erzeugt genau ein Ticket; zweiter Klick → `npc.join.already`; Party nur durch Leiter | Unit mit Fake-Valkey |
| 7 | Countdown: 60 s, Ansagen genau bei {60,50,40,30,20,10,5,4,3,2,1}, Singular bei 1, voll/`/start` → 10 (nur wenn > 10), < min → WAITING + Meldung, ab ≤ 5 kein Abbruch | Unit `CountdownPlan` (parametrisiert) |
| 8 | `minPlayers` = ceil(max/2) (8x4 → 16; 8x1 → 4; 12 → 6), Override wirkt | Unit |
| 9 | Join-Sequenz an neuen Spieler in Reihenfolge Teaming-Hinweis(opt.) → Nick → Online → Map → Tipp | Integration (Chat-Mitschnitt) |
| 10 | `TeamAllocator`: Party bleibt zusammen, wenn Platz; sonst Split + Meldung; Differenz ≤ 1; deterministisch bei gleichem Input | Unit (≥ 15 Fälle inkl. 8x4 mit 3er-/4er-/5er-Party) |
| 11 | Team-GUI-Slots je Teamzahl wie §5.10 (2→11,15; 4→10,12,14,16; 8→9–12,14–17) | Unit `TeamGuiLayout` |
| 12 | `/forcemap`: Limit Supremium 10, Supremium+ 30, Tageswechsel 00:00 Europe/Berlin, 1× pro Runde, gesperrt ≤ 10 s | Unit `ForcemapPolicy` mit fester Uhr |
| 13 | Zuschauer: unsichtbar für Lebende, Herzen sichtbar, Hotbar 1/7/8/9, „Nächste Runde“ doppelt → Fehlermeldung | Integration |
| 14 | Kill-Feed: Formate §7.2; `HeartLine.of(19.0)` = 9×`§c♥` + 1×`§f♡`; `of(20)` = 10 rot; `of(1)` = 0 rot + 10 leer | Unit |
| 15 | Rundenende: Reihenfolge Sieg → Block → Restart(20) → Coins; Ansagen 20/15/10/5/4/3/2/1; danach `SEND_LOBBY` mit Modus; Server-Stopp ≤ 3 s später | Unit `RestartPlan`; Integration |
| 16 | `StatsService.submit` wird genau einmal je `matchId` aufgerufen (auch bei doppeltem `finish`) | Unit |
| 17 | Tagesbelohnung: zwei parallele Claims → genau einer erfolgreich; Streak 1→2→… und Reset nach Lücke; Beträge je Rang | Unit + DB-Test (Testcontainers PostgreSQL) |
| 18 | Lotterie: Monte-Carlo 10⁶ Ziehungen, Mittelwert 995 ± 15 | Unit |
| 19 | Vote-Tausch 1/7/20 Keys, nicht negativ | Unit |
| 20 | Private Runden: GUI-Tooltip exakt 7 Zeilen wie §5.5; nur `supremium`+ erstellt; 1 aktive Runde je Besitzer (DB-Constraint) | Unit + DB |
| 21 | Schilder: Status-Mapping WAITING/STARTING/INGAME/voll/kein Server → 5 Texte | Unit |
| 22 | `/gameteleport`-Registry: Aliase `bw`/`skywars`, entfernte Keys → `gameteleport.removed` | Unit |
| 23 | `MapValidator` deaktiviert Map ohne ausreichende Spawns | Unit |
| 24 | Alle Keys aus §7 existieren in `messages_de.properties`, keine unbenutzten | Unit (Key-Scan) |

---

## 10. Offene Punkte (je mit DEFAULT-ENTSCHEIDUNG)

| # | Offener Punkt / Widerspruch | DEFAULT |
|---|---|---|
| 1 | Paketname | erledigt: Marke RetroMC, Pakete `net.retromc.lobby` / `net.retromc.game.framework` sind im Gerüst bereits so umgesetzt (Nutzer-Entscheidung); keine Umbenennung nötig |
| 2 | Layout und Funktion des NPC-Linksklick-Menüs („kommende Runden, Einstellungen für laufende Runden und Countdowns“) unbelegt | §5.4 |
| 3 | Lobby-Hotbar 2026: Funktion von Slot 3 (Laterne) und 5 (Truhe) nur aus Icons geschlossen; Ära-20+-Slot-7-Item (Papier/Schlüssel) unbekannt | 3 = aktives Gadget, 5 = Cosmetics, 7 leer |
| 4 | Farbe „Navigator“: §c (eine Messung) vs. §e/§6 vs. §7/§f; Spec 02 §4.10 nennt Beispiel `§a` | `§c`, Item-Farben in `hotbar.yml`; Spec-02-Beispiel ist nur illustrativ |
| 5 | Navigator Ära 26: Ersatz für Game1vs1/Kit1vs1/FFA Hardcore (abgeschaltet bzw. nicht mehr in `/gameteleport`) unbelegt; ShoppingChaos (1.21+) 2025 im Navigator gesehen | ItemChaos (34), LavaEscape (41), SMP (43); ShoppingChaos nicht (kein Modul) |
| 6 | Lobby-Countdown-Länge nicht direkt belegt (OCR „68“, Werte 46/40/39/38/30/18) | 60 s, Ansagen-Raster §6.5 + Join-Ansage |
| 7 | Mindestspieler: nur BedWars „halb voll“ belegt | `ceil(max/2)` für alle, Modus-Override |
| 8 | `/start`: Befehl, Rang und Regel unbelegt (nur „Das Spiel startet bereits“ 2017) | Premium aufwärts, auf 10 s, nur ≥ Mindestspieler (wie Spec 05) |
| 9 | Map-Voting: 2025/26 steht die Map beim Join fest; Voting nur in einem 1.8-Modus (Schilder) gesehen | Standard kein Voting (`voting=false`), Forcemap per GUI 5.11/`/forcemap`; Voting als Modus-Option |
| 10 | Bedeutung Wartelobby-Hotbar Slot 2 (weißes Item/Papier) und Slot 7 (Netherstern) | 2 = Map-Abstimmung/Forcemap, 7 = Erfolge & Level |
| 11 | Reihenfolge Rundenende: SkyWars Restart vor Block, BedWars/PartyGames danach | Block → Restart → Coins |
| 12 | Restart-Ansage Spec 05 `§7`-Text vs. Beleg `§c` | `§c` (dieser Katalog ist verbindlich für `common.*`) |
| 13 | Rückkehr nach Runde: Modus-Lobby vs. Hauptlobby | Modus-Lobby, falls vorhanden (B BedWars), sonst Hauptlobby |
| 14 | Zuschauer-Hotbar BedWars 2025 zeigt Buch/Diamant auf 8/9 statt Nächste Runde/Lobby | Rahmen-Standard 1/7/8/9 für alle Modi |
| 15 | Fortbewegung je Rang (Doppelsprung Premium, Fliegen ab Premium+) nur unsicher belegt | wie Spec 01 §6.2 |
| 16 | Tagesbelohnung: Coin-Beträge, Streak-Bonus, Lotterie-Tabelle unbelegt (nur Ø 1.000 und 8.000 für Höchstrang) | §6.7 |
| 17 | Lobby-Slots (Spieler pro Lobby), Silent-Lobby-Slots unbelegt | 100 / 50 |
| 18 | Replay-Bedienung (ID am Rundenende, `/replay`) unbelegt | `common.replay` mit [ANSEHEN], `/replay <ID>`, NPC-Menü 5.9; Aufnahme-Technik in Betriebs-Spec |
| 19 | Private Runden: Stats/Coins, Leerlauf-Ende, Besitzerbefehle unbelegt | keine Wertung, 5 min Leerlauf, `/pr kick|max|close` |
| 20 | Schilder-Status-Texte außer „[Joinbar]“ unbelegt; Schilder 2026 durch NPCs ersetzt | §6.4 Texte; Schilder nur Ära 18/20+ (`lobby.signs.enabled=false` Standard) |
| 21 | Teamzahl/Farbe des 8. BedWars-Teams (Orange nur 1.8-OCR) | Orange `§6` |
| 22 | Zuschauer-Chat-Sichtbarkeit unbelegt | nur Zuschauer untereinander |
| 23 | „Spieler als VIPs anzeigen“-Chat-Sync: Rang (ab Premium) „unverified“ | ab Premium |
| 24 | Tipp-Intervall unbelegt | 30 s, nicht in den letzten 10 s |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | Pakete `dev.netzwerk.*` mit Umbenennungs-Hinweis; Nodes `netzwerk.*`; Kanal `netzwerk:bridge` | `net.retromc.*` (bereits im Gerüst), Nodes `retromc.*`, `retromc:bridge`; Hinweise entfernt |
| K2 | Migration `V4__lobby_game` | `V3_1__lobby_game.sql` (Spec 08 W2) |
| K3 | Rang „Elite“/„Elite+“/„Creator“ aus Spec 02 alt, nicht in Spec 01 §6.1 | Elite → **Supremium** (`supremium`), Elite+ → **Supremium+** (`supremiumplus`), Creator → **VIP** (`vip`); alle Limits wie Spec 01 §6.2 |
| K4 | Eigene Map-Registry `game.map` (+ `map.yml`) doppelt zu Spec 08 `infra.map` (+ `map.json`) | `infra.map` + `map.json` (Spec 08 §6.11); Zusatzspalten per ALTER; Rahmen-Klasse heißt `MapSelection` |
| K5 | Schemas `game` (hier) und `games` (Spec 04/05) | einheitlich `games` |
| K6 | `server:{name}`: eigene Kategorien (`MODE_LOBBY`, `SILENT_LOBBY` …), Feld `phase`, Heartbeat 2 s; Indizes `servers:{category}`/`servers:mode:*` | Spec 08 ist Eigentümer: Kategorien LOBBY/GAME/PERSISTENT (+ Template/`mode`/`silent`/`rankMin`), Feld `state`, Heartbeat 5 s + sofort bei Wechsel; Zugriff über `ServerRegistry` |
| K7 | `RETURN_TO_LOBBY` (net:game) doppelt zu `SEND_LOBBY` (Spec 08) | `SEND_LOBBY {uuids, reasonKey, mode?}` über `PlayerTransfer.toLobby` |
| K8 | Ticket-TTL 120 s vs. Controller 60 s | 60 s (Spec 08 §6.8) |
| K9 | Server-Stopp „Spec 07 eine Runde pro Pod“ (falsche Spec) | Spec 08 §6.9/§6.10 (`oneMatch`, `requestShutdown`) |
| K10 | Framework-Typen doppelt: `TeamService`/`CountdownService`/`GameWinEvent`/`PlayerEliminatedEvent` in Spec 04–06 | hier verbindlich: `TeamAllocator`, `PhaseTimer` (Duration-Signatur übernommen), `GameEndEvent`, `PlayerEliminatedEvent` ergänzt |
| K11 | Tab-Header ohne `.net` | `§e{brand}.net {modeColor}{serverDisplay}` |
