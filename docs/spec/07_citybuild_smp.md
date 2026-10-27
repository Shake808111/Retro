# Spec 07: CityBuild & SMP (Langzeitmodi)

Stand 2026-10-21 · Quellen: Notiz 04 (N04, Q1–Q10), Notiz 09 (N09, Wiki-Repo `citybuild-wiki`, Inhalt Dez. 2024), Video-Sweep (VS, Clips 2021–2026), OCR-Review (OCR), 1 Frame gesichtet (SMP „Kopfgeld-Übersicht“, 20.09.2026).
Beleg-Stufen: **belegt** (Wiki-Text, Frame oder OCR klar lesbar) · **abgeleitet** (aus Belegen gerechnet/geschlossen) · **Annahme** (ohne Beleg festgelegt).
Ära-Schalter `cb.era`: `MODERN` (Standard, Stand 2025/26) | `LEGACY` (2023/24: Score-Zahlen sichtbar, Doppelpunkte, „Quest n“-Bossbar, Chat-Level ohne Klammern).
Eigene Namen: Welten, Bosse, NPCs, Talismane und Custom-Items bekommen eigene Namen (Spalte „Nachbau“). Das Original steht nur als Referenz daneben. Rein funktionale Wörter (Jobnamen, Seltenheiten, „Zurück“, „Kopfgeld“) bleiben.
Schreibweise wie Spec 02: §-Notation, `{x}` = Platzhalter, Umsetzung über `core-i18n`/MiniMessage. `{P}` = CityBuild-Präfix `§7[§6CityBuild§7] `, `{S}` = SMP-Präfix `§7[§5SMP§7] `. Brand-Platzhalter `<brand>` = „RetroMC“.

---

## 1. Umfang & Module

| Gradle-Modul | Status | Paket | Plugin | Inhalt |
|---|---|---|---|---|
| `:citybuild` | vorhanden (Gerüst) | `net.retromc.citybuild` (Unterpakete `world`, `plot`, `economy`, `shop`, `level`, `job`, `guild`, `talisman`, `boss`, `space`, `item`, `crate`, `booster`, `quest`, `tutorial`, `chat`, `ui`, `home`, `warp`, `collection`) | `${brandName}CityBuild` (`main: net.retromc.citybuild.CityBuildPlugin`) | alle CityBuild-Server; Rolle per `server-role` in `config.yml`: `LOBBY`, `PLOT`, `FARM`, `SPACE`, `END` |
| `:citybuild-core` | **NEU** (in `settings.gradle.kts` ergänzen) | `net.retromc.citybuild.core` | – (reines Java) | Formeln ohne Paper: Geldformat, Level-/Prestige-Kurve, Plotpreis, Merge-/Hologramm-Limit, Gilden-Kostenkurve, Beutel-Formel, Job-Limits, Marktplatz-Limit, Inaktivitätsfrist. Voll unit-testbar |
| `:smp` | **NEU** | `net.retromc.smp` (`economy`, `shop`, `sell`, `combat`, `bounty`, `tpa`, `playtime`, `order`, `ui`) | `${brandName}SMP` | SMP-Lobby und SMP-Welt-Server, Rolle `SMP_LOBBY` / `SMP_WORLD` |
| `:platform:paper-core` | vorhanden | `net.retromc.paper.core` | `${brandName}Core` | Board/Tab/Bossbar/Actionbar/GUI/Hologramm/Chat (Spec 02) |
| `:platform:velocity-core` | vorhanden | – | – | Registrierung der Templates `cb-*`, `smp-*` (Spec 08 §6.5), Warteschlange SMP; Tablist-Footer **nicht** über den Proxy (Spec 02 `TabService`) |

Server-Topologie (Default, Annahme bis auf Belege). Namen nach Spec 08 §6.6 (`{templateId}-{n}`); Templates `cb-lobby`, `cb-plot`, `cb-farm`, `cb-space`, `cb-end`, `smp-lobby`, `smp-world` sind Kategorie `PERSISTENT` (Spec 08 §6.5):

| Server-ID | Rolle | Inhalt | Beleg |
|---|---|---|---|
| `cb-lobby-1..n` | LOBBY | CityBuild-Lobby (Spawnstadt, NPCs, Shops, HoF, Portale) | belegt („Diese Lobby wird in 60 Sekunden neugestartet“) |
| `cb-plot-1..n` | PLOT | 11 Plotwelten; Welt→Server-Zuordnung in `plotworlds.yml` | Annahme |
| `cb-farm-1..9` | FARM | je eine Farmwelt + eigener Nether; Tab zeigt „Farming 2“ | belegt (Tab-Footer „Du bist auf Server Farming 2/3“) |
| `cb-space-1..n` | SPACE | Weltall-Instanzen (solo/duo) | abgeleitet |
| `cb-end-1` | END | End-Event (nur bei Event offen) | belegt (Wiki) |
| `smp-lobby-1` / `smp-world-1..n` | SMP | Spawn/Lobby bzw. Welt („World 2“); Warteschlange | belegt (Tab-Footer „World 2“, „Lobby“) |

Abhängigkeiten: `compileOnly(libs.paper.api)`, `compileOnly(project(":platform:paper-core"))`, `implementation(project(":citybuild-core"))` (shadowed). Keine Fremd-Plugins (kein WorldEdit/PlotSquared): eigener `PlotChunkGenerator`, eigene Region-Kopie (Chunk-Snapshots) für clear/move/restore. NPCs und Hologramme über `HologramService`/NPC-API aus paper-core (Spec 02 §4.9/§4.10). Tests: JUnit 6 in `:citybuild-core`, MockBukkit optional (Annahme).

---

## 2. Datenmodell

### 2.1 PostgreSQL – Migration `db/migrations/V7_1__citybuild_smp.sql` (NEU), Schemas `cb` und `smp`

Geld: immer `BIGINT` in Cent (`*_c`). Zeit: `TIMESTAMPTZ`. Item-Daten: `BYTEA` (Paper `ItemStack#serializeAsBytes`).

| Tabelle | Spalten (Typ) | Schlüssel / Indizes | Zweck |
|---|---|---|---|
| `cb.player` | `uuid UUID`, `name VARCHAR(16)`, `money_c BIGINT NOT NULL DEFAULT 0 CHECK (money_c>=0)`, `level SMALLINT DEFAULT 0`, `xp BIGINT DEFAULT 0` (im aktuellen Level), `xp_total BIGINT DEFAULT 0`, `prestige SMALLINT DEFAULT 0 CHECK (prestige BETWEEN 0 AND 3)`, `job VARCHAR(16)`, `side_job VARCHAR(16)`, `tutorial_step SMALLINT DEFAULT 1`, `tutorial_done BOOLEAN DEFAULT false`, `ec_bought SMALLINT DEFAULT 0`, `ec_books SMALLINT DEFAULT 0`, `home_books SMALLINT DEFAULT 0`, `quest_points INT DEFAULT 0`, `toggles JSONB DEFAULT '{}'` (trade, plotRoles, shopTx, shopChest, plotNotify, tpa, chatTrade, chatChannel), `first_join TIMESTAMPTZ`, `last_seen TIMESTAMPTZ` | PK `uuid`; IDX `(prestige DESC, level DESC, xp DESC)` für `/top level`; IDX `lower(name)` | Spielerstand |
| `cb.ledger` | `id BIGSERIAL`, `uuid UUID`, `delta_c BIGINT`, `balance_c BIGINT`, `reason VARCHAR(24)` (JOB, MARKET, EXCHANGE, PLOT_BUY, PLOT_MERGE, PLOT_MOVE, PLOT_SALE, SHOP_BUY, SHOP_SELL, TRADE, ADMINSHOP, MALL, FURNITURE, GUILD_DEPOSIT, TALISMAN, QUEST, ADMIN, TUTORIAL, ENDERCHEST, CRATE), `ref VARCHAR(64)`, `idem_key VARCHAR(64) UNIQUE`, `created_at` | PK `id`; IDX `(uuid, created_at DESC)` | Geldjournal (nur Anhängen) |
| `cb.plot_world` | `id SMALLINT`, `key VARCHAR(16) UNIQUE`, `icon VARCHAR(32)`, `tier SMALLINT`, `size SMALLINT`, `road SMALLINT DEFAULT 7`, `surcharge_c BIGINT`, `min_level SMALLINT`, `server VARCHAR(32)`, `open BOOLEAN` | PK `id` | Plotwelten (Seed §6.2) |
| `cb.plot` | `id BIGSERIAL`, `world_id SMALLINT`, `owner UUID NULL`, `alias VARCHAR(24)`, `privacy VARCHAR(7) DEFAULT 'PUBLIC'`, `claimed_at`, `owner_active_at` (letzte Owner-Aktivität), `admin_lock BOOLEAN DEFAULT false`, `home BOOLEAN DEFAULT false`, `spawn JSONB`, `title_on BOOLEAN DEFAULT true`, `buildall BOOLEAN DEFAULT false`, `offers_on BOOLEAN DEFAULT true`, `notify_on BOOLEAN DEFAULT true`, `fly_on BOOLEAN DEFAULT false`, `config JSONB DEFAULT '{}'`, `edge VARCHAR(48)`, `wall VARCHAR(48)`, `floor VARCHAR(48)`, `weather VARCHAR(5)`, `bossbar VARCHAR(128)`, `physics BOOLEAN DEFAULT true`, `deleted_at` | PK `id`; IDX `(owner)`; IDX `(world_id)`; UNIQUE `(world_id, lower(alias), owner)` | ein „Grundstück“ = Merge-Gruppe (zählt als 1 Plot) |
| `cb.plot_cell` | `world_id SMALLINT`, `gx INT`, `gz INT`, `plot_id BIGINT` | PK `(world_id,gx,gz)`; IDX `(plot_id)` | Teilplots einer Gruppe |
| `cb.plot_member` | `plot_id`, `uuid`, `role VARCHAR(9)` (CO_OWNER, ADMIN, BUILDER), `added_by`, `added_at` | PK `(plot_id,uuid)`; IDX `(uuid)` | Rollen |
| `cb.plot_ban` | `plot_id`, `uuid`, `by UUID`, `at` | PK `(plot_id,uuid)` | `/plot ban` |
| `cb.plot_visit` | `plot_id`, `uuid`, `at` | IDX `(plot_id, at DESC)` | Logbuch (letzte 9) |
| `cb.plot_hologram` | `id BIGSERIAL`, `plot_id`, `x,y,z DOUBLE PRECISION`, `text VARCHAR(256)` | IDX `(plot_id)` | Hologramme |
| `cb.plot_offer` | `id BIGSERIAL`, `plot_id`, `buyer UUID`, `amount_c`, `state VARCHAR(8)`, `created_at`, `expires_at` | IDX `(plot_id, state)` | `/plot offer` |
| `cb.plot_move` | `id BIGSERIAL`, `plot_id`, `target_world`, `target_gx`, `target_gz`, `state VARCHAR(10)` (SELECTING, RUNNING, DONE, CANCELLED), `progress SMALLINT`, `fee_c`, `started_at` | IDX `(plot_id)` | Verschiebung |
| `cb.plot_archive` | `id BIGSERIAL`, `plot_id`, `owner`, `snapshot JSONB` (Zeile+Zellen+Mitglieder), `blocks_ref VARCHAR(128)` (Datei im Objektspeicher), `reason VARCHAR(10)` (INACTIVE, DELETE, ADMIN), `archived_at` | IDX `(owner, archived_at DESC)` | `/plot restore` |
| `cb.shop` | `id BIGSERIAL`, `plot_id`, `creator UUID`, `world VARCHAR(32)`, `cx,cy,cz INT` (Behälter), `sx,sy,sz INT` (Schild), `item BYTEA`, `buy_c BIGINT NULL`, `sell_c BIGINT NULL`, `limit_c BIGINT NULL`, `turnover_in_c`, `turnover_out_c`, `active BOOLEAN`, `locked_until`, `created_at` | PK `id`; UNIQUE `(world,cx,cy,cz)`; IDX `(creator)`; IDX `(plot_id)` | Spielershops |
| `cb.shop_tx` | `id BIGSERIAL`, `shop_id`, `customer`, `qty INT`, `amount_c`, `dir CHAR(1)` (B=Kunde kauft, S=Kunde verkauft), `at` | IDX `(shop_id, at DESC)` | Journal |
| `cb.job_season` | `uuid`, `job VARCHAR(16)`, `season CHAR(7)` (`2026-10`), `job_xp BIGINT`, `money_c BIGINT`, `actions INT`, `side BOOLEAN` | PK `(uuid,job,season)`; IDX `(season, job_xp DESC) WHERE NOT side` | Job-Stats/Ranking |
| `cb.job_alltime` | `uuid`, `job`, `job_xp`, `money_c`, `actions` | PK `(uuid,job)` | `/job statsall` |
| `cb.job_of_month` | `season CHAR(7)`, `job` | PK `season` | Job des Monats |
| `cb.guild` | `id SERIAL`, `name VARCHAR(16)`, `founder UUID`, `deposited_c BIGINT`, `level SMALLINT`, `banner BYTEA`, `motd VARCHAR(256)`, `created_at`, `settings JSONB` | PK `id`; UNIQUE `lower(name)`; IDX `(deposited_c DESC)` | Gilden |
| `cb.guild_member` | `guild_id`, `uuid`, `role VARCHAR(16)` (FOUNDER, LEADER, MODERATOR, MEMBER, GUEST, `custom:<id>`), `joined_at` | PK `uuid`; IDX `(guild_id)` | 1 Gilde je Spieler |
| `cb.guild_role` | `id SERIAL`, `guild_id`, `name VARCHAR(16)`, `weight SMALLINT`, `perms TEXT[]` | IDX `(guild_id)` | Eigene Rollen (2026, Option §10) |
| `cb.guild_invite` | `guild_id`, `uuid`, `by`, `expires_at` | PK `(guild_id,uuid)` | Einladungen |
| `cb.guild_backpack` | `guild_id`, `contents BYTEA`, `version INT` | PK `guild_id` | `/backpack` (optimistisches Locking) |
| `cb.talisman_bag` | `uuid`, `slots SMALLINT DEFAULT 0` (0 = nicht freigeschaltet), `contents BYTEA` | PK `uuid` | Beutel |
| `cb.talisman_prod` | `uuid`, `slot SMALLINT`, `started_at`, `ready_at`, `type SMALLINT`, `rarity SMALLINT` | PK `(uuid,slot)` | Herstellung |
| `cb.home` | `uuid`, `name VARCHAR(16)`, `server`, `world`, `x,y,z DOUBLE PRECISION`, `yaw,pitch REAL` | PK `(uuid, lower(name))` | Homes |
| `cb.warp` | `name VARCHAR(24)`, `owner UUID NULL` (NULL = Serverwarp), `server`, `world`, `x,y,z,yaw,pitch`, `created_at`, `visits_week INT` | PK `lower(name)`; IDX `(owner)`; IDX `(visits_week DESC)` | Warps |
| `cb.warp_visit` | `warp`, `uuid`, `day DATE` | PK `(warp,uuid,day)` | Zählung eindeutiger Besucher |
| `cb.enderchest` | `uuid`, `idx SMALLINT CHECK (idx BETWEEN 1 AND 12)`, `contents BYTEA` | PK `(uuid,idx)` | `/ec` |
| `cb.collection` | `uuid`, `head_key VARCHAR(32)`, `at` | PK `(uuid,head_key)`; IDX `(uuid)` | Mob-Collection |
| `cb.quest` | `uuid`, `day DATE`, `slot SMALLINT`, `quest_key`, `progress INT`, `goal INT`, `done BOOLEAN` | PK `(uuid,day,slot)` | Tages-Quests |
| `cb.milestone` | `id SERIAL`, `key`, `goal BIGINT`, `progress BIGINT`, `starts_at`, `ends_at`, `reward JSONB` | PK `id` | Meilensteine |
| `cb.booster` | `id BIGSERIAL`, `type VARCHAR(12)`, `starts_at`, `ends_at`, `by UUID` | IDX `(type, ends_at)` | globale Booster |
| `cb.personal_booster` | `uuid`, `type`, `ends_at`, `factor NUMERIC(4,2)` | PK `(uuid,type)` | Quest-/Meilenstein-Booster |
| `cb.crate_balance` | `uuid`, `crate VARCHAR(24)`, `count INT` | PK `(uuid,crate)` | Crate-Einheiten |
| `cb.crate_log` | `id BIGSERIAL`, `uuid`, `crate`, `reward_key`, `at` | IDX `(uuid, at DESC)` | Öffnungen |
| `cb.reward_claim` | `uuid`, `chest VARCHAR(8)` (DAILY, WEEKLY, MONTHLY), `period VARCHAR(10)` | PK `(uuid,chest,period)` | Belohnungstruhen |
| `cb.rotation_offer` | `shop VARCHAR(10)` (ADMINSHOP, MALL, FURNITURE), `period VARCHAR(10)`, `slot SMALLINT`, `item_key`, `price_c`, `ticket VARCHAR(16)`, `stock_total INT NULL`, `stock_left INT NULL` | PK `(shop,period,slot)` | wechselnde Angebote mit Bestand |
| `cb.market_price` | `item VARCHAR(48)`, `price_c` | PK `item` | Marktplatz-Ankauf |
| `cb.boss_kill` | `id BIGSERIAL`, `boss_key`, `server`, `killed_at`, `damage JSONB` (uuid→Schaden), `side BOOLEAN` | IDX `(boss_key, killed_at DESC)` | Statistik „Bosse getötet“ |
| `cb.space_progress` | `uuid`, `zone SMALLINT DEFAULT 0` (0 = keine), `unlocked_at JSONB` | PK `uuid` | Weltall-Zonen |
| `cb.buddy` | `uuid`, `buddy UUID`, `since` | PK `(uuid,buddy)` | Buddy (Annahme §10) |
| `smp.player` | `uuid`, `money_c BIGINT DEFAULT 0`, `kills INT`, `deaths INT`, `playtime_s BIGINT`, `first_join`, `last_seen`, `toggles JSONB` | PK `uuid`; IDX `(playtime_s DESC)` | SMP-Stand |
| `smp.ledger` | wie `cb.ledger`, Gründe SELL, SHOP_BUY, BOUNTY_SET, BOUNTY_CLAIM, TRADE, ADMIN, ORDER | IDX `(uuid, created_at DESC)` | Journal |
| `smp.bounty` | `target UUID`, `total_c`, `contributors INT`, `last_raise` | PK `target`; IDX `(total_c DESC)` | Kopfgeld |
| `smp.bounty_contrib` | `target`, `uuid`, `amount_c`, `at` | PK `(target,uuid)` | Geber |
| `smp.order` | `id BIGSERIAL`, `uuid`, `items BYTEA`, `source VARCHAR(16)`, `created_at`, `picked_at` | IDX `(uuid) WHERE picked_at IS NULL` | `/abholen` |
| `smp.home` | wie `cb.home` | PK `(uuid, lower(name))` | Homes |
| `smp.wildcard` | `owner UUID`, `target UUID`, `at` | PK `(owner,target)` | `/wildcard` (Annahme) |
| `smp.season` | `id SERIAL`, `name`, `starts_at`, `ends_at` | PK `id` | Season-Ende 21 Uhr |

### 2.2 Valkey – Schlüssel und Kanäle

| Key / Kanal | Typ | TTL | Inhalt |
|---|---|---|---|
| `cb:presence:{uuid}` | HASH `server`, `world`, `role` | 60 s, alle 20 s erneuert | Builder-Regel, Cross-Server-Chat, `/plot online` |
| `cb:job:hour:{uuid}:{yyyyMMddHH}` | HASH `money_c`, `xp`, `side_money_c`, `side_xp` | 2 h | Stundenlimit |
| `cb:market:{uuid}:{yyyyMMdd}` | STRING sold_c | 48 h | Marktplatz-Tageslimit |
| `cb:exchange:{uuid}:{yyyyMMdd}` | STRING coins | 48 h | Wechselbörse |
| `cb:claim:cd:{uuid}` | STRING | 24 h | Claim-Abklingzeit |
| `cb:repair:cd:{uuid}` | STRING | 3600 s | `/repair` |
| `cb:shop:lock:{shopId}` | STRING | 30 s | Sperre nach Preisänderung |
| `cb:plot:lock:{plotId}` | STRING owner-op | 15 min | clear/move/delete exklusiv |
| `cb:trade:req:{to}:{from}` | STRING | 60 s | Handelsanfrage |
| `cb:tpa:req:{to}:{from}` | STRING `tpa`/`tpahere` | 60 s | TPA |
| `cb:space:invite:{to}` | STRING from | 60 s | `/weltall invite` |
| `cb:boosters` | HASH type→endsAtMs | keine (Einträge werden beim Ablauf gelöscht) | aktive globale Booster |
| `cb:farm:{n}` | HASH `online`, `max`, `nextReset` | 30 s | Portal-Hologramm |
| `cb:online:{serverId}` | STRING | 30 s | Auslastung |
| `cb:top:{kat}` | ZSET | 5 min Neuaufbau | `/top` Cache |
| ~~`cb:afk:{uuid}`~~ | – | – | entfällt: AFK-Status = `presence:{uuid}.afk` (Spec 01 §6.12, `PresenceService.setAfk`) |
| `net:cb-chat` (Kanal) | Pub/Sub JSON `{ch, from, level, prestige, text, target?, plotId?, guildId?}` | – | Global/Trade/Private/Plot/Gilde serverübergreifend |
| `net:cb-events` (Kanal) | Pub/Sub, Typen §8.3 | – | Zustandsänderungen |
| `smp:combat:{uuid}` | STRING | 15 s, bei Treffer erneuert | Kampf-Status |
| `smp:sell:{uuid}:{cat}` | STRING sold_c | 3600 s ab erstem Verkauf (Ablauf = Reset) | Verkaufslimit je Kategorie |
| `smp:tpa:req:{to}:{from}` | STRING | 60 s | TPA |
| `smp:queue:world` | LIST uuid | keine | Warteschlange Welt-Server |
| `net:smp-events` (Kanal) | Pub/Sub | – | Kopfgeld-Broadcasts, Bestellungen |

---

## 3. Befehle

Recht-Präfix `retromc.cb.` bzw. `retromc.smp.`; Rang-Stufen nach Spec 01 §6.1 (über `RankService`/`RankDisplayService`): „Premium“ = Gewicht ≥ 200 (Premium, Premium+), „Supremium“ = Gewicht ≥ 350 (Supremium, Supremium+, VIP), `team` = Teamränge (≥ 500). „¹“ = Rang, Crate-Recht (Rechte-Buch) oder Vorteilspaket nötig (belegt Wiki-Legende). Fehler allgemein: `cb.err.no_permission`, `cb.err.player_offline`, `cb.err.not_in_plotworld`, `cb.err.no_plot`, `cb.err.level` (`{P}§cDafür brauchst du Level §e{lvl}§c.`).

### 3.1 CityBuild allgemein

| Syntax | Aliase | Recht/Rang | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|
| `/cb` | `/citybuild`, `/hilfe` | alle | Hilfeseite (klickbare Befehlsliste, 2 Seiten) | `cb.help.*` |
| `/money [Spieler]` | `/geld` | alle | Kontostand | `cb.money.self` / `.other` |
| `/lvl [Spieler]` | `/level` | alle | Level, XP, Fortschritt %, Prestige | `cb.level.info` |
| `/prestige join` | – | Level 100, Prestige < 3 | Bestätigungs-GUI → Level 0, Prestige +1 | `cb.prestige.join`, `cb.prestige.not_ready`, `cb.prestige.max` |
| `/top <level\|job\|guild\|warp\|collection>` | – | alle | Top 10 + eigener Platz | `cb.top.header`, `cb.top.line`, `cb.top.self` |
| `/warp <Name>` | – | alle | Teleport (Server-/Spielerwarp, ggf. Serverwechsel) | `cb.warp.tp`, `cb.warp.unknown` |
| `/warp set <Name>` | – | alle; 1 Warp, ab Level 100: 2 | Setzt/verschiebt eigenen Warp (nur Plotwelt, eigenes Plot oder Config „Warps erlaubt“) | `cb.warp.set`, `cb.warp.limit`, `cb.warp.not_allowed` |
| `/warp delete <Name>` | – | Besitzer | löscht Warp (Annahme) | `cb.warp.deleted` |
| `/warps` | – | alle | GUI Serverwarps + Top-Warps (Annahme) | – |
| `/home [Name]` | – | alle | Teleport (Default-Name „Home“) | `cb.home.tp`, `cb.home.unknown` |
| `/home set [Name]` | `/sethome` | alle | Limit 3 / Premium 3 / Supremium 8, +1 je Rechte-Buch | `cb.home.set`, `cb.home.limit`, `cb.home.forbidden` (Weltall) |
| `/home delete <Name>` | `/delhome` | alle | Annahme | `cb.home.deleted` |
| `/homes` | – | alle | Liste (klickbar) | `cb.home.list` |
| `/tpa <Spieler>` · `/tpahere <Spieler>` | – | alle | Anfrage 60 s; Teleport nach 3 s Stillstand | `cb.tpa.*` |
| `/tpaccept [Spieler]` · `/tpdeny [Spieler]` | `/tpyes`, `/tpno` | alle | | |
| `/tpatoggle` | – | alle | Anfragen an/aus (OCR belegt) | `cb.tpa.toggle` |
| `/ec [1-12]` | `/enderchest` | freigeschaltete Truhe | öffnet Endertruhe n (Default 1) | `cb.ec.locked` |
| `/trade <Spieler>` | `/handel` | alle; Geld ab Level 8 | Anfrage 60 s, Handels-GUI §5.20 | `cb.trade.*` |
| `/tradetoggle` | – | alle | Anfragen an/aus | `cb.trade.toggle` |
| `/msg <Spieler> <Text>` · `/r <Text>` | `/tell`, `/w` | Level 5 oder Buddy | Kanal [Private] | `cb.chat.locked` |
| `/chat <global\|trade\|plot\|guild>` | – | alle | Standard-Schreibkanal wechseln / Trade-Empfang an/aus (`/chat trade`) | `cb.chat.switched` |
| `@t <Text>` · `@p <Text>` · `@g <Text>` | – | – | Einmal-Nachricht in Trade/Plot/Gilde (`@t` belegt, `@p`/`@g` Annahme) | |
| `/afk` | – | alle | abwesend an/aus über `PresenceService.setAfk` (Spec 01); auto nach 5 min (= Spec 01 §6.12) | `cb.afk.on/.off` |
| `/job [info\|menu\|quit <Job>\|stats\|statsall\|limit]` | `/jobs` | alle | §6.6 | `cb.job.*` |
| `/talisman [menu\|bag]` | `/ts` (menu), `/tb` (bag) | alle | G3/G4 | – |
| `/guild …` | `/gilde`, `/g` | §3.3 | | |
| `/backpack` | `/bp` | Gildenmitglied ab Rolle Mitglied | Gildenrucksack | `cb.guild.no_backpack` |
| `/collection [menu]` | – | alle | Mob-Collection-GUI | – |
| `/ci` | `/recipe`, `/rezepte`, `/recipes` | alle | Custom-Rezepte-GUI | – |
| `/quest` | `/quests` | alle | Quest-GUI (3 Tagesquests) | – |
| `/tutorial` | – | alle | aktueller Schritt erneut + Bossbar an/aus | `cb.tut.*` |
| `/adminshop` | – | alle | Adminshop-GUI (nur Lobby) | – |
| `/booster` | – | alle | Booster-Info-GUI | – |
| `/repair` | – | Supremium | Hand-Item reparieren, 1×/h | `cb.repair.ok`, `cb.repair.cooldown` |
| `/hat` · `/sit` | – | ¹ | Item auf Kopf / hinsetzen | |
| `/limits` | `/plot limits` | alle | Plot-/Merge-/Teilplot-Limits | `cb.plot.limits` |
| `/weltall [invite <Spieler>\|accept\|leave]` | `/space` | Zone ≥ 1 | Duo-Einladung, Verlassen | `cb.space.*` |
| `/toggle plot roles` | – | alle | Rollenvergabe an mich erlauben | `cb.toggle.plotroles` |
| `/shop list [Seite]` | – | alle | eigene Shops mit Füllstand (10/Seite, klickbar) | `cb.shop.list` |
| `/shop toggle <transactions\|chest>` | – | alle | Chat-Meldungen an/aus | `cb.shop.toggle` |
| `/rand [reset]` · `/wand [reset]` | `/plot edge`, `/plot wall` | ¹ (Owner/Co-Owner) | Blockauswahl-GUI | |
| `/hologram create <Text>` · `/hologram remove` | `/plot hologram …` | Premium + Owner/Co-Owner | §6.3.9 | `cb.holo.*` |
| `/buddy [Spieler]` | – | alle | Buddy-GUI (Annahme §10) | |

### 3.2 `/plot` (Alias `/p`, `/plots`)

Rollen: O = Owner, C = Co-Owner, A = Admin, B = Builder, S = Spieler (Matrix §6.3.4).

| Syntax | Recht | Verhalten | Ausgaben & Fehler |
|---|---|---|---|
| `tp [Alias/QuickID]` · `tp <Spieler> [Alias/QuickID]` | alle | Teleport zum eigenen/fremden Plot (QuickID `gx;gz@welt`, z. B. `3;-2@Cactus`) | `cb.plot.tp`, `cb.plot.not_found`, `cb.plot.private` |
| `list` · `listall` | alle | eigene Plots / Plots mit Rolle (klickbar) | `cb.plot.list.*` |
| `roles` | alle | listet Rollen und Kurzrechte | `cb.plot.roles` |
| `auto` | Plotwelt | kauft zufälligen freien Plot dieser Welt (nächster Ring zuerst) | `cb.plot.claimed`, `cb.plot.no_money`, `cb.plot.limit`, `cb.plot.cooldown`, `cb.plot.world_level` |
| `claim` | Plotwelt | kauft Plot unter dir | dito + `cb.plot.taken` |
| `info` | O C A B, S wenn öffentlich | Infoblock (Besitzer, ID, Alias, Rollen, Größe, Level, Löschdatum für O/C/A) | `cb.plot.info.*` |
| `setrole <Spieler> <Co_Owner\|Admin\|Builder>` | O; C bis Admin | Rolle vergeben (Ziel muss `/toggle plot roles` erlauben) | `cb.plot.role.set`, `.full`, `.refused` |
| `removerole <Spieler>` | O; C bis Admin | Rolle entfernen; Shops des Spielers deaktivieren | `cb.plot.role.removed` |
| `setalias <Alias>` · `removealias` | O C | | `cb.plot.alias.*` |
| `setprivacy <private\|public>` · `lock` | O C | privat = nur Mitglieder; Spawnplots nie privat | `cb.plot.privacy`, `cb.plot.spawnplot_denied` |
| `offer <Spieler\|Betrag>` | Käufer ab Level 35 → `offer <Betrag>` auf fremdem Plot; Verkauf: O ab Level 8 | Kaufangebot an Owner (5 min gültig, klickbar) | `cb.plot.offer.*` |
| `offer toggle` | O | Angebote an/aus | `cb.plot.offer.toggle` |
| `merge` | O | verbindet mit Plot in Blickrichtung (beide eigene), Reihen-Limit | `cb.plot.merge.*` |
| `mergelimits` | alle | Merge-Größe je Reihe dieser Welt | `cb.plot.mergelimits` |
| `row` | O C A | Reihe des Plots | `cb.plot.row` |
| `delete` | O | Bestätigung → archivieren, freigeben, zurücksetzen | `cb.plot.deleted` |
| `clear` | O | Bestätigung → Terrain zurücksetzen (Alias `reset`) | `cb.plot.cleared` |
| `restore` | O (Archiv ≤ 365 Tage, Annahme) | GUI mit archivierten eigenen Plots; Wiederherstellung nur wenn Fläche frei | `cb.plot.restore.*` |
| `kick <Spieler>` | O; C bis Co-Owner | wirft vom Plot (zum Weg) | `cb.plot.kick` |
| `ban <Spieler>` · `unban <Spieler>` · `bans` | O C | | `cb.plot.ban.*` |
| `setspawn` | O C | Spawn auf Plot | `cb.plot.spawn_set` |
| `sethome` | O | Home-Grundstück (Ziel von `/plot tp` ohne Argument) | `cb.plot.home_set` |
| `center` | O C | Teleport Mitte | – |
| `online` | alle auf Plot | Spieler auf dem Plot | `cb.plot.online` |
| `title` | O C | Willkommens-Title an/aus | `cb.plot.title.toggle` |
| `togglenotify` | O | Betreten-Meldung an/aus | `cb.plot.notify.toggle` |
| `deleteinfo` | O C A | Löschtermin | `cb.plot.deleteinfo` |
| `level` | alle | Plot-Level | `cb.plot.level` |
| `limits` · `entitylimits` | O C A (entity), alle (limits) | | `cb.plot.limits`, `cb.plot.entitylimits` |
| `buildall` | O | alle dürfen bauen + Kisten | `cb.plot.buildall` |
| `config` | O C | GUI §5.11 | – |
| `logbuch` | O C A | GUI letzte 9 Besucher | – |
| `chat` | O C | Plot-Chat an/aus (Kanal-Toggle) | `cb.chat.switched` |
| `setid` | O C | Plot-ID/QuickID kopieren (klickbar) (Annahme) | – |
| `move` · `move status` | O | Status | `cb.plot.move.status` |
| `move start` · `move confirm` · `move cancel` | O | §6.3.7 | `cb.plot.move.*` |
| `fly` ¹ | O C | Fliegen für alle auf dem Plot | `cb.plot.fly` |
| `edge` ¹ · `wall` ¹ · `floor` ¹ | O C | Block-GUI §5.13 (floor Annahme Supremium) | |
| `border` ¹ | O C A | Partikel-Umrandung 10 s | |
| `setweather <clear\|rain>` ¹ | O C | Plot-Wetter (client-seitig) | |
| `bossbar create <Nachricht>` ¹ · `bossbar remove` | O C | Plot-Bossbar für alle auf dem Plot | `cb.plot.bossbar.*` |
| `biome set <Biom>` ¹ · `setsubplot <Biom>` ¹ · `setxyz x1 y1 z1 x2 y2 z2 <Biom>` ¹ · `reset` · `resetsubplot` | O (C wenn vom Owner freigegeben) | Biom ganzes Merge / Teilplot unter dir / Quader; reset = `plains` | `cb.plot.biome.*` |
| `physics` ¹ | O | Blockphysik an/aus (Crate-Recht) | |
| `admin <lock\|unlock\|setowner <Spieler>\|delete\|restore <id>\|info>` | `team` | Adminsperre, Verlosungen, Moderation | `cb.plot.admin.*` |

### 3.3 `/guild` (Alias `/gilde`)

| Syntax | Recht (Gildenrolle) | Verhalten | Ausgaben & Fehler |
|---|---|---|---|
| `create <Name>` | ohne Gilde | 3–16 Zeichen `[A-Za-z0-9_]`, eindeutig | `cb.guild.created`, `.name_taken`, `.name_invalid`, `.already` |
| `rename <Name>` | Gründer, Leiter | | `cb.guild.renamed` |
| `invite <Spieler>` | ab Moderator | Einladung 5 min, klickbar | `cb.guild.invite.*`, `.full` |
| `accept <Gilde>` · `deny <Gilde>` | Eingeladener | | `cb.guild.joined`, `.denied` |
| `leave` | alle außer Gründer | | `cb.guild.left`, `.founder_cant_leave` |
| `kick <Spieler>` | Staffel §6.8.3 | | `cb.guild.kicked` |
| `promote <Spieler>` · `demote <Spieler>` | Staffel | nächsthöhere/-niedrigere Rolle | `cb.guild.promoted/.demoted` |
| `transfer <Spieler>` | Gründer | Bestätigung | `cb.guild.transferred` |
| `delete` | Gründer | Bestätigung; Kasse verfällt | `cb.guild.deleted` |
| `deposit <Betrag>` | alle Mitglieder | nicht rückzahlbar | `cb.guild.deposit` (Broadcast), `cb.guild.levelup` |
| `menu` | Mitglied | G2 | – |
| `info [Gilde]` | alle | Infoblock inkl. Online-Status (2025) | `cb.guild.info.*` |
| `roles` | Mitglied | Rollenübersicht (2025) | |
| `motd <Text>` | Gründer, Leiter | Willkommensnachricht | `cb.guild.motd_set` |
| `setbanner` · `removebanner` | Gründer, Leiter | Banner in der Hand | `cb.guild.banner.*` |
| `chat` | Mitglied | Kanal-Toggle | |
| `/top guild` | alle | Top 10 nach Einzahlung | `cb.guild.top.line` |

### 3.4 SMP

| Syntax | Aliase | Recht | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|
| `/smp` | – | alle | Befehlsübersicht | `smp.help.*` |
| `/shop` | – | alle | Server-Shop, Kategorien §5.27 | `smp.shop.*` |
| `/sell` | – | alle | Verkaufsinventar §5.28 | `smp.sell.*` |
| `/sellall` | – | alle | füllt Verkaufsinventar mit allen verkaufbaren Items | |
| `/sellinfo` | – | alle | Limits je Kategorie, Reset-Restzeit | `smp.sell.info` |
| `/smpplaytime [Spieler]` | `/playtimesmp` | alle | Spielzeit (belegt) | `smp.playtime` |
| `/bounty <Spieler> <Betrag>` · `/bounty info [Spieler]` · `/bounty` | `/kopfgeld` | alle | Kopfgeld aussetzen/ansehen; ohne Argument GUI §5.29 | `smp.bounty.*` |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpatoggle` | – | alle | wie CB, nicht im Kampf | `smp.tpa.*` |
| `/home`, `/sethome`, `/delhome`, `/homes` | – | alle; 3 / Supremium +5 (Annahme) | | |
| `/spawn` | – | alle | zur SMP-Lobby (nicht im Kampf) | |
| `/abholen` | – | alle | offene Bestellungen (Shop-Käufe/Store) ins Inventar | `smp.order.*` |
| `/wildcard <Spieler>` | – | alle | vergibt „Wildcard“ (Annahme: Spieler darf trotz voller Welt einreihen, Priorität in der Warteschlange) | `smp.wildcard.*` |
| `/trade <Spieler>` | – | alle | wie CB (belegt „hat dir eine Anfrage zum Handeln geschickt“) | |
| `/msg`, `/r` | – | alle | `{S}`-Privatformat | |

---

## 4. Anzeige-Elemente

### 4.1 Scoreboard CityBuild (FastBoard, Titel `§f§l<BRAND_UPPER>.NET`; belegt VS 2023–2026)

| Zeile | MODERN (2025/26, Standard, Score-Zahlen ausgeblendet) | LEGACY (2023/24, Score-Zahlen §c 13…0 sichtbar) | Wert/Quelle |
|---|---|---|---|
| 1 | `§fGeld§7:` | `§fGeld` | – |
| 2 | `§a{geld}$` | `§a{geld}$` | `money_c`, Format §6.1 (z. B. `3.372,01$`, `4.630.158,3$`) |
| 3 | ` ` | ` ` | |
| 4 | `§fLevel §7(/lvl)` | `§fLevel §7(/lvl):` | |
| 5 | `§e{level}` bzw. mit Prestige `§e{sterne} §f{level}` | dito | `✪` je Prestige-Liga (1–3) |
| 6 | ` ` | ` ` | |
| 7 | `§fGilde §7(/guild)` | `§fGilde §7(/guild):` | |
| 8 | `§b{gilde}` / `§b-` | dito | Gildenname |
| 9 | ` ` | ` ` | |
| 10 | `§fJob §7(/job)` | `§fJob §7(/job):` | |
| 11 | `§c{job}` / `§c-` | dito | Hauptjob |
| 12 | ` ` | ` ` | |
| 13 | `§fHilfe §e/cb` | `§fHilfe§7: §e/cb` | |

Takt: Geld/Level bei Event (`MoneyChangedEvent`, `LevelChangedEvent`), sonst 5 s. In allen CB-Server-Rollen gleich (auch Weltall, belegt).

### 4.2 Scoreboard SMP (belegt VS 2026)

| Zeile | Text | Wert |
|---|---|---|
| Titel | `§f§l<BRAND_UPPER>.NET` | |
| 1/2 | `§fGeld` / `§a{geld}$` | `smp.player.money_c` |
| 3 | ` ` | |
| 4/5 | `§fKills` / `§a{kills}` | |
| 6 | ` ` | |
| 7/8 | `§fTode` / `§c{tode}` | |
| 9 | ` ` | |
| 10/11 | `§fClan` / `§a{online}/{gesamt} §7[§e{TAG}§7]` bzw. `§7—` | Clan aus Social (Spec 01) |
| 12 | ` ` | |
| 13 | `§fHilfe §e/smp` | |

### 4.3 Tablist (4 Spalten à 20 = 80 Plätze, Sortierung/Einträge aus Spec 01 §Tab; belegt)

| Teil | CityBuild | SMP |
|---|---|---|
| Header 1 | `§e<brand> CityBuild` | `§f<brand> §dSMP` |
| Header 2 | `§7Du willst einen Spieler melden? §c/report §f<Spieler>` | gleich |
| Footer 1 | `§fDu bist auf Server §e{serverName}` (`Farming 2`, `Lobby`, `Plotwelt Cactus`, `Weltall`) | `§7Du bist auf Server §e{serverName}` (`World 2`, `Lobby`) |
| Footer 2 | ` ` | `§7Nutze §e/smp§7, um die wichtigsten Befehle zu sehen` |
| Footer 3 (keine Booster) | `§fGerade sind §ckeine §fBooster aktiv.` | – |
| Footer 4 | `§fBooster gibt es im §6Gold-Shop§f.` | – |
| Footer 5 | `§fMehr Infos mit §b/booster` | – |
| Footer 3+ (Booster aktiv) | `§fAktive Booster:` + je Booster `§e{typ} §7({mm}m {ss}s)` kommagetrennt, max. 2 Zeilen | – |

Übertragung: `TabService.setServerFooter(lines)` (Spec 02 §8.1, paper-core rendert Header/Footer selbst), Footer-Refresh 1 s solange Booster aktiv, sonst bei Änderung.
Nametag/Tab-Eintrag: `[{prefixColor}{rank} §7| ]{nameColor}{name}` (Spec 01); CB: Prestige-Sterne vor dem Namen `§e{✪…} ` (belegt Wiki). SMP: Clan-Tag ` §7[§e{TAG}§7]` (belegt).

### 4.4 Bossbars

| Kanal | Text | Farbe/Stil | Fortschritt | Ära/Beleg |
|---|---|---|---|---|
| `tutorial` | `§6Tutorial §7({n}/34): §f{schritt} §7/tutorial` | GREEN, SOLID | n/34 | MODERN belegt (15/34) |
| `tutorial` LEGACY | `§6Quest {n}: §f{schritt} §7/quest` | GREEN, SOLID | n/34 | LEGACY belegt („Quest 9: Erreiche Level 4 /quest“) |
| `boss` | `§e{bossName} §c❤ §f{hp}/{max}` | RED, SOLID | hp/max | belegt („73/1000“) |
| `plot` | `/plot bossbar`-Text (Farbcodes erlaubt) | WHITE, SOLID | 1,0 | Annahme Farbe |
| `combat` (SMP) | `§cKAMPF-LOGGING: §fDu bist im Kampf – nicht ausloggen!` | RED, SOLID | Restzeit/15 s | belegt (Struktur), Text eigen |
| `event` | Event-Countdown (End-Event) | PURPLE | Rest | Annahme |

Priorität: `boss` > `combat` > `event` > `plot` > `tutorial` (max. 2 gleichzeitig: `tutorial` immer zusätzlich, bis abgeschlossen).

### 4.5 Actionbar

| Kanal | Text | Wann | Beleg |
|---|---|---|---|
| `news` | `§bNeu: §b{titel}` (z. B. `Neu: Winter-Crate`) | dauerhaft rotierend in CB-Lobby, 8 s je Eintrag | belegt |
| `space` | `§7O2 §a{20×|}§7{rest|} §8| §fTreibstoff §b{20×|}§7{rest|}` | Weltall, 4 Ticks | belegt |
| `job` | `{P}§7Du hast ein §adoppeltes Item §7erhalten` | Doppel-Drop | belegt |
| `job.xp` | `§a+{xp} CityBuild-XP §8| §e+{geld}$` | jede Job-Belohnung, wenn Chat-XP-Meldung aus (Annahme) | Annahme |
| `combat` | `§cIm Kampf: §f{s}s` | SMP | Annahme |
| `shop` | `§c[Shop] §7gesperrt: §e{s}s` | Preisänderung | Annahme |

### 4.6 Titles

| Anlass | Title | Subtitle | Zeiten (in/stay/out Ticks) | Beleg |
|---|---|---|---|---|
| Join CityBuild | `§eWillkommen, {rankColor}{name}§e!` | `§fViel Freude in unserer Stadt!` | 10/60/20 | Struktur belegt |
| Plot betreten (title an) | `§e{alias oder Besitzer}` | `§7Grundstück von §f{owner}` | 5/40/10 | abgeleitet („Grundstück von …“) |
| Level-Up | `§6Level {lvl}` | `§7{freischaltung}` oder leer | 10/50/20 | Annahme |
| Prestige | `§e{✪…} §6Prestige {n}` | `§7Dein Level startet neu` | 10/70/20 | Annahme |
| Weltall Absturz | `§cAbgestürzt!` | `§7Zu nah an der Erde` | 5/40/10 | Annahme |
| Farmwelt-Reset-Kick | `§cFarmwelt wird zurückgesetzt` | `§7Du wurdest in die Lobby gebracht` | 10/60/20 | Annahme |

### 4.7 Hologramme (TextDisplay, `HologramService`)

| Ort | Zeilen | Beleg |
|---|---|---|
| NPC-Kopf (alle Lobby-NPCs) | `§{farbe}§l{NAME}` / `§7(Rechtsklick)` | belegt („MEINE GILDE“, „STADTWACHE“, „TALISMANE (Rechtsklick)“) |
| Farmwelt-Portal | `§6Farmwelt 1-9` / `§bReset in {n} {Tag/Tagen/Stunden}` / `§7{online}/{max} Spieler` | belegt |
| End-Event-Portal | `§dEnd-Event` / `§6Farmwelt {x}` / `§eGesamt: {n} Stunden` | belegt (Struktur) |
| Crate-Ort | `§b{CrateName}` / `§7(Rechtsklick)` | abgeleitet |
| Hall of Fame | Top 50 Level / Top 50 Job-XP (Season) je `§e#{n} §f{name} §7- §a{wert}`; Top 5 als Statue (NPC mit Skin); Gilden-Top 5 mit Banner; Turm Top 9 Collection | belegt (Inhalt), Format Annahme |
| Plot-Hologramm | Freitext mit §-Codes | belegt |
| Shop | über Schild optional `§a{item}` (Annahme) | – |
| SMP-Spawn | `§5§l<brand> SMP` / `§fWillkommen auf SMP, {rankColor}{name}` / `§fNutze §e/smp§f, um die wichtigsten Befehle zu sehen` / (Beta) `§cDer gesamte Fortschritt wird nach der Beta zurückgesetzt` | belegt (Struktur, persönlich) |

### 4.8 NPCs CityBuild-Lobby (Skin/Name eigen; Original-Referenz)

| NPC (Nachbau) | Holo-Farbe | Funktion | Original |
|---|---|---|---|
| `JOBS` Arbeitsamt | §e | G1 | Jobs |
| `MEINE GILDE` Gildenwächter | §a | G2 bzw. Info ohne Gilde | Gildenwächter |
| `TALISMANE` | §d | G3 | Talismane |
| `GOLD-SHOP` | §6 | §5.16 | MARKENSHOP / Gold-Shop |
| `MOB-COLLECTION` | §2 | §5.14 | Mob-Collection |
| `REZEPTE` | §b | §5.15 | Custom Craftingrezepte |
| `STADTWACHE` | §c | Chattext zu `/report` und Bugmeldung | Stadtwache |
| `TRESOR` | §6 | §5.17 Endertruhen | Tresorwächter |
| `MÜLLEIMER` („Bruno“) | §7 | öffentliche Kiste 54 Slots, jeder kann entnehmen, Leerung täglich 04:00 (Annahme) | Hugo |
| `SCHMIED` („Bjarne“) | §8 | Armortrims + Custom-Rüstung reparieren §5.19 | Thor |
| `BELOHNUNGEN` | §a | Belohnungstruhen §5.18 | Belohnungen |
| `MALL` | §d | §5.9 | Mall |
| `WECHSELBÖRSE` | §e | §5.10 | Wechselbörse |
| `QUESTS` | §6 | §5.21 | Quest-NPC |
| `WELTALL` | §b | §5.25 | Weltall-NPC |
| `BUDDY` | §a | §5.26 | Buddy |
| `RAUMHÄNDLER` („Cassius“) | §b | persönlicher Weltall-Shop (Annahme §10) | Clementius |

### 4.9 Hotbar

CityBuild und SMP vergeben **keine** festen Hotbar-Items (freies Survival-Inventar, belegt VS). Ausnahme: Spawnelytra beim Farmwelt-Spawn (Brustplatz, unsichtbar, §6.5).

---

## 5. GUIs

Konventionen (belegt Wiki-Hausstil, N09 §2.1): Rahmen `gray_stained_glass_pane` (Name ` `) oben, unten (wenn vorhanden); Zurück = `red_stained_glass_pane` `§cZurück` in Slot 8; Aktions-Fußzeile `§a<Klicke zum …>`; Geld `§a{n}§2$`; Rang-Hinweis `§7Hinweis:` + `§7Unsere §6Premium§7-Ränge …`. Lore-Zeilen ohne eigene Farbe bekommen die geerbte Farbe explizit (N09 §4.2). Custom-Köpfe (`question_skull`, `money_skull`, `bundle_skull`, `plus_skull`, `talisman_overview`) = `player_head` mit eigener Textur (eigene Grafik). Leerplätze `.` = Luft.

### 5.1 G1 Jobs (`/job menu`, NPC JOBS) – „Jobs“, 4 Reihen (belegt)

Raster: Z0 `# # # # A # # # #` · Z1 `B C D G . H I J K` · Z2 `#########` · Z3 `L . . . . . . . .`

| Slot | Item | Name | Lore | Klick |
|---|---|---|---|---|
| 4 | `wheat` (Icon des JdM) | `§eJob des Monats` | `§7Diesen Monat gekrönt: §c{job}` · `§7` · `§7Vorteile:` · `§b+ §735 % mehr XP` · `§b+ §720 % weniger Aufwand` | – |
| 9 | `cornflower` | `§cSammler §7({status})` | `§7Jobbeschreibung:` · `§7Du ziehst durch die Wildnis und` · `§7pflückst Blumen aller Farben` · ` ` · `§7Spieler mit diesem Job: §b{n}` | Links: Job wählen |
| 10 | `iron_shovel` | `§cGräber §7({status})` | `§7Jobbeschreibung:` · `§7Erde, Sand, Kies: du gräbst dich` · `§7durch den Boden – ideal zum Start` · ` ` · `§7Spieler mit diesem Job: §b{n}` | dito |
| 11 | `stone_axe` | `§cHolzfäller §7({status})` | `§7Jobbeschreibung:` · `§7Du fällst Bäume und sorgst für` · `§7Nachschub an Holz` · ` ` · `§7Spieler …` | dito |
| 12 | `spyglass` | `§cAbenteurer §7({status})` | `§7Jobbeschreibung:` · `§7Du spürst gefährliche Monster auf` · `§7und besiegst sie` · ` ` · `§7Spieler …` | dito |
| 13 | – | – | – | – |
| 14 | `fishing_rod` | `§cAngler §7({status})` | `§7Jobbeschreibung:` · `§7Am Wasser holst du Fische und mit` · `§7Glück auch Schätze heraus` · ` ` · `§7Spieler …` | dito |
| 15 | `netherite_pickaxe` | `§cBergarbeiter §7({status})` | `§7Jobbeschreibung:` · `§7Tief unter Tage baust du Erze ab` · ` ` · `§7Spieler …` | dito |
| 16 | `porkchop` | `§cJäger §7({status})` | `§7Jobbeschreibung:` · `§7Du jagst friedliche Tiere in der` · `§7freien Natur` · ` ` · `§7Spieler …` | dito |
| 17 | `wheat` | `§cFarmer §7({status})` | `§7Jobbeschreibung:` · `§7Du säst, pflegst und erntest` · `§7deine Felder` · ` ` · `§7Spieler …` | dito |
| 27 | `question_skull` | `§aAnleitung lesen` | `§7Klicke, um die Anleitung erneut anzuzeigen` | Buch-GUI (Annahme: geschriebenes Buch, 4 Seiten) |

`{status}`: `freigeschaltet` · `ab Level {n}` (gesperrt, Item bleibt, Name `§8` statt `§c`, Annahme) · `aktiv` (aktueller Job, Glint, Annahme) · `Nebenjob`. Klick auf gesperrt: `cb.job.locked`. Rechtsklick (Supremium): als Nebenjob wählen (Annahme). Spielerzahl aus `cb.player` (Cache 60 s).

### 5.2 G2 Gildenmenü (`/guild menu`) – `{Gildenname} (Stufe {lvl})`, 5 Reihen (belegt)

Z0 `#########` · Z1 leer · Z2 `. . A . B . C . .` · Z3 leer · Z4 `#########`

| Slot | Item | Name | Lore | Klick |
|---|---|---|---|---|
| 20 | `oak_sign` | `§bGildeninfos` | `§7Name: §e{name}` · `§7Stufe: §e{lvl}` · `§7Mitglieder: §e{n}/{max}` · `§7Gegründet am: §e{TT.MM.JJJJ}` | – |
| 22 | `book` | `§6Gildenvorteile §7(Linksklick)` | `§7` · `§eDeine freigeschalteten Vorteile:` · `§7Zusätzliche Mitgliederplätze: §6{n}` · `§7Größe Gildenrucksack: §6{r}x9` · `§7Mehr Geld aus Jobs: §6{p}%` · `§7Schatzchance: §6{c}` · `§7Passive Erfahrung: §6{xp}` · `§7` · `§7Klicke für alle Gildenvorteile` | Liste §5.2a |
| 24 | `money_skull` | `§aIn die Gilde einzahlen §7(Linksklick)` | `§7` · `§7` · `§eGildenkasse` · `§7Bisher eingezahlt: §a{sum}§2$` · `§7Stufe: §a{lvl}` · `§7Noch nötig bis zur nächsten Stufe §a{rest}§2$` · `§7` · `§aKlicke, um Geld in die Kasse zu legen` | Chat-Eingabe 30 s („abbrechen“), dann Bestätigung |

§5.2a Gildenvorteile-Liste (Annahme Layout): `PagedGui`, 6 Reihen, Titel `Gildenvorteile`, je Stufe aus §6.8.4 ein Item (freigeschaltet `lime_dye`, offen `gray_dye`), Name `§e{Stufe}`, Lore `§7{Vorteil}` · `§7Benötigt: §a{kumuliert}§2$`; Slot 49 `§cZurück`.

### 5.3 G3 Talisman-Shop (`/talisman menu`, `/ts`) – „Talisman Shop“, 5 Reihen (belegt)

Z0 `# # # # A # # # #` · Z1 `. . B . B . B . .` · Z2 leer · Z3 `. . . C . D . . .` · Z4 `# # # # G # # # #`

| Slot | Item | Name | Lore | Klick |
|---|---|---|---|---|
| 4 | `talisman_overview` | `§eAlle Talismane` | `§7Klicke für eine Übersicht aller` · `§7verfügbaren Talismane` | §5.4 |
| 11/13/15 | `anvil` (freier Slot), `clock` (läuft, Annahme), Talisman-Item (fertig) | frei: `§dTalisman herstellen` | frei: `§7Klicke, um einen Talisman herzustellen` · ` ` · `§7Kosten: §a{kosten}§2$` · `§7Dauer: §e6-24 Stunden` · ` ` · `§7Hinweis:` · `§7Unsere §6Premium§7-Ränge senken die` · `§7Herstellungskosten und die` · `§7Herstellungsdauer` · ` ` · `§a<Klicke zum Herstellen>`; läuft: `§dIn Herstellung` · `§7Fertig in: §e{hh:mm}` · `§7Verkürzen: §a{x}§2$ §7für §e4 Stunden` · `§a<Klicke zum Verkürzen>`; fertig: Talisman-Lore + `§a<Klicke zum Einsammeln>`; gesperrt (Rang fehlt): `barrier` `§cNoch nicht freigeschaltet` · `§7Benötigt §6{Rang}` | Start (Bestätigung) / Verkürzen / Einsammeln |
| 30 | `bundle_skull` | `§6Talisman-Beutel §7(§afreigeschaltet§7)` bzw. `§7(§cgesperrt§7)` | `§7Dein Beutel ist bereits freigeschaltet` · ` ` · `§7Öffnen auch mit /talisman bag` · ` ` · `§a<Klicke zum Öffnen>` | G4 |
| 32 | `bundle_skull` | `§6Talisman-Beutel vergrößern` (vor Freischaltung `§6Talisman-Beutel freischalten`, kostenlos) | `§7Schalte 3 weitere Plätze für` · `§7deinen Beutel frei` · ` ` · `§7Aktuelle Plätze: §e{n}` · ` ` · `§7Benötigt:` · `§7- §a{preis}§2$` · `§7- {f}x §dBeutel-Fragment` · ` ` · `§a<Klicke zum Upgraden>`; bei 36: `§7Maximale Größe erreicht` | Upgrade (Bestätigung) |
| 40 | `lodestone` | `§dTalismane kombinieren` | `§7Kombiniere §e2 Talismane §7gleicher` · `§eSeltenheit§7 für die Chance auf einen` · `§7Talisman mit §ehöherer Seltenheit` | G5 |

### 5.4 Talisman-Übersicht – „Alle Talismane“, 6 Reihen (Annahme Layout)
Z0 Panes + Slot 8 `§cZurück`; Slots 10–16, 19–25, 28–34, 37–43 (28 Plätze) = 27 Talismane (§6.10.1) in Seltenheitsfarbe; Lore `§7{Effekt je Seltenheit}` je Zeile `§{farbe}{Seltenheit}: §7{wert}`; `§7Nicht in jeder Seltenheit erhältlich` falls zutreffend.

### 5.5 G4 Talisman-Beutel (`/tb`) – „Deine Talismane“, 2 + k Reihen (belegt für k=1)
Z0 Panes, Slot 8 `§cZurück` (→ G3); Inhalt k = `max(1, ceil(slots/9))` Reihen; freigeschaltete Plätze leer/Talisman (nur Talisman-Items einlegbar, sonst `cb.talisman.only_talismans`); restliche Plätze der Inhaltsreihen `barrier` `§cNoch nicht freigeschaltet` · `§7Diesen Platz hast du noch` · `§7nicht freigeschaltet`; letzte Reihe Panes. Wirkung sofort beim Einlegen/Entnehmen.

### 5.6 G5 Talismane kombinieren – „Talismane kombinieren“ (Original zeigt Kopie „Talisman Shop“), 3 Reihen (belegt)
Z0 Panes + Slot 8 `§cZurück`; Slot 11 und 15 Eingabe (`barrier` `§eSlot #1`/`§eSlot #2` · `§7Lege hier einen Talisman ab`); Slot 13 `plus_skull` `§dTalisman kombinieren` · `§7Kombiniere zwei Talismane gleicher` · `§7Seltenheit für die Chance auf ein` · `§7Upgrade. Zwei Talismane derselben` · `§7Art erhöhen die Chance` · ` ` · (leer) `§cLege erst zwei Talismane ein, um` · `§cdie Kosten zu sehen` / (befüllt) `§7Kosten: §a{k}§2$` · `§7Chance: §e{p} %` · ` ` · `§7Hinweis:` · `§7Unsere §6Premium§7-Ränge` · `§7senken die Kosten` · ` ` · `§a<Klicke zum Kombinieren>`; Z2 Panes. Schließen gibt eingelegte Talismane zurück.

### 5.7 Plotwelten-Auswahl (`/warp Plotwelten`, Portal) – „Plotwelten“, 6 Reihen (Annahme Layout; belegt: Graspfad für volle Welten, Filter unten rechts, eigene Plots seit 2025)

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–8, 45–52 | Panes | | |
| 4 | `compass` | `§ePlotwelten` · `§7Wähle eine Welt aus` | – |
| 10–16, 19–20 | Welt-Icon (Stufe 1) bzw. `dirt_path` wenn voll | `§a{Welt}` · `§7Stufe: §e1` · `§7Größe: §e35x35` · `§7Freie Plots: §e{n}` · `§7Deine Plots hier: §e{m}` · ` ` · `§a<Klicke zum Teleportieren>`; voll: `§c{Welt} §7(voll)` | Teleport (Serverwechsel) |
| 22 | Icon Stufe 2 | `§6{Welt}` · `§7Stufe: §e2` · `§7Größe: §e39x39` · `§7Aufpreis: §a10.000§2$` · `§7Ab Level §e7` | dito / `cb.err.level` |
| 24 | Icon Stufe 3 | `§b{Welt}` · `§7Stufe: §e3` · `§7Größe: §e45x45` · `§7Aufpreis: §a90.000§2$` · `§7Ab Level §e22` | dito |
| 40 | `oak_door` | `§eMeine Grundstücke` · `§7{n} Plots in {w} Welten` | Liste (PagedGui, Klick = tp) |
| 53 | `hopper` | `§eFilter: §a{alle\|nur freie}` | volle Welten aus-/einblenden |

### 5.8 Adminshop (`/adminshop`, `/warp Adminshop`) – „Adminshop“, 6 Reihen (Annahme Layout; belegt: Wochenrotation Do 18:00, Bestand)
Z0/Z5 Panes, Slot 4 `clock` `§eNeues Angebot in` · `§e{d}T {h}h`; Slots 10–16, 19–25, 28–34 = Angebote. Item-Lore (Struktur aus Shop-GUI belegt): `§eKaufe das Item für §a{preis}$` · `§7Noch §e{n} §7Items verfügbar` · (Questpunkte) `§7Questrabatt: §a-5 %`. Links = 1, Shift-Links = Stack (64 bzw. maxStack) mit Bestätigung ab 10.000$. Ausverkauft: Item bleibt, Lore `§cAusverkauft`.

### 5.9 Mall (`/warp Mall`) – „Mall“, 6 Reihen (Annahme Layout)
Wie 5.8; drei Bereiche: Z1 Köpfe (`player_head`), Z2 Gemälde, Z3 Bilder (Kartenbilder). Lore `§7Preis: §a{geld}$ §7oder §e1x {Ticket}` · `§aLinksklick: §7mit Geld` · `§eRechtsklick: §7mit Ticket`. Mall-Booster: Preis durchgestrichen `§7§m{alt}$` + `§a{neu}$`.

### 5.10 Wechselbörse (NPC) – „Wechselbörse“, 3 Reihen (Annahme Layout)
Z0/Z2 Panes; Slot 11 `sunflower` `§e1.000 Coins` → `§a100$`; Slot 13 `sunflower`×5 `§e5.000 Coins` → `§a500$`; Slot 15 `gold_block` `§e10.000 Coins` → `§a1.000$`; Lore `§7Heute noch tauschbar: §e{rest} Coins`. Klick bucht über `CoinService.debit` (§8).

### 5.11 Plot-Config (`/plot config`) – „Plot-Einstellungen“, 4 Reihen (Annahme Layout; belegt: Beispiele Werkbank, Zaubertisch, Items aufheben, Warps)
Z0 Panes; Schalter je Slot (Item + Name `§e{Einstellung}` + `§7Status: §a{an}`/`§c{aus}` + `§a<Klicke zum Umschalten>`), Rang-Schalter mit `§7Benötigt §6{Rang}`:

| Slot | Item | Einstellung | Default | Rang |
|---|---|---|---|---|
| 10 | `crafting_table` | Alle dürfen Werkbänke nutzen | aus | – |
| 11 | `enchanting_table` | Alle dürfen Zaubertische nutzen | aus | – |
| 12 | `anvil` | Alle dürfen Ambosse nutzen | aus | – |
| 13 | `hopper` | Niemand darf Items aufheben | aus | – |
| 14 | `oak_door` | Alle dürfen Türen/Tore nutzen | an | – |
| 15 | `stone_button` | Alle dürfen Knöpfe/Hebel nutzen | an | – |
| 16 | `ender_pearl` | Alle dürfen hier Warps setzen | aus | – |
| 19 | `zombie_head` | Monster spawnen | aus | – |
| 20 | `wheat` | Tiere spawnen | an | – |
| 21 | `ice` | Eis/Schnee schmelzen | an | Premium |
| 22 | `vine` | Pflanzenwachstum (Ranken) | an | Premium |
| 23 | `iron_sword` | PvP auf dem Plot | aus | Supremium |
| 24 | `elytra` | Elytra-Flug erlaubt | an | – |
| 25 | `chest` | Alle dürfen Kisten öffnen (ohne buildall) | aus | – |
| 31 | `red_stained_glass_pane` | `§cZurück` (schließen) | | |

Owner/Co-Owner sind immun (belegt Matrix).

### 5.12 Logbuch (`/plot logbuch`) – „Logbuch“, 3 Reihen (Annahme Layout; belegt: letzte 9)
Z0 Panes; Slots 9–17 je `player_head` `§e{name}` · `§7Besuch: §f{TT.MM.JJJJ HH:mm}`; Z2 Panes.

### 5.13 Rand/Wand/Boden-Auswahl (`/rand`, `/wand`, `/plot floor`) – „Rand wählen“/„Wand wählen“/„Boden wählen“, 6 Reihen, `PagedGui` (Annahme)
Inhalt: Liste aus `plot-blocks.yml` (Default 45 Blöcke je Liste: Stufen/Mauern/Platten bzw. Vollblöcke), Name `§e{Block}` · `§a<Klicke zum Setzen>`; Slot 49 `barrier` `§cZurücksetzen`; 45/53 Blättern.

### 5.14 Mob-Collection (`/collection`) – „Mob-Collection“, 6 Reihen (Annahme Layout; belegt: 8 Kategorien, Kopf-Tooltip „Dropchance: 5%“, „Nicht freigeschaltet“)
Übersicht: Slots 10,12,14,16,28,30,32,34 = 8 Kategorien (`player_head`), Lore `§7Gesammelt: §e{n}/{m}`. Kategorie-Seite: Köpfe; gesammelt = Kopf `§e{Mob}` · `§7Dropchance: §e{p}%`; offen = `skeleton_skull` grau `§7???` · `§7Dropchance: §e{p}%` · `§cNicht freigeschaltet`. Slot 49 `§cZurück`.

### 5.15 Custom-Rezepte (`/ci`) – „Custom-Rezepte“, 6 Reihen, `PagedGui`
Liste aller Custom-Items (§6.13); Klick → Rezept-Ansicht (6 Reihen: 3×3 Raster Slots 10–12/19–21/28–30, Pfeil `arrow` 23, Ergebnis 25, Slot 49 Zurück) (Annahme).

### 5.16 Gold-Shop (NPC GOLD-SHOP) – „Gold-Shop“, 5 Reihen (Annahme Layout; belegt: Gold, Crates, Ränge, Universal-Booster)
Slot 4 `gold_ingot` `§6Dein Gold: §e{n} Gold`; Slots 19/20/21/22 Crate-Pakete `§b{n} Crates` · `§7Preis: §6{g} Gold` (2/300, 4/500, 10/950, 21/1.800); Slot 24 `nether_star` `§dUniversal-Booster` · `§7Preis: §6{g} Gold`; Slot 25 `name_tag` `§6Ränge` → Webshop-Link (Chat, klickbar); Slot 40 `book` `§eGold aufladen` → Webshop-Link.

### 5.17 Tresor (NPC TRESOR) – „Tresor“, 3 Reihen (Annahme Layout)
Slots 9–17 + 18–20 = Endertruhen 1–12 (`ender_chest`): `§aEndertruhe {i}` (besessen, Klick öffnet) · `§eEndertruhe {i}` + `§7Ab Level §e{l}` + `§7Preis: §a{p}§2$` + `§a<Klicke zum Kaufen>` · `§cEndertruhe {i}` + `§7Nur mit Rechte-Buch freischaltbar`.

### 5.18 Belohnungstruhen (`/warp Chests`, NPC) – „Belohnungen“, 3 Reihen (Annahme Layout)
Slot 11 `chest` `§aTägliche Belohnung` · Status `§aBereit`/`§7Wieder in §e{hh:mm}`; Slot 13 `ender_chest` `§eWöchentliche Belohnung` (So 00:00); Slot 15 `shulker_box` `§6Monatliche Belohnung` (`§7Nur für §6Premium§7-Ränge`). Klick: Zufallsbelohnung §6.15.

### 5.19 Schmied – „Schmiede“, 3 Reihen (Annahme)
Slot 10 Rüstungsteil, Slot 12 Schmiedevorlage, Slot 14 Material, Slot 16 Ergebnis-Vorschau (`§aAnwenden` · Kosten `§a5.000§2$`); Slot 22 `anvil` `§eCustom-Rüstung reparieren` (`§a{k}§2$` = 10 % Itemwert, Annahme).

### 5.20 Handel (`/trade`) – „Handel mit {name}“, 6 Reihen (Annahme Layout; belegt: beidseitige Bestätigung, Geld ab L8)
Spalten 0–3 (Slots 0–3, 9–12, 18–21, 27–30) eigenes Angebot; Spalte 4 `black_stained_glass_pane`; Spalten 5–8 Partner (nur Ansicht). Z4: Slot 37 `sunflower` `§eGeld anbieten: §a{x}$` (Links +1.000/Rechts −1.000/Shift ×10/Mitte Chat-Eingabe; < L8: `barrier` `§cGeld ab Level 8`); Slot 43 Partner-Geld. Z5: Slot 47 `red_dye` → `lime_dye` `§aBestätigen`/`§aBestätigt`; Slot 51 Partner-Status. Jede Änderung setzt beide Bestätigungen zurück; nach beiden: 3-s-Countdown im Titel (Annahme), dann atomarer Tausch. Schließen = Abbruch.

### 5.21 Quests (`/quest`, NPC QUESTS) – „Tagesquests“, 3 Reihen (Annahme Layout)
Slots 11/13/15 je Quest (`paper`, erledigt `map`), Name `§e{Quest}`, Lore `§7Fortschritt: §e{p}/{g}` · `§7Belohnung: §a{geld}$§7, §e{xp} XP§7, §61 Questpunkt` · Status. Slot 22 `nether_star` `§6Meilensteine` → Liste (12 Einträge, Fortschrittsbalken in Lore).

### 5.22 Booster (`/booster`) – „Booster“, 3 Reihen (Annahme Layout)
Slots 10–16 je Typ (§6.14): aktiv `§a{Typ}` · `§7Läuft noch §e{mm:ss}` · `§7Aktiviert von §f{name}`; inaktiv `§7{Typ}` · `§7Nicht aktiv`; Slot 22 `nether_star` `§dEigene Booster aktivieren` (aus Inventar-Booster-Items).

### 5.23 Crates (`/warp Crates`, Rechtsklick Crate) – „{Crate}“, 6 Reihen (belegt: Vorschau vor Öffnen)
Vorschau: alle möglichen Inhalte mit `§7Chance: §e{p} %`; Slot 49 `tripwire_hook` `§aCrate öffnen §7({n} übrig)`. Öffnen: 5 Reihen „Dein Gewinn“: Z0/Z4 Mitte `nether_star` (Zeiger, Name `§5Gewinn`), Z1/Z3 Glasmuster, Z2 Laufband 4 s mit Abbremsen (Struktur belegt aus Netzwerk-Kisten-GUI, Annahme für CB).

### 5.24 Spielershop – Kauf („Shop“, 3 Reihen, belegt VS 2025) und Erstellung
Kauf: Z0/Z2 `light_blue_stained_glass_pane`, Slot 13 = Shop-Item mit Original-Name, Lore `§eKaufe das Item für §a{preis}$` · `§eVerkaufe das Item für §a{preis}$` (falls Ankauf) · `§7Noch §e{n} §7Items verfügbar`. Links = kaufen 1, Shift-Links = Stack, Rechts = verkaufen 1, Shift-Rechts = Stack.
Erstellung (Schild `[Shop]`) „Shop erstellen“, 3 Reihen (Annahme): Slot 10 `item_frame` `§eItem wählen` (Klick mit Item am Cursor), Slot 12 `emerald` `§aVerkaufspreis: §f{p}$` (Chat-Eingabe), Slot 14 `gold_ingot` `§eAnkaufspreis: §f{p}$`, Slot 16 `hopper` `§6Shoplimit: §f{l}$`, Slot 22 `lime_dye` `§aShop speichern`.

### 5.25 Weltall (NPC WELTALL) – „Weltall-Mission“, 3 Reihen (Annahme Layout)
Slots 11/13/15 = Zonen (`moon`-Kopf / `orange_glazed_terracotta` / `black_concrete`): `§b{Zone}` · Status `§afreigeschaltet` / `§cGesperrt` + `§7Benötigt:` Zeilen (§6.12) · `§a<Klicke zum Starten>`. Slot 22 `player_head` `§eMitspieler einladen` (`/weltall invite`).

### 5.26 Buddy (NPC BUDDY) – „Buddy“, 3 Reihen (Annahme §10)
Slot 13 `player_head` `§aDein Buddy: §f{name}`/`§7Kein Buddy` · `§7Buddys schreiben sich privat`, `§7auch unter Level 5`.

### 5.27 SMP-Shop (`/shop`) – „Shop“, 6 Reihen (belegt: Kategorien End, Nether, Equipment, Essen …)
Übersicht: Z0/Z5 Panes, Kategorien Slots 20 `end_stone` `§dEnd`, 21 `netherrack` `§cNether`, 22 `diamond_chestplate` `§bEquipment`, 23 `cooked_beef` `§6Essen`, 24 `bricks` `§eBlöcke`, 29 `redstone` `§cRedstone`, 30 `oak_sapling` `§aNatur`, 31 `experience_bottle` `§eSonstiges` (Rest Annahme). Kategorie: Items `§eKaufe das Item für §a{preis}$` · `§aLinks: 1 §7| §aShift: 64`; Lieferung direkt ins Inventar, bei vollem Inventar `smp.order` (`/abholen`).

### 5.28 SMP-Verkauf (`/sell`) – „Verkaufen“, 6 Reihen (belegt: Gold Ingot zeigt Summe und Limit)
Slots 0–44 Ablage; Z5: Slot 49 `gold_ingot` `§eSumme: §a{summe}$` · `§7Gebühr: §c-{f}$ §7({p} %)` · `§7Auszahlung: §a{netto}$` · je Kategorie `§7{Kat}: §e{genutzt}/{limit}$` · `§a<Klicke zum Verkaufen>`; Slot 45 `hopper` `§eAlles einfügen` (= /sellall). Schließen ohne Verkauf gibt Items zurück; nicht verkaufbare Items bleiben liegen und werden zurückgegeben.

### 5.29 SMP Kopfgeld-Übersicht (`/bounty`) – „Kopfgeld-Übersicht“, 6 Reihen (belegt Frame 20.09.2026)
Z0 `light_blue_stained_glass_pane`, Slot 4 `lime_concrete` `§aSeite {n}/{m}`; Slots 9–35 (27) Köpfe absteigend nach Kopfgeld: Name `§c{name}` (Rangfarbe), Lore `§eKopfgeld: {summe}$` · ` ` · `§eAusgesetzt von: {n} Spielern` · `§eLetzte Erhöhung: {TT.MM.JJJJ HH:mm}`; Z4 `light_blue_stained_glass_pane`; Z5 Slot 45 `yellow_shulker_box` `§eKopfgeld aussetzen` (Chat-Eingabe), Slot 51 `hopper` `§eSortierung: {Höhe|Neueste}`, Slot 52 `black_concrete` `§7Vorherige Seite`, Slot 53 `black_concrete` `§7Nächste Seite` (Funktion Z5 Annahme).

---

## 6. Mechaniken & Zahlen

### 6.1 Geld & Formate
1. Interne Einheit Cent (`long`). Anzeige `#.##0,##` (de-DE), `$` direkt dahinter: `3.372,01$`, `4.630.158,3$`, `100.000$` (belegt Scoreboard/Chat). Tooltip `§a{n}§2$`. Schadenswerte Bosse `0.00` (Punkt, 2 Stellen, belegt).
2. Startguthaben 0$ (Annahme); Tutorial-Belohnungen §6.17.
3. Jede Buchung: `cb.ledger` + `money_c` in einer Transaktion (`SELECT … FOR UPDATE`), Idempotenz-Key. Kein `/pay` (nicht belegt) – Geldtransfer nur per `/trade` ab Level 8 und Shops (Entscheidung).

### 6.2 Welten
| Welt (Nachbau) | Icon | Stufe | Plotgröße | Aufpreis | Min-Level | Original |
|---|---|---|---|---|---|---|
| Tulip, Cactus, Lantern, Kelp, Carrot, Cherry, Moss, Amethyst, Copper | gleichnamiges Item | 1 | 35×35 | 0$ | – | Poppy, Pumpkin, Sponge, Melon, Slime, Clay, Honey, Bamboo, Coral |
| Terracotta | `terracotta` | 2 | 39×39 | 10.000$ | 7 | Brick |
| Prismarine | `prismarine` | 3 | 45×45 | 90.000$ | 22 | Ice |

Plotwelt-Generator (Annahme bis auf Höhe): Grundgestein Y −64, Erde −63…−61, Gras −60 (`/plot floor` = Ebene −64… belegt „Höhe −64“), Straße 7 Blöcke `smooth_stone`, Rand (edge) `smooth_stone_slab` auf Y −59, Wand (wall) unter dem Rand bis Y −64 `stone`. Biom `plains`. Weltspawn auf der Kreuzung (0,0). Grid: Zelle `gx` = `floor((x - road/2) / (size+road))`; **Reihe** = `max(r(gx), r(gz))` mit `r(g) = g ≥ 0 ? g+1 : -g`. Reihen 1–3 = Spawnplots.
Lobby: handgebaute Welt, Schutz komplett, Portale (Plotwelten → GUI §5.7, Farmwelten → Server mit geringster Last, Nether-Portal-Look). Farmwelten 1–9: Vanilla-Generator, Weltgrenze 10.000 × 10.000 (Annahme), Nether je Farmwelt-Server (Annahme; Wiki „in jeder Farmwelt“), End nur Event.

### 6.3 Plots
6.3.1 **Kauf** (`/plot auto|claim`):
1. Prüfe Plotwelt, Min-Level der Welt, Plotlimits (§6.3.2), Claim-Abklingzeit 24 h (`cb:claim:cd`, ab 2. Plot; LEGACY 48 h; belegt Changelog), Geld.
2. Preis `P(n) = min(61.500, 3.000 + 1.500·(n−1))` $ mit n = Anzahl besessener Teilplots + 1 (Annahme Kurve; belegt Deckel 61.500 ab 40. Teilplot: P(40)=61.500) + Welt-Aufpreis (10.000/90.000) → Höchstpreise 61.500/71.500/151.500 (belegt). Supremium −5 % (belegt).
3. Buchung `PLOT_BUY`, Zelle anlegen, Title, `cb.plot.claimed`, Event `PlotClaimEvent`.
6.3.2 **Limits** (Annahme Werte; belegt: Anhebungen bei Level 2, 10, 15, 20, 25, 35, 40, 45, 50; Merge zählt als 1 Plot; lokale + globale Limits):

| Level ≥ | 0 | 2 | 10 | 15 | 20 | 25 | 35 | 40 | 45 | 50 |
|---|---|---|---|---|---|---|---|---|---|---|
| Plots global | 42 | 44 | 45 | 47 | 50 | 55 | 60 | 70 | 75 | 80 |
| Teilplots global | 74 | 76 | 77 | 79 | 82 | 87 | 92 | 102 | 107 | 112 |
| Plots je Plotwelt | 10 | 10 | 10 | 12 | 12 | 14 | 14 | 16 | 16 | 20 |
(Startwerte 42/74 aus altem Forumsbeitrag, unbelegt für 2026.)
6.3.3 **Merge**: nur eigene, angrenzende Plots in Blickrichtung; Ergebnis muss Rechteck sein; max. Teilplots je Reihe: Reihe 1–4: 1 (kein Merge), 5–8: 4, 9–12: 9, ≥ 13: 36 (belegt Stützstellen R1/R5/R9/R13, Zwischenreihen abgeleitet). Maßgeblich ist die niedrigste Reihe im Ergebnis. Gebühr `2.500$ × Teilplots im Ergebnis` (Annahme), Supremium −5 %. Straßen innerhalb werden zu Plotfläche, Kreuzungen ebenso.
6.3.4 **Rollen-Matrix** (belegt Wiki):

| Recht | Owner | Co-Owner | Admin | Builder | Spieler |
|---|---|---|---|---|---|
| Betreten | ✓ | ✓ | ✓ | ✓ | wenn öffentlich und nicht gebannt |
| Bauen | ✓ | ✓ | ✓ | nur wenn Owner in derselben Plotwelt online (`cb:presence`) | wenn `buildall` |
| Lift-/Teleport-Schilder nutzen | ✓ | ✓ | ✓ | ✓ | wenn öffentlich |
| `/plot info` | ✓ | ✓ | ✓ | ✓ | wenn öffentlich |
| `deleteinfo`, `row`, `border`, `entitylimits` | ✓ | ✓ | ✓ | ✖ | ✖ |
| `offer` (verkaufen) | ✓ | ✖ | ✖ | ✖ | ✖ |
| Immun gegen Plot-Config | ✓ | ✓ | ✖ | ✖ | ✖ |
| Shop-/Lift-/Teleport-Schilder erstellen | ✓ | ✓ | ✓ | ✖ | ✖ |
| `center` | ✓ | ✓ | ✖ | ✖ | ✖ |
| `setrole`/`removerole` | ✓ | bis Admin | ✖ | ✖ | ✖ |
| `kick` | ✓ | bis Co-Owner | ✖ | ✖ | ✖ |
| `biome` | mit Recht | wenn vom Owner freigegeben | ✖ | ✖ | ✖ |
| Bodyguards setzen | ✓ | ✓ | ✖ | ✖ | ✖ |
| `title`, `setprivacy`, `setspawn`, `hologram`, `fly`, `wall`, `edge`, `chat`, `ban`, `unban`, `config`, `setalias`, `removealias`, `setid` | ✓ | ✓ | ✖ | ✖ | ✖ |
| `buildall`, `sethome`, `merge`, `move`, `clear/reset`, `delete`, verkaufen | ✓ | ✖ | ✖ | ✖ | ✖ |
Mitgliederplätze je Plot: 15 / Premium 30 / Supremium 50 (belegt). Rolle entfernt → alle Shops dieses Spielers auf dem Plot `active=false`.
6.3.5 **Verkauf** (`/plot offer`): Käufer ab Level 35, Verkäufer ab Level 8 (belegt). Angebot 5 min; Owner klickt `[ANNEHMEN]` → Geld Käufer→Verkäufer (`PLOT_SALE`), Owner wechselt, alle Rollen/Bans gelöscht, alter Owner ohne Rechte (belegt). Spawnplots mit Adminsperre: kein Verkauf/Merge/Move/Delete.
6.3.6 **Inaktivität**: Frist nach Plot-Level: L0 28 T, L4 45, L8 60, L11 90, L13 120, L21 180 (belegt; Stufenfunktion, Zwischenlevel = nächst kleinere Stufe); Premium/Supremium ×1,5 (belegt „+50 %“). Plot-Level = `floor(Besitztage / 7)`, max. 30 (Annahme). Inaktiv = Owner-`last_seen`. Löschlauf täglich 20:00–21:00, fällige Plots verteilt über max. 48 h (belegt); vorher Archiv (`cb.plot_archive`) → `/plot restore`.
6.3.7 **Verschiebung**: bis 36 Teilplots (belegt); Ablauf: `move start` (auf dem Plot) → Welt-GUI §5.7 → zur Zielposition gehen (Ziel-Merge muss frei sein, gleiche Form, Reihen-Limit) → `move confirm` → Gebühr `10.000$ × Teilplots` + bei größerer Stufe Aufpreisdifferenz × Teilplots (Annahme) → Kopie in Schritten von 1 Teilplot je 5 s (Plot gesperrt, `cb:plot:lock`) → `move status` zeigt `{p} %` → alt archivieren und leeren. `move cancel` vor confirm kostenlos, danach nicht möglich.
6.3.8 **Shops**: max. 1.000 je Spieler (belegt); Schild an/auf Kiste oder Fass oder Hängeschild darüber, Text `[Shop]` in beliebiger Zeile; nur Rolle O/C/A. Schildzeilen: 1 `[Shop]` in Füllfarbe, 2 Itemname (`§0`), 3 `§0K {buy}$` , 4 `§0V {sell}$` (Annahme; Original-Clip zeigt „Buy“/„Sell“). Füllfarbe: leer `§4`, < 20 % `§c`, < 50 % `§e`, < 95 % `§a`, ≥ 95 % `§2` (belegt; 95 % → `§2` entschieden). Preisänderung → 30 s gesperrt (belegt). Kauf: Bestand prüfen, Geld Kunde→Ersteller (Shoplimit `limit_c` bei Einnahmen bzw. Ausgaben), Chatmeldung an Ersteller wenn online und Toggle an.
6.3.9 **Hologramme**: Limit `3 × Teilplots + Kreuzungen im Merge` (`(w−1)·(h−1)` bei w×h) (belegt Regel); Text an Fußposition; `remove` = nächstes ≤ 5 Blöcke.
6.3.10 **Entity-Limits** (Annahme) je Teilplot: 30 Tiere, 10 Fahrzeuge, 20 Rüstungsständer, 30 Item-Rahmen/Gemälde, 0 Monster (außer Config); Merge = Summe.
6.3.11 **Spawnplots**: Reihen 1–3; Privatsphäre immer öffentlich; Reihe 1 Vergabe per `/plot admin setowner` + Adminsperre; Bebauungspflicht 6 Wochen (Team prüft, `/plot admin info` zeigt Besitzdauer).
6.3.12 **Showcase**: Schild `[Showcase]` an Kiste/Shulker → Fremde sehen Inhalt (Lesekopie-GUI), Shift+Rechtsklick auf Shulker-Item darin zeigt dessen Inhalt.
6.3.13 Plot betreten: Title (§4.6), Bossbar `plot`, Meldung an Owner (togglenotify) `cb.plot.enter_notify`, Logbuch-Eintrag (max. 1 je Spieler/10 min).

### 6.4 Level & Prestige
1. XP für Level L→L+1: `need(L) = 100 + 3·L²` (Annahme; L0→1 = 100, L4→5 Summe 590, L99→100 = 29.503; Summe 0–100 ≈ 995.050).
2. Prestige-Faktor auf `need`: Liga 0 ×1, Liga 1 ×2, Liga 2 ×10, Liga 3 ×100 (belegt; „um das Zehnfache“ = ×10 entschieden). Max: Level 100 in Liga 3.
3. Quellen: Jobs, Quests, Meilensteine, Bosse. XP-Booster +15 % (belegt: `1.200 XP (+180 XP durch Booster)`).
4. Level-Up: Chat `cb.level.up` + Freischaltzeile `cb.level.unlock.*` (Tabelle unten), Title.

| Level | Freischaltung (Nachricht) |
|---|---|
| 1, 9, 14, 21, 31, 38 | +1 Endertruhe kaufbar |
| 2, 10, 15, 20, 25, 35, 40, 45, 50 | Plotlimit erhöht |
| 5 | Chat |
| 6 | Nether |
| 7 | Job Bergarbeiter, Plotwelt Stufe 2 |
| 8 | Geld in `/trade`, Shops & Plots verkaufen |
| 11 | Job Jäger |
| 15 | Marktplatz 2.000$ |
| 18 | Job Farmer |
| 22 | Plotwelt Stufe 3 |
| 25 | Marktplatz 3.000$ |
| 35 | Plots von Spielern kaufen |
| 50 | Marktplatz 4.000$ |
| 100 | 2 Warps; `/prestige join` |
Nach Prestige bleiben alle Freischaltungen erhalten (Annahme).

### 6.5 Farmwelten
1. Beitritt: Portal/`/warp Farmwelten` → Server mit niedrigster Last (`cb:farm:{n}`), max. 40 Spieler je Farmwelt (abgeleitet: 75/360 über 9 Welten), Spawn auf Himmelsinsel (Y 200), unsichtbare Elytra im Brustplatz (Original-Brustplatz wird zwischengespeichert), Elytra wird bei Bodenkontakt entfernt, Raketen erlaubt. 16 s unverwundbar nach Ankunft (belegt `Du bist nun für 16 Sekunden unverwundbar`).
2. PvP aus (belegt Meldung beim Betreten).
3. Reset: jeden Donnerstag ab 18:00, Welten nacheinander alle 10 min (Annahme Abstand): Warnung 10/5/1 min, Spieler → Lobby, Welt + Nether neu (neuer Seed), Homes in der Welt löschen.
4. Item-Räumung (Annahme Takt alle 15 min): Ansage 60 s, 10 s, 3/2/1 s, dann `Items wurden entfernt` (belegt Texte).
5. Nacht: Tag wird gesetzt, wenn ≥ 25 % schlafen (Annahme) → `cb.farm.day`.
6. Regeln (belegt, 17 Punkte N09 §3.5.1): Redstone aus; Lama/Maultier/Esel ohne Kiste; Villager-Spawnei nicht auf Spawner; Eisen-/Gold-/Kupfererz 1 Roherz, kein Fortune; Schmiedevorlagen nicht duplizierbar und (außer Netherite-Upgrade) nicht in Strukturen; Schatzkarten und Wurftränke ohne Funktion; kein Leuchtfeuer-Crafting; Villager ohne Mending; Betten explodieren nicht; Spawnei nicht auf Trial Spawner; Rüstungsständer behalten Pose; Villager/Sniffer/Kamel vermehren sich nicht; keine Nitwits; Bambusbretter → keine Kiste/Fass; Glow Berries 10 s Leuchten; Redstone-Block unter Stufe/Platte = Sitzbank.
7. Nether: Portal auf Spawninsel, Zutritt ab Level 6 (belegt); eigene Portale werden nicht entzündet.
8. End: geschlossen; bei Event Portal in Lobby + `/warp End`.

### 6.6 Jobs
1. 8 Jobs (belegt): Sammler, Gräber, Holzfäller, Abenteurer, Angler (L1), Bergarbeiter (L7), Jäger (L11), Farmer (L18). Nur in Farmwelten (inkl. Nether). 1 Job; Supremium + Nebenjob (50 % Geld+XP des Stundenlimits, zählt nicht für Ranking; belegt).
2. Aktion → Belohnung `geld(a)×stufenfaktor×(1+Gildenbonus)`, `xp(a)×stufenfaktor×(1+Booster)`; Meldung `cb.job.xp` (belegt „Du hast 17 CityBuild-XP erhalten“).
3. Basiswerte (Annahme; je Aktion): Sammler Blume 1,50$/4 XP; Gräber Block 0,25$/1 XP; Holzfäller Stamm 0,80$/3 XP; Abenteurer Monster 4$/17 XP (Wither-Skelett 20$/60); Angler Fang 3$/12 XP, Schatz 8$/30; Bergarbeiter Kohle 0,6$/2, Eisen 1,5$/5, Gold 2$/7, Diamant 10$/35, Smaragd 12$/40, Antiker Schrott 25$/80; Jäger Tier 2$/8; Farmer reife Pflanze 0,4$/2. Nur natürlich generierte bzw. ausgewachsene Blöcke (Platzier-Marker im Chunk-PDC).
4. Stufen (Job-XP der Season): Anfänger 0, Fortgeschrittener 5.000, Profi 20.000, Meister 60.000, Elite 150.000 (Annahme); Faktor 1,00/1,15/1,30/1,50/1,75; Doppel-Drop 0/2/4/7/10 % (Annahme).
5. Stundenlimit (Annahme) Geld/XP: Anfänger 600$/1.500, Fortg. 800$/2.000, Profi 1.000$/2.500, Meister 1.250$/3.200, Elite 1.500$/4.000; Reset volle Stunde (belegt). Limit erreicht → `cb.job.limit_xp` bzw. `cb.job.limit_money` (belegt Texte), keine Belohnung bis zur vollen Stunde. Job des Monats: +35 % XP (CB und Job) und Limit-„Aufwand“ −20 % (= Aktionen zählen ×1,25 auf Fortschritt; belegt Werte).
6. Season = Kalendermonat: am 1. 00:00 Job-Season-Stats/Stufen → Anfänger, neuer Job des Monats (gewichtete Zufallswahl, Gewicht unbeliebter Jobs 0,5, Annahme), HoF Top 50 neu.
7. Kündigen `/job quit <Job>`: erreichte Stufen bleiben, Teilfortschritt verfällt (belegt). Wechsel ohne Kosten, Abklingzeit 1 h (Annahme).
8. Talisman-Einmalbelohnung beim ersten Erreichen von Profi in einem Job (Annahme zu „Jan 2025: einmalig als Job-Belohnung“).

### 6.7 Economy-Quellen und -Senken
| System | Regel | Beleg |
|---|---|---|
| Marktplatz | NPC-Stände (Rechtsklick 1, Shift+Klick alle einer Sorte); Ankauf `cb.market_price`; Tageslimit L0 1.000$, L15 2.000$, L25 3.000$, L50 4.000$; NPC-Trading-Booster +30 % (1.300/2.600/3.900/5.200); Reset 00:00 | belegt; 3.000$ ab L25 entschieden (Option L30) |
| Wechselbörse | max. 10.000 Coins/Tag → 1.000$ (10:1), Stufen 1.000/5.000/10.000; Reset 00:00 | belegt/abgeleitet |
| Adminshop | Wochenangebot Do 18:00; Bestand je Angebot global (`stock_total`, Default 10.000 Stück/Woche; Mending-Bücher 50, Spawn-Eier 100, Annahme); Questpunkte −5 % (Annahme: ab 10 Punkten); Shopping-Booster −15 % | belegt (Rotation, Booster), Bestand Annahme |
| Mall | Do 18:00, Wiederkehr frühestens 6 Monate; Kopf 5.000$ oder 1 Ticket, Gemälde 100.000$ oder 1 Gemälde-Ticket, Bild 50.000$ oder 1 Gemälde-Ticket; Mall-Booster −15 % | belegt |
| Möbelhaus | täglich 00:00 neu, Preis Geld + 1 Möbel-Ticket (Default 2.500$) | belegt/Annahme Preis |
| Gilde | Einzahlung nicht rückzahlbar | belegt |
| Talismane | Herstellung, Verkürzung, Beutel, Kombination | belegt |
| Quests | je Quest 5.000$, 50 XP, 1 Questpunkt (Annahme nach altem Beitrag) | Annahme |

### 6.8 Gilden
6.8.1 Gründung kostenlos (Annahme); Name 3–16 Zeichen; Kapazität `3 + Σ(+1 je Premium-, +3 je Supremium-/Teammitglied) + Vorteile` (belegt).
6.8.2 Einzahlung: Betrag ≥ 1$; `deposited_c += x`; Level = größtes L mit `T(L) ≤ deposited`, `T(L) = floor(1000·(L−1)^2.22)`$, T(1)=0 (belegt 41/42 Tabellenwerte). Broadcast an Online-Mitglieder `cb.guild.deposit`; bei Aufstieg `cb.guild.levelup` und je neuem Vorteil `cb.guild.perk_unlocked` (belegt Struktur).
6.8.3 Rollen (belegt): Gründer, Leiter, Moderator, Mitglied, Gast. Befördern/Kicken: Gründer bis Leiter, Leiter bis Moderator, Moderator bis Mitglied; Umbenennen/Banner/MOTD: Gründer+Leiter; Übertragen/Löschen: Gründer; Einladen: ab Moderator; Rucksack: ab Mitglied (Gast nicht); Chat + Vorteile: alle.
6.8.4 Vorteile (belegt, 42 Stufen): +Spieler bei 2(1), 4(1), 6(2), 8(3), 10(2), 12(3), 14(2), 16(3), 18(2), 20(3), 25(5), 30(5), 95(5) = +37; Rucksack +9 Slots bei 3, 7, 13, 22, 28 (Basis 9, max. 54); Job-Geld +% bei 15(1), 21(2), 26(3), 35(3), 60(3), 110(4), 170(4), 220(5) = 25 % (Job-Limit bleibt); Schatzchance +% bei 5(0,05), 11, 19, 24, 29, 50 (je 0,01), 80(0,05), 150(0,05) = 0,20 %; passive XP alle 2 min bei 9(1), 17(2), 23(2), 27(5), 40(5), 70(5), 130(5), 195(5) = 30. Widerspruch Wiki-Beispiel (32 Mitglieder / 0,25) → Tabelle gilt.
6.8.5 Schatzkisten: je abgebautem Block in Farmwelt Chance = Gilden-Schatzchance + Talisman (in %); Inhalt Lootable `cb:treasure` (u. a. Schmiedevorlagen, Annahme Rest).
6.8.6 Online/Offline-Meldungen an Gilde, MOTD beim Join (`cb.guild.motd`).
6.8.7 Top: `/top guild` und HoF Top 5 nach `deposited_c`. Chat-Ausgabe `cb.guild.top.line` (belegt „#1 {name} {summe}$“).

### 6.9 Endertruhen, Homes, Warps, TPA
1. Endertruhen: 12 (`/ec 1-12`); 6 per Level (L1, 9, 14, 21, 31, 38) im Tresor kaufbar (Annahme Preise 5.000/15.000/30.000/60.000/120.000/250.000$), 6 per Rechte-Buch.
2. Homes: 3; Premium 3; Supremium 8 (belegt); +1 je Home-Buch. Nicht im Weltall; Farmwelt-Homes verfallen beim Reset.
3. Spielerwarps: 1, ab Level 100: 2 (belegt). `/top warp` = eindeutige Besucher der laufenden Woche (Annahme).
4. TPA: Anfrage 60 s; Annahme klickbar; Teleport nach 3 s ohne Bewegung (belegt SMP, für CB übernommen); nicht im Weltall (belegt); Server-übergreifend über Proxy.

### 6.10 Talismane
6.10.1 27 Typen, Seltenheiten Gewöhnlich `§f`, Selten `§9`, Episch `§5`, Legendär `§6` (Farben Annahme). Item: Basis `player_head` mit Textur je Typ, Name `§{farbe}{Name}`, Lore `§7{Effekt}` · ` ` · `§7Seltenheit: §{farbe}{Seltenheit}`. Belegte Effekte: „Krieger“-Talisman (Selten) 5 % Chance auf +100 % Treffer-Schaden; „Gliederfüßer“-Talisman (Legendär) −20 % Schaden von Spinnen und Silberfischen; ein Schatzkisten-Talisman. Liste (Annahme, Werte G/S/E/L, „–“ = nicht in der Seltenheit):

| # | Nachbau-Name | Effekt | G | S | E | L |
|---|---|---|---|---|---|---|
| 1 | der Kämpfer | Chance auf +100 % Schaden | 2 % | 5 % | 8 % | 12 % |
| 2 | der Achtbeiner | − Schaden durch Spinnen/Silberfische | 5 % | 10 % | 15 % | 20 % |
| 3 | des Schatzsuchers | + Schatzkistenchance (absolut) | – | 0,02 % | 0,04 % | 0,06 % |
| 4 | des Bergmanns | + Job-Geld Bergarbeiter | 2 % | 4 % | 6 % | 8 % |
| 5 | des Gärtners | + Job-Geld Farmer | 2 % | 4 % | 6 % | 8 % |
| 6 | des Fischers | + Job-Geld Angler | 2 % | 4 % | 6 % | 8 % |
| 7 | des Holzes | + Job-Geld Holzfäller | 2 % | 4 % | 6 % | 8 % |
| 8 | des Jägers | + Job-Geld Jäger | 2 % | 4 % | 6 % | 8 % |
| 9 | des Abenteuers | + Job-Geld Abenteurer | 2 % | 4 % | 6 % | 8 % |
| 10 | der Erde | + Job-Geld Gräber | 2 % | 4 % | 6 % | 8 % |
| 11 | der Blüte | + Job-Geld Sammler | 2 % | 4 % | 6 % | 8 % |
| 12 | des Wissens | + CityBuild-XP | 1 % | 2 % | 3 % | 5 % |
| 13 | der Zwillinge | + Doppel-Drop-Chance | 1 % | 2 % | 3 % | 5 % |
| 14 | des Panzers | − Monster-Schaden | 2 % | 4 % | 6 % | 8 % |
| 15 | des Feuers | − Feuer-/Lavaschaden | 10 % | 20 % | 30 % | – |
| 16 | der Feder | − Fallschaden | 10 % | 20 % | 30 % | 40 % |
| 17 | des Händlers | + Marktplatz-Ankaufspreis | 1 % | 2 % | 3 % | – |
| 18 | der Sammlung | + Kopf-Dropchance Mob-Collection | 5 % | 10 % | 15 % | 25 % (relativ) |
| 19 | der Bestien | + Schaden gegen Bosse | 2 % | 4 % | 6 % | 10 % |
| 20 | des Atems | + Sauerstoff im Weltall | 10 % | 20 % | 30 % | 40 % |
| 21 | des Antriebs | − Treibstoffverbrauch | 5 % | 10 % | 15 % | 20 % |
| 22 | der Ausdauer | − Hungerverbrauch | 5 % | 10 % | 15 % | – |
| 23 | des Vampirs | Lebensraub bei Treffer (Chance 1 Herz) | – | 3 % | 5 % | 8 % |
| 24 | der Eile | Eile I beim Abbauen (Chance 10 s) | 2 % | 4 % | 6 % | – |
| 25 | des Glücks | + Glück beim Angeln (Stufe) | 1 | 1 | 2 | 3 |
| 26 | der Erfahrung | + Vanilla-XP | 5 % | 10 % | 15 % | 20 % |
| 27 | der Händlerin | − Adminshop-Preise | – | – | 2 % | 4 % |
Gleiche Typen im Beutel stapeln nicht (nur der höchste wirkt, Annahme).
6.10.2 Herstellung: Basiskosten 125.000$ (GUI-Wert entschieden; Option 150.000$); Premium −10.000$, Supremium −25.000$; Slots 1/2/3 (belegt). Ergebnis-Seltenheit (Annahme) G 60 %, S 27 %, E 10 %, L 3 %; Dauer 6/12/18/24 h (belegt). Verkürzen beliebig oft: 4 h für 50.000$ (Premium −20 % = 40.000$, Supremium −40 % = 30.000$; belegt %).
6.10.3 Beutel: Freischaltung kostenlos, 3 Plätze; Upgrade auf n (6…36, Schritt 3): `Preis = 25.000$·(n/3−1)`, `Fragmente = n/3+1` (belegt Tabelle; Summe 88 / 1.650.000$).
6.10.4 Kombination: 2 gleiche Seltenheit; Erfolgschance (Annahme) G→S 40 %, S→E 30 %, E→L 20 %; gleicher Typ +10 Prozentpunkte; L+L → zufälliger anderer Legendärer (100 %, belegt); Misserfolg → 1 der beiden zufällig zurück (belegt); Kosten 25.000/50.000/100.000/150.000$ je Ausgangsstufe (Annahme), Premium −10 %, Supremium −20 % (Annahme).

### 6.11 Bosse (Farmwelt)
1. 16 Strukturen je Farmwelt (Generator platziert 1–2 Instanzen je Strukturtyp, Annahme). Boss spawnt, wenn ein Spieler die Arena betritt und kein Boss aktiv ist; Respawn 30 min nach Tod (Annahme). HP 400–1.500 (Annahme). Alle Bosse droppen ihren Collection-Kopf (belegt).

| Struktur (Nachbau) | Boss (Nachbau) | Drops (belegt Typen) | Original |
|---|---|---|---|
| Bambustempel | Meister Ren | Stahl, Ticket | Asiatischer Tempel / Thai-Lee-Son |
| Versunkene Stadt | Okeanos | Hornschilder, Ticket | Atlantis / Neptun |
| Ballonhafen | Kapitän Gerd | Lavaeimer, Ticket | Heißluftballons / Georg |
| Schutzraum | Major Krämer | Ticket, Totem | Bunker / Karl |
| Festung | König Aldric | Ticket, Totem | Burg / Arthur |
| Spukdorf | Nebelgraf | Blut, Totem | Geisterstadt / Huibuh |
| Leuchtfeuer | Wärter Ole | Fackel, Seelenfackel | Leuchtturm / Hugo |
| Dschungeltempel | Itzam | Ticket, Totem | Mayatempel / Diego |
| Orklager | Grimzahn | Ticket, Metall, Stock | Orkstadt / Godlin |
| Wüstengrab | Sethos | Ticket, Leder, Totem | Pyramide / Anubis |
| Gutshof | Baron Hagen | Ticket, Totem | Schloss Mühlenhof / Hoddock |
| Geisterschiff | Kapitänin Morra | Ticket, Totem | Piratenschiff / Salazar |
| Wachturm | Hauptmann Varus | Kette, Logbuch | Schlossturm / Leonidas |
| Uralter Baum | Borkmund | Stock, Ticket | Mammutbaum / Gruut |
| Schattenreich | Umbrax | Ticket, Metall, Totem | UpsideDown / Demongor |
| Die Villa | Der Hausherr | Wächter-Brustplatte, Stock, Ticket | Vorbild- Villa / Don |
2. Drop-Chancen (Annahme): Ticket 50 %, Material 35 %, Totem 10 %, Beutel-Fragment 8 % (alle Bosse), Möbel-Ticket 5 %, Reparaturbuch 3 % (2025 belegt nur für einige Weltall-Bosse). Loot an jeden Teilnehmer mit ≥ 5 % Schaden einzeln gewürfelt.
3. Seltener Boss „Der Fremde“ (Original Herobrine): Prüfung alle 10 min je aktivem Spieler 0,2 % (belegt Chance), mit Anführer + Gefolge, Geräusche, starrt an, Blindheit nach Angriff, kein Hit-Cooldown, verschwindet nach 15 s (belegt); Drops: Glück-V-Spitzhacke 10 %, Sichel 40 %, Stärke-IV-Trank 5 % (Annahme).
4. Saisonal „Herzhüter“ (Original Valentinsprotektor): Februar, nur Flower Forest / Sunflower Plains, mit Skeletten; 30 % Kopf + Rosenstrauch mit Verzauberung „Liebe X“ (sinkt alle 24 h um 1) (belegt).
5. Statistik „Bosse getötet“ zählt nur Hauptbosse, nicht Nebenbosse (belegt Aug 2025).

### 6.12 Weltall
1. Zugang: NPC WELTALL (§5.25) → Instanz-Server `cb-space`; solo oder Duo (`/weltall invite`, 60 s). Keine Homes/Warps, kein `/tpa`, keine Enderperlen (belegt).
2. Zonen (eigene Namen; Original „Unsere Sphäre“, „Das Sonnensystem“, „Die tiefen Weiten“): Zone 1 **Erdorbit**, Zone 2 **Planetenbahn**, Zone 3 **Tiefraum**. Freischaltung (Annahme Mengen): Z1 Level 30 + 250.000$ + volle Diamant-/Netherite-Rüstung; Z2 Raumschiff-Zündschlüssel (belegt Item, „Mission ins Sonnensystem“) + 1.000.000$; Z3 UFO-Zündschlüssel + 5.000.000$.
3. HUD: O2 (Start 100 %, −1 %/3 s außerhalb Station) und Treibstoff (−1 %/s beim Fliegen mit Jetpack = Leertaste in der Luft, Nachfüllen an Station) als Actionbar (§4.5). O2 0 → 1 Herz/s Schaden. Gravitationsfeld: unter Y 40 Warnung `cb.space.gravity_warn` (belegt), unter Y 0 Absturz `cb.space.crashed` (belegt) → zurück zur Station, Instanz-Loot bleibt (Annahme).
4. Bosse (belegt Namen/HP teilweise): Zone 1 „Mondmann“ 1.000 HP (Original Mann im Mond), „Raumfahrer“ 800 HP (Astronaut), „Sputnika“ 600 HP (Laika), „Grüner Gast“ 500 HP (Alien); Zone 2/3: „Der Händler“ 5.000 HP (Geschäftsmann) (HP Annahme außer 1.000/500).
5. Schadensliste: während des Kampfs Panel `§f{Boss} - §a{max}HP` · `§f#{n} | {name} | {schaden 0.00}` (Top 3) · `§fDein Rang: {r} | {schaden}` (belegt Struktur, „Your rank“ eingedeutscht). Darstellung Default in der Sidebar (ersetzt Zeilen während Kampf), Option Resourcepack-HUD unten links (§10).
6. Drops: Kapselstück (`disc_fragment_5`, für „kleine Rettungskapsel“), Zündschlüssel, Reparaturbücher, Tickets, Collection-Kopf (Kategorie „Dungeon-Kreaturen“, belegt).

### 6.13 Custom-Items
Format: Name in Itemfarbe, Lore Zeile 1 `§9Custom-Item` (belegt), dann `§7`/`§f`-Beschreibung; PDC `retromc:ci` = Schlüssel; nicht in Vanilla-Rezepten nutzbar.

| Schlüssel | Name | Basis | Herkunft |
|---|---|---|---|
| `ticket` | `§eTicket` | `paper` | Bosse (Mall-Kopf) |
| `painting_ticket` | `§6Gemälde-Ticket` | `paper` | Rezept aus 4 Tickets + Gemälde (Annahme) |
| `furniture_ticket` | `§bMöbel-Ticket` | `paper` | Crates, Bosse |
| `bag_fragment` | `§dBeutel-Fragment` | `amethyst_shard` | Bosse |
| `totem_<boss>` | `§6Totem ({Boss})` | `totem_of_undying` | Bosse (Effekt: wie Totem + 10 s Resistenz, Annahme) |
| Materialien | Stahl, Hornschilder, Blut, Fackel, Seelenfackel, Metall, Stock, Leder, Kette, Logbuch | Vanilla-Basis passend | Bosse |
| `guard_chestplate` | `§cWächter-Brustplatte` | `netherite_chestplate` | Villa-Boss |
| `titan_*` | `§bTitan-{Werkzeug}` | Netherite-Werkzeug, Effizienz VI (Annahme) | Rezept (OCR „Titanwerkzeuge“) |
| `ship_key` | `§cRaumschiff-Zündschlüssel` | `ominous_trial_key` | belegt |
| `ufo_key` | `§5UFO-Zündschlüssel` | `trial_key` | belegt (Name Annahme) |
| `capsule_piece` | `§9Kapselstück` | `disc_fragment_5` | belegt |
| `escape_capsule` | `§bKleine Rettungskapsel` | `heart_of_the_sea` | Rezept; Nutzung: Teleport zur Station bei Absturz (Annahme) |
| `repair_book` | `§aReparaturbuch` | `enchanted_book` | Weltall-Bosse |
| `rights_book_*` | `§6Rechte-Buch: {Recht}` | `knowledge_book` | Crates: `/sit`, `/plot physics`, `/plot biome`, +1 Endertruhe, +1 Home (belegt) |
| `booster_*` | `§d{Typ}-Booster ({dauer})` | `nether_star` | Crates, Monthly Chest, Quests |
| `rose_bush_love` | `§cRosenstrauch` | `rose_bush` | Saisonboss |

### 6.14 Booster (global, belegt Typen)
| Typ | Effekt | Default-Dauer |
|---|---|---|
| Fly | Fliegen in Plotwelten für alle | 30 min |
| XP | +15 % CityBuild-XP | 30 min |
| Job-XP | +10 % Job-XP | 30 min |
| NPC-Trading | +30 % Marktplatz-Tageslimit | 30 min |
| Farming | +25 % Ernte-Drops Farmwelt (Annahme) | 30 min |
| Mining | Eile Stufe 8 in Farmwelten (Changelog „effect level 8“) | 30 min (belegt) |
| Shopping | −15 % Mall und Adminshop | 30 min |
| Universal | aktiviert einen frei gewählten Typ | – |
Gleicher Typ: Dauer addiert (belegt). Aktivierung → Broadcast `cb.booster.activated` auf allen CB-Servern, Tab-Footer.

### 6.15 Crates, Gold, Belohnungen
1. Crates als Guthaben (`cb.crate_balance`), Kauf im Gold-Shop: 2 = 300, 4 = 500, 10 = 950, 21 = 1.800 Gold (belegt). Öffnen an `/warp Crates`, Vorschau mit Chancen (Pflicht, Transparenz). Inhalte (Annahme Gewichte): Farbverläufe 25 %, Partikel 20 %, Custom-Items 25 %, Rechte-Bücher 10 %, Booster 15 %, Möbel-Tickets 5 %. Saison-Crates (Winter, Frühling, Jubiläum) mit eigener Tabelle. Alle Crate-Items handelbar (belegt).
2. Belohnungstruhen: täglich 00:00, wöchentlich So 00:00, monatlich am 1. 00:00 nur Premium/Supremium; Supremium Zusatzbelohnung weekly+monthly (belegt). Inhalte Lootables `cb:reward/*` (Villager-Spawnei nur hier und Handel, belegt).

### 6.16 Chat (Kanäle, serverübergreifend über `net:cb-chat`)
| Kanal | Format MODERN | LEGACY | Empfänger | Recht |
|---|---|---|---|---|
| Global | `§7[§3Global§7] §8({sterne}§f{level}§8) {nameColor}{name}§7: §f{text}` | `§7[§3Global§7] §f{level} {nameColor}{name}§7: §f{text}` | alle CB-Server | Level 5 |
| Trade | `§7[§eTrade§7] §8(§f{level}§8) {nameColor}{name}§7: §f{text}` | ohne Klammern | alle mit Trade-Empfang an | Level 5; `@t`/`/chat trade` |
| Private | `§7[§dPrivate§7] {nameColor}{von} §7» §c{an}§7: §f{text}` | `>>` statt `»` | Absender + Ziel | Level 5 oder Buddy |
| Plot | `§7[§9Plot§7] §8(§f{level}§8) {nameColor}{name}§7: §f{text}` | – | alle auf demselben Plot | – |
| Gilde | `§7[§cGilde§7] {nameColor}{name}§7: §f{text}` | `[Gilde]` §e | Gildenmitglieder überall | – |
`{sterne}` = Prestige-Glyph(s) `§e✪` × Liga + Leerzeichen (belegt Icon-Anzahl 0–2). Namensfarbe = Rangfarbe (Spieler `§a`, belegt). Unter Level 5: `cb.chat.locked`. Spam-Schutz aus Kern. Systempräfix `{P}` für alle CB-Systemmeldungen.

### 6.17 Tutorial (34 Schritte, Bossbar; Schritte 9, 10, 15 belegt, Rest Annahme)
Fortschritt automatisch per Event; `/tutorial` wiederholt Hinweis; Abschluss: 5.000$ + 1 Crate + `cb.tut.done`.

| # | Schritt (Bossbar-Text) | Erfüllt bei |
|---|---|---|
| 1 | Sieh dich am Spawn um | 20 Blöcke bewegt |
| 2 | Öffne die Hilfe mit /cb | Befehl |
| 3 | Wähle einen Job im Arbeitsamt | Job gesetzt |
| 4 | Betritt eine Farmwelt über das Portal | Server FARM |
| 5 | Verdiene CityBuild-XP mit deinem Job | 1 Job-Belohnung |
| 6 | Erreiche Level 1 | Level |
| 7 | Verkaufe ein Item am Marktplatz | Marktplatz-Verkauf |
| 8 | Prüfe dein Geld mit /money | Befehl |
| 9 | Erreiche Level 4 | Level (belegt) |
| 10 | Kaufe eine Endertruhe im Tresor am Spawn | Kauf (belegt) |
| 11 | Öffne deine Endertruhe mit /ec 1 | Befehl |
| 12 | Setze ein Home mit /home set | Home |
| 13 | Betritt die Plotwelten | Server PLOT |
| 14 | Kaufe dein erstes Grundstück mit /plot auto | Claim |
| 15 | Sprich mit den NPCs am Spawn | 5 verschiedene NPCs (belegt Text) |
| 16 | Sieh dir dein Grundstück mit /plot info an | Befehl |
| 17 | Erstelle einen Spielershop mit einem [Shop]-Schild | Shop |
| 18 | Besuche den Adminshop | GUI geöffnet |
| 19 | Erreiche Level 5 und schreibe im Chat | Chat-Nachricht |
| 20 | Besuche die Mall | GUI |
| 21 | Öffne das Talisman-Menü mit /ts | GUI |
| 22 | Schalte deinen Talisman-Beutel frei | Freischaltung |
| 23 | Hol dir die tägliche Belohnung | Claim |
| 24 | Sieh dir die Crates an | Vorschau |
| 25 | Öffne die Mob-Collection | GUI |
| 26 | Sieh dir die Custom-Rezepte mit /ci an | GUI |
| 27 | Tritt einer Gilde bei oder gründe eine | Gilde |
| 28 | Besuche die Hall of Fame | Region |
| 29 | Sieh dir die Bestenliste mit /top level an | Befehl |
| 30 | Erreiche Level 6 und betritt den Nether | Weltwechsel |
| 31 | Erledige eine Tagesquest | Quest |
| 32 | Nutze die Wechselbörse | Tausch |
| 33 | Schalte einen Booster-Überblick mit /booster auf | GUI |
| 34 | Erreiche Level 8 | Level |

### 6.18 Quests & Meilensteine
3 Tagesquests je Spieler (leicht/mittel/schwer, Reset 00:00, belegt Anzahl); Pool `quests.yml` (Job-Aktionen, Boss, Marktplatz, Mob-Köpfe). 12 Community-Meilensteine, je 14–28 Tage (belegt), Belohnungen persönliche Booster 1/3/7 Tage (Job-XP, Mining, CB-XP) oder +10 % Boss-Schaden (belegt Typen).

### 6.19 Sonstiges CB
AFK nach 5 min (Annahme) mit Meldung; Lobby-Neustart-Countdown 60, 45, 30, 15, 10, 5…1 s (belegt 60/45); `/repair` 1×/h nur Items mit Mending (belegt); Mülleimer; Schmied; HoF-Aktualisierung 5 min.

### 6.20 SMP
1. Welt: Survival, Griefing/Trolling/Fallen/Scams erlaubt, keine Claims (belegt Regeln). Server `smp-lobby` (Spawn, Villager-Handel, Shop) + `smp-world-n`; Warteschlange bei voller Welt (belegt „Platz 1“), Wildcards priorisieren (Annahme).
2. Geld `$`, Start 0$; Einnahmen `/sell`, Handel, Kopfgeld.
3. `/sell`: Gebühr 10 % (Annahme, belegt dass es eine gibt); Limit je Kategorie 10.000$ pro Stunde ab erster Nutzung (Annahme Wert; belegt „genau 1 Stunde nach Benutzung“).
4. Kampf: Treffer durch Spieler → 15 s Kampf (Annahme), Meldungen belegt; im Kampf gesperrt: `/tpa`, `/home`, `/spawn`, End-Kristalle (belegt), Logout = Tod (Inventar droppt, Annahme).
5. Kopfgeld: `/bounty <Spieler> <Betrag>` min. 1.000$ (Annahme); Summe je Ziel; Killer des Ziels erhält Summe (`BOUNTY_CLAIM`), Broadcast `smp.bounty.claimed` (belegt); Ziel erhält `smp.bounty.set_target` (belegt Struktur). Selbst-/Alt-Kill: nicht auszahlen, wenn gleiche IP (Annahme).
6. Spielzeit `/smpplaytime`; Season-Ende 21:00 am Season-Endtag (belegt Uhrzeit), Beta-Fortschritt wird zurückgesetzt (belegt Hologramm).
7. Max. 5 Accounts pro Person für AFK-Farmen (Regel, nicht technisch durchgesetzt).
8. Nether: Reset nur manuell (seit 13.09.2026 nicht bei jedem Neustart, belegt).

---

## 7. Nachrichten-Katalog

Beleg: b = belegt (Wortlaut/Struktur aus Quelle), a = abgeleitet, A = Annahme. „Original“ nennt die Referenz knapp (nicht übernehmen, wo markant).

| Key | Format | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| `cb.join.chat` | `{P}§aAlle Neuigkeiten zu CityBuild findest du unter §b{url}` | Alle Neuigkeiten … unter {url} | „Alle Informationen zu den neusten CityBuild Updates findest du unter …/go/cb“ | b |
| `cb.join.title` | `§eWillkommen, {rankColor}{name}§e!` / `§fViel Freude in unserer Stadt!` | – | „Willkommen Vorbild!“ / „Viel Spaß auf unserem CityBuild!“ | b |
| `cb.restart` | `{P}§cDiese Lobby startet in §e{s} Sekunden §cneu` | Diese Lobby startet in {s} Sekunden neu | „Diese Lobby wird in 60 Sekunden neugestartet“ | b |
| `cb.afk.on` | `§aDu bist jetzt als abwesend markiert` | – | „Du wurdest als abwesend markiert“ | b |
| `cb.afk.off` | `§aDu bist nicht mehr als abwesend markiert` | – | gleich | b |
| `cb.err.no_permission` | `{P}§cDazu fehlt dir die Berechtigung.` | – | – | A |
| `cb.err.level` | `{P}§cDafür brauchst du Level §e{lvl}§c.` | – | – | A |
| `cb.err.no_money` | `{P}§cDu hast nicht genug Geld. Es fehlen §e{fehlt}$§c.` | – | – | A |
| `cb.err.player_offline` | `{P}§cDer Spieler §e{name} §cist nicht online.` | – | – | A |
| `cb.money.self` | `{P}§7Dein Kontostand: §a{geld}$` | – | – | A |
| `cb.money.other` | `{P}§7Kontostand von §e{name}§7: §a{geld}$` | – | – | A |
| `cb.level.info` | `{P}§7Level §e{sterne}{lvl} §8| §7XP §e{xp}§7/§e{need} §8(§a{p} %§8)` | – | – | A |
| `cb.level.xp` | `{P}§7Du hast §e{xp} CityBuild-XP §7erhalten` | Du hast {xp} CityBuild-XP erhalten | „Du hast 17 CityBuild-XP erhalten“ (funktional) | b |
| `cb.level.xp_boost` | `{P}§7Du hast §e{xp} CityBuild-XP §7(§a+{bonus} XP durch Booster§7) erhalten` | – | „Du hast 1.200 CityBuild-XP (+180 XP durch Booster) erhalten“ | b |
| `cb.level.up` | `{P}§aDu bist jetzt Level §e{lvl}§a!` | – | „Du bist nun Level …“ (OCR) | b |
| `cb.level.unlock.ec` | `§b[+] §7Eine weitere Endertruhe ist freigeschaltet` | – | „[+] Du hast eine weitere Enderchest freigeschaltet“ | b |
| `cb.level.unlock.plotlimit` | `§b[+] §7Dein Plotlimit wurde erhöht` | – | „Dein Plotlimit wurde zum Teil angehoben“ | b |
| `cb.level.unlock.chat` | `§b[+] §7Du kannst jetzt im Chat schreiben` | – | „Du hast den Chat freigeschaltet“ | b |
| `cb.level.unlock.nether` | `§b[+] §7Du darfst jetzt in den Nether` | – | „Du kannst nun den Nether betreten“ | b |
| `cb.level.unlock.job` | `§b[+] §7Neuer Job verfügbar: §e{job}` (+ `§7, Plotwelt §e{welt}`) | – | „Du hast den Job Bergarbeiter sowie die Plotwelt Brick freigeschaltet“ | b |
| `cb.level.unlock.trade` | `§b[+] §7Du kannst jetzt per /trade mit Geld handeln` / `§b[+] §7und Shops sowie Plots verkaufen` | – | gleich (funktional) | b |
| `cb.level.unlock.market` | `§b[+] §7Dein Marktplatz-Limit liegt jetzt bei §a{n}$` | – | „Dein Verkaufslimit am Marktplatz beträgt nun …“ | b |
| `cb.level.unlock.plotbuy` | `§b[+] §7Du kannst jetzt Plots von anderen Spielern kaufen` | – | Level 35 | b |
| `cb.level.unlock.world` | `§b[+] §7Plotwelt §e{welt} §7ist freigeschaltet` | – | Level 22 | b |
| `cb.level.unlock.warp` | `§b[+] §7Du kannst jetzt zwei Warps setzen` | – | Level 100 | b |
| `cb.prestige.join` | `{P}§aWillkommen in Prestige-Liga §e{n}§a! Dein Level beginnt wieder bei 0.` | – | – | A |
| `cb.prestige.not_ready` | `{P}§cDu brauchst Level §e100§c.` | – | – | A |
| `cb.prestige.max` | `{P}§cDu hast die höchste Liga erreicht.` | – | – | A |
| `cb.top.header` | `§7§m          §r §6Top 10 – {kat} §7§m          ` | – | – | A |
| `cb.top.line` | `{P}§e#{n} §f{name} §a{wert}` | – | „[CityBuild] #1 Crones 346.260.911$“ | b |
| `cb.top.self` | `{P}§7Dein Platz: §e#{n} §7(§a{wert}§7)` | – | – | A |
| `cb.warp.tp` | `{P}§7Du wurdest zu §e{warp} §7teleportiert` | – | – | A |
| `cb.warp.unknown` | `{P}§cDen Warp §e{warp} §cgibt es nicht.` | – | – | A |
| `cb.warp.set` | `{P}§aDein Warp §e{warp} §awurde gesetzt.` | – | – | A |
| `cb.warp.limit` | `{P}§cDu kannst nur §e{n} §cWarp(s) setzen.` | – | – | A |
| `cb.warp.not_allowed` | `{P}§cHier darfst du keinen Warp setzen.` | – | Changelog Config | a |
| `cb.home.set` / `.tp` / `.limit` / `.forbidden` / `.deleted` / `.unknown` / `.list` | `{P}§aHome §e{name} §agesetzt.` / `{P}§7Teleport zu §e{name}` / `{P}§cDu hast bereits §e{n} §cHomes.` / `{P}§cHier kannst du kein Home setzen.` / `{P}§7Home §e{name} §7gelöscht.` / `{P}§cDieses Home gibt es nicht.` / `{P}§7Deine Homes: §e{liste}` | – | – | A |
| `cb.tpa.sent` | `{P}§7Anfrage an {nameColor}{name} §7gesendet` | – | „TPA-Anfrage an … gesendet“ | b |
| `cb.tpa.received` | `{P}{nameColor}{name} §7möchte sich zu dir teleportieren` | – | gleich (funktional) | b |
| `cb.tpa.received_here` | `{P}{nameColor}{name} §7möchte dich zu sich holen` | – | – | A |
| `cb.tpa.buttons` | `{P}§7Klicke hier: §a[ANNEHMEN] §c[ABLEHNEN]` | – | „Klicke hier zum: [ANNEHMEN] [ABLEHNEN]“ | b |
| `cb.tpa.accepted` | `{P}§7Du hast die Anfrage von {nameColor}{name} §7angenommen` | – | gleich (funktional) | b |
| `cb.tpa.accepted_by` | `{P}§7Deine Anfrage wurde von {nameColor}{name} §7angenommen` | – | gleich | b |
| `cb.tpa.countdown` | `{P}§7Du wirst teleportiert. Bewege dich §e{s} Sekunden §7nicht` | – | „Du wirst nun teleportiert. Bewege dich für 3 Sekunden nicht“ | b |
| `cb.tpa.cancelled` | `{P}§cDie Teleportation wurde abgebrochen` | – | gleich | b |
| `cb.tpa.toggle` | `{P}§7TPA-Anfragen: {an§a/aus§c}` | – | – | A |
| `cb.tpa.denied_space` | `{P}§cIm Weltall ist /tpa nicht möglich.` | – | Fandom | a |
| `cb.ec.locked` | `{P}§cDiese Endertruhe hast du noch nicht freigeschaltet.` | – | – | A |
| `cb.trade.request` | `{P}{nameColor}{name} §7hat dir eine Handelsanfrage geschickt!` | – | „… hat dir eine Anfrage zum Handeln geschickt!“ | b |
| `cb.trade.buttons` | `{P}§7Klicke hier: §a[ANNEHMEN]` | – | „Klicke hier zum: [ANNEHMEN]“ | b |
| `cb.trade.sent` / `.expired` / `.done` / `.cancelled` / `.money_level` / `.toggle` | `{P}§7Handelsanfrage an §e{name} §7gesendet.` / `{P}§cDie Handelsanfrage ist abgelaufen.` / `{P}§aHandel abgeschlossen.` / `{P}§cDer Handel wurde abgebrochen.` / `{P}§cGeld kannst du erst ab Level §e8 §chandeln.` / `{P}§7Handelsanfragen: {status}` | – | – | A |
| `cb.chat.locked` | `{P}§cDu kannst ab Level §e5 §cim Chat schreiben.` | – | Level 5 | a |
| `cb.chat.switched` | `{P}§7Du schreibst jetzt im Kanal §e{kanal}§7.` | – | – | A |
| `cb.job.joined` | `{P}§aDu arbeitest jetzt als §e{job}§a.` | – | – | A |
| `cb.job.quit` | `{P}§7Du hast als §e{job} §7gekündigt.` | – | – | A |
| `cb.job.locked` | `{P}§cDiesen Job gibt es ab Level §e{lvl}§c.` | – | – | A |
| `cb.job.only_farm` | `{P}§cJobs gibt es nur in den Farmwelten.` | – | Wiki | a |
| `cb.job.double` | `{P}§7Du hast ein §adoppeltes Item §7erhalten` (Actionbar) | – | „Du hast ein doppeltes Item erhalten“ | b |
| `cb.job.limit_xp` | `{P}§cDu hast das stündliche Limit an Erfahrungspunkten erreicht.` / `{P}§7Es setzt sich zu jeder vollen Stunde zurück.` | – | „Du hast das maximale Limit an Erfahrungspunkten pro Stunde erreicht. Dieses setzt sich jede volle Stunde zurück“ | b |
| `cb.job.limit_money` | `{P}§cDu hast das stündliche Limit an Geld erreicht.` | – | „… maximale Limit an Geld pro Stunde erreicht“ | b |
| `cb.job.tierup` | `{P}§aDu bist jetzt §e{stufe} §aals §e{job}§a!` | – | – | A |
| `cb.job.limit` | `{P}§7Stundenlimit §e{job}§7: §a{g}$§7/§a{gmax}$ §8| §e{x}§7/§e{xmax} XP §8| §7Reset in §e{mm} min` | – | – | A |
| `cb.job.stats` | `{P}§e{job} §8| §7Stufe §e{stufe} §8| §7Fortschritt §a{p} % §8| §e{jobxp} Job-XP §8| §a{geld}$` | – | Wiki-Inhalt | a |
| `cb.plot.claimed` | `{P}§aDu hast das Grundstück §e{id} §afür §e{preis}$ §agekauft.` | – | – | A |
| `cb.plot.taken` | `{P}§cDieses Grundstück gehört schon jemandem.` | – | – | A |
| `cb.plot.limit` | `{P}§cDu hast dein Plotlimit erreicht. §7(/limits)` | – | – | A |
| `cb.plot.cooldown` | `{P}§cDu kannst erst in §e{hh:mm} §cwieder ein Grundstück kaufen.` | – | – | A |
| `cb.plot.world_level` | `{P}§cDiese Plotwelt ist ab Level §e{lvl} §cbetretbar.` | – | – | A |
| `cb.plot.enter_title` | `§e{name}` / `§7Grundstück von §f{owner}` | – | „Grundstück von …“ | b |
| `cb.plot.enter_notify` | `{P}§e{name} §7hat dein Grundstück §e{id} §7betreten` | – | – | A |
| `cb.plot.info.*` | Block: `§7Besitzer: §e{owner}` · `§7ID: §e{id}` · `§7Alias: §e{alias}` · `§7Größe: §e{w}x{h} §7Teilplots` · `§7Level: §e{lvl}` · `§7Rollen: §e{n}/{max}` · `§7Löschung: §e{datum}` | – | – | A |
| `cb.plot.role.set` / `.removed` / `.full` / `.refused` | `{P}§e{name} §7ist jetzt §e{rolle}§7.` / `{P}§e{name} §7hat keine Rolle mehr.` / `{P}§cAlle §e{max} §cRollenplätze sind belegt.` / `{P}§c{name} nimmt keine Plot-Rollen an.` | – | – | A |
| `cb.plot.builder_owner_away` | `{P}§cDu kannst hier nur bauen, wenn der Besitzer in dieser Plotwelt ist.` | – | Regel belegt | a |
| `cb.plot.merge.ok` / `.limit` / `.shape` / `.not_owner` | `{P}§aGrundstücke verbunden (§e{n} §aTeilplots, §e{preis}$§a).` / `{P}§cIn Reihe §e{row} §csind höchstens §e{max}§c Teilplots erlaubt.` / `{P}§cDas Ergebnis muss ein Rechteck sein.` / `{P}§cDu musst beide Grundstücke besitzen.` | – | – | A |
| `cb.plot.mergelimits` | `{P}§7Reihe §e{von}-{bis}§7: max. §e{n} §7Teilplots` (je Zeile) | – | – | A |
| `cb.plot.row` | `{P}§7Dieses Grundstück liegt in Reihe §e{row}§7.` | – | – | A |
| `cb.plot.deleted` / `.cleared` | `{P}§7Grundstück §e{id} §7wurde gelöscht.` / `{P}§7Grundstück §e{id} §7wurde zurückgesetzt.` | – | – | A |
| `cb.plot.deleteinfo` | `{P}§7Löschung bei Inaktivität ab §e{datum} §7(zwischen 20 und 21 Uhr)` | – | Wiki-Inhalt | a |
| `cb.plot.level` | `{P}§7Grundstücks-Level: §e{lvl}` | – | – | A |
| `cb.plot.limits` | `{P}§7Plots: §e{p}/{pmax} §8| §7Teilplots: §e{t}/{tmax} §8| §7In dieser Welt: §e{l}/{lmax}` | – | – | A |
| `cb.plot.entitylimits` | `{P}§7{typ}: §e{n}/{max}` je Zeile | – | – | A |
| `cb.plot.offer.sent` / `.received` / `.accepted` / `.expired` / `.disabled` / `.toggle` | `{P}§7Angebot über §a{betrag}$ §7gesendet.` / `{P}§e{name} §7bietet §a{betrag}$ §7für §e{id}§7. §a[ANNEHMEN] §c[ABLEHNEN]` / `{P}§aGrundstück §e{id} §afür §a{betrag}$ §averkauft.` / `{P}§cDas Angebot ist abgelaufen.` / `{P}§cFür dieses Grundstück sind Angebote deaktiviert.` / `{P}§7Kaufangebote: {status}` | – | – | A |
| `cb.plot.move.*` | `start`: `{P}§7Wähle die Zielwelt und gehe zur Zielposition. Bestätige mit §e/plot move confirm§7.` · `confirm`: `{P}§aVerschiebung gestartet (§e{preis}$§a).` · `status`: `{P}§7Verschiebung: §e{p} %` · `done`: `{P}§aDein Grundstück wurde verschoben.` · `cancel`: `{P}§7Verschiebung abgebrochen.` · `blocked`: `{P}§cDie Zielfläche ist nicht frei.` | – | Wiki-Ablauf | a |
| `cb.plot.spawnplot_denied` | `{P}§cSpawnplots dürfen nicht privat sein.` | – | Wiki | a |
| `cb.plot.admin_locked` | `{P}§cDieses Grundstück ist durch das Team gesperrt.` | – | „Adminsperre“ | a |
| `cb.plot.restore.ok` / `.blocked` | `{P}§aGrundstück §e{id} §awiederhergestellt.` / `{P}§cDie Fläche ist inzwischen belegt.` | – | – | A |
| `cb.holo.created` / `.removed` / `.limit` / `.rank` | `{P}§aHologramm erstellt (§e{n}/{max}§a).` / `{P}§7Hologramm entfernt.` / `{P}§cHologramm-Limit erreicht (§e{max}§c).` / `{P}§cDafür brauchst du §6Premium§c.` | – | – | A |
| `cb.shop.created` / `.locked` / `.bought` / `.sold` / `.empty` / `.full` / `.limit` / `.tx_owner` / `.max` / `.toggle` | `{P}§aShop erstellt.` / `{P}§cDieser Shop ist noch §e{s}s §cgesperrt.` / `{P}§7Du hast §e{n}x {item} §7für §a{preis}$ §7gekauft.` / `{P}§7Du hast §e{n}x {item} §7für §a{preis}$ §7verkauft.` / `{P}§cDer Shop ist leer.` / `{P}§cDer Shop ist voll.` / `{P}§cDas Shoplimit ist erreicht.` / `{P}§e{name} §7hat §e{n}x {item} §7für §a{preis}$ §7{gekauft/verkauft}.` / `{P}§cDu hast bereits §e1.000 §cShops.` / `{P}§7Shop-Meldungen ({art}): {status}` | – | Mechanik belegt | A |
| `cb.market.sold` | `{P}§7Verkauft: §e{n}x {item} §7für §a{preis}$ §8(§7heute §a{s}$§7/§a{max}$§8)` | – | – | A |
| `cb.market.limit` | `{P}§cDein Tageslimit am Marktplatz (§a{max}$§c) ist erreicht.` | – | – | A |
| `cb.exchange.done` / `.limit` | `{P}§7Du hast §e{c} Coins §7in §a{g}$ §7getauscht.` / `{P}§cDu hast heute schon §e10.000 Coins §cgetauscht.` | – | – | A |
| `cb.guild.created` / `.name_taken` / `.name_invalid` / `.already` | `{P}§aGilde §e{name} §agegründet.` / `{P}§cDiesen Namen gibt es schon.` / `{P}§cErlaubt: 3–16 Zeichen, Buchstaben, Zahlen, _.` / `{P}§cDu bist bereits in einer Gilde.` | – | – | A |
| `cb.guild.invite.sent` / `.received` / `.full` | `{P}§7Einladung an §e{name} §7gesendet.` / `{P}§7Die Gilde §e{gilde} §7lädt dich ein. §a[ANNEHMEN] §c[ABLEHNEN]` / `{P}§cDie Gilde ist voll (§e{n}/{max}§c).` | – | – | A |
| `cb.guild.joined` / `.left` / `.kicked` / `.promoted` / `.demoted` / `.transferred` / `.deleted` / `.renamed` / `.founder_cant_leave` / `.denied` | `{G}§e{name} §7ist der Gilde beigetreten.` usw. (`{G}` = `§7[§cGilde§7] `) | – | – | A |
| `cb.guild.online` / `.offline` | `§7[§cGilde§7] §a{name} §7ist jetzt §aonline` / `… §7ist jetzt §coffline` | – | „[Gilde] X ist nun online/offline“ | b |
| `cb.guild.deposit` | `{P}§e{name} §7hat §a{betrag}$ §7in die Gilde eingezahlt.` | – | „X hat 100.000$ in die Gilde eingezahlt“ | b |
| `cb.guild.levelup` | `{P}§7Die Gilde ist jetzt Stufe §e{lvl}§7.` | – | „Das neue Level ist nun 18“ | b |
| `cb.guild.perk_unlocked` | `{P}§aNeuer Gildenvorteil: §7{vorteil}` | – | „Neue Gildenfunktion freigeschaltet: Du bekommst alle zwei Minuten +1 Minecraft-Erfahrungspunkt durch deine Gilde“ | b |
| `cb.guild.motd` | `{P}§aWillkommensnachricht der Gilde §b{gilde}§a:` / `§7{motd}` | – | „Willkommensnachricht für die Gilde HYPERS:“ | b |
| `cb.guild.top.line` | `{P}§e#{n} §f{gilde} §a{summe}$` | – | „#1 Crones 346.260.911$“ | b |
| `cb.guild.no_backpack` | `{P}§cGäste haben keinen Zugriff auf den Gildenrucksack.` | – | Matrix | a |
| `cb.talisman.started` / `.ready` / `.speedup` / `.bag_unlocked` / `.bag_upgraded` / `.combine_ok` / `.combine_fail` / `.only_talismans` / `.need_same_rarity` | `{P}§aHerstellung gestartet. Fertig in §e{h} Stunden§a.` / `{P}§aDein Talisman ist fertig! §7(/ts)` / `{P}§7Herstellung um §e4 Stunden §7verkürzt.` / `{P}§aTalisman-Beutel freigeschaltet.` / `{P}§aDein Beutel hat jetzt §e{n} §aPlätze.` / `{P}§aErfolg! Du erhältst {talisman}§a.` / `{P}§cKein Upgrade – du erhältst {talisman} §czurück.` / `{P}§cHier passen nur Talismane hinein.` / `{P}§cBeide Talismane brauchen dieselbe Seltenheit.` | – | – | A |
| `cb.collection.new` | `{P}§7Deiner Sammlung wurde ein neuer Kopf hinzugefügt` | – | gleich (funktional) | b |
| `cb.collection.detail` | `{P}§7Kategorie: §b{kat} §7Kopf: §e{kopf}` | – | gleich | b |
| `cb.collection.count` | `{P}§7Du hast jetzt §e{n}/{m} §7Köpfe der Kategorie §b{kat}` | – | „Du besitzt nun 23/32 Köpfen der Kategorie …“ | b |
| `cb.farm.pvp_off` | `{P}§7In dieser Farmwelt ist PvP ausgeschaltet` | – | „Auf dieser Farmwelt ist PvP deaktiviert“ | b |
| `cb.farm.invulnerable` | `{P}§7Du bist jetzt §e{s} Sekunden §7unverwundbar` | – | „Du bist nun für 16 Sekunden unverwundbar“ | b |
| `cb.farm.items_warn` | `{P}§7Herumliegende Items werden in §e{zeit} §7entfernt` | – | „Gedroppte Items werden in 1 Minute gelöscht“ | b |
| `cb.farm.items_done` | `{P}§7Herumliegende Items wurden entfernt` | – | „Items wurden gelöscht“ | b |
| `cb.farm.day` | `{P}§7Es ist jetzt Tag` | – | „Die Uhrzeit wurde auf Tag gesetzt“ | b |
| `cb.farm.reset_warn` | `{P}§cFarmwelt §e{n} §cwird in §e{min} Minuten §czurückgesetzt!` | – | – | A |
| `cb.farm.nether_level` | `{P}§cDen Nether kannst du ab Level §e6 §cbetreten.` | – | Wiki | a |
| `cb.space.gravity_warn` | `{P}§cVorsicht: Du bist zu nah am Schwerefeld der Erde. Steig höher, sonst stürzt du ab!` | – | „Achtung: Du befindest dich zu nah am Gravitationsfeld der Erde …“ | b |
| `cb.space.crashed` | `{P}§cDu bist der Erde zu nahe gekommen und abgestürzt!` | – | „Du bist zu nah an die Erde gekommen und durch das Gravitationsfeld dieser abgestürzt!“ | b |
| `cb.space.no_fuel` / `.no_oxygen` | `{P}§cDein Treibstoff ist leer!` / `{P}§cDir geht der Sauerstoff aus!` | – | – | A |
| `cb.space.invite` / `.locked` / `.no_pearls` | `{P}§e{name} §7lädt dich ins Weltall ein. §a[ANNEHMEN]` / `{P}§cDiese Zone ist noch gesperrt.` / `{P}§cEnderperlen funktionieren im Weltall nicht.` | – | – | A |
| `cb.boss.spawn` / `.killed` / `.loot` | `{P}§c{boss} §7ist erschienen!` / `{P}§a{boss} §7wurde besiegt. Bester Kämpfer: §e{name}` / `{P}§7Beute: §e{item}` | – | – | A |
| `cb.booster.activated` | `{P}§e{name} §7hat einen §d{typ}-Booster §7für §e{dauer} §7aktiviert!` | – | – | A |
| `cb.repair.ok` / `.cooldown` | `{P}§aItem repariert.` / `{P}§cWieder möglich in §e{mm} min§c.` | – | – | A |
| `cb.reward.claimed` / `.not_ready` | `{P}§aBelohnung erhalten: §e{item}` / `{P}§cDie nächste Belohnung gibt es in §e{zeit}§c.` | – | – | A |
| `cb.crate.won` | `{P}§7Du hast §e{item} §7gewonnen!` | – | – | A |
| `cb.tut.step` | Bossbar §4.4 | – | „Tutorial (15/34): Sprich mit den NPCs am Spawn /tutorial“ | b |
| `cb.tut.next` | `{P}§aAufgabe erledigt! §7Nächster Schritt: §f{text}` | – | „Aufgabe erledigt!“ (OCR) | b |
| `cb.tut.done` | `{P}§aTutorial abgeschlossen! §7Belohnung: §a5.000$ §7und §b1 Crate` | – | – | A |
| `cb.quest.done` | `{P}§aQuest erledigt: §e{quest}` | – | – | A |
| `cb.npc.citywatch` | `{P}§7Spieler meldest du mit §c/report <Spieler>§7, Fehler im Forum.` | – | Stadtwache | a |
| `smp.tpa.*` | wie `cb.tpa.*` mit `{S}` | – | „[SMP] … möchte sich zu dir teleportieren“ | b |
| `smp.combat.start` | `{S}§cDu bist jetzt im Kampf. Logge dich nicht aus!` | – | „Du bist nun im Kampf. Logge dich nicht aus!“ (funktional) | b |
| `smp.combat.end` | `{S}§7Du bist nicht mehr im Kampf und kannst dich ausloggen.` | – | „Du bist nun nicht im Kampf. Du kannst dich nun ausloggen!“ | b |
| `smp.combat.blocked` | `{S}§cDas geht im Kampf nicht.` | – | „Du kannst das im Kampf nicht benutzen“ | b |
| `smp.kill` | `{S}§7Du hast §e{name} §7getötet` | – | gleich | b |
| `smp.bounty.set` | `{S}§7Du hast ein Kopfgeld von §a{betrag}$ §7auf §e{name} §7ausgesetzt` | – | „Du hast Kopfgeld auf … ausgesetzt“ | b |
| `smp.bounty.set_target` | `{S}§e{name} §7hat §a{betrag}$ §7Kopfgeld auf dich ausgesetzt. Pass auf dich auf!` | – | „… in Höhe von … auf dich ausgesetzt. Nimm dich in Acht!“ | b |
| `smp.bounty.claimed` | `{S}§e{killer} §7hat das Kopfgeld auf §e{ziel} §7eingesammelt (§a{betrag}$§7)` | – | „Der Spieler X hat das Kopfgeld auf Y beansprucht (91.484,89$)“ | b |
| `smp.order.open` | `{S}§7Du hast noch §e{n} §7offene Bestellung(en) – §e/abholen` | – | „Du hast noch 1 offene Bestellungen - /abholen“ | b |
| `smp.wildcard.info` | `{S}§7Mit §e/wildcard <Spieler> §7kannst du unbegrenzt Wildcards vergeben` | – | gleich | b |
| `smp.sell.done` / `.limit` / `.info` | `{S}§7Verkauft für §a{netto}$ §8(§7Gebühr §c{f}$§8)` / `{S}§cLimit für §e{kat} §cerreicht. Reset in §e{mm} min§c.` / `{S}§e{kat}§7: §a{genutzt}$§7/§a{limit}$ §8| §7Reset §e{mm} min` | – | Mechanik belegt | A |
| `smp.playtime` | `{S}§7Spielzeit von §e{name}§7: §e{d}T {h}h {m}min` | – | – | A |
| `smp.trade.request` | `{S}{nameColor}{name} §7hat dir eine Handelsanfrage geschickt` + Buttons | – | „[SMP] X hat dir eine Anfrage zum Handeln geschickt“ | b |
| `smp.private` | `{S}{nameColor}{von} §7» §c{an}§7: §f{text}` | – | „[SMP] 3YYD >> Vorbild: …“ | b |

Gesamt: 140 Tabellenzeilen (Keys bzw. Key-Gruppen); Gruppen mit `/` einzeln gezählt ca. 220 Meldungen.

---

## 8. Schnittstellen

### 8.1 Genutzte Kern-Services (Spec 02)
| Service | Nutzung |
|---|---|
| `BoardService` | CB-/SMP-Board (§4.1/4.2), `showScores(era==LEGACY)`; Boss-Kampf-Override (Zeilen-IDs `dmg1..5`) |
| `TabService` | `setHeaderFooter`, Footer über `setServerFooter` (kein `TABLIST_OVERRIDE`) |
| `BossbarService` | Kanäle `tutorial`, `boss`, `plot`, `combat`, `event` |
| `ActionbarService` | Kanäle `news`, `space`, `job`, `combat`, `shop` |
| `TitleService`, `GuiService` (`ChestGui`, `PagedGui`, `ConfirmGui`), `HologramService`, NPC-API | §4–§5 |
| `ChatService` | `registerContext` (Kanalrouting §6.16), `prefix(CITYBUILD)` = `{P}` |
| `CoinService` | `debit(uuid, coins, Reason.CB_EXCHANGE, idemKey)` für Wechselbörse |
| `RankDisplayService` | Rangfarbe/-präfix, `isPremium/isSupremium/isTeam` aus LuckPerms-Meta |
| Wallet `gold` (Ledger `currency='GOLD'`) | Gold-Shop-Käufe (`reason='CB_GOLDSHOP'`) |
| `I18n` | alle Keys §7 |

### 8.2 Bereitgestellte Services (`net.retromc.citybuild.api`, Bukkit ServicesManager)
| Service | Methoden |
|---|---|
| `CbEconomy` | `long balance(UUID)`, `boolean debit(UUID, long cents, Reason, String idem)`, `void credit(…)`, `String format(long)` |
| `PlotService` | `Optional<Plot> at(Location)`, `Role roleOf(Plot, UUID)`, `boolean canBuild(Player, Location)`, `List<Plot> owned(UUID)`, `claim(...)`, `merge(...)`, `move(...)` |
| `LevelService` | `int level(UUID)`, `int prestige(UUID)`, `void addXp(UUID, long, XpSource)` |
| `JobService` | `Job job(UUID)`, `reward(Player, JobAction)` |
| `GuildService` | `Optional<Guild> of(UUID)`, `deposit(UUID, long)`, `perks(Guild)` |
| `TalismanService` | `double value(Player, TalismanType)` (summiert Beutel) |
| `BoosterService` | `boolean active(BoosterType)`, `activate(type, Duration, UUID)` |
| `CustomItems` | `ItemStack create(String key, int amount)`, `Optional<String> keyOf(ItemStack)` |
| `TutorialService` | `void progress(Player, TutorialTrigger)` |
| `SmpCombat`, `SmpBounty`, `SmpEconomy` | analog für SMP |

### 8.3 Events (`net.retromc.citybuild.event`)
`CbMoneyChangedEvent(uuid, old, new, reason)` · `CbLevelUpEvent(player, oldLvl, newLvl, prestige)` · `CbXpGainEvent(player, amount, source)` (cancellable, modifizierbar) · `JobRewardEvent(player, job, action, money, xp)` (cancellable) · `PlotClaimEvent`, `PlotEnterEvent(player, plot)`, `PlotLeaveEvent`, `PlotRoleChangeEvent`, `PlotDeleteEvent(plot, reason)`, `PlotMergeEvent` · `ShopTransactionEvent` · `GuildDepositEvent`, `GuildLevelUpEvent` · `BossSpawnEvent`, `BossKilledEvent(boss, Map<UUID,Double> damage, side)` · `CollectionHeadEvent` · `TalismanChangeEvent` · `BoosterActivatedEvent` · `TutorialStepEvent(player, step)` · SMP: `CombatTagEvent`, `BountyClaimEvent`.

### 8.4 Messaging (Valkey Pub/Sub, Envelope Spec 08 §6.12)
| Kanal | Typ | Payload | Sender → Empfänger |
|---|---|---|---|
| `net:cb-chat` | `ChatMessage` | `{ch, from, name, level, prestige, rankColor, text, target?, plotId?, guildId?}` | jeder CB-Server → alle CB-Server |
| `net:cb-events` | `MoneyChanged` | `{uuid, balance}` | → Board-Refresh auf Server des Spielers |
| `net:cb-events` | `PlotChanged` | `{plotId, fields[]}` | → Cache-Invalidierung auf PLOT-Servern |
| `net:cb-events` | `GuildChanged` / `GuildBroadcast` | `{guildId, type, args}` | → alle CB-Server (Online/Offline, Deposit) |
| `net:cb-events` | `BoosterChanged` | `{type, endsAt}` | → alle CB-Server (Tab, Effekte) |
| `net:cb-events` | `FarmReset` | `{n, phase}` | Scheduler (Lobby-Leader) → FARM n, Lobby |
| `net:cb-events` | `RotationChanged` | `{shop, period}` | Scheduler → LOBBY |
| `net:proxy` | `CONNECT` | `{uuid, server}` | CB/SMP → Proxy (Server-Wechsel, Warp/Home/TPA) |
| `net:profile` | `ProfileChanged` | `fields:["gold","coins"]` | nach Gold-/Coin-Buchung |
| `net:smp-events` | `BountyClaimed`, `OrderCreated` | | SMP-Server untereinander |
Teleport-Übergabe zwischen Servern: Ziel als `cb:pending_tp:{uuid}` (HASH, TTL 30 s) setzen, dann `CONNECT`; Zielserver liest beim Join.

### 8.5 Pflichten anderer Bereiche
- **Proxy (Spec 01):** Server-Gruppen `citybuild` (Lobby als Einstieg) und `smp` (Lobby + Welt-Queue); `/gameteleport citybuild|smp`; Rang-Stufen aus Spec 01 §6.1 (`RankService`); Social-Clan-Daten für SMP-Scoreboard.
- **Kern-UI (Spec 02):** Bossbar-Kanäle `tutorial`/`boss`/`plot`/`combat`, Board `showScores` je Board, `PagedGui`, `HologramService` mit TextDisplay.
- **Spiel-Rahmen:** keine Abhängigkeit (CityBuild/SMP sind dauerhafte Server, kein `MatchResult`). Coins aus Minispielen fließen nur über die Wechselbörse ein.
- **Lobby (Netzwerk-Hub):** Kompass-Eintrag CityBuild (`crafting_table`, `§e§lCityBuild`) und SMP; Newsticker-Einträge.

---

## 9. Abnahmekriterien

1. Scoreboard MODERN zeigt exakt 13 Zeilen (§4.1), ohne Score-Zahlen; LEGACY mit `:` und Score-Zahlen 13…0.
2. Geldformat: Unit-Test `format(342828888048)` = `3.428.288.880,48$`, `format(463015830)` = `4.630.158,3$`, `format(10000000)` = `100.000$`.
3. Plotpreis: Unit-Tests `P(1)=3.000`, `P(40)=61.500`, `P(41)=61.500`; Stufe 2 Max 71.500, Stufe 3 Max 151.500; Supremium ×0,95.
4. Reihe: Unit-Test `row(0,0)=1`, `row(-1,0)=1`, `row(4,-2)=5`; Merge-Limit Reihe 4 = 1, 5 = 4, 12 = 9, 13 = 36.
5. Hologramm-Limit: 1×1 = 3, 2×2 = 13, 6×6 = 133 (Unit-Test).
6. Builder kann nur bauen, wenn der Owner (Presence) in derselben Plotwelt ist; Integrationstest mit 2 Spielern.
7. Rollen-Matrix: parametrisierter Test aller Matrixzeilen (§6.3.4, Sammelzeilen je Befehl aufgelöst) × 5 Rollen gegen `PlotPermissions.check`.
8. Inaktivität: Unit-Test Frist(L0)=28, (L10)=60, (L12)=90, (L25)=180; Premium ×1,5; Löschlauf nur 20:00–21:00.
9. Shop-Füllfarbe: 0 % `§4`, 19 % `§c`, 49 % `§e`, 94 % `§a`, 95 % `§2`; nach Preisänderung 30 s keine Transaktion.
10. Max. 1.000 Shops je Spieler; Rolle entfernen deaktiviert alle Shops des Spielers auf dem Plot.
11. Gilden-Kostenkurve: `T(2)=1.000`, `T(3)=4.658`, `T(220)=156.958.895`, `T(313)=344.368.325` (Unit-Test); Level aus Einzahlung korrekt; Summen der Vorteile +37/+45/25 %/0,20 %/30.
12. Gildenrechte: Moderator darf Mitglied kicken, nicht Moderator; Gast hat keinen `/backpack`.
13. Talisman-Beutel: Preis(6)=25.000, Fragmente(6)=3, Preis(36)=275.000, Fragmente(36)=13, Summe 1.650.000/88 (Unit-Test); GUI-Reihen 3 bei 3 Plätzen, 4 bei 12.
14. Herstellung: 125.000$/115.000$/100.000$ je Rang; Verkürzung 50.000/40.000/30.000$; parallele Slots 1/2/3.
15. Marktplatz: L0-Limit 1.000$, mit Booster 1.300$; L25 3.000$; Reset 00:00.
16. Wechselbörse: 10.001 Coins am Tag abgelehnt; 10.000 Coins → 1.000$; Coins-Abbuchung idempotent.
17. Job-Stundenlimit greift, Meldung einmal je Stunde, Reset zur vollen Stunde (Clock-Mock-Test).
18. Job des Monats: XP ×1,35; Nebenjob 50 %, nicht im Ranking.
19. Level-Kurve: `need(0)=100`, `need(99)=29.503`; Prestige 1 ×2, 2 ×10, 3 ×100; Prestige nur bei Level 100.
20. Level-Up-Meldungen erscheinen genau bei den Leveln aus §6.4.
21. Chat: unter Level 5 kein Global/Trade/Private (außer Buddy); Global erreicht alle CB-Server (2-Server-Test).
22. Tablist zeigt aktuellen Servernamen und Booster-Restzeiten, aktualisiert ≤ 1 s.
23. Tutorial-Bossbar `Tutorial (n/34)` mit Fortschritt n/34; Schritt 15 erst nach 5 NPCs.
24. Farmwelt: Elytra wird bei Landung entfernt und Brustplatz wiederhergestellt; 16 s Unverwundbarkeit; Redstone ohne Wirkung; Eisenerz mit Fortune III droppt 1 Roheisen.
25. Farmwelt-Reset Do 18:00 nacheinander; danach neuer Seed, Homes in der Welt gelöscht.
26. Weltall: Enderperle wird blockiert, `/tpa` und `/home set` abgelehnt; Treibstoff 0 → Absturzmeldung; Actionbar-Balken 20 Zeichen.
27. Boss-Schadensliste summiert Schaden je Spieler, Format `0.00`, Rang korrekt; „Bosse getötet“ zählt keine Nebenbosse.
28. Handel: Änderung nach Bestätigung setzt beide zurück; Tausch atomar (Rollback bei vollem Inventar).
29. Crate-Vorschau zeigt Chancen, Summe = 100 %.
30. Booster gleichen Typs addieren Dauer.
31. Geld-Konsistenz: Summe `cb.ledger.delta_c` je Spieler = `money_c` (nächtlicher Prüfjob, Test).
32. SMP-Board: Geld/Kills/Tode/Clan/Hilfe; Kill erhöht Kills/Tode sofort.
33. SMP-Kampf: 15 s; `/spawn` im Kampf → `smp.combat.blocked`; Logout im Kampf → Tod.
34. SMP-Kopfgeld: Summe zweier Geber korrekt, Killer erhält Betrag, Broadcast `smp.bounty.claimed`; GUI sortiert absteigend.
35. SMP-`/sell`: Gebühr 10 % abgezogen, Kategorie-Limit, Reset 3.600 s nach erstem Verkauf.
36. Alle Nachrichten-Keys aus §7 existieren in `de_DE` (Test: Katalog-Vollständigkeit, keine fehlenden Platzhalter).

---

## 10. Offene Punkte (je mit DEFAULT-ENTSCHEIDUNG)

| # | Offener Punkt / Widerspruch | DEFAULT |
|---|---|---|
| 1 | Talisman-Herstellung 125.000$ (GUI) vs. 150.000$ (Wiki-Text) | 125.000$, `talisman.baseCost` konfigurierbar |
| 2 | Marktplatz 3.000$ ab Level 25 (Level-Tabelle) vs. 30 (Geld-Seite) | Level 25, konfigurierbar |
| 3 | Plotpreis-Kurve, Merge-/Move-Gebühr, Plotlimits ab Level unbelegt (nur Deckel 61.500 und alter Forumswert 42/74) | §6.3.1–§6.3.3 Werte, alle in `plots.yml` |
| 4 | Claim-Abklingzeit: was genau 24 h (vorher 48 h) begrenzt | 1 Claim je 24 h ab dem 2. Plot; LEGACY 48 h |
| 5 | Level-XP-Kurve, Job-Werte, Stundenlimits, Stufenschwellen unbelegt | §6.4/§6.6, in `jobs.yml`/`levels.yml` |
| 6 | Liste der 27 Talismane und Wahrscheinlichkeiten unbelegt (nur 3 Effekte) | Tabelle §6.10.1, `talismans.yml` |
| 7 | Gilden-Beispiel (32 Mitglieder / 0,25 Schatz) vs. Tabelle (37 / 0,20) | Tabelle |
| 8 | „Guilds Reborn“ 2026: eigene Rollen, neue Belohnungen, Einstellungen – Inhalt unbekannt | Tabelle `cb.guild_role` vorhanden, Feature-Flag `guild.customRoles=false`; Standardrollen wie Wiki |
| 9 | Chat-Präfixfarben je Ära abweichend ([Gilde] §c / §e, [Plot] §9 / §d, Level mit/ohne Klammern) | MODERN wie §6.16; LEGACY ohne Klammern, [Gilde] §e |
| 10 | Prestige-Glyph: Original nutzt Resourcepack-Icon (rundes Sternabzeichen) | Unicode `✪` §e; Option `cb.prestige.glyph` |
| 11 | Boss-Schadensliste unten links (vermutlich Resourcepack-HUD) | Sidebar-Override während des Kampfs; Option `RESOURCEPACK_HUD` später |
| 12 | Weltall-Zonen: Kosten, Bosse der Zonen 2/3, Jetpack-Steuerung unbelegt | §6.12 Werte, `space.yml` |
| 13 | NPC „Clementius“ (persönlicher Shop „für deine Heimatdimension“) unklar | NPC RAUMHÄNDLER mit Wochenangebot für Weltall-Materialien, Feature-Flag aus |
| 14 | „Buddy“-System (NPC, Chat ohne Level 5) nur aus Chat-Fetzen bekannt | Buddy = gegenseitige Bestätigung, max. 3; Buddys dürfen sich privat schreiben unter Level 5; sonst keine Vorteile |
| 15 | Plot-Level-Erwerb unbelegt | `floor(Besitztage/7)`, max. 30 |
| 16 | Bodyguards (Rollenrecht belegt, Funktion unbekannt) | v1 nicht umgesetzt; Recht reserviert |
| 17 | Adminshop-Preise/-Bestände, Mall-/Möbelhaus-Kataloge, Crate-/Truhen-Inhalte unbelegt | Seed-Dateien `rotation/*.yml`, `crates/*.yml` mit Annahmen §6.7/§6.15 |
| 18 | Endertruhen-Preise im Tresor unbelegt | 5.000/15.000/30.000/60.000/120.000/250.000$ |
| 19 | Premium-Homes (Wiki: 3 vs. „5 zusätzliche“ für Supremium) | Spieler 3, Premium 3, Supremium 8 |
| 20 | Shop-Schildzeilen „Buy/Sell“ (Clip) vs. deutsche Oberfläche | `K {p}$` / `V {p}$`, Option `shop.signStyle=EN` |
| 21 | SMP: Claims, Weltgröße, Homes-Zahl, Verkaufsgebühr, Limitwerte, `/wildcard`-Zweck unbelegt | keine Claims; Border 20.000; 3 Homes (+5 Supremium); Gebühr 10 %; 10.000$/h je Kategorie; Wildcard = Warteschlangen-Priorität |
| 22 | SMP-Kampfdauer und Logout-Strafe unbelegt | 15 s, Logout = Tod |
| 23 | Nether geteilt oder je Farmwelt | je Farmwelt-Server eigener Nether, Reset mit der Farmwelt |
| 24 | Bossnamen, Weltnamen, NPC-Namen des Originals sind markant | eigene Namen §6.2/§6.11/§4.8; Originale nur in Doku |
| 25 | Paketname | erledigt: `net.retromc.citybuild` ist im Gerüst umgesetzt (Marke RetroMC, Nutzer-Entscheidung); keine Umbenennung |
| 26 | Server-Aufteilung der Plotwelten unbekannt | `plotworlds.yml`: Dev alle 11 auf `cb-plot-1`; Produktion 3 Server (Stufe-1-Hälfte A, Hälfte B, Stufe 2+3) |
| 27 | Kein `/pay` belegt | kein `/pay`; Geldtransfer nur `/trade` (ab L8) und Shops |
| 28 | Tutorial: nur 3 von 34 Schritten belegt (9, 10, 15) | Liste §6.17, `tutorial.yml` |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | Pakete `dev.netzwerk.*` mit Umbenennungs-Hinweis, Brand `<brand>` = „Netzwerk“, PDC `netzwerk:ci`, Nodes `netzwerk.*` | `net.retromc.*` (Gerüst), `<brand>` = „RetroMC“, `retromc:ci`, `retromc.cb.*`/`retromc.smp.*` |
| K2 | Migration `V7__citybuild_smp` | `V7_1__citybuild_smp.sql` |
| K3 | Tablist-Footer über `TABLIST_OVERRIDE` an den Proxy (Spec 02 rendert selbst) | `TabService.setServerFooter` |
| K4 | Kanäle `cb:chat`, `cb:events`, `smp:events` außerhalb der `net:*`-Konvention; Envelope „Spec 01“ | `net:cb-chat`, `net:cb-events`, `net:smp-events`; Envelope Spec 08 §6.12 |
| K5 | eigener AFK-Key `cb:afk` neben `presence.afk` (Spec 01) | `presence:{uuid}.afk` |
| K6 | Servernamen `cb-end`, `smp-lobby` ohne Nummer; Spec 08 kannte nur Template `citybuild` | `{template}-{n}`; Templates `cb-*`/`smp-*` in Spec 08 §6.5 |
| K7 | Rang-Meta `rank.premium`/`rank.supremium` existiert in Spec 01 nicht | Gewichtsstufen aus Spec 01 §6.1 |
| K8 | Scoreboard-Titel ohne `.NET` | `§f§l<BRAND_UPPER>.NET` |
