# Spec 08: Infrastruktur, Dienste & Betrieb

Stand 2026-10-21 · Bereich: Gradle-Module, Pakete, Plugin-Lebenszyklus, Server-Controller (Templates, Start/Stopp, Namen, Lobby-Balancing, Zuteilung), Messaging, Flyway, Map-Pipeline, API-Dienst, Discord-Bot, Docker-Compose, Tests, CI, Versionen, Recht.
Quellen: **N07** = Notiz 07 (Stack/Recht), **N08** = Notiz 08 (GommeHD-Quellcode), **ARCH** = `docs/ARCHITEKTUR.md`, **VER** = `docs/VERSIONEN.md`, **SET** = `settings.gradle.kts`. Zum Abgleich der Schnittstellen: **S01**, **S02**, **S05**, **S06** (Nachbar-Specs). Punktuell per Suche: N15 §1.1 und N17 Z. 83 (Tablist-Header, Servertypen).
Beleg-Stufen: **belegt** (steht so in Quelle/Code) · **abgeleitet** (aus Belegen geschlossen) · **Annahme** (festgelegt, ohne Beleg).
Grundsatz: GommeHDs Cloud, Controller, Messaging und Plugins sind **nicht öffentlich** (N08 §9, §11). Fast alle Abläufe hier sind daher eigene Festlegungen (Annahme) auf Basis der Empfehlungen in N07. Belegt sind nur die Randbedingungen: Proxy/Backend-Kopplung, Versionen, Build-Kette und Servertypen.

---

## 1. Umfang & Module

### 1.1 Gradle-Module (aus SET; NEU = fehlt dort bzw. neu anzulegen)

| Modul | Status | Paket | Artefakt / Plugin-Name | Abhängigkeiten | Inhalt aus diesem Bereich |
|---|---|---|---|---|---|
| `:core:core-api` | vorhanden | `net.retromc.core.api` (+ `.server`, `.map`, `.msg`) | Bibliothek | – | `ServerInfo` erweitern (§2.3), `ServerState` erweitern (§6.1), `ServerCategory`, `TemplateId`, `MapMeta`, `Envelope`, `BuildInfo` |
| `:core:core-data` | vorhanden | `net.retromc.core.data` (+ `.infra`) | Bibliothek | core-api, HikariCP, postgresql, lettuce | `ValkeyKeys` erweitern (§2.2), `ServerInstanceRepository`, `MapRepository`, `AdminActionRepository`, `DiscordLinkRepository`; **testFixtures** mit Testcontainers (§6.16) |
| `:core:core-messaging` | vorhanden | `net.retromc.core.messaging` | Bibliothek | core-api, core-data, **Gson (NEU)** | `Envelope`-Codec, `NetworkChannel` um Kanäle aus §2.4 erweitern, `RequestReply` (Korrelation, Timeout) |
| `:core:core-i18n` | vorhanden | `net.retromc.core.i18n` | Bibliothek | – | Keys aus §7 |
| `:platform:paper-core` | vorhanden | `net.retromc.paper.core` + `.lifecycle`, `.registry`, `.transfer`, `.restart`, `.map` | Paper-Plugin `RetroMCCore` | paper-api (compileOnly), alle core-* (Shadow) | Lebenszyklus §6.2, Heartbeat, Spieler-Transfer, Neustart-Countdown, `MapService` |
| `:platform:velocity-core` | vorhanden | `net.retromc.velocity.core` + `.registry`, `.balance`, `.command.infra` | Velocity-Plugin, ID `retromc-core` | velocity-api (compileOnly), alle core-* (Shadow) | dynamische Server-Registrierung, Lobby-Balancer, `/server`, `/send`, `/whereami`, `/glist`, `/cloud` (Weiterleitung) |
| `:game:game-framework` | vorhanden | `net.retromc.game.framework` | Bibliothek (eingebettet) | paper-api, core-api (compileOnly) | nutzt `ServerLifecycle`, `MapService` (§8) |
| `:game:*`, `:lobby`, `:citybuild` | vorhanden | `net.retromc.<modul>` | `RetroMC<Modus>` | paper-core (compileOnly) | nichts Eigenes; liefern `mapschema/<mode>.json` (§6.11) |
| `:services:controller` | vorhanden | `net.retromc.controller` + `.template`, `.launcher`, `.scaling`, `.allocation`, `.registry`, `.maps`, `.jobs` | Java-Anwendung `controller` | core-api, core-data, core-messaging, Flyway, SnakeYAML (**NEU**) | Kern dieses Bereichs (§6.4–6.10) |
| `:services:api` | vorhanden | `net.retromc.api` | Java-Anwendung `api` | core-api, **core-data (NEU)**, Gson | REST-API §6.14 (JDK-`HttpServer` beibehalten) |
| `:services:discord-bot` | vorhanden | `net.retromc.discord.bot` | Java-Anwendung `discord-bot` | core-api, **core-data, core-messaging, JDA (NEU)** | Verknüpfung, Rollen-Sync, `/status` §6.13 |
| `:tools:map-exporter` | vorhanden | `net.retromc.map.exporter` | Java-Anwendung `map-exporter` | core-api, **core-data (NEU, nur `upload`)**, Gson | validate/pack/upload §6.11 |
| `deploy/templates/` | **NEU** (kein Gradle-Modul) | – | YAML je Template | – | Server-Templates §6.5 |
| `deploy/docker/` | **NEU** | – | Dockerfiles `paper-base`, `velocity`, `service` | – | Produktiv-Images §6.17 |
| `deploy/k8s/` | **NEU, später** | – | Agones-Fleets | – | Phase 2 (§10) |
| `config/checkstyle/`, `.editorconfig`, `renovate.json` | **NEU** | – | – | – | Stilprüfung, Abhängigkeits-PRs (N08 §10.2, N07 §6) |

Kein neues Gradle-Modul nötig. Testhilfen liegen als `java-test-fixtures` in `core-data` (Entscheidung: kein Modul `:testing`).

### 1.2 Widersprüche zwischen Quellen → Entscheidung

| # | Widerspruch | Entscheidung |
|---|---|---|
| W1 | Marke/Pakete: ältere Spec-Entwürfe nannten `dev.netzwerk` / `brandName=Netzwerk` | **Nutzer-Entscheidung, bereits im Gerüst umgesetzt: `brandName=RetroMC`, `group=net.retromc`, Pakete `net.retromc.*`, Paper-Plugin `RetroMCCore` (= `${brandName}Core`), Velocity-ID `retromc-core`.** Kein Umbau. Die frühere Entscheidung „Rückbenennung auf dev.netzwerk“ ist verworfen |
| W2 | Flyway-Versionen kollidierten (mehrere Specs nutzten `V2__…`; Flyway bricht bei doppelter Version ab) | Schema **`V<Spec>_<lfd>__<name>.sql`** (Flyway-Version `<Spec>.<lfd>`), in allen Specs durchgezogen: `V1_1__proxy_social`, `V1_2__seed_reasons`, `V2_1__core_ui_stats`, `V3_1__lobby_game`, `V4_1__bedwars_skywars`, `V5_1__klassiker`, `V6_1__minigames`, `V7_1__citybuild_smp`, `V8_1__infra`. `V1__init` bleibt |
| W3 | Server-Status: ARCH/S01 `server:<name>` (S01 erwartet `state=ONLINE`), S05 `game:<server>:state` (TTL 30 s), Code-Enum nur `WAITING/STARTING/INGAME/ENDING` | ein Schlüssel **`server:{name}`**, Enum erweitert (§6.1), Heartbeat 5 s, **TTL 15 s**. `game:<server>:state` aus S05 entfällt |
| W4 | Feldnamen Server-Status: S05 `players`, S06/Code `online` | **`online`** |
| W5 | N07: Kubernetes + Agones als Ziel. ARCH: k8s fehlt, Dev mit Docker Compose | Schnittstelle `ServerLauncher`: **Dev/Staging `DockerCliLauncher`** (Standard), `StaticLauncher` für feste Compose-Server, `AgonesLauncher` erst Phase 2 |
| W6 | Velocity-Konfig im Gerüst: `enable-player-address-logging = true`. N08 (IP-freie Logs), N07 §9 E (DSGVO) | **`false`** |
| W7 | N07: Valkey Streams für „at-least-once“. ARCH/Code: nur Pub/Sub | **Pub/Sub** überall. Verlust wird durch Heartbeats, Resync und Ticket-Timeouts aufgefangen (§6.12). Streams sind Option, wenn Messwerte Verlust zeigen |

---

## 2. Datenmodell

### 2.1 PostgreSQL – NEU `db/migrations/V8_1__infra.sql`

```sql
CREATE SCHEMA IF NOT EXISTS infra;

CREATE TABLE infra.server_instance (           -- Historie jeder gestarteten Instanz
  id            UUID PRIMARY KEY,
  name          VARCHAR(48)  NOT NULL,          -- z. B. lobby-3, bedwars-4x4-12
  template      VARCHAR(32)  NOT NULL,
  category      VARCHAR(12)  NOT NULL,          -- LOBBY | GAME | PERSISTENT
  launcher      VARCHAR(12)  NOT NULL,          -- STATIC | DOCKER | AGONES
  host          VARCHAR(64)  NOT NULL,
  port          INT          NOT NULL CHECK (port BETWEEN 1 AND 65535),
  build         VARCHAR(48),                    -- BuildInfo.VERSION + Git-SHA
  map_id        VARCHAR(32),
  map_version   INT,
  started_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  ready_at      TIMESTAMPTZ,
  stopped_at    TIMESTAMPTZ,
  stop_reason   VARCHAR(24),                    -- MATCH_END|IDLE|DRAIN|ADMIN|BOOT_TIMEOUT|HEARTBEAT_LOST|CRASH|MAX_LIFETIME|RESTART
  matches       INT          NOT NULL DEFAULT 0,
  peak_online   SMALLINT     NOT NULL DEFAULT 0);
CREATE INDEX server_instance_tpl_idx  ON infra.server_instance (template, started_at DESC);
CREATE UNIQUE INDEX server_instance_live_name ON infra.server_instance (name) WHERE stopped_at IS NULL;

CREATE TABLE infra.map (
  id            VARCHAR(32)  PRIMARY KEY CHECK (id ~ '^[a-z0-9_]{2,32}$'),
  mode          VARCHAR(24)  NOT NULL,
  display_name  VARCHAR(24)  NOT NULL,
  variants      TEXT[]       NOT NULL DEFAULT '{}',
  builders      TEXT[]       NOT NULL DEFAULT '{}',
  min_players   SMALLINT     NOT NULL,
  max_players   SMALLINT     NOT NULL CHECK (max_players >= min_players),
  enabled       BOOLEAN      NOT NULL DEFAULT false,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now());
CREATE INDEX map_mode_idx ON infra.map (mode) WHERE enabled;

CREATE TABLE infra.map_version (
  map_id        VARCHAR(32)  NOT NULL REFERENCES infra.map ON DELETE CASCADE,
  version       INT          NOT NULL CHECK (version >= 1),
  sha256        CHAR(64)     NOT NULL,
  size_bytes    BIGINT       NOT NULL,
  storage_key   TEXT         NOT NULL,          -- maps/{mode}/{id}/{version}.zip
  meta          JSONB        NOT NULL,          -- vollständige map.json
  uploaded_by   VARCHAR(64)  NOT NULL,          -- Git-Autor oder Staff-Name
  uploaded_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  active        BOOLEAN      NOT NULL DEFAULT false,
  PRIMARY KEY (map_id, version));
CREATE UNIQUE INDEX map_version_one_active ON infra.map_version (map_id) WHERE active;

CREATE TABLE infra.admin_action (               -- Audit für /cloud, /send, Map-Schalter
  id            BIGSERIAL    PRIMARY KEY,
  actor         UUID,                           -- NULL = System/CLI
  action        VARCHAR(24)  NOT NULL,          -- START|STOP|DRAIN|RESTART|SCALE|RELOAD|MAP_ENABLE|MAP_DISABLE|SEND
  target        VARCHAR(64)  NOT NULL,
  args          JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now());
CREATE INDEX admin_action_time_idx ON infra.admin_action (created_at DESC);

-- Discord (Tabelle integ.discord_link existiert aus V1__init: uuid PK, discord_id BIGINT UNIQUE, linked_at)
ALTER TABLE integ.discord_link
  ADD COLUMN nick_sync       BOOLEAN     NOT NULL DEFAULT true,
  ADD COLUMN roles_synced_at TIMESTAMPTZ;
CREATE TABLE integ.discord_role_map (
  lp_group        VARCHAR(32) PRIMARY KEY,      -- LuckPerms-Gruppe (S01 §Ränge)
  discord_role_id BIGINT      NOT NULL UNIQUE);

CREATE TABLE integ.api_token (                  -- interne/Staff-Zugriffe auf services:api
  id            BIGSERIAL    PRIMARY KEY,
  name          VARCHAR(48)  NOT NULL,
  token_sha256  BYTEA        NOT NULL UNIQUE,   -- nur Hash speichern
  scopes        TEXT[]       NOT NULL,          -- z. B. {status:read, chatlog:read, appeal:write}
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  last_used_at  TIMESTAMPTZ,
  revoked_at    TIMESTAMPTZ);
```

Aufbewahrung (Job im Controller, §6.10): `server_instance` 90 Tage nach `stopped_at`; `admin_action` 365 Tage; `map_version` mit `active=false` bleibt (für Replays/Reports nachvollziehbar), Löschung nur per CLI.

### 2.2 Valkey-Schlüssel

| Schlüssel | Typ | Felder / Inhalt | TTL | Schreiber → Leser |
|---|---|---|---|---|
| `server:{name}` | HASH | `name, template, mode, variant, category, state, online, max, reserved, map, host, port, minProtocol, joinable(0/1), silent(0/1), rankMin, privateRound(0/1), versionLabel, displayName, matchId, startedAt, build`; Zusatzfelder Spiel-Rahmen (S03): `countdown` (s, −1), `spectatable(0/1)`, `teams`, `teamSize`, `privateId`, `owner`. Bei Zustands-/Spielerzahlwechsel sofort geschrieben, in STARTING `countdown` jede Sekunde | **15 s**, Heartbeat alle 5 s | Server → Velocity, Controller, Lobby (NPCs/Navigator), API, Bot |
| `servers:all` | SET | Servernamen | – (Controller entfernt abgelaufene alle 5 s) | Server (SADD beim Start) → alle |
| `servers:tpl:{template}` | SET | Servernamen je Template | – (wie oben) | Server → Controller, Lobby |
| `reserve:{server}` | ZSET | `uuid → expiresAtMillis` | 30 s (Key), Einträge 30 s | Controller → Spielserver (Login-Prüfung) |
| `proxy:{id}` | HASH | `online, startedAt, build` | 15 s, Heartbeat 5 s | Velocity → Controller, API |
| `proxies` | SET | Proxy-IDs | – (Controller räumt) | Velocity → alle |
| `count:online` | STRING | Summe `online` aller Proxys | 15 s, alle 5 s | Controller → MOTD (S01), API, Bot |
| `ctl:leader` | STRING | Instanz-ID des aktiven Controllers | 10 s, `SET NX PX 10000`, Verlängerung alle 3 s | Controller |
| `ctl:names:{template}` | SET | belegte laufende Nummern | – | Controller (nur Leader) |
| `ctl:override:{template}` | HASH | `minReady, max` aus `/cloud scale` | bis `/cloud reload` | Controller |
| `ticket:{id}` | HASH | `players(csv), mode, variant, partyId, createdAt, state, source` (`NPC`/`SPECTATOR_NEXT`/`SIGN`, S03) | 60 s | Controller |
| `maps:{mode}` | HASH | `mapId → aktive Version` | – (neu bei Änderung) | Controller/map-exporter → Spielserver |
| `restart:{server}` | HASH | `at(epochMillis), reason, actor` | bis Ausführung + 60 s | Controller → Server |
| `discord:code:{code}` | STRING | uuid | **600 s** | paper-core → Bot |
| `discord:pending:{uuid}` | STRING | code (höchstens ein offener Code je Spieler) | 600 s | paper-core |
| `rl:discordcode:{uuid}` | STRING | Zähler | 600 s, max 5 | paper-core |
| `rl:api:{ipHmac}` | STRING | Zähler | 60 s, max 60 | API |
| `cache:api:{path}` | STRING | JSON-Antwort | 30 s | API |

Bestehend und unverändert (ARCH, S01, S02): `presence:{uuid}` (60 s), `queue:{variant}` (ZSET), `party:*`, `profile:{uuid}`, `lb:*`, `maintenance`.

### 2.3 `ServerInfo` (core-api, erweitert)

`record ServerInfo(String name, String template, String mode, String variant, ServerCategory category, ServerState state, int online, int max, int reserved, String map, String host, int port, int minProtocol, boolean silent, int rankMin, boolean privateRound, String displayName, String versionLabel, String matchId, Instant startedAt)`, dazu die Methoden `freeSlots()` = `max(0, max − online − reserved)` und `joinable()` (§6.7). Serialisierung: HASH-Felder 1:1, Zahlen als Dezimal-Strings, Booleans als `0/1`.

### 2.4 Kanäle (Pub/Sub, alle mit Envelope §6.12)

| Kanal | Status | Typen (dieser Bereich **fett**) | Eigentümer |
|---|---|---|---|
| `net:server-state` | vorhanden | **SERVER_UP, SERVER_STATE, SERVER_DOWN, PROXY_UP, PROXY_DOWN**; MAINTENANCE_CHANGED (S01). `TABLIST_OVERRIDE` entfällt (Tablist rendert nur paper-core, S02) | S08 / S01 |
| `net:controller` | **NEU** | **CTL_REQUEST, CTL_REPLY, SHUTDOWN, DRAIN, RESTART_SCHEDULE, RESTART_CANCEL, RELOAD_TEMPLATES, MAPS_CHANGED** | S08 |
| `net:queue` | vorhanden | **QUEUE_JOIN, QUEUE_LEAVE, MATCH_ASSIGNED, QUEUE_FAILED** (Server-Wahl §6.8, Matcher-Details Duels S06) | S08 Rahmen |
| `net:proxy` | NEU (S01) | CONNECT {uuid, server}, RUN_PROXY_COMMAND; hier zusätzlich **CONNECT_MANY {uuids[], server}**, **SEND_LOBBY {uuids[], reasonKey, mode?}** (`mode` → bevorzugt Modus-Lobby, S03 §6.10) | S01 / S08 |
| `net:integ` | **NEU** | **DISCORD_LINKED, DISCORD_UNLINKED, ROLE_SYNC_REQUEST** | S08 |
| `net:chat`, `net:social`, `net:moderation`, `net:profile`, `net:stats`, `net:private`, `net:game`, `net:duels`, `net:clanwar`, `net:cb-chat`, `net:cb-events`, `net:smp-events` | S01/S02/S03/S05/S06/S07 | unverändert | andere |

---

## 3. Befehle

Recht = LuckPerms-Node (Präfix `retromc.` = `brandId`, S01). „Team“ = Gruppen mit Node. Alle Ausgaben §7, Fehler §c. Präfix `prefix.cloud` (§7). `/cloud` wird auf **Velocity** registriert (funktioniert überall) und als `CTL_REQUEST` an den Controller geschickt. Antwort-Timeout 3 s → `cloud.err.timeout`.

| # | Syntax | Aliase | Recht/Rang | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 1 | `/server` | – | `retromc.staff.server` | eigener Server + klickbare Liste aller `joinable` Server, gruppiert nach Template (ersetzt Velocity-Standard) | `server.current`, `server.list.header`, `server.list.entry` |
| 2 | `/server <Name>` | – | `retromc.staff.server` | verbindet direkt; ignoriert `joinable`, nicht `minProtocol` | `server.connecting`, `server.err.unknown`, `server.err.same`, `connect.version` (S01) |
| 3 | `/send <Spieler\|current\|all> <Server\|Template>` | – | `retromc.admin.send` | Template → Ziel per Balancer (§6.7); Audit `SEND` | `send.ok` ({count},{target}), `server.err.unknown`, `send.err.noplayer` |
| 4 | `/whereami` | `/serverinfo` (Annahme) | alle (Annahme) | zeigt Server und Proxy, klickbar zum Kopieren (für Bug-Reports) | `whereami.info` |
| 5 | `/glist` | – | `retromc.staff.glist` | Spieler je Kategorie und Template, Summe | `glist.header`, `glist.line`, `glist.total` |
| 6 | `/cloud` | `/cloud help` | `retromc.admin.cloud` | Hilfe, klickbare Zeilen | `cloud.help.*` |
| 7 | `/cloud list [Template]` | `/cloud ls` | `retromc.admin.cloud` | Server: Name, Zustand (Farbe §6.1), online/max, Map, Laufzeit | `cloud.list.header`, `cloud.list.entry`, `cloud.list.empty` |
| 8 | `/cloud info <Server>` | – | `retromc.admin.cloud` | alle Felder aus `server:{name}` + Instanz-ID, Build | `cloud.info.*`, `server.err.unknown` |
| 9 | `/cloud gui` | – | `retromc.admin.cloud` | öffnet GUI A (§5.1); nur auf Paper-Servern | – |
| 10 | `/cloud start <Template> [Anzahl]` | – | `retromc.admin.cloud.start` | Anzahl 1–10 (Standard 1); prüft `max` und globale Grenze | `cloud.start.ok`, `cloud.err.template`, `cloud.err.limit`, `cloud.err.amount` |
| 11 | `/cloud stop <Server> [force]` | – | `retromc.admin.cloud.stop` | ohne `force`: Spieler → Lobby, dann Stopp (§6.10); mit `force`: sofort `docker kill` | `cloud.stop.ok`, `cloud.stop.force`, `server.err.unknown`, `cloud.err.static` (STATIC-Server nur per `force`) |
| 12 | `/cloud drain <Server>` | – | `retromc.admin.cloud.stop` | `DRAINING`: keine neuen Spieler, Stopp sobald leer oder nach 600 s | `cloud.drain.ok` |
| 13 | `/cloud restart <Server> [Sekunden]` | – | `retromc.admin.cloud.stop` | Countdown 10–600 s (Standard 60), dann wie `stop` + Neustart desselben Templates | `cloud.restart.ok`, `cloud.err.seconds` |
| 14 | `/cloud restart cancel <Server>` | – | `retromc.admin.cloud.stop` | bricht Countdown ab | `cloud.restart.cancelled`, `server.restart.cancelled` (an Spieler) |
| 15 | `/cloud templates` | `/cloud tpl` | `retromc.admin.cloud` | Templates mit `laufend/minReady/max`, Overrides markiert `*` | `cloud.tpl.header`, `cloud.tpl.entry` |
| 16 | `/cloud scale <Template> <minReady> [max]` | – | `retromc.admin.cloud.scale` | Laufzeit-Override (`ctl:override:*`) bis Reload; 0 ≤ minReady ≤ max ≤ 50 | `cloud.scale.ok`, `cloud.err.template`, `cloud.err.amount` |
| 17 | `/cloud reload` | – | `retromc.admin.cloud.scale` | Templates neu lesen, Overrides löschen | `cloud.reload.ok` ({count}), `cloud.reload.err` ({file},{error}) |
| 18 | `/cloud maps [Modus]` | – | `retromc.admin.cloud` | Maps mit aktiver Version, an/aus | `cloud.maps.header`, `cloud.maps.entry` |
| 19 | `/cloud map <enable\|disable> <MapId>` | – | `retromc.admin.cloud.maps` | schaltet `infra.map.enabled`; Publish `MAPS_CHANGED` | `cloud.map.enabled`, `cloud.map.disabled`, `cloud.err.map`, `cloud.err.mapversion` (keine aktive Version) |
| 20 | `/cloud proxies` | – | `retromc.admin.cloud` | Proxys mit online, Build, Laufzeit | `cloud.proxies.entry` |
| 21 | `/discord` | – | alle | Einladungslink (`NETWORK_DISCORD_INVITE`) + Verknüpfungsstatus | `discord.info`, `discord.status.linked`, `discord.status.unlinked` |
| 22 | `/discord link` | – | alle | erzeugt Code (§6.13), klickbar zum Kopieren | `discord.code`, `discord.err.linked`, `discord.err.ratelimit` |
| 23 | `/discord unlink` | – | alle | löscht Verknüpfung, Publish `DISCORD_UNLINKED` | `discord.unlinked`, `discord.err.notlinked` |
| D1 | Discord `/link code:<Code>` | – | Discord-Nutzer | prüft Code, schreibt `integ.discord_link`, Rollen-Sync | ephemeral `bot.link.ok` / `bot.link.invalid` / `bot.link.taken` |
| D2 | Discord `/unlink` | – | verknüpfte Nutzer | löscht Zeile, entfernt verwaltete Rollen | `bot.unlink.ok` / `bot.unlink.none` |
| D3 | Discord `/status` | – | alle | Embed: online gesamt, je Kategorie, Wartung | `bot.status.*` |
| D4 | Discord `/stats spieler:<Name> [modus]` | – | alle | Embed aus API `/v1/players` (respektiert `togglestats`, S02) | `bot.stats.*`, `bot.stats.hidden`, `bot.stats.unknown` |
| C1 | `map-exporter validate <Ordner>` | – | Entwickler/CI | §6.11 Schritte 1–3; Exit 0/1 | Liste `PROBLEM: …` |
| C2 | `map-exporter pack <Ordner> [--out <Datei>]` | – | Entwickler/CI | validate + deterministisches ZIP + SHA-256 | Pfad + Hash |
| C3 | `map-exporter upload <Ordner> [--activate]` | – | CI (main) | pack + Store-Upload + `infra.map(_version)`; `--activate` setzt aktiv | neue Version |
| C4 | `controller migrate` | – | Betrieb | Flyway (vorhanden) | Anzahl Migrationen |
| C5 | `controller run` | Standard ohne Argument | Betrieb | Controller-Schleife §6.4 | Log |

Recht-Nodes werden in LuckPerms den Gruppen `admin` und `developer` gegeben (Annahme), `retromc.staff.*` zusätzlich `moderator` und höher (S01-Gruppen).

---

## 4. Anzeige-Elemente

Dieser Bereich besitzt **kein** Scoreboard, keine Hologramme, keine NPCs und keine Hotbar. Er liefert Daten für andere Bereiche.

| Element | Inhalt | Ära | Beleg |
|---|---|---|---|
| Scoreboard | – (S02). Liefert `displayName` des Servers für die Server-Zeile | – | – |
| Tablist-Header Zeile 1 | `§e{brand}.net {modeColor}{serverDisplay}` (S02 §4.4, Key `tab.header.mode`); Zeile 1 muss mit Marke/Domain + Leerzeichen + Servername beginnen (Client-Addons lesen so den Modus). Rendering nur in paper-core (S02), Wert `serverDisplay` von hier | beide | Struktur belegt N15 §1.1 („GommeHD.net <Servername>“, 1.8 und 2026) |
| `serverDisplay` | Lobbys: `{displayName}-{n}` (z. B. `Lobby-3`, `Premium-Lobby-1`, `Silent-Lobby-1`); Spiel- und Dauerserver: `{displayName}` (z. B. `BedWars`, `CityBuild`) | beide | Servertypen `lobby`, `premiumlobby`, `silentlobby` belegt N17 Z. 83; Schreibweise Annahme |
| Actionbar | Neustart-Countdown letzte 10 s: `server.restart.actionbar` | – | Annahme |
| Title | bei Neustart 5 s vorher: Titel `server.restart.title`, Untertitel `server.restart.subtitle` (fadeIn 5, stay 40, fadeOut 10 Ticks) | – | Annahme |
| Bossbar | Neustart ab 60 s: `server.restart.bossbar`, Farbe RED, Fortschritt = Rest/Gesamt, `SOLID` | – | Annahme |
| Chat-Broadcast | Neustart bei 300, 120, 60, 30, 10, 5, 4, 3, 2, 1 s (nur Werte ≤ Gesamtdauer): `server.restart.chat`. Template-Feld `restartAnnounce` überschreibt die Liste (CityBuild-Lobby belegt 60, 45, 30, 15, 10, 5–1, S07 §6) | – | Annahme |
| Kick-Bildschirm | `screen.server.stopping` (Server stoppt, Lobby nicht erreichbar) | – | Annahme |
| Server-Liste (MOTD) | Spielerzahl = `count:online`; Rest S01 §4.1 | – | – |

---

## 5. GUIs

Bibliothek: InventoryFramework (N07 §5). Rahmen-Glas `GRAY_STAINED_GLASS_PANE`, Name `§r` (Annahme, Stil wie S01).

### 5.1 GUI A „Server-Übersicht“ (`/cloud gui`)
Titel `§8Server §7» §e{filter}` (`{filter}` = `Alle` oder Template), **6 Reihen**.

| Slot | Item | Name | Lore | Klick |
|---|---|---|---|---|
| 0–44 | je Server, sortiert Kategorie → Template → Nummer; Material nach Zustand: `LIME_CONCRETE` ONLINE/WAITING, `YELLOW_CONCRETE` STARTING, `RED_CONCRETE` INGAME, `ORANGE_CONCRETE` ENDING/DRAINING, `LIGHT_GRAY_CONCRETE` BOOTING, `BLACK_CONCRETE` STOPPING; Menge = `max(1, min(64, online))` | `{stateColor}{name}` | `§7Template: §e{template}` · `§7Zustand: {stateColor}{state}` · `§7Spieler: §e{online}§7/§e{max}` (+ ` §8(+{reserved} reserviert)`) · `§7Map: §e{map}` · `§7Läuft seit: §e{uptime}` · leer · `§eLinksklick §7» Infos` · `§eShift+Links §7» Verbinden` · `§eRechtsklick §7» Leeren (drain)` · `§eShift+Rechts §7» Stoppen` | Links: `/cloud info`; Shift+Links: `/server {name}` (Recht `staff.server`); Rechts: GUI B mit Aktion DRAIN; Shift+Rechts: GUI B mit Aktion STOP |
| 45 | `ARROW` | `§7« Zurück` | `§7Seite {page}/{pages}` | vorige Seite (nur wenn page>1, sonst Glas) |
| 46–47, 51–52 | Glas | `§r` | – | – |
| 48 | `HOPPER` | `§eFilter` | `§7Aktuell: §e{filter}` · `§7Klicke, um zu wechseln` | wechselt zyklisch Alle → Templates (alphabetisch) |
| 49 | `COMPARATOR` | `§eTemplates` | `§7Laufend: §e{running} §7Server` · `§7Spieler: §e{total}` | öffnet GUI C |
| 50 | `CLOCK` | `§aAktualisieren` | `§7Automatisch alle 2 Sekunden` | neu laden |
| 53 | `ARROW` | `§7Weiter »` | `§7Seite {page}/{pages}` | nächste Seite |

Zustandsfarben (`stateColor`): ONLINE/WAITING §a, STARTING §e, INGAME §c, ENDING/DRAINING §6, BOOTING §7, STOPPING §8. Auto-Refresh alle 40 Ticks, solange geöffnet.

### 5.2 GUI B „Bestätigen“
Titel `§8{actionLabel}: §c{name}` (`actionLabel` = `Stoppen` | `Leeren` | `Neustarten`), **3 Reihen**. Slots 0–10, 12–14, 16–26 Glas.

| Slot | Item | Name | Lore | Klick |
|---|---|---|---|---|
| 11 | `LIME_WOOL` | `§aBestätigen` | `§7{online} Spieler werden in die Lobby geschickt.` | führt Aktion aus → Chat `cloud.stop.ok`/`cloud.drain.ok`/`cloud.restart.ok`, schließt |
| 13 | Server-Item wie GUI A | wie GUI A | wie GUI A ohne Klickzeilen | – |
| 15 | `RED_WOOL` | `§cAbbrechen` | – | zurück zu GUI A |

### 5.3 GUI C „Templates“
Titel `§8Templates`, **6 Reihen**. Slots 0–44: je Template, Material nach Kategorie: `NETHER_STAR` LOBBY, `IRON_SWORD` GAME, `GRASS_BLOCK` PERSISTENT. Name `§e{displayName} §8({id})`. Lore: `§7Kategorie: §e{category}` · `§7Laufend: §e{running}§7 · bereit: §a{ready}` · `§7minReady: §e{minReady}§7 · max: §e{max}` (+ ` §c*` bei Override) · `§7Spieler: §e{online}` · leer · `§eLinksklick §7» Server anzeigen` · `§eShift+Links §7» 1 Server starten`. Klick: Links → GUI A mit Filter; Shift+Links → `/cloud start {id} 1`. Slot 49 `ARROW` `§7« Zurück` → GUI A. Rest der Reihe 6 Glas.

### 5.4 Lobbywechsler (Datenvertrag; GUI gehört der Lobby-Spec)
- Belegt: Seit 03.03.2026 enthält der Lobbywechsler auch das Silent-Lobby-Item (N05 §1, per Suche). Layout, Titel und Items des Originals: **LUECKE**.
- Hier festgelegt ist nur die Datenquelle: `ServerRegistry.byCategory(LOBBY)` sortiert nach Template (`lobby`, `premiumlobby`, `silentlobby`), dann Nummer. Je Eintrag gibt es `serverDisplay`, `online/max`, `joinable`, `rankMin`, `silent` und `current` (eigener Server).
- Fallback-Layout, falls die Lobby-Spec keines festlegt (Annahme): 3 Reihen, Lobbys ab Slot 10. `GLOWSTONE_DUST` = aktuelle Lobby, `SUGAR` = beitretbar, `REDSTONE` = voll oder gesperrt. Name `§a{serverDisplay}`, Lore `§7{online}/{max} Spieler`. Klick → `CONNECT`.

---

## 6. Mechaniken & Zahlen

### 6.1 Server-Zustände

| Zustand | Bedeutung | `joinable` möglich | Übergänge |
|---|---|---|---|
| `BOOTING` | Prozess läuft, Plugins laden | nein | → ONLINE/WAITING (bereit), → STOPPING (Fehler) |
| `ONLINE` | Lobby/Dauerserver bereit | ja | → DRAINING, → STOPPING |
| `WAITING` | Spielserver, Wartelobby offen | ja | → STARTING, → STOPPING |
| `STARTING` | Countdown läuft | ja (bis voll) | → WAITING (zu wenige), → INGAME |
| `INGAME` | Runde läuft | nein (nur Zuschauer/Rejoin nach Spiel-Rahmen) | → ENDING |
| `ENDING` | Siegerphase | nein | → STOPPING |
| `DRAINING` | nimmt keine Neuen an | nein | → STOPPING (leer oder 600 s) |
| `STOPPING` | Spieler werden verschoben, Daten geschrieben | nein | Key verschwindet |

`ServerState.acceptsPlayers()` = `ONLINE|WAITING|STARTING` (Code-Methode erweitern). Die Zustände WAITING bis ENDING setzt der Spiel-Rahmen über `ServerLifecycle.setState` (§8). BOOTING, ONLINE, DRAINING und STOPPING setzt paper-core.

### 6.2 Plugin-Lebenszyklus `RetroMCCore` (Paper)
1. `onLoad`: Umgebungsvariablen lesen (§6.15). Pflicht sind `NETWORK_SERVER_NAME`, `NETWORK_DB_*` und `NETWORK_VALKEY_URL`. Fehlt eine → Log `FEHLER` + `Bukkit.shutdown()`.
2. `onEnable` in dieser Reihenfolge: DB-Pool (HikariCP, `NETWORK_DB_POOL_SIZE` Standard 5 auf Paper) → Valkey (Lettuce) → `MessageBus` → `ServerRegistry`-Client → Dienste aus S01/S02 → Befehle. Jeder Verbindungsaufbau: 3 Versuche im Abstand von 2 s, danach Shutdown (kein Betrieb ohne DB).
3. Registrierung: `SADD servers:all`, `SADD servers:tpl:{template}`, `server:{name}` mit `state=BOOTING`, Publish `SERVER_UP`.
4. `ServerLoadEvent(STARTUP)`: für LOBBY/PERSISTENT `state=ONLINE`. Für GAME übernimmt der Spiel-Rahmen (`WAITING`, sobald die Map geladen ist).
5. Heartbeat alle **100 Ticks (5 s)**, asynchron: HSET aller Felder + `EXPIRE 15`. `online` = `Bukkit.getOnlinePlayers().size()`, `reserved` = `ZCOUNT reserve:{name} now +inf`.
6. Login-Prüfung (`AsyncPlayerPreLoginEvent`, nur GAME): Wer weder in `reserve:{name}` steht noch das Recht `retromc.staff.server` hat, wird abgelehnt (`connect.notreserved`). Ausnahme: `joinable` und Template `publicJoin: true`. Nach erfolgreichem Join wird die Reservierung entfernt (`ZREM`).
7. Mitteilungen aus `net:controller` für den eigenen Namen: `SHUTDOWN` → §6.10, `DRAIN` → `state=DRAINING`, `RESTART_SCHEDULE/CANCEL` → Countdown §4.
8. `onDisable`: `state=STOPPING` + Publish. Noch verbundene Spieler → `SEND_LOBBY` (`server.stopping.moved`). Statistik-Puffer leeren (S02). Publish `SERVER_DOWN`, `DEL server:{name}`, `SREM`, Pools schließen. Höchstdauer 10 s.
9. Abhängige Plugins (Lobby, Modi, CityBuild) laden nach dem Core (`load: BEFORE`, `join-classpath: true`, ARCH §3) und rufen keine core-Dienste in `onLoad` auf.

### 6.3 Lebenszyklus `velocity-core`
1. `ProxyInitializeEvent`: DB, Valkey, Bus, Registrierung `proxy:{id}` (`id` = `NETWORK_SERVER_NAME`, z. B. `proxy-1`), Heartbeat 5 s / TTL 15 s, Publish `PROXY_UP`.
2. Server-Sync: Alle Einträge aus `servers:all` mit gültigem `server:*` → `proxy.registerServer(name, host:port)`. Danach Live-Updates über `SERVER_UP`/`SERVER_DOWN` und ein voller Abgleich **alle 30 s** (fehlende registrieren, abgelaufene abmelden). Einträge aus `velocity.toml [servers]` bleiben bestehen (statische Dev-Server).
3. Abmelden eines Servers mit Spielern: nicht nötig. Velocity leitet bei Kick bzw. Trennung zur Lobby um (S01 §6.3 Schritt 8).
4. `ProxyShutdownEvent`: Publish `PROXY_DOWN`, `DEL proxy:{id}`.

### 6.4 Controller-Schleife (`controller run`)
1. Start: DB-Migration **nicht** automatisch (nur `migrate`). Templates laden, Bus verbinden.
2. Leader-Wahl: `SET ctl:leader {instanz} NX PX 10000`, verlängern alle 3 s. Nur der Leader startet und stoppt Server. Andere Instanzen antworten trotzdem auf lesende `CTL_REQUEST` (list/info).
3. Takt **1 s** (Leader):
   a. Registry einlesen (`servers:all` → `server:*`). Namen ohne Key gelten als **HEARTBEAT_LOST**: `SREM`, Instanz in DB schließen, Container entfernen (`docker rm -f`).
   b. Boot-Timeout: `BOOTING` länger als **120 s** → kill, `stop_reason=BOOT_TIMEOUT`.
   c. Spielserver: `ENDING` länger als **60 s** oder Gesamtlaufzeit über **7 200 s** → `SHUTDOWN` (MAX_LIFETIME).
   d. Tickets abarbeiten (§6.8).
   e. Skalierung je Template (§6.9). Höchstens **3 gleichzeitige Boots** je Launcher-Host (Dev), Annahme.
   f. Alle 5 s: `count:online` schreiben, abgelaufene `proxies` bereinigen.
4. Jobs (§6.10): Rangliste-/Aufbewahrungs-Jobs um 04:00 Europe/Berlin (Annahme).

### 6.5 Templates (`deploy/templates/<id>.yml`)

| Feld | Typ | Pflicht | Standard | Bedeutung |
|---|---|---|---|---|
| `id` | `^[a-z0-9-]{2,24}$` | ja | – | Template-ID = Präfix des Servernamens |
| `displayName` | Text ≤ 20 | ja | – | für `serverDisplay` |
| `category` | LOBBY \| GAME \| PERSISTENT | ja | – | – |
| `mode`, `variant` | Text | GAME ja | `variant` = `default` | Registry-Felder, Queue-Schlüssel `{mode}-{variant}` |
| `image` | Docker-Image | ja | `itzg/minecraft-server:2026.10.0-java25` | Dev; Prod: eigenes `paper-base` (§6.17) |
| `memory` | z. B. `2G` | nein | LOBBY `3G`, GAME `2G`, PERSISTENT `8G` | N07 §8 (Kapazitätsmodell, Annahme-Werte) |
| `plugins` | Liste von Ordnern unter `docker/dev-plugins/` | ja | – | z. B. `[core, lobby]` |
| `maxPlayers` | Zahl | ja | LOBBY 100, sonst Modus-Spec | Hardlimit |
| `softCap` | Zahl | LOBBY | 80 | Balancer-Grenze §6.7 |
| `minReady` | 0–50 | nein | LOBBY 2, GAME 2, PERSISTENT 1 | bereite Server (ONLINE bzw. WAITING ohne Spieler) |
| `max` | 1–50 | nein | LOBBY 20, GAME 50, PERSISTENT 1 | Instanzen je Template |
| `oneMatch` | bool | GAME | true | eine Runde pro Server (N07 §3), danach Stopp |
| `publicJoin` | bool | nein | false | Join ohne Reservierung (z. B. FFA-Dauerserver S06) |
| `restartAnnounce` | Liste s | nein | §4 | Sekunden für Neustart-Chatmeldungen |
| `minProtocol` | Protokollnummer | nein | 0 | Versionsprüfung Velocity (S01 §6.3 Schritt 9) |
| `rankMin` | LuckPerms-Gewicht | nein | 0 | `premiumlobby` 200 (Annahme = Premium, S01) |
| `silent` | bool | nein | false | `silentlobby` true |
| `mapPool` | Modus-ID | GAME | `mode` | §6.11 |
| `env` | Map | nein | – | zusätzliche Variablen |

Standard-Templates (Dev, Annahme; Varianten-IDs gehören den Modus-Specs):

| id | Kategorie | displayName | max/softCap | minReady / max | Besonderheit |
|---|---|---|---|---|---|
| `lobby` | LOBBY | Lobby | 100/80 | 2 / 20 (Dev 1/3) | Ziel bei Login |
| `premiumlobby` | LOBBY | Premium-Lobby | 100/80 | 1 / 5 | `rankMin: 200` |
| `silentlobby` | LOBBY | Silent-Lobby | 100/80 | 1 / 3 | `silent: true`, Recht aus S01 |
| `lobby-{mode}` (z. B. `lobby-bedwars`) | LOBBY | {Modus}-Lobby | 100/80 | 1 / 5 | Modus-Lobby: `mode` = Modus; Ziel von `SEND_LOBBY{mode}` und `/gameteleport` (S03 §6.10/§6.12), Ort für `/resetstats` (S02) |
| `cb-lobby`, `cb-plot`, `cb-farm`, `cb-space`, `cb-end` | PERSISTENT | CityBuild / Farming / … | S07 | `cb-lobby` 1/3, `cb-plot` 1/3, `cb-farm` 9/9, `cb-space` 0/4, `cb-end` 0/1 | Rollen aus S07 §1 (`server-role`), Welt-Volume, kein `oneMatch`, täglicher Neustart (O12) |
| `smp-lobby`, `smp-world` | PERSISTENT | SMP | S07 | 1/1, 1/4 | Warteschlange `smp:queue:world` (S07) |
| `<mode>-<variant>` je Modus | GAME | Modusname | Modus-Spec | 2 / 50 (Dev 1/3) | `oneMatch: true` |

Globale Grenze: höchstens **200** dynamische Server insgesamt (Dev **12**), Annahme.

### 6.6 Servernamen
1. Format `{templateId}-{n}`, nur `[a-z0-9-]`, höchstens 48 Zeichen. Beispiele: `lobby-1`, `premiumlobby-1`, `bedwars-4x4-12`.
2. `n` ist die **kleinste freie Zahl ≥ 1** in `ctl:names:{template}`. Sie wird beim Stopp freigegeben, nachdem `server:*` gelöscht ist (frühestens 15 s später, damit es keine Verwechslung im Proxy gibt).
3. Statische Compose-Server (STATIC) heißen genau wie ihr `NETWORK_SERVER_NAME` (`lobby`, `bedwars-1`, Gerüst). Neu wird `NETWORK_SERVER_TEMPLATE` verlangt (`lobby`, `bedwars-…`). Lobby `lobby` (ohne Zahl) zeigt `serverDisplay` `Lobby-1` (Annahme).
4. Container-Name `net-{servername}`, Docker-Netz `network-dev_default`, Adresse `net-{name}:25565`.

### 6.7 Lobby-Balancing
`joinable(LOBBY)` = `state==ONLINE && online < maxPlayers && !draining`. `joinable(GAME)` = `state ∈ {WAITING, STARTING} && online+reserved < max && !privateRound`.
1. **Erster Join** (`PlayerChooseInitialServerEvent`, S01): Kandidaten = Template `lobby`, `joinable`, `online < softCap`. Wahl = **wenigste Spieler**, bei Gleichstand kleinste Nummer. Kein Kandidat → alle `lobby` mit `online < maxPlayers` (über softCap). Keiner → `premiumlobby`, wenn `rank ≥ rankMin`. Sonst `screen.nolobby` (S01).
2. **`/l`** (S01 #63): gleiche Wahl. Ausnahmen: Wer schon in einer Lobby ist → `lobby.already`. Silent-Lobby nur, wenn der Spieler schon dort ist (S01).
3. **Party**: Wenn der Leiter `/l` nutzt, gehen die Mitglieder nicht mit (S01-Party-Regeln). Muss eine Gruppe gemeinsam verschoben werden (`CONNECT_MANY`), wird eine Lobby gesucht, auf der `freeSlots ≥ Anzahl` ist.
4. **Hochskalieren**: Ein neuer `lobby`-Server startet, wenn (a) die Zahl bereiter Lobbys (`online < softCap`) unter `minReady` liegt **oder** (b) `Σonline / Σ softCap ≥ 0,75` über alle `lobby`-Server ist.
5. **Herunterskalieren**: Liegt die Auslastung `≤ 0,30` für **300 s** am Stück und laufen mehr als `minReady` Lobbys, wird die Lobby mit den wenigsten Spielern → `DRAINING` gesetzt. Ihre Spieler werden einzeln per `SEND_LOBBY` auf andere Lobbys verteilt (höchstens 5 je Sekunde, Hinweis `lobby.drain.moved`), dann Stopp `IDLE`.
6. Wartungsmodus (S01): Der Balancer arbeitet unverändert. Velocity filtert die Logins.

### 6.8 Spielserver-Zuteilung (Rahmen; Lobby-Seite S03 §6.4, Duels-Matcher S06)
1. Lobby (NPC-Rechtsklick „nächste Runde“, belegt N05 §1; Navigator) publiziert `QUEUE_JOIN {ticket, players[], partyId?, mode, variant}`. Die Party kommt immer als ein Ticket (S01 `PartyService`).
2. Controller: Kandidaten = `servers:tpl:{mode}-{variant}` mit `joinable` und `freeSlots ≥ |players|`. Wahl (abgestimmt mit S03 §6.4): zuerst `STARTING` mit kleinstem `countdown`, dann `WAITING` mit **meisten Spielern** (füllt Runden schneller), bei Gleichstand der älteste Server; sonst neuer Server (§6.9).
3. Treffer: `ZADD reserve:{server}` je Spieler (Ablauf now+30 s) → `MATCH_ASSIGNED {ticket, server, matchId?}` → Velocity verbindet (`CONNECT_MANY`). Meldung an die Spieler: `queue.connecting`.
4. Kein Treffer: Ticket in `ticket:{id}` parken, ggf. Server starten (§6.9), alle 1 s neu versuchen. Nach **60 s** → `QUEUE_FAILED` → `queue.failed`. `QUEUE_LEAVE` bricht ab.
5. Ein Spieler hat höchstens ein offenes Ticket. Ein neues ersetzt das alte (`queue.replaced`).
6. Die Party passt nicht in die Teamgröße der Variante → `QUEUE_FAILED reason=PARTY_TOO_BIG` → `queue.partytoobig` (Teamgröße kommt aus der Modus-Spec).

### 6.9 Skalierung Spielserver
- `ready` = Server des Templates in `BOOTING` oder `WAITING` mit `online+reserved == 0`.
- Starten, solange `ready < minReady + ceil(wartende Spieler im Template / maxPlayers)` und `laufend < max` und global < 200.
- Stoppen: Ein `WAITING`-Server ohne Spieler, der länger als **600 s** leer ist und bei dem `ready > minReady` gilt → `SHUTDOWN IDLE`.
- `oneMatch`: Nach `ENDING` kein Reset. Der Server stoppt (§6.10). Für Ersatz sorgt die Regel oben.

### 6.10 Stopp, Neustart, Jobs
1. `SHUTDOWN {server, reason}` → Server: `STOPPING`, `SEND_LOBBY` an alle (`server.stopping.moved`), 2 s warten, `Bukkit.shutdown()`. Der Controller wartet bis zu **30 s** auf `SERVER_DOWN`, danach `docker kill`.
2. `/cloud restart`: `RESTART_SCHEDULE {server, at, reason}` → Countdown §4. Bei 0 → Schritt 1, danach startet der Controller dasselbe Template (bei PERSISTENT mit demselben Namen und Volume).
3. Geplanter täglicher Neustart der PERSISTENT-Server um **05:00 Europe/Berlin** mit 300 s Countdown (Annahme; GommeHD-Wartungen liegen laut N06 oft am Wochenendmorgen).
4. Jobs (Leader, 04:00 Europe/Berlin):

| Job | Regel | Quelle |
|---|---|---|
| `retention.server_instance` | löschen, wenn `stopped_at < now()-90 d` | Annahme |
| `retention.admin_action` | `< now()-365 d` | Annahme |
| `retention.player_ip` | `core.player_ip` mit `last_seen < now()-30 d` und ohne aktive Strafe löschen | N07 §9 E (7–30 Tage) |
| `retention.chatlog` | `moderation.chatlog` `< now()-90 d`, außer verknüpft mit offenem Report | N07 §9 E (30–90 Tage) |
| `discord.reconcile` | alle 10 min, siehe §6.13 | N07 §7 |

### 6.11 Map-Pipeline
**Map-Ordner** (Quelle: Builder-Server mit FAWE, N07 §5): `<mapId>/map.json`, `level.dat`, `region/`, optional `entities/`, `poi/`.

`map.json` (Pflichtfelder fett):

| Feld | Regel |
|---|---|
| **`id`** | `^[a-z0-9_]{2,32}$`, gleich dem Ordnernamen |
| **`displayName`** | 1–24 Zeichen, keine §-Codes |
| **`mode`** | bekannter Modus (Liste der `mapschema/*.json`) |
| `variants` | Liste von Varianten-IDs; leer = alle |
| **`version`** | Ganzzahl ≥ 1, steigt mit jeder Änderung |
| **`builders`** | ≥ 1 Name (eigenes Bauteam) |
| **`license`** | muss `"own"` sein. Fremde Maps sind gesperrt (§6.19) |
| **`minPlayers`, `maxPlayers`** | 1 ≤ min ≤ max ≤ 200 |
| **`bounds`** | `{min:{x,y,z}, max:{x,y,z}}`, min < max je Achse |
| **`spectatorSpawn`** | `{x,y,z,yaw,pitch}` innerhalb `bounds` |
| `waitingLobby` | Spawn der Wartelobby, falls auf der Map |
| **`markers`** | modusspezifisch, geprüft gegen `mapschema/<mode>.json` aus dem Modus-Plugin-Jar (Teams, Betten, Spawner, Kisten …; Inhalt in der Modus-Spec) |

Ablauf:
1. **Validieren** (`MapValidator`, vorhanden: `map.json`, `region/`). Neu dazu: `level.dat` vorhanden, JSON gültig, Pflichtfelder, `id`==Ordner, `license=="own"`, alle Marker-Koordinaten in `bounds`, Schema des Modus erfüllt.
2. **Bereinigen** beim Packen: `playerdata/`, `stats/`, `advancements/`, `session.lock`, `uid.dat`, `datapacks/` werden ausgelassen.
3. **Größe**: ZIP höchstens **200 MB** (Annahme). Bei Überschreitung bricht der Vorgang ab.
4. **Packen** deterministisch: Einträge sortiert, Zeitstempel fest `1980-01-01T00:00:00Z`, Kompression DEFLATE 9. Daraus `sha256` (gleicher Inhalt → gleicher Hash, Unit-Test).
5. **Upload** in den `MapStore`. Schlüssel `maps/{mode}/{id}/{version}.zip`. Implementierungen: `FileMapStore` (Dev, Standard, `NETWORK_MAP_STORE=file:/maps-store`) und `S3MapStore` (Prod, Hetzner Object Storage, N07 §3). Dann `infra.map` upserten und `infra.map_version` einfügen. Mit `--activate`: alte Version `active=false`, neue `true`, `maps:{mode}` neu schreiben, Publish `MAPS_CHANGED {mode}`.
6. **Laden auf dem Spielserver** (`MapService`): Beim Boot werden alle aktiven Maps des `mapPool` in den Cache `/data/map-cache/{id}-{version}.zip` geholt (Hash prüfen, bei Abweichung neu laden, 3 Versuche). `prepare(mapId)` entpackt nach `worlds/{mapId}`, lädt die Welt mit `autoSave=false` und gibt `MapMeta` zurück. Die Welt wird nie zurückgeschrieben (eine Runde pro Server).
7. **Auswahl**: Kandidaten für Voting bzw. `/forcemap` (S03 `MapSelection`) = aktive, `enabled` Maps des Modus mit passender Variante und `minPlayers ≤ Teamgröße×Teams ≤ maxPlayers`. Zufallsauswahl ohne die letzten **3** auf demselben Template gespielten Maps (Annahme).
8. CI: Auf PRs, die `maps/**` im Map-Repo ändern, läuft `validate`; auf `main` läuft `upload --activate`. Maps liegen **nicht** im Code-Repo (getrenntes Repo `retromc-maps` mit Git LFS, Annahme).

### 6.12 Messaging-Regeln
1. Envelope (JSON, UTF-8): `{"t":"SERVER_STATE","v":1,"id":"<uuid>","ts":<epochMillis>,"src":"<server|proxy-id|controller>","re":"<id>?","d":{…}}`. `re` nur bei Antworten.
2. Höchstens **64 KiB** je Nachricht. Größere Daten gehen über DB bzw. Valkey-Keys plus Verweis.
3. Unbekannte `t` und höhere `v` werden ignoriert und auf DEBUG geloggt (vorwärtskompatibel).
4. Anfrage/Antwort (`CTL_REQUEST`/`CTL_REPLY`): Korrelation über `id`/`re`, Timeout **3 s**, keine automatische Wiederholung (Admin-Aktionen sind nicht idempotent).
5. Empfang immer asynchron (Lettuce-Thread). Bukkit-API nur über `Bukkit.getScheduler().runTask`.
6. Verlust-Toleranz: Der Zustand ist immer aus Keys rekonstruierbar (Registry-Resync 30 s, Ticket-Timeout 60 s, Reservierung 30 s).
7. Plugin-Messaging (`BungeeCord`-Kanal, in `velocity.toml` an) nur als Fallback für `Connect`, wenn Valkey ausfällt.
8. Signierter Chat wird auf dem Proxy **nie** abgebrochen. Moderation läuft auf dem Backend (N08 §10.2: vermeidet den Fork-Trick mit negativem Offset).

### 6.13 Discord-Bot
1. `/discord link` (Paper): Ist der Spieler schon verknüpft → `discord.err.linked`. Rate-Limit **5 Codes / 10 min**. Code = **6 Zeichen** aus `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`. Ein alter offener Code wird gelöscht. Gespeichert werden `discord:code:{code}` → uuid und `discord:pending:{uuid}`, beide 600 s.
2. Discord `/link code`: Code großschreiben, `GETDEL` → uuid. Fehlt er → `bot.link.invalid`. Ist die Discord-ID schon verknüpft → `bot.link.taken`. Sonst INSERT `integ.discord_link`, Publish `DISCORD_LINKED {uuid, discordId}`. Ist der Spieler online → `discord.linked`.
3. Rollen-Sync: Ziel-Rollen = `discord_role_map` für die Gruppen des Spielers (LuckPerms-Tabellen bzw. `profile:{uuid}.rank`). Nur Rollen aus der Map werden verwaltet, andere nie angefasst. Auslöser: Link, `ProfileChanged` mit Feld `rank` (S02) und Abgleich alle **10 min** (höchstens 50 Mitglieder je Durchlauf, Discord-Rate-Limits).
4. Nickname = Minecraft-Name, wenn `nick_sync=true`. Bei Fehler (z. B. Server-Owner) still überspringen.
5. `/unlink` (beide Seiten): DELETE, Rollen aus der Map entfernen, Publish `DISCORD_UNLINKED` (DSGVO, N07 §7).
6. Gateway-Intents: nur `GUILD_MEMBERS` (für Rollen). Keine Message-Content-Intent.
7. Umgebung: `NETWORK_DISCORD_TOKEN`, `NETWORK_DISCORD_GUILD`. Ohne Token beendet sich der Bot mit Log-Hinweis (Exit 0, Dev-Standard).

### 6.14 API-Dienst (`services:api`, Port `NETWORK_API_PORT`, Standard 8080)

| Methode/Pfad | Auth | Antwort | Cache | Eigentümer der Daten |
|---|---|---|---|---|
| `GET /health` | – | `200 ok` (vorhanden) + DB/Valkey-Ping → `503` bei Fehler | – | S08 |
| `GET /v1/status` | – | `{online, maintenance, categories:{lobby,game,persistent}, modes:{mode:online}}` | 30 s | S08 |
| `GET /v1/players/{name\|uuid}` | – | Profil + Stats (30 Tage/gesamt); `404` unbekannt; `hidden:true` bei `togglestats` | 30 s | S02 |
| `GET /v1/leaderboards/{mode}?period=30d\|all&limit=1..100` | – | Liste `{rank, name, score}` | 30 s | S02 |
| `GET /v1/clans/{tag}` | – | Clan-Info | 30 s | S01 |
| `GET /v1/chatlog/{id}` | – | Chatlog-Seite (Daten) | – | S01 |
| `POST /v1/appeals` | – | Entbannungsantrag | – | S01 |
| `GET /v1/servers` | Token `status:read` | volle Registry | – | S08 |

- Rate-Limit **60 Anfragen/min je IP**. Die IP geht nur als HMAC in den Schlüssel ein. Bei Überschreitung `429` mit `Retry-After`.
- JSON mit Gson. Fehlerformat `{"error":"<code>","message":"<text>"}`.
- CORS nur für `NETWORK_WEB_ORIGIN`.
- Keine IPs in Logs.
- Token: `Authorization: Bearer <token>`. Geprüft wird über den SHA-256-Hash in `integ.api_token`.

### 6.15 Docker-Compose (lokal) und Umgebungsvariablen
Vorhanden: `postgres` 18.6, `migrate` (Flyway), `valkey` 8.1.10, `velocity`, `lobby`, `game-bedwars-1` (VER). Ergänzungen:

| Änderung | Inhalt |
|---|---|
| NEU Dienst `controller` | Image `eclipse-temurin:25-jre` + `controller.jar` (oder `gradle installDist`-Mount), `command: run`, Mounts: `/var/run/docker.sock`, `../deploy/templates:/templates:ro`, `./dev-plugins:/dev-plugins:ro`, `maps-store:/maps-store`; `NETWORK_LAUNCHER=docker`, `NETWORK_HOST_PROJECT_DIR=${PWD}/..` (Host-Pfade für `docker run -v`) |
| NEU Dienst `api` | Port `127.0.0.1:8080:8080` |
| NEU Dienst `discord-bot` | `profiles: ["bot"]` (startet nur mit `--profile bot`) |
| NEU Volume | `maps-store` (FileMapStore), `map-cache` je Spielserver |
| `velocity.toml` | `enable-player-address-logging = false` (W6) |
| `.env.example` | NEU: `NETWORK_HMAC_SECRET`, `NETWORK_DISCORD_TOKEN`, `NETWORK_DISCORD_GUILD`, `NETWORK_DISCORD_INVITE` |
| `devPlugins` | Ordner zusätzlich `core/` (nur paper-core), damit Templates Ordner kombinieren können |

Variablen (neu fett): `NETWORK_SERVER_NAME`, **`NETWORK_SERVER_TEMPLATE`**, **`NETWORK_SERVER_HOST`** (Standard = Hostname), **`NETWORK_SERVER_PORT`** (25565), `NETWORK_DB_URL/USER/PASSWORD/POOL_SIZE`, `NETWORK_VALKEY_URL`, `NETWORK_API_PORT`, **`NETWORK_ENV`** (`dev|staging|prod`), **`NETWORK_LAUNCHER`** (`static|docker|agones`), **`NETWORK_TEMPLATES_DIR`**, **`NETWORK_MAP_STORE`** (`file:/…` oder `s3://bucket`), **`NETWORK_S3_ENDPOINT/ACCESS_KEY/SECRET_KEY`**, **`NETWORK_HMAC_SECRET`**, **`NETWORK_WEB_ORIGIN`**, **`NETWORK_DISCORD_*`**.

`DockerCliLauncher.start(template, name)` ruft auf:
`docker run -d --name net-{name} --network network-dev_default --label network.server={name} --label network.template={id} -e EULA -e TYPE=PAPER -e VERSION=26.3 -e PAPER_BUILD=206 -e ONLINE_MODE=FALSE -e MEMORY={memory} -e NETWORK_*… -v {hostDir}/docker/paper/config:/config:ro -v {merged plugins}:/plugins:ro -v map-cache:/data/map-cache {image}`.
Beim Controller-Start werden verwaiste Container mit Label `network.server` ohne Registry-Eintrag entfernt.

### 6.16 Teststrategie

| Ebene | Werkzeug | Umfang | Ort / Lauf |
|---|---|---|---|
| Unit | JUnit 6.1.3 (BOM, vorhanden) | reine Logik: `ValkeyKeys`, `Envelope`-Codec, Namensvergabe, Balancer-Wahl, Skalierungsformel, Template-Parser, `MapValidator`, deterministisches ZIP, Discord-Code-Generator | jedes Modul, `./gradlew test` |
| Integration | **Testcontainers (NEU)** mit `postgres:18.6-alpine`, `valkey/valkey:8.1.10-alpine` | Flyway (alle Migrationen in Reihenfolge, 2. Lauf „up to date“), Repositories, Pub/Sub-Rundlauf, Leader-Wahl mit 2 Instanzen, Reservierungs-TTL | Source-Set `integrationTest` in core-data/controller, Tag `integration`, `./gradlew integrationTest` |
| Plugin-Logik | **kein MockBukkit** bis zur Prüfung auf Paper 26.3 (ARCH Lücke 6). Bukkit-Code bleibt dünner Adapter; Logik liegt in plattformfreien Klassen | – | – |
| Smoke | Docker Compose | `velocity`, `lobby`, `controller` hochfahren. Prüfen: Lobby-Log enthält `RetroMCCore aktiv` und `server:lobby` existiert in ≤ 120 s. Prüfen: `controller` startet bei `minReady=1` einen `bedwars-…`-Server, der in ≤ 120 s `WAITING` erreicht | CI-Job `smoke` |
| Fake-Client (optional) | Annahme: Headless-Bot-Bibliothek erst prüfen | Join → Lobby, `/l`, Queue | später |

### 6.17 CI (GitHub Actions)

| Workflow / Job | Auslöser | Schritte |
|---|---|---|
| `build.yml` / `build` (vorhanden) | Push `main`, PR | JDK 25 Temurin → `./gradlew build` → Plugin-Jars als Artefakt. **NEU:** `checkstyle`, Marken-Prüfung (§6.19) |
| `build.yml` / `integration` (NEU) | Push, PR | `./gradlew integrationTest` (Docker auf `ubuntu-latest` vorhanden) |
| `build.yml` / `smoke` (NEU) | Push `main`, PR mit Label `smoke` | `devPlugins`, `.env` aus Secrets/Zufall, `docker compose up -d`, Prüfungen §6.16, Logs als Artefakt |
| `images.yml` (NEU) | Push `main`, Tags `v*` | Images `ghcr.io/<org>/retromc-{paper-base,velocity,controller,api,discord-bot}:<git-sha>` aus `deploy/docker/`. Basis `eclipse-temurin:25-jre`, Paper-Jar fest eingebaut (keine Downloads beim Start, N07 §6), Nutzer ohne Root |
| `maps.yml` (im Map-Repo) | PR / `main` | `validate` / `upload --activate` (§6.11) |
| Renovate (NEU) | wöchentlich | Paper-Build, Velocity, Bibliotheken, Images, Actions |

### 6.18 Versionsliste (Stand VER 2026-10-20; NEU = vor Einbindung prüfen)

| Komponente | Version | Status |
|---|---|---|
| Paper-API / Server-Build | `26.3.build.206-stable` / Build 206 | geprüft |
| Velocity-API / Server | `4.3.0` / Build 44 | geprüft |
| JDK | Temurin `25.0.4.1+1`, Toolchain 25 | geprüft |
| Gradle | `9.8.2` | geprüft |
| Shadow / run-task / foojay | `9.6.1` / `3.1.0` / `1.0.0` | geprüft |
| HikariCP / PostgreSQL-Treiber | `7.1.0` / `42.7.14` | geprüft |
| Flyway (core, postgresql, Image) | `13.10.3` | geprüft |
| Lettuce | `7.9.0.RELEASE` | geprüft |
| SLF4J simple / JUnit-BOM | `2.0.20` / `6.1.3` | geprüft |
| Caffeine / LuckPerms-API / Cloud / FastBoard / IF / JDA | `3.3.1` / `5.5` / `2.1.0`+`2.0.1` / `2.2.2` / `0.12.2` / `6.7.0` | geprüft, JDA jetzt genutzt |
| Images | `postgres:18.6-alpine`, `valkey/valkey:8.1.10-alpine`, `itzg/minecraft-server:2026.10.0-java25`, `itzg/mc-proxy:2026.10.1-java25`, `flyway/flyway:13.10.3-alpine` | geprüft |
| Actions | `checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, `upload-artifact@v7` | geprüft |
| Gson | neueste 2.x von Maven Central | **NEU, prüfen**. Auf Paper/Velocity `compileOnly` (von der Plattform mitgeliefert), in Diensten `implementation` |
| SnakeYAML (Controller) | neueste 2.x | **NEU, prüfen** |
| Testcontainers (`postgresql`, Kern) | neueste stabile | **NEU, prüfen** |
| `eclipse-temurin:25-jre` | aktueller Tag | **NEU, prüfen** (ARCH Lücke 9) |
| MockBukkit | – | nicht eingebunden (§6.16) |

### 6.19 Rechtliches (Pflicht-Prüfungen)

| Regel | Umsetzung | Quelle |
|---|---|---|
| eigene Marke, keine fremden Namen | Marke nur über `brandName`. CI-Job: `git grep -niE 'gomme\|gommehd\|cheetah\|octopus'` außerhalb `docs/` → Fehler | N07 §9 B, N08 |
| keine fremden Maps | `map.json.license == "own"` (Pflicht), `builders` nur eigenes Team. Map-Repo mit Herkunftsnachweis je Map (Commit-Autor) | N07 §9 B |
| keine fremden Logos/Texte | Texte nur in `core-i18n`, eigene Formulierungen. Status- und Wartungstexte selbst schreiben (nicht die N08-Vorlage übernehmen) | N07 §9 B, N08 §8 |
| keine Mojang-Logos, Disclaimer | Bot-Embed-Footer und API-`/v1/status` enthalten `disclaimer` (Text in §7) | N07 §9 A |
| IP-Datenschutz | Velocity-IP-Logging aus, Paper-Log4j-Filter maskiert IPv4/IPv6, IP nur als HMAC (`NETWORK_HMAC_SECRET`), Aufbewahrung §6.10 | N07 §9 E, N08 §2.3 Nr. 3 |
| Discord-Daten | nur `discord_id ↔ uuid`, Unlink löscht | N07 §7, §9 E |
| Lizenzen Dritter | Velocity-, Paper-, Via*-, Grim-Jars unverändert als Server-Plugins/Server, kein eigener Fork (N08 §10.1). Eigene Plugins linken gegen APIs | N07 §1, N08 §10 |

---

## 7. Nachrichten-Katalog

Präfixe (Annahme): `prefix.cloud` = `§7[§bCloud§7] `, `prefix.discord` = `§7[§9Discord§7] `. Keys ohne Präfix stehen mit `{p:cloud}` bzw. `{p:discord}` im Format. Ein Original ist für keinen Key dieses Bereichs belegt. Ausnahme ist der Tablist-Header (N15 §1.1), dessen Struktur übernommen wird.

| Key | Format (§, {Platzhalter}) | Standardtext | Original-Referenz | Beleg |
|---|---|---|---|---|
| tab.header.line1 | → S02 `tab.header.mode` (`§e{brand}.net {modeColor}{serverDisplay}`) | – | „GommeHD.net <Servername>“ (N15 §1.1) | belegt (Struktur) |
| server.current | `{p:cloud}§7Du bist auf §e{server}§7.` | Du bist auf … | – | Annahme |
| server.list.header | `{p:cloud}§7Server (§e{count}§7):` | – | – | Annahme |
| server.list.entry | `§8» §e{server} §7({online}/{max}) §8[{stateColor}{state}§8]` (klickbar: `/server {server}`) | – | – | Annahme |
| server.connecting | `{p:cloud}§7Verbinde mit §e{server}§7…` | – | – | Annahme |
| server.err.unknown | `{p:cloud}§cDer Server §e{server} §cexistiert nicht.` | – | – | Annahme |
| server.err.same | `{p:cloud}§cDu bist bereits auf diesem Server.` | – | – | Annahme |
| send.ok | `{p:cloud}§e{count} §7Spieler werden zu §e{target} §7geschickt.` | – | – | Annahme |
| send.err.noplayer | `{p:cloud}§cKein passender Spieler gefunden.` | – | – | Annahme |
| whereami.info | `{p:cloud}§7Server: §e{server} §8┃ §7Proxy: §e{proxy}` (Klick kopiert `{server}`) | – | – | Annahme |
| glist.header | `{p:cloud}§7Spieler im Netzwerk:` | – | – | Annahme |
| glist.line | `§8» §e{template}§7: §e{online}` | – | – | Annahme |
| glist.total | `§7Gesamt: §e{total}` | – | – | Annahme |
| cloud.help.line | `§8» §e/cloud {usage} §8- §7{description}` | je Unterbefehl | – | Annahme |
| cloud.list.header | `{p:cloud}§7Server §8(§e{filter}§8)§7: §e{count}` | – | – | Annahme |
| cloud.list.entry | `§8» {stateColor}{server} §7{online}/{max} §8┃ §7{map} §8┃ §7{uptime}` | – | – | Annahme |
| cloud.list.empty | `{p:cloud}§7Keine Server gefunden.` | – | – | Annahme |
| cloud.info.line | `§8» §7{field}: §e{value}` | – | – | Annahme |
| cloud.start.ok | `{p:cloud}§aStarte §e{count}§a× §e{template}§a.` | – | – | Annahme |
| cloud.stop.ok | `{p:cloud}§7Server §e{server} §7wird gestoppt.` | – | – | Annahme |
| cloud.stop.force | `{p:cloud}§cServer §e{server} §cwurde sofort beendet.` | – | – | Annahme |
| cloud.drain.ok | `{p:cloud}§7Server §e{server} §7nimmt keine Spieler mehr an.` | – | – | Annahme |
| cloud.restart.ok | `{p:cloud}§7Neustart von §e{server} §7in §e{seconds} §7Sekunden.` | – | – | Annahme |
| cloud.restart.cancelled | `{p:cloud}§7Neustart von §e{server} §7abgebrochen.` | – | – | Annahme |
| cloud.tpl.header | `{p:cloud}§7Templates: §e{count}` | – | – | Annahme |
| cloud.tpl.entry | `§8» §e{template} §7{running} laufend §8┃ §7minReady §e{minReady}{override} §8┃ §7max §e{max}` | – | – | Annahme |
| cloud.scale.ok | `{p:cloud}§e{template}§7: minReady §e{minReady}§7, max §e{max}§7.` | – | – | Annahme |
| cloud.reload.ok | `{p:cloud}§a{count} Templates geladen.` | – | – | Annahme |
| cloud.reload.err | `{p:cloud}§cFehler in §e{file}§c: §7{error}` | – | – | Annahme |
| cloud.maps.header | `{p:cloud}§7Maps §8(§e{mode}§8)§7: §e{count}` | – | – | Annahme |
| cloud.maps.entry | `§8» {enabledColor}{map} §7v{version} §8┃ §7{builders}` (`enabledColor` §a an / §c aus) | – | – | Annahme |
| cloud.map.enabled / .disabled | `{p:cloud}§7Map §e{map} §7ist jetzt §aaktiv§7.` / `… §cdeaktiviert§7.` | – | – | Annahme |
| cloud.proxies.entry | `§8» §e{proxy} §7{online} Spieler §8┃ §7{build} §8┃ §7{uptime}` | – | – | Annahme |
| cloud.err.template | `{p:cloud}§cUnbekanntes Template: §e{template}` | – | – | Annahme |
| cloud.err.limit | `{p:cloud}§cLimit erreicht (§e{running}§c/§e{max}§c).` | – | – | Annahme |
| cloud.err.amount | `{p:cloud}§cUngültige Anzahl. Erlaubt: §e{min}§c–§e{max}` | – | – | Annahme |
| cloud.err.seconds | `{p:cloud}§cErlaubt sind §e10§c–§e600 §cSekunden.` | – | – | Annahme |
| cloud.err.static | `{p:cloud}§cFeste Server nur mit §eforce §cstoppen.` | – | – | Annahme |
| cloud.err.map | `{p:cloud}§cUnbekannte Map: §e{map}` | – | – | Annahme |
| cloud.err.mapversion | `{p:cloud}§cFür §e{map} §cist keine Version aktiv.` | – | – | Annahme |
| cloud.err.timeout | `{p:cloud}§cDer Controller antwortet nicht.` | – | – | Annahme |
| server.restart.chat | `{p:cloud}§cDieser Server startet in §e{time} §cneu.` (`{time}` = `5 Minuten`, `60 Sekunden`, `1 Sekunde`) | – | – | Annahme |
| server.restart.bossbar | `§cNeustart in §e{seconds}s` | – | – | Annahme |
| server.restart.actionbar | `§cNeustart in §e{seconds} §cSekunden` | – | – | Annahme |
| server.restart.title / .subtitle | `§cNeustart` / `§7Du wirst gleich verschoben` | – | – | Annahme |
| server.restart.cancelled | `{p:cloud}§aDer Neustart wurde abgebrochen.` | – | – | Annahme |
| server.stopping.moved | `{p:cloud}§7Der Server wird beendet. Du wurdest in die Lobby geschickt.` | – | – | Annahme |
| screen.server.stopping | `§cDer Server wird neu gestartet.\n§7Bitte verbinde dich gleich erneut.` | – | – | Annahme |
| lobby.drain.moved | `{p:cloud}§7Deine Lobby wird geschlossen. Du bist jetzt auf §e{server}§7.` | – | – | Annahme |
| connect.notreserved | `§cDieser Server ist nur über die Warteschlange erreichbar.` | – | – | Annahme |
| queue.connecting | `§7Du wirst mit §e{server} §7verbunden…` | – | – | Annahme |
| queue.failed | `§cGerade ist keine Runde frei. Versuch es gleich noch einmal.` | – | – | Annahme |
| queue.replaced | `§7Deine vorige Warteschlange wurde verlassen.` | – | – | Annahme |
| queue.partytoobig | `§cDeine Party ist für diesen Modus zu groß (max. §e{max}§c).` | – | – | Annahme |
| discord.info | `{p:discord}§7Unser Discord: §e{invite}` (klickbar) | – | – | Annahme |
| discord.status.linked / .unlinked | `{p:discord}§7Verknüpft mit §e{discordName}§7.` / `{p:discord}§7Nicht verknüpft. Nutze §e/discord link§7.` | – | – | Annahme |
| discord.code | `{p:discord}§7Dein Code: §e§l{code} §8(§7{minutes} Min. gültig§8)`, Zeile 2 `§7Gib auf Discord §e/link {code} §7ein.` | – | – | Annahme |
| discord.linked | `{p:discord}§aDein Konto ist jetzt mit §e{discordName} §averknüpft.` | – | – | Annahme |
| discord.unlinked | `{p:discord}§7Verknüpfung entfernt.` | – | – | Annahme |
| discord.err.linked | `{p:discord}§cDein Konto ist bereits verknüpft.` | – | – | Annahme |
| discord.err.notlinked | `{p:discord}§cDein Konto ist nicht verknüpft.` | – | – | Annahme |
| discord.err.ratelimit | `{p:discord}§cZu viele Codes. Warte §e{minutes} §cMinuten.` | – | – | Annahme |
| bot.link.ok | `Verknüpft mit **{name}**.` | – | – | Annahme |
| bot.link.invalid | `Der Code ist ungültig oder abgelaufen.` | – | – | Annahme |
| bot.link.taken | `Dieses Discord-Konto ist schon verknüpft. Nutze zuerst /unlink.` | – | – | Annahme |
| bot.unlink.ok / .none | `Verknüpfung entfernt.` / `Es besteht keine Verknüpfung.` | – | – | Annahme |
| bot.status.title | `<brand> – Status` · Felder `Spieler online`, `Lobbys`, `Spielserver`, `Wartung` (`ja`/`nein`) | – | – | Annahme |
| bot.stats.hidden / .unknown | `{name} hat die Statistik verborgen.` / `Spieler nicht gefunden.` | – | – | Annahme |
| disclaimer | `Kein offizielles Minecraft-Produkt. Nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.` | – | Pflichthinweis (N07 §9 A) | abgeleitet |

---

## 8. Schnittstellen

### 8.1 Java-Dienste (core-api / paper-core / velocity-core)

| Schnittstelle | Methoden | Implementiert in | Genutzt von |
|---|---|---|---|
| `ServerRegistry` (core-api, read) | `Optional<ServerInfo> get(name)`, `List<ServerInfo> byTemplate(id)`, `byCategory(cat)`, `byMode(mode, variant)`, `int onlineTotal()`, `subscribe(Consumer<ServerEvent>)` | core-data (Valkey) | Velocity (S01), Lobby (NPC-Zahlen, Navigator, Lobbywechsler), API, Bot |
| `ServerLifecycle` (paper-core) | `ServerInfo self()`, `setState(ServerState)`, `setMap(String)`, `setMatchId(String)`, `setPrivateRound(boolean)`, `incrementMatches()`, `requestShutdown(String reason)` | paper-core | Spiel-Rahmen (S03), CityBuild |
| `PlayerTransfer` (paper-core) | `toServer(Player, String)`, `toLobby(Collection<UUID>, String reasonKey, @Nullable String mode)`, `toTemplate(Collection<UUID>, String template)` | paper-core → `net:proxy` | Spiel-Rahmen (Rundenende), Lobby |
| `QueueClient` (paper-core) | `join(List<UUID>, mode, variant, partyId)`, `leave(UUID)`, Callback `onAssigned/onFailed` | paper-core → `net:queue` | Lobby-NPCs, Navigator |
| `MapService` (paper-core, einziger Map-Lader) | `List<MapMeta> candidates(mode, variant, players)`, `CompletableFuture<World> prepare(mapId)`, `MapMeta meta(mapId)` | paper-core | Spiel-Rahmen (`MapSelection`, S03: Voting, `/forcemap`) |
| `LobbyBalancer` (velocity-core) | `Optional<String> pickLobby(Player, int groupSize, @Nullable String mode)`, `pickFor(template, int groupSize)` | velocity-core | S01 (`/l`, Initial-Server, Kick-Fallback) |
| `ServerLauncher` (controller) | `start(Template, String name)`, `stop(String name, boolean force)`, `List<String> running()` | `StaticLauncher`, `DockerCliLauncher`, später `AgonesLauncher` | Controller intern |
| `MapStore` (core-data) | `put(key, Path)`, `Path fetch(key, sha256)`, `exists(key)` | `FileMapStore`, `S3MapStore` | map-exporter, paper-core |

### 8.2 Bukkit-/Velocity-Events (NEU)

| Event | Plattform | Felder | Zweck |
|---|---|---|---|
| `NetworkServerStateEvent` | Paper | `ServerState old, now` | Plugins reagieren auf eigenen Zustand |
| `NetworkRestartScheduledEvent` | Paper | `Instant at, String reason`, cancelbar nur durch `/cloud restart cancel` | CityBuild speichert vorher |
| `NetworkServerRegisteredEvent` / `…UnregisteredEvent` | Velocity | `ServerInfo` | S01 (Tablist, `/goto`) |

### 8.3 Nachrichten (Payload `d` im Envelope)

| Kanal | Typ | Payload | Richtung |
|---|---|---|---|
| net:server-state | SERVER_UP / SERVER_STATE | `ServerInfo` vollständig (SERVER_STATE nur bei Zustandswechsel, nicht je Heartbeat) | Server → alle |
| net:server-state | SERVER_DOWN | `{server, reason}` | Server/Controller → alle |
| net:server-state | PROXY_UP / PROXY_DOWN | `{proxy}` | Velocity → alle |
| net:controller | CTL_REQUEST | `{actor(uuid), cmd, args[]}` | Velocity → Controller |
| net:controller | CTL_REPLY | `{ok, lines[] (i18n-Key + Parameter)}` | Controller → Velocity |
| net:controller | SHUTDOWN / DRAIN | `{server, reason}` | Controller → Server |
| net:controller | RESTART_SCHEDULE / RESTART_CANCEL | `{server, at?, reason?}` | Controller → Server |
| net:controller | RELOAD_TEMPLATES | `{}` | Velocity → Controller |
| net:controller | MAPS_CHANGED | `{mode}` | Controller/CI → Spielserver |
| net:queue | QUEUE_JOIN / QUEUE_LEAVE | `{ticket, players[], partyId?, mode, variant, elo?}` | Lobby → Controller |
| net:queue | MATCH_ASSIGNED | `{ticket, server, matchId?, arena?}` | Controller → Velocity |
| net:queue | QUEUE_FAILED | `{ticket, players[], reason: TIMEOUT\|PARTY_TOO_BIG\|NO_TEMPLATE}` | Controller → Lobby |
| net:proxy | CONNECT_MANY | `{uuids[], server}` | Controller/Server → Velocity |
| net:proxy | SEND_LOBBY | `{uuids[], reasonKey, mode?}` | Server/Controller → Velocity (nutzt `LobbyBalancer`; `mode` → Modus-Lobby `lobby-{mode}`, sonst Hauptlobby) |
| net:integ | DISCORD_LINKED / DISCORD_UNLINKED | `{uuid, discordId}` | Bot/paper-core → alle |
| net:integ | ROLE_SYNC_REQUEST | `{uuid}` | API/Admin → Bot |

### 8.4 Getrennt bauen
- **Proxy (S01)**: braucht nur `ServerRegistry` und `LobbyBalancer`. Bis der Controller fertig ist, reichen die Einträge aus `velocity.toml` und die Selbstregistrierung der Paper-Server (STATIC).
- **Kern-UI/Stats (S02)**: liest `serverDisplay` (Tablist/Scoreboard) und ruft `ServerLifecycle` nicht auf. Die API nutzt S02-Repositories nur lesend.
- **Spiel-Rahmen (S03, genutzt von S04–S06)**: setzt Zustände über `ServerLifecycle` und holt Maps über `MapService`. Ohne Controller: Template `publicJoin: true`, Map über `NETWORK_MAP_ID` erzwingbar (Dev).
- **Lobby**: `QueueClient` und `ServerRegistry`. Ohne Controller antwortet `QueueClient` mit `QUEUE_FAILED NO_TEMPLATE`.

---

## 9. Abnahmekriterien

1. Es gibt keine Datei und keinen String mit `dev.netzwerk` oder `brandName=Netzwerk`; alle Pakete liegen unter `net.retromc.*`. `paper-plugin.yml` hat `name: ${brandName}Core` (→ `RetroMCCore`), Velocity-ID `retromc-core` (W1).
2. `docker compose … up`: Flyway wendet `V1`, `V1_1`, `V1_2`, `V2_1`, `V3_1`, `V4_1`, `V5_1`, `V6_1`, `V7_1`, `V8_1` in dieser Reihenfolge an. Ein zweiter Lauf meldet „up to date“ (W2).
3. Lobby startet → innerhalb von 10 s existiert `server:lobby` mit `state=ONLINE`, `ttl ≤ 15`. Nach `docker stop` ist der Key spätestens 15 s später weg und Velocity hat den Server abgemeldet.
4. Controller mit Template `bedwars-…` `minReady=1`: Innerhalb von 120 s existiert `bedwars-…-1` in `WAITING`. Velocity kann `/server bedwars-…-1` ausführen.
5. `/cloud stop <Server>`: Spieler landen in einer Lobby mit `server.stopping.moved`. Der Container ist nach höchstens 30 s weg. `infra.server_instance.stop_reason=ADMIN`, `infra.admin_action` hat eine Zeile.
6. Namensvergabe: Laufen `x-1` und `x-3`, heißt der nächste Server `x-2` (Unit-Test `NameAllocatorTest`).
7. Balancer (Unit-Test): Lobbys {12, 5, 5, 90 (softCap 80)} → Wahl = Lobby 2 (5, kleinere Nummer). Alle ≥ softCap → wenigste unter maxPlayers. Alle voll → leer.
8. Skalierung (Unit-Test): 3 Lobbys, softCap 80, Σonline 181 (Auslastung 0,754) → 1 Start. Σonline 70 für 300 s bei minReady 2 → genau 1 DRAIN.
9. Queue (Integrationstest): Party aus 3 Spielern, Server A (online 2, max 4) und B (online 0, max 4) → Zuteilung B (A hat nur 2 freie Plätze). Bei A (online 1, max 8) → A (meiste Spieler zuerst). Ohne passenden Server → `QUEUE_FAILED TIMEOUT` nach 60 s ± 1 s.
10. Reservierung: Ein Spieler ohne Reservierung wird auf einem GAME-Server (ohne `publicJoin`) mit `connect.notreserved` abgewiesen. Mit Reservierung kommt er rein, danach ist der ZSET-Eintrag entfernt.
11. Leader-Wahl (Integrationstest): Zwei Controller → genau einer startet Server. Wird der Leader beendet, übernimmt der andere in ≤ 10 s.
12. `map-exporter validate`: Eine Map ohne `license:"own"`, mit Marker außerhalb `bounds` oder ohne `level.dat` ergibt je 1 Problem und Exit 1. Zweimal `pack` desselben Ordners → identischer SHA-256.
13. `MapService.prepare` lädt eine Map mit falschem Hash neu (Test mit manipuliertem Cache) und speichert die Welt nicht (`autoSave=false`).
14. Envelope-Codec: Rundlauf aller Typen aus §8.3. Unbekannter Typ wird ohne Exception ignoriert. Eine Nachricht über 64 KiB wird beim Senden abgelehnt.
15. `/cloud list` ohne laufenden Controller → nach 3 s `cloud.err.timeout`.
16. Discord: Code hat 6 Zeichen aus dem Alphabet, verschwindet nach 600 s, ist nur einmal einlösbar (`GETDEL`). Ein 6. Code in 10 min → `discord.err.ratelimit`. `/unlink` löscht die Zeile.
17. API: `/health` liefert 503, wenn Valkey gestoppt ist. 61 Anfragen/min von einer IP → `429`. In Logs steht keine IP (Grep über das Log im Smoke-Test).
18. Velocity-Log und Paper-Log enthalten nach Login keine IP-Adresse (W6, Log4j-Filter).
19. Neustart-Countdown 60 s: Chat bei 60/30/10/5–1, Bossbar ab 60, Actionbar ab 10, Title bei 5. `/cloud restart cancel` entfernt die Bossbar sofort.
20. CI: `build`, `integration` und `smoke` grün. Der Marken-Grep findet keine fremde Marke außerhalb `docs/`.
21. Abgelaufene Daten: Der Job löscht `core.player_ip` älter als 30 Tage ohne aktive Strafe. Test mit 3 Testzeilen.

---

## 10. Offene Punkte (jeweils mit DEFAULT-ENTSCHEIDUNG)

| # | Offener Punkt | DEFAULT-ENTSCHEIDUNG |
|---|---|---|
| O1 | GommeHDs Cloud-/Controller-Software, Servernamen und Balancing-Regeln sind nicht öffentlich (N08 §9, §11.3) | eigene Regeln aus §6.6–6.9; Werte als Template-Felder konfigurierbar |
| O2 | Kubernetes/Agones fehlt (ARCH Lücke 9) | Phase 1 `DockerCliLauncher` auf 1–3 Hosts. `AgonesLauncher` (Fleets je Template, Buffer = `minReady`) erst ab ~500 CCU oder 2. Host |
| O3 | Mehrere Docker-Hosts im Dev/Staging | Phase 1: genau ein Host. Mehrhost-Betrieb erst über Agones |
| O4 | Pub/Sub kann Nachrichten verlieren | bleibt (W7). Wenn `QUEUE_FAILED TIMEOUT` > 1 % der Tickets → Umstieg auf Valkey Streams für `net:queue` |
| O5 | Gson/SnakeYAML/Testcontainers/`eclipse-temurin:25-jre`: Versionen nicht geprüft | neueste stabile Version zum Einbindungstag, Eintrag in VER mit Datum |
| O6 | MockBukkit für Paper 26.3 unklar | Logik plattformfrei testen, Paper per Smoke-Test. MockBukkit-Tests aus S01–S03 laufen nur mit Gradle-Tag `mockbukkit`, sobald eine MockBukkit-Version für Paper 26.3 geprüft und in VER eingetragen ist (Konvention in `00_bauplan_uebersicht.md`) |
| O7 | Kapazitätswerte (Lobby 100/80, RAM 3G/2G/8G) ohne Messung (N07 §8) | übernehmen. Nach erstem Lasttest anpassen (nur Template-YAML) |
| O8 | Lobbywechsler-Layout des Originals unbekannt | Lobby-Spec entscheidet, sonst Fallback §5.4 |
| O9 | Premium-Lobby: automatisch für Premium beim Login? | nein. Login immer `lobby`, Premium-Lobby über Lobbywechsler bzw. `/gameteleport premiumlobby` (S01) |
| O10 | Speicherort der Maps (Repo, LFS, Größe) | separates Repo `retromc-maps` mit Git LFS. ZIP ≤ 200 MB. Prod-Store Hetzner Object Storage (S3) |
| O11 | DataVersion-Prüfung von `level.dat` (NBT lesen) | Phase 1 nur Existenzprüfung. NBT-Prüfung, sobald eine NBT-Bibliothek eingebunden ist |
| O12 | Zeitpunkt des täglichen Neustarts von CityBuild | 05:00 Europe/Berlin, 300 s Countdown, abschaltbar per Template `dailyRestart: false` |
| O13 | Discord-Rollen je Rang | `integ.discord_role_map` leer ausliefern. Ohne Einträge findet kein Rollen-Sync statt, nur die Verknüpfung |
| O14 | Statusseite (GommeHD nutzt „issue-status“, MIT, N08 §8) | Phase 2: issue-status oder Uptime Kuma mit eigenen Texten. `/v1/status` reicht bis dahin |
| O15 | Mehrere Paper-Versionen parallel (GommeHD fährt das, N08 §2.2) | nein. Alle Templates auf derselben Paper-Version. `image` je Template erlaubt Ausnahmen |
| O16 | Rechtsprüfung (Impressum, DSGVO, DSA) liegt außerhalb dieser Spec und ist unverifiziert (N07 §9 Gaps) | vor Launch anwaltlich prüfen. Technische Punkte aus §6.19 sind Pflicht ab Phase 1 |
| O17 | `/whereami` für alle Spieler sichtbar? | ja (hilft bei Bug-Reports). Ändern über das LuckPerms-Node `retromc.whereami` (Standard: true) |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | W1 entschied Rückbenennung auf `dev.netzwerk` / `brandName=Netzwerk` / Plugin `NetzwerkCore` | **verworfen**: Nutzer-Entscheidung Marke RetroMC, `net.retromc.*`, Plugin `RetroMCCore`, Velocity-ID `retromc-core`; Images/Map-Repo `retromc-*`; Nodes `retromc.*`. Abnahme §9 #1 angepasst |
| K2 | W2 listete nur 6 Migrationen | vollständige Liste `V1_1` … `V8_1` (alle Specs umgestellt) |
| K3 | `server:{name}`: S03 nutzte eigene Kategorien/Felder/Heartbeat 2 s | ein Schema hier; S03-Zusatzfelder aufgenommen; Heartbeat 5 s + Sofort-Update |
| K4 | Rückkehr nach Rundenende doppelt (`RETURN_TO_LOBBY` S03, `SEND_LOBBY` hier) | `SEND_LOBBY` mit optionalem `mode`; Modus-Lobby-Templates `lobby-{mode}` ergänzt |
| K5 | Server-Wahl bei Queue „meiste Spieler“ vs. S03 „STARTING mit kleinstem Countdown zuerst“ | S03-Reihenfolge übernommen |
| K6 | Map-Registry doppelt (`game.map` S03), `MapService` in paper-core und Spiel-Rahmen | `infra.map` hier; `MapService` = paper-core, Rahmen-Klasse heißt `MapSelection` |
| K7 | Tablist-Header `§l<brand> §r§7…` (S01/S02 `§e…`), `TABLIST_OVERRIDE` | `§e{brand}.net {modeColor}{serverDisplay}`, Override entfällt |
| K8 | Nur Template `citybuild` (S07 braucht 5 CB-Rollen + SMP) | Templates `cb-*`, `smp-*` |
| K9 | Neustart-Ansagen fest (S07 belegt andere Werte) | Template-Feld `restartAnnounce` |
| K10 | Verweise „Spiel-Rahmen S05/S06“ | Spiel-Rahmen ist S03 |
| K11 | MockBukkit: S01–S03 planen Tests, hier „kein MockBukkit“ | O6 präzisiert (Tag `mockbukkit`, nach Versionsprüfung) |
