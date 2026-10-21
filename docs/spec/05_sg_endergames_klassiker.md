# Spec 05 – SurvivalGames, EnderGames, TTT, Cores, MasterBuilders, ClanWar

Stand 2026-10-21. Quellen: Notiz 14 (komplett), Notiz 02 Q6–Q9 (SG-Teile), Notiz 03 §2/§3/§4/§7/§13/§14/§15, Video-Befunde `sweep/batch2_*.md` + `thumbs.md`, OCR `sweep/review/*.md`, 7 Einzelbilder (unten mit **[Bild]** markiert).

**Beleg-Kürzel:** **B** = belegt (Quelle genannt) · **A** = abgeleitet (Schluss aus Belegen) · **AN** = Annahme (frei festgelegt, Konfigwert).
**Ära-Kürzel:** **E26** = 2025/26 (Standard) · **E23** = 2023/24 (SG-Testphase, TTT 1.20) · **E19** = 2019–2021 · **E17** = 1.8-Ära 2014–2018 (Option „legacy“).
**Marke:** Wo das Original „GOMMEHD.NET“ zeigt, steht `{BRAND_UPPER}.NET` (aus `brandName`, Großbuchstaben, `§f§l` → `RETROMC.NET`). Modusnamen (SurvivalGames, EnderGames, TTT, Cores, MasterBuilders, ClanWar) sind Gattungsnamen und bleiben, über i18n umbenennbar.

**Wichtigster Neubefund dieser Spec:** EnderGames lief **2026 wieder** (Twitch-Clip 2026-07-28, [Bild] t0/t8/t48): 24 Spieler, Schutzzeit, danach „Deathmatch in 30:00“, Schneegolem-„Switcher“-Meldung, Kit-Zeile. SG lief Jan/Feb 2025 (Clips 2025-01-28, 2025-02-24). Für beide Modi ist damit eine E26-Ausprägung belegt.

---

## 1. Umfang & Module

| Modus | Gradle-Modul (settings.gradle.kts) | Status | Paket | Plugin-Name (paper-plugin.yml) | Einstieg | Kampfprofil |
|---|---|---|---|---|---|---|
| SurvivalGames (+ QuickSG-Preset) | `:game:survivalgames` | vorhanden (Stub) | `net.retromc.survivalgames` | `${brandName}SurvivalGames` | `SurvivalGamesPlugin` | `LEGACY_1_8` |
| EnderGames | `:game:endergames` | vorhanden (Stub) | `net.retromc.endergames` | `${brandName}EnderGames` | `EnderGamesPlugin` | `LEGACY_1_8` (E26-Bild zeigt 1.8-Optik, Version 1.8.9 belegt 2021) |
| TTT | `:game:ttt` | vorhanden (Stub) | `net.retromc.ttt` | `${brandName}TTT` | `TttPlugin` | `LEGACY_1_8` (Scoreboard E23/E26 „Hit-Cooldown: Deaktiviert“ **B**) |
| Cores | `:game:cores` | vorhanden (Stub) | `net.retromc.cores` | `${brandName}Cores` | `CoresPlugin` | `LEGACY_1_8` |
| MasterBuilders | `:game:masterbuilders` | vorhanden (Stub) | `net.retromc.masterbuilders` | `${brandName}MasterBuilders` | `MasterBuildersPlugin` | `MODERN` (kein Kampf) |
| ClanWar | `:game:clanwar` | vorhanden (Stub) | `net.retromc.clanwar` | `${brandName}ClanWar` | `ClanWarPlugin` | vom Ziel-Modus |
| ClanWar-Befehle/Herausforderung (Proxy) | `:platform:velocity-core` | vorhanden | `net.retromc.velocity.core.clanwar` | – (Teil von `${brandName}Core` Velocity) | `ClanWarCommand` | – |
| Gemeinsame Klassiker-Bausteine | `:game:game-framework` | vorhanden | `net.retromc.game.framework.{loot,deathmatch,spectate,achievement}` | eingebettet | – | – |

**Kein NEU-Modul nötig.** Alle sechs Modi haben Module. Neu sind nur Unterpakete (oben) und die Migration `db/migrations/V5_1__klassiker.sql` (Abschnitt 2).

**Abhängigkeiten je Modus-Plugin** (wie `game/survivalgames/build.gradle.kts`): `compileOnly(libs.paper.api)`, `compileOnly(project(":platform:paper-core"))`, `implementation(project(":game:game-framework"))`. Laufzeit: `${brandName}Core` mit `load: BEFORE`, `required: true`, `join-classpath: true`. Optional zur Laufzeit: OldCombatMechanics (Kampfprofil `LEGACY_1_8`). Eine Runde pro Server (GameState WAITING→STARTING→INGAME→ENDING, kein Rückweg).

**Varianten (modeId/variant in `stats.match`):**

| modeId | variant | Spieler | Teams | Ära | Beleg |
|---|---|---|---|---|---|
| `survivalgames` | `sg24` (Standard) | 24 | 12×2 („Team #n“) | E23/E26 | B: „Spieler online 22/24“ (2019), „Team #2“, Nametag „T4 \| …“ bei 2 Spielern; Teamgröße 2 = A |
| `survivalgames` | `sg16`, `sg8x2` | 16 | 16×1 / 8×2 | E17 | B (Notiz 02 Q6) |
| `survivalgames` | `sg64` | 64 | 64×1 | E17 | B (Achievement „SG 64“); Regeln AN = wie sg24 |
| `survivalgames` | `qsg` | 20 | Solo | E17 | B (Notiz 02 Q6) |
| `endergames` | `eg` | 24 | Teaming erlaubt (keine festen Teams) | E26 | B [Bild] „Spieler 24“, „Teams Erlaubt“ |
| `endergames` | `eg-speed` | 24 | wie `eg` | E17 | B (Videotitel), Regeln AN |
| `ttt` | `ttt12` (Standard) | 12 | Rollen | E23/E26 | B „12/12“ |
| `ttt` | `ttt24` | 24 | Rollen | E19 | B (Changelog „TTT24“), Option |
| `cores` | `cores16` | 16 | 2×8 (Rot/Blau) | E17–E19 | B [Bild] „Team Blau [8]“, Achievement „Cores 16“ |
| `masterbuilders` | `mb` | 12 (AN) | Solo | E26 | Spielerzahl AN |
| `clanwar` | `cw-<modus>` | 2×4 | Clan gegen Clan | E19 | B (4 vs 4) |

---

## 2. Datenmodell

### 2.1 PostgreSQL (neue Migration `V5_1__klassiker.sql`, neues Schema `games`)

Mitbenutzt: `stats.match`, `stats.match_player`, `stats.daily`, `stats.alltime` (Spec 02 – `stats.total`/`stats.monthly` aus V1 werden von `V2_1` gelöscht), `economy.wallet`, `economy.ledger`, `social.clan`, `social.clan_member`, `social.clan_elo`, `social.clan_war` (Spec 01; Clan-Elo **je Modus** in `social.clan_elo`, die V1-Spalte `social.clan.elo` ist deprecated). Schema `games` teilen sich Spec 03/04/05 (`CREATE SCHEMA IF NOT EXISTS games`).

| Tabelle | Spalten (Typ) | Schlüssel / Indizes | Zweck |
|---|---|---|---|
| `games.kit_owned` | `uuid UUID NOT NULL`, `mode VARCHAR(24) NOT NULL`, `kit_id VARCHAR(32) NOT NULL`, `price BIGINT NOT NULL`, `bought_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `(uuid, mode, kit_id)`; IDX `(mode, kit_id)` | Gekaufte Kits (EG; wiederverwendbar für SkyWars) |
| `games.kit_selected` | `uuid UUID`, `mode VARCHAR(24)`, `kit_id VARCHAR(32) NOT NULL` (`random` erlaubt) | PK `(uuid, mode)` | Zuletzt gewähltes Kit, vorbelegt in Wartelobby |
| `games.achievement` | `uuid UUID`, `mode VARCHAR(24)`, `achievement_id VARCHAR(48)`, `unlocked_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `(uuid, mode, achievement_id)`; IDX `(mode, achievement_id)` | Erfolge EG/SG/Cores. Entfällt, wenn Kern-Spec (Stats) eine gleichwertige Tabelle liefert |
| `games.achievement_progress` | `uuid UUID`, `mode VARCHAR(24)`, `achievement_id VARCHAR(48)`, `value BIGINT NOT NULL` | PK `(uuid, mode, achievement_id)` | Zähler über Runden (z. B. „Veteran 150 Siege“, „640 Holzstämme“) |
| `games.ttt_profile` | `uuid UUID PRIMARY KEY`, `karma INT NOT NULL DEFAULT 1000`, `traitor_passes INT NOT NULL DEFAULT 0 CHECK (>=0)`, `detective_passes INT … DEFAULT 0`, `innocent_passes INT … DEFAULT 0`, `updated_at TIMESTAMPTZ` | PK `uuid`; IDX `(karma DESC)` | Karma + Pass-Bestand |
| `games.ttt_pass_ledger` | `id BIGSERIAL PK`, `uuid UUID NOT NULL`, `pass VARCHAR(12) NOT NULL` (`traitor`/`detective`/`innocent`), `delta INT NOT NULL`, `reason VARCHAR(32) NOT NULL` (`vote_shop`,`store`,`use`,`refund`,`admin`), `match_id UUID NULL`, `idem_key TEXT UNIQUE`, `created_at TIMESTAMPTZ DEFAULT now()` | IDX `(uuid, created_at)` | Nachvollziehbarkeit, Doppelbuchungsschutz |
| `games.mb_theme` | `id BIGSERIAL PK`, `name VARCHAR(32) NOT NULL`, `status SMALLINT NOT NULL` (0 vorgeschlagen, 1 aktiv, 2 abgelehnt), `submitted_by UUID NULL`, `created_at TIMESTAMPTZ DEFAULT now()`, `score_sum INT DEFAULT 0`, `score_count INT DEFAULT 0` | UNIQUE `lower(name)`; IDX `(status)` | Themenpool (Start ≥ 400 aktive Begriffe, s. 6.5) |
| `games.mb_theme_rating` | `theme_id BIGINT REFERENCES games.mb_theme ON DELETE CASCADE`, `uuid UUID`, `value SMALLINT CHECK (value IN (-1,1))`, `created_at TIMESTAMPTZ DEFAULT now()` | PK `(theme_id, uuid)` | Bewertung von Vorschlägen am NPC |
| `games.cores_level` | `uuid UUID PRIMARY KEY`, `xp BIGINT NOT NULL DEFAULT 0`, `level INT NOT NULL DEFAULT 1` | IDX `(level DESC, xp DESC)` | Cores-Level (Inhalt AN, s. 6.4) |
| ~~`games.cw_match`~~ → `social.clan_war` (Spec 01) | Diese Migration ergänzt: `ALTER TABLE social.clan_war ADD COLUMN size SMALLINT, ADD COLUMN map VARCHAR(48), ADD COLUMN end_reason VARCHAR(16)` (`win`,`deathmatch`,`draw`,`abort`,`timeout`), `ADD COLUMN aborted_by BIGINT NULL, ADD COLUMN elo_a_before INT, ADD COLUMN elo_b_before INT`. Abbildung: `game`→`mode`, `fun`→`NOT ranked`, `id`→`match_id` (= `stats.match.id`), `elo_delta` (Nullsumme) | IDX wie Spec 01 + `(mode, ended_at DESC)` | ClanWar-Historie / ClanPage; Schreiben nur über `ClanService.recordWar` (Spec 01 §8.1) |
| `games.cw_ban` | `id BIGSERIAL PK`, `clan_id BIGINT NULL REFERENCES social.clan`, `uuid UUID NULL`, `until TIMESTAMPTZ NOT NULL`, `reason VARCHAR(32) NOT NULL`, `created_at TIMESTAMPTZ DEFAULT now()` | IDX `(clan_id, until)`, `(uuid, until)`; CHECK `clan_id IS NOT NULL OR uuid IS NOT NULL` | `/cw status` |

**Stat-Felder je Modus** (Spec 02 §2.1: `kills`, `deaths`, `games`, `wins`, `points` als Spalten in `stats.daily`/`stats.alltime`, alle anderen Schlüssel in `extra` JSONB; Modus-Seed in `stats.mode`):

| mode | keys |
|---|---|
| `survivalgames` | `games`, `wins`, `kills`, `deaths`, `chests_opened`, `deathmatches`, `points` |
| `endergames` | `games`, `wins`, `kills`, `deaths`, `kills_kit:<kit_id>` (Kit-Rangliste, B: „Kit-Top 5“), `enderchests_opened` |
| `ttt` | `games`, `wins`, `wins_traitor`, `wins_innocent`, `wins_detective`, `kills`, `deaths`, `traitor_kills`, `bodies_found`, `karma` (Spiegel) |
| `cores` | `games`, `wins`, `kills`, `deaths`, `cores_destroyed` (B: „Zerstörte Cores“) |
| `masterbuilders` | `games`, `wins`, `points`, `ratings_given` |
| `clanwar` | `cw_games`, `cw_wins` (pro Spieler); Clan-Werte in `social.clan_war`/`social.clan_elo` |

`stats.match_player.extra` (JSONB): SG `{"team":n,"damage_dm":x}`, EG `{"kit":"yeti","kits_used":[…]}`, TTT `{"role":"traitor","karma_delta":n,"points_spent":n}`, Cores `{"cores":n}`, MB `{"theme":"…","score":n}`.

### 2.2 Valkey

| Key / Kanal | Typ | TTL | Inhalt |
|---|---|---|---|
| `kits:<mode>:<uuid>` | SET | 1 h (beim Join neu geladen) | gekaufte Kit-IDs (Cache von `games.kit_owned`) |
| `ttt:pass:<uuid>` | STRING (`traitor`/`detective`/`innocent`) | bis Rundenende, max 30 min | eingelöster Pass für die aktuelle Runde (Server-lokal ausreichend; Valkey nur wegen Reconnect) |
| `mb:themes:active` | SET | 6 h | aktiver Themenpool |
| `cw:challenge:<fromUuid>:<toUuid>` | HASH {game, fun, size, created} | **60 s** (AN) | offene Herausforderung |
| `cw:match:<id>` | HASH {server, clanA, clanB, state, started} | 3 h | laufender ClanWar, für `/cw join` |
| `cw:clan:<clanId>:active` | STRING matchId | 3 h | höchstens ein aktiver CW pro Clan |
| `cw:abort:<clanId>` | ZSET (ts) | 24 h | Abbrüche für Sperrregel (6.6) |
| `lb:{mode}:30d` / `lb:{mode}:all` (Spec 02 §2.2) | ZSET | – | Bestenlisten (kein Monatsreset). Zusätzlich `lb:endergames:kills_kit:all` mit Member `<kit_id>` (Kit-Top 5) und `lb:clanwar:{mode}` = Sicht auf `social.clan_elo` |
| ~~`game:<server>:state`~~ | – | – | **entfällt** (Spec 08 W3): Status steht nur in `server:{name}` und wird über `ServerLifecycle` geschrieben |
| Kanal `net:clanwar` (**NEU** in `NetworkChannel`) | Pub/Sub | – | `ChallengeCreated`, `ChallengeAccepted`, `ClanWarStarted`, `ClanWarEnded`, `ClanWarAborted` |
| Kanal `net:server-state` | Pub/Sub | – | bestehend; Modi melden `WAITING/STARTING/INGAME/ENDING` |

---

## 3. Befehle

Recht-Präfix: `retromc.<modus>.<recht>`. „Alle“ = jeder Spieler ohne Rang. Fehlermeldungen mit Präfix des Modus (Abschnitt 7).

| Syntax | Aliase | Recht/Rang | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|
| `/kit [<kit>]` (EG) | `/kits` | Alle, nur WAITING/STARTING | Ohne Argument: öffnet GUI „Kit-Auswahl“. Mit Argument: wählt gekauftes Kit (`random` = Zufällig) | OK `eg.kit.selected`; Fehler `eg.kit.not_owned`, `eg.kit.unknown`, `common.not_in_lobby` |
| `/erfolge` (EG/SG/Cores) | `/achievements` | Alle, jede Phase | öffnet GUI „Erfolge“ des Modus | – |
| `/traitor` (TTT) | `/tpass` | Alle, nur WAITING/STARTING, Bestand ≥ 1 | löst Traitor-Pass für diese Runde ein (Abbuchung erst bei Rollenvergabe) | `ttt.pass.redeemed` {rolle}; Fehler `ttt.pass.none`, `ttt.pass.already`, `ttt.pass.limit`, `common.not_in_lobby` |
| `/detective` (TTT) | `/dpass` | wie oben | Detective-Pass | wie oben |
| `/innocent` (TTT) | `/ipass` | wie oben | Innocent-Pass (garantiert kein Traitor) – **AN**, Gegenstück zu den belegten Pässen | wie oben |
| `/shop` (TTT) | `/s` | Traitor/Detective, INGAME | öffnet Rollen-Shop (B: `/s`, `/shop`) | Fehler `ttt.shop.no_shop` (Innocent/Zuschauer) |
| `/t <Nachricht>` und Chat-Präfix `@t <Nachricht>` | – | nur Traitor | Team-Chat Traitor (B: `@t`, `/t`) | Format `ttt.chat.traitor`; Fehler `ttt.chat.not_traitor` |
| `/d <Nachricht>` und `@d <Nachricht>` | – | nur Detective | Team-Chat Detectives (**AN**, analog `@t`) | `ttt.chat.detective`; Fehler `ttt.chat.not_detective` |
| `/karma [<Spieler>]` | – | Alle | zeigt Karma | `ttt.karma.show`; Fehler `common.player_unknown` |
| `/finish` (MB) | `/fertig` | Spieler der Runde, nur Bauphase | Stimme „fertig“; wenn **alle** aktiven Spieler gestimmt haben → Bauphase endet sofort (B) | `mb.finish.voted` {n}/{max} an alle; `mb.finish.all`; Fehler `mb.finish.already`, `mb.finish.wrong_phase` |
| `/cw <Spieler> <Spiel>` | `/clanwar` | Party-Leiter, alle Party-Mitglieder im selben Clan (B), Größe = `size` | fordert Party des Ziels heraus; Ziel muss Clanmitglied und Party-Leiter sein (B). Spiel ∈ `/cw maps`-Liste der Spiele | `cw.challenge.sent`, beim Ziel `cw.challenge.received` mit Klick `[ANNEHMEN]` `[ABLEHNEN]`; Fehler `cw.err.not_leader`, `cw.err.mixed_party`, `cw.err.target_not_leader`, `cw.err.same_clan`, `cw.err.size`, `cw.err.banned`, `cw.err.active`, `cw.err.game_unknown` |
| `/cw accept <Spieler>` / `/cw deny <Spieler>` | Klick-Ziel | Party-Leiter (Ziel) | nimmt an / lehnt ab; bei Annahme Controller weist Server zu, beide Partys werden verschoben | `cw.challenge.accepted`, `cw.challenge.denied`, `cw.challenge.expired` |
| `/cw maps [<Spiel>]` | – | Alle | listet CW-fähige Spiele/Maps (B) | `cw.maps.header`, `cw.maps.line` |
| `/cw status` | – | Alle | eigener CW-Sperrstatus (B) | `cw.status.free` / `cw.status.banned` {bis} {grund} |
| `/cw abort` | – | Party-Leiter im laufenden CW | beendet Match (B); zählt als Niederlage des abbrechenden Clans (AN) | `cw.abort.done` an beide Teams; Fehler `cw.err.no_match` |
| `/cw join <Clantag>` | – | Alle | Zuschauen bei laufendem CW des Clans (B) | Fehler `cw.err.no_match_for_tag` |
| `/cw top` | – | Alle | Top 10 Clan-Elo (**AN**) | `cw.top.line` |
| `/fcw <Spieler> <Spiel>` | `/funclanwar` | Party-Leiter, kein Clan nötig (B) | wie `/cw`, aber `fun = true`, keine Elo | wie `/cw` mit `cw.fun.*` |
| `/start` | – | Spiel-Rahmen **Spec 03 #4**: Premium aufwärts (`premium`+), nur WAITING mit ≥ Mindestspieler | setzt Lobby-Countdown auf 10 s | `common.start.done`, `common.start.too_few` |
| `/stats [<Spieler>]` | – | Alle | Kern-Befehl, liefert Modus-Statistik (30 Tage) | Format Kern-Spec; Cores-Beispiel 6.4 |

**Befehle gesamt: 19** (ohne Rahmen-Befehle `/leave`, `/spec`).

---

## 4. Anzeige-Elemente

Allgemein (alle sechs Modi, **B** aus E17–E26-Bildern): Sidebar-Titel `§f§l{BRAND_UPPER}.NET`; je Eintrag **Label-Zeile** (`§f`) + **Wert-Zeile** (farbig), Einträge durch Leerzeile getrennt; rechts sichtbare **rote Vanilla-Scorezahlen** absteigend bis 0 (Standard sichtbar = 1:1; Option `scoreboard.hideNumbers`). Lobby-Countdown zusätzlich in der **XP-Level-Anzeige** (B SG 2019).

### 4.1 SurvivalGames

**Scoreboard INGAME (E23/E26, Standard)** – B (Clips 2023-10-20, 2024-01-30, 2024-12-22, 2025-01-28, 2025-02-24)

| # | Text | Farbe | Dynamik |
|---|---|---|---|
| 11 | `Map` | §f | – |
| 10 | `{map}` | §e | Mapname |
| 9 | ` ` | – | – |
| 8 | `Kills` | §f | – |
| 7 | `{kills}` | §c | eigene Kills (Zuschauer: bleibt) |
| 6 | `  ` | – | – |
| 5 | `Spieler` | §f | – |
| 4 | `{alive}` | §b | lebende Spieler |
| 3 | `   ` | – | – |
| 2 | `Refill` / nach letztem Refill `Deathmatch` (AN) | §f | – |
| 1 | `{mm:ss}` | §e | Countdown bis Refill bzw. DM |

**Scoreboard INGAME (E17, Option `legacy`)**: wie oben, davor `Teams` §f / `erlaubt` §a (B 2017 „Teams erlaubt“). **Wartelobby E19**: `Map` §f / `{map}` §e / leer / `Teams` §f / `erlaubt` §a (B). **Wartelobby E26 (AN)**: `Map`/`{map}`, `Spieler`/`{n}/{max}` §b, `Start`/`{mm:ss}` §e.

| Element | Inhalt | Beleg |
|---|---|---|
| Actionbar INGAME | `§7Team #{n}` dauerhaft | B |
| Actionbar Zuschauer | `§7Zuschauer` (+ `§7(Rechtsklick)` bei Kompass in der Hand) | B |
| Actionbar Lobby (E19) | `§aTeams erlaubt` | B (Option legacy) |
| Nametag | `T{team} \| {name} §7[§e{clantag}§7]` (Team-Nr. vor Namen) | B (Format), Farben A |
| Tablist | Kern-Format Spec 02 §4.4 (Header `§e{brand}.net §cSurvivalGames`, `tab.header.report`, `tab.footer.*`) | B (TTT-Bild, gleiches Netzwerk-Format), Modusname A |
| Titles | Start: Title `§aLos!` Subtitle `§7Viel Glück` (AN); Sieg: Title `§6Sieg!` (AN); DM-Countdown 3-2-1 als Title `§e{n}` (AN) | AN |
| Bossbar | keine | A (keine gesehen) |
| Hologramme | keine | A |
| Hotbar Wartelobby (E19) | Slot 9 Schwert-Item = Teamwahl (A: „Team wählen“) ; übrige leer | B (Slot 9 Schwert), Bedeutung AN |
| Hotbar Zuschauer | Slot 1 Kompass „Teleporter“ (→ GUI 5.8), Slot 7 Netherstern „Einstellungen“, Slot 8 Limettenfarbstoff „Nächste Runde“, Slot 9 weißes Item „Zurück zur Lobby“ | B (Items), Bedeutungen A/AN; Rahmen-Spec |
| Wartelobby-Welt | begehbares Dorf **auf** der Rundenmap (E19) | B |

### 4.2 EnderGames

**Scoreboard INGAME (E26, Standard)** – B [Bild] 2026-07-28 t8/t48, Clip 2024-11-28

| # | Text | Farbe | Dynamik |
|---|---|---|---|
| 14 | `Teams` | §f | – |
| 13 | `Erlaubt` | §a | konstant (Konfig `teaming=true`) |
| 12 | ` ` | | |
| 11 | `Kit` | §f | |
| 10 | `{kit}` | §d (E26-Bild magenta; Notiz 14: lila §5 → Option) | aktives Kit; Zuschauer: `Zuschauer` |
| 9 | `  ` | | |
| 8 | `Kills` | §f | |
| 7 | `{kills}` | §c | |
| 6 | `   ` | | |
| 5 | `Spieler` | §f | |
| 4 | `{alive}` | §b | |
| 3 | `    ` | | |
| 2 | `Schutzzeit` → nach Ende `Deathmatch in` → im DM `Deathmatch` (AN) | §f | |
| 1 | `{mm:ss}` | §e | Countdown |

Hinweis: Bild t48 zeigt Labels mit Doppelpunkt (`Kit:`), Bild t8 ohne. **Entscheidung:** ohne Doppelpunkt (Konfig `labelColon`).

**Scoreboard Wartelobby (E19, B Notiz 14 1.6):** `Biom` §f / `{biom}` §6 / leer / `Kit` §f / `{kit}` §d. Biom-Name englisch wie Vanilla-Biom-ID im Original („Swampland“) → wir zeigen deutschen Biom-Namen (AN).

| Element | Inhalt | Beleg |
|---|---|---|
| Actionbar Schutzzeit/INGAME | `§aTeaming ist erlaubt` (E17–E19); E26 nicht gesehen → Standard: nur während Schutzzeit (AN) | B/AN |
| Titles | Kitname beim Start: Title `§d{kit}` Subtitle `§7Dein Kit` (AN) | AN |
| Bossbar | keine | A |
| Hotbar Wartelobby | Slot 1 Truhe „Kit-Auswahl“ (→ 5.1), Slot 2 Netherstern „Erfolge“ (→ 5.4), Slot 9 „Zurück zur Lobby“ (AN, Rahmen) | B (Slot 1/2) |
| Hotbar INGAME | Slot 1 **Kompass** (Tracker, jeder Spieler) + Kit-Items ab Slot 2 | B (Hotbars 2024/2026 Slot 1 Kompass), Zweck A |
| Startplattform | schwebende lila Plattform, Podeste im Kreis, Holzwege, Endportalrahmen mit Enderauge, Glasscheiben | B (Notiz 14 1.1); Bauwerk selbst neu gestalten |
| Hologramme/NPC | keine belegt | – |

### 4.3 TTT

**Scoreboard INGAME (E23/E26, Standard)** – B [Bild] 2024-01-02, Clips 2024-02-14, 2024-09-19

| # | Text | Farbe | Dynamik / Sichtbarkeit |
|---|---|---|---|
| 11 | `T-Punkte` (Traitor) / `D-Punkte` (Detective) | §f | nur für T/D; Innocent/Zuschauer: Zeilen 11–9 entfallen |
| 10 | `{points}` | §c bei T (AN), §9 bei D (B) | Punktestand |
| 9 | ` ` | | |
| 8 | `Spieler` | §f | |
| 7 | `{alive}/{start}` | §a | lebende/Rundenstart (B „6/12“) |
| 6 | `  ` | | |
| 5 | `Ende` | §f | |
| 4 | `{mm:ss}` | §e | Rundentimer ab 10:00 |
| 3 | `   ` | | |
| 2 | `Hit-Cooldown` | §f | |
| 1 | `Deaktiviert` / `Aktiviert` | §c / §a | Kampfprofil |

**Scoreboard E17 (Option `legacy`)** – B [Bild] 2017: `Karma` §f / `{karma}` §b (Tausenderpunkt „1.210“) / leer / `Spieler` §f / `{alive}` §a / leer / `Ende` §f / `{mm:ss}` §e. **Wartelobby (E17, B „Map/Karma/Schutzzeit“):** `Map`/`{map}` §e, `Karma`/`{karma}` §b, `Start`/`{mm:ss}` §e (AN für letzte Zeile).

| Element | Inhalt | Beleg |
|---|---|---|
| Title bei Rollenvergabe | Title `§a Innocent` / `§9Detective` / `§cTraitor`; Subtitle `§7{rollenziel}` | B (Innocent + Subtitle-Struktur), Texte eigen |
| Actionbar | dauerhaft Rolle: `§aInnocent` / `§9Detective` / `§cTraitor`; Zuschauer `§7Zuschauer §7(Rechtsklick)` | B |
| Hologramm Leiche (3 Zeilen) | `§7Leiche von §e{name} §7({rollenfarbe}{rolle}§7)` · `§7Tot seit §e{mm:ss}` (zählt hoch) · `§7Getötet von §e{killer}` – bis Identifizierung durch Detective: `§eErmittlung läuft` | B |
| Leiche | liegende Spielerfigur (Mannequin/NPC) mit Skin des Toten | A |
| Nametags | `{name} §7[§e{clantag}§7]`, keine Rollenfarbe; Traitor sehen andere Traitor `§c{name}` (AN) | B/AN |
| Tablist | Kern-Format Spec 02 §4.4: Header `§e{brand}.net §cTTT`, Zeile 2 `tab.header.report`, Footer `tab.footer.1/.2` | B (Struktur) |
| Hotbar Wartelobby | Slot 1 Pass-Item (Papier „Pässe“) → GUI 5.11 | B (Slot 1) |
| Hotbar INGAME (Start) | Slot 1 Holzschwert, Slot 2 Bogen, Slot 9 Pfeile ×32; Shop-Item Smaragd in Slot 8 nur für T/D (AN; Bild D-Hotbar: Smaragd Slot 4, Schwert 1, Bogen 2, Pfeile 9) → **Entscheidung Slot 8** um Kollision mit Waffen-Loot zu vermeiden | B/AN |
| Traitor-Tester | Raum mit Knopf, 5 s geschlossen, Glas **grün** (Innocent/Detective) oder **rot** (Traitor); Chat an alle | B |
| Bossbar | keine | A |

### 4.4 Cores

**Scoreboard INGAME (E17/E19)** – B [Bild] 2017-07-28

| # | Text | Farbe | Dynamik |
|---|---|---|---|
| 9 | `Team Blau §7[{alive}]` | §9 | lebende Spieler |
| 8–(8-k+1) | `{✔\|✘}{coreName}` je Core | ✔ §a / ✘ §c, Name §f | z. B. `LeftCore`, `RightCore` (Name aus Map-Datei) |
| … | ` ` | | |
| 5 | `Team Rot §7[{alive}]` | §c | |
| 4–… | Cores Rot | wie oben | |
| 2 | `Kills` | §f | |
| 1 | `{kills}` | §e | |

| Element | Inhalt | Beleg |
|---|---|---|
| Actionbar | `§9Team Blau` / `§cTeam Rot` dauerhaft | B [Bild] |
| Core-Angriff | Chat-Signal + Ton (`BLOCK_NOTE_BLOCK_PLING`, AN) an verteidigendes Team; Titel kurz `§c⚠ {coreName}` (AN) | B (Signal+Ton) |
| Nametag | `{teamfarbe}{name} §7[§e{clantag}§7]` | B |
| Hotbar Start (E17) | 1 Steinschwert, 2 Bogen, 3 Holz ×64, 4 Pfeile ×16, 5 leer, 6 Goldapfel ×16?, 7 Essen ×32, 8 Axt, 9 Spitzhacke | B [Bild]; Mengen 16/32 abgelesen |
| Bossbar | keine | A |

### 4.5 MasterBuilders

**Scoreboard (E26)** – B (Clips): `Zeit` §f / `{mm:ss}` §e / leer / `Thema` §f / `{thema}` §a.

| Element | Inhalt | Beleg |
|---|---|---|
| Hologramm über Plot (Bewertung) | `§6{erbauer}` groß über dem bewerteten Plot – **nur nach** der Bewertung des Plots (anonym während Bewertung) | B (Name gesehen), Zeitpunkt A |
| Hotbar Bewertung | Slots 1–6 = Noten (Farbstoff/Terrakotta): 1 `§4Grauenvoll`, 2 `§cSchlecht`, 3 `§6Okay`, 4 `§aGut`, 5 `§bSuper`, 6 `§5Legendär`; gewählter Name als Item-Einblendung | B (6 Noten, farbige Items, „Okay“ §6) ; Farben je Note A |
| Hotbar Bauphase | Kreativ-Inventar; Slot 9 Buch „Plot-Optionen“ (Boden/Wetter/Zeit, AN) | B (Slot 9 Buch) |
| NPC Wartelobby | Villager „Themen“ → GUI 5.14 | B |
| Nametag | `{name} §7[§e{clantag}§7]` | B |

### 4.6 ClanWar

| Element | Inhalt | Beleg |
|---|---|---|
| Wartelobby-Hologramm | `§f§lClanWar` bzw. `§f§lFunClanWar`, darunter `§fvs` | B (FunClanWar/vs) |
| Team-Listen (Hologramm links/rechts) | je Spieler Kopf + `§c{name} §e[{tag}]` (eigenes Team), Gegner vor Beitritt `§9Unknown` mit „?“-Kopf | B (mit ?-Markern) |
| Bossbar | `{clanfarbe}{clanA} §e[{tagA}] §7: §f{mm:ss}` – Rundenzeit (60:00 → 0) | B unscharf („Complexity [Tag] : 00:00“), Struktur A |
| Scoreboard | Ziel-Modus-Scoreboard, zusätzlich Zeile `Clan` §f / `{tag}` §e (AN) | AN |

### 4.7 Gemeinsam (Rahmen)

| Element | Inhalt | Beleg |
|---|---|---|
| Zuschauer-GUI | „Wähle einen Spieler“, Köpfe ohne Rahmen, so viele Reihen wie nötig | B (SG + TTT) |
| Coin-Meldung nach Tod/Ende | `§6[CoinSystem] §e+{n} Coins`, ggf. Boost-Zeile | B; Kern-Spec |

---

## 5. GUIs

Konventionen: Slot-Index 0-basiert (Zeile×9+Spalte). Titelfarbe `§8` wenn nicht anders angegeben. Klick in Spieler-Inventar ignoriert, Shift-Klick gesperrt (außer Truhen-Loot).

### 5.1 EG „Kit-Auswahl“ (Wartelobby, Hotbar Slot 1, `/kit`)
- Titel `§8Kits` (AN; Original-Layout unbelegt) · **6 Reihen** (54).
- Slots 0–49: 50 Kits in Tabellenreihenfolge 6.2 (Rucksack nur bei `legacy`; sonst 49 Kits auf 0–48); Slot 53: „Zufälliges Kit“ (Item `ENDER_EYE`). Slot 49 (E26) / 50 frei.
- Item: Icon laut 6.2. Name: gekauft `§d{kit} §7(Gekauft)`; ausgewählt zusätzlich Verzauberungs-Glanz; nicht gekauft `§c{kit}`.
- Lore (B-Struktur): `§7§nFähigkeiten:§r §7{fähigkeit…}` (umbrechen bei 40 Zeichen) · leer · `§7§nAusrüstung:§r §7{ausrüstung}` · (nicht gekauft:) leer · `§7Preis: §e{preis} Coins` · `§eKlicke zum Kaufen` / (gekauft) `§aKlicke zum Auswählen`.
- Klick gekauft → wählen, `eg.kit.selected`, GUI schließt. Klick nicht gekauft → 5.3. Klick „Zufällig“ → `kit_selected=random`.

### 5.2 EG Kit-Kauf (in 5.1 integriert) – kein eigener Shop-NPC (AN).

### 5.3 EG „Kauf bestätigen“ (AN)
- Titel `§8{kit} kaufen?` · 3 Reihen. Slot 11 grüne Wolle `§aKaufen §7({preis} Coins)` → `CoinService.debit(idem=eg:kit:{uuid}:{kit})`, Erfolg `eg.kit.bought`, Fehler `eg.kit.no_coins`. Slot 13 Kit-Icon (Lore wie 5.1). Slot 15 rote Wolle `§cAbbrechen` → zurück zu 5.1.

### 5.4 „Erfolge“ (EG, SG, Cores; Hotbar Slot 2 / `/erfolge`)
- Titel `§8Erfolge` · **4 Reihen** (B), Slots 0–35 in Listenreihenfolge 6.2.7.
- Erreicht: `LIME_DYE`, Name `§a{name}`, Lore `§7{beschreibung}`, `§a✔` (B). Nicht erreicht: `GRAY_DYE`, Name `§7{name}`, Lore `§7{beschreibung}`, `§c✘` (AN). Klick: keine Aktion.
- Bei > 36 Erfolgen (EG: 38): Slot 35 Pfeil `§eNächste Seite`, Seite 2 Slot 27 `§eVorherige Seite` (AN).

### 5.5 EG „EnderChest“ (Loot-Behälter)
- Titel `§dEnderChest` (B: hell-magenta) · **3 Reihen** (27, B). Inhalt aus Loot-Tabelle 6.2.5, zufällige Slots. Normales Truhenverhalten; leere Kiste bleibt (Achievement „Ins Leere“ nur SG).
- Kit Dieb (E26): Items wandern im 3-Tick-Takt (AN) einzeln ins Spieler-Inventar, GUI schließt erst wenn leer (B).

### 5.6 EG „Rucksack“ (Kit Dieb E26 / Rucksack legacy)
- Titel `§8Rucksack` · 3 Reihen (27, B). Persistenz nur für die Runde; Inhalt droppt beim Tod.

### 5.7 SG „Truhe“
- Vanilla-Truhe, Titel `§8Truhe` (B) · 3 Reihen. Doppeltruhen zulässig (6 Reihen, Titel `§8Große Truhe`). Inhalt aus Tier-Tabelle 6.1.3, bei Refill neu befüllt.

### 5.8 Zuschauer „Wähle einen Spieler“ (Rahmen; hier für SG/EG/TTT/Cores/CW)
- Titel `§8Wähle einen Spieler` (B) · Reihen = ⌈lebende/9⌉ (B: 1 Reihe bei 7, 2 Reihen bei 11). Slots 0…n-1: `PLAYER_HEAD` des Lebenden, Name in Rangfarbe (B `§b`), Lore `§7Klicke zum Teleportieren` (AN). Klick → Teleport, GUI schließt.

### 5.9 TTT „Traitor-Shop“ (`/shop`, Smaragd)
- Titel `§cTraitor-Shop` (AN) · 3 Reihen. Slots 10–16 und 19–25 = Items 6.3.5 in Tabellenreihenfolge. Slot 4: Smaragd `§cT-Punkte: §e{n}`.
- Item-Lore: `§7{wirkung}` · leer · `§7Preis: §e{p} T-Punkte` · `§eKlicke zum Kaufen` (oder `§cZu wenig Punkte`).
- Klick: genug Punkte → abbuchen, Item ins Inventar, `ttt.shop.bought`; sonst `ttt.shop.no_points` {fehlend}. Limit je Item/Runde laut 6.3.5 → `ttt.shop.limit`.

### 5.10 TTT „Detective-Shop“
- Titel `§9Detective-Shop` (AN) · 3 Reihen, Aufbau wie 5.9 mit D-Items und `§9D-Punkte`.

### 5.11 TTT „Pässe“ (Wartelobby Slot 1)
- Titel `§8Pässe` · 3 Reihen. Slot 11 rotes Papier `§cTraitor-Pass` Lore `§7Vorrat: §e{n}` · `§7Garantiert die Traitor-Rolle` · `§eKlicke zum Einlösen`. Slot 13 grünes Papier `§aInnocent-Pass` (AN). Slot 15 blaues Papier `§9Detective-Pass`. Slot 22 Buch `§eKarma: §b{karma}`.
- Klick → wie `/traitor` usw. Vorrat 0 → `ttt.pass.none` mit Hinweis Vote-Shop.

### 5.12 Cores „Ausrüstung“ (Kit-Menü, B nur „Kit-Menü“ gesehen)
- Titel `§8Ausrüstung` (AN) · 3 Reihen. Slot 11 Eisenblock `§fEisen-Ausrüstung`, 13 Goldblock `§6Gold-Ausrüstung`, 15 Diamantblock `§bDiamant-Ausrüstung` – Lore `§7Status: §a✔ freigeschaltet` / `§c✘ Baue einen {erz}block ab`. Klick freigeschaltet → setzt Respawn-Kit. Rein informativ, wenn nur eine Stufe frei.

### 5.13 MB „Themenwahl“
- Titel `§8Wähle ein Thema` (AN; B: 3 Begriffe zur Wahl) · 1 Reihe. Slots 2, 4, 6: Papier `§a{thema}`, Lore `§7Stimmen: §e{n}`. Klick = Stimme (änderbar), Anzeige live. Öffnet automatisch zu Phasenbeginn; schließen erlaubt, Wiederöffnen per Hotbar Slot 5 Papier.

### 5.14 MB „Themen“-NPC (Wartelobby)
- Titel `§8Themen` · 3 Reihen. Slot 11 Feder `§aThema vorschlagen` → Schild/Anvil-Eingabe (max 32 Zeichen, Filter Kern-Chatfilter) → `mb.theme.submitted`. Slot 15 Buch `§eThemen bewerten` → Unter-GUI 1 Reihe: Slot 4 Papier `§f{vorschlag}`, Slot 2 grüne Wolle `§aGefällt mir` (+1), Slot 6 rote Wolle `§cGefällt mir nicht` (−1), danach nächster Vorschlag; keiner offen → `mb.theme.none`.

### 5.15 CW „Herausforderung“ (Chat-Klick statt GUI; B: Klick-Zeilen im Netzwerk üblich)
- Kein Inventar-GUI. Chatzeile mit `§a[ANNEHMEN]` (`/cw accept {name}`) und `§c[ABLEHNEN]`.

**GUIs gesamt: 14** (5.1, 5.3–5.14; 5.2/5.15 kein eigenes Inventar).

---

## 6. Mechaniken & Zahlen

### 6.1 SurvivalGames

**6.1.1 Ablauf (sg24, Standard E26)**
1. WAITING: Spieler betreten Wartelobby-Bereich der Map. Mindestspieler **8** (AN) → STARTING.
2. Lobby-Countdown **60 s** (AN; B: Werte 50/40/30 gesehen). Meldungen bei 60, 50, 40, 30, 20, 10, 5, 4, 3, 2, 1. Bei 24/24 Spielern auf 10 s kürzen (AN). Fällt Spielerzahl < 8 → zurück WAITING, `common.countdown.cancel`.
3. Teams: Partys bleiben zusammen, Rest zufällig zu 2er-Teams (`Team #1…#12`), Teamwahl-Item optional (AN).
4. Teleport auf **Kreis-Podeste** um die Mitteltruhen (B), Freeze **10 s** (AN), Chat-Countdown `sg.start.countdown`.
5. INGAME: Schutzzeit **30 s** (AN; Original für SG unbelegt; QSG: 0 s B). Kein Teamschaden.
6. Truhen: jede Truhe beim ersten Öffnen befüllt (Lazy-Fill). **Refill** aller Truhen (auch geöffneter) bei **15:00 Spielzeit** (Standard E23/E26, B: Refill-Timer bis 14:31 bei Rundenbeginn gesehen) – Option `legacy`: **10:00** (B Regeltext). Danach zweiter Refill nach weiteren 15:00 (AN) – Scoreboard zeigt nach letztem Refill „Deathmatch“.
7. Abbau/Bau verboten außer **Pflanzen** (Gras, Blumen, Pilze, Laub NICHT bei QSG) (B), TNT darf gesetzt werden (sofort gezündet? → normal, AN), Feuerzeug/Lava erlaubt.
8. Tod → Kill-Meldung + „Leben von {killer}“ + „{n} verbleibende Spieler“ (B), Zuschauer (Rahmen), Coins.
9. **Deathmatch**-Trigger (AN): `alive ≤ 4` ODER Spielzeit **30:00** → 60-s-Countdown `sg.dm.countdown` (Meldungen 60/30/10/5–1), dann Teleport in **DM-Arena** (B: eigene Arena, Teleport). 10 s Freeze, Border-freie Arena.
10. DM-Dauer **5:00** (AN). Ende: letzter Überlebender / letztes Team gewinnt. Zeitablauf: Sieger = meister **Schwertschaden im DM** (B Forum, spätere Antwort; Option `dmTimeout=draw`).
11. ENDING: Sieger-Meldung, Coins, Stats, `common.restart` 15 s → Server stoppt.

**6.1.2 Zahlen**

| Größe | Wert | Beleg |
|---|---|---|
| Max. Spieler sg24 / sg16 / sg64 / qsg | 24 / 16 / 64 / 20 | B |
| Teamgröße sg24 | 2 | A |
| Mindestspieler | 8 (qsg 6, sg64 16) | AN |
| Refill | 15:00 (legacy 10:00) | B/A |
| QSG Kampfbeginn | 10 s nach Teleport, keine Schutzzeit | B |
| QSG Rüstung in Truhen | nur Gold-, Eisen-, Diamant-**Brustpanzer** (plus Leder/Ketten andere Teile, AN) | B |
| QSG Map | keine Werkbänke/Zaubertische zusätzlich, Laub/Melonen nicht abbaubar, eigene DM-Arena | B |
| DM-Trigger / Dauer | ≤4 lebende oder 30:00; 5:00 | AN |
| Coins | Teilnahme 10, je Kill 12, Sieg 100, DM-Erreichen 20 | AN (stimmig mit B „+34“ bei 2 Kills) |

**6.1.3 Truhen-Loot (AN, Belegbasis: gesehene Inventare 2024/25)**

| Tier | Truhen | Items (Gewicht) | Items je Truhe |
|---|---|---|---|
| 1 (Map-Truhen) | alle außer Mitte | Holzschwert 6, Steinschwert 3, Holzaxt 5, Goldaxt 3, Bogen 3, Pfeile ×4–8 4, Angel 3, Lederrüstung je Teil 4, Ketten-Teile 2, Goldhelm/-stiefel 3, Brot ×1–3 6, Apfel ×1–3 6, Steak/Hühnchen ×1–2 5, Weizen ×2–4 3, Spinnweben ×1–3 3, Feder ×1–3 2, Feuerstein 2, Kompass 1, Goldbarren ×1–3 2, Kohle 2, Stöcke 3, TNT 1 | 3–6 |
| 2 (Mitte + markierte Map-Truhen) | Mitte (Podestkreis) | Steinschwert 5, Eisenschwert 2, Goldschwert 3, Bogen 3, Pfeile ×8 4, Eisenteile je 2, Ketten-Teile 3, Goldapfel 1, Eisenbarren ×1–2 3, Diamant 1, Steak ×2 4, Kuchen 1, Erfahrungsfläschchen ×2 2 | 4–7 |

Map-Anforderungen SG: 24 Podeste (Kreis um Mitte, Radius 12–20), ≥ 8 Tier-2-Truhen in der Mitte, ≥ 60 Tier-1-Truhen, Werkbank(e) an der Mitte (B), Wartelobby-Bereich, getrennte DM-Arena mit 24 Spawnpunkten (Durchmesser ≤ 40 Blöcke, keine Angel-Zone auf Eis: Angeln in Arena-Truhen entfernen – B Forumshinweis), Map-Grenze (Worldborder) Pflicht.

**6.1.4 SG-Erfolge (B Fan-Seite, Auswahl):** Beschter Man (Sieg SG24), Best of 64 (Sieg SG64), Blitzkrieg (Sieg vor DM-Teleport), Alleingang (3 Kills im DM), Ich liebe Verstecken! (kein Kill vor DM), Sieg < 7 min, Letzte Sekunde (Join 1 s vor Start), Genull nau Uhr (Rundenstart 0:00 Uhr), Die Antwort („42“ in Chat), Ups, wie hab ich gewonnen? (Sieg ohne Truhe), Ins Leere gegriffen (40 leere Truhen), Randale (80 Blöcke), Spartakus (Goldapfel essen), Unglückszahl (genau 13 Kills). **Namen eigen formulieren** (Liste in i18n), Bedingungen 1:1.

### 6.2 EnderGames

**6.2.1 Ablauf (E26)**
1. WAITING in gebauter Wartelobby (dunkles Holz/Stein, B). Biom der Rundenwelt wird vorab gewählt und angezeigt (B). Mindestspieler **12** (AN; B max 24).
2. Lobby-Countdown **60 s** (AN); bei 24/24 → 10 s.
3. Kit-Zuteilung: gewähltes Kit; `random` = zufällig aus **gekauften** Kits (B); kein Kit gekauft → Zufall aus Gratis-Kits (AN).
4. Teleport auf **Startplattform** (Podeste im Kreis, schwebend über Weltzentrum, B). Countdown **10 s** im Chat (B: „… beginnen in 10, 9, 8 …“; E26-OCR „starten in 18 Sekunden“ → Countdown startet bei **20 s**, Meldungen 20, 15, 10, 5–1, AN/A).
5. Podeste geöffnet (Glas entfernt), Spieler springen ab (A). Kein Fallschaden für die ersten **10 s** (AN). Startausrüstung: Kompass (Slot 1) + Kit-Items.
6. **Schutzzeit 120 s** (AN; B: Countdown mit Meldungen „endet in 50/40 Sekunden“, 3/2 s). Meldungen bei 60, 50, 40, 30, 20, 10, 5, 4, 3, 2, 1. Ende: zwei Zeilen `eg.grace.end` + `eg.grace.end2` (B Wortlaut-Struktur „Die Schutzzeit ist beendet“ / „Alle Spieler sind nun verwundbar“).
7. Kampfphase: Scoreboard „Deathmatch in **30:00**“ (B [Bild]: 29:53 kurz nach Schutzzeitende). Teaming erlaubt (B).
8. Tod → `eg.kill`, `eg.killer_health` (Herzen), `eg.victim_kit` („Benutztes Kit: {kit}“, B), `eg.remaining`. Drops normal.
9. Sinkt `alive ≤ 3` → Deathmatch-Timer auf 60 s (AN).
10. **Deathmatch** (AN Form, B Existenz): Teleport aller Lebenden auf die Startplattform-Ebene am Weltzentrum (Arena-Kreis Radius 25), 10 s Freeze, Worldborder schrumpft von 50 auf 10 Blöcke Durchmesser in 3:00. Kein Zeitlimit-Unentschieden: nach Borderende Wither-Effekt alle 10 s (AN).
11. Sieg: letzter Spieler (B: Restspieler zählt herunter). Teaming: gewinnt nur **ein** Spieler (A). ENDING wie SG.

**6.2.2 Welt**

| Größe | Wert | Beleg |
|---|---|---|
| Welt | pro Runde **zufallsgeneriert** (Vanilla-Generator, Seed zufällig), Biom am Zentrum aus Liste: Wüste, Mesa, Wiese/Ebene, Dschungel, Schnee, Sumpf, Wald, Taiga | B (Biome aus Bildern) / A |
| Vorgenerierung | Radius 300 Blöcke vor Rundenstart (Server startet Welt in WAITING) | AN |
| Worldborder | Durchmesser **600** zentriert | AN |
| Startplattform-Höhe | Y = Oberfläche + 40 | AN |
| Enderkisten | **90** zufällig auf Oberfläche verteilt (min. Abstand 24), dazu **8** in der „Mitte“ (Boden unter Plattform) mit Tier-2 | B (Existenz/„Mitte“), Zahl AN |
| Enderkisten-Refill | keiner | AN |
| Partikel über Enderkiste | `PORTAL`-Partikel alle 1 s, Sichtweite 32 | B (Symbol), Art AN |
| Suppen | Pilzsuppe heilt **3,5 Herzen** sofort, Schüssel bleibt (Soup-PvP) ; Rezepte: Schüssel + brauner + roter Pilz, Schüssel + Kakaobohnen (AN), Schüssel + Kaktus (AN) | B (Recraft), Werte AN |
| Enderperlen | Schaden 2,5 Herzen (Vanilla 1.8), kein Cooldown | AN |
| Tracker-Kompass (alle) | zeigt auf nächsten Gegner > 15 Blöcke, Rechtsklick aktualisiert | AN |
| Coins | Teilnahme 10, je Kill 15, Sieg 150 | AN |

**6.2.3 Kits (49 aktiv E26 + 1 legacy).** Preis in Coins. „Teleport“ = Enderperlen-Landung. Icon = GUI-Material (AN wo nicht genannt). Wo Original-Zahl fehlt: **AN**-Wert in eckigen Klammern.

| # | ID | Name | Preis | Fähigkeit (Implementierung) | Startausrüstung | Icon |
|---|---|---|---|---|---|---|
| 1 | archer | Bogenschütze | 0 | Pfeiltreffer am Kopf (Treffer-Y ≥ Augenhöhe −0,3) auf Gegner **ohne Helm** = Tod | Bogen, 5 Pfeile | BOW |
| 2 | lumberjack | Holzfäller | 0 | ein Axtschlag fällt ganzen Baum (zusammenhängende Stämme, max [64]); jede gecraftete Axt erhält Schärfe I | Holzaxt, Lederjacke rot, Lederhose blau, Lederschuhe schwarz | WOODEN_AXE |
| 3 | hunter | Jäger | 0 | getötete Tiere droppen gebratenes Fleisch | Steinschwert, Lederjacke grün, Lederhose grau | STONE_SWORD |
| 4 | barbarian | Barbar | 0 | keine | Holzschwert, Lederrüstung komplett (Haltbarkeit 1) | WOODEN_SWORD |
| 5 | ham | Schinken | 100.000 | kein Hungertod (Hunger min 1 Punkt); Essen „Schinken“: **6 s** Schnelligkeit I + Regeneration I + **+50 % Schaden**; Item regeneriert alle [60 s] | 1 rohes Schweinefleisch „Schinken“ (Gag-Lore eigen) | PORKCHOP |
| 6 | soupmaster | Suppenmeister | 100.000 | Suppen wirken ohne Verzehr-Verzögerung und heilen [+1 Herz] extra | 6 Pilzsuppen | MUSHROOM_STEW |
| 7 | berserker | Berserker | 100.000 | Kill (Mob/Spieler) → **10 s Resistenz**; Serie: Stufe +1 je Kill innerhalb der Wirkung, max [Resistenz III] | – | BLAZE_POWDER |
| 8 | vampire | Vampir | 50.000 | Mob-Kill **+3 Herzen** Heilung; Spieler-Kill **volle Heilung** | – | REDSTONE |
| 9 | bomber | Bomber | 70.000 | immun gegen Explosionsschaden; getötete Spieler/Mobs explodieren (Kraft [2], ohne Blockschaden); eigenes TNT zündet beim Setzen sofort | 5 TNT | TNT |
| 10 | stomper | Stampfer | 85.000 | Landung auf Spieler: Fallschaden des Stampfers wird als Schaden übertragen, ≥ [4 Blöcke] Fall = tödlich (B „tötet“); eigener Fallschaden max **2 Herzen** | – | DIAMOND_BOOTS |
| 11 | tank | Panzer | 35.000 | Schleichen: eingehender Schaden fix **½ Herz**, eigener Nahkampfschaden 0 | Goldhelm, Goldharnisch | GOLDEN_CHESTPLATE |
| 12 | wizard | Wizard | 100.000 | immun gegen negative Trankeffekte | Wurftränke: Schnelligkeit ×3, Schwäche ×6, Regeneration ×3, Gift ×5, Verlangsamung ×4, Schaden ×5; 20 Erfahrungsfläschchen | SPLASH_POTION |
| 13 | scorpion | Skorpion | 70.000 | Nahkampftreffer **6 s Gift**, Pfeiltreffer **2 s Gift** (Gift I); selbst giftimmun | Lederharnisch grün | SPIDER_EYE |
| 14 | kangaroo | Känguru | 85.000 | **Doppelsprung** (Feuerwerk-Item Rechtsklick in Luft); Sprungkraft sinkt mit Hunger (Hunger 20 → 1,0; 6 → 0,4 Faktor); unter **4 Herzen** gesperrt | Feuerwerksrakete (Kit-Item) | FIREWORK_ROCKET |
| 15 | shark | Hai | 85.000 | im Wasser: Wasseratmung, **Regeneration II + Resistenz II**; an Land: Schwäche I | Wassereimer | WATER_BUCKET |
| 16 | redstoner | Redstoner | 35.000 | Minen (Druckplatte, Explosion Kraft [3] bei Betreten durch Gegner) und C4 (Rechtsklick Detonator zündet alle eigenen C4 im Umkreis **40 Blöcke**) | Detonator, 10 C4, 8 Minen | REDSTONE_TORCH |
| 17 | crafter | Crafter | 35.000 | mobile Werkbank + mobiler Amboss (Rechtsklick öffnet GUI) | Werkbank, Amboss, 7 Leder, 6 Stöcke, 7 Goldbarren | CRAFTING_TABLE |
| 18 | angler | Angler | 35.000 | Angel-Treffer zieht Spieler heran, Angler erhält **4 s Schnelligkeit** | Angel, Lederkappe gelb | FISHING_ROD |
| 19 | yeti | Yeti | 70.000 | Schlag: **40 %** Verlangsamung [Slowness II 3 s]; Schneespur; läuft über Wasser (Wasser unter Füßen → Eis, schmilzt nach [5 s]) | Lederhose weiß, Lederharnisch weiß | SNOW_BLOCK |
| 20 | dolphin | Delfin | 100.000 | im Wasser Doppelsprung; alle [20 s] im Wasser 1 roher Fisch; Nachtsicht + Wasseratmung im Wasser | Lederschuhe blau | COD |
| 21 | coward | Angsthase | 100.000 | bei erlittenem Schaden [3 s Schnelligkeit II] | Holzschwert (Rückstoß III) | FEATHER |
| 22 | souldiver | Seelendieb | 20.000 | mobiler Zaubertisch; erhält XP aus Spielerkämpfen [1 Level je Kill + Schaden/4 XP] | mobiler Zaubertisch | ENCHANTING_TABLE |
| 23 | mario | Mario | 85.000 | Sprungkraft II dauerhaft; Schleichen in der Luft = Stampf-Landung (wie Stampfer, Schaden [3 Herzen]); Linksklick Feuerkugel = Feuerball (Verbrauch 1) | Lederkappe rot, Lederhose blau, Feuerkugeln [5] | FIRE_CHARGE |
| 24 | spy | Spion | 50.000 | Schleichen: **unsichtbar** (auch Rüstung) + Resistenz I, Hunger ×3; Hunger 0 → sichtbar | Lederjacke weiß | GLASS_PANE |
| 25 | chameleon | Chamäleon | 70.000 | nach Tier-Kill: Verwandlung in diese Tierart (Disguise), erlittener Treffer verwandelt zurück | Goldschwert | SLIME_BALL |
| 26 | tracker | Tracker | 20.000 | „Kompass 2.0“: Rechtsklick zeigt nächsten Gegner: Kit, Herzen, Rüstung + Chat `eg.tracker` | Kompass 2.0, Lederjacke | COMPASS |
| 27 | assassin | Assassine | 70.000 | **doppelter Schaden von hinten** (Winkel Blickrichtung Opfer vs. Angreifer < 60°) | Holzschwert, Lederkappe weiß | IRON_SWORD |
| 28 | morpher | Morpher | 35.000 | Spieler-Kill → erhält **zusätzlich** die Fähigkeit des Opfer-Kits (stapelbar, B) | Lederjacke gelb, Lederschuhe gelb | ENDER_EYE |
| 29 | enderman | Enderman | 100.000 | Enderperlen-Schaden 0 (B „weniger“ → AN: 0); Enderkisten öffnen aus **[6 Blöcken]** Reichweite (Rechtsklick in Blickrichtung); **+1 Enderperle pro Kill** | 5 Enderperlen | ENDER_PEARL |
| 30 | teleporter | Teleporter | 85.000 | bei jedem Teleport: volle Herzen + volle Hungerleiste + **10 s unverwundbar** | – | ENDER_CHEST |
| 31 | grappler | Enterhaken | 85.000 | Angel-Haken an Block → zieht Spieler zum Haken (Velocity) | Angel „Enterhaken“ | LEAD |
| 32 | irongolem | Eisengolem | 70.000 | Rechtsklick auf Tier/Spieler: **4 s** hochhalten, danach Wurf (Velocity 1,5 in Blickrichtung); selbst Langsamkeit I dauerhaft | Eisenhelm | IRON_BLOCK |
| 33 | police | Polizist | 70.000 | **unsterblicher Wolf** greift Kampfziel an; Item „Wolf rufen“ teleportiert ihn zurück | Lederjacke blau, Stock (Rückstoß III), Wolf (Name eigen: „Wachhund“), Item „Wolf rufen“ | BONE |
| 34 | spider | Spinne | 50.000 | Wandklettern (an Blockwand Leertaste), Schnelligkeit I, Spinnweben werfen (Rechtsklick → Projektil setzt Netz); **+8 Spinnweben pro Kill** | 32 Spinnweben, Lederkappe schwarz | COBWEB |
| 35 | cactus | Kaktus | 50.000 | Schleichen: Kaktus-Verwandlung (Block-Disguise, unbeweglich); Nahkampf-Angreifer erhalten [1 Herz] Schaden | Lederkappe grün, Lederhose grün | CACTUS |
| 36 | squid | Tintenfisch | 100.000 | Wasserspur (Blöcke hinter sich kurz Wasser, [2 s]); Schlag: **50 %** Chance auf **5 s Blindheit** | Lederjacke blau, Lederschuhe schwarz (Wasserläufer I) | INK_SAC |
| 37 | thief | Dieb | 50.000 | E26: nach Öffnen einer Enderkiste **Schnelligkeit I 8 s**, Inhalt wird nach und nach eingezogen, Kiste schließt erst wenn leer (B Changelog). Legacy: ganzer Inhalt sofort + 12 s Speed | Rucksack (27 Slots) | CHEST_MINECART |
| 38 | technician | Techniker | 70.000 | Spieler-Kill → nächstbessere Rüstungsstufe für ein zufälliges Teil (Leder→Gold→Ketten→Eisen→Diamant), Haltbarkeit 50 % | – | ANVIL |
| 39 | cat | Katze | 85.000 | roher Fisch essen → [10 s Schnelligkeit II]; Fallschaden −50 %; Faustschaden [+3] | 20 roher Fisch | COD |
| 40 | seeker | Zielsucher | 50.000 | Gegner mit **< 5 Herzen** im Umkreis **30 Blöcke** → **Schnelligkeit III + 20 % Schaden** solange zutreffend | Goldschwert, Goldschuhe | TARGET |
| 41 | snowgolem | Schneegolem | 20.000 | Schneeball „Switcher“ (unbegrenzt, Cooldown [3 s]): Treffer **tauscht Positionen** (B 2026 „Du wurdest mit … getauscht“); Schneespur; Schleichen = Schneegolem-Disguise | Schneeball „Switcher“ | SNOWBALL |
| 42 | blocker | Blocker | 35.000 | blockierend (Schwert-Rechtsklick, 1.8) gegen Pfeile: 0 Schaden, Pfeil fliegt zurück, **50 %** Chance [1 Herz] Heilung | Lederhelm, Goldschwert | SHIELD |
| 43 | link | Link | 100.000 | Start mit **3 Herzen** max; +1 Herz max je **Teleport oder Kill**, max **20 Herzen**; **3 min Resistenz II** ab Spielstart | Lederkappe grün, Lederjacke grün, Holzschwert (Haltbarkeit 2) | GOLDEN_APPLE |
| 44 | sonic | Sonic | 70.000 | Sprinten steigert Tempo: alle [3 s] Schnelligkeit +1, max III; Stopp → zurück | – | SUGAR |
| 45 | monster | Ungeheuer | 85.000 | Schaden **nur mit Bogen**; andere Waffen zerbrechen beim Treffer; Kill: **60 %** Chance Bogen-Upgrade (Stärke +1, max V) | Bogen (Unendlichkeit), 1 Pfeil, Lederkappe braun, Lederhose grün, „Veganes Essen“ ([8 Karotten]) | BOW |
| 46 | slime | Slime | 50.000 | dauerhaft **Sprungkraft II**; beim Springen [10 %] Schleimball; Wurf trifft → Langsamkeit III [3 s]; erlittener Treffer spawnt [2] Mini-Slimes (befreundet) | 10 Schleimbälle, Lederjacke grün, Lederschuhe grün | SLIME_BLOCK |
| 47 | thor | Thor | 100.000 | Hammer Rechtsklick: Blitz auf Zielblock (Reichweite [40]), Cooldown **5 s**; Treffer **1 Herz absolut** + 1 Herz Brand; Blitz auf Block entzündet Oberfläche | Thors Helm (Goldhelm), Hammer (Goldaxt), 1 Goldapfel | GOLDEN_AXE |
| 48 | shadow | Schatten | 90.000 | Rechtsklick Essenzen: Klon (NPC mit Spieler-Skin, kämpft wie Spieler, [5 Herzen], Kosten [8] Essenzen); **+3 Essenzen je Teleport, +5 je Kill** | 32 Schattenessenzen | BLACK_DYE |
| 49 | blaze | Lohe | [85.000] | Feuerspur hinter sich (Feuer auf Luftblock hinter Spieler, [1 s]); immun gegen **Feuerzeug**-Feuer; im Wasser Schwäche I; Schwerttreffer **20 %** anzünden [4 s] | Goldschwert, Lohenstaub ([3]) | BLAZE_POWDER |
| L | backpack | Rucksack (nur `legacy`) | 20.000 (Fan) / 50.000 (Changelog) | Truhe im Inventar mit 27 Zusatzplätzen; E26 in Dieb aufgegangen (B) | Truhe | CHEST |
| – | random | Zufälliges Kit | – | zufällig aus gekauften Kits | – | ENDER_EYE |

Preisstufen: 0 / 20k / 35k / 50k / 70k / 85k / 90k / 100k (B). Nicht übernommen: Ritter, Voodoo (nur Videotitel, keine Fähigkeit belegt) → offene Punkte.

**6.2.4 Disguise/Klon:** über Paper-API (Entity „Mannequin“/fake player bzw. eigenes Paket `game-framework.disguise`). Kit-Fähigkeiten wirken erst **nach Schutzzeit** auf Gegner (Schaden), Bewegungs-Fähigkeiten sofort (AN).

**6.2.5 Enderkisten-Loot (AN; B-Beispiele: Pfeile, rohes Fleisch, Wassereimer, Spinnweben ×24, Goldbarren; Inventare: Stein/Eisen/Diamantschwerter, Diamanten, Enderperlen, Pilzsuppe, TNT, Lederrüstung, Holz)**

| Tier | Items (Gewicht, Menge) | Items je Kiste |
|---|---|---|
| 1 (Welt) | Pfeile 6 (4–12), rohes Rind/Schwein 6 (2–7), Wassereimer 3, Spinnweben 3 (4–24), Goldbarren 4 (1–5), Eisenbarren 4 (1–3), Steinschwert 4, Eisenschwert 2, Bogen 3, Enderperle 6 (1–3), Pilze braun/rot 6 (4–8), Schüssel 5 (4–8), Pilzsuppe 4, TNT 2 (1–3), Lederrüstung je Teil 3, Holz 4 (4–16), Feuerzeug 1, Diamant 1 | 4–7 |
| 2 (Mitte) | Eisenschwert 4, Diamantschwert 1, Eisenrüstungsteil 3, Enderperle 5 (2–4), Pilzsuppe 5 (2–4), Goldapfel 1, Diamant 2 (1–2), Bogen 3, Pfeile 4 (8–16) | 5–8 |

**6.2.6 Achievements EG (38, B Fan-Seite; Namen eigen in i18n, Bedingungen 1:1):** Sieg mit Kit Dieb/Rucksack · 150 Siege · 10 Kills/Runde · 4 Kills/Runde · Sieg ohne Kill · Sieg ohne Sprinten · Stampfer-Kill aus ≥ 60 Blöcken Höhe · Kill durch Eisengolem-Wurf · Kill mit Tränken · an Verbrennung sterben · Headshot (Bogenschütze) · Yeti 100 Eisblöcke · Chamäleon in jede Tierart · 100 Herzen durch Kit geheilt · Katze Faust-Kill · 1 min am Stück Kaktus · Stampfer-Kill · Kill mit Thor-Blitz · 1 min unsichtbar (Spion) · Ungeheuer Bogen Stärke 4 · Link 20 Herzen · Sieg als Polizist · Morpher 5 Kits in einer Runde · 2 Kills gleichzeitig mit C4 · Techniker volle Diamantrüstung durch Kills · Villager-Handel · Bett craften · Notch-Apfel craften · volle Diamantrüstung craften · Kuchen craften · volle Goldrüstung craften · 640 Holzstämme (über Runden) · 100 Suppen in einer Runde craften · 32 Enderperlen in einer Runde · Angel kaputt benutzen · Level 15 · nur Gemüse essen (Runde) · „gg“ in Chat nach Rundenende.

### 6.3 TTT

**6.3.1 Ablauf (ttt12, E23/E26)**
1. WAITING: Wartelobby; Mindestspieler **6** (AN), max 12. Pass-Item Slot 1 (B).
2. Lobby-Countdown **60 s** (AN), bei 12/12 → 10 s.
3. Teleport auf Map-Spawns, Spieler verteilt. **Schutzzeit 30 s** (B ~30 s bis Rollenvergabe): kein Schaden, Waffen aus Map-Truhen aufnehmbar. Meldungen 10 (AN), 5, 4, 3, 2, 1 (B).
4. **Rollenvergabe** bei Schutzzeitende: Pässe zuerst (Reihenfolge der Einlösung, max Rollenkontingent), Rest zufällig.
   - Traitor = `max(1, floor(n × 0,25))` → 12 Spieler: **3** (AN, Genre-Standard).
   - Detective = `n ≥ 8 ? max(1, floor(n × 0,125)) : 0` → 12: **1** (AN).
   - Pass nicht erfüllbar (Kontingent voll) → Pass wird **nicht** verbraucht, `ttt.pass.not_used` (AN).
5. Title + Chat-Rollentext (B), Startmeldung „Runde startete mit {n}/{max} Spielern“ (B). Traitor erhalten Liste der Mit-Traitor (AN).
6. Rundentimer **10:00** (B). Keine Verlängerung (AN; GMod-Haste nicht übernommen).
7. Tod: kein Kill-Broadcast; Opfer erhält `ttt.death.killed_by` (B), Zuschauer. Leiche + Hologramm am Todesort.
8. Leiche untersuchen (Rechtsklick): Detective **sofort** identifiziert, alle anderen erst nach **[30 s]** seit Tod (B Prinzip, Zeit AN). Identifizierung → Broadcast `ttt.body.found` mit Rolle (B), Finder erhält die **Rest-Shoppunkte** der Leiche (B, in eigene Punkteart T/D, Innocent: verfallen, AN). Detective sieht zusätzlich Killer-Name im Hologramm („Getötet von“) (A).
9. Sieg: alle Traitor tot → Innocents+Detectives; alle Nicht-Traitor tot → Traitor; Zeitablauf → **Innocents** (B).
10. ENDING: `ttt.win.*` (B Struktur), Rollenauflösung aller Spieler (AN), Karma-Abrechnung, Coins, `common.restart` 15 s (B).

**6.3.2 Karma**

| Killer → Opfer | Δ Karma | Beleg |
|---|---|---|
| Innocent → Innocent | −20 | B |
| Innocent → Detective | −50 | B |
| Innocent → Traitor | +20 | B |
| Detective → Innocent | −20 | B |
| Detective → Detective | −50 | B |
| Detective → Traitor | +20 und +2 D-Punkte | B |
| Traitor → Traitor | −50 | B |
| Traitor → Innocent/Detective | +10 | AN |
| Rundensieg der eigenen Seite (überlebt) | +20 | AN |
| Runde überlebt | +10 | AN |

Start **1000**, Untergrenze **0**, keine Obergrenze (B: „1.210“ gesehen). Max. Innocent-Gewinn pro Runde ≈ 100 (B) → Deckel **+100 pro Runde** (A). Wirkung: Karma < **500** → keine Pässe einlösbar (AN); sonst nur Anzeige.

**6.3.3 Punkte**

| Ereignis | T-Punkte | D-Punkte | Beleg |
|---|---|---|---|
| Start | 1 | 1 | AN |
| Traitor tötet Innocent | +1 | – | AN |
| Traitor tötet Detective | +2 | – | AN |
| Detective tötet Traitor | – | +2 | B |
| Leiche identifiziert | + Restpunkte der Leiche | + Restpunkte | B |

**6.3.4 Map-Truhen/Waffen (AN, Genre):** normale Truhe → zufällig Holzschwert/Steinschwert/Bogen/Pfeile ×32 (B: Pfeile 32 in Hotbar); Endertruhe (selten, max 2 pro Map) → Eisenschwert. Spielerstart: leer bis auf Shop-Item (T/D). Hotbar-Beleg 2017: Schwert, Bogen, Pfeile 32, Holzschwert.

**6.3.5 Shops** (Namen eigen; Original-Name in Klammern)

| Shop | Item | Preis | Limit/Runde | Wirkung | Beleg |
|---|---|---|---|---|---|
| T | Medipack | 1 | 3 | heilt 2,5 Herzen | B |
| T | Radar | 1 | 1 | Kompass auf gewählten Spieler, Aktualisierung [alle 10 s] | B |
| T | Creeper-Pfeile | 3 (legacy 2) | 2 | [5] Pfeile, Treffer spawnt Creeper/explodiert [Kraft 2] | B (Preis), Wirkung AN |
| T | Verkleidung (Disguiser) | 3 | 1 | Nametag verborgen, [60 s] | B Preis, Dauer AN |
| T | Tester-Täuscher (Tester-Spoofer) | 4 | 1 | nächster Tester-Durchgang zeigt **grün** | B |
| T | Schneemann (Snowman) | 4 | 1 | [Schneegolem-Begleiter, Schneebälle mit Rückstoß] | B Preis, Wirkung AN |
| T | Märtyrer (Martyr) | 1 | 1 | Explosion beim eigenen Tod [Kraft 3] | B Preis, Wirkung A |
| T | Landmine | [2] | 2 | Druckplatte, Explosion bei Gegner | AN |
| T | Teleport-Pistole | [2] | 1 | [Tausch/Teleport an Treffpunkt] | AN |
| T | Stampf-Stiefel (Stomper-Boots) | [2] | 1 | Fallschaden auf getroffene Spieler | AN |
| T | Blitzstab (Lightning Staff) | [3] | 1 | [1 Blitz, 4 Herzen] | AN |
| T | Stolperdraht (Trip Wire) | [1] | 2 | Stolperdraht-Alarm/Falle | AN |
| T | Feuerprobe (Ordeal) | [3] | 1 | [AN, s. Offene Punkte] | AN |
| T | Eisschwert (Ice Sword) | [2] | 1 | Treffer Langsamkeit [II 3 s] | AN |
| T | Fake-Truhe | 4 | – | **entfernt** (nur `legacy`) | B |
| D | Zufalls-Tester (Random Tester) | 2 | 1 | testet **15 s** nach Benutzung einen zufälligen Lebenden, Ergebnis an Detective | B |
| D | Super-Identifizierer | 2 | 1 | [Leiche: Killer + Waffe sofort] | B Preis |
| D | Traitor-Detektor | [2] | 1 | [Rechtsklick Spieler: rot/grün], zerbricht nach **60 s** | B 60 s |
| D | Medipack | [1] | 3 | wie T | AN |

**6.3.6 Pässe:** Traitor-Pass **3 Vote-Keys** im Vote-Shop (B; Vote-Shop = Kern-Spec). Detective-/Innocent-Pass Preis [2 Vote-Keys] (AN). Max **1 Pass pro Runde und Spieler** (AN).

**6.3.7 Traitor-Tester / -Falle (Map):** Tester-Raum: Spieler betritt Druckplatten-Kabine, Knopf drücken → Türen **5 s** zu, Glasfarbe grün/rot (B), Chat `ttt.tester.entered` (B). Traitor-Falle: versteckter Knopf (Map-Markierung), nur Traitor nutzbar, entfernt Boden der Testerkabine für [3 s] (B Prinzip). Freiwillig; erzwungenes Testen nicht technisch geregelt (B Regel).

Map-Anforderungen TTT: 12–24 Spawns, 1 Tester mit Falle, ≥ 15 Waffentruhen, ≤ 2 Endertruhen, Wartelobby, Map-Grenze. Maps eigen (Original: Winkelgasse/Haus/Valley entfernt, Teenroom, Lotus u. a. – nicht nachbauen).

### 6.4 Cores

**Ablauf**
1. WAITING, Mindestspieler **8** (AN), max 16 (8 v 8, B). Teamwahl per Hotbar-Wolle (AN) oder automatisch.
2. Countdown 60 s (AN). Teleport auf Team-Spawn (Himmelsinseln, B).
3. Jedes Team hat **k Cores** (Beacons, je Map 1–4; B „LeftCore/RightCore“ = 2) mit Namen aus Map-Datei.
4. Core abbauen nur durch Gegner; Abbau-Zeit Vanilla (Beacon) (AN). Angreifer im Radius **[8] Blöcke** um einen **gegnerischen** Core erhalten **Abbaulähmung I** (Mining Fatigue), solange sie im Radius sind (B „Abbauschwäche in der Nähe gegnerischer Cores“; Radius AN).
5. Angriffsalarm: sobald ein Gegner den Core schlägt/abbaut → Chat + Ton an Verteidiger-Team, max. alle [5 s] (B Prinzip).
6. Core zerstört → Broadcast, Scoreboard ✘.
7. Respawn: sofort (AN [3 s]) mit Start-Kit; **Upgrade** der Respawn-Ausrüstung durch Abbau eines **Eisen-/Gold-/Diamantblocks** auf der Map (B): Eisenblock → Eisen-Stufe (Eisenschwert, Eisenrüstung), Goldblock → Gold-Stufe (AN Inhalt), Diamantblock → Diamant-Stufe (AN). Stufe pro Spieler.
8. Bogen-Kill → **+4 Pfeile** (B). Map-Truhen mit Extras (B).
9. Sieg: erstes Team, das **alle** gegnerischen Cores zerstört (B). Zeitlimit **[60 min]** → Team mit mehr übrigen Cores, sonst Unentschieden (AN).
10. Stats (B Chat-Ausgabe `/stats`, 30 Tage): Position im Ranking, Kills, Deaths, Zerstörte Cores, Gespielte Spiele, Gewonnene Spiele, Siegwahrscheinlichkeit in Prozent.

**Zahlen:** Teamgröße 8; Start-Kit (B Bild): Steinschwert, Bogen, 64 Holz, 16 Pfeile, Goldapfel, Essen ×32, Axt, Spitzhacke; Cores-Level: XP = 10/Kill + 50/Core + 100/Sieg, Level n benötigt `500 × n` XP (AN). Coins: 10 Teilnahme, 10/Kill, 50/Core, 100 Sieg (AN). Trolling (Eigenschaden am Team) → kein Teamschaden technisch (AN).

Map-Anforderungen: 2 Teams × Spawn, 1–4 Cores je Team (benannt), Erz-Upgradeblöcke (je ≥ 1 Eisen/Gold/Diamant pro Seite), Truhen, Map-Grenze, Void-Tod.

### 6.5 MasterBuilders

**Ablauf (E26)**
1. WAITING, Mindestspieler **4** (AN), max **12** (AN). NPC „Themen“ in Wartelobby (B).
2. Countdown 60 s (AN). Teleport je Spieler auf eigenen Plot.
3. **Themenwahl**: 3 zufällige Begriffe aus aktivem Pool (B), **20 s** (AN), Mehrheit; Gleichstand → Zufall.
4. **Bauphase** Kreativmodus **8:00** (B: 7 min + 1 min seit 07/2020); Scoreboard „Zeit“ zählt herunter. `/finish` aller Spieler → sofort Bewertung (B). Plot **[32×32]**, Bauhöhe [32], Fliegen erlaubt, verbotene Blöcke: TNT, Lava-/Wassereimer außerhalb Plot, Spawn-Eier, Befehlsblöcke (AN).
5. **Bewertung**: Plots in **zufälliger Reihenfolge** (B), alle Spieler werden auf den Plot teleportiert, je Plot **[15 s]**; Erbauer anonym (B); eigener Plot nicht bewertbar. 6 Noten (B) = Punkte: Grauenvoll 0, Schlecht 1, Okay 2, Gut 3, Super 4, Legendär 5 (AN). Keine Stimme → Okay (AN).
6. Nach jedem Plot: Hologramm Erbauer-Name (B Name über Plot).
7. Ende: Rangliste nach Punktsumme; Gleichstand → mehr „Legendär“, dann Zufall (AN). Top 3 im Chat (AN), Sieger-Teleport auf Gewinner-Plot.
8. Coins: Platz 1 100, 2 60, 3 40, Teilnahme 10 (AN).

Themenpool: Start **≥ 400** Begriffe (B: 300+ und +100), eigene Liste. Vorschläge → Status 0, Freischaltung durch Team (`retromc.mb.theme.admin`) oder automatisch ab Bewertung ≥ +10 bei ≥ 15 Stimmen (AN).

Legacy-Option: 5 Noten (2018), Bauzeit 7:00 (B).

### 6.6 ClanWar

**Ablauf**
1. Leiter A: `/cw <LeiterB> <Spiel>` (B). Prüfungen (B): A und B jeweils Party-Leiter; alle Party-Mitglieder je Seite im selben Clan; Clans verschieden (AN). `/fcw` ohne Clan-Prüfung (B).
2. Größe: Standard **4 v 4** (B). Erlaubt 2–4 je Seite, beide Partys gleich groß (AN; Community 2v2/3v3/4v4 B).
3. Herausforderung gilt **60 s** (AN). Annahme → Controller reserviert Server (`clanwar` mit Ziel-Modus `game`), verschiebt beide Partys, Map aus `/cw maps` zufällig (AN) oder Herausforderer-Wahl als optionales 3. Argument (AN).
4. Runde im Ziel-Modus mit Teams = Clans; **60 Minuten** Limit (B). Danach **5 Minuten DeathMatch** (B) (Modus-eigene DM; BedWars: Betten zerstört + Border, AN).
5. Kein Sieger nach DM → Unentschieden (AN).
6. Zuschauer: `/cw join <Tag>` (B).
7. Abbruch `/cw abort` (B) → Niederlage für abbrechenden Clan (AN). **3 Abbrüche in 24 h** → CW-Sperre **24 h** für den Clan (AN; B „Ban-Status“ existiert).
8. Spieler verlässt Runde → kein Ersatz, Runde läuft weiter (AN).

**Elo:** Formel, Start und K aus **Spec 01 §6.6** (`EloCalculator`, Start 1000, **K = 50**, Nullsumme, je Modus in `social.clan_elo`); S = 1 / 0,5 / 0. FunClanWar: keine Elo. Schreiben über `ClanService.recordWar` (Spec 01). Rangliste `/cw top` = `social.clan_elo` nach Elo.

Spiele (`/cw maps`): Standard **BedWars** (CWBW), Optionen Cores, SurvivalGames (CWCores/CWSG historisch, B); EG/TTT/MB nicht CW-fähig (A).

---

## 7. Nachrichten-Katalog

Präfixe (Key `prefix.<modus>`): Klammern `§f[`…`§f]` (einheitlich nach Spec 02 `prefix.mode`/§10 #4; früher hier §8), Modusname farbig, danach Leerzeichen; Fließtext **§7**, Zahlen **§e**, Spielernamen in **Rangfarbe** `{name}` (B). Präfix-Farben: SurvivalGames **§b** (B §b/§3), EnderGames **§5** (B), TTT **§c** (B), Cores **§b** (B), MasterBuilders **§9** (B), ClanWar **§6** (AN). „Standardtext“ = unser Text; Original-Referenz nur zum Abgleich, wird **nicht** ausgeliefert, wenn markant.

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| prefix.sg | `§f[§bSurvivalGames§f] ` | – | `[SurvivalGames]` | belegt |
| prefix.eg | `§f[§5EnderGames§f] ` | – | `[EnderGames]` | belegt |
| prefix.ttt | `§f[§cTTT§f] ` | – | `[TTT]` | belegt |
| prefix.cores | `§f[§bCores§f] ` | – | `[Cores]` | belegt |
| prefix.mb | `§f[§9MasterBuilders§f] ` | – | `[MasterBuilders]` | belegt |
| prefix.cw | `§f[§6ClanWar§f] ` | – | – | Annahme |
| **Hinweis** | Alle `common.*`-Zeilen dieser Tabelle sind nur noch Referenz: verbindlich ist **Spec 03 §7.2** (gleichnamige Keys werden nicht erneut definiert). Neue Keys von hier (`common.join.round`, `common.players_online`→`common.online`, `common.map_info`→`common.map`, `common.win.solo`→`common.win.player`) gelten als Aliase des Rahmen-Keys | – | – | – |
| common.join | `§7» {name} §7hat das Spiel betreten` | wie Original (funktional) | „» <Name> hat das Spiel betreten“ (SG) / „…die Runde betreten“ (EG) | belegt |
| common.join.round | `§7» {name} §7hat die Runde betreten` | wie Original | EG/TTT-Lobby | belegt |
| common.quit | `{prefix}{name} §7hat die Runde verlassen` | wie Original | MB | belegt |
| common.players_online | `{prefix}§7Spieler online §e{n}§7/§e{max}` | wie Original | „Spieler online 22/24“ | belegt |
| common.map_info | `{prefix}§7Map §e{map} §7von §e{builder}` | wie Original | „Map SURO von SURO-Projekt“ | belegt |
| common.countdown | `{prefix}§7Die Runde beginnt in §e{n} §7Sekunden` | wie Original | „Die Runde beginnt in 50 Sekunden“ | belegt |
| common.countdown.one | `{prefix}§7Die Runde beginnt in §e1 §7Sekunde` | – | – | abgeleitet |
| common.countdown.cancel | `{prefix}§cZu wenige Spieler – der Countdown wurde angehalten` | eigen | – | Annahme |
| common.not_in_lobby | `{prefix}§cDas geht nur in der Wartelobby` | eigen | – | Annahme |
| common.player_unknown | `{prefix}§cDieser Spieler wurde nicht gefunden` | eigen | – | Annahme |
| common.start.done | `{prefix}§aDer Countdown wurde verkürzt` | eigen | – | Annahme |
| common.start.too_few | `{prefix}§cEs sind noch nicht genug Spieler da` | eigen | – | Annahme |
| common.spectator | `{prefix}§7Du bist nun Zuschauer!` | wie Original | „Du bist nun Zuschauer!“ | belegt |
| common.remaining | `{prefix}§e{n} §7verbleibende Spieler` | wie Original | „11 verbleibende Spieler“ | belegt |
| common.kill | `{prefix}{victim} §7wurde von {killer} §7getötet` | wie Original | „X wurde von Y getötet“ | belegt |
| common.death | `{prefix}{victim} §7ist gestorben` | wie Original | „[Cores] … ist gestorben“ | belegt |
| common.killer_health | `{prefix}§7Leben von {killer}§7: {hearts}` | `{hearts}` = 10× `§c❤` voll / `§7❤` leer (halbe Herzen aufrunden) | „Leben von X ♥♥♥…“ | belegt |
| common.restart | `{prefix}§7Der Server startet in §e{n} §7Sekunden neu` | wie Original | „Der Server startet in 15 Sekunden neu“ | belegt |
| common.win.solo | `{prefix}{name} §7hat die Runde gewonnen!` | eigen | – | Annahme |
| common.win.team | `{prefix}§7Team §e#{team} §7({names}§7) hat die Runde gewonnen!` | eigen | – | Annahme |
| common.coins | → Spec 02 `coins.round` (`§7[§eCoinSystem§7] §6+{n} Coins`) | Kern-Spec | „[CoinSystem] +34 Coins“ | belegt |
| common.coins.boost | → Spec 02 `coins.boost` | Kern-Spec | „+12 Coins durch …-Boost“ | belegt |
| sg.start.countdown | `{prefix}§7Der Kampf beginnt in §e{n} §7Sekunden` | eigen | – | Annahme |
| sg.start.go | `{prefix}§aDie Spiele haben begonnen. Viel Glück!` | eigen | – | Annahme |
| sg.grace.countdown | `{prefix}§7Die Schutzzeit endet in §e{n} §7Sekunden` | wie Original (funktional) | EG/TTT-Wortlaut | abgeleitet |
| sg.grace.end | `{prefix}§7Die Schutzzeit ist vorbei` | eigen | – | Annahme |
| sg.refill | `{prefix}§aAlle Truhen wurden neu befüllt!` | eigen | Regel „neu aufgefüllt“ | abgeleitet |
| sg.refill.warn | `{prefix}§7Die Truhen werden in §e{n} §7Sekunden neu befüllt` | eigen | – | Annahme |
| sg.dm.countdown | `{prefix}§7Das Deathmatch beginnt in §e{n} §7Sekunden` | wie Original (funktional) | „Das Deathmatch beginnt in 2“ (OCR) | belegt |
| sg.dm.start | `{prefix}§cDas Deathmatch hat begonnen!` | eigen | – | Annahme |
| sg.dm.timeout | `{prefix}§7Zeit abgelaufen – {name} §7hat den meisten Schaden verursacht` | eigen | Forum (Schaden) | abgeleitet |
| sg.dm.draw | `{prefix}§7Zeit abgelaufen – die Runde endet unentschieden` | eigen | Forum (Unentschieden) | abgeleitet |
| sg.team.actionbar | `§7Team #{n}` | wie Original | „Team #2“ | belegt |
| sg.teams.allowed | `§aTeams erlaubt` | wie Original (legacy) | „Teams erlaubt“ | belegt |
| sg.build.denied | `{prefix}§cHier kannst du nichts abbauen` | eigen | Regel | Annahme |
| eg.countdown | `{prefix}§7Die EnderGames starten in §e{n} §7Sekunden` | wie Original (funktional) | „starten in 18 Sekunden“ (E26) / „beginnen in 10 Sekunden“ (E17) | belegt |
| eg.grace.countdown | `{prefix}§7Die Schutzzeit endet in §e{n} §7Sekunden` | wie Original (funktional) | „Die Schutzzeit endet in 40 Sekunden“ | belegt |
| eg.grace.end | `{prefix}§7Die Schutzzeit ist vorbei` | eigen | „Die Schutzzeit ist beendet“ | belegt (Struktur) |
| eg.grace.end2 | `{prefix}§7Ab jetzt kann jeder verletzt werden` | eigen | „Alle Spieler sind nun verwundbar“ | belegt (Struktur) |
| eg.teaming | `§aTeams sind erlaubt` | eigen | „Teaming ist erlaubt“ | belegt |
| eg.victim_kit | `{prefix}§7Benutztes Kit: §6{kit}` | wie Original (funktional) | „Benutztes Kit: Yeti“ | belegt |
| eg.switch | `{prefix}§7Du hast die Position mit {name} §7getauscht` | eigen | „Du wurdest mit X getauscht“ | belegt |
| eg.tracker | `§6Spieler {name} §6geortet: §d{n} §6Blöcke` | eigen | „Player X getrackt: n Blöcke“ | belegt |
| eg.tracker.none | `{prefix}§cKein Gegner in Reichweite` | eigen | – | Annahme |
| eg.dm.countdown | `{prefix}§7Das Deathmatch beginnt in §e{n} §7Sekunden` | wie Original (funktional) | Scoreboard „Deathmatch in“ | abgeleitet |
| eg.dm.start | `{prefix}§cDas Deathmatch hat begonnen!` | eigen | – | Annahme |
| eg.kit.selected | `{prefix}§7Du hast das Kit §d{kit} §7gewählt` | eigen | OCR „Du hast das Kit gewä…“ | belegt (Struktur) |
| eg.kit.random | `{prefix}§7Dein zufälliges Kit: §d{kit}` | eigen | – | Annahme |
| eg.kit.bought | `{prefix}§aDu hast das Kit §d{kit} §afür §e{price} Coins §agekauft` | eigen | – | Annahme |
| eg.kit.no_coins | `{prefix}§cDir fehlen §e{missing} Coins` | eigen | – | Annahme |
| eg.kit.not_owned | `{prefix}§cDieses Kit besitzt du noch nicht` | eigen | – | Annahme |
| eg.kit.unknown | `{prefix}§cDieses Kit gibt es nicht` | eigen | – | Annahme |
| eg.kit.cooldown | `§7Kit-Fähigkeit: §cbereit in §e{n} Sekunden` | eigen (Actionbar) | SkyWars-Format „Kit-Fähigkeit: Nutzbar in 22 Sekunden“ | abgeleitet |
| eg.kit.grace | `§7Kit-Fähigkeit: §6nicht während der Schutzzeit` | eigen | SkyWars „Nicht in der Schutzzeit nutzbar“ | abgeleitet |
| eg.kit.morph | `{prefix}§7Du hast die Fähigkeit von §d{kit} §7übernommen` | eigen | – | Annahme |
| eg.achievement | `{prefix}§aErfolg freigeschaltet: §e{name}` | eigen | – | Annahme |
| ttt.grace.countdown | `{prefix}§7Die Schutzzeit endet in §e{n} §7Sekunden` | wie Original | B | belegt |
| ttt.grace.countdown.one | `{prefix}§7Die Schutzzeit endet in §e1 §7Sekunde` | wie Original | B | belegt |
| ttt.role.innocent | `{prefix}§eDu bist ein §aInnocent` | wie Original (funktional) | „Du bist ein Innocent“ | belegt |
| ttt.role.detective | `{prefix}§eDu bist ein §9Detective` | wie Original | abgeleitet | abgeleitet |
| ttt.role.traitor | `{prefix}§eDu bist ein §cTraitor` | wie Original | abgeleitet | abgeleitet |
| ttt.goal.innocent | `{prefix}§eBeobachte genau und finde alle Traitor` | eigen | „Finde und eliminiere durch Beobachten alle Traitor“ | belegt |
| ttt.goal.detective | `{prefix}§eUntersuche Leichen und entlarve die Traitor` | eigen | – | Annahme |
| ttt.goal.traitor | `{prefix}§eSchalte unbemerkt alle Innocents und Detectives aus` | eigen | – | Annahme |
| ttt.traitor.mates | `{prefix}§7Deine Mit-Traitor: {names}` | eigen | – | Annahme |
| ttt.title.innocent.sub | `§7Hilf den Detectives, die Traitor zu entlarven` | eigen | „Unterstütze die Detectives dabei die Traitor zu enttarnen“ | belegt |
| ttt.title.detective.sub | `§7Finde die Traitor mit deinen Werkzeugen` | eigen | – | Annahme |
| ttt.title.traitor.sub | `§7Töte alle anderen, ohne aufzufliegen` | eigen | – | Annahme |
| ttt.round.started | `{prefix}§eDiese Runde begann mit §b{n}/{max} §eSpielern` | eigen | „Diese Runde startete mit 12/12 Spielern“ | belegt |
| ttt.death.killed_by | `{prefix}§cDu wurdest von {killer} §cgetötet` | wie Original (funktional) | B | belegt |
| ttt.body.found | `{prefix}§7Die Leiche von {rolefarbe}{name} §7({rolefarbe}{role}§7) wurde gefunden` | wie Original (funktional) | B | belegt |
| ttt.body.wait | `{prefix}§cDu kannst diese Leiche erst in §e{n} §cSekunden untersuchen` | eigen | – | Annahme |
| ttt.body.points | `{prefix}§7Du hast §e{n} §7Punkte aus der Leiche erhalten` | eigen | – | Annahme |
| ttt.holo.line1 | `§7Leiche von §e{name}{rolle?}` | eigen-gleich | B | belegt |
| ttt.holo.line2 | `§7Tot seit §e{mm:ss}` | wie Original | B | belegt |
| ttt.holo.line3 | `§7Getötet von §e{killer\|Ermittlung läuft}` | wie Original | B | belegt |
| ttt.tester.entered | `{prefix}{name} §7hat den Traitor-Tester betreten` | wie Original (funktional) | B | belegt |
| ttt.tester.busy | `{prefix}§cDer Tester ist gerade belegt` | eigen | – | Annahme |
| ttt.win.traitor | `{prefix}§cDie Traitor §7haben gewonnen` | eigen-gleich | „Die Traitor haben das Spiel gewonnen“ | belegt |
| ttt.win.traitor.reason | `{prefix}§7Alle Innocents wurden ausgeschaltet` | eigen | „…haben alle Innocents eliminiert“ | belegt |
| ttt.win.innocent | `{prefix}§aDie Innocents §7haben gewonnen` | eigen | – | abgeleitet |
| ttt.win.innocent.reason | `{prefix}§7Alle Traitor wurden ausgeschaltet` | eigen | – | Annahme |
| ttt.win.timeout | `{prefix}§7Die Zeit ist abgelaufen – §adie Innocents §7gewinnen` | eigen | Regel | abgeleitet |
| ttt.roles.reveal | `{prefix}§7Traitor dieser Runde: {names}` | eigen | – | Annahme |
| ttt.karma.change | `{prefix}§7Karma: §b{karma} §7({sign}{delta})` | eigen | – | Annahme |
| ttt.karma.show | `{prefix}§7Karma von {name}§7: §b{karma}` | eigen | – | Annahme |
| ttt.pass.redeemed | `{prefix}§7Dein {rollenfarbe}{role}§7-Pass wird in dieser Runde eingelöst` | eigen | – | Annahme |
| ttt.pass.none | `{prefix}§cDu hast keinen {role}-Pass. Pässe gibt es im Vote-Shop` | eigen | – | Annahme |
| ttt.pass.already | `{prefix}§cDu hast in dieser Runde bereits einen Pass eingelöst` | eigen | – | Annahme |
| ttt.pass.limit | `{prefix}§cFür diese Rolle wurden schon genug Pässe eingelöst` | eigen | – | Annahme |
| ttt.pass.not_used | `{prefix}§7Dein Pass konnte nicht genutzt werden und bleibt erhalten` | eigen | – | Annahme |
| ttt.pass.karma | `{prefix}§cDein Karma ist zu niedrig für Pässe (mind. §e500§c)` | eigen | – | Annahme |
| ttt.shop.bought | `{prefix}§7Gekauft: §e{item} §7(§e-{p} §7Punkte)` | eigen | – | Annahme |
| ttt.shop.no_points | `{prefix}§cDir fehlen §e{n} §cPunkte` | eigen | – | Annahme |
| ttt.shop.limit | `{prefix}§cDieses Item kannst du nicht mehr kaufen` | eigen | – | Annahme |
| ttt.shop.no_shop | `{prefix}§cDu hast keinen Zugang zu einem Shop` | eigen | – | Annahme |
| ttt.chat.traitor | `§8[§cT§8] {name}§7: §f{msg}` | eigen | – | Annahme |
| ttt.chat.detective | `§8[§9D§8] {name}§7: §f{msg}` | eigen | – | Annahme |
| ttt.chat.not_traitor | `{prefix}§cDiesen Chat können nur Traitor nutzen` | eigen | – | Annahme |
| ttt.chat.not_detective | `{prefix}§cDiesen Chat können nur Detectives nutzen` | eigen | – | Annahme |
| ttt.random_tester.result | `{prefix}§7Testergebnis: {name} §7ist {farbe}{role}` | eigen | – | Annahme |
| cores.core.attacked | `{prefix}§cDein Core §e{core} §cwird angegriffen!` | eigen | Regel „Signal“ | abgeleitet |
| cores.core.destroyed | `{prefix}§7Der Core §e{core} §7von {teamfarbe}Team {team} §7wurde von {name} §7zerstört` | eigen | – | Annahme |
| cores.upgrade | `{prefix}§aDeine Ausrüstung wurde auf §e{tier} §averbessert` | eigen | – | Annahme |
| cores.win | `{prefix}{teamfarbe}Team {team} §7hat alle Cores zerstört und gewinnt!` | eigen | – | Annahme |
| cores.stats.header | `§7-= Statistiken von {name} §7({days} Tage) =-` | wie Original-Struktur | B | belegt |
| cores.stats.line | `§7{label}§8: §e{value}` (Labels: Position im Ranking, Kills, Deaths, Zerstörte Cores, Gespielte Spiele, Gewonnene Spiele, Siegwahrscheinlichkeit `{p} Prozent`) | wie Original (funktional) | B | belegt |
| mb.theme.vote | `{prefix}§7Wähle eines von drei Themen!` | eigen | – | Annahme |
| mb.theme.chosen | `{prefix}§7Das Thema lautet: §a{theme}` | eigen | – | Annahme |
| mb.build.start | `{prefix}§7Du hast §e{min} §7Minuten Zeit. Leg los!` | eigen | – | Annahme |
| mb.build.warn | `{prefix}§7Noch §e{n} §7Sekunden Bauzeit` | eigen | – | Annahme |
| mb.finish.voted | `{prefix}{name} §7ist fertig (§e{n}§7/§e{max}§7)` | eigen | – | Annahme |
| mb.finish.all | `{prefix}§aAlle sind fertig – die Bewertung beginnt` | eigen | – | Annahme |
| mb.finish.already | `{prefix}§cDu hast bereits abgestimmt` | eigen | – | Annahme |
| mb.finish.wrong_phase | `{prefix}§cDas geht nur während der Bauphase` | eigen | – | Annahme |
| mb.rate.start | `{prefix}§7Bewerte dieses Bauwerk mit den Items in deiner Hotbar` | eigen | – | Annahme |
| mb.rate.given | `§7Deine Bewertung: {notenfarbe}{note}` (Actionbar) | eigen | „Okay“ als Itemname | belegt (Struktur) |
| mb.rate.builder | `{prefix}§7Dieses Bauwerk stammt von §6{name}` | eigen | Hologramm | abgeleitet |
| mb.result.line | `{prefix}§e#{place} {name} §7(§e{points} §7Punkte)` | eigen | GuessIt-Rangliste „#1 Name (41 Punkte)“ | abgeleitet |
| mb.theme.submitted | `{prefix}§aDein Vorschlag §e{theme} §awurde eingereicht` | eigen | – | Annahme |
| mb.theme.none | `{prefix}§7Gerade gibt es nichts zu bewerten` | eigen | – | Annahme |
| cw.challenge.sent | `{prefix}§7Du hast {name} §7zu einem ClanWar in §e{game} §7herausgefordert` | eigen | – | Annahme |
| cw.challenge.received | `{prefix}§e{clan} §7fordert euch zu einem ClanWar in §e{game} §7heraus ({size}v{size}) §a[ANNEHMEN] §c[ABLEHNEN]` | eigen | Klick-Zeilen-Stil `[MITSPIELEN]` | abgeleitet |
| cw.challenge.accepted | `{prefix}§aDer ClanWar gegen §e{clan} §abeginnt gleich` | eigen | – | Annahme |
| cw.challenge.denied | `{prefix}§c{name} §chat die Herausforderung abgelehnt` | eigen | – | Annahme |
| cw.challenge.expired | `{prefix}§7Die Herausforderung ist abgelaufen` | eigen | – | Annahme |
| cw.err.not_leader | `{prefix}§cDu musst Leiter einer Party sein` | eigen | Regel | belegt (Regel) |
| cw.err.mixed_party | `{prefix}§cAlle Party-Mitglieder müssen in deinem Clan sein` | eigen | Regel | belegt (Regel) |
| cw.err.target_not_leader | `{prefix}§c{name} §cist kein Party-Leiter oder in keinem Clan` | eigen | Regel | belegt (Regel) |
| cw.err.same_clan | `{prefix}§cDu kannst deinen eigenen Clan nicht herausfordern` | eigen | – | Annahme |
| cw.err.size | `{prefix}§cBeide Partys müssen gleich groß sein (§e2§c–§e4 §cSpieler)` | eigen | – | Annahme |
| cw.err.banned | `{prefix}§cEuer Clan ist bis §e{until} §cfür ClanWars gesperrt` | eigen | – | Annahme |
| cw.err.active | `{prefix}§cEuer Clan spielt bereits einen ClanWar` | eigen | – | Annahme |
| cw.err.game_unknown | `{prefix}§cDieses Spiel ist nicht verfügbar. Siehe §e/cw maps` | eigen | – | Annahme |
| cw.err.no_match | `{prefix}§cDu bist in keinem laufenden ClanWar` | eigen | – | Annahme |
| cw.err.no_match_for_tag | `{prefix}§cDer Clan §e{tag} §cspielt gerade keinen ClanWar` | eigen | – | Annahme |
| cw.maps.header | `{prefix}§7Verfügbare Spiele und Maps:` | eigen | – | Annahme |
| cw.maps.line | `§8- §e{game}§7: {maps}` | eigen | – | Annahme |
| cw.status.free | `{prefix}§aDein Clan ist für ClanWars freigegeben` | eigen | – | Annahme |
| cw.status.banned | `{prefix}§cGesperrt bis §e{until} §7(Grund: §e{reason}§7)` | eigen | – | Annahme |
| cw.abort.done | `{prefix}§c{clan} §chat den ClanWar abgebrochen` | eigen | – | Annahme |
| cw.dm.start | `{prefix}§cDie Zeit ist um – §e5 Minuten §cDeathMatch!` | eigen | Regel | belegt (Regel) |
| cw.result | `{prefix}§e{winner} §7gewinnt gegen §e{loser} §7(Elo §a+{d} §7/ §c-{d2}§7)` | eigen | – | Annahme |
| cw.top.line | `§e#{place} §7{clan} §8[§e{tag}§8] §7– §b{elo} Elo` | eigen | – | Annahme |

**Meldungen gesamt: 150.**

---

## 8. Schnittstellen

### 8.1 Von diesem Bereich **genutzt** (müssen andere Specs liefern)

| Schnittstelle | Anbieter | Signatur (Java, Paket) | Nutzung |
|---|---|---|---|
| Rundenablauf | game-framework | `GameLifecycle`, `GameState`, `MiniGamePlugin#definition()` | Phasen, Countdown, ENDING→Stopp |
| Phasen-Timer | game-framework (Spec 03 §8.1) | `PhaseTimer.start(String id, Duration d, IntConsumer tick, Runnable end)`; `announceAt(int... seconds)` | alle Countdowns |
| Teams | game-framework (Spec 03) | `TeamAllocator.allocate(List<Group>, List<TeamSlot>)` | SG-2er-Teams, Cores, CW |
| Zuschauer | game-framework.spectate | `SpectatorService.makeSpectator(Player)`; GUI 5.8 | alle |
| Loot | game-framework.loot (NEU-Paket) | `LootTable.load(yaml)`, `LootTable.fill(Inventory, Random)` | SG, EG, TTT-Waffentruhen |
| Deathmatch | game-framework.deathmatch (NEU-Paket) | `DeathmatchController(trigger: alive≤N or time, arena: Location[], duration)` | SG, EG, CW |
| Achievements | game-framework.achievement (NEU-Paket) | `AchievementService.unlock(uuid, mode, id)`, `progress(uuid, mode, id, delta)` | EG, SG, Cores |
| Kampfprofil | game-framework | `CombatProfile.LEGACY_1_8` | alle außer MB |
| Scoreboard/Tab/Nametag | paper-core (Spec 02 §8.1) | `BoardService.show/update` mit Label/Wert-Paaren; `TabService.registerEntryProvider`; `NametagService.prefix/suffix` | Abschnitt 4 |
| i18n | core-i18n (Spec 02) | `I18n.render(player, key, TagResolver...)`, Legacy-§-Ausgabe aus MiniMessage | Abschnitt 7 |
| Coins | paper-core (Spec 02 §8.1) | `CoinService.payoutRound(matchId, Map<UUID,Long> base, roundPlayers)` (Boost + Meldungen), `debit(uuid, long, idemKey) → boolean` | Belohnung, Kit-Kauf |
| Vote-Keys/Vote-Shop | Kern/economy | `VoteShopService.grantItem(uuid, "ttt_traitor_pass", qty)` → schreibt `games.ttt_pass_ledger` über `TttPassService` | Pässe |
| Statistik | paper-core (Spec 02 §6.4) | `StatsService.submit(MatchResult)` (idempotent per `matchId`, über `RoundEnd.finish`, Spec 03) | alle |
| Ränge/Rechte | LuckPerms über paper-core | `player.hasPermission("retromc.…")` | `/start`, MB-Admin |
| Party/Clan | velocity-core / core-data | `PartyService.of(uuid)`, `ClanService.of(uuid)` (Tag, Rolle, Elo) | Teams, CW, Nametags |
| Queue/Server-Zuteilung | services/controller | `net:queue` Anfrage `{mode, variant, partyId}`; CW: `{mode:"clanwar", game, partyA, partyB, fun}` | Rundenstart, CW |

### 8.2 Von diesem Bereich **angeboten**

| Element | Form | Inhalt |
|---|---|---|
| Bukkit-Events (Paket `net.retromc.game.framework.event`, gemeinsam) | Eigentümer **Spec 03 §8.1**: `GamePhaseChangeEvent(old, new)`, `PlayerEliminatedEvent(victim, killer?, remaining)`, `GameEndEvent(matchId, placements)` (= früheres `GameWinEvent`) | von allen Modi gefeuert |
| SG-Events | `ChestRefillEvent(int refillNo)`, `DeathmatchStartEvent` | Achievements, Logging |
| EG-Events | `KitAssignEvent(Player, KitId)`, `KitAbilityEvent(Player, KitId, String ability)`, `EnderPearlTeleportEvent(Player)` („Teleport“-Hook für Link/Schatten/Teleporter) | Kit-Logik, Achievements |
| TTT-Events | `RoleAssignEvent(Player, Role)`, `BodyIdentifiedEvent(Player finder, UUID body, Role)`, `TesterUseEvent(Player, boolean traitor, boolean spoofed)`, `KarmaChangeEvent(UUID, int delta, String reason)` | Shop, Karma, Stats |
| Cores-Events | `CoreDamageEvent(Team, String core, Player)`, `CoreDestroyEvent(Team, String core, Player)` | Alarm, Stats, CW |
| MB-Events | `ThemeChosenEvent(String)`, `PlotRatedEvent(UUID builder, UUID rater, int grade)` | Stats |
| Services (eigene Module) | `KitRegistry` (EG; `List<KitDefinition>` aus `kits.yml`), `KitOwnershipService` (DB+Valkey), `TttPassService` (`balance`, `grant`, `consume(idem)`), `KarmaService`, `ThemeService` (MB), `ClanWarService` (velocity-core: challenge/accept/abort/elo) | – |
| Messaging `net:clanwar` (JSON) | `{"type":"ChallengeCreated","from":uuid,"to":uuid,"game":"bedwars","fun":false,"size":4,"ts":…}`; `ClanWarStarted{matchId,server,clanA,clanB}`; `ClanWarEnded{matchId,winner,endReason,eloDeltaA,eloDeltaB}`; `ClanWarAborted{matchId,byClan}` | Proxy ↔ Spielserver ↔ Web/Discord |
| Server-Status | `SERVER_STATE` (Spec 08) über `ServerLifecycle.setState`, Feld `online` | Navigator/Queue |
| Konfiguration je Modus | `plugins/<Plugin>/config.yml` (Timer, Spielerzahlen, Coins, Ära-Schalter `era: E26\|legacy`), Map-Dateien `maps/<map>/map.json` (Spawns, Truhen-Tiers, Cores, DM-Arena, Tester) | Tools/map-exporter prüft Pflichtfelder |

Ära-Schalter (`era`): `E26` (Standard) / `legacy` – wirkt auf Scoreboard-Layouts (4.x), SG-Refill 10:00, EG-Präfix-Textfarbe §3 (türkis), Kit Rucksack, TTT-Karma-Scoreboard, TTT-Rundenzeit 15:00 (AN), MB 5 Noten/7:00.

---

## 9. Abnahmekriterien

**Allgemein**
1. Jedes Modus-Plugin startet auf Paper 26.3/Java 25 mit `${brandName}Core`, durchläuft WAITING→STARTING→INGAME→ENDING und stoppt den Server nach Restart-Countdown 20 s + 3 s (Spec 03 §6.10).
2. Alle Texte kommen aus core-i18n; `grep` nach „GommeHD“/„GOMMEHD“ im Quellcode und in Ressourcen = 0 Treffer.
3. Sidebar entspricht Zeile für Zeile Abschnitt 4 (Screenshot-Vergleich mit Referenzbildern; Farben exakt).
4. Statistiken landen am Rundenende in `stats.match`, `stats.match_player`, `stats.daily`, `stats.alltime` (Integrationstest mit Testcontainers-PostgreSQL).

**SurvivalGames**
5. 24 Spieler → 12 Teams à 2, Actionbar „Team #n“, Nametag `T{n} | {name}`; Party-Mitglieder im selben Team.
6. Refill-Timer startet bei 15:00 (legacy 10:00), bei 0 werden alle Truhen (auch geöffnete) neu befüllt; Scoreboard wechselt danach.
7. Blockabbau nur für Pflanzen; Setzen verboten außer TNT/Feuer.
8. DM startet bei ≤ 4 Lebenden oder 30:00; Zeitablauf → meister Schwertschaden gewinnt.
9. Pro Tod genau 3 Chatzeilen: Kill, Leben des Killers (10 Herzsymbole), verbleibende Spieler.
- Unit-Tests: `RefillScheduleTest` (Zeitpunkte 15:00/30:00 bzw. legacy 10:00), `DeathmatchTriggerTest` (alive 5→4 löst aus; Zeit 30:00 löst aus; nur einmal), `HeartsFormatterTest` (7,5 Herzen → 8 rot, 2 grau), `TeamAssignerTest` (Party 2 + 3 Solo → Teams korrekt, keine Party getrennt), `LootTableTest` (Seed fix → deterministische Befüllung, Mengen innerhalb Grenzen).

**EnderGames**
10. 49 Kits (E26) bzw. 50 (legacy) aus `kits.yml` geladen; GUI 5.1 zeigt Preise, Kauf bucht genau einmal (Idempotenz-Key) und schreibt `games.kit_owned`.
11. „Zufälliges Kit“ wählt nur gekaufte Kits; ohne Kauf Gratis-Kit.
12. Schutzzeit 120 s mit Meldungen bei 60/50/40/30/20/10/5–1; danach Scoreboard „Deathmatch in 30:00“.
13. Tod → zusätzliche Zeile „Benutztes Kit: {kit}“.
14. EnderChest-GUI 27 Slots, Titel §d; Enderman öffnet aus 6 Blöcken.
15. Switcher-Treffer tauscht Positionen beider Spieler und sendet beiden eine Meldung.
- Unit-Tests: `KitRegistryTest` (49 IDs eindeutig, Preise ∈ Preisstufen), `RandomKitTest` (nur Besitz), `LinkHeartsTest` (3 → +1 je Kill/Teleport, Deckel 20), `AssassinBackstabTest` (Winkel 30° → ×2, 90° → ×1), `SeekerConditionTest` (<5 Herzen & ≤30 Blöcke), `MorpherStackTest`, `StomperDamageTest` (Fallschaden-Cap 2 Herzen), `KangarooJumpTest` (<4 Herzen gesperrt).

**TTT**
16. 12 Spieler → 3 Traitor, 1 Detective; Pässe werden vor Zufall berücksichtigt, nicht erfüllbare Pässe bleiben erhalten.
17. `@t`/`/t` nur für Traitor sichtbar; `@d`/`/d` nur für Detectives; Zuschauer sehen keinen Teamchat.
18. Leiche: Detective identifiziert sofort, andere nach 30 s; Finder erhält Restpunkte; Broadcast mit Rolle.
19. Tester: 5 s geschlossen, grün/rot; Spoofer erzwingt grün einmalig.
20. Karma-Tabelle 6.3.2 exakt; Rundendeckel +100; Boden 0.
21. Zeitablauf 10:00 → Innocents gewinnen.
- Unit-Tests: `RoleDistributionTest` (n=6…24), `KarmaCalculatorTest` (alle 7 belegten Paare + Deckel), `PassQueueTest`, `ShopPurchaseTest` (Punkte, Limits), `BodyIdentifyTest` (Zeitsperre, Punktübertrag), `WinConditionTest`.

**Cores**
22. 8 v 8, Scoreboard mit ✔/✘ je Core, Alarm max alle 5 s, Abbaulähmung im Radius 8 um feindliche Cores.
23. Abbau Eisen-/Gold-/Diamantblock hebt Respawn-Kit-Stufe dauerhaft für die Runde; Bogen-Kill +4 Pfeile.
- Unit-Tests: `CoreStateTest`, `WinByCoresTest`, `UpgradeTierTest`, `AttackAlarmThrottleTest`.

**MasterBuilders**
24. Themenwahl aus 3, Bauzeit 8:00, `/finish` aller → sofort Bewertung; Bewertung anonym, zufällige Reihenfolge, eigener Plot ausgeschlossen.
- Unit-Tests: `ThemeVoteTest` (Gleichstand-Zufall mit Seed), `ScoreTest` (Noten 0–5, fehlende Stimme = 2), `RankingTieBreakTest`, `FinishVoteTest`.

**ClanWar**
25. `/cw` lehnt gemischte Partys, Nicht-Leiter, ungleiche Größen ab; Herausforderung verfällt nach 60 s (Valkey-TTL).
26. Elo nach Spec 01 §6.6 (K=50) wird in einer Transaktion für beide Clans in `social.clan_elo` geschrieben und in `social.clan_war` protokolliert; FunClanWar ändert Elo nicht.
27. 3 Abbrüche/24 h → `games.cw_ban` 24 h, `/cw status` zeigt Sperre.
- Unit-Tests: `EloCalculatorTest` (= Spec 01 A3: 1000 vs 1000 Sieg → +25/−25; 1200 vs 1000 Sieg → +12), `ChallengeValidatorTest`, `AbortBanRuleTest`.

---

## 10. Offene Punkte (jeweils mit DEFAULT-ENTSCHEIDUNG)

| # | Offener Punkt | Widerspruch / Lücke | DEFAULT-ENTSCHEIDUNG |
|---|---|---|---|
| 1 | SG-Spielerzahl & Teams | 16 (Regeltext 2014) vs. 24 (2019) vs. 64; „Teams erlaubt“ (E17/E19) vs. „Team #n“ (E23+) | sg24 mit festen 2er-Teams; sg16/sg64/qsg/„Teams erlaubt“ als Varianten |
| 2 | SG-Refill-Zeit | Regel 10:00 vs. Timer ~15:00 (2023–25) | 15:00 (E26), legacy 10:00; zweiter Refill nach weiteren 15:00 |
| 3 | SG/EG Deathmatch-Trigger, -Ablauf | nur Existenz belegt (SG), EG-Timer 30:00 belegt | SG: ≤4 Lebende oder 30:00, 60 s Countdown, 5:00; EG: Timer 30:00 nach Schutzzeit oder ≤3 Lebende → 60 s |
| 4 | DM-Zeitablauf-Sieger | Forum: Unentschieden vs. meister Schwertschaden | meister Schwertschaden (spätere Aussage); Option `draw` |
| 5 | Schutzzeit-Dauer SG/EG | nur Endphase der Countdowns gesehen | SG 30 s, EG 120 s |
| 6 | Mindestspieler | nirgends belegt | SG 8, EG 12, TTT 6, Cores 8, MB 4, CW = volle Partys |
| 7 | Truhen-/Enderkisten-Loot | nur Beispiel-Items belegt | Tabellen 6.1.3 / 6.2.5, als YAML austauschbar |
| 8 | EG-Kit-Layout im Menü, Preise der 4 Startkits, Preis „Lohe“ | Layout unbelegt; Fan-Seite ohne Preis | 6 Reihen, Tabellenreihenfolge; Startkits 0 Coins; Lohe 85.000 |
| 9 | EG-Kits Ritter, Voodoo, „2. Kit des Lohe-Updates“ | nur Videotitel | nicht umgesetzt; Kit-Registry erweiterbar |
| 10 | EG-Scoreboard-Farbe Kit / Labels mit Doppelpunkt | §5 (2024-Notiz) vs. §d (2026-Bild); Doppelpunkt uneinheitlich | §d, ohne Doppelpunkt |
| 11 | EG-Chatfarbe | türkis (E17) vs. §7 (E23/E26) | §7 Text, §e Zahlen; legacy §3 |
| 12 | Rucksack-Preis | 20k (Fan) vs. 50k (Changelog) | nur legacy, 50.000 |
| 13 | Kit-Zahlen ohne Original-Wert ([…] in 6.2.3) | Fähigkeit belegt, Zahl nicht | Werte in eckigen Klammern; in `kits.yml` konfigurierbar |
| 14 | TTT Rundenzeit | 10:00 (Changelog) vs. „Ende 11:33“ (2017) | 10:00; legacy 15:00 |
| 15 | TTT 12 vs. 24 Spieler | Changelog widersprüchlich | ttt12 Standard, ttt24 Variante (Creeper-Pfeile 3) |
| 16 | TTT Rollenverteilung | nicht belegt | 25 % Traitor (min 1), 12,5 % Detective ab 8 Spielern |
| 17 | TTT Karma-Start/-Wirkung, Traitor-Karma | nur Teiltabelle belegt | Start 1000, Boden 0, Deckel +100/Runde, < 500 keine Pässe, Traitor-Kill +10 |
| 18 | TTT Preise neuer Items + Wirkung „Ordeal“ | nur Namen | Preise laut 6.3.5; „Feuerprobe“: Ziel-Spieler wird für 10 s für alle sichtbar markiert (Glowing) |
| 19 | TTT Startpunkte/Punkte pro Kill | nicht belegt | Start 1, Traitor +1/+2, Detective +2 je Traitor-Kill |
| 20 | Innocent-Pass, Detective-Pass-Preis, `@d`-Chat | nur Traitor-Pass + `@t` belegt | Innocent-Pass existiert; D-/I-Pass 2 Vote-Keys; `@d` existiert |
| 21 | TTT Scoreboard Wartelobby | E17 „Map/Karma/Schutzzeit“ teils belegt | `Map` / `Karma` / `Start` |
| 22 | Cores Upgrade-Inhalte, Zeitlimit, Cores-Level | nicht belegt | Stufen Eisen/Gold/Diamant (Schwert+Rüstung des Materials, Gold mit Schärfe I), Limit 60 min, Level-Formel 6.4 |
| 23 | MB Spielerzahl, Plotgröße, Bewertungszeit, Punkte | nicht belegt | 12 Spieler, 32×32, 15 s/Plot, Noten 0–5 |
| 24 | MB Notenfarben | nur „Okay“ §6 gesehen | §4/§c/§6/§a/§b/§5 |
| 25 | ClanWar Elo-Formel, Sperrregel, Map-Wahl, Größen | nicht belegt | Elo nach Spec 01 §6.6 (K=50, Start 1000, je Modus); 3 Abbrüche/24 h → 24 h Sperre; Map zufällig; 2–4 je Seite |
| 26 | CW-fähige Spiele 2026 | CWCores/CWSG eingefroren | BedWars Standard; Cores/SG per Konfig zuschaltbar |
| 27 | Coins je Modus | nur Beispielwerte (+34, +57) | Werte in 6.x; zentral in Konfig, Kern-Boost |
| 28 | `/start`-Rang | nicht belegt | Premium aufwärts (Spec 03 #4), Countdown → 10 s |
| 29 | Achievement-Namen | Fan-Seite (markant) | Bedingungen 1:1, Namen eigen in i18n |
| 30 | Weltgröße EG, Vorgenerierung | nicht belegt | Border 600, Vorgenerierung Radius 300 in WAITING |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | Pakete `dev.netzwerk.*`, Nodes `netzwerk.*` | `net.retromc.*`, `retromc.*` |
| K2 | Migration `V<n>__klassiker` ohne Nummer | `V5_1__klassiker.sql` |
| K3 | `stats.total`/`stats.monthly` und Monats-Bestenlisten (Spec 02 löscht diese Tabellen) | Spalten/`extra` in `stats.daily`/`stats.alltime`, Keys `lb:{mode}:30d|all` |
| K4 | `games.cw_match` doppelt zu `social.clan_war` (Spec 01), Elo in `social.clan.elo` (deprecated), K=32 vs. K=50 (Spec 01) | `social.clan_war` (+ ALTER), `social.clan_elo` je Modus, K=50 |
| K5 | `game:<server>:state` (TTL 30 s, Feld `players`) | entfällt; `server:{name}` (Spec 08 W3/W4) |
| K6 | Präfix-Klammern `§8[…§8]` (Spec 02: `§f`, Spec 06: `§7`) | Rundenmodi einheitlich `§f[…§f]` |
| K7 | `common.*` doppelt zu Spec 03 (andere Farben, `common.restart` §7, Stopp nach 15 s) | Spec 03 §7.2 verbindlich (`§c`, 20 s) |
| K8 | „Premium+“ für `/start` mehrdeutig (Rang Premium Plus vs. „ab Premium“) | „Premium aufwärts“ (`premium`+, Gewicht ≥ 200) |
| K9 | Schnittstellen-Namen `SidebarService`, `StatsSink`, `CoinService.reward`, `TeamService`, `GameWinEvent`, `Messages.send` | Spec 02/03-Namen (`BoardService`, `StatsService.submit`, `CoinService.payoutRound`, `TeamAllocator`, `GameEndEvent`, `I18n`) |
| K10 | Scoreboard-Titel ohne `.NET`, Tablist mit Original-Wortlaut | `§f§l{BRAND_UPPER}.NET`, Spec-02-Keys |
| K11 | `map.yml` | `map.json` (Spec 08 §6.11) |
