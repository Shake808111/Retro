# Spec 04 – BedWars (alle Varianten) & SkyWars

Stand 2026-10-21. Quellen: Notiz 02 (komplett, Abk. **N02**), Notiz 16 (BedWars-/SkyWars-Teile, **N16**), Notiz 15 (BedWars-/SkyWars-Zeilen, **N15**), Video-Befunde `sweep/batch2_*.md` + `thumbs.md` (**VS**, mit Clipdatum), OCR `sweep/review/*.md` (**OCR**), 4 Einzelbilder (**[Bild]**: SkyWars „Chestrefill“ 2026-09-05, „Team-Truhe | Rot“ 2025-12-14, SkyWars-Kit-GUI „Engel“ 2026-03-26, BedWars-Shop „Fallende Brücke“ 2025-01-26).

**Beleg-Kürzel:** **B** = belegt (Quelle genannt) · **A** = abgeleitet (Schluss aus Belegen) · **AN** = Annahme (frei festgelegt, Konfigwert).
**Ära-Kürzel:** **E26** = 2025/26 (Standard) · **E24** = 2024 (1.20/1.21 vor Mai-2025-Update) · **E21** = BedWars 2.0 2021–2023 · **E17** = 1.8-Ära (Classic, Option `legacy`).
**Marke:** Original „GOMMEHD.NET“/„GommeHD.net“ → `{BRAND_UPPER}.NET` (`§f§l`, Scoreboard → `RETROMC.NET`) bzw. `{brand}` (Tab, Map-Zeile). Modusnamen BedWars/SkyWars sind Gattungsnamen und bleiben (i18n-umbenennbar).
**Grundsatz Texte:** Spalte „Standardtext“ in §7 ist **unser** Wortlaut. Rein funktionale Phrasen („Die Runde beginnt in 5 Sekunden“, „X wurde von Y getötet“) sind gleich, markante Texte (Tipps, Lore, Kit-Beschreibungen, Warnungen) sind neu formuliert, Struktur/Farben/Platzhalter identisch.

**Wichtigste Neubefunde dieser Spec (aus Bildern/OCR):**
1. Generator-Zeitplan ist aus 20+ Scoreboard-Frames exakt rekonstruierbar: Diamant II @05:00, Diamant III @15:00, Gold II @10:00, Gold III @25:00, danach „Max.“ (**A**, §6.1.3).
2. BedWars-Scoreboard nutzt die **roten Vanilla-Scores** als Sortierschlüssel: Teamzeile = lebende Spieler, Kopfzeilen = `maxTeamSize + 6 … +1` (**A**, erklärt alle beobachteten Sortierungen und Nummern 7..0 / 9..0 / 11..0).
3. SkyWars 2026 hat „Globale Events“ (`[SkyWars] Globales Event: Chestrefill (Stufe 1)`, **B [Bild]**), Rundensieg-Challenges per Bossbar (**B**) und Kits mit Nutzungs-Zählern (**B**).

---

## 1. Umfang & Module

| Bereich | Gradle-Modul (settings.gradle.kts) | Status | Paket (Soll) | Plugin-Name (paper-plugin.yml) | Einstieg | Kampfprofil |
|---|---|---|---|---|---|---|
| BedWars (modern, „2.0“) + Classic-Preset + Ranked-Option | `:game:bedwars` | vorhanden (Stub) | `net.retromc.bedwars` | `${brandName}BedWars` | `BedWarsPlugin` | `MODERN` (1.9+-Cooldown, Offhand gesperrt, **B** N02 Q8); Classic: `LEGACY_1_8` |
| SkyWars (modern) + Classic-Preset + Ranked-Option | `:game:skywars` | vorhanden (Stub) | `net.retromc.skywars` | `${brandName}SkyWars` | `SkyWarsPlugin` | `MODERN` (Scoreboard „Hit-Cooldown: Aktiviert“ **B**); Classic: `LEGACY_1_8` |
| Gemeinsame Bausteine (Teams, Spawner, Loot, Kits, Zuschauer, Phasen) | `:game:game-framework` | vorhanden | `net.retromc.game.framework.{team,spawner,loot,kit,spectate,phase,level}` | eingebettet (shadow) | – | – |
| Map-Prüfung (Pflichtfelder BedWars/SkyWars) | `:tools:map-exporter` | vorhanden | `net.retromc.map.exporter` (liest `mapschema/bedwars.json`, `mapschema/skywars.json` aus den Modus-Jars, Spec 08 §6.11) | – | CLI | – |

**Kein NEU-Modul.** NEU sind Unterpakete: `net.retromc.bedwars.{shop,upgrade,trap,spawner,bed,board,npc,classic}`, `net.retromc.skywars.{kit,chest,border,event,challenge,board}`, Framework-Pakete `spawner`, `kit`, `level` (falls nicht schon von Spec 05 angelegt) und die Migration `db/migrations/V4_1__bedwars_skywars.sql`.

**Abhängigkeiten je Plugin** (wie `game/bedwars/build.gradle.kts`): `compileOnly(libs.paper.api)`, `compileOnly(project(":platform:paper-core"))`, `implementation(project(":game:game-framework"))`. Laufzeit: `${brandName}Core` (`load: BEFORE`, `required: true`, `join-classpath: true`). Optional: OldCombatMechanics nur für Classic-Presets. Eine Runde pro Server: `WAITING → STARTING → INGAME → ENDING` (kein Rückweg), Stopp 20 s nach Rundenende (**B** „Der Server startet in 20 Sekunden neu“).

**Varianten (`stats.match.mode_id` / `variant`):**

| modeId | variant | Teams × Größe | Spieler | Ära | Beleg |
|---|---|---|---|---|---|
| `bedwars` | `8x1` | 8 × 1 | 8 | E24/E26 | B (Scoreboard „Spieler 8x1“, „Spieler online: 8/8“) |
| `bedwars` | `4x2` | 4 × 2 | 8 | – | AN (Classic-Liste B) |
| `bedwars` | `4x3` | 4 × 3 | 12 | E24 | A (Skycloud: 4 Teams, max. 3 lebend) |
| `bedwars` | `4x4` (Standard-Queue) | 4 × 4 | 16 | E25 | B (Yggdrasil: Scores 4,4,3,3) |
| `bedwars` | `8x2` | 8 × 2 | 16 | E24 | A (8 Teams, Scores 2) |
| `bedwars` | `8x4` | 8 × 4 | 32 | E25 | B („8×4“, „14/32“) |
| `bedwars` | `9x5` (Sondermap Lotus) | 9 × 5 | 45 | E24/E25 | B (9 Teams, Scores 5,5,5) |
| `bedwars_classic` | `8x1`,`4x2`,`8x2`,`4x3`,`4x4`,`8x4`,`4x5` | s. Name | – | E17 | B (Map-Richtlinien N02 Q1) |
| `bedwars_classic` | `quick` („QBW“) | wie Map | – | E17 | B (Gold 15 s statt 30 s, N15 1.1) |
| `bedwars` | `ranked` | 4 × 4 | 16 | – | AN (Option, Standard **aus**; Nametag „Legende“ B Thumbnail) |
| `skywars` | `16x1` | 16 × 1 | 16 | E25 | B („Spieler online: 16/16“) |
| `skywars` | `8x1` | 8 × 1 | 8 | E24/E26 | B („Spieler online 8/8“) |
| `skywars` | `8x2`, `4x3`, `4x4` | s. Name | 16/12/16 | – | A (offizielle Seite: Teams 2, 3, 4; N02 Q5) |
| `skywars` | `12x2` | 12 × 2 | 24 | E17 | B (Regel-Thread, historisch) |
| `skywars_classic` | `16x1`,`12x2`,`4x2` | – | – | E17 | B (N02 Q5) |
| `skywars` | `ranked` | 12 × 1 | 12 | E19 | B historisch (N16 §3.4), Option **aus** |

---

## 2. Datenmodell

### 2.1 PostgreSQL (Migration `V4_1__bedwars_skywars.sql`, Schema `games`)

Mitbenutzt (andere Specs): `stats.match`, `stats.match_player`, `stats.daily`, `stats.alltime`, `stats.map_alltime` (Spec 02), `economy.wallet/ledger` (Coins), `games.kit_owned`, `games.kit_selected`, `games.achievement`, `games.achievement_progress` (Spec 05; hier mit `mode='skywars'`).

| Tabelle | Spalten (Typ) | Schlüssel / Indizes | Zweck |
|---|---|---|---|
| `games.bw_quickshop` | `uuid UUID NOT NULL`, `variant_group VARCHAR(16) NOT NULL DEFAULT 'modern'` (`modern`/`classic`), `slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 20)`, `item_id VARCHAR(40) NOT NULL` | PK `(uuid, variant_group, slot)` | Schnellkauf-Belegung (21 Felder, B 3×7-Raster) |
| `games.bw_shop_settings` | `uuid UUID PRIMARY KEY`, `category_order SMALLINT[] NOT NULL DEFAULT '{0,1,2,3,4,5,6,7,8}'`, `hotbar_pref JSONB NOT NULL DEFAULT '{}'` (`{"weapon":0,"pickaxe":1,"blocks":3,…}`), `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `uuid` | „Shop-Einstellungen“ (roter Beutel, B news #139) |
| `games.bw_goldvote` | `uuid UUID PRIMARY KEY`, `rate VARCHAR(8) NOT NULL CHECK (rate IN ('none','low','normal','high'))`, `saved_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `uuid` | `/goldvote save` (Classic, B) |
| `games.mode_level` | `uuid UUID NOT NULL`, `mode VARCHAR(24) NOT NULL`, `xp BIGINT NOT NULL DEFAULT 0 CHECK (xp>=0)`, `level INT NOT NULL DEFAULT 1`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `(uuid, mode)`; IDX `(mode, level DESC, xp DESC)` | Modus-Level (SkyWars „[38]“ B; BedWars XP B) |
| `games.sw_kit_settings` | `uuid UUID PRIMARY KEY`, `legacy_enabled BOOLEAN NOT NULL DEFAULT false`, `hidden_kits VARCHAR(32)[] NOT NULL DEFAULT '{}'` | PK `uuid` | „Kit-Einstellungen“ (frühere Kits wieder aktivieren, B 01.12.2023) |
| `games.sw_kit_uses` | `uuid UUID NOT NULL`, `kit_id VARCHAR(32) NOT NULL`, `tier SMALLINT NOT NULL DEFAULT 1` | PK `(uuid, kit_id)` | Kit-Stufe (AN; nur falls Kit-Upgrades aktiviert, Standard: alle Stufe 1) |
| `games.ranked_rating` | `uuid UUID NOT NULL`, `mode VARCHAR(24) NOT NULL`, `season SMALLINT NOT NULL`, `elo INT NOT NULL DEFAULT 0`, `league VARCHAR(16) NOT NULL DEFAULT 'unranked'`, `placements SMALLINT NOT NULL DEFAULT 0`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `(uuid, mode, season)`; IDX `(mode, season, elo DESC)` | Ranked-Option (Standard aus) |
| `games.sw_map_vote` | – | – | **entfällt** (nur Valkey, rundenlokal) |

SQL-Hinweise: alle `uuid`-Spalten referenzieren `core.player(uuid)` `ON DELETE CASCADE`. Schreiben am Rundenende in **einer** Transaktion zusammen mit `StatsService.submit` (idempotent über `match_id`; Level/XP-Update `UPDATE … SET xp = xp + $delta` nur, wenn `stats.match` neu eingefügt wurde).

### 2.2 Valkey

| Key / Kanal | Typ | Inhalt | TTL |
|---|---|---|---|
| `bw:qs:{uuid}` | HASH `slot→item_id` | Cache Schnellkauf | 1 h, invalidiert bei Änderung |
| `bw:ss:{uuid}` | STRING (JSON) | Cache Shop-Einstellungen | 1 h |
| `sw:kit:{uuid}` | HASH `selected`, `owned` (CSV), `legacy` | Cache Kit-Auswahl/-Besitz | 1 h |
| `lvl:{mode}:{uuid}` | HASH `xp`, `level` | Cache Modus-Level (Chat/Nametag) | 10 min |
| `vote:map:{server}` | HASH `uuid→map` | Map-Stimmen der Wartelobby (SkyWars Map-Menü) | 30 min |
| `vote:gold:{server}` | HASH `uuid→rate` | Gold-Vote Classic | 30 min |
| `party:team:{server}` | HASH `partyId→teamIndex` | Party-Teamzuteilung beim Server-Join | 30 min |
| `net:server-state` (Kanal, besteht) | PUB | `SERVER_STATE` (Spec 08) über `ServerLifecycle`; Felder `online` (nicht `players`), Zusatzfeld `teams:[{i,alive,bed}]` | – |
| `net:queue` (Kanal, besteht) | PUB/Request | `{mode:"bedwars", variant:"4x4", partyId, size}` | – |
| `net:game` (Kanal, NEU, Spec 03) | PUB | `GAME_ENDED{matchId, mode, variant, map, winners[], durationS, …}` (Spec 03 §8.4; früher `RoundEnded`) für Lobby-Wände/Discord | – |

---

## 3. Befehle

Allgemeine Befehle `/stats`, `/statsall`, `/statsd`, `/top`, `/resetstats`, `/tokens`, `/togglestats` liefert Spec 02 (gelten auf BedWars-/SkyWars-Servern für den aktuellen Modus). `/hub`, `/l`, `/leave` liefert Spec 01/Framework.

| Syntax | Aliase | Recht/Rang | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|
| `/start` | – | **Spiel-Rahmen, Spec 03 #4** (`retromc.game.start`, Premium aufwärts) | Countdown → 10 s, nur ≥ `minPlayers` | Keys `common.start.*` (Spec 03 §7.2) |
| `/forcemap <Map>` | `/fm` | **Spiel-Rahmen, Spec 03 #5** (Supremium 10/Tag, Supremium+ 30/Tag, VIP/Team ∞) | Wartelobby: setzt Map vor Teleport | Keys `common.forcemap.*` (Spec 03 §7.2) |
| `@a <Text>` / `@all <Text>` | – | alle | Chat-Präfix im Spiel: an alle statt ans Team (Teamvarianten). In 1er-Varianten geht jeder Chat an alle | Anzeige `§7[@all] …` (**B**) · leerer Text: nichts |
| `/goldvote` | – | alle, nur `bedwars_classic` Wartelobby | öffnet Gold-Vote-GUI (5.8) | `{prefix}§7Du hast für §6{rate} §7gestimmt.` · außerhalb Classic: `{prefix}§cDas geht hier nicht.` |
| `/goldvote save` | – | alle | speichert die aktuelle Wahl als Standard für künftige Runden (**B**) | `{prefix}§7Deine Gold-Wahl wurde gespeichert.` · ohne Stimme: `{prefix}§cDu hast noch nicht abgestimmt.` |
| `/kit [Name]` | `/kits` | alle, SkyWars Wartelobby | ohne Arg: Kit-GUI (5.9); mit Arg: wählt besessenes Kit | Erfolg = `sw.kit.selected` · `{prefix}§cDieses Kit besitzt du nicht.` · `{prefix}§cDieses Kit gibt es nicht.` · in Runde: `{prefix}§cDu kannst dein Kit nur in der Wartelobby ändern.` |
| `/coins` | – | alle | zeigt Coins (Spec 02 `coins.balance`) | `{prefix}§7Du hast §e{n} §7Coins` (**B**) |
| `/teleporter` | `/tp` (nur Zuschauer) | Zuschauer | öffnet Zuschauer-Teleporter (Spec 05, GUI 5.8 Framework) | `§cDu bist kein Zuschauer.` |
| `/shopsettings` | – | alle, BedWars Wartelobby | öffnet Shop-Einstellungen (5.4) | – |
| `/quickshop` | – | alle, BedWars Wartelobby | öffnet Schnellkauf-Editor (5.5) | – |
| `/bwsetup <create|spawn|bed|shop|upgrade|spawner|chest|save> …` | – | `retromc.admin.mapsetup` | Map-Einrichtung, schreibt `maps/<map>/map.json` | `§aGespeichert: {key}` · `§cUnbekannter Teamname.` |
| `/swsetup <create|island|chest|mid|spectator|border|save> …` | – | `retromc.admin.mapsetup` | wie oben | wie oben |
| `/bwdebug spawner <res> <tier>` | – | `retromc.admin.debug` | setzt Generatorstufe sofort | `§aStufe gesetzt.` |
| `/swdebug event <id>` | – | `retromc.admin.debug` | löst globales Event sofort aus | `§aEvent ausgelöst.` |

`{prefix}` = Spec 02 `prefix.mode` → `§f[§cBedWars§f] ` bzw. `§f[§bSkyWars§f] ` (Klammerfarbe §f/§7 in Frames uneinheitlich, Spec 02 entscheidet §f). Classic: `§f[§cClassicBW§f] ` (**B** 2024).

---

## 4. Anzeige-Elemente

### 4.1 Scoreboard BedWars – Wartelobby (E24/E26, **B** 2024-01-23, 2025-01-30)

Titel `§f§l{BRAND_UPPER}.NET`. Scores sichtbar (rot, Vanilla).

| Score | Zeile | Dynamik |
|---|---|---|
| 8 | ` ` (leer) | – |
| 7 | `§fMap` | – |
| 6 | `§e{map}` | gewählte/gesetzte Map |
| 5 | `  ` | – |
| 4 | `§fSpieler` | (E24: `Spieler:` – Option) |
| 3 | `§e{teams}x{size}` | Variante, z. B. `8x1`, `8x4` |
| 2 | `   ` | – |
| 1 | `§fHit-Cooldown` | – |
| 0 | `§aAktiviert` | `§cDeaktiviert` bei Classic |

### 4.2 Scoreboard BedWars – Ingame (E24/E26, **B** 25+ Frames)

| Score | Zeile | Dynamik |
|---|---|---|
| H+5 | ` ` | – |
| H+4 | `§fMap §e{map}` (eine Zeile; E25-Variante `§fMap§7: §e{map}` → Option `board.mapColon`, Standard aus) | – |
| H+3 | `  ` | – |
| H+2 | `§bDiamant {I|II|III} §e{mm:ss}` bzw. `§bDiamant III §eMax.` | Countdown bis zur angezeigten Stufe, 1 Hz |
| H+1 | `§6Gold {I|II|III} §e{mm:ss}` bzw. `§6Gold III §eMax.` | dito |
| H | `   ` | – |
| `alive(t)` | `{§a✔|§c✘} {teamColor}{teamName}` je Team | ✔ = Bett steht, ✘ = Bett zerstört; Score = lebende Spieler |

`H = maxTeamSize` (B: Kopf-Nummern 7..2 bei 8x1, 9..4 bei 4x3, 11..6 bei 9x5). **Sortierung = Vanilla** (Score absteigend). Gleichstand: eigenes Team zuerst, dann feste Teamreihenfolge (6.1.1) – umgesetzt über unsichtbare Eintrags-Namen `§{hex}` in Sortierreihenfolge (**A** aus Fairy, Reserve, Kingdom, Mittelalter). Unbesetzte Teams: ✔ (Bett bleibt), Score 0 (**B** Fairy).
Legacy (E17, Option): Teamzeilen `§c❤ {teamColor}{team}` / `§7✘`, Kopf „Map“ – Rest wie oben (**AN**, Frames 2017 unlesbar).

### 4.3 Scoreboard SkyWars – Wartelobby / Ingame (**B** 2024-12 … 2026-09)

| Score | Wartelobby | Score | Ingame |
|---|---|---|---|
| 8 | ` ` | 11 | ` ` |
| 7 | `§fMap` | 10 | `§fMap` |
| 6 | `§e{map}` | 9 | `§e{map}` |
| 5 | `  ` | 8 | `  ` |
| 4 | `§fKit` | 7 | `§fKills` |
| 3 | `§a{kit}` | 6 | `§c{kills}` |
| 2 | `   ` | 5 | `   ` |
| 1 | `§fHit-Cooldown` | 4 | `§fSpieler` |
| 0 | `§aAktiviert` | 3 | `§b{lebendeSpieler}` |
|  |  | 2 | `    ` |
|  |  | 1 | `§fKit` |
|  |  | 0 | `§a{kit}` |

Doppelpunkt-Variante (`Map:`, `Kills:`, `Spieler:`, E25-01 einmalig) = Option `board.labelColon`, Standard aus. Zuschauer sehen dasselbe Board (eigene Kills).

### 4.4 Tablist

| Element | Format | Beleg |
|---|---|---|
| Header 1 | `§e{brand}.net §cBedWars` / `§e{brand}.net §bSkyWars` (Spec 02 `tab.header.mode`) | B |
| Header 2 | Spec 02 `tab.header.report` (`§7Regelverstoß gesehen? §c/report §7<Spieler>`) | B |
| Footer | Spec 02 (`/friend` §a, `/party` §d, `/clan` §e) | B |
| Eintrag BedWars | `{teamColor}{team} §7| §f{name}[ §7[§e{clan}§7]][ §7[§dParty§7]]` | B |
| Eintrag SkyWars | Rang-Präfix wie Lobby (`§cAdmin §7| §c{name}`), danach `§7[§e{clan}§7]` | B |
| Ausgeschiedene | ganz unten, Name `§7` (Spec 02 `TabEntryProvider.order = 1000+`) | A |
| Sortierung BedWars | Team-Index, im Team nach Name | A |

### 4.5 Nametags

| Modus | Format | Beleg |
|---|---|---|
| BedWars | `{teamColor}{team} §7| §f{name}` + Suffix ` §7[§e{clan}§7]` und/oder ` §7[§dParty§7]`; Teamkameraden durch Wände sichtbar | B |
| SkyWars Ingame | `§f{name} §7[{lvlColor}{level}§7]`; Party-Mitglieder ` §7[§dParty§7]` | B |
| SkyWars Teamvarianten | Prefix `§f#{n} ` (E26, **AN**); legacy `T{n} ` (B 1.8) | A |
| Ranked | Liganame über Kopf (`§dLegende`, `§bChampion`, `§aHolz` …) | B Thumbnail, Farben teils AN |

### 4.6 Actionbar

| Kontext | Text | Priorität | Beleg |
|---|---|---|---|
| BedWars Wartelobby, ohne Team | `§fWähle dein Team` | dauerhaft | B |
| BedWars Ingame | `{teamColor}Team {team}` | dauerhaft (Kanal `team`) | B |
| BedWars/SkyWars Zuschauer | `§7Zuschauer` | dauerhaft | B |
| SkyWars Wartelobby | `§fWähle dein Team` (nur Teamvarianten) | dauerhaft | B |
| SkyWars Ingame | `§fTeam #{n}` | dauerhaft (niedrig) | B |
| SkyWars Kit bereit | `§7Kit-Fähigkeit: §aAb jetzt nutzbar §a[{uses} Nutzungen]` | hoch, 3 s | B |
| SkyWars Kit Cooldown | `§7Kit-Fähigkeit: §cNutzbar in §e{s} Sekunden` | hoch, jede Sekunde | B |
| SkyWars Kit Schutzzeit | `§7Kit-Fähigkeit: §6Nicht in der Schutzzeit nutzbar` | hoch, 2 s | B |
| SkyWars Kit Restnutzung | `§a{uses} Nutzungen!` | hoch, 2 s | B |
| SkyWars Kit-Item (Engel) | `§7Engeltotem` (Itemname) | Vanilla | B |

### 4.7 Titles

| Auslöser | Titel | Untertitel | Zeiten (in/stay/out Ticks) | Beleg |
|---|---|---|---|---|
| BedWars Tod mit Bett | `§cRespawn in §e{n} §cSekunde(n)` (5→1, Singular bei 1) | – | 0/25/0 je Sekunde | B |
| BedWars eigenes Bett zerstört | `§cBett zerstört!` | `§7Du kannst nicht mehr respawnen` | 10/50/10 | AN |
| BedWars Rundenstart | `§cBedWars` | `§f{map}` | 10/40/10 | AN (SkyWars-Analog B) |
| BedWars Sieg (alle) | `{teamColor}Team {team}` | `§7hat die Runde gewonnen` | 10/70/20 | B |
| SkyWars Rundenstart/Teleport | `§bSkyWars` | `§f{map}` | 10/40/10 | B |
| SkyWars Sieg (alle) | `§6{name}` (Teamvarianten: `§6Team #{n}`) | `§fhat die Runde gewonnen` | 10/70/20 | B |
| Spawner-Info | – (Hologramm, s. 4.9) | – | – | B |

### 4.8 Bossbar

| Modus | Inhalt | Farbe/Stil | Beleg |
|---|---|---|---|
| SkyWars | `§6Challenge: §7{challengeText}`; Fortschritt = verbleibender Anteil der Rundenzeit bzw. Challenge-Fortschritt | YELLOW, PROGRESS | B |
| BedWars | keine | – | A (kein Frame mit Bossbar) |

### 4.9 Hologramme (TextDisplay, `HologramService`)

| Ort | Zeilen | Beleg |
|---|---|---|
| Über Item-Händler | `§e§lITEMS` (TextDisplay `scale 2.0`, Billboard CENTER) | B |
| Über Upgrade-Händler | `§e§lUPGRADES` (scale 2.0) | B |
| Über Diamant-Spawner | `§bDiamant` / `§eSpawnt in {n} Sekunde(n)` + rotierender Diamantblock (ItemDisplay) | B |
| Über Gold-Spawner (Mitte) | `§6Gold` / `§eSpawnt in {n} Sekunde(n)` + Goldblock | A |
| BedWars Wartelobby | persönliches Stats-Hologramm Spec 02 `HOLO_PERSONAL` (`§cBedWars §6Statistiken`) | B |
| SkyWars Wartelobby/Spawn | `§eKit-Einstellungen` / `§7Klicke hier zum Anpassen` (Interaction-Entity öffnet 5.10) | B |
| SkyWars Wartelobby | Stats-Hologramm wie BedWars (`§bSkyWars §6Statistiken`), Top-10-Wand (Spec 02) | B |

### 4.10 NPCs

| NPC | Entity (Standard, pro Map überschreibbar `npc.*.type`) | Klick | Beleg |
|---|---|---|---|
| Item-Händler (je Team) | VILLAGER (Profession FARMER) | Rechts/Links → GUI 5.1 | B (E24 Villager, E25 auch Zombie-Modelle → Map-Option) |
| Upgrade-Händler (je Team) | ZOMBIE_VILLAGER | → GUI 5.2 | B news #121 |
| Wartelobby-Händler | VILLAGER, Hologramm `§e§lSCHNELLKAUF` (AN) | → GUI 5.5 (Editor) / Vorschau 5.1 nur ansehen | B (Existenz) |
| Teamwahl-Figuren (BedWars Wartelobby, optional) | Mannequin/Armorstand in Teamfarbe | Klick = Team beitreten | B (Dragons-Wartelobby) |
NPCs sind unverwundbar, ohne KI, Kopf folgt nächstem Spieler (AN).

### 4.11 Hotbar

| Kontext | Slot → Item → Name → Aktion | Beleg |
|---|---|---|
| BedWars Wartelobby | Rahmen-Hotbar **Spec 03 §4.10** (einheitlich): 1 → RED_BED → `§fWähle dein Team §7(Rechtsklick)` → GUI 5.3 · 2 → PAPER → `§eMap-Abstimmung` → Spec 03 GUI 5.11 · 5 → RED_BUNDLE → `§cShop-Einstellungen` → GUI 5.4 (modusspezifisch, Slot 3–6 frei für Modus) · 7 → NETHER_STAR → `§bErfolge & Level` · 8 → WRITTEN_BOOK → `§6Anleitung` · 9 → DIAMOND → `§bCosmetics` | B Itempositionen 1,2,7,8,9; roter Beutel B (Slot AN); Bedeutungen 7/8/9 nach Spec 03 (B N05) |
| SkyWars Wartelobby | 1 → CHEST → `§bKits §7(Rechtsklick)` → GUI 5.9 · 3 → PAPER → `§eMap-Abstimmung` → GUI 5.11 · 5 → COMPARATOR → `§eKit-Einstellungen` → GUI 5.10 · 7/8/9 wie Spec 03 §4.10 (`§bErfolge & Level`, `§6Anleitung`, `§bCosmetics`); Teamvarianten zusätzlich 2 → WHITE_BANNER → `§fWähle dein Team` → GUI 5.12. Abweichung von Spec 03 (Papier auf 3 statt 2) ist belegt und als Modus-Override erlaubt | B Positionen [Bild], Bedeutungen 3/5 AN, 7/8/9 Spec 03 |
| Zuschauer (beide) | **Spec 03 §4.11** (1 Kompass `§fTeleporter`, 7 Netherstern `§bEinstellungen`, 8 `§aNächste Runde`, 9 `§cZurück zur Lobby`) | B Positionen/Kompassname |
| BedWars Start-Inventar | 1 Holzschwert, 2 Holzspitzhacke, Rüstung Leder in Teamfarbe (Helm–Schuhe, unzerstörbar) | B |
| SkyWars Start | Kit-Inhalt; Kit-Fähigkeits-Item in Slot 9 (AN) | A |

---

## 5. GUIs

Konventionen aus Spec 02 §5.1 (Rahmen-Glas ohne Namen, `hide_tooltip`, Klick-Sounds). Titel in §8 (Vanilla-Dunkelgrau).

### 5.1 BedWars Item-Shop – Titel `Wähle eine Kategorie`, 6 Reihen (**B [Bild]**)

| Slot | Inhalt | Name / Lore | Klick |
|---|---|---|---|
| 0 | AMETHYST_CLUSTER | `§eSchnellkauf` / `§7Deine Lieblings-Items auf einen Blick` | Kategorie 0 |
| 1 | OBSIDIAN | `§eBlöcke` / `§7Zum Bauen, Brücken und Verteidigen` | Kategorie 1 |
| 2 | CHAINMAIL_CHESTPLATE | `§eRüstung` / `§7Bessere Brustpanzer für dich` | Kategorie 2 |
| 3 | IRON_PICKAXE | `§eWerkzeuge` / `§7Spitzhacken, Äxte und Scheren` | Kategorie 3 |
| 4 | IRON_SWORD | `§eWaffen` / `§7Schwerter, Bögen und Munition` | Kategorie 4 |
| 5 | POTION (rot) | `§eTränke` / `§7Heilung, Tempo und mehr` | Kategorie 5 |
| 6 | ENDER_PEARL | `§eSpezial` / `§7Nützliche Spezial-Items` | Kategorie 6 |
| 7 | TNT | `§eExplosives` / `§7Explosive Items, mit denen du dir den Weg frei sprengst` | Kategorie 7 |
| 8 | BRICKS | `§eSonstiges` / `§7Essen und Kleinkram` | Kategorie 8 |
| 9–17 | Glasscheibe; Farbe = `PURPLE` bei Schnellkauf, sonst `LIGHT_BLUE`; Scheibe unter der aktiven Kategorie `WHITE` | – | – |
| 18, 26, 27, 35, 36, 44 | leer | – | – |
| 19–25, 28–34, 37–43 | 21 Waren der Kategorie (Reihenfolge 6.1.6) | s. u. | Kauf |
| 45–53 | Glasscheibe wie 9–17 | – | – |

Kategorienamen außer „Explosives“ (**B**) sind **AN**; Icons **B**. Kategoriereihenfolge aus `bw_shop_settings.category_order`.

**Waren-Tooltip** (**B** Tooltips Ton/Glas/Eisenrüstung/Fallende Brücke):
```
§f{itemName}                                (Diamantschwert: §b)
§7Preis: {resColor}{price} {resName}         Kupfer §c · Eisen §f · Gold §6 · Diamant §b
§7
§7{lore1}
§7{lore2}
§7(Item bleibt nach Kauf permanent)          nur bei permanent=true
§7
§c<Doppelt-Rechtsklick zum Entfernen>        nur in Kategorie Schnellkauf
§7<Shift-Klick: zum Schnellkauf hinzufügen>  nur außerhalb Schnellkauf (AN)
```
Bei fehlenden Ressourcen bleibt der Name `§f`, Preiszeile wird `§7Preis: §c{price} {resName}` (AN). Klick: Kauf (6.1.6). Doppelt-Rechtsklick (2 Klicks < 400 ms) in Schnellkauf: Slot leeren. Shift-Klick: in ersten freien Schnellkauf-Slot.

### 5.2 BedWars Upgrade-Shop – Titel `Upgrades`, 5 Reihen (**AN** Layout; Inhalte 6.1.7)

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–8, 18–26, 36–44 | Glasscheibe LIGHT_BLUE (Reihe 3 = Trenner `GRAY`) | – | – |
| 10 | IRON_SWORD | `§cWaffen` / `§7Schärfe für alle Schwerter deines Teams` / `§7` / `§7Level {n}/{max}` / `§7Preis: §b{p} Diamanten` oder `§aMaximal` | Upgrade |
| 11 | IRON_CHESTPLATE | `§cRüstung` / `§7Schutz für die Rüstung deines Teams` / … | Upgrade |
| 12 | IRON_PICKAXE | `§cAbbau` / `§7Effizienz für alle Werkzeuge deines Teams` / … | Upgrade |
| 13 | FEATHER | `§cFederfall` / `§7Weniger Fallschaden für dein Team` / … | Upgrade |
| 14 | BEACON | `§dBasis-Effekte` / `§7Effekte für dein Team in der Basis` / … | Upgrade |
| 15 | FURNACE | `§cRessourcen` / `§7Schnellere Kupfer- und Eisen-Spawner` / … | Upgrade |
| 28 | LIGHTNING_ROD | `§fBlitz-Falle` / `§7{beschreibung}` / `§7Preis: §b{p} Diamanten` | Falle kaufen |
| 29 | INK_SAC | `§fBlindheitsfalle` | dito |
| 30 | FERMENTED_SPIDER_EYE | `§fSchwächefalle` | dito |
| 31 | SPYGLASS | `§fEntdeckerfalle` | dito |
| 32 | IRON_PICKAXE (Glint) | `§fAbbaulähmungs-Falle` | dito |
| 38–40 | Falle in Warteschlange oder LIGHT_GRAY_DYE `§7Freier Fallenplatz` | `§7Position {i}` | – |

Kauf ohne genug Diamanten: Chat `bw.shop.missing`. Max-Level: Klick ohne Wirkung, Sound VILLAGER_NO.

### 5.3 BedWars Teamauswahl – Titel `Wähle dein Team`, Reihen = ⌈Teams/9⌉+0 (8 Teams: 1 Reihe; 9 Teams: 1 Reihe) (**AN**)

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–(n−1) | `{FARBE}_WOOL` (Braun/Türkis/Magenta/Dunkelgrün über passende Wolle) | `{teamColor}Team {team}` / `§7{cur}/{size} Spieler` / `§7- §f{name}` je Mitglied / `§7` / `§aKlicke zum Beitreten` bzw. `§cTeam ist voll` | beitreten (6.1.2) |
Glint auf eigenem Team. Party-Leiter zieht Party mit, wenn Platz (6.1.2).

### 5.4 BedWars Shop-Einstellungen – Titel `Shop-Einstellungen`, 3 Reihen (**AN**; Existenz **B** news #139)

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–8 | Kategorie-Icons in aktueller Reihenfolge | `§e{Kategorie}` / `§7Linksklick: nach links` / `§7Rechtsklick: nach rechts` | verschiebt |
| 9–17 | Glas GRAY | – | – |
| 18 | IRON_SWORD | `§eWaffen-Slot: §f{n}` / `§7Klicke, um den Hotbar-Slot zu ändern` | Slot+1 (1–9 zyklisch) |
| 19 | IRON_PICKAXE | `§eWerkzeug-Slot: §f{n}` | dito |
| 20 | WHITE_WOOL | `§eBlock-Slot: §f{n}` | dito |
| 21 | BOW | `§eBogen-Slot: §f{n}` | dito |
| 22 | POTION | `§eTrank-Slot: §f{n}` | dito |
| 26 | BARRIER | `§cZurücksetzen` | Standard laden |

### 5.5 BedWars Schnellkauf-Editor – Titel `Schnellkauf bearbeiten`, 6 Reihen (**AN**)
Layout wie 5.1. Klick auf Ware in Kategorie = „aufnehmen“ (Cursor-Item), Klick auf Schnellkauf-Slot = setzen. Speichern sofort in `games.bw_quickshop` (+ Cache-Invalidierung). Vorschau-Modus (gleicher NPC, Shift-Rechtsklick): 5.1 ohne Kauf.

### 5.6 Team-Truhe – Titel `Team-Truhe | {Team}`, 3 Reihen (**B [Bild]**)
Vanilla-Truhe, teamweit geteilt (eine `Inventory`-Instanz je Team). Nur Teammitglieder können öffnen (`§cDas ist nicht die Truhe deines Teams.`, AN). Endertruhe = Vanilla, pro Spieler (B Enderkiste neben Händler).

### 5.7 BedWars Upgrade-/Fallen-Vorschau – entfällt (Info im Upgrade-Shop).

### 5.8 Classic Gold-Vote – Titel `Gold-Spawnrate`, 1 Reihe (**AN**; Mechanik **B**)
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 1 | BARRIER | `§cKein Gold` / `§7Stimmen: §e{n}` | Stimme |
| 3 | GOLD_NUGGET | `§6Wenig Gold` / `§7Alle §e60 §7Sekunden` / `§7Stimmen: §e{n}` | Stimme |
| 5 | GOLD_INGOT | `§6Normal` / `§7Alle §e30 §7Sekunden` | Stimme |
| 7 | GOLD_BLOCK | `§6Viel Gold` / `§7Alle §e15 §7Sekunden` | Stimme |
| 8 | WRITABLE_BOOK | `§aWahl speichern` (= `/goldvote save`) | speichern |

### 5.9 SkyWars Kits – Titel `Kits`, 5 Reihen (**B [Bild]**)

| Slot | Inhalt | Name / Lore | Klick |
|---|---|---|---|
| 0–8 | YELLOW_STAINED_GLASS_PANE | – | – |
| 9–35 | Kits in Katalogreihenfolge (6.2.5), max. 27 | s. u. | wählen/kaufen |
| 36–44 | YELLOW_STAINED_GLASS_PANE | – | – |

Kit-Tooltip (**B** Engel):
```
§6{Kit}
§6Ausrüstung:
§7{n}x {item}            je Zeile
§7
§6Fähigkeit:
§7{beschreibung}        (Zeilenumbruch ~40 Zeichen)
§7
§7Preis: §e{price} Coins            nicht gekauft (AN)
§aKlicke zum Kaufen | §cNicht genug Coins
§aIm Besitz – §eKlicke zum Auswählen        gekauft (AN)
§a✔ Ausgewählt                              aktiv, + Glint (AN)
```
Kauf: `ConfirmGui` (Spec 02 §5.2) → `CoinService.debit(uuid, price, "kit:skywars:{id}")` → `games.kit_owned`. Auswahl → `games.kit_selected` + Chat `sw.kit.selected`.

### 5.10 SkyWars Kit-Einstellungen – Titel `Kit-Einstellungen`, 3 Reihen (**AN**; Existenz **B**)
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 11 | CLOCK | `§eFrühere Kits` / `§7Zeigt entfernte Kits wieder an` / `§7Status: §aAn`/`§cAus` | toggelt `legacy_enabled` |
| 13 | ENDER_EYE | `§eKit-Fähigkeit ankündigen` / `§7Gegner sehen dein Kit beim Kill` (immer an, nur Info) | – |
| 15 | HOPPER | `§eKits ausblenden` → Unter-GUI Liste mit Toggle je Kit | `hidden_kits` |

### 5.11 Map-Menü – Titel `Map-Menü`, 3 Reihen (**B** Titel/Tooltip SkyWars 2026; BedWars AN)
Slots 10–16 (max. 7 Maps der Variante, Rotation zufällig je Runde), Item = `FILLED_MAP`:
```
§6{map} §a(ausgewählt)          nur bei eigener Stimme
§7Punkte: §e{p}
§7K/D: §e{kd}
§7Siegesquote: §e{wr} %
§7
§7Stimmen: §e{votes}            (AN)
§eKlicke zum Abstimmen          (AN)
```
Werte aus `stats.map_alltime` des Spielers (Spec 02). Klick = Stimme (`vote:map:{server}`), Gewinner bei Countdown 10 s (6.2.1).

### 5.12 SkyWars Teamauswahl – wie 5.3 mit Items `WHITE_BANNER`, Name `§fTeam #{n}` (**AN**).

### 5.13 Loot-Container – Vanilla `Truhe`/`Fass` (3 Reihen), Inhalt 6.2.4 (**B** Fass-GUI).

---

## 6. Mechaniken & Zahlen

### 6.1 BedWars (modern, E26 Standard)

#### 6.1.1 Teams
| Index | Name | Farbe | Blockfarbe | Beleg |
|---|---|---|---|---|
| 0 | Rot | §c | RED | B |
| 1 | Blau | §9 | BLUE | B |
| 2 | Gelb | §e | YELLOW | B |
| 3 | Grün | §a | LIME | B |
| 4 | Rosa | §d | PINK | B |
| 5 | Hellblau | §b | LIGHT_BLUE | B |
| 6 | Orange | §6 | ORANGE | B |
| 7 | Violett | §5 | PURPLE | B |
| 8 | Türkis | §3 | CYAN | B (Lotus) |
| 9 | Magenta | `#FF55FF`→ wegen Kollision mit Rosa: `#C040C0` | MAGENTA | B Name, Farbe AN |
| 10 | Braun | `#8B5A2B` | BROWN | B Name, Farbe AN |
| 11 | Dunkelgrün | §2 (Kurzform `D-Grün` im Scoreboard) | GREEN | B Name, Farbe A |
Map wählt die ersten N Teams oder eine explizite Liste (`teams:` in `map.json`). Teamblöcke = „Farbiger Ton“ (Terracotta) und Wolle in Teamfarbe.

#### 6.1.2 Ablauf
1. **WAITING:** Spieler betritt Wartelobby (Mini-Map-Modell, **B**). Chat: Join-Zeile, `bw.join.online`, `bw.join.map`, ggf. Tipp (alle 30 s ein zufälliger Tipp, AN). Hotbar 4.11, Board 4.1, Actionbar `Wähle dein Team`.
2. Teamzuteilung: Party wird beim Join gemeinsam in ein Team mit genug Platz gesetzt; passt sie nicht: `bw.party.split` (**B**). Solo-Spieler werden bevorzugt zu bestehenden Teams aufgefüllt („Solos spielen öfter in Teams“, **B** news #121): Algorithmus = Team mit den meisten Spielern, das noch Platz hat, Gleichstand → kleinster Index.
3. **Countdown-Start**, sobald Spieler ≥ ⌈max/2⌉ (**B** „halb voll“) und ≥ 2 Teams belegt: 60 s (AN). Ansagen bei 60, 50, 40, 30, 20, 15, 10, 5, 4, 3, 2, 1 (**B** beobachtet 40/30/15/10/5–2). XP-Level-Anzeige = Restsekunden (**B**). Server voll → Countdown auf 10 s kürzen, falls größer (AN). Fällt die Zahl unter ⌈max/2⌉: Abbruch, `bw.countdown.abort`.
4. Bei 10 s: Map fix (Map-Menü-Gewinner), Spieler ohne Team werden verteilt (Schritt 2).
5. **STARTING→INGAME (0 s):** Teleport auf Team-Spawn, Inventar 4.11, Titel, `bw.start`, Generatoren starten, Board 4.2. Leere Teams behalten Bett (✔, Score 0).
6. **Spawn-Raum:** Radius 3 Blöcke (AN) um Team-Spawn: Blockplatzierung verboten (**B**), Meldung keine (Aktion wird still abgebrochen, AN).
7. Spiel läuft bis 1 Team übrig ist oder 60:00 (AN). Bei 60:00: alle Betten zerstören (`bw.bed.suddendeath` AN), nach weiteren 10:00 Unentschieden (keine Sieger, AN).
8. **ENDING:** Siegermeldung + Title, Rundenblock (Spec 02 `roundSummary`), Coins (`CoinService.payoutRound`), `bw.end.restart`, nach 20 s Stopp.

#### 6.1.3 Ressourcen & Generatoren (**B** Kupfer/Eisen/Gold/Diamant, Takte Stufe I aus Guide; Stufenzeiten **A** aus Scoreboards)

| Ressource | Item (Material) | Ort | Stufe I | Stufe II | Stufe III | Stapel-Cap am Spawner | Beleg |
|---|---|---|---|---|---|---|---|
| Kupfer | `COPPER_INGOT`, Name `§cKupfer` | Basis | 1 / 1 s | – | – | 64 (AN) | B (Item/Name/Tooltip), Takt B Guide |
| Eisen | `IRON_INGOT`, `§fEisen` | Basis | 1 / 8 s | – | – | 16 (AN) | B Takt Guide |
| Gold | `GOLD_INGOT`, `§6Gold` | Mitte | 1 / 35 s | 1 / 25 s (AN) | 1 / 15 s (AN) | 8 (AN) | Stufe I B (Guide; Konflikt 40 s, s. §10) |
| Diamant | `DIAMOND`, `§bDiamant` | Inseln | 1 / 25 s | 1 / 20 s (AN) | 1 / 15 s (AN) | 4 (AN) | Stufe I B (Guide) |

Stufenwechsel (Rundenzeit ab Start): **Diamant II 05:00, Diamant III 15:00; Gold II 10:00, Gold III 25:00** (**A**: Differenz Gold−Diamant 5:00 bei II/II, Diamant−Gold 5:00 bei III/II, Gold−Diamant 10:00 bei III/III, „Max.“ ab Diamant III). Scoreboard zeigt **nächste** Stufe + Restzeit; nach letzter Stufe `III Max.`. Chat `bw.spawner.tier` an alle. Cap-Regel (**B**): Gold/Diamant spawnen nicht weiter, wenn ≥ Cap Items im Radius 1,5 Blöcke liegen. Kupfer/Eisen: Items stapeln als ein Entity (Merge). Pickup durch mehrere Spieler: Item geht an Spieler im Radius ≤ 1,5 (Vanilla). „Ressourcen“-Upgrade (6.1.7) verkürzt Kupfer/Eisen-Takte.

Classic (E17): Bronze (`BRICK`, §c) 1/1 s (AN), Eisen 1/10 s, Gold 1/30 s (**B** älteres Listing), QBW Gold 1/15 s (**B**); Gold-Vote: Kein/60/30/15 s (AN-Werte, Mechanik B).

#### 6.1.4 Bett, Tod, Respawn, Final Kill
1. Bett (2 Blöcke) nur durch Gegner abbaubar; eigenes Team: Abbau abgebrochen, `bw.bed.own` (AN). Kein Drop.
2. Bettabbau: `bw.bed.destroyed.self` an Opferteam (+ Title 4.7), `bw.bed.destroyed` an alle, `bw.bed.destroyed.you` an Zerstörer (+10 XP, **B**). Board ✔→✘.
3. Tod (Spieler mit Bett): Zuschauer-Kamera am Todesort, 5 s Respawn (**B**), Title-Countdown, `bw.respawn.in`. Respawn am Team-Spawn mit Holzschwert, gekauften permanenten Items (Rüstung, Werkzeugstufen) und Lederrüstung; Hotbar-Positionen von Schwert/Spitzhacke bleiben (**B** news #121). Nicht permanente Items und Ressourcen gehen verloren (siehe 5.).
4. Kill: Ressourcen (Kupfer, Eisen, Gold, Diamant) des Opfers wandern in das Inventar des Killers, `bw.resources.transfer` (**B**, auch bei normalem Kill). Killer = letzter Schadensverursacher innerhalb 10 s (AN; Void/Fallschaden zählt dann als Kill). Ohne Killer: Ressourcen verfallen.
5. Tod ohne Bett = **Final Kill**: Opfer wird Zuschauer, `bw.team.remaining` an alle; ist das Team leer: `bw.team.eliminated`, Team-Truhen-Inhalt geht an den Killer (**B** news #139), Board-Score 0.
6. Verlassen während der Runde: zählt als Final Death; Ressourcen + Truhen-Anteil gehen an den letzten Schadensverursacher bzw. – ohne – zu gleichen Teilen an alle lebenden Gegner (**B** „alle Spieler erhalten die Ressourcen“), Meldung `bw.resources.transfer` an jeden Empfänger, `bw.leave.ingame` an alle.
7. Sieg: letztes Team mit lebenden Spielern. Final-Kill-Statistik: nur Final Kills/Deaths zählen in `kills`/`deaths` (**B** N16 §3.4).
8. Rettungskapsel (Spezial): löst automatisch aus, wenn ein Treffer tödlich wäre (**B** 2021) → setzt auf 2 Herzen, Teleport 5 Blöcke nach oben auf Plattform (AN), Item verbraucht.

#### 6.1.5 Punkte, XP, Coins, Level
| Größe | Formel / Wert | Beleg |
|---|---|---|
| Ranking-Punkte | 5 × Final-Kill + 10 × Bett + 50 × Sieg | A (Spec 02, 2 Datenpunkte exakt) |
| Chat-Belohnung Kill (E24) | `(+5 Punkte)` nur bei Final Kill | B Text, Bedingung A |
| XP Kill (E26) | +25 XP (alle Kills, AN: auch nicht-finale) | B |
| XP Bett | +10 XP | B |
| XP Sieg / Überlebt / Spielzeit | +50 / +20 / +1 je 30 s Spielzeit (AN; prüfen: Beispiel 3 Kills + 1 Bett = 181 XP) | AN |
| Coins Rundenende | `CoinService.payoutRound`: Basis = 2,5 × Runden-XP (gerundet) | AN (Beleg-Werte +314/+475 Coins) |
| Level | `games.mode_level`, XP-Kurve Spec 02/Level-Spec, Anzeige nur in Rundenblock | AN |
| Rundenblock | `Punkte`, `Kills`, `Zerstörte Betten`, `Gesammelte Erfahrung: {xp} XP`, Leerzeile, `Überlebt: ✓/✗`, `Gewonnen: ✓/✗` | B |
| Achievements | 5 neue (2025) + „Auf Distanz“ (Bogen-Kills) | B Existenz; Liste §10 |

#### 6.1.6 Shop-Katalog (E26; Preise konfigurierbar in `shop.yml`, Stapelgröße = gekaufte Menge)

Spalte **Q** = Standard-Schnellkauf-Position (0–20, AN aus [Bild] 2025-01-26).

| Kat. | Item (Name §f) | Material | Menge | Preis | Q | Lore (unser Text, §7) | Beleg |
|---|---|---|---|---|---|---|---|
| Blöcke | Farbiger Ton | `{TEAM}_TERRACOTTA` | 16 | 8 Kupfer | 0 | „Ein einfacher Block für schnelle, sichere Wege“ | B Preis 2025-11 (E24: 1 Kupfer), Menge A |
| Blöcke | Wolle | `{TEAM}_WOOL` | 16 | 4 Kupfer | – | „Billig und schnell verbaut“ | AN |
| Blöcke | Farbiges Glas | `{TEAM}_STAINED_GLASS` | 4 | 2 Kupfer | 6 | „Explosionsschutz I“ / „Durch diesen Block siehst du deine Gegner“ | B Preis 2024 (Menge AN) |
| Blöcke | Endstein | `END_STONE` | 3 | 12 Kupfer | 3 | „Hält Explosionen gut stand“ | B 2021 (Preis 2024 geändert, Wert unbekannt) |
| Blöcke | Terrakotta-Treppe | `{TEAM}_TERRACOTTA`-Stufe → `CUT_SANDSTONE_STAIRS` | 4 | 3 Kupfer | – | „Für schnelle Aufstiege“ | B 2021 Preis (Sandsteintreppe) |
| Blöcke | Leiter | `LADDER` | 8 | 4 Kupfer | – | „Klettern ohne Umwege“ | B Preis 2025-05, Menge AN |
| Blöcke | Holzbretter | `OAK_PLANKS` | 16 | 4 Eisen | – | „Stabiler als Ton“ | AN |
| Blöcke | Obsidian | `OBSIDIAN` | 4 | 4 Gold | – | „Der härteste Block im Shop“ | AN (Icon B) |
| Blöcke | Schleimblock | `SLIME_BLOCK` | 1 | 2 Eisen | 14 | „Federt Stürze ab“ | A (im Schnellkauf gesehen) |
| Rüstung | Kettenrüstung (Brustpanzer) | `CHAINMAIL_CHESTPLATE` | 1 | 32 Kupfer | 2 | „Leichter Schutz für den Anfang“ / permanent | B 2021 |
| Rüstung | Eisenrüstung (Brustpanzer) | `IRON_CHESTPLATE` | 1 | 12 Eisen | 9 | „Solider Schutz für harte Kämpfe“ / permanent | B 2025 |
| Rüstung | Diamantrüstung (Brustpanzer) | `DIAMOND_CHESTPLATE` | 1 | 16 Gold | – | „Bester Schutz im Shop“ / permanent | B 2021 |
| Werkzeuge | Holzspitzhacke | `WOODEN_PICKAXE` | 1 | Start | – | permanent | B (Start) |
| Werkzeuge | Eisenspitzhacke | `IRON_PICKAXE` | 1 | 10 Kupfer | 10 | „Schneller durch Ton und Holz“ / permanent | AN |
| Werkzeuge | Goldspitzhacke | `GOLDEN_PICKAXE` | 1 | 3 Gold | – | „Effizienz II“ / permanent | AN |
| Werkzeuge | Diamantspitzhacke | `DIAMOND_PICKAXE` | 1 | 6 Gold | – | „Effizienz III“ / permanent | AN |
| Werkzeuge | Axt | `IRON_AXE` | 1 | 8 Eisen | – | „Bricht Holz und Schilde“ / permanent | A (Hotbar), Preis AN |
| Werkzeuge | Schere | `SHEARS` | 1 | 20 Kupfer | 4 | „Wolle in Sekunden“ / permanent | A (häufig in Hotbar), Preis AN |
| Waffen | Knüppel | `STICK` (Rückstoß I) | 1 | 7 Eisen | – | „Schubst Gegner von der Brücke“ | B 2025-05 (Knockback Stick) |
| Waffen | Steinschwert | `STONE_SWORD` | 1 | 24 Kupfer | 1 | – | AN |
| Waffen | Eisenschwert | `IRON_SWORD` | 1 | 15 Eisen | – | – | B 2021 |
| Waffen | Diamantschwert (Name §b) | `DIAMOND_SWORD` | 1 | 5 Gold | 7 | – | B 2021 |
| Waffen | Bogen Stufe I | `BOW` | 1 | 12 Eisen | 11 | – | B Name, Preis AN |
| Waffen | Bogen Stufe II | `BOW` (Stärke I) | 1 | 3 Gold | – | – | AN |
| Waffen | Bogen Stufe III | `BOW` (Stärke II, Schlag I) | 1 | 6 Gold | – | – | AN |
| Waffen | Armbrust | `CROSSBOW` (4 Varianten: Normal, Mehrfachschuss, Durchschuss, Schnellladen) | 1 | 8 Eisen / 3 Gold / 3 Gold / 3 Gold | – | – | B Existenz 2022, Preise AN |
| Waffen | Pfeile | `ARROW` | 8 | 2 Gold | 12 | – | B Menge, Preis AN |
| Waffen | Windkugel | `WIND_CHARGE` | 2 | 4 Eisen | – | „Wirbelt Gegner durch die Luft“ | B Existenz 2025, Preis AN |
| Waffen | Windkanone | `BREEZE_ROD` | 1 | 6 Gold | – | „Feuert zielsuchende Windkugeln auf Gegner bis 10 Blöcke; braucht Windkugeln“ | B Mechanik 2025, Preis AN |
| Tränke | Trank der Heilung Stufe II | `POTION` (instant_health II) | 1 | 3 Eisen | 15 | – | B Name, Preis AN |
| Tränke | Trank der Schnelligkeit | `POTION` (Speed II, 45 s) | 1 | 1 Gold | – | – | AN |
| Tränke | Trank der Sprungkraft | `POTION` (Jump V, 45 s) | 1 | 1 Gold | – | – | AN |
| Tränke | Trank der Stärke | `POTION` (Strength I, 30 s) | 1 | 3 Gold | – | – | AN |
| Spezial | Enderperle | `ENDER_PEARL` | 1 | 11 Gold | – | – | B 2021 (Classic ebenso) |
| Spezial | Rettungsplattform | `BLAZE_ROD` | 1 | 3 Gold | – | „Fängt dich mit einer Plattform unter dir auf“ | B 2021 |
| Spezial | Rettungskapsel | `TOTEM_OF_UNDYING`-Optik (Custom Model) | 1 | 4 Gold | – | „Rettet dich automatisch vor dem Tod“ | B Existenz, Preis AN |
| Spezial | Teleporter | `GUNPOWDER` | 1 | 3 Eisen | – | „Bringt dich nach 6 Sekunden zurück in deine Basis“ | B Preis 2021, Lore-Inhalt 6 s B |
| Spezial | Mobiler Shop | `VILLAGER_SPAWN_EGG` | 1 | 8 Eisen | – | „Stellt einen Händler an deine Position (60 s)“ | B Preis 2025, Dauer AN |
| Spezial | Portal | `END_PORTAL_FRAME`-Optik | 2 | 4 Gold | – | „Verbindet zwei Orte – wer eins betritt, kommt am anderen heraus“ | B Preis 2025 |
| Spezial | Freezer | `PACKED_ICE` | 1 | 6 Eisen | – | „Friert Gegner im Umkreis 3 s ein“ | B Preis 2022, Wirkung AN |
| Spezial | Fallende Brücke | `WHITE_CONCRETE`-Optik | 1 | 4 Eisen | 16 | „Baut eine 12 Blöcke lange Treppe abwärts in Blickrichtung“ | B |
| Spezial | Sneaker | `LEATHER_BOOTS` (Custom) | 1 | 8 Kupfer | – | „Leise Schritte: keine Fallen-Auslösung 30 s“ | B Preis 2024, Wirkung AN |
| Spezial | Bettbauer Plus | `RED_BED`-Optik | 1 | 16 Eisen | – | „Baut einen Schutzwall um dein Bett“ | B Preis 2025 (Name AN) |
| Spezial | Spinnennetz | `COBWEB` | 1 | 8 Kupfer | – | – | AN |
| Spezial | Brücken-Ei | `EGG` | 2 | 2 Eisen | 13 | „Zieht eine Brücke hinter sich her“ | A (Ei ×2 im Schnellkauf), Rest AN |
| Spezial | Schneeball | `SNOWBALL` | 2 | 6 Kupfer | – | „Rückstoß auf Distanz“ | A, Preis AN |
| Spezial | Truhe | `CHEST` | 1 | 1 Eisen | 5 | „Eine eigene Truhe für unterwegs“ | A (im Schnellkauf), Preis AN |
| Explosives | TNT | `TNT` (zündet beim Setzen) | 1 | 4 Gold | – | – | AN |
| Explosives | Super-TNT | `TNT` (Custom, Kraft 6) | 1 | 6 Gold | – | – | B 2025 |
| Explosives | C4 | `TNT`-Optik + Fernzünder | 1 | 1 Gold | – | „Per Rechtsklick fernzünden“ | B 2024 |
| Explosives | Sprengweste | `LEATHER_CHESTPLATE` (Custom) | 1 | 10 Eisen | – | „Explodiert bei deinem Tod“ | B 2024 |
| Explosives | Feuerball | `FIRE_CHARGE` | 1 | 40 Kupfer | 8 | „Fliegt geradeaus und explodiert beim Aufprall“ | B Item, Preis AN (Fehlbeträge 4/18 Kupfer gesehen) |
| Sonstiges | Keks | `COOKIE` | 4 | 4 Kupfer | – | – | A (Hotbar), AN |
| Sonstiges | Goldener Apfel | `GOLDEN_APPLE` | 1 | 3 Gold | – | – | AN |
| Sonstiges | Gebratenes Fleisch | `COOKED_BEEF` | 2 | 6 Kupfer | – | – | AN |

Entfernt (nicht anbieten): Booster, Glowstone, Honigblock (**B**). Kauf-Ablauf: (1) Prüfen Bestand der Währung im Inventar; (2) fehlt etwas: `bw.shop.missing` (Restbetrag), Sound VILLAGER_NO; (3) sonst abziehen, Item in bevorzugten Hotbar-Slot (Shop-Einstellungen), sonst ersten freien Slot, sonst Boden; (4) permanente Items ersetzen die alte Stufe (Rüstung: nur Brustpanzer wird ersetzt, **B** Tipp). Werkzeug-Stufe sinkt beim Tod nicht (AN). Shift-Klick-Kauf ×4 nicht vorgesehen (AN).

#### 6.1.7 Team-Upgrades & Fallen (Diamanten, Upgrade-Händler)

| Upgrade (Chatname) | Level / Kosten (Diamanten) | Wirkung | Beleg |
|---|---|---|---|
| Waffen | 1: 4 · 2: 12 | Schärfe I / II auf allen Schwertern und Äxten des Teams | B (Guide Schärfe I/II), Name B |
| Rüstung | 1: 2 · 2: 8 · 3: 14 · 4: 20 | Schutz I–IV | B |
| Abbau | 1: 2 · 2: 6 | Effizienz I / II auf Werkzeugen | B (Guide), Name AN |
| Federfall | 1: 3 · 2: 6 | Federfall I / II auf Schuhen | B (Konflikt II = 7, s. §10) |
| Basis-Effekte | 1: 3 · 2: 6 | L1 Regeneration I, L2 + Eile I im Basis-Radius 12 Blöcke | B Name, Wirkung/Kosten AN |
| Ressourcen | 1: 2 · 2: 4 · 3: 6 · 4: 8 · 5: 12 | Kupfer/Eisen-Takt −15 % je Level (L5 = −75 %, AN) | B „bis Level 5“ (news #139), Rest AN |

| Falle (Chatname) | Kosten | Wirkung bei Eindringen (Radius 10 AN, nur Gegner) | Beleg |
|---|---|---|---|
| Blitz-Falle | 1 Diamant | Blitz (kosmetisch) + Glowing 10 s + Alarm an Team | B Name, Wirkung AN |
| Blindheitsfalle | 1 Diamant | Blindheit + Langsamkeit 8 s | B Name, Wirkung AN |
| Schwächefalle | 2 Diamanten | senkt alle Upgrades des Eindringlings um **1 Level** für 60 s | B |
| Entdeckerfalle | 1 Diamant | hebt Unsichtbarkeit auf, Glowing 15 s | B Name, Wirkung AN |
| Abbaulähmungs-Falle | 1 Diamant | Abbaulähmung I 10 s, Radius **6** (verkleinert) | B Existenz/Verkleinerung, Werte AN |
Warteschlange max. 3 Fallen, eine Falle löst pro Eindringen aus, danach 30 s Sperre (AN). Kauf: `bw.trap.bought` an Team. Auslösen: Title an Team `§cFalle ausgelöst!` / `§7{trap}` (AN). Upgrade-Kauf: `bw.upgrade.self` (Käufer) und `bw.upgrade.team` (Team).

#### 6.1.8 Sonstige Regeln & Limits
| Regel | Wert | Beleg |
|---|---|---|
| Kampf | 1.9-Cooldown an, Offhand gesperrt (Klick in Slot 40 abgebrochen) | B |
| Item-Cooldowns (Rettungsplattform, Portal, Freezer, Windkanone) | 20 s / 10 s / 15 s / 2 s (AN); Meldung `bw.item.cooldown` | B Meldung |
| Bauhöhe | Map `buildMaxY` (Pflicht), Standard Spawn-Y + 40 (AN) | AN |
| Abbaubar | nur von Spielern gesetzte Blöcke + Betten | A (Standard) |
| Endertruhe | Vanilla, pro Spieler, bleibt nach Tod | B Existenz |
| Team-Truhe | 27 Slots, Team-weit; bei Team-Auslöschung → Killer | B |
| Chat | Team-Chat Standard; `@a`/`@all` → alle mit Präfix `§7[@all] `; Zuschauer/Tote: Präfix `§7[✗] `, nur an Zuschauer (AN: auch an Lebende in 8x1) | B Präfixe |
| Teaming | in 8x1 verboten (Warnung beim Join `bw.teaming`) | A (SkyWars-Analog B) |
| Map-Anforderungen | je Team: Spawn, Bett (Kopf+Fuß), Item-NPC, Upgrade-NPC, Team-Truhe, Kupfer-/Eisen-Spawner; global: ≥ 4 Diamant-Spawner (1 je Insel), ≥ 1 Gold-Spawner (Mitte), Zuschauer-Spawn, Wartelobby-Spawn, `buildMaxY`, `voidY`, Map-Autor (`builder`) | A |
| Leerlauf | AFK 120 s in Wartelobby → kein Kick (AN) | AN |

#### 6.1.9 Classic BedWars (Preset `bedwars_classic`, E17)
Kampfprofil `LEGACY_1_8`, 1 Händler (Villager, Shop wie 5.1 ohne Upgrades), Ressourcen Bronze/Eisen/Gold (6.1.3), Gold-Vote, Board-Option `legacy`, Präfix `[ClassicBW]`, Ranking nach Wins (`stats.mode.points_formula` für `bedwars_classic` = `{"wins":1}`, **B** bis 2021). Preise (**B** älteres Listing, sonst AN = moderne Werte in Bronze): Lederrüstung je Teil 1 Bronze, Knüppel 8 Bronze, Eisenschwert 5 Gold, Enderperle 11 Gold, Endstein 7 Bronze, Sandstein 2 Stück 1 Bronze, Rettungsplattform 3 Gold.

#### 6.1.10 Ranked (Option, Standard aus)
Elo je Saison (3 Monate, Spec 02/Duels), Start 0, +10 je Final Kill, +20 Bett, +30 Sieg, Einsatz −15 je Runde (AN; historische SkyWars-Werte B). Ligen-Nametag (4.5).

### 6.2 SkyWars (modern, E26 Standard)

#### 6.2.1 Ablauf
1. **WAITING:** Join-Zeile, bei Solo-Varianten `sw.teaming` (rot, **B**), Nick-Info (Spec 01), ein Tipp (**B**: Schilde/Axt, XP-Tipp). Board 4.3 links, Hotbar 4.11.
2. Countdown-Start bei ≥ ⌈max/2⌉ (AN wie BedWars): 60 s (AN), Ansagen 60, 30, 20, 10, 5, 4, 3, 2, 1 (**B** 10, 5–1). Bei 10 s: `sw.join.online` + `sw.join.map` (**B** Reihenfolge), Map-Vote abgeschlossen.
3. **0 s:** Teleport auf Inseln (Käfig aus Glas, 1 Block über Boden, AN), `sw.teleported`, Title `SkyWars`/`{map}`, `sw.kit.announce` an alle für jeden (AN: nur an sich selbst, B-Zeile „X nutzt das [Engel]-Kit“ zeigt eigenes Kit), Ansage `Die Runde beginnt in 3/2/1 Sekunde(n)` (**B**).
4. **Käfig auf:** `sw.phase.build` („Aufbauphase beginnt“). **Schutzzeit 30 s** (kein PvP-Schaden, Kit-Fähigkeiten gesperrt, **B**), Ansagen 30, 20, 10, 3, 2, 1 (**B**, Singular bei 1).
5. **Kampf:** `sw.phase.fight` (§e), `sw.border.in` mit 600 s (**B**).
6. **Globale Events** (2026, **B** Chestrefill Stufe 1): Chestrefill Stufe 1 bei Kampf+05:00, Stufe 2 bei Kampf+10:00 (AN). Refill füllt **alle** Truhen/Fässer neu (Stufe 1: Tier-1-Tabelle Inseln, Tier-2 Mitte; Stufe 2: beide Tier+1-Tabelle, AN).
7. **Border:** Start bei Kampf+600 s oder 60 s nach „nur noch 2 Teams“ (**B**), was früher kommt. Ansagen 600, 300, 60, 30, 10, 5–1 (AN). Border schrumpft von Map-Radius auf 10 Blöcke um Mitte in 180 s, 1 Schaden/s außerhalb (AN).
8. Tod: sofort Zuschauer (**B** offizielle Regel), Meldungen `sw.kill.kit`, `sw.kill` / `sw.death`, `sw.life` (nur an Opfer, **B** Reihenfolge), `sw.remaining`, ggf. `sw.border.twoteams`.
9. Sieg: letzter Spieler/letztes Team. `sw.win`, Title, Rundenblock, Coins, `sw.end.restart` (20 s).

#### 6.2.2 Inseln & Mitte
| Element | Regel | Beleg |
|---|---|---|
| Insel je Team | 1 Spawn je Spieler (Teamgröße), 2–3 Inseltruhen/Fässer Tier 1 | B (Truhe + Fass) |
| Mitte | 4–8 Truhen Tier 2; Schneebälle und Eier **nur** dort | B |
| Blöcke | alle abbaubar außer Map-Grenze/`unbreakable`-Regionen | AN |
| Void | Tod unter `voidY` (Map), Killer = letzter Schaden ≤ 10 s | AN |
| Teaming | in `16x1`/`8x1` verboten (Meldung beim Join) | B |
| Schilde | aktiv; Axt-Treffer deaktiviert Schild (Vanilla 5 s) | B Tipp |

#### 6.2.3 Punkte, XP, Coins
| Größe | Wert | Beleg |
|---|---|---|
| Ranking-Punkte | 1 × Kill + 5 × Sieg | A (Spec 02) |
| XP Kill | +20 XP (Kill-Meldung `(+20 XP)`) | B (E26; einmal 15 → Option) |
| XP sonst | Sieg +50, Challenge geschafft +50, 1 XP je 15 s Überleben (AN; Beispiel 2 Kills → 118 XP) | Quellenarten B (Tipp), Werte AN |
| Coins | `payoutRound`: Basis = 2 × Runden-XP + 10 je Kill (AN; Beleg-Werte +42 … +377) | AN |
| Rundenblock | `Punkte`, `Kills`, `Deaths`, `Geöffnete Truhen`, `Platzierte Blöcke`, `Gesammelte Erfahrung: {xp} XP`, `Challenge: ✓/✗` (✗ §c) | B |
| Level | `games.mode_level` (`skywars`), Chat-Präfix `§7[{lvlColor}{lvl}§7] ` (`✪` ab 300, AN-Schwelle), Nametag 4.5 | B Anzeige |
| Map-Stats | `stats.map_alltime` je Map (Punkte, K/D, Siegesquote) | B |

#### 6.2.4 Truhen-Loot (Tabellen `loot/skywars_t1.yml`, `t2.yml`, `refill1.yml`, `refill2.yml`; Gewichte **AN**, Itempool **B** aus Frames)
Füllung: je Truhe 5–8 Stacks (T1) bzw. 6–9 (T2), zufällige Slots; je Insel garantiert: 1 Schwert (≥ Stein), 1 Rüstungsteil, 1 Block-Stack ≥ 16, 1 Essen (AN, faire Starts).

| Item | Menge | T1 Gewicht | T2 Gewicht | Beleg |
|---|---|---|---|---|
| Stein / Bruchstein / Steinziegel / Polierter Tiefenschiefer | 16–48 | 10 | 6 | B |
| Eichenbretter / Wolle (rosa) / Ziegel | 16–32 | 10 | 6 | B |
| Steinschwert | 1 | 6 | 2 | AN |
| Eisenschwert | 1 | 4 | 4 | AN |
| Diamantschwert (Schärfe I–II, Fluch des Verschwindens möglich) | 1 | 1 | 5 | B (Tooltip „Schärfe II“, „Fluch des Verschwindens“) |
| Netheritschwert | 1 | 0 | 1 | B (Frames; evtl. Kit) |
| Eisenhelm/-brustpanzer/-hose/-stiefel (Schutz I, Haltbarkeit I) | 1 | 5 je | 3 je | B |
| Diamanthelm/-brust/-hose/-stiefel (Schutz 0–II) | 1 | 1 je | 4 je | B |
| Axt (Eisen/Diamant) | 1 | 3 | 3 | B |
| Spitzhacke (Eisen/Diamant) | 1 | 4 | 2 | B |
| Bogen / Pfeile | 1 / 8–16 | 2 / 3 | 4 / 4 | B (Pfeile 13) |
| Goldener Apfel | 1–3 | 2 | 5 | B |
| Goldene Karotte / Brot / Steak | 3–16 | 6 | 4 | B |
| Wassereimer / Lavaeimer | 1 | 3 / 2 | 2 / 3 | B |
| Spinnennetz | 2–5 | 3 | 4 | B |
| TNT | 2–5 | 2 | 3 | B |
| Enderperle | 1 | 1 | 4 | B |
| Wurftrank der Heilung II | 1 | 1 | 4 | B |
| Schneeball / Ei | 8–16 | 0 | 5 | B („nur Mitte“) |
| Diamant / Eisenbarren / Stöcke | 2–7 | 3 | 3 | B |
| Werkbank / Kompass | 1–17 / 1 | 2 / 1 | 1 / 1 | B |
| Erfahrungsfläschchen | 4–8 | 2 | 3 | AN |

#### 6.2.5 Kits (Katalog `kits/skywars.yml`; ein Kit pro Spieler, Kauf mit Coins, permanent)

Aktiver Katalog E26 (Namen **B** aus Chat „hatte das [X]-Kit“/Changelog; Inhalte/Preise überwiegend **AN**):

| # | id | Name | Preis (Coins) | Ausrüstung | Fähigkeit (Nutzungen / Cooldown) | Beleg |
|---|---|---|---|---|---|---|
| 1 | starter | Starter | 0 | Steinschwert, Holzspitzhacke, 16 Bretter | – | B (kostenlos, Fan-Liste) |
| 2 | engel | Engel | 25.000 | 1× Engeltotem (Totem der Unsterblichkeit, Name `§7Engeltotem`) | passiv: pro Kill 1 Goldener Apfel | **B [Bild]** Ausrüstung+Fähigkeit; Preis AN |
| 3 | teufel | Teufel | 60.000 | Netheritschwert (ohne Verzauberung) | Plagegeist beschwören (Vex, Angriff alle 10 s, halber Schaden), Cooldown 30 s, 3 Nutzungen | B Plagegeist/30 s; Rest AN |
| 4 | jaeger | Jäger | 40.000 | Netheritschwert Schärfe II | passiv: +1 Extraherz je Kill (max. +5) | A (Zuschauer-Chat), AN |
| 5 | phantom | Phantom | 35.000 | 3 Phantommembranen | kurzer Flug-Dash, 6 Nutzungen, Cooldown 10 s | B Name/„[6 Nutzungen]“, Wirkung AN |
| 6 | ziege | Ziege | 30.000 | Ziegenhorn | Rammstoß (starker Rückstoß), 7 Nutzungen, Cooldown 8 s | B Name/7 Nutzungen, Wirkung AN |
| 7 | elytra | Elytra | 50.000 | Elytren, 3 Feuerwerksraketen | – | B Name |
| 8 | panzer | Panzer | 80.000 | Eisenrüstung komplett (Haltbarkeit 10), Trank der Feuerresistenz | – | B (= früher „Tank“, Fan-Liste 80k) |
| 9 | ziegel | Ziegel | 15.000 | 64 Ziegel, Steinspitzhacke | – | B Name, AN |
| 10 | stampfer | Stampfer | 45.000 | Federfall-Stiefel IV | Landung aus ≥ 4 Blöcken: Schockwelle 3 Schaden r=3, Cooldown 15 s | B Name, AN |
| 11 | ninja | Ninja | 40.000 | 3 Enderperlen, Goldschwert | Unsichtbarkeit 5 s, 2 Nutzungen | B Name, AN |
| 12 | poseidon | Poseidon | 60.000 | Dreizack (Loyalität) | Wasserwelle, 3 Nutzungen | B Name, AN |
| 13 | wolke | Wolke | 30.000 | 16 Schneeblöcke | Wolkenplattform unter dir (5 s), 3 Nutzungen | B Name, AN |
| 14 | hundefluesterer | Hundeflüsterer | 40.000 | 2 Knochen, Wolf-Spawnei ×2 | ruft 2 zahme Wölfe | B Name (2024-10), Item Knochen B |
| 15 | luftbaendiger | Luftbändiger | 45.000 | 4 Windkugeln | Windstoß, 4 Nutzungen | B Name, AN |
| 16 | nachtmagier | Nachtmagier | 45.000 | Trank der Nachtsicht | Blindheit 3 s an Gegner r=6, 2 Nutzungen | B Name (Einzelquelle), AN |
| 17 | hexe | Hexe | 35.000 | 2 Wurftränke Schaden, 2 Heilung | – | B Name |
| 18 | goldsucher | Goldsucher | 20.000 | Goldspitzhacke Effizienz II | passiv: Erz-Abbau gibt doppelt | B Name, AN |
| 19 | bienen | Bienen | 30.000 | Honigflasche ×2 | Bienen-Schwarm (3 Bienen) 1 Nutzung | B Name (Changelog), AN |
| 20 | grillmeister | Grillmeister | 18.000 | 32 Gebratenes Fleisch, 32 Gebratenes Hähnchen, 12 Öfen, Feuerzeug (Haltbarkeit 10) | – | B (Fan-Liste) |
| 21 | zauberer | Zauberer | 35.000 | 32 Erfahrungsfläschchen, 1 Zaubertisch | – | B |
| 22 | crafter | Crafter | 18.000 | 64 Werkbänke, 32 Ambosse, 64 Stöcke, 5 Eisen, 5 Diamanten, 5 Kohle | – | B |
| 23 | assassine | Assassine | 75.000 | Eisenschwert Schärfe I, Unsichtbarkeitstrank 30 s | – | B Name/Preisbereich, Inhalt AN |
| 24 | creeper | Creeper | 75.000 | 5 TNT, 2 Creeper-Spawneier | – | B Name/Preisbereich, AN |
| 25 | enderman | Enderman | 50.000 | 2 Enderperlen | – | B Name („schnell zur Mitte“) |
| 26 | angler | Angler | 20.000 | Angel Haltbarkeit 30 | – | B Name |
| 27 | miner | Miner | 25.000 | Eisenspitzhacke Effizienz II, 32 Stein | – | B Name |
Legacy (nur mit „Frühere Kits“ an, E17/E19): `pirat`, `mushroom` (100.000), `maurer` (5.000), `pyro` (30.000), `bandit` (entfernt 2023) – Inhalte AN.

Kit-Regeln: (1) Kit-Items unzerbrechlich nur wo „Haltbarkeit X“ angegeben; (2) Fähigkeits-Item mit Rechtsklick, Anzeige 4.6; (3) in der Schutzzeit gesperrt (**B**); (4) Opfer-Kit wird bei Kill an alle gemeldet (**B**); (5) `random`-Wahl erlaubt (AN); (6) ohne Wahl: zuletzt gewähltes, sonst Starter.

#### 6.2.6 Challenges (pro Runde 1 zufällig, **B** Existenz/Meldung)
| id | Text (unser) | Bedingung | XP |
|---|---|---|---|
| no_sword | Gewinne diese Runde, ohne ein Schwert zu benutzen | Sieg ∧ kein Schwerttreffer/-halten im Kampf | 50 |
| kills_3 | Erziele in dieser Runde 3 Kills | Kills ≥ 3 | 50 |
| no_armor | Gewinne ohne Rüstung zu tragen | Sieg ∧ nie Rüstung angelegt | 50 |
| chests_10 | Öffne 10 Truhen | ≥ 10 | 30 |
| no_mid | Gewinne, ohne die Mitte zu betreten | Sieg ∧ nie in Mid-Region | 50 |
Scheitern sofort melden (`sw.challenge.failed`, **B**), Erfolg am Rundenende (`sw.challenge.done`). Achievement „Overkill“: Sieg mit ≥ 6 Kills (**B**; unser Name „Übermacht“).

#### 6.2.7 Classic SkyWars & Ranked
Classic (`skywars_classic`): `LEGACY_1_8`, keine Fähigkeiten-Kits (nur Item-Kits 20–27 + Legacy), keine Events/Challenges, Board legacy (`Map`, `Kills`, `Spieler`, `Kit`), Team-Nametag `T{n}`. Ranked (Option): Elo Start 0, Kill +10, Sieg +20, Einsatz je Liga zu Rundenbeginn (Holz 0 … Legende 20, AN), Killstreak-Boni 4/6/10 Kills je +10, 15 Kills +40, Platz 1/2/3 +30/+10/+10 (**B** historisch Season 1).

---

## 7. Nachrichten-Katalog

`{p}` = `{prefix}` (s. §3). `{tc}` = Teamfarbe, `{team}` = Teamname. Namen von Spielern werden in Teamfarbe (BedWars) bzw. Rangfarbe (SkyWars) eingefärbt. Beleg: b = belegt, a = abgeleitet, A = Annahme.

### 7.1 BedWars

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| `bw.join` | `§7» {rc}{name} §7hat die Runde betreten` | gleich (funktional) | `» X hat die Runde betreten` | b |
| `bw.quit.lobby` | `§7« {rc}{name} §7hat die Runde verlassen` | gleich | `« X hat die Runde verlassen` | b |
| `bw.join.online` | `{p}§7Spieler online: §a{cur}/{max}` | gleich | `Spieler online: 8/8` (E24) / ohne „:“ (E25) | b |
| `bw.join.map` | `{p}§eMap: {map} von {brand}-Buildteam ({builder})` | gleich | `Map: Dragons von GommeHD.net-Buildteam (BitConi)` | b |
| `bw.tip.armor` | `{p}§7Tipp: Ein Kauf von §eRüstung §7ersetzt nur deinen Brustpanzer` | eigen | „Wenn man die Rüstung verbessert, dann ändert sich nur dein Brustpanzer“ | b |
| `bw.tip.copper` | `{p}§7Kein Kupfer, aber Eisen übrig? Hol dir §eHolzbretter §7oder §eLeitern§7!` | eigen | „Du hast kein Kupfer, aber 5 Eisen? Dann kauf dir …“ | b (teilw.) |
| `bw.teaming` | `§cTeaming mit anderen Teams ist hier §4§lVERBOTEN §cund wird gebannt!` | eigen | SkyWars-Text (s. sw.teaming) | a |
| `bw.team.joined` | `{p}§7Du bist jetzt in {tc}Team {team}§7!` | eigen | `Du bist nun in Team <Team>!` (E17) | b |
| `bw.team.full` | `{p}§cDieses Team ist bereits voll.` | eigen | – | A |
| `bw.party.split` | `{p}§cFür deine Party war kein gemeinsames Team frei. Ihr spielt §4nicht §cim selben Team.` | eigen | `Es konnte kein passendes Team gefunden werden. Die Party ist NICHT im selben Team.` | b |
| `bw.countdown` | `{p}§7Die Runde beginnt in §e{n} §7Sekunde(n)` | gleich | `Die Runde beginnt in 40 Sekunden` (§7/§e) | b |
| `bw.countdown.abort` | `{p}§cZu wenige Spieler – der Countdown wurde gestoppt.` | eigen | – | A |
| `bw.start` | `{p}§7Die Runde beginnt!` | gleich | `[Bedwars] Die Runde beginnt!` (2024) / `Das Spiel beginnt` (E17) | b |
| `bw.kill` | `{p}{tcV}{victim} §7wurde von {tcK}{killer} §7getötet` | gleich | `X wurde von Y getötet` | b |
| `bw.death.you` | `{p}§7Du wurdest von {tcK}{killer} §7getötet` | gleich | `Du wurdest von X getötet` | b |
| `bw.kill.you` | `{p}§7Du hast {tcV}{victim} §7getötet §a(+{xp} XP)` | gleich | `Du hast X getötet (+25 XP)`; E24 `(+5 Punkte)` | b |
| `bw.died` | `{p}{tc}{name} §7ist gestorben` | gleich | `X ist gestorben` | b |
| `bw.respawn.in` | `{p}§7Du wirst in §e{n} §7Sekunde(n) wiederbelebt` | gleich | `Du wirst in 5 Sekunden wiederbelebt` | b |
| `bw.title.respawn` | `§cRespawn in §e{n} §cSekunde(n)` | gleich | `Respawn in 1 Sekunde` | b |
| `bw.bed.destroyed.self` | `{p}§cDas Bett deines Teams wurde zerstört!` | gleich | dito (E17: „…abgebaut!“) | b |
| `bw.bed.destroyed` | `{p}§cDas Bett von {tc}Team {team} §cwurde von {tcK}{name} §czerstört!` | gleich | dito (einmal „!!“) | b |
| `bw.bed.destroyed.you` | `{p}§7Du hast das Bett von {tc}Team {team} §7zerstört! §a(+{xp} XP)` | gleich | `Du hast das Bett von Team Blau zerstört! (+10 XP)` | b |
| `bw.bed.own` | `{p}§cDein eigenes Bett kannst du nicht abbauen.` | eigen | – | A |
| `bw.bed.suddendeath` | `{p}§cDie Zeit ist um – alle Betten wurden zerstört!` | eigen | – | A |
| `bw.team.remaining` | `{p}{tc}Team {team} §7hat noch §e{n} §7Spieler` | gleich | `Team Rot hat noch 3 Spieler` | b |
| `bw.team.eliminated` | `{p}{tc}Team {team} §cwurde vernichtet!` | gleich | dito | b |
| `bw.leave.ingame` | `{p}{tc}{name} §7hat die Runde verlassen.` | gleich | dito | b |
| `bw.resources.transfer` | `{p}§7Dir wurde{n} {list} §7übertragen` · `{list}` = `§c{k} Kupfer§7, §f{e} Eisen§7, §6{g} Gold§7, §b{d} Diamant(en)` (nur Werte > 0) | gleich | `Dir wurde 160 Kupfer, 4 Eisen, 3 Gold, 8 Diamanten übertragen` | b |
| `bw.chest.loot` | `{p}§7Du hast den Inhalt der {tc}Team-Truhe §7von {tc}Team {team} §7erhalten` | eigen | (Mechanik news #139) | A |
| `bw.spawner.tier` | `{p}§7Die Spawnrate von {resColor}{res} §7wurde auf §cStufe {n} §7erhöht` | gleich | `Die Spawnrate von Gold wurde auf Stufe 2 erhöht` | b |
| `bw.upgrade.self` | `{p}§7Du hast das §c{upgrade} Upgrade §7auf §eLevel {n} §7freigeschaltet` | gleich | `Du hast das Waffen Upgrade auf Level 1 freigeschaltet` | b |
| `bw.upgrade.team` | `{p}{tc}{name} §7hat das §d{upgrade} Upgrade §7auf §eLevel {n} §7freigeschaltet` | gleich | `X hat das Basis-Effekte Upgrade auf Level 1 freigeschaltet` | b |
| `bw.trap.bought` | `{p}{tc}{name} §7hat die §f{trap} §7gekauft` | gleich | `X hat die Blitz-Falle gekauft` | b |
| `bw.trap.triggered` | `{p}§cDeine §f{trap} §cwurde von {tcI}{name} §causgelöst!` | eigen | – | A |
| `bw.shop.missing` | `{p}§cDir fehlen noch §e{n} {res}§c, um §e{item} §czu kaufen` | eigen (Struktur gleich) | `Du benötigst noch 1 Kupfer um dir Farbiger Ton zu kaufen` | b |
| `bw.shop.nores` | `{p}§cDu hast nicht genügend Ressourcen` | gleich | dito | b |
| `bw.shop.maxlevel` | `{p}§cDu hast bereits die höchste Stufe.` | eigen | – | A |
| `bw.item.cooldown` | `{p}§7Du kannst das Item erst in §e{n} §7Sekunde(n) wieder benutzen` | gleich | dito | b |
| `bw.spawnroom` | – (still abbrechen) | – | – | A |
| `bw.win` | `{p}{tc}Team {team} §7hat gewonnen!` | gleich | `Team Violett hat gewonnen!` | b |
| `bw.title.win` | `{tc}Team {team}` / `§7hat die Runde gewonnen` | gleich | `Team Grün hat die Runde gewonnen` | b |
| `bw.end.restart` | `{p}§cDer Server startet in §e{n} §cSekunden neu` | gleich | dito | b |
| `bw.spectate.tp` | `§7{name} wurde zu {target} teleportiert` | gleich | dito (ohne Präfix) | b |
| `bw.spectator` | `{p}§7Du bist jetzt Zuschauer.` | eigen | `Du bist nun Spectator` (E17) | b |
| `bw.goldvote.voted` | `{p}§7Du hast für §6{rate} §7gestimmt.` | eigen | – | A |
| `bw.goldvote.saved` | `{p}§7Deine Gold-Wahl wurde gespeichert.` | eigen | Mechanik `/goldvote save` | a |
| `bw.goldvote.result` | `{p}§7Gold-Spawnrate dieser Runde: §6{rate}` | eigen | – | A |
| `bw.leaver.transfer` | = `bw.resources.transfer` | – | news #139 | b |
| `bw.chat.all` | `§7[@all] {clan}{tc}{name}§7: §f{msg}` | gleich | `[@all] [3IX] Dreix31: sicher?` | b |
| `bw.chat.team` | `{clan}{tc}{name}§7: §f{msg}` | gleich | Teamchat ohne Präfix | a |
| `bw.chat.dead` | `§7[§c✗§7] {clan}§7{name}§7: §f{msg}` | gleich | `[✗] [GOMME] GommeHD: gg` | b |
| `bw.forcemap` / `game.start.*` | s. §3 | eigen | – | A |
| `bw.round.header` … | Rundenblock Spec 02 `round.*` | – | `-= Statistiken dieser Runde =-` | b |

### 7.2 SkyWars

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| `sw.join` / `sw.quit.lobby` | wie `bw.join` / `bw.quit.lobby` | gleich | `» / «` | b |
| `sw.teaming` | `§cTeaming ist in diesem Modus §4§lVERBOTEN §cund führt zu einem Bann!` | eigen | `Teams sind auf diesem Server VERBOTEN und werden mit einem Ban bestraft!` | b |
| `sw.tip.shield` | `{p}§7Mit einer Axt kannst du gegnerische Schilde zerbrechen` | eigen | `Schilde können mit Äxten zerstört werden` | b |
| `sw.tip.xp` | `{p}§7Kills, Siege, Spielzeit und Challenges bringen dir Erfahrung für dein Level` | eigen | `Kills, Gewonnene Spiele, Spielzeit und Challenges geben dir Erfahrungspunkte, um im Level aufzusteigen` | b |
| `sw.join.online` | `{p}§7Spieler online: §b{cur}/{max}` | gleich | `Spieler online: 16/16` | b |
| `sw.join.map` | `{p}§eMap: {map} von {brand} Buildteam ({builder})` | gleich | `Map: Maya von GommeHD.net Buildteam (DerAutist)` | b |
| `sw.countdown` | `{p}§7Die Runde beginnt in §e{n} §7Sekunde(n)` | gleich | dito | b |
| `sw.team.joined` | `{p}§7Du bist jetzt in §fTeam #{n}§7.` | eigen | `Du wurdest zu Team #<n> hinzugefügt` | b |
| `sw.teleported` | `{p}§7Alle Spieler wurden zu ihren Inseln teleportiert` | gleich | dito (E17: „auf ihre Insel“) | b |
| `sw.kit.announce` | `{p}§7{name} nutzt das §e[{kit}]§7-Kit` | gleich | `GommeHD nutzt das [Engel]-Kit` | b |
| `sw.kit.selected` | `{p}§7Du hast das Kit §a{kit} §7gewählt` | eigen | `Du hast das Kit <Kit> ausgewählt` | b |
| `sw.kit.bought` | `{p}§aDu hast das Kit §e{kit} §afür §e{price} Coins §agekauft` | eigen | – | A |
| `sw.kit.nocoins` | `{p}§cDir fehlen §e{n} Coins§c für dieses Kit` | eigen | – | A |
| `sw.phase.build` | `{p}§7Die Aufbauphase beginnt!` | gleich | dito | b |
| `sw.protection` | `{p}§7Die Schutzzeit endet in §e{n} §7Sekunde(n)` | gleich | dito | b |
| `sw.phase.fight` | `{p}§eDie Aufbauphase ist vorbei, der Kampf beginnt` | gleich | dito | b |
| `sw.border.in` | `{p}§7Die §bBorder §7startet in §e{n} Sekunden` | gleich | `Die Border startet in 600 Sekunden` | b |
| `sw.border.twoteams` | `{p}§7Die §bBorder §7startet in §e{n} Sekunden§7, da nur noch 2 Teams am Leben sind` | gleich | dito | b |
| `sw.border.start` | `{p}§cDie Border bewegt sich jetzt!` | eigen | – | A |
| `sw.event` | `{p}§7Globales Event: §a{event} §7(Stufe {n})` | gleich | `Globales Event: Chestrefill (Stufe 1)` | b [Bild] |
| `sw.kill.kit` | `{p}{rcV}{victim} §7hatte das §e[{kit}]§7-Kit` | gleich | `X hatte das [Elytra]-Kit` | b |
| `sw.kill` | `{p}{rcV}{victim} §7wurde von {rcK}{killer} §7getötet §a(+{xp} XP)` (XP nur für Killer-Ansicht) | gleich | `X wurde von Y getötet (+20 XP)` | b |
| `sw.death` | `{p}{rc}{name} §7ist gestorben` | gleich | dito | b |
| `sw.life` | `§eLeben von {name}: §c{full×♥}§f{empty×♡}` (10 Symbole, halbe Herzen gerundet ab) | gleich | `Leben von X: ♥♥♥♥♥♡♡♡♡♡` | b |
| `sw.remaining` | `{p}§e{n} §7verbleibende Spieler` | gleich | dito | b |
| `sw.challenge.failed` | `{p}§7Deine Challenge für diese Runde ist §cgescheitert` | gleich | dito | b |
| `sw.challenge.done` | `{p}§7Deine Challenge für diese Runde ist §ageschafft §a(+{xp} XP)` | eigen | – | A |
| `sw.bossbar.challenge` | `§6Challenge: §7{text}` | gleich (Texte eigen, 6.2.6) | `Challenge: Gewinne diese Runde ohne ein Schwert zu verwenden` | b |
| `sw.ability.ready` | `§7Kit-Fähigkeit: §aAb jetzt nutzbar §a[{n} Nutzungen]` | gleich | dito | b |
| `sw.ability.cooldown` | `§7Kit-Fähigkeit: §cNutzbar in §e{s} Sekunden` | gleich | dito | b |
| `sw.ability.protection` | `§7Kit-Fähigkeit: §6Nicht in der Schutzzeit nutzbar` | gleich | dito | b |
| `sw.ability.uses` | `§a{n} Nutzungen!` | gleich | `3 Nutzungen!` | b |
| `sw.ability.empty` | `§7Kit-Fähigkeit: §cKeine Nutzungen mehr` | eigen | – | A |
| `sw.win` | `{p}§a{name} hat SkyWars gewonnen` (Team: `§aTeam #{n} hat SkyWars gewonnen`) | gleich | `X hat SkyWars gewonnen` | b |
| `sw.title.win` | `§6{name}` / `§fhat die Runde gewonnen` | gleich | dito | b |
| `sw.title.start` | `§bSkyWars` / `§f{map}` | gleich | dito | b |
| `sw.end.restart` | `{p}§cDer Server startet in §e{n} §cSekunden neu` | gleich | dito | b |
| `sw.chat.lobby` | `§7[{lvlColor}{lvl}§7] {rc}{name}§7: §f{msg}` | gleich | `[38] PlohxMops: GG` | b |
| `sw.chat.dead` | `§7[§c✗§7] …` | gleich | `[✗] …` | b |
| `sw.coins` | `{p}§7Du hast §e{n} §7Coins` (Spec 02) | gleich | `Du hast <x> Coins` | b |
| `sw.mapvote` | `{p}§7Du hast für §6{map} §7gestimmt` | eigen | – | A |
| `sw.mapvote.result` | `{p}§7Gespielt wird: §e{map}` | eigen | – | A |
| `sw.achievement.overkill` | Toast `§c§lÜbermacht` / `§fGewinne mit mindestens 6 Kills` | eigen | `OVERKILL – Win this round with at least 6 kills` | b |
| `sw.round.*` | Rundenblock-Zeilen `§7 Geöffnete Truhen: §e{n}`, `§7 Platzierte Blöcke: §e{n}`, `§7 Challenge: {§a✓|§c✗}` (Rest Spec 02) | gleich | dito | b |

### 7.3 Gemeinsam (aus Spec 02 genutzt)
`coins.round` / `coins.boost` (`[CoinSystem] +{n} Coins`, Boost-Zeile, Spec 02), `stats.*`, `round.*`, `prefix.mode` – nicht doppelt definieren. **Rahmen-Texte:** Wo ein `bw.*`/`sw.*`-Key einem `common.*`-Key aus Spec 03 §7.2 entspricht (`join`, `quit.lobby`, `countdown`, `countdown.abort`, `kill`, `death.you`, `died`, `team.full`, `team.joined`, `party.split`, `team.eliminated`, `win`, `end.restart`, `spectator`, `remaining`, `life`, `kill.kit`), gilt der `common.*`-Key; der Modus-Key bleibt nur als Override mit dem hier belegten Wortlaut/Farbwert (z. B. `bw.join.online` `§a`).

**Zählung:** 53 BedWars-Zeilen + 44 SkyWars-Zeilen = **97 Katalogzeilen** (inkl. Titles/Actionbar/Bossbar; einzelne Zeilen bündeln 2 Keys).

---

## 8. Schnittstellen

### 8.1 Genutzt (andere Bereiche liefern)

| Schnittstelle | Anbieter | Signatur | Nutzung |
|---|---|---|---|
| Rundenablauf, Phasen-Timer | game-framework | `GameLifecycle`, `PhaseTimer.start(id, Duration, IntConsumer, Runnable)`, `announceAt(int…)` | Countdown, Schutzzeit, Border, Generatorstufen |
| Teams | game-framework (Spec 03) | `TeamAllocator.allocate(List<Group>, List<TeamSlot>)` (Strategie PREFER_FILLED als Option) | 6.1.2 |
| Zuschauer | game-framework.spectate | `SpectatorService.makeSpectator(Player)` + Teleporter-GUI | Final Death, SkyWars-Tod |
| Loot | game-framework.loot | `LootTable.load(yaml)`, `fill(Inventory, Random, Guarantees)` | 6.2.4 |
| Kits | game-framework.kit (aus Spec 05 verallgemeinert) | `KitRegistry.of("skywars")`, `KitOwnershipService.owns/buy/select` | 5.9, 6.2.5 |
| Achievements | game-framework.achievement | `AchievementService.unlock/progress` | 6.1.5, 6.2.6 |
| Kampfprofil | game-framework | `CombatProfile.MODERN` (+ `offhandLocked`), `LEGACY_1_8` | Varianten |
| UI | paper-core (Spec 02) | `BoardService`, `TabService.registerEntryProvider`, `NametagService`, `ActionbarService`, `TitleService`, `BossbarService`, `HologramService`, `GuiService`, `HotbarService` | Abschnitt 4/5 |
| i18n / Chat | core-i18n, `ChatService` | `I18n.render(...)`, `ChatService.registerContext(ChatContextProvider)` (Team, `@all`, `[✗]`, Level) | Abschnitt 7 |
| Stats | paper-core | `StatsService.submit(MatchResult{mode, variant, map, players[]{uuid,kills,deaths,win,xp,survived,extra{beds | chests_opened,blocks_placed}}})` | Rundenende |
| Coins | Kern | `CoinService.payoutRound(matchId, base, players)`, `debit(uuid, n, idem)` | Belohnung, Kit-Kauf |
| Party/Clan | velocity-core/core-data | `PartyService.of(uuid)`, `ClanService.tag(uuid)` | Teamwahl, Tab, Chat |
| Queue | services/controller | `net:queue` Request, Server-Rolle `GAME` | Join, „Nächste Runde“ |

### 8.2 Angeboten (Bukkit-Events, Paket `net.retromc.bedwars.event` / `net.retromc.skywars.event`)

| Event | Felder | Zweck |
|---|---|---|
| `BedDestroyEvent` (cancellable) | `Team team, Player destroyer, Location bed` | Stats, Achievements, Clan-War |
| `TeamEliminatedEvent` | `Team team, @Nullable Player lastKiller` | Truhen-Loot, Board |
| `FinalKillEvent` | `Player victim, @Nullable Player killer, Team team` | Stats (Final Kills) |
| `ResourceTransferEvent` | `UUID from, UUID to, Map<Resource,Integer> amounts, Cause(KILL,LEAVE,CHEST)` | Meldung, Logging |
| `GeneratorTierChangeEvent` | `Resource res, int tier, boolean max` | Board, Chat |
| `ShopPurchaseEvent` (cancellable) | `Player, ShopItem, Map<Resource,Integer> price` | Ranked-Regeln, Logging |
| `TeamUpgradeEvent` | `Team, UpgradeType, int level, Player buyer` | Effekte, Chat |
| `TrapTriggerEvent` (cancellable) | `Team owner, TrapType, Player intruder` | Sneaker-Item, Chat |
| `KitAbilityUseEvent` (cancellable) | `Player, KitId, int usesLeft` | SkyWars-Kits |
| `ChestRefillEvent` (bestehend, Spec 05) | `int refillNo` | SkyWars-Events |
| `BorderStartEvent` | `Cause(TIME,TWO_TEAMS), int durationS` | Board, Chat |
| `ChallengeResultEvent` | `Player, ChallengeId, boolean success` | XP, Achievements |
| `GamePhaseChangeEvent`, `PlayerEliminatedEvent`, `GameEndEvent` | (Framework, Spec 03 §8.1) | gefeuert von beiden Modi |

Services (eigene): `ShopCatalog` (`shop.yml` → `List<ShopCategory>`), `QuickshopService` (`load/save(uuid)`, Cache `bw:qs:*`), `ShopSettingsService`, `UpgradeService`, `TrapQueue`, `GeneratorService` (Takt, Stufenplan aus `generators.yml`), `GlobalEventService` (SkyWars, `events.yml`), `ModeLevelService` (`addXp(uuid, mode, xp) → LevelUp?`, Cache `lvl:*`).

Messaging: `net:server-state` `SERVER_STATE` (Felder §2.2, Spec 08), `net:game` `GAME_ENDED` (Spec 03 §8.4). Clan-War (Spec 05) startet BedWars-Runden mit `variant` `4x4`, Teamnamen der Clans (AN) – Schnittstelle `ClanWarHook.onRoundEnd(winnerTeam)`.

### 8.3 Konfiguration (`plugins/${brandName}BedWars/`)
`config.yml` (Timer, Varianten, `era: E26|legacy`, `board.mapColon`, XP/Coins), `shop.yml` (6.1.6), `upgrades.yml` (6.1.7), `generators.yml` (6.1.3), `maps/<map>/map.json` (6.1.8). SkyWars: `config.yml`, `kits/skywars.yml`, `loot/*.yml`, `events.yml`, `challenges.yml`, `maps/<map>/map.json` (Inseln, Truhen mit `tier`, Mitte-Region, `voidY`, Border-Zentrum/-Radius). `tools:map-exporter` prüft Pflichtfelder.

---

## 9. Abnahmekriterien

**BedWars**
1. Board Ingame entspricht 4.2: bei 8x1 Kopf-Scores 7..2, bei 4x4 10..5, bei 9x5 11..6; Teamzeilen-Score = lebende Spieler; ✔/✘ wechselt in ≤ 1 Tick nach Bettabbau.
2. Generator-Timer: bei Rundenzeit 03:00 zeigt das Board `Diamant II 02:00` und `Gold II 07:00`; bei 12:00 `Diamant III 03:00`/`Gold III 13:00`; ab 15:00 `Diamant III Max.`; ab 25:00 beide `Max.`.
3. Kupfer spawnt 60 ± 1 Items/min, Eisen 7–8/min an jedem Basis-Spawner (Stufe I); Gold/Diamant stoppen bei Cap.
4. Kill überträgt alle vier Ressourcen des Opfers; Chatzeile listet nur Werte > 0, Kommas korrekt.
5. Final Kill: Opfer Zuschauer, `Team X hat noch N Spieler`, bei 0 `Team X wurde vernichtet!` + Team-Truhe an Killer.
6. Start nur bei ≥ ⌈max/2⌉ Spielern; Unterschreitung stoppt Countdown.
7. Shop: Kauf zieht exakten Preis ab; fehlende Ressourcen → Meldung mit Restbetrag; Schnellkauf-Änderung bleibt nach Reconnect (DB) erhalten.
8. Respawn genau 5 s; Hotbar-Slot von Schwert/Spitzhacke unverändert.
9. Offhand-Slot nicht belegbar; 1.9-Cooldown aktiv (Angriffsgeschwindigkeit Vanilla).
10. Stats: `MatchResult` enthält nur Final Kills/Deaths; Punkte = 5k+10b+50w in `stats.daily`.

**SkyWars**
11. Phasenfolge und Zeiten: Teleport → 3-2-1 → Aufbauphase/Schutzzeit 30 s (PvP-Schaden 0) → Kampf → Border-Ansage 600 s; bei 2 Teams 60 s.
12. Mitte-Truhen enthalten Schneebälle/Eier, Inseltruhen nie (1.000 Füllungen, Unit-Test).
13. Kill-Reihenfolge im Chat: `hatte das [Kit]-Kit` → `wurde von … getötet` → `Leben von …` (nur Opfer) → `N verbleibende Spieler`.
14. Kit-Fähigkeit in Schutzzeit gesperrt, Nutzungs- und Cooldown-Actionbar korrekt; Teufel-Cooldown 30 s.
15. Kit-Kauf ist idempotent (Doppelklick bucht einmal), Coins-Ledger und `games.kit_owned` konsistent.
16. Chestrefill-Event füllt alle Container neu und meldet `Globales Event: Chestrefill (Stufe 1)`.

**Allgemein**
17. Kein String „GommeHD“/„GOMMEHD“ in Code/Ressourcen (grep = 0).
18. Alle Keys aus Abschnitt 7 existieren in `de_DE` und `en_US`.

**Unit-Test-Ideen**
| # | Test | Art |
|---|---|---|
| U1 | `GeneratorSchedule.at(Duration)` liefert (Stufe, Rest) für 0:00, 4:59, 5:00, 14:59, 15:00, 24:59, 25:00 | Unit |
| U2 | `BoardScores.header(maxTeamSize)` = [s+6 … s+1]; Sortierung bei Gleichstand: eigenes Team, dann Index | Unit |
| U3 | `ResourceTransferFormatter` (nur >0, Plural „Diamanten“, Reihenfolge K/E/G/D) | Unit |
| U4 | `Purchase.tryBuy(inv, price)` Grenzfälle: exakt, 1 zu wenig, mehrere Stacks | Unit |
| U5 | `TeamAssigner` PREFER_FILLED: 3 Solos + Party(3) in 4x4 | Unit |
| U6 | `TrapQueue` max 3, Sperre 30 s, Schwächefalle −1 Level, Rückkehr nach 60 s | Unit |
| U7 | `LootTable` Garantien je Insel (Schwert, Rüstung, Blöcke, Essen) | Unit, 10.000 Seeds |
| U8 | `LifeFormatter` 9,5 Herzen → `♥♥♥♥♥♥♥♥♥♡` | Unit |
| U9 | `PointsFormula` bedwars/skywars (Spec 02 Datenpunkte) | Unit |
| U10 | Rundenende-Transaktion idempotent (gleiche `match_id` 2× → 1 Buchung) | Testcontainers |

---

## 10. Offene Punkte (je mit DEFAULT-ENTSCHEIDUNG)

| # | Offener Punkt | Widerspruch / Lücke | DEFAULT-ENTSCHEIDUNG |
|---|---|---|---|
| 1 | Gold-Takt Stufe I | Guide 35 s vs. Forum 40 s | 35 s (Guide offiziell) |
| 2 | Takte Stufe II/III, Caps | nicht belegt | Gold 25/15 s, Diamant 20/15 s; Caps Kupfer 64, Eisen 16, Gold 8, Diamant 4 |
| 3 | Vollständige Preisliste | nur ~25 Preise belegt, Rest AN (6.1.6) | Tabelle 6.1.6 als `shop.yml`, nach Sichtung weiterer Frames anpassen |
| 4 | Farbiger Ton | 1 Kupfer (E24) vs. 8 Kupfer (E26) | 16 Stück für 8 Kupfer; `legacy`: 1 Stück je 1 Kupfer |
| 5 | Portal-Preis | 8→4 (#117) vs. 6→4 (#131) | 4 Gold |
| 6 | Federfall II | 6 vs. 7 Diamanten | 6 |
| 7 | Kategorienamen/Glasfarbe Shop | nur „Explosives“ und Icons belegt; Glas violett vs. hellblau | Namen 5.1; Glas violett bei Schnellkauf, sonst hellblau |
| 8 | Upgrade-Shop-Layout, Fallenkosten/-wirkung | nicht belegt | 5.2 / 6.1.7 |
| 9 | „Upgrade-Level bis 5“ (news #139) | worauf bezogen unbekannt | Upgrade „Ressourcen“ L1–5 |
| 10 | Lobby-Countdown-Länge | nur 40 s als höchster Wert gesehen | 60 s, verkürzt auf 10 s bei vollem Server |
| 11 | Board-Doppelpunkt | `Map Fairy` vs. `Map: Tipis`; SkyWars `Map` vs. `Map:` | ohne Doppelpunkt; Optionen `board.mapColon`/`labelColon` |
| 12 | Team-Präfix im Chat `[BedWars]` | Klammern §7 vs. §f | Spec 02: §f |
| 13 | Kill-Belohnungsanzeige | E24 `(+5 Punkte)` vs. E26 `(+25 XP)` | E26 `(+XP)`; `legacy`: Punkte |
| 14 | „!!“ bei Bettmeldung | einmal zwei Ausrufezeichen | ein „!“ |
| 15 | XP-/Coins-Formeln | nur Einzelwerte (25/10 XP, 20 XP SW) | 6.1.5 / 6.2.3, alles in `config.yml` |
| 16 | Wartelobby-Hotbar-Bedeutungen (Papier, Nether-Stern, Buch, Diamant, Komparator) | Positionen belegt, Funktionen nicht | 7/8/9 nach Spec 03 §4.10 (B N05); Papier = Map-Abstimmung; Komparator = Kit-Einstellungen |
| 17 | NPC-Modell | Villager (E24) vs. Zombie (E24/E25) | Items VILLAGER, Upgrades ZOMBIE_VILLAGER; pro Map überschreibbar |
| 18 | Team-Farben Magenta/Braun | Kollision Magenta/Rosa (§d) | Hex `#C040C0` / `#8B5A2B` |
| 19 | Unbesetzte Teams | Bett bleibt (✔, 0) | Bett bleibt, kein Spawner-Betrieb für leere Teams (spart Entities) |
| 20 | SkyWars-Kitpreise/-inhalte 2026 | Fan-Liste undatiert, neue Kits ohne Daten | Tabelle 6.2.5 (AN markiert) |
| 21 | SkyWars-Refill-Zeiten | nur „Chestrefill (Stufe 1)“ belegt | Stufe 1 @Kampf+5:00, Stufe 2 @Kampf+10:00 |
| 22 | Border-Verlauf | nur Startzeiten belegt | 180 s auf Radius 10, 1 Schaden/s |
| 23 | SkyWars-Loot-Gewichte | nicht belegt | 6.2.4 |
| 24 | Challenges-Liste | nur 1 Challenge belegt | 5 Challenges 6.2.6 |
| 25 | Teamvarianten SkyWars 2026 | offiziell 2/3/4, Queue-Liste unbelegt | `8x1`, `16x1`, `8x2`, `4x4` aktiv; `4x3` aus |
| 26 | Ranked BedWars/SkyWars | nur historisch/Thumbnails | Varianten vorhanden, Standard deaktiviert |
| 27 | Classic-Gold-Vote-Optionen | Mechanik belegt, Stufen nicht | Kein/60/30/15 s, Mehrheit, Gleichstand → Normal |
| 28 | BedWars-Achievements (5 neue 2025) | Namen unbelegt außer „Auf Distanz“ | „Auf Distanz“ (10 Bogen-Kills), „Bettenjäger“ (3 Betten in 1 Runde), „Ungeschlagen“ (Sieg ohne Tod), „Sparfuchs“ (Sieg ohne Gold-Kauf), „Händlerkönig“ (50 Käufe in 1 Runde), „Fallensteller“ (5 Fallen ausgelöst) |
| 29 | Paketname im Stub | erledigt: Code und Spec nutzen `net.retromc.*` (Marke RetroMC, Nutzer-Entscheidung); keine Umbenennung |
| 30 | Rundenhöchstdauer BedWars | nicht belegt | 60:00 → alle Betten weg, +10:00 → Unentschieden |
| 31 | Chat-Regel in 8x1 / Zuschauer | `[@all]` und `[✗]` belegt, Empfänger nicht | 8x1: alle Chats global; Tote nur an Tote/Zuschauer |
| 32 | Item-Wirkungen (Freezer, Sneaker, Bettbauer Plus, C4, Mobiler Shop) | Preise belegt, Verhalten nicht | Werte 6.1.6 Lore, Konstanten in `shop.yml` |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | Pakete `dev.netzwerk.*` mit Umbenennungs-Hinweis, `dev.netzwerk.tools.mapexporter`, Nodes `netzwerk.*` | `net.retromc.*` (Gerüst), `net.retromc.map.exporter`, Nodes `retromc.*`; Hinweise entfernt |
| K2 | Migration `V<n>__bedwars_skywars` ohne Nummer | `V4_1__bedwars_skywars.sql` |
| K3 | `/start`, `/forcemap` mit eigener Regel (≥ 2 Teams, nur Team) gegen Spiel-Rahmen | Spec 03 #4/#5 verbindlich |
| K4 | Scoreboard-Titel ohne `.NET`, Tab-Header ohne `.net`, Header 2 mit Original-Wortlaut | `§f§l{BRAND_UPPER}.NET`; `§e{brand}.net …`; Spec-02-Key `tab.header.report` |
| K5 | Wartelobby-Slots 7/8/9 (Extras/Erfolge/Statistiken) und Zuschauer-Hotbar abweichend von Spec 03 | Spec 03 §4.10/§4.11 (einheitlich in allen Modi) |
| K6 | `map.yml` und Map-Setup ohne Bezug zur Map-Pipeline | `map.json` + `mapschema/<mode>.json` (Spec 08 §6.11) |
| K7 | Status-Feld `players`, eigener Status-Takt | `online`, `SERVER_STATE` über `ServerLifecycle` (Spec 08) |
| K8 | `RoundEnded` vs. `GAME_ENDED` (Spec 03) | `GAME_ENDED` |
| K9 | `TeamService.assign`, `Messages.send`, `GameWinEvent`, `coins.reward` | `TeamAllocator`, `I18n`/`ChatService`, `GameEndEvent`, `coins.round`/`coins.boost` |
| K10 | Party-Suffix SkyWars ` §d[Party]` | ` §7[§dParty§7]` |
| K11 | `bw.*`/`sw.*` doppeln `common.*` (Spec 03) | `common.*` gilt, Modus-Keys nur als Override (§7.3) |
