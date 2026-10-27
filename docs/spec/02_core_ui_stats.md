# Spec 02: Kern-UI, Daten & Stats (Paper-Core + core-*)

Stand 2026-10-21 · Quellen: Notizen 05, 15, 16, 07 §4/§5/§6 (Abkürzung N05 …), Video-Sweep (VS), OCR-Review (OCR).
Beleg-Stufen: **belegt** (Code, Text oder Frame klar lesbar) · **abgeleitet** (aus mehreren Belegen geschlossen) · **Annahme** (festgelegt, ohne Beleg).
Ära-Schalter `ui.era`: `MODERN` (Standard, 2024–2026) | `LEGACY` (1.8-Ära bis ca. 2020, optional).
Schreibweise: Formate stehen in §-Notation. Umsetzung in `core-i18n` mit MiniMessage. `{x}` wird zu `<x>`. §-Farben werden zu Tags, zum Beispiel `§e`→`<yellow>`, `§6`→`<gold>`, `§7`→`<gray>`, `§8`→`<dark_gray>`, `§l`→`<bold>`, `§k`→`<obf>`. `<brand>` bleibt.

---

## 1. Umfang & Module

| Gradle-Modul | Status | Paket | Inhalt in dieser Spec |
|---|---|---|---|
| `:core:core-api` | vorhanden | `net.retromc.core.api` | DTOs/Events: `ModeKey`, `StatPeriod`, `StatLine`, `MatchResult`, `PlayerMatchStats`, `CoinPayout`, `RankDisplay`, Events aus §8 |
| `:core:core-data` | vorhanden | `net.retromc.core.data` | Repositories `StatsRepository`, `WalletRepository`, `DisplayAnchorRepository`, `ProfileRepository`; `ValkeyKeys` erweitern (§2.2); Migration `V2_1__core_ui_stats.sql` |
| `:core:core-messaging` | vorhanden | `net.retromc.core.messaging` | Kanäle `net:profile` und `net:stats` (**NEU** in `NetworkChannel`), Nachrichtentypen §8.3 |
| `:core:core-i18n` | vorhanden | `net.retromc.core.i18n` | Nachrichtenkatalog §7, `Formats` (Zahlen, Datum, Dauer), Locale-Auflösung |
| `:core:core-stats` | **NEU** (in `settings.gradle.kts` ergänzen) | `net.retromc.core.stats` | Reines Java: Punkteformeln, K/D, Siegesquote, Fenster-Logik, Ranking-Score, Tie-Break, Ausgabe-Builder für `/stats` (ohne Paper). Nutzbar auch für `:services:api` |
| `:platform:paper-core` | vorhanden | `net.retromc.paper.core` (Unterpakete `chat`, `tab`, `board`, `bar`, `title`, `gui`, `item`, `hotbar`, `coins`, `stats`, `holo`, `lang`) | Plugin **`${brandName}Core`** (`paper-plugin.yml`, `main: net.retromc.paper.core.PaperCorePlugin`) |

Abhängigkeiten aus `libs.versions.toml`, alle vorhanden:

| Zweck | Bibliothek |
|---|---|
| Scoreboard | FastBoard 2.2.2 |
| GUI | InventoryFramework 0.12.2 (nur als Unterbau; eigenes Konventions-API §5) |
| Befehle | Incendo Cloud (cloud-paper 2.0.1) |
| Datenbank | HikariCP, PostgreSQL-Treiber, Flyway |
| Valkey | Lettuce |
| Cache | Caffeine |
| Ränge | LuckPerms-API 5.5 (Meta: `rank`, `prefix`, `color`, `weight`) |
| Text | Adventure/MiniMessage (in Paper enthalten) |

**NEU in `core-stats`:** keine externen Abhängigkeiten. Für Tests: JUnit 6.
Spielmodule hängen per `paper-plugin.yml` an `${brandName}Core` (`join-classpath: true`) und nutzen nur die Services aus §8.

---

## 2. Datenmodell

### 2.1 PostgreSQL – Migration `db/migrations/V2_1__core_ui_stats.sql` (NEU)

Spieler-ID ist überall `UUID`, wie in V1. Die V1-Tabellen `stats.total` und `stats.monthly` sind beim Release leer und werden **gelöscht**. Sie werden durch die Tagesbuckets ersetzt (N16 §10.2). Begründung: `/statsd N` und das rollierende 30-Tage-Fenster brauchen Tageswerte.

| Tabelle | Spalten (Typ) | Schlüssel / Indizes | Zweck |
|---|---|---|---|
| `stats.mode` | `id SMALLINT`, `key VARCHAR(24) NOT NULL`, `i18n_key VARCHAR(48) NOT NULL`, `color VARCHAR(16) NOT NULL` (MiniMessage-Farbe), `gradient VARCHAR(32) NULL` (`#RRGGBB:#RRGGBB`), `ranked BOOLEAN NOT NULL DEFAULT true`, `metric VARCHAR(12) NOT NULL DEFAULT 'points'` (`points`/`karma`/`time`/`none`), `points_formula JSONB NOT NULL DEFAULT '{}'`, `fields JSONB NOT NULL` (geordnete Stat-Keys für `/stats`), `resettable BOOLEAN NOT NULL DEFAULT false`, `active BOOLEAN NOT NULL DEFAULT true` | PK `id`; UNIQUE `key` | Modus-Katalog (Seed §6.7) |
| `stats.daily` | `uuid UUID NOT NULL`, `mode_id SMALLINT NOT NULL`, `day DATE NOT NULL` (Europe/Berlin), `kills INT`, `deaths INT`, `games INT`, `wins INT` (alle `NOT NULL DEFAULT 0`), `points BIGINT NOT NULL DEFAULT 0`, `extra JSONB NOT NULL DEFAULT '{}'` | PK `(uuid, mode_id, day)`; `PARTITION BY RANGE (day)`, eine Partition pro Monat; IDX `(mode_id, day)` | Tagesbuckets, Aufbewahrung 35 Tage |
| `stats.alltime` | `uuid`, `mode_id`, `kills`, `deaths`, `games`, `wins BIGINT NOT NULL DEFAULT 0`, `points BIGINT NOT NULL DEFAULT 0`, `extra JSONB NOT NULL DEFAULT '{}'`, `first_game_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `(uuid, mode_id)`; IDX `(mode_id, points DESC, wins DESC, uuid)` | Gesamtwerte, inkrementell |
| `stats.map_alltime` | `uuid`, `mode_id`, `map VARCHAR(48)`, `kills INT`, `deaths INT`, `games INT`, `wins INT`, `points BIGINT` | PK `(uuid, mode_id, map)` | Stats pro Map (SkyWars-Map-Menü, N16 §2.2) |
| `stats.match` | wie V1, zusätzlich `mode_id SMALLINT`, `applied_at TIMESTAMPTZ` | PK `id` | Idempotenz für die Runde |
| `stats.match_player` | wie V1, zusätzlich `wins SMALLINT NOT NULL DEFAULT 0`, `points INT NOT NULL DEFAULT 0`, `xp INT NOT NULL DEFAULT 0` | PK `(match_id, uuid)` | Audit, Rundenblock |
| `stats.player_settings` | `uuid UUID`, `hidden BOOLEAN NOT NULL DEFAULT false`, `hidden_since TIMESTAMPTZ` | PK `uuid` | `/togglestats` |
| `stats.network_level` (**NEU, Integration**) | `uuid UUID`, `xp BIGINT NOT NULL DEFAULT 0`, `level INT NOT NULL DEFAULT 0`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `uuid`; IDX `(level DESC, xp DESC)` | Globales Level (Chat-Level Spec 01, Kill-XP Spec 03, Bingo/PartyGames-XP Spec 06), Dienst `NetworkLevelService` §8.1 |
| `stats.reset_log` | `id BIGSERIAL`, `uuid UUID NOT NULL`, `mode_id SMALLINT NOT NULL`, `scope VARCHAR(8) NOT NULL CHECK (scope IN ('player','staff','clan'))`, `ledger_id BIGINT`, `actor UUID`, `snapshot JSONB NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` | PK `id`; IDX `(uuid, created_at DESC)` | Snapshot vor dem Löschen |
| `economy.wallet` (ALTER) | zusätzlich `gold BIGINT NOT NULL DEFAULT 0 CHECK (gold >= 0)`, `reset_tokens INT NOT NULL DEFAULT 0 CHECK (reset_tokens >= 0)` | – | Gold (Scoreboard), ResetTokens |
| `economy.ledger` (ALTER) | zusätzlich `currency VARCHAR(12) NOT NULL DEFAULT 'COINS' CHECK (currency IN ('COINS','GOLD','RESET_TOKEN'))` | IDX `(uuid, created_at DESC)` | Buchungsjournal, nur Anhängen; `idem_key` UNIQUE (V1) |
| `core.display_anchor` | `id BIGSERIAL`, `server_group VARCHAR(32) NOT NULL`, `world VARCHAR(64) NOT NULL`, `type VARCHAR(16) NOT NULL CHECK (type IN ('SIGN_WALL','HOLO_TOP','HOLO_PERSONAL'))`, `mode_id SMALLINT NOT NULL`, `period VARCHAR(4) NOT NULL CHECK (period IN ('30d','all'))`, `size SMALLINT NOT NULL DEFAULT 10`, `x/y/z DOUBLE PRECISION`, `yaw REAL`, `facing VARCHAR(8)`, `created_by UUID`, `created_at TIMESTAMPTZ DEFAULT now()` | PK `id`; IDX `(server_group)` | Positionen von Bestenlisten-Wänden und Hologrammen |
| `core.player` | unverändert (`locale VARCHAR(8) DEFAULT 'de_DE'`, `playtime_s`, `settings JSONB`) | – | `settings` erhält die Schlüssel `ui.scores`, `ui.era` (nur Team) |

Hilfsfunktion: `stats.jsonb_add(a JSONB, b JSONB) RETURNS JSONB`. Sie addiert numerische Schlüssel und ist IMMUTABLE.
Partitionswartung: Ein Job legt am 25. jedes Monats die Partition für Monat +1 an und löscht Partitionen, deren Ende vor `heute − 35 Tage` liegt.

### 2.2 Valkey – Schlüssel und Kanäle

| Schlüssel / Kanal | Typ | TTL | Inhalt / Regel |
|---|---|---|---|
| `lb:{mode}:30d` | ZSET | keine | Member = UUID, Score = Ranking-Score (§6.6). **Ersetzt** `ValkeyKeys.leaderboardMonthly` (wird entfernt) |
| `lb:{mode}:all` | ZSET | keine | wie oben, Gesamtwerte. **Ersetzt** `leaderboardAllTime(mode,key)` durch `leaderboard(mode, period)` |
| `lb:{mode}:{period}:tmp` | ZSET | 600 s | Neuaufbau, danach `RENAME` |
| `st:{mode}:{uuid}:{30d\|all}` | HASH | 60 s | Cache der `/stats`-Werte; wird bei Rundenende des Spielers gelöscht |
| `top:{mode}:{30d\|all}` | STRING (JSON, Top 10 inkl. Name, Rangfarbe, Werte) | 30 s | für Schilder, Hologramme und `/top` |
| `rl:stats:{uuid}` | STRING (INCR) | 1000 ms (PEXPIRE) | Ratenlimit, 1 Abfrage pro Sekunde |
| `lock:stats:rollover:{yyyy-mm-dd}` | STRING `SET NX PX 600000` | 10 min | ein Rollover pro Tag im ganzen Netzwerk |
| `lock:stats:reconcile:{yyyy-mm-dd}` | STRING `SET NX PX 3600000` | 60 min | nächtlicher Neuaufbau |
| `reset:confirm:{uuid}` | STRING (`mode`) | 30 s | offener Reset-Dialog |
| `profile:{uuid}` | HASH | → **Spec 01 §2.3** (einzige Definition; diese Spec schreibt die Felder `coins`, `gold`, `playtimeS`, `statsHidden`) | Profil-Cache für Chat, Tab und Scoreboard |
| `ui:news` | LIST (JSON `{color,title,url}`) | keine | Lobby-Newsticker (Actionbar), wird vom Lobby-Modul gepflegt |
| Kanal `net:profile` (**NEU**) | Pub/Sub | – | `ProfileChanged{uuid, fields[]}`: Coins, Gold, Rang, Locale, Clan |
| Kanal `net:stats` (**NEU**) | Pub/Sub | – | `StatsUpdated{mode, uuids[]}`, `LeaderboardChanged{mode, period}` |
| Kanal `net:social` (vorhanden) | Pub/Sub | – | wird gelesen (Typen aus Spec 01 §2.4): `CLAN_UPDATE`, `CLAN_ONLINE`/`CLAN_OFFLINE`, `FRIEND_ONLINE`/`FRIEND_OFFLINE`/`FRIEND_ADDED`/`FRIEND_REMOVED`, `PROFILE_INVALIDATE` |

---

## 3. Befehle

Recht = Permission-Knoten (LuckPerms). Ohne Angabe darf jeder Spieler den Befehl nutzen. Fehlertexte siehe §7, hier als Schlüssel.

| Syntax | Aliase | Recht/Rang | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|
| `/stats [Spieler\|#Platz]` | – | – | Modus = Modus des Servers (Lobby, Wartelobby oder Runde). Zeigt 30-Tage-Stats. Ohne Argument: eigene Stats. `#N` = Spieler auf Platz N (30 Tage). Auf Servern ohne Modus gilt `/stats <Modus> [Spieler\|#Platz]` (Annahme). | Block `stats.header`/`stats.line`/`stats.footer` (§4.7). Fehler: `stats.notfound`, `stats.hidden`, `stats.rank-empty`, `stats.need-mode`, `stats.cooldown`, `error.data` |
| `/statsall [Spieler\|#Platz]` | `/statsalltime` (Annahme) | – | wie `/stats`, Zeitraum gesamt | Kopf `({period}=insgesamt)`, sonst wie oben |
| `/statsd <Tage> [Spieler]` | – | – | Fenster der letzten N Tage, N 1–30 (belegt sind 1/3/5/10/15/20, Grenze 30 ist eine Annahme). Zeile „Position im Ranking“ zeigt `-` (Annahme) | Fehler `stats.days-invalid` |
| `/togglestats` | – | mind. 100 h Spielzeit (`playtime_s ≥ 360000`) | schaltet `stats.player_settings.hidden` um | `togglestats.hidden` / `togglestats.visible` / `togglestats.requirement` |
| `/top [statsall]` | `/top 30d` | – | Top 10 des Server-Modus; ohne Argument 30 Tage, `statsall` = gesamt. Funktioniert in der Wartelobby während der ganzen Runde | `top.header`, `top.line` ×10, `top.self`, `stats.footer`; Fehler `stats.need-mode` |
| `/resetstats` | – | Besitz ≥ 1 ResetToken; nur auf einer Modus-Lobby (Kategorie `LOBBY` mit `mode` = Modus, Spec 08 §6.5) | öffnet einen Chat-Dialog (§6.9) | `reset.warn`, `reset.ask` mit Klick-Buttons; Fehler `reset.no-token`, `reset.wrong-place`, `reset.not-resettable` |
| `/resetstats confirm` / `/ja` | Chat-Eingabe „Ja“ (case-insensitive) | wie oben, nur bei offenem Dialog | führt den Reset aus | `reset.done`; `reset.expired` |
| `/resetstats cancel` | Chat-Eingabe „Nein“ | – | bricht ab | `reset.cancel` |
| `/tokens` | – | – | zeigt die Anzahl ResetTokens | `tokens.show` |
| `/coins` | – | – | zeigt den Coin-Stand (Annahme; 1.8 nur in SW/EG belegt) | `coins.balance` |
| `/coins <add\|remove\|set> <Spieler> <Betrag>` | – | `retromc.coins.admin` | Ledger-Buchung, Grund `ADMIN` | `coins.admin-done`; `coins.insufficient`, `stats.notfound` |
| `/language` | – | – | **Spec 01 #67** (Velocity, alle Sprachen aus Spec 01 §6.14, Keys `lang.*`). Diese Spec rendert nach `LocaleChangedEvent` neu | – |
| `/statsadmin reset <Spieler> <Modus>` | – | `retromc.stats.admin` | Team-Reset ohne Token (`scope='staff'`) | `statsadmin.reset-done` |
| `/statsadmin rebuild <Modus\|all>` | – | `retromc.stats.admin` | baut die ZSETs sofort neu auf (§6.5 Schritt 3) | `statsadmin.rebuild-done` |
| `/statswall <sign\|holo\|personal> <Modus> <30d\|all> [Größe]` | – | `retromc.stats.wall` | legt einen `core.display_anchor` an der Blickposition an (Schilderwand: 10 Schilder nach rechts) | `statswall.created`; `stats.unknown-mode` |
| `/statswall remove` | – | `retromc.stats.wall` | entfernt den Anker, auf den der Spieler blickt (≤ 5 Blöcke) | `statswall.removed` |

Kein Befehl braucht einen Rang für fremde Stats (abgeleitet aus Addon-Verhalten, N16 §4). Genickte Spieler sind nur über den Echtnamen abfragbar. Ein Nickname liefert `stats.notfound`.

---

## 4. Anzeige-Elemente

### 4.1 Scoreboard (Sidebar) – allgemeine Regeln
| Regel | Wert | Beleg |
|---|---|---|
| Titel | `§f§l{BRAND_UPPER}.NET` (→ `RETROMC.NET`; Original zeigt die Domain in Großbuchstaben) | belegt (Struktur), 1.8 und 2026 |
| Zeilenfarbe | Label `§f`, Wert in der Zeile darunter, farbig; Blöcke durch Leerzeilen getrennt | belegt (VS Lobby 2024–26) |
| Score-Zahlen | `MODERN` Lobby: sichtbar, `§c`, absteigend n−1…0. Duels-Match: ausgeblendet. Legacy-Clients: immer sichtbar. Pro Board `showScores` (true/false) | belegt (VS) |
| Max. Zeilen | 15 | Vanilla |
| Flackerfrei | FastBoard: nur geänderte Zeilen senden; Leerzeilen erhalten eindeutige unsichtbare Suffixe | – |
| Ladezustand | Wert `§70` bis die Daten geladen sind (Original zeigt 0). Option `loadingText=§7…` | belegt N05 §6 |

### 4.2 Lobby-Scoreboard (`MODERN`, 15 Zeilen)
| Score | Text | Farbe / dynamischer Wert | Beleg |
|---|---|---|---|
| 14 | (leer) | – | Annahme (15 Scores 14…0 sichtbar, 14 Textzeilen) |
| 13 | `Rang` ⇄ `Spielzeit` | `§f`; Wechsel alle **5 s** (Annahme; belegt: „wechselt alle paar Sekunden“) | belegt |
| 12 | Rang: `{rankColor}{rankName}` (z. B. `§4Administrator`, `§ePremium Plus`) ⇄ `§6{hours} Stunden` | Stunden = `floor(playtime_s/3600)`, de-Tausenderpunkt; bei 1 `Stunde` | belegt |
| 11 | (leer) | – | belegt |
| 10 | `Coins` | `§f` | belegt |
| 9 | `§e{coins}` | z. B. `1.355.470` | belegt |
| 8 | (leer) | – | belegt |
| 7 | `Gold` | `§f` | belegt |
| 6 | `§6{gold}` | z. B. `4.950` | belegt |
| 5 | (leer) | – | belegt |
| 4 | `Clan` | `§f` | belegt |
| 3 | `§b{online}§7/§7{members} §7[§b{tag}§7]`; ohne Clan `§7-` | online = Clan-Mitglieder online | belegt (Format); Bedeutung abgeleitet |
| 2 | (leer) | – | belegt |
| 1 | `Freunde` | `§f` | belegt |
| 0 | `§a{online}§7/{total}` | Freunde online / gesamt | belegt |

`LEGACY` (2017): Zeilen `Coins`, `Dein Clan`, `Freunde` mit den Werten darunter, ohne Rang, Spielzeit und Gold (belegt OCR, Werte-Farben Annahme wie MODERN).
Spieler ohne Rang: Rang-Wert `§aSpieler` (Annahme).

### 4.3 Scoreboard-API für Spielmodule
Belegte Muster für die Spiel-Specs: Label `§f` und Wert `§e`, oder Label-Wert in einer Zeile (`Map §eFarmland`). Timer im Format `mm:ss`. Teamzeilen in Teamfarbe. Die API ist in §8.1 `BoardService` beschrieben.

### 4.4 Tablist
| Teil | Inhalt | Farben | Beleg |
|---|---|---|---|
| Header Zeile 0 (optional) | Bild-Banner per Resourcepack-Glyphe (`<font:retromc:banner>`). Fehlt das Pack, entfällt die Zeile | – | belegt 2025 (Original-Banner), eigenes Motiv |
| Header Zeile 1 | `§e{brand}.net {modeColor}{serverDisplay}` (z. B. BedWars `§c`, SkyWars `§b`, TTT `§c`, Duels `§9`, SMP `§d`, Silent Lobby `§c`, CityBuild `§6`, Lobby `§a`; Farben = `modeColor`-Registry Spec 01 §4.2; `serverDisplay` aus Spec 08 §4). **paper-core ist einziger Renderer von Header/Footer** (kein Proxy-Header, kein `TABLIST_OVERRIDE`) | Modusfarbe aus `stats.mode.color` bzw. Server-Konfiguration | belegt |
| Header Zeile 2 | `§7Regelverstoß gesehen? §c/report §7<Spieler>` | Text `§7`, Befehl `§c`, Platzhalter `§7` | Struktur belegt, Text eigen |
| Footer (Standard) | `§7Lade Freunde ein, starte Partys oder gründe` / `§7deinen eigenen Clan: §a/friend§7, §d/party §7und §e/clan` | `/friend §a`, `/party §d`, `/clan §e` | Struktur und Farben belegt, Text eigen |
| Footer (Override pro Server) | `§7Du bist auf Server §e{server}` + optionale Zusatzzeilen vom Modul | – | belegt (CityBuild, SMP) |
| Eintrag (Lobby) | `[{rankColor}{prefix} §7| ]{rankColor}{name}[ §7[§e{tag}§7]]` | Präfix und Name in Rangfarbe, Trenner `§7|` | belegt 2026 |
| Eintrag (Spiel) | `{teamColor}{teamName} §7| §f{name}[ §7[§e{tag}§7]]` | – | belegt (BedWars 2024/25) |
| Eintrag `LEGACY` | `[{rankColor}{prefix} §8┃ ]{rankColor}{name}[ §7[§e{tag}§7]]` | Trenner `§8┃` | teilweise lesbar |
| Spalten | Vanilla, bis 80 Einträge (4×20) | – | belegt |
| Ping | Vanilla-Latenzbalken (echte Latenz) | – | belegt |
| Sortierung | §6.2 | – | belegt |

### 4.5 Bossbar
| Verwendung | Text | Farbe/Stil | Fortschritt | Beleg |
|---|---|---|---|---|
| Saison (Duels-Lobby) | `§cSeason: §e{Mon}-{Mon} {yyyy}` (z. B. `Jul-Sep 2026`, englische Monatskürzel) | BLUE, PROGRESS | 1.0 | belegt |
| Tutorial | `§6Tutorial §7({step}/{total}): §f{text} §7/tutorial` | GREEN, PROGRESS | step/total | belegt (Thumbnail) |
| Newsticker `LEGACY` | `§e§l{TITLE}` (z. B. „2 NEW CORES MAPS“) | PINK (Annahme) | 1.0 | belegt (OCR, 2017) |
| Kampf, Boss usw. | Modul-spezifisch | über API | – | – |
API: maximal 1 Bossbar pro Kanal (`season`, `news`, `game`, `event`), Reihenfolge der Anzeige: game > event > season > news (Annahme).

### 4.6 Actionbar
| Verwendung | Format | Dauer | Beleg |
|---|---|---|---|
| Lobby-Newsticker | `§e+ {color}§l{TITLE}: §f{url}` | rotiert alle 8 s (Annahme) durch `ui:news` | belegt (Format) |
| Teamanzeige (Modul) | `{teamColor}Team {teamName}` | dauerhaft (Kanal `persistent`) | belegt |
| Zuschauer (Modul) | `§7Zuschauer` | dauerhaft | belegt |
| Kit-Cooldown (Modul) | `§7Kit-Fähigkeit: §cNutzbar in §e{n} Sekunden` / `§7Kit-Fähigkeit: §aAb jetzt nutzbar §7[{uses} Nutzungen]` | jede Sekunde neu | belegt |
| Event-Countdown | `§7Das Event endet in §e{hh:mm:ss}` | jede Sekunde | belegt |
Priorität: `temporary` (Dauer in Ticks) > `cooldown` > `persistent` > `ticker`. Neu senden alle 40 Ticks, weil Vanilla die Actionbar nach etwa 3 s ausblendet.

### 4.7 Chat-Blöcke (Stats)
`/stats` (MODERN, belegt pixelgenau N16 §1.1):
```
§7-= §eStatistiken von §6{name} §7({period}) =-
§7 Position im Ranking: §e{rank}
§7 Punkte: §e{points}
… (Felder nach stats.mode.fields)
§7----------------------            (22 × "-")
```
Name immer `§6`, ohne Rangfarbe. Fehlt ein Wert oder ein Platz: `-`.
Rundenblock: `§7-= §eStatistiken dieser Runde §7=-`, Zeilen `§7 {label}: §e{value}`, optional eine Leerzeile, dann `§7 Überlebt: §a✓|§c✗` und `§7 Gewonnen: …`, Abschluss `§8----------------------` (22 ×).

### 4.8 Titles
| Verwendung | Title | Subtitle | Zeiten (Ticks in/stay/out) | Beleg |
|---|---|---|---|---|
| Sieger | `{rankOrTeamColor}{name}` | `§fhat die Runde gewonnen` (Duels: `hat das Match gewonnen`) | 10/70/20 (Annahme) | belegt |
| Modus-Intro | `{modeColor}{modeName}` | `§f{map}` | 10/50/10 (Annahme) | belegt |
| Start | `§aLOS!` | – | 0/20/10 | belegt |
| Respawn | `§cRespawn in §e{n} §cSekunden` (1 → `Sekunde`) | – | 0/25/0, jede Sekunde neu | belegt |
| Rolle (TTT) | `{roleColor}{role}` | `§7{hint}` | 10/70/20 | belegt |
| CityBuild-Join | `§eWillkommen {rankColor}{name}§e!` | `§f{text}` | 10/70/20 | belegt (Text eigen im Modul) |

### 4.9 Hologramme & Wände
| Typ | Aufbau (Zeile für Zeile) | Sichtbarkeit | Beleg |
|---|---|---|---|
| `HOLO_PERSONAL` | 1 `{modeColor}{mode} §6Statistiken` · 2 `§7Deine §6All-Time Statistiken` **oder** `§7Deine §eStatistiken §7der letzten §e30 Tage` · 3 ff. `§7{label} §e{value}` (ohne Doppelpunkt; Siegesquote im Hologramm-Format `27,59%`) · letzte Zeile `§8▬▬▬▬▬▬▬▬▬▬` (Annahme) | nur für den Betrachter (eine `TextDisplay` pro Viewer, `visibleByDefault=false`) | belegt (BedWars, PartyGames, SkyWars 2021) |
| `HOLO_TOP` | 1 `{modeColor}{mode} §6Bestenliste` · 2 `§7{periodText}` · 3–(N+2) `§e#{pos} {rankColor}{name} §7- §e{points}` | alle | belegt (GunGame Top 5 30 Tage und gesamt), Format Annahme |
| `SIGN_WALL` | Spielerkopf (Skull-Block) über jedem Schild. Schild: Z1 `§l#{pos}`, Z2 `{name}`, Z3 `§e{points} Punkte`, Z4 `§7{wins} Siege` | alle; Rechtsklick auf Schild → `/stats {name}` (30d) bzw. `/statsall` | belegt (Layout 2018, Klick News #17); Z3/Z4-Inhalt abgeleitet |
| Versteckte Spieler | erscheinen auf Wänden und Hologrammen mit Namen | – | belegt (Forum) |
| Gleichstand | Spieler mit gleichem Ranking-Score wechseln sich auf dem Platz alle 5 s ab | – | belegt (Bingo), Takt Annahme |

### 4.10 NPCs und Hotbar
- NPCs: Dieser Bereich enthält keine NPCs (Profil-NPC und Join-NPCs stehen in der Lobby-Spec).
- Hotbar (Framework): `HotbarService` setzt gesperrte Items (nicht verschiebbar, kein Drop) mit Klick-Handlern. Namenskonvention `{color}{Name} §7(Rechtsklick)`, z. B. `§aNavigator §7(Rechtsklick)` (belegt: „Navigator (Rechtsklick)“, „Teleporter (Rechtsklick)“). Slotbelegung definiert jedes Modul.

---

## 5. GUIs

### 5.1 Konventionen (GUI-Framework `net.retromc.paper.core.gui`)
| Punkt | Regel | Beleg |
|---|---|---|
| Titel | Vanilla-Titelfarbe (kein §-Code), kurzer deutscher Satz, z. B. „Wähle eine Kategorie“, „Wähle einen Spieler“, „Einstellungen für: {rankColor}{name}“ | belegt |
| Reihen | 1–6; Standard 3 (Dialog), 5 (Übersicht), 6 (Listen/Shops) | belegt |
| Rahmen | Glasscheiben, Name `" "` (leer), Tooltip ausgeblendet (`hide_tooltip`). Farbe pro Kontext: Standard `LIGHT_BLUE`, Modus über `stats.mode` → Glasfarbe (BedWars-Shop violett/blau, Party violett) | belegt |
| Rahmenformen | `FULL_BORDER` (äußerer Ring), `TOP_BOTTOM` (Reihe 1 und letzte Reihe), `SEPARATOR_ROW(r)` (z. B. Reihe 2 unter der Kategorieleiste) | belegt |
| Item-Name | Farbe nach Funktion: `§a` Aktion/positiv, `§c` abbrechen/negativ, `§e` Kategorie, `§b` Info; nicht kursiv | belegt |
| Lore | `§7Label: §fWert` (Doppelpunkt direkt am Label), Leerzeile vor Fließtext, Fließtext `§7`; Aktionshinweis zuletzt in spitzen Klammern: `§c<Doppelt-Rechtsklick zum Entfernen>`, `§7<Klicke zum Betreten>` | belegt |
| Zeilenumbruch Lore | max. 38 sichtbare Zeichen pro Zeile, Umbruch an Wortgrenzen | Annahme |
| Klicks | nur Top-Inventar, Shift-Klick und Ziehen blockiert; Cooldown pro Spieler 150 ms | Annahme |
| Sounds | Klick `UI_BUTTON_CLICK` 0.5/1.0; Fehler `ENTITY_VILLAGER_NO`; Erfolg `ENTITY_EXPERIENCE_ORB_PICKUP` | Annahme |
| Blättern | 6 Reihen: Inhalt Slots 10–16, 19–25, 28–34 (21 pro Seite). Slot 48 `§eVorherige Seite` (Pfeil), Slot 49 `§7Seite §e{p}§7/§e{n}` (Papier), Slot 50 `§eNächste Seite`. Buttons nur bei Bedarf, sonst Rahmen | Annahme (Seitenanzeige-Item oben belegt im Kopfgeld-Menü) |
| Zurück/Schließen | Slot 45 `§cZurück` (Pfeil) bei Untermenü, Slot 53 `§cSchließen` (Barriere) optional | Annahme |

### 5.2 Bestätigungsdialog (`ConfirmGui`)
Titel `{frage}` (max. 32 Zeichen), 3 Reihen, Rahmen `FULL_BORDER` hellgrau.
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 11 | Grüner Farbstoff (`LIME_DYE`) | `§aBestätigen` / `§7Klicke hier, um deine Auswahl zu bestätigen.` (Text eigen; Struktur belegt) | `onConfirm`, schließen |
| 13 | Info-Item vom Aufrufer | Name/Lore vom Aufrufer | – |
| 15 | Roter Farbstoff (`RED_DYE`) | `§cAbbrechen` / `§7Klicke hier, um abzubrechen.` | `onCancel`, schließen |
| Schließen per ESC | – | – | entspricht `onCancel` |

### 5.3 Listen-GUI (`PagedGui<T>`)
Titel vom Aufrufer, 6 Reihen, Rahmen `FULL_BORDER`, Blättern nach §5.1. Ein `ItemRenderer<T>` liefert das Item, ein `ClickHandler<T>` die Aktion. Leere Liste: Slot 22 `§cKeine Einträge` (Barriere).

### 5.4 Statistik-Profil (`StatsProfileGui`) – Layout Annahme (N16 §11 Nr. 10: Original unbelegt)
Wird aus Profil-Menü der Lobby (Lobby-Spec) und per API geöffnet. Titel `Statistiken von {rankColor}{name}`, 5 Reihen, Rahmen `TOP_BOTTOM` hellblau.
| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 4 | Spielerkopf | `{rankColor}{name}` / `§7Spielzeit: §f{hours} Stunden`, `§7Coins: §f{coins}` | – |
| 10–16, 19–25, 28–34 | Modus-Icon (`stats.mode`-Seed `icon`) je aktivem Modus, nach `id` | `{modeColor}{mode}` / `§7Zeitraum: §f{periodText}`, Leerzeile, je Feld `§7{label}: §e{value}` (Format wie `/stats`), Leerzeile, `§7<Linksklick: Zeitraum wechseln>`, `§7<Rechtsklick: im Chat anzeigen>` | Links: 30 Tage ⇄ insgesamt (pro Item); Rechts: GUI schließen + `/stats` bzw. `/statsall` für diesen Modus ausführen |
| 40 | Uhr | `§eZeitraum: §f{periodText}` / `§7<Klicke zum Wechseln>` | schaltet alle Items um |
| 36 | Pfeil | `§cZurück` | Callback des Aufrufers |
| 44 | Hebel | Sichtbarkeit: `§7Deine Statistiken sind §asichtbar`/`§cverborgen` / `§7<Klicke zum Umschalten>` | `/togglestats` (nur eigenes Profil) |
Versteckte Fremd-Stats: Modus-Items zeigen nur `§cNicht öffentlich`.

### 5.5 Item-Builder (`ItemBuilder`)
Fluent API: `of(Material)`, `name(Component)`, `lore(List)`, `loreKey(i18nKey, args)`, `amount`, `glow()` (Enchant-Glint-Override), `skull(UUID|textures)`, `color(DyeColor)`, `hideAll()` (Attribute, Verzauberungen, Trims, Tooltip-Zusätze), `flag(String, value)` (PDC `retromc:action`), `build()`. Kursiv in Namen und Lore standardmäßig aus.

---

## 6. Mechaniken & Zahlen

### 6.1 Chat-Format-Engine (Ablauf)
1. Der `AsyncChatEvent` wird abgefangen. Das Modul fragt `ChatContextProvider` (vom Spielmodul) nach dem Kanal: `GLOBAL`, `TEAM`, `ALL` (`@a`/`@all`-Präfix im Text), `SPECTATOR`, `PARTY`, `CLAN`.
2. Bausteine in fester Reihenfolge: `[Kanalpräfix] [Level-Icon] [Kontextpräfix] [Clan-Tag] Name §7: §fNachricht`.
   - Kanalpräfix: `§7[@all] ` / `§7[OFF] ` (Zuschauer). Belegt.
   - Level-Icon: Resourcepack-Glyphe vor dem Namen (Global Level). Belegt als Existenz, Glyph Annahme.
   - Kontextpräfix: vom Modul, z. B. Modus-Level `§7[{lvlColor}{level}§7] ` (SkyWars belegt).
   - Clan-Tag: `§7[§e{tag}§7] ` vor dem Namen. In Lobby-Servern aus (belegt N05). Ohne Clan in Spielservern: `§7[-] ` (belegt 2025 BedWars-Wartelobby; Option `chat.noClanDash`, Standard an).
   - Name: Rangfarbe, im Spiel Teamfarbe (belegt); Supremium+-Äquivalent als Hex-Verlauf (`MODERN`), Legacy-Fallback auf Hauptfarbe.
   - Rang-Präfix: **nicht** im Chat (belegt 2017/2024), nur in Tab und Nametag.
3. Trenner `§7: `, Nachricht `§f` (belegt).
4. Farbiges GG/GL (ab `supremium`: Supremium, Supremium+, VIP, Team – Spec 01 §6.2): Ganze Nachricht ∈ {`gg`, `gege`, `ggwp`, `wp`, `nd`, `gl`} (Groß- und Kleinschreibung egal), bei `gl` nur in der Wartezeit. Dann wird sie zu `§7§k--§r {farbe}{TEXT} §7§k--`. Farbe = Spieler-Einstellung, Standard `§6`.
5. Empfängerfilter: „Spieler verstecken“ mit Chat-Sync (Lobby-Spec) über `ChatAudienceFilter`.
6. Systempräfixe: Modus `§f[{modeColor/gradient}{Modus}§f] ` (MODERN mit Hex-Verlauf aus `stats.mode.gradient`, sonst einfarbig). Systeme `§7[{farbe}{Name}§7] `. Systemtext `§7`, Zahlen `§e`, positiv `§a`, Fehler `§c`.
7. Join/Quit in Runden-Lobbys: `§7» {rankColor}{name} §7hat die Runde betreten` / `§7« … verlassen` (belegt; funktionale Standardphrase).

### 6.2 Tablist-Sortierung
`Player#setPlayerListOrder(order)` (Paper ≥ 1.21.2, höherer Wert steht oben). Dabei gilt `order = rankWeight` (Gewicht aus Spec 01 §6.1, 0–1000); bei gleichem Wert alphabetisch (Client). Legacy-Clients: Scoreboard-Team `"%04d%s"` mit `(9999−weight)` und dem Namen. Im Spiel sortiert das Modul nach Team (Override über `TabEntryProvider`).

### 6.3 Scoreboard-Takt
- Ereignisgetrieben: `ProfileChanged` (Coins, Gold, Rang, Clan, Freunde) ändert nur die betroffene Zeile.
- Timer-Zeilen: Takt 20 Ticks, synchron zu Countdown-Ticks.
- Wechsel Rang ⇄ Spielzeit: alle 100 Ticks.
- Spielzeit-Zähler: `playtime_s` wird beim Verlassen und alle 5 min gespeichert. Die Anzeige rechnet live: Session-Start + Datenbankwert.

### 6.4 Stats-Aufzeichnung (Rundenende)
1. Das Spielmodul ruft `StatsService.submit(MatchResult)` auf, mit `matchId`, `mode`, `map`, `players[]{uuid, kills, deaths, win(0/1), extra{beds,…}, xp, survived}`. Kills und Deaths in BedWars sind nur Final-Kills und Final-Deaths (das entscheidet das Modul).
2. `core-stats` berechnet `points` je Spieler nach `stats.mode.points_formula` (§6.7). Liefert das Modul `points` selbst (metric ≠ points-Formel), gilt dieser Wert.
3. Eine Transaktion: `INSERT stats.match … ON CONFLICT DO NOTHING`. Wenn 0 Zeilen eingefügt wurden: Abbruch (idempotent). Danach `INSERT stats.match_player`, UPSERT `stats.daily` (Tag = `now() AT TIME ZONE 'Europe/Berlin'`) und UPSERT `stats.alltime`/`stats.map_alltime`.
4. Nach dem Commit eine Pipeline: `ZINCRBY lb:{mode}:30d Δscore` und `lb:{mode}:all Δscore`, `DEL st:{mode}:{uuid}:*`, `DEL top:{mode}:*`, Publish `net:stats StatsUpdated`.
5. Rundenblock (§4.7) an jeden Spieler. Danach Coin-Auszahlung (§6.11).
6. Schlägt die Valkey-Pipeline fehl: loggen. Der Reconcile-Job korrigiert das nachts.

### 6.5 Rollierendes Fenster
1. 30 Tage = die Tage `heute−29 … heute` (Europe/Berlin), also `day > current_date − 30`. `/statsd N`: `day > current_date − N`.
2. Rollover täglich **00:00:05** Europe/Berlin (Lock §2.2): Für `D = heute − 30` werden alle `(uuid, points, wins)` des Tages gelesen, dann `ZINCRBY lb:{mode}:30d −score(points,wins)` und am Ende `ZREMRANGEBYSCORE lb:{mode}:30d -inf 0`.
3. Reconcile täglich **04:00**: Aufbau `lb:{mode}:30d:tmp` aus `SUM(daily)` und `lb:{mode}:all:tmp` aus `alltime`, danach `RENAME`.
4. Partitionsjob (§2.1).
Kein Monatsreset (belegt N05/N16).

### 6.6 Ranking
- Score = `points × 2^21 + min(wins, 2^21−1)` (exakt als Double < 2^53). Bei gleichen Punkten liegen mehr Siege vorne, danach die UUID lexikografisch (Valkey). Annahme (Original-Tie-Break unbelegt).
- Position = `ZREVRANK + 1`. Nicht im Set → `-`.
- `metric=karma`: Score = Karma; `metric=time` (Bestzeit, Bingo): kleinere Zeit ist besser, Score = `−millis`; `metric=none` (MasterBuilders): kein ZSET, Zeile „Position im Ranking“ entfällt.
- Versteckte Spieler bleiben im Ranking.
- Gesperrte Spieler bleiben drin, bis ein Team-Reset erfolgt (Option `ranking.excludeBanned=false`).

### 6.7 Modus-Katalog (Seed `stats.mode`)
| key | Felder in `/stats`-Reihenfolge | metric / Formel | resettable | Beleg |
|---|---|---|---|---|
| `bedwars` | rank, points, kills, deaths, kd, games, wins, winrate, beds | points = **5·kills + 10·beds + 50·wins** | ja | Felder belegt; Formel abgeleitet (2 Datenpunkte, exakt) |
| `skywars` | rank, points, kills, deaths, kd, games, wins, winrate | points = **1·kills + 5·wins** | ja | belegt / abgeleitet (1 Datenpunkt, exakt) |
| `jumpleague` | rank, points, kills, kd, wins, goals_clean | points = 40·Ziel fehlerfrei + 50·Sieg + 10·DM-Kill | ja | teilweise belegt (N16 §9) |
| `ttt` | rank, karma, kills, deaths, games, wins | karma vom Modul: +10 Traitor tötet Innocent, +20 Innocent/Detective tötet Traitor, +20 Sieg | ja | teilweise belegt |
| `cores` | rank, points, kills, deaths, kd, games, wins, winrate, cores | points vom Modul (Annahme: 5·kill + 20·core + 50·win) | nein | Felder teilweise belegt; Formel Annahme |
| `partygames` | rank, points, games, wins, winrate, minigames_played, minigames_won | points vom Modul | nein | belegt (Hologramm 2025) |
| `survivalgames`, `endergames` | rank, points, kills, deaths, kd, games, wins, winrate | points = 1·kill + 5·win (Annahme wie SkyWars) | nein | Annahme |
| `gungame` | rank, points, kills, deaths, kd, level_record | points = kills (Annahme) | ja | teilweise belegt |
| `masterbuilders` | games, wins, losses, points | `none` | nein | belegt |
| `bingo` | rank, best_time, games, wins | `time` je Teamgröße (eigener Modus-Key `bingo_x1/x2/x3`) | nein | belegt |
| `duels` | – (Elo/Liga in der Duels-Spec) | – | ja (Kit1vs1) | – |
`extra`-Schlüssel: `beds`, `cores`, `karma`, `goals_clean`, `minigames_played`, `minigames_won`, `level_record`, `best_time_ms`, `chests_opened`, `blocks_placed`.
Labels: `rank`=„Position im Ranking“, `points`=„Punkte“, `kills`=„Kills“, `deaths`=„Deaths“, `kd`=„K/D“, `games`=„Gespielte Spiele“, `wins`=„Gewonnene Spiele“, `winrate`=„Siegesquote“, `beds`=„Zerstörte Betten“ (alle belegt). Weitere Labels siehe §7.

### 6.8 Abgeleitete Werte & Formate
| Wert | Formel | Format `/stats` | Format Hologramm | Format Scoreboard |
|---|---|---|---|---|
| K/D | `deaths==0 ? kills : kills/deaths` (Annahme bei 0) | `#.##`, Punkt, HALF_UP: `11.21`, `1.88`, `0` | `#.##` Punkt: `1.33` | – |
| Siegesquote | `games==0 ? 0 : 100·wins/games` | `#.##` Punkt + ` %`: `84.85 %`, `25 %`, `0 %` | `#,##` Komma, ohne Leerzeichen: `27,59%` | `0,00` Komma + ` %`: `69,49 %` |
| Ganze Zahl | – | de_DE Tausenderpunkt `10.560` | ohne Trenner (LEGACY) / Punkt (MODERN) | `1.355.470` |
| Spielzeit | `floor(s/3600)` | – | – | `{n} Stunden` / `1 Stunde` |
| Countdown | – | `mm:ss`; ab 1 h `hh:mm:ss` | – | – |
| Datum/Zeit | Europe/Berlin | `dd.MM.yyyy`, `HH:mm` (Annahme) | – | – |
| Saison | – | `MMM-MMM yyyy`, Locale.ENGLISH (`Jul-Sep 2026`) | – | – |
`LEGACY`: Ganze Zahlen mit Komma (`11,877`, belegt 2019), ohne Punkte-Zeile und Siegesquote (BedWars 2019). 2017 lauten die Labels `Tode` und `Siegwahrscheinlichkeit: {n} Prozent`, der Kopf `Stats von` (Option `stats.legacyLabels`).

### 6.9 Statsreset (ResetToken)
1. `/resetstats` auf dem Lobby-Server des Modus. Prüfen: `mode.resettable`, `wallet.reset_tokens ≥ 1`, kein offener Dialog.
2. Ausgabe `reset.warn` + `reset.ask` mit Klick-Buttons `§a[Ja]` (`/resetstats confirm`) und `§c[Nein]` (`/resetstats cancel`). `SET reset:confirm:{uuid} mode EX 30`.
3. Annahme per Klick, per Chat „Ja“/„Nein“ (die Nachricht wird nicht an andere gesendet) oder per `/ja` (für Spieler mit Chat-Sperre).
4. Eine Transaktion: Snapshot nach `stats.reset_log`; `DELETE` aus `daily`, `alltime` und `map_alltime` für (uuid, mode); `wallet.reset_tokens − 1`; Ledger `currency='RESET_TOKEN', delta=−1, reason='STATS_RESET', idem_key='reset:{uuid}:{mode}:{epochMin}'`.
5. Nach dem Commit: `ZREM` aus beiden ZSETs, Cache löschen, `reset.done`. Der Token wird nur bei Erfolg verbraucht.
6. Coins: Das Original setzte (modusgebundene) Coins mit zurück. Hier gibt es netzwerkweite Coins, deshalb **kein Coin-Reset** (Option `reset.coins=false`).
Ein Reset gilt immer für 30 Tage **und** gesamt (belegt). Resetfähige Modi laut Shop: BedWars, FFA, GunGame, JumpLeague, SkyWars, TTT, Kit1vs1.

### 6.10 /togglestats
Voraussetzung: 100 Spielstunden (Forenangabe, kein Rang nötig). Wirkung:
- Fremde sehen `stats.hidden` (in `/stats`, `/statsall`, `/statsd`, `/stats #N` und im Profil-GUI).
- Team mit `retromc.stats.bypass` sieht die Stats trotzdem.
- Wände und Hologramme zeigen den Spieler weiter.
- Kein Cooldown (Annahme: 3 s Anti-Spam).

### 6.11 Coins & CoinSystem
| Punkt | Regel | Beleg |
|---|---|---|
| Speicher | `economy.wallet.coins` + Ledger, write-through, idempotent über `idem_key` | N07 |
| Rundenauszahlung | Basisbetrag pro Spieler vom Modul (Sieg, Kills, Spielzeit). GunGame und FFA zahlen 0 | Quellen belegt, Beträge pro Modus in den Spiel-Specs |
| Boost | Jeder Spieler der Runde mit Boost-Rang (Supremium, Supremium+; Spec 01 §6.1) ist ein Booster. Bonus = `floor(base × min(0,10 × n, 0,40))`, n = Booster in der Runde | abgeleitet: 13 von 20 Datenpaaren passen exakt (z. B. 475→142 bei n=3, 239→95 bei n=5, 314→31 bei n=1, 134→26 bei n=2); Shop: „bis zu 10 %“ |
| Anzeige | Zeile 1 `coins.round` (`+{base} Coins`), Zeile 2 `coins.boost`. Namensliste: 1 → `A`; 2 → `A, B`; ≥ 3 → `A, B und {n−2} weiteren` | belegt |
| Einzelgutschrift | außerhalb von Rundenenden: `coins.received` | belegt |
| Ledger-Gründe | `ROUND`, `ROUND_BOOST`, `DAILY`, `LOTTERY`, `VOTE`, `ADMIN`, `PURCHASE`, `REFUND`, `STATS_RESET`, `CB_EXCHANGE`, `CB_GOLDSHOP` (Spec 07), `KIT_PURCHASE` (Spec 04/05) | Annahme |
| Kein Handel | Coins sind nicht übertragbar und nicht gegen Echtgeld verkäuflich | belegt |

### 6.12 Ränge (Anzeige-Defaults) – Quelle **Spec 01 §6.1** (einzige Rangtabelle, LuckPerms-Meta)
| Gruppe | Anzeigename | Tab-Präfix | Farbe MODERN | Farbe LEGACY | Gewicht | Boost/GG |
|---|---|---|---|---|---|---|
| `admin` | Administrator | `Admin` | `§4` | `§4` | 900 | GG |
| `srdev` / `dev` | Senior-Developer / Developer | `SrDev` / `Dev` | `§c` | `§c` | 850/800 | GG |
| `srcontent` / `content` | Senior-Content / Content | `SrContent` / `Content` | `§c` | `§c` | 750/700 | GG |
| `srmod` / `mod` / `sup` | Senior-Moderator / Moderator / Supporter | `SrMod` / `Mod` / `Sup` | `§c` | `§c` | 650/600/550 | GG |
| `builder` | Builder | `Builder` | `§2` | `§2` | 500 | GG |
| `vip` | VIP | `VIP` | `§d` | `§5` (Option `YouTuber`) | 450 | GG |
| `supremiumplus` | Supremium+ | `Suprem+` | Verlauf `#FF55FF→#55FFFF`, Fallback `§d` | `§d` | 400 | Boost, GG |
| `supremium` | Supremium | `Suprem` | `§b` | `§b` | 350 | Boost, GG |
| `premiumplus` | Premium Plus | `Premium+` | `§e` | `§e` | 300 | – |
| `premium` | Premium | – | `§6` | `§6` | 200 | – |
| `default` | Spieler | – | `§a` | `§8` | 0 | – |
Präfix und Name haben dieselbe Farbe. Trenner `§7|` (MODERN) bzw. `§8┃` (LEGACY). `spec` (1000, §7) siehe Spec 01 O6. „X+“ in Specs = Gewicht ≥ X.

### 6.13 i18n
1. Sprachen: Liste und Fallback-Kette aus **Spec 01 §6.14** (`de_DE` Standard und Rückfall, `en_US` Pflicht, `bar`/`gsw` Dialekte).
2. Erster Join: Client-Locale `de*` → de_DE, `en*` → en_US, sonst de_DE (Annahme). Danach gilt `core.player.locale`.
3. `/language` (Spec 01 #67, Velocity) setzt die Sprache. Ablauf: Datenbank → `profile:{uuid}.locale` → Publish `net:profile` → alle Server rendern neu (Tab, Scoreboard).
4. Rendering: `I18n.render(player, key, TagResolver...)`. Platzhalter sind typisiert: `Placeholder.unparsed` für Namen und Texte von Spielern, `Placeholder.component` für Farb-Bausteine. Fehlender Key → `<red>[fehlender Text: key]` (vorhanden).
5. Zahlen, Datum und Dauer formatiert `core-i18n` mit `Formats` je Locale. Die /stats-Dezimalregel (Punkt) gilt in allen Sprachen (Kompatibilität mit Addons).

### 6.14 Grenzen
| Grenze | Wert |
|---|---|
| Scoreboard-Titel | ≤ 32 sichtbare Zeichen |
| Scoreboard-Zeile | ≤ 40 sichtbare Zeichen |
| GUI-Titel | ≤ 32 Zeichen |
| `/stats` | max. 1 Abfrage/s pro Spieler |
| Top-Listen | 10 Einträge (GunGame-Hologramm 5) |
| `/statsd` | 1–30 Tage |
| Reset-Dialog | 30 s |
| Clan-Tag | 2–5 Zeichen |
| Tab-Einträge | 80 sichtbar |

---

## 7. Nachrichten-Katalog

Spalte „Orig.“ = Original-Wortlaut als Referenz (nicht übernehmen, wo eigener Text steht). B = Beleg: b (belegt) / a (abgeleitet) / A (Annahme).

| Key | Format | Standardtext (de_DE) | Orig. | B |
|---|---|---|---|---|
| `prefix.mode` | `§f[{modeColor}{mode}§f] ` | – | `[BedWars] ` | b |
| `prefix.coins` | `§7[§e{name}§7] ` | name = `Coins` | `[CoinSystem]` | b (Struktur) |
| `prefix.friends` / `prefix.clan` / `prefix.party` / `prefix.lobby` | → **Spec 01 §7** (MODERN `§7[§cFreunde§7] `, `§7[§cClans§7] `, `§7[§dParty§7] `, `§6[Lobby] `; LEGACY §4/§5) | – | – | b |
| `chat.player` | `{channel}{icon}{ctx}{clan}{nameColor}{name}§7: §f{message}` | – | `[TAG] Name: Text` | b |
| `chat.clan-tag` | `§7[§e{tag}§7] ` | – | dito | b |
| `chat.no-clan` | `§7[-] ` | – | `[-]` | b |
| `chat.channel.all` | `§7[@all] ` | – | `[@all]` | b |
| `chat.channel.spectator` | `§7[OFF] ` | – | `[OFF]` | b |
| `chat.gg` | `§7§k--§r {color}{text} §7§k--` | – | dito | b |
| `chat.join` / `chat.quit` / `game.countdown` / `game.countdown.one` / `game.restart` | → **Spec 03 §7.2** (`common.join`, `common.quit`, `common.countdown`, `common.countdown.one`, `common.restart`) – hier nicht mehr definiert | – | – | b |
| `tab.header.mode` | `§e{brand}.net {modeColor}{serverDisplay}` | – | `Vorbild.net BedWars` | b |
| `tab.header.report` | `§7Regelverstoß gesehen? §c/report §7<Spieler>` | eigen | `Du möchtest einen Spieler melden? /report <Spieler>` | b |
| `tab.footer.1` | `§7Lade Freunde ein, starte Partys oder gründe` | eigen | `Füge deine Freunde hinzu, erstelle Partys oder gründe` | b |
| `tab.footer.2` | `§7deinen eigenen Clan: §a/friend§7, §d/party §7und §e/clan` | eigen | `deinen eigenen Clan mit: /friend, /party und /clan` | b |
| `tab.footer.server` | `§7Du bist auf Server §e{server}` | funktional | gleich | b |
| `tab.entry.rank` | `{color}{prefix} §7| ` | – | `Suprem \| ` | b |
| `tab.entry.clan` | ` §7[§e{tag}§7]` | – | `[CLAN]` | b |
| `tab.entry.team` | `{teamColor}{team} §7| §f{name}` | – | `Grün \| Name` | b |
| `sb.title` | `§f§l{BRAND_UPPER}.NET` | `RETROMC.NET` | `VORBILD.NET` | b |
| `sb.label.rank` / `.playtime` / `.coins` / `.gold` / `.clan` / `.friends` | `§f{label}` | `Rang`/`Spielzeit`/`Coins`/`Gold`/`Clan`/`Freunde` | gleich | b |
| `sb.value.rank` | `{rankColor}{rankName}` | – | `Administrator` | b |
| `sb.value.playtime` | `§6{n} Stunden` / `§6{n} Stunde` | – | `455 Stunden` | b |
| `sb.value.coins` / `.gold` | `§e{n}` / `§6{n}` | – | dito | b |
| `sb.value.clan` | `§b{online}§7/{total} §7[§b{tag}§7]` | – | `7/29 [CLAN]` | b |
| `sb.value.no-clan` | `§7-` | – | – | A |
| `sb.value.friends` | `§a{online}§7/{total}` | – | `2/75` | b |
| `stats.header` | `§7-= §eStatistiken von §6{name} §7({period}) =-` | funktional | gleich | b |
| `stats.period.30d` / `.all` / `.days` / `.day` | – | `30 Tage` / `insgesamt` / `{n} Tage` / `1 Tag` | gleich (days/day: A) | b/A |
| `stats.line` | `§7 {label}: §e{value}` | – | gleich | b |
| `stats.footer` | `§7----------------------` | 22 × `-` | gleich | b |
| `stats.label.rank` … `.beds` | – | `Position im Ranking`, `Punkte`, `Kills`, `Deaths`, `K/D`, `Gespielte Spiele`, `Gewonnene Spiele`, `Siegesquote`, `Zerstörte Betten` | gleich | b |
| `stats.label.karma` / `.cores` / `.goals_clean` / `.losses` / `.best_time` / `.level_record` / `.minigames_played` / `.minigames_won` | – | `Karma` / `Zerstörte Cores` / `Fehlerfreie Ziele` / `Verlorene Spiele` / `Bestzeit` / `Level-Rekord` / `Gespielte Minispiele` / `Gewonnene Minispiele` | teils (`Zerstörte Cores`, Minispiele b) | b/A |
| `stats.value.none` | `-` | – | `-` | a |
| `stats.hidden` | `§cDieser Spieler hat seine Statistiken verborgen.` | eigen | `Die Statistiken von diesem Spieler sind versteckt` | b (Farbe A) |
| `stats.notfound` | `§cZu diesem Namen gibt es keinen Spieler.` | eigen | `Dieser Spieler konnte nicht gefunden werden` | b (Farbe A) |
| `stats.rank-empty` | `§cAuf Platz §e#{n} §csteht noch niemand.` | eigen | – | A |
| `stats.need-mode` | `§cBitte gib einen Spielmodus an: §e/stats <Modus> [Spieler]` | eigen | – | A |
| `stats.unknown-mode` | `§cUnbekannter Spielmodus: §e{mode}` | eigen | – | A |
| `stats.days-invalid` | `§cGib eine Anzahl an Tagen von §e1 §cbis §e30 §can.` | eigen | – | A |
| `stats.cooldown` | `§cBitte warte einen Moment, bevor du erneut Statistiken abfragst.` | eigen | – | A |
| `round.header` | `§7-= §eStatistiken dieser Runde §7=-` | funktional | gleich | b |
| `round.xp` | `§7 Gesammelte Erfahrung: §e{xp} XP` | – | gleich | b |
| `round.survived` / `round.won` | `§7 Überlebt: {mark}` / `§7 Gewonnen: {mark}` | – | gleich | b |
| `round.mark.yes` / `.no` | `§a✓` / `§c✗` | – | gleich | b |
| `round.footer` | `§8----------------------` | – | gleich | b |
| `top.header` | `§7-= §eTop 10 §7in {modeColor}{mode} §7({period}) =-` | eigen | – | A |
| `top.line` | `§7 #{pos} {nameColor}{name} §7- §e{points} {unit}` | eigen | – | A |
| `top.self` | `§7 Dein Platz: §e{pos}` | eigen | – | A |
| `togglestats.hidden` | `§7Deine Statistiken sind jetzt §cverborgen§7.` | eigen | – | A |
| `togglestats.visible` | `§7Deine Statistiken sind jetzt §asichtbar§7.` | eigen | – | A |
| `togglestats.requirement` | `§cDafür brauchst du mindestens §e100 Spielstunden§c.` | eigen | – | a |
| `reset.warn` | `§cAlle Statistiken in {modeColor}{mode} §cwerden endgültig gelöscht.` | eigen | `Es werden ALLE Statistiken in SkyWars unwiderruflich gelöscht` | b (teilw.) |
| `reset.ask` | `§cWillst du deine Statistiken für §e1 Token §czurücksetzen? {yes} {no}` | eigen | `Möchtest du wirklich deine Stats für 1 Token zurücksetzen lassen? [Ja] [Nein]` | b (teilw.) |
| `reset.button.yes` / `.no` | `§a§l[Ja]` / `§c§l[Nein]` (klickbar, Hover `§7Klicke zum Bestätigen`/`Abbrechen`) | funktional | `[Ja] [Nein]` | b |
| `reset.done` | `§aDeine Statistiken in {mode} wurden zurückgesetzt.` | eigen | – | A |
| `reset.cancel` | `§cDer Reset wurde abgebrochen.` | eigen | – | A |
| `reset.expired` | `§cDie Bestätigung ist abgelaufen.` | eigen | – | A |
| `reset.no-token` | `§cDu hast keinen Reset-Token.` | eigen | – | A |
| `reset.wrong-place` | `§cDas geht nur in der Lobby dieses Spielmodus.` | eigen | – | A |
| `reset.not-resettable` | `§cIn diesem Spielmodus kann man die Statistiken nicht zurücksetzen.` | eigen | – | A |
| `tokens.show` | `§7Du hast §e{n} §7Reset-Token.` | eigen | – | A |
| `coins.round` | `{prefix}§6+{n} Coins` | – | `[CoinSystem] +475 Coins` | b |
| `coins.boost` | `{prefix}§6+{n} Coins §7durch §b{RANK}§7-Boost von {names}` | – | `+142 Coins durch SUPREMIUM-Boost von A, B und 1 weiteren` | b |
| `coins.names.more` | `{a}§7, {b} §7und §b{n} §7weiteren` (Namen `§b`) | – | dito | b |
| `coins.received` | `{prefix}§7Du hast §e{n} §7Coins erhalten` | funktional | gleich | b |
| `coins.balance` | `{prefix}§7Du hast §e{n} §7Coins` | funktional | `[SkyWars] Du hast x Coins` | b |
| `coins.insufficient` | `{prefix}§cDu hast nicht genug Coins.` | eigen | – | A |
| `coins.admin-done` | `{prefix}§7Neuer Stand von §e{name}§7: §e{n} §7Coins` | eigen | – | A |
| `language.*` | → Spec 01 §7 (`lang.list.*`, `lang.set`) | – | – | – |
| `gui.confirm.yes` / `.yes.lore` | `§aBestätigen` / `§7Klicke hier, um deine Auswahl zu bestätigen.` | eigen | `Bestätigung` / `Klicke hier, um deine Auswahl zu bestätigen.` | b |
| `gui.confirm.no` / `.no.lore` | `§cAbbrechen` / `§7Klicke hier, um abzubrechen.` | eigen | – | A |
| `gui.page.prev` / `.next` / `.indicator` | `§eVorherige Seite` / `§eNächste Seite` / `§7Seite §e{p}§7/§e{n}` | eigen | – | A |
| `gui.back` / `gui.close` / `gui.empty` | `§cZurück` / `§cSchließen` / `§cKeine Einträge` | eigen | – | A |
| `item.hint.right` | ` §7(Rechtsklick)` | funktional | gleich | b |
| `title.win` / `title.win.sub` | `{color}{name}` / `§fhat die Runde gewonnen` | funktional | gleich | b |
| `title.go` | `§aLOS!` | funktional | gleich | b |
| `title.respawn` | `§cRespawn in §e{n} §cSekunden` (`.one`: `Sekunde`) | funktional | gleich | b |
| `bar.season` | `§cSeason: §e{range}` | funktional | gleich | b |
| `bar.tutorial` | `§6Tutorial §7({step}/{total}): §f{text} §7/tutorial` | – | gleich | b |
| `actionbar.news` | `§e+ {color}§l{title}: §f{url}` | – | gleich | b |
| `holo.personal.title` | `{modeColor}{mode} §6Statistiken` | funktional | gleich | b |
| `holo.personal.all` / `.30d` | `§7Deine §6All-Time Statistiken` / `§7Deine §eStatistiken §7der letzten §e30 Tage` | funktional | gleich | b |
| `holo.line` | `§7{label} §e{value}` | – | gleich | b |
| `holo.top.title` | `{modeColor}{mode} §6Bestenliste` | eigen | – | A |
| `sign.top` | `§l#{pos}` / `{name}` / `§e{points} Punkte` / `§7{wins} Siege` | eigen | `#2 / Name / 1000 wins / …` | a |
| `statsadmin.reset-done` / `.rebuild-done` | `§aStatistiken von §e{name} §ain {mode} gelöscht.` / `§aRangliste {mode} neu aufgebaut ({n} Einträge).` | eigen | – | A |
| `statswall.created` / `.removed` | `§aAnzeige angelegt (#{id}).` / `§aAnzeige entfernt.` | eigen | – | A |
| `error.data` | `§cDaten konnten nicht geladen werden. Versuche es gleich noch einmal.` | eigen | – | A |
| `general.no-permission` | vorhanden | – | – | – |

Englisch (`messages_en.properties`): jeder Key ist Pflicht. Die Stats-Labels sind die englischen Entsprechungen (`Statistics of`, `30 days`, `total`, `Ranking position`, `Points`, `Kills`, `Deaths`, `K/D`, `Games played`, `Games won`, `Win rate`, `Beds destroyed`). Belegt sind nur `Statistics of this game` und `played`/`won`; der Rest ist eine Annahme.

---

## 8. Schnittstellen

### 8.1 Services (Paper, `Bukkit ServicesManager` + statischer Zugang `PaperCorePlugin.services()`)
| Service | Methoden (Kern) | Nutzer |
|---|---|---|
| `I18n` | `Component render(Player/Locale, String key, TagResolver...)`, `Locale locale(UUID)`, `Formats formats(Locale)` | alle |
| `ChatService` | `registerContext(ChatContextProvider)`, `broadcast(Audience, key, args)`, `prefix(ModeKey)` | Spiel-Rahmen |
| `TabService` | `setHeaderFooter(Player, Component, Component)`, `setServerFooter(lines)`, `registerEntryProvider(TabEntryProvider)` (Teamformat, Order) | Spiel-Rahmen, Lobby |
| `BoardService` | `show(Player, BoardSpec)`, `update(Player, lineId, Component)`, `BoardSpec.builder().title().line(id, Supplier<Component>).blank().showScores(bool)` | alle |
| `BossbarService` | `show(Player, channel, BossBarSpec)`, `hide(Player, channel)` | Duels, Lobby, CityBuild |
| `ActionbarService` | `set(Player, channel, priority, Component, durationTicks)`, `clear(Player, channel)`, `ticker(List<NewsItem>)` | alle |
| `TitleService` | `show(Player, key, subKey, times, args)` | alle |
| `GuiService` | `open(Player, Gui)`, Builder `ChestGui`, `PagedGui<T>`, `ConfirmGui` | alle |
| `HotbarService` | `apply(Player, HotbarLayout)`, `HotbarLayout.slot(i, ItemStack, ClickHandler)` | Lobby, Spiel-Rahmen |
| `CoinService` | `long balance(UUID)`, `credit(UUID, long, Reason, idemKey)`, `debit(...) → boolean`, `payoutRound(matchId, Map<UUID,Long> base, Collection<UUID> roundPlayers)` (berechnet Boost, sendet Meldungen) | Spiel-Rahmen, Lobby |
| `StatsService` | `submit(MatchResult)`, `CompletableFuture<StatSnapshot> query(UUID, ModeKey, StatWindow)`, `byRank(ModeKey, period, int)`, `top(ModeKey, period, n)`, `sendStats(CommandSender, …)`, `roundSummary(Player, RoundSummary)` | Spiel-Rahmen, Lobby, Profil |
| `RankDisplayService` | `RankDisplay of(UUID)` (Farbe, Präfix, Gewicht, Gradient, Boost/GG) aus LuckPerms-Meta | Proxy-Spec liefert Daten |
| `HologramService` | `personal(Player, Location, lines)`, `global(Location, Supplier<List<Component>>)` | Lobby, Spiel-Rahmen |
| `NpcService` (**NEU**, angefordert von Spec 03 §8.3) | `spawn(NpcSpec{location, skin, name, lookAtPlayers, onRightClick, onLeftClick}) → Npc` (Paket-Entitäten, keine Fremd-Plugins) | Lobby, BedWars-Händler, CityBuild |
| `NametagService` (**NEU**, genutzt von Spec 04/05/06) | `prefix(Player, Component)`, `suffix(Player, Component)` (Scoreboard-Teams) | Spiel-Rahmen, Modi |
| `NetworkLevelService` (**NEU**, Integration) | `level(UUID)`, `addXp(UUID, String source, long xp) → LevelUp?`; Tabelle `stats.network_level`; Kurve `xpFür(n) = 250·n·(n+1)` (Annahme, wie Clan-Level Spec 01) | Spec 01 (Chat-Level), Spec 03 (Kill-XP), Spec 06 (Bingo/PartyGames/GunGame) |

### 8.2 Bukkit-Events (`net.retromc.paper.core.event`)
| Event | Felder | Auslöser |
|---|---|---|
| `ProfileLoadedEvent` | Player, Profile | Profil beim Join geladen (vor dem ersten Board) |
| `StatsSubmittedEvent` | MatchResult, Map<UUID, StatDelta> | nach dem Commit (§6.4 Schritt 4) |
| `CoinsChangedEvent` | UUID, oldBalance, newBalance, Reason | nach einer Ledger-Buchung |
| `LocaleChangedEvent` | Player, Locale | `/language` |
| `StatsResetEvent` | UUID, ModeKey, scope | §6.9 |

### 8.3 Netzwerk-Nachrichten (core-messaging, JSON)
| Kanal | Typ | Payload | Sender → Empfänger |
|---|---|---|---|
| `net:profile` | `ProfileChanged` | `{uuid, fields:["coins","gold","rank","locale","clan"]}` | alle Server/Proxy → alle |
| `net:stats` | `StatsUpdated` | `{mode, uuids[]}` | Spielserver → Lobbys (Wände, Hologramme) |
| `net:stats` | `LeaderboardChanged` | `{mode, period}` | Rollover-Job → alle |
| `net:social` | Typen aus Spec 01 §2.4 (`CLAN_UPDATE`, `CLAN_ONLINE`/`OFFLINE`, `FRIEND_*`, `PROFILE_INVALIDATE`) | Payload Spec 01 §8.2 | Proxy (Social) → Paper (Tab, Chat, Scoreboard) |
Proxy-Seite (velocity-core): Sie liefert den Rang über LuckPerms-Messaging, Clan- und Freundesdaten über `net:social` und `presence:{uuid}`. Diese Spec setzt voraus, dass `social`-Daten über `SocialQuery` (core-data, read-only SQL + Valkey) lesbar sind.

### 8.4 Pflichten anderer Bereiche
- **Spiel-Rahmen:** liefert `MatchResult` (Final-Kills bei BedWars), Basis-Coins, `ChatContextProvider` (Team, Zuschauer, Level), `TabEntryProvider` (Team), Rundenblock-Zeilen und setzt den Server-Zustand über `ServerLifecycle` (Spec 08 §8.1). Modus-Lobby = Kategorie `LOBBY` mit `mode` ≠ `lobby` (Spec 08 §6.5).
- **Lobby:** füllt `ui:news`, platziert Anker, öffnet `StatsProfileGui`, verwaltet die Hotbar.
- **Shop/Store:** bucht `RESET_TOKEN` und `GOLD` über den Ledger (`reason='PURCHASE'`, idempotent per `tx_id`).

---

## 9. Abnahmekriterien

| # | Prüfung | Art |
|---|---|---|
| 1 | `Formats.integer(10560, de_DE)` = `10.560`; `kd(314,28)` = `11.21`; `kd(186,99)` = `1.88`; `kd(0,0)` = `0`; `winrate(140,165)` = `84.85 %`; `winrate(33,132)` = `25 %`; `winrate(0,1)` = `0 %`; Hologramm `winrate(16,58)` = `27,59%`; Scoreboard `winrate(16,19)` = `84,21 %` | Unit (core-stats) |
| 2 | BedWars-Formel: (314 Kills, 199 Betten, 140 Siege) → 10.560; (3, 1, 0) → 25. SkyWars: (186, 33) → 351 | Unit |
| 3 | Boost: (475, n=3) → 142; (239, n=5) → 95; (314, n=1) → 31; (134, n=2) → 26; (15, n=6) → 6; (0, n=4) → 0 und keine Boost-Zeile | Unit |
| 4 | Namensliste: 1/2/3/7 Booster → `A` / `A, B` / `A, B und 1 weiteren` / `A, B und 5 weiteren` | Unit |
| 5 | `/stats`-Ausgabe (§-bereinigt) passt auf die Community-Regexe: Kopf `.*statistiken von\s+(?<p>[^\(]+?)\s*\((?<period>[^\)]+)\).*` (case-insensitive), Zeilen `^\s*(?<key>[^:]+)\s*:\s*(?<val>.+?)\s*$`, Fuß `^-{5,}$`; Periode enthält `30` bzw. `insgesamt` | Unit (Golden-File) |
| 6 | Fußzeile hat genau 22 `-`; jede Datenzeile beginnt mit genau 1 Leerzeichen; Name ist `§6` ohne Rangfarbe | Unit |
| 7 | Rolling Window: Runde an Tag D zählt in `/stats` bis einschließlich D+29 und fällt an D+30 um 00:00:05 heraus; ZSET-Score danach = SQL-Summe | Integration (Testcontainers Postgres + Valkey, Uhr injiziert) |
| 8 | `submit()` zweimal mit derselben `matchId` → Werte nur einmal gezählt | Integration |
| 9 | Reset: Token −1, `daily`/`alltime` leer, ZREM aus beiden ZSETs, Snapshot im `reset_log`. Bei Abbruch oder Ablauf (30 s): kein Token-Verbrauch | Integration |
| 10 | Ranking: gleiche Punkte, mehr Siege → besserer Platz; `/stats #1` = Spieler mit höchstem Score; nicht gelisteter Spieler → `Position im Ranking: -` | Unit + Integration |
| 11 | Versteckter Spieler: Fremde bekommen `stats.hidden`, er selbst und `retromc.stats.bypass` sehen die Stats, die Wand zeigt ihn | Integration (MockBukkit) |
| 12 | `/togglestats` bei 99 h → `togglestats.requirement` | Unit |
| 13 | Lobby-Scoreboard: 15 Zeilen in §4.2-Reihenfolge, Scores 14…0, Rang ⇄ Spielzeit alle 100 Ticks; Coin-Änderung auf anderem Server erscheint ≤ 1 s nach `ProfileChanged` | Manuell + MockBukkit |
| 14 | Kein Flackern: Bei einer Wertänderung wird nur 1 Team-Update-Paket gesendet (FastBoard-Diff) | Paket-Mitschnitt |
| 15 | Tab-Sortierung: Admin vor Dev vor Mod vor Supremium vor Premium vor Spieler; innerhalb gleicher Stufe alphabetisch | Manuell |
| 16 | Chat: Spieler mit Clan `ABC`, Rang Premium → `§7[§eABC§7] §6Name§7: §fhi`; in der Lobby ohne Clan-Tag; `gg` von Supremium → verschleiertes GG | Unit (Formatter ohne Server) |
| 17 | 1.8-Client (ViaVersion): Verläufe fallen auf die Fallback-Farbe zurück, Scores bleiben sichtbar, keine Zeile > 40 Zeichen | Manuell |
| 18 | `/language en` → Tab, Scoreboard und Stats-Labels englisch, ohne Neuverbindung | Manuell |
| 19 | Jede Taste in `messages.properties` existiert in `messages_en.properties` und umgekehrt; keine verwaisten Keys im Code | Unit |
| 20 | GUI: Shift-Klick und Ziehen bewegen keine Items; ESC im `ConfirmGui` ruft `onCancel` | MockBukkit |
| 21 | `/stats` zweimal innerhalb von 1 s → zweiter Aufruf `stats.cooldown` | Integration |
| 22 | Nickname-Abfrage → `stats.notfound`, Echtname wird nicht verraten | Integration |

---

## 10. Offene Punkte (je mit DEFAULT-ENTSCHEIDUNG)

| # | Offener Punkt / Widerspruch | DEFAULT |
|---|---|---|
| 1 | Punkteformeln nur abgeleitet (BedWars aus 2 Datenpunkten, SkyWars aus 1). Ein Forumsbeitrag von 2021 nennt für Classic BedWars das Verhältnis 3:2:3 | 5/10/50 bzw. 1/5 (2024–26), als JSON in `stats.mode` änderbar ohne Migration |
| 2 | Coin-Boost: 10 % pro Booster, Deckel 40 %. Abgeleitet; 7 von 20 Datenpaaren weichen ab (z. B. 367→73 und 89→17 bei n=1; OCR-Fehler, Booster ohne Namensnennung oder eigener Boost des Empfängers möglich) | `floor(base × min(0,1n, 0,4))`; `n` zählt Booster beim Rundenende inkl. Empfänger, die Namensliste ohne Empfänger |
| 3 | Original-Layout des Statistik-Profil-GUIs unbelegt | §5.4 |
| 4 | Klammerfarbe der Modus-Präfixe: §f (Messung 1.8, JumpLeague 2026) gegen §7 (mehrere Video-Notizen mit „?“) | Modus-Präfix `§f`, System-Präfixe `§7` |
| 5 | Admin-Farbe: §4 (Scoreboard 2025, Tab 2016) gegen §c (Tab 2026) | `§4`, konfigurierbar |
| 6 | Freunde-/Party-Präfix: 1.8 §4/§5, 2026 §c/§d | MODERN `§c`/`§d`, LEGACY `§4`/`§5` |
| 7 | Dezimalformat je Oberfläche verschieden (Chat Punkt, Hologramm/Scoreboard Komma) | wie §6.8 beibehalten (1:1) |
| 8 | Score-Zahlen: in der Lobby sichtbar, im Duels-Match nicht | pro Board; Standard sichtbar |
| 9 | Kopf des Rundenblocks `-= =-` (BedWars, Addon-Code) gegen `=- -=` (OCR SkyWars/PartyGames) | `-= … =-` |
| 10 | Rundenblock-Fuß §8 gegen /stats-Fuß §7 | wie belegt: Runde `§8`, /stats `§7` |
| 11 | Coins wurden beim Reset im Original mit zurückgesetzt (modusgebundene Coins) | kein Coin-Reset (`reset.coins=false`) |
| 12 | Zweck und Herkunft von „Gold“ unbelegt (Scoreboard-Zeile, „Gold-Shop“ in CityBuild) | `wallet.gold` als zweite Währung, nur Anzeige und Ledger; Verdienst und Ausgabe regelt die CityBuild-/Store-Spec |
| 13 | Wortlaut des `/statsd`-Kopfs und Ranking-Zeile darin | `({n} Tage)`/`(1 Tag)`, Rang `-` |
| 14 | `/stats` in der Hauptlobby (ohne Modus) unbelegt | `/stats <Modus> [Spieler]` |
| 15 | Tie-Break im Ranking unbelegt | mehr Siege, dann UUID |
| 16 | K/D bei 0 Deaths unbelegt | = Kills |
| 17 | Cooldowns `/stats`/`/togglestats` unbelegt | 1 s / 3 s |
| 18 | Hex-Werte der Verläufe 2026 nur aus Twitch-Messung | JumpLeague `#D95D51:#ECE12A`, sonst einfarbig; Supremium+ `#FF55FF→#55FFFF` (Spec 01 §6.1) |
| 19 | Rangnamen des Originals (Premium/Supremium/Suprem) | Rangnamen und -farben aus Spec 01 §6.1 (Premium, Premium Plus, Supremium, Supremium+, VIP); alles per LuckPerms-Meta änderbar. Frühere Eigennamen `Elite`/`Elite+`/`Creator` verworfen |
| 20 | Spielerfarbe §8 (1.8) gegen §a (2024+) | MODERN `§a`, LEGACY `§8` |
| 21 | Lobby-Scoreboard-Zeile 14 (leer oben) nur aus 15 Scores geschlossen | leere Zeile oben |
| 22 | Taktung Newsticker (Actionbar) und Rang/Spielzeit-Wechsel unbelegt | 8 s / 5 s |
| 23 | Retention `stats.match_player` | 180 Tage, danach löschen (Datenschutz, Notiz 07 §9) |
| 24 | Anzeige gesperrter Spieler auf Bestenlisten | bleiben bis zum Team-Reset (`ranking.excludeBanned=false`) |
| 25 | Englische Stats-Labels großteils unbelegt | Liste in §7 |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | Pakete `dev.netzwerk.*`, statischer Zugang `NetzwerkCore.services()`, PDC `netzwerk:*`, Titel-Standard `NETZWERK` | `net.retromc.*`, `PaperCorePlugin.services()`, PDC/Font `retromc:*`, Titel `RETROMC.NET` |
| K2 | Migration `V2__core_ui_stats` kollidierte mit Spec 01 | `V2_1__core_ui_stats.sql` (Spec 08 W2) |
| K3 | Eigene Rangtabelle (`elite`, `elite_plus`, `creator`, `player`, Gewichte 0–100) widersprach Spec 01 §6.1 | §6.12 übernimmt Spec 01 §6.1 (Gruppen, Farben, Gewichte 0–1000); Boost/GG = Supremium/Supremium+ (+VIP/Team für GG) |
| K4 | Scoreboard-Titel `{BRAND_TITLE}` ohne `.NET` (Spec 01/03 mit `.NET`) | überall `§f§l{BRAND_UPPER}.NET` |
| K5 | Tab-Header `§e{brand} …` ohne `.net`, CityBuild `§e` (Spec 01 `§6`) | `§e{brand}.net {modeColor}{serverDisplay}`, CityBuild `§6`; paper-core einziger Renderer |
| K6 | Tab-Sortierformel `10_000 − weight*10` (Paper zeigt höhere Werte oben) | `order = rankWeight` |
| K7 | `/language` und `language.*`-Keys doppelt zu Spec 01 | Befehl/Keys in Spec 01; hier nur Neu-Rendern |
| K8 | `prefix.friends/clans/party/lobby` doppelt und abweichend (`prefix.clans`, Lobby `§7[§6Lobby§7]`) | Definition nur in Spec 01 §7 (`prefix.clan`, `§6[Lobby] `) |
| K9 | `chat.join/quit`, `game.countdown*`, `game.restart` doppelt zu Spec 03 `common.*` | Spec 03 §7.2 verbindlich |
| K10 | `profile:{uuid}` mit anderen Feldern/TTL als Spec 01 | eine Definition (Spec 01 §2.3) |
| K11 | `net:social`-Typen `ClanTagChanged`… existieren beim Erzeuger (Spec 01) nicht | Typen aus Spec 01 §2.4 |
| K12 | Server-Rolle `MODE_LOBBY` unbekannt in der Registry (Spec 08) | Modus-Lobby = Kategorie `LOBBY` + `mode` |
| K13 | Globales Level (Chat, Kill-XP, Bingo-XP) ohne Eigentümer | `NetworkLevelService` + `stats.network_level` hier |
| K14 | `NpcService`/`NametagService` von Spec 03–06 genutzt, hier nicht angeboten | in §8.1 ergänzt |
| K15 | MockBukkit-Tests (#11, #13, #20) vs. Spec 08 O6 „kein MockBukkit“ | Logik plattformfrei testen; MockBukkit-Tests nur mit Tag `mockbukkit`, sobald eine Version für Paper 26.3 geprüft ist (Spec 00 Konventionen) |
