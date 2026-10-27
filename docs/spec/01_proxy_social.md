# Spec 01 – Proxy & Social (Velocity)

Stand 2026-10-21 · Bereich: Velocity-Core, Ränge, Freunde, Party, Clan, PN, Global-Chat, Report, Strafen, Nick, Sprache, Einstellungen, AFK, Wartung, Anti-Spam.

**Kürzel Belege:** N05/N15/N16/N17/N08 = Notizen `research_notes/NN_*.md` · V = Video-Sweep (`scratchpad/sweep/batch2_*.md`, `thumbs.md`) · OCR = `sweep/review/*.md` · B1 = Bild Partymenü (tw_dreix31_clip_20250603_3365521103 t26s) · B2 = Bild Nick-Menü (tw_vorbild_clip_20260728_1361148519 t0s).
**Beleg-Stufen:** *belegt* (Code/Text/lesbarer Frame) · *abgeleitet* (aus Belegen geschlossen) · *Annahme* (festgelegt, unbelegt).
**Ära:** **26** = Standard (2025/26, 1.21-Lobby) · **20+** = 2023–Anfang 2026 · **18** = 1.8-Ära (Option `legacyStyle`).
**Textregel:** Formate mit §-Codes; Umsetzung in `core-i18n` als MiniMessage (`§7`→`<gray>` …, `{x}`→`<x>`). Spalte „Standardtext“ = eigene Formulierung; Original nur als Referenz.

---

## 1. Umfang & Module

| Gradle-Modul (settings.gradle.kts) | Status | Paket(e) | Inhalt in diesem Bereich |
|---|---|---|---|
| `:platform:velocity-core` | vorhanden (Gerüst) | `net.retromc.velocity.core` + Unterpakete `.login`, `.motd`, `.rank`, `.friend`, `.party`, `.clan`, `.msg`, `.chat`, `.report`, `.punish`, `.nick`, `.lang`, `.settings`, `.afk`, `.maintenance`, `.spam`, `.command`, `.bridge` | alle Befehle, Login/Ban/Wartung, MOTD, Tab-Header/-Footer-Default, Routing `/l`, PN, Social-Broadcasts, Report, Strafen |
| `:platform:paper-core` | vorhanden | `net.retromc.paper.core.social` (`.gui`, `.chat`, `.nick`, `.afk`, `.tab`) | **GUIs** (Velocity kann keine Inventare öffnen), Chat-Rendering + Mute/Spam/Ignore auf Backend, Nick-Anwendung (Profil/Skin), AFK-Erkennung, Tab-Einträge/Nametags |
| `:core:core-api` | vorhanden | `net.retromc.core.api.social`, `.rank`, `.punish`, `.event`, `.util` | DTOs, Service-Interfaces, Events, `EloCalculator`, `PunishmentId`, `DurationFormat`, Regex-Validatoren |
| `:core:core-data` | vorhanden | `net.retromc.core.data.social`, `.punish` | Repositories (JDBC/Hikari), Valkey-Keys (Erweiterung `ValkeyKeys`) |
| `:core:core-messaging` | vorhanden | `net.retromc.core.messaging` | Kanäle `net:*` (+ neu `net:proxy`), JSON-Envelope |
| `:core:core-i18n` | vorhanden | `net.retromc.core.i18n` | Schlüssel aus §7; Sprachen erweitern (§6.14) |
| `db/migrations` | vorhanden | – | **NEU:** `V1_1__proxy_social.sql` (§2) |
| `:services:api` | vorhanden | `net.retromc.services.api` | Entbannungsantrag-Endpunkt, Chatlog-Seite (nur Schnittstelle, §8) |
| `:game:clanwar` | vorhanden | – | ruft `ClanService.recordWar(...)` (nur Schnittstelle) |

- **Kein neues Gradle-Modul nötig.** NEU sind nur Pakete und die Migrationen `V1_1__proxy_social.sql` und `V1_2__seed_reasons.sql`.
- **Plugin-Name / ID:** Velocity-ID `<brandId>-core` (heute `retromc-core`), Name `${brandName}Core` (aus `BuildInfo`), Paper-Gegenstück `${brandName}Core`.
- **Abhängigkeiten velocity-core:** `velocity-api 4.3.0` (compileOnly + annotationProcessor), `luckperms-api 5.5` (compileOnly; LuckPerms-Velocity als Pflicht-Plugin `dependencies = {@Dependency(id="luckperms")}`), `cloud-velocity 2.0.1` + `cloud-core 2.1.0` (Befehle), `caffeine 3.3.1` (lokale Caches), `hikaricp`, `postgresql`, `lettuce` (über core-data). paper-core zusätzlich `cloud-paper`, `inventoryframework 0.12.2` (GUIs).
- **Ränge:** LuckPerms auf Proxy und allen Backends, Storage PostgreSQL-Schema `luckperms`, Messaging-Service `redis` gegen Valkey (V1-Entscheidung beibehalten).

---

## 2. Datenmodell

### 2.1 PostgreSQL – vorhanden (V1, unverändert nutzen)
`core.player` (uuid PK, name, name_lower UNIQUE, first_join, last_join, last_quit, playtime_s, **locale**, settings JSONB) · `core.player_name_history` · `core.player_ip` · `social.friendship (a<b, since)` · `social.friend_request (sender, receiver, created_at)` · `social.privacy` · `social.clan` · `social.clan_member` · `moderation.punishment` · `moderation.report` · `moderation.chatlog`.
`social.clan.elo` (V1) wird **nicht** genutzt (Elo je Modus in `social.clan_elo`); Spalte bleibt, Kommentar „deprecated“.

### 2.2 PostgreSQL – NEU `V1_1__proxy_social.sql`

```sql
-- Defaults nachziehen
ALTER TABLE social.friend_request ALTER COLUMN created_at SET DEFAULT now();
CREATE INDEX ON social.friend_request (receiver);
CREATE INDEX ON social.friendship (b);

-- Privatsphäre/Einstellungen (Werte siehe §6.13)
ALTER TABLE social.privacy
  ADD COLUMN friend_req_from  SMALLINT NOT NULL DEFAULT 2,  -- 0 niemand, 1 ab Premium, 2 alle
  ADD COLUMN jump_from        SMALLINT NOT NULL DEFAULT 1,  -- 0 niemand, 1 Freunde, 2 nur Favoriten
  ADD COLUMN notify_friends   BOOLEAN  NOT NULL DEFAULT true,
  ADD COLUMN notify_clan      BOOLEAN  NOT NULL DEFAULT true,
  ADD COLUMN hide_from_clan   BOOLEAN  NOT NULL DEFAULT false, -- ab Premium
  ADD COLUMN global_chat      BOOLEAN  NOT NULL DEFAULT true,
  ADD COLUMN nick_on_join     BOOLEAN  NOT NULL DEFAULT false,
  ADD COLUMN nick_keep        BOOLEAN  NOT NULL DEFAULT false,
  ADD COLUMN updated_at       TIMESTAMPTZ NOT NULL DEFAULT now();
-- vorhandene Spalten: friend_requests (Master-Schalter, bleibt), msg_from 0/1/2, party_from 0/1/2, show_online

CREATE TABLE social.friend_favorite (            -- pro Richtung (owner markiert friend)
  owner        UUID NOT NULL,
  friend       UUID NOT NULL,
  notify       BOOLEAN NOT NULL DEFAULT true,    -- Online-Meldung trotz notify_friends=false
  allow_jump   BOOLEAN NOT NULL DEFAULT true,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (owner, friend)
);

CREATE TABLE social.ignore (
  owner      UUID NOT NULL,
  target     UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (owner, target)
);
CREATE INDEX ON social.ignore (target);

ALTER TABLE social.clan
  ADD COLUMN join_mode     SMALLINT NOT NULL DEFAULT 1,     -- 0 nur Einladung, 1 Einladung+Anfrage
  ADD COLUMN show_roles    BOOLEAN  NOT NULL DEFAULT false, -- Leader: Rangunterschiede sichtbar
  ADD COLUMN xp            BIGINT   NOT NULL DEFAULT 0,
  ADD COLUMN last_active   TIMESTAMPTZ NOT NULL DEFAULT now(), -- letzter Login eines Mitglieds
  ADD COLUMN description   VARCHAR(64);
CREATE INDEX ON social.clan (last_active);
ALTER TABLE social.clan_member
  ADD CONSTRAINT clan_member_role_chk CHECK (role IN (-1,0,1,2)); -- -1 Testmitglied, 0 Mitglied, 1 Moderator, 2 Leiter
CREATE UNIQUE INDEX clan_one_leader ON social.clan_member (clan_id) WHERE role = 2;

CREATE TABLE social.clan_invite (
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  uuid UUID, invited_by UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (clan_id, uuid));
CREATE TABLE social.clan_request (
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  uuid UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (clan_id, uuid));
CREATE TABLE social.clan_stats_mode (            -- /clan togglestats
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  mode VARCHAR(24), enabled BOOLEAN NOT NULL DEFAULT false,
  PRIMARY KEY (clan_id, mode));
CREATE TABLE social.clan_elo (
  clan_id BIGINT REFERENCES social.clan ON DELETE CASCADE,
  mode VARCHAR(24), elo INT NOT NULL DEFAULT 1000,
  games INT NOT NULL DEFAULT 0, wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (clan_id, mode));
CREATE INDEX ON social.clan_elo (mode, elo DESC);
CREATE TABLE social.clan_war (
  id BIGSERIAL PRIMARY KEY, mode VARCHAR(24) NOT NULL, ranked BOOLEAN NOT NULL,
  clan_a BIGINT, clan_b BIGINT, winner BIGINT, elo_delta INT NOT NULL DEFAULT 0,
  match_id UUID, hidden BOOLEAN NOT NULL DEFAULT false,      -- true nach /clanstatsreset
  started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ);
CREATE INDEX ON social.clan_war (clan_a, ended_at DESC);
CREATE INDEX ON social.clan_war (clan_b, ended_at DESC);
CREATE TABLE social.clan_rename_log (clan_id BIGINT, at TIMESTAMPTZ NOT NULL DEFAULT now(),
  old_name VARCHAR(16), old_tag VARCHAR(5), PRIMARY KEY (clan_id, at));

-- Strafen
ALTER TABLE moderation.punishment
  ADD COLUMN public_id VARCHAR(16),               -- Anzeige-ID, §6.10
  ADD COLUMN origin    VARCHAR(8) NOT NULL DEFAULT 'MANUAL', -- MANUAL, REPORT, AC, AUTO
  ADD COLUMN ac_code   INT,                       -- Anticheat-Code "(3574)"
  ADD COLUMN stage     SMALLINT NOT NULL DEFAULT 1;
CREATE UNIQUE INDEX ON moderation.punishment (public_id);
CREATE INDEX ON moderation.punishment (uuid, type, issued_at DESC);
CREATE TABLE moderation.reason (
  code VARCHAR(32) PRIMARY KEY, type VARCHAR(8) NOT NULL,       -- BAN/MUTE
  label_key VARCHAR(64) NOT NULL,                               -- i18n-Schlüssel
  stages_s BIGINT[] NOT NULL,                                   -- Dauer je Stufe, -1 = permanent
  reportable BOOLEAN NOT NULL DEFAULT false, report_scope VARCHAR(32)); -- z. B. 'TTT,REPLAY'
CREATE TABLE moderation.ban_ip (ip_hmac BYTEA, punishment_id BIGINT REFERENCES moderation.punishment,
  PRIMARY KEY (ip_hmac, punishment_id));
ALTER TABLE moderation.report ADD COLUMN public_id VARCHAR(12);
CREATE INDEX ON moderation.report (status, created_at);
CREATE INDEX ON moderation.report (target, created_at DESC);

-- Nick
CREATE TABLE core.nick_pool (
  name VARCHAR(16) PRIMARY KEY, texture_value TEXT NOT NULL, texture_signature TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT true);

-- Wartung
CREATE TABLE core.maintenance_whitelist (uuid UUID PRIMARY KEY, added_by UUID,
  added_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE core.network_setting (key VARCHAR(64) PRIMARY KEY, value JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()); -- maintenance, motd, tablist (Hot-Reload)
```

Seed (`V1_2__seed_reasons.sql`, Annahme-Werte §6.10): Gründe `hacking`, `clientmod`, `ban_evasion`, `teaming`, `randomkilling`, `bugusing`, `name_skin`, `chat_insult`, `chat_respect`, `chat_spam`, `chat_ad`.

### 2.3 Valkey-Schlüssel (Erweiterung `ValkeyKeys`)

| Schlüssel | Typ | Inhalt | TTL |
|---|---|---|---|
| `presence:{uuid}` (vorh.) | HASH | proxy, server, since, **afk** (0/1), **nick** (leer/Name), **hidden** (show_online=false) | 60 s, Heartbeat 20 s |
| `profile:{uuid}` | HASH | name, rank, color (§x), prefix, locale, clanTag, clanId, level, nickName, coins, gold, playtimeS, statsHidden (die letzten vier pflegt Spec 02) | 10 min, bei Zugriff verlängert, bei Login/Änderung neu. **Einzige Definition** (Spec 02 §2.2 verweist hierher) |
| `name:{lower}` | STRING | uuid | 24 h |
| `party:{id}` (vorh.) | HASH | leader, created, public (0/1), publicFilter (ALL/PREMIUM), requireRequest (0/1), maxSize | 30 min ab letzter Aktivität |
| `party:{id}:members` (vorh.) | **ZSET** | uuid → Beitritt (ms) (Reihenfolge = Nachfolge) | wie Party |
| `party:{id}:requests` | ZSET | uuid → Anfragezeit | 60 s je Eintrag (Score prüfen) |
| `player:{uuid}:party` (vorh.) | STRING | partyId | wie Party |
| `party:invite:{target}` | HASH | partyId → inviter;expiresMs | 60 s |
| `party:public` | ZSET | partyId → Erstellzeit | – (bei Auflösung ZREM) |
| `party:grace:{uuid}` | STRING | partyId | 300 s (Offline-Karenz) |
| `msg:last:{uuid}` | STRING | uuid des letzten PN-Partners | 30 min |
| `msg:recent:{uuid}` | LIST | letzte 10 empfangene PN (JSON {from,text,ts}) | 24 h |
| `ignore:{uuid}` | SET | ignorierte uuids (Cache von `social.ignore`) | 10 min |
| `mute:{uuid}` | HASH | publicId, reasonCode, expiresMs (-1 perm) | Restlaufzeit, perm: keine |
| `ban:none:{uuid}` | STRING | „1“ = geprüft, kein Bann (Negativ-Cache) | 60 s |
| `nick:{uuid}` | HASH | name, texture, signature, since | solange aktiv |
| `nick:name:{lower}` | STRING | uuid (Reverse, Kollisionsschutz) | solange aktiv |
| `spam:{uuid}` | HASH | lastText, lastAt, repeatCount, burst | 30 s |
| `cd:report:{reporter}:{target}` | STRING | „1“ | 300 s |
| `cd:report:{reporter}` | STRING (INCR) | Reports/Stunde | 3600 s |
| `cnt:joinme:{uuid}:{yyyy-mm-dd}` | STRING (INCR) | Nutzung | 26 h |
| `cnt:clanrename:{clanId}:{yyyy-mm-dd}` | STRING (INCR) | Umbenennungen | 26 h |
| `chatbuf:{server}` | LIST | letzte 200 Chatzeilen (JSON) für `/chatlog` | 15 min |
| `maintenance` | HASH | enabled, reason, since | – |
| `afk:{uuid}` | – | (in presence.afk) | – |

### 2.4 Kanäle (Pub/Sub, `NetworkChannel`)

| Kanal | NEU? | Ereignisse (`type`) |
|---|---|---|
| `net:social` | vorh. | FRIEND_ONLINE, FRIEND_OFFLINE, FRIEND_REQUEST, FRIEND_ADDED, FRIEND_REMOVED, PARTY_UPDATE, PARTY_INVITE, PARTY_DISBAND, PARTY_WARP, CLAN_UPDATE, CLAN_ONLINE, CLAN_OFFLINE, PROFILE_INVALIDATE, NICK_CHANGED, AFK_CHANGED |
| `net:chat` | vorh. | GLOBAL_CHAT, PRIVATE_MSG (Zustellung an Proxy des Empfängers), PARTY_CHAT, CLAN_CHAT, STAFF_CHAT, BROADCAST |
| `net:moderation` | vorh. | PUNISH_ISSUED, PUNISH_REVOKED, MUTE_UPDATE, REPORT_NEW, REPORT_CLOSED, KICK |
| `net:server-state` | vorh. | MAINTENANCE_CHANGED (Server-Typen SERVER_UP/STATE/DOWN: Spec 08 §2.4). ~~TABLIST_OVERRIDE~~ entfällt: Tablist rendert nur paper-core (Spec 02 §4.4) |
| `net:proxy` | **NEU** | RUN_PROXY_COMMAND {uuid, commandLine} (Fallback, falls Plugin-Message nicht geht), CONNECT {uuid, server} |

Envelope (JSON): **Format aus Spec 08 §6.12** (`{"t":"<TYP>","v":1,"id":"<uuid>","ts":<ms>,"src":"<server|proxy-id>","re":"<id>?","d":{…}}`); die Typen oben stehen in `t`, die Nutzdaten in `d`.
Plugin-Message-Kanal **NEU** `retromc:bridge` (Paper→Velocity): `RUN_COMMAND <commandLine>` (GUI-Klick führt Proxy-Befehl als Spieler aus), `OPEN_GUI` (Velocity→Paper: `<guiId> <arg>`).

---

## 3. Befehle

Recht = LuckPerms-Node `retromc.<…>` (Präfix = `brandId`); „alle“ = ohne Node. Ausgabe-Keys → §7. Kurzbefehle sind echte Aliase.

### 3.1 Freunde

| # | Syntax | Aliase | Recht/Rang | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 1 | `/friend` | `/friends` (Annahme) | alle | Hilfe Seite 1 (klickbare Zeilen `suggest_command`) | `help.friend.1` |
| 2 | `/friend 2` | – | alle | Hilfe Seite 2 | `help.friend.2` |
| 3 | `/friend add <Spieler>` | – | alle | Anfrage, prüft Limits/Filter/Ignore; existiert Gegenanfrage → sofort befreundet | `friend.request.sent`, Empfänger `friend.request.received` + Klickzeile; Fehler `friend.err.self`, `.already`, `.pending`, `.limit.self`, `.limit.other`, `.blocked`, `error.player.unknown` |
| 4 | `/friend accept <Spieler>` | – | alle | Anfrage annehmen | `friend.added` (beide) |
| 5 | `/friend deny <Spieler>` | – | alle | ablehnen | `friend.denied` |
| 6 | `/friend acceptall` | `acceptAll` | alle | alle annehmen bis Limit | `friend.acceptall` ({n}) |
| 7 | `/friend denyall` | `denyAll` | alle | alle ablehnen | `friend.denyall` ({n}) |
| 8 | `/friend requests` | `/fr` | alle | öffnet GUI „Freundschaftsanfragen“ (§5.3) bzw. Chatliste außerhalb Paper | – |
| 9 | `/friend remove <Spieler>` | – | alle | entfernen (beidseitig) | `friend.removed` |
| 10 | `/friend list [Seite]` | – | alle | Chatliste, online zuerst, 10/Seite | `friend.list.header/.line/.empty` |
| 11 | `/friend jump <Spieler>` | – | alle (Annahme) | zum Server des Freundes, wenn `jump_from` erlaubt | `friend.jump.ok`, `.err.denied`, `.err.offline`, `.err.same` |
| 12 | `/friend favorite <Spieler>` | `fav` | alle | Favorit an/aus | `friend.fav.on/.off` |
| 13 | `/friend toggleonline` | – | alle | `show_online` umschalten | `friend.online.hidden/.visible` |
| 14 | `/friend toggle` | – | alle | Anfragen an/aus (`friend_requests`) | `friend.toggle.on/.off` |
| 15 | `/friend clear` → `/friend clear confirm` | – | alle | Liste leeren mit Bestätigung (60 s) | `friend.clear.confirm`, `friend.clear.done` |

### 3.2 Private Nachrichten, Ignorieren

| # | Syntax | Aliase | Recht | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 16 | `/msg <Spieler> <Text>` | `/tell`, `/whisper` (Annahme) | alle | PN; erlaubt laut `msg_from` des Empfängers (Default nur Freunde); Mute, Spam, Ignore geprüft; `msg:last` beidseitig | `msg.out`, `msg.in`; `msg.err.offline`, `.denied`, `.self`, `.ignored`, `mute.chat` |
| 17 | `/msg list` | – | alle | letzte 10 empfangene PN | `msg.list.header/.line/.empty` |
| 18 | `/r <Text>` | `/reply` | alle (Annahme) | an `msg:last` | `msg.err.noreply` |
| 19 | `/ignore <Spieler>` | – | alle (Annahme) | hinzufügen; Team nicht ignorierbar; max 100 | `ignore.added`, `.err.staff`, `.err.limit`, `.err.already` |
| 20 | `/ignore remove <Spieler>` | `/unignore` | alle | entfernen | `ignore.removed` |
| 21 | `/ignore list` | – | alle | Liste | `ignore.list.*` |

### 3.3 Party

| # | Syntax | Aliase | Recht | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 22 | `/party` / `/party 2` | – | alle | Hilfe 1/2 | `help.party.1/.2` |
| 23 | `/party invite <Spieler>` | `/pi` | Leiter (oder Mitglied, wenn keine Party → erstellt) | lädt ein, erstellt Party bei Bedarf; prüft `party_from`, Ignore, Größe | Einlader `party.invite.sent`; Ziel `party.invite.received` + `click.line` [ANNEHMEN][ABLEHNEN]; Fehler `party.err.full`, `.notleader`, `.already.member`, `.invite.denied`, `.already.invited` |
| 24 | `/party accept <Spieler>` | `/pa` | alle | Einladung von <Spieler> annehmen | an alle `party.joined`; `party.err.noinvite`, `.err.inparty` |
| 25 | `/party deny <Spieler>` | – | alle | ablehnen | `party.invite.denied` |
| 26 | `/party leave` | `/pl` | Mitglied | verlassen; Leiter → Nachfolger | `party.left`, `party.leader.new` |
| 27 | `/party kick <Spieler>` | `/pk` | Leiter | entfernen | `party.kicked` (Party), `party.kicked.you` |
| 28 | `/party promote <Spieler>` | `/ppm` | Leiter | Leitung übertragen (sofort, Annahme; 2×-Aufruf aus N17 nicht nötig) | `party.leader.new` |
| 29 | `/party jump` | `/pj` | Mitglied | zum Server des Leiters | `party.jump.ok`, `party.err.leader.same` |
| 30 | `/party pull` | – | Leiter | zieht alle Mitglieder auf eigenen Server (Lobby/Wartelobby) | `party.pull` |
| 31 | `/party chat <Text>` | `/p <Text>` | Mitglied | Partychat | `party.chat` |
| 32 | `/party list` | – | Mitglied | Mitglieder | `party.list.header`, `party.list.names` |
| 33 | `/party public` | `/pp` | Leiter, Premium+ | öffentlich/privat umschalten | `party.public.on/.off`, `error.rank.required` |
| 34 | `/party publicfilter <alle\|premium>` | – | Leiter, Premium+ | wer beitreten darf | `party.public.filter` |
| 35 | `/party requests <an\|aus>` | – | Leiter | Beitritt nur per Anfrage | `party.public.requests` |
| 36 | `/party join <Spieler>` | – | alle | öffentlicher Party von <Spieler> beitreten bzw. Anfrage | `party.joined` / `party.request.sent`; `party.err.notpublic`, `.err.filter` |
| 37 | `/party random` | – | alle | zufällige öffentliche Party mit Platz | `party.err.nopublic` |
| 38 | `/party disband` | – | Leiter (Annahme) | auflösen | `party.disbanded` |
| 39 | `/partytoggle [alle\|freunde\|aus]` | – | alle | `party_from` zyklisch/gesetzt | `party.toggle.*` |

### 3.4 Clan

| # | Syntax | Aliase | Recht | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 40 | `/clan` / `/clan 2` | – | alle | Hilfe | `help.clan.1/.2` |
| 41 | `/clan create <Name> <Tag>` | – | alle ohne Clan | Name 3–16, Tag 2–5 (§6.6) | `clan.created`; `clan.err.name`, `.tag`, `.name.taken`, `.tag.taken`, `.inclan` |
| 42 | `/clan invite <Spieler>` | – | Moderator+ | Einladung (7 Tage gültig, Annahme) | `clan.invite.sent`, Ziel `clan.invite.received` + Klick; `clan.err.full` |
| 43 | `/clan join <Clanname>` | – | alle ohne Clan | nimmt Einladung an | `clan.joined` (Clan) |
| 44 | `/clan request <Tag>` | – | alle ohne Clan | Beitrittsanfrage, wenn `join_mode=1` | `clan.request.sent`; Leitung `clan.request.received` |
| 45 | `/clan accept <Spieler>` | – | Moderator+ | Anfrage annehmen (→ Testmitglied) | `clan.joined` |
| 46 | `/clan deny <Spieler>` | – | Moderator+ (Annahme) | ablehnen | `clan.request.denied` |
| 47 | `/clan leave` | – | Mitglied | verlassen; Leiter muss vorher übertragen oder löschen | `clan.left`; `clan.err.leader.leave` |
| 48 | `/clan kick <Spieler>` | – | Moderator+ (nur niedrigere Rolle) | entfernen | `clan.kicked` |
| 49 | `/clan promote <Spieler>` | – | Leiter | Testmitglied→Mitglied→Moderator; Moderator→Leiter = Übertragung (Bestätigung) | `clan.promoted` |
| 50 | `/clan demote <Spieler>` | – | Leiter | eine Stufe runter | `clan.demoted` |
| 51 | `/clan info [Clanname\|Tag]` | – | alle | Infoblock | `clan.info.*` |
| 52 | `/clan list` | – | Mitglied | Mitglieder nach Rolle | `clan.list.*` |
| 53 | `/clan rename <Name> <Tag>` | – | Leiter, max 2×/Tag | Name und Tag ändern | `clan.renamed`; `clan.err.rename.limit` |
| 54 | `/clan delete` → `/clan delete confirm` | – | Leiter (Annahme) | löschen | `clan.deleted` |
| 55 | `/clan stats [Modus]` | – | alle | Clan-Stats (nur freigeschaltete Modi) | `clan.stats.*`, `clan.err.stats.disabled` |
| 56 | `/clan togglestats <Modus>` | – | Leiter | Modus freischalten/sperren | `clan.stats.toggle.on/.off` |
| 57 | `/clan cwstats <Clanname>` | – | alle | Moduswahl (Klickzeile) → Elo, Platz, Spiele, Siege, Niederlagen, Siegesquote | `clan.cwstats.*` |
| 58 | `/clan settings` | – | Mitglied | öffnet Clan-GUI-Einstellungen | – |
| 59 | `/cc <Text>` | `/clan chat` | Mitglied | Clanchat | `clan.chat` |
| 60 | `/clc <Text>` | – | Moderator+ | Leitungs-Chat | `clan.chat.lead` |
| 61 | `/clanlvl` | – | Mitglied | Clan-Level/XP-Fortschritt | `clan.level` |
| 62 | `/clanstatsreset <Modus>` | – | Leiter + 1 ResetToken | Elo des Modus auf 1000, Stats 0, CWs `hidden=true`; Bestätigung [JA][NEIN] | `clan.reset.confirm`, `.done`, `.err.notoken` |

### 3.5 Netzwerk, Ränge, Sprache, Einstellungen

| # | Syntax | Aliase | Recht | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 63 | `/l` | `/hub`, `/lobby`, `/leave` (`/hub` belegt, Rest Annahme) | alle | auf Lobby mit wenigsten Spielern (Silent-Lobby nur, wenn schon dort) | `lobby.already`, `lobby.none` |
| 64 | `/gameteleport <Schlüssel>` | – | alle | Modus-Lobby per Registry-Schlüssel (`classicbw`, `classicsw`, `duelsl`, `ttt`, `citybuild`, …) | `gameteleport.unknown` |
| 65 | `/rank` | – | alle | Rang + Restlaufzeit | `rank.info`, `rank.info.permanent` |
| 66 | `/joinme` | – | Supremium+ (3×/Tag) | Broadcast an alle Lobbys mit [BETRETEN] | `joinme.sent`, `joinme.err.limit`, `.err.lobby` |
| 67 | `/language [Code]` | `/sprache` (Annahme) | alle | ohne Arg: klickbare Liste; mit Arg: setzen | `lang.list.*`, `lang.set` |
| 68 | `/settings` | `/einstellungen` (Annahme) | alle | öffnet GUI §5.6 | – |
| 69 | `/nick` | – | `retromc.nick` (Supremium, Supremium+, Premium+, VIP, Team) | Lobby: Auto-Nick umschalten; Spielserver (Wartephase): Nick an/aus | `nick.current`, `nick.auto.on/.off`, `nick.removed`, `nick.err.phase` |
| 70 | `/unnick` | – | wie oben | Nick entfernen | `nick.removed` |
| 71 | `/nicks` | – | wie oben | eigener Nick; Team: alle Nicks des Servers | `nick.list.self`, `nick.list.line` |
| 72 | `/keepnick` | – | wie oben (Ära 18, Alias auf `nick_keep`) | Nick behalten an/aus | `nick.keep.on/.off` |

### 3.6 Report, Chatlog, Strafen, Team

| # | Syntax | Aliase | Recht | Verhalten | Ausgaben & Fehler |
|---|---|---|---|---|---|
| 73 | `/report <Spieler>` | – | alle | Klickliste der Gründe (nur die am Server zulässigen) | `report.choose`, `report.reason.line` |
| 74 | `/report <Spieler> <Grund>` | – | alle | Bestätigungsfrage [BESTÄTIGEN] | `report.confirm` |
| 75 | `/report <Spieler> <Grund> confirm` | – | alle | Report anlegen, Chatbuffer-Snapshot bei Grund `chat` | `report.thanks`; `report.err.self`, `.cooldown`, `.limit`, `.reason`, `.staff` |
| 76 | `/chatlog <Spieler>` | – | alle | Chatlog aus `chatbuf:{server}` (letzte 15 min, Zeilen des Ziels + Kontext) | `chatlog.created` ({url}); `chatlog.err.empty` |
| 77 | `/ban <Spieler> <Grund> [Dauer]` | – | `retromc.punish.ban` | Grund-Code aus `moderation.reason`; Stufe automatisch; Dauer überschreibt (`perm`, `30d`, `12h`) | `punish.ok`, Ziel: Kick mit Ban-Screen |
| 78 | `/mute <Spieler> <Grund> [Dauer]` | – | `retromc.punish.mute` | Mute | `punish.ok`, Ziel `mute.notice` |
| 79 | `/kick <Spieler> <Grund-Text>` | – | `retromc.punish.kick` | Kick vom Netzwerk | Kick-Screen `screen.kick` |
| 80 | `/unban <Spieler\|ID> <Notiz>` | `/pardon` | `retromc.punish.unban` | Revoke | `punish.revoked` |
| 81 | `/unmute <Spieler\|ID> <Notiz>` | – | `retromc.punish.unmute` | Revoke | `punish.revoked` |
| 82 | `/history <Spieler>` | – | `retromc.punish.history` | Strafliste (ID, Typ, Grund, Datum, Status) | `history.*` |
| 83 | `/reports` | – | `retromc.report.view` | offene Reports, klickbar [ÜBERNEHMEN] | `reports.*` |
| 84 | `/report close <ID> <ok\|abgelehnt>` | – | `retromc.report.view` | schließen → Reporter erhält Ergebnis | `report.result.*` |
| 85 | `/goto <Spieler>` | – | `retromc.staff.goto` | Team springt auf Server des Spielers | `goto.ok` |
| 86 | `/tc <Text>` | – | `retromc.staff.chat` | Teamchat | `staff.chat` |
| 87 | `/maintenance <on\|off\|add <Spieler>\|remove <Spieler>\|list>` | `/wartung` | `retromc.admin.maintenance` | Wartung schalten; bei `on` Nicht-Berechtigte kicken | `maintenance.on/.off/.added/.removed` |
| 88 | `/netreload` | – | `retromc.admin.reload` | `core.network_setting` + i18n neu laden | `admin.reloaded` |

**Gesamt: 88 Befehlszeilen** (76 Spieler-, 12 Team-Befehle; Aliase zusätzlich).

---

## 4. Anzeige-Elemente

### 4.1 Serverliste / MOTD (Velocity `ProxyPingEvent`)

| Element | Ära 26 (Standard) | Ära 20+ (Option) | Beleg |
|---|---|---|---|
| Zeile 1 | `§6⬇ §6§l{BRAND_UPPER}.NET §6⬇` zentriert (Leerzeichen-Padding auf 45 Zeichen) | `§e{brand}.net §7- §b{brand} Network §7- §f[§d1.8-1.21§f]` | V/N17 (2026 „⬇ VORBILD.NET ⬇“ gold), V (2017/2023-Layout) |
| Zeile 2 | News in Großbuchstaben, Hex-Verlauf (1.16+), Fallback `§9{WORT1} §d{REST}` aus `network_setting.motd.news` | gleich | N17, V („DUELS“ §9 + Rest §d-Verlauf) |
| Spielerzahl | `online/10000` (max konfigurierbar, Default 10000) | ältere Option 20000 | N05, V |
| Versionstext bei inkompatiblem Client | `1.21.11+` (Annahme) | – | N17 |
| Icon | `server-icon.png` 64×64 aus Konfig | – | – |
| Wartung | Zeile 2 `§c§lWARTUNGSARBEITEN`, Versionstext `§cWartung` (Protokoll -1) | – | Annahme |

### 4.2 Tablist

| Teil | Format | Ort | Beleg |
|---|---|---|---|
| Header Z. 0 (optional) | Ressourcenpaket-Glyphe Logo-Banner (Logo mittig, links Shop-URL, rechts Social-Handle) | Proxy, nur 1.21+ mit Pack | V 2025/26 (Annahme: Glyph-Font) |
| Header Z. 1 | `§e{brand}.net {modeColor}{serverDisplay}` (`serverDisplay` aus Spec 08 §4, Key `tab.header.mode` Spec 02) | **paper-core** (Spec 02 §4.4, einziger Renderer von Header/Footer) | N15, V |
| Header Z. 2 | `§7Regelverstoß gesehen? §c/report §7<Spieler>` (Key `tab.header.report`, Spec 02) | paper-core | Struktur belegt (N15/N17/V: „Du möchtest einen Spieler melden? /report <Spieler>“) |
| Footer Z. 1–2 | Keys `tab.footer.1`/`tab.footer.2` aus Spec 02 §7 (`§7Lade Freunde ein, starte Partys oder gründe` / `§7deinen eigenen Clan: §a/friend§7, §d/party §7und §e/clan`) | paper-core | N15, V (Farben §a/§d/§e) |
| Footer-Override | `TabService.setServerFooter(lines)` (Spec 02 §8.1), z. B. CityBuild „Du bist auf Server {x}“ + Booster | Backend (paper-core-API) | V |
| Eintrag | `[{prefixColor}{prefix} §7| ]{nameColor}{name}[ §7[§e{TAG}§7]]` ; für Mitglieder der eigenen Party statt Clan-Tag ` §7[§dParty§7]` (V „[Party]“; Format einheitlich mit Spec 03 §4.3/04 §4.4) | **paper-core** (Scoreboard-Teams) | N15, V |
| Eintrag Ära 18 | Trenner `§8┃` statt `§7|`; Spieler `§8` | Option `legacyStyle` | N15 |
| Sortierung | Spec 02 §6.2: `Player#setPlayerListOrder(order)` mit `order` = Rang-Gewicht aus §6.1 (höherer Wert steht oben), gleiches Gewicht alphabetisch; Legacy-Clients Teamname `"%04d%s"` mit `(9999−Gewicht)` | paper-core | V (SMP/CityBuild-Tab) |
| Spielmodus-Teams | in Runden: `{Teamfarbe}{Team} §7| {name}` (Spiel-Rahmen) | game-framework | V |

`modeColor`-Registry (Default, aus V): BedWars §c, SkyWars §b, Duels §9, TTT §c, CityBuild §6, SMP §d, Silent Lobby §c, Lobby §a (Annahme), sonst §a.

### 4.3 Scoreboard (nur Social-Zeilen; Board selbst = Kern-UI/Lobby)

Lobby-Board Ära 26 (V, mehrere Frames 2024–2026), Titel `§f§lVORBILD.NET` → `§f§l{BRAND_UPPER}.NET`:

| Zeile (Score) | Text | Farbe | Quelle Wert |
|---|---|---|---|
| 14 | (leer) | – | – |
| 13 | `Rang` ⇄ `Spielzeit` (Wechsel alle 5 s, Annahme) | §f | Kern-UI |
| 12 | `{RangAnzeigename}` ⇄ `{h} Stunden` | Rangfarbe ⇄ §6 | **RankService** / Stats |
| 11 | leer | | |
| 10–9 | `Coins` / `{coins}` | §f / §e | Economy |
| 8 | leer | | |
| 7–6 | `Gold` / `{gold}` | §f / §6 | Economy |
| 5 | leer | | |
| 4 | `Clan` | §f | – |
| 3 | `§b{online}§7/{total} §7[§b{tag}§7]` bzw. `§7-` (Keys `sb.value.clan`/`sb.value.no-clan`, Spec 02 §7) | | **ClanService** (online = presence ohne hidden) |
| 2 | leer | | |
| 1 | `Freunde` | §f | – |
| 0 | `§a{online}§7/{gesamt}` | | **FriendService** |

Ära 18: `Dein Clan:` / `{TAG}`, `Freunde:` / `{online}/{gesamt}` (N17, OCR). Zahlenformat de_DE Tausenderpunkt.

### 4.4 Chatzeilen (Rendering in paper-core, Daten aus Proxy)

| Kontext | Format | Beleg |
|---|---|---|
| Spielerchat lokal | `[§7[§e{TAG}§7] ]{nameColor}{name}§7: §f{msg}` (kein Rangpräfix im Chat) | belegt N15/V |
| Global (alle Lobbys) | `§7[§3Global§7] §7({level}) {nameColor}{name}§7: §f{msg}` | belegt N17/V 2024 |
| Global Ära 26 Option | `§7[{level}] {nameColor}{name}§7: §f{msg}` | OCR/B2 unscharf → Option |
| Farbiges GG (Supremium) | `{…}§7: §7§k--§{c} GG §7§k--` (für gg/gege/nd/wp/ggwp) | N15 (Paper, Spiel-Rahmen) |

### 4.5 Bossbar / Actionbar / Titles / Hologramme / NPCs / Hotbar

| Element | Inhalt in diesem Bereich | Beleg |
|---|---|---|
| Bossbar | keine (Season-/News-Bars = Lobby/Duels) | V |
| Actionbar | keine eigene; Wartungs-Countdown Annahme: `§cWartung in §e{s} §cSekunden` vor `/maintenance on` mit Vorlauf 60 s | Annahme |
| Titles | keine | – |
| Hologramme/NPCs | keine | – |
| Hotbar | Profil-Item (eigener Kopf, Slot 9) öffnet Profil → Untermenüs Freunde/Party/Clan/Einstellungen (Item gehört `lobby`, Menüs hier §5) | N05, V, B2-Hotbar |

---

## 5. GUIs (paper-core, `net.retromc.paper.core.social.gui`)

Konvention (aus B1/B2 abgeleitet): Kopf- und Fußreihe aus gefärbtem Glas (Name `§r `), Inhalte dazwischen, Status-Farbstoff direkt unter dem Schalter-Icon (grau = aus, lime = an, gelb/orange = eingeschränkt). Klicks auf Proxy-Funktionen senden `RUN_COMMAND` über `retromc:bridge` (§2.4). Spielerinventar unten bleibt sichtbar, Items darin sind gesperrt.

### 5.1 Partymenü – belegt Layout (B1, 2025), Funktionen Annahme

Titel `Partymenü` · 5 Reihen (45).

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–3, 5–8 | `PURPLE_STAINED_GLASS_PANE` | `§r ` | – |
| 4 | `CAKE` | `§5Party von {leader}` / `§7Mitglieder: §e{n}§7/§e{max}` / `§7Status: §a Öffentlich`∣`§cPrivat` | – |
| 9–26 | `PLAYER_HEAD` je Mitglied (Leiter zuerst, dann Beitrittsreihenfolge; >18 → Pfeile 39/41 entfallen, Blättern über 40) | `{nameColor}{name}` / `§7Rolle: §e{Leiter∣Mitglied}` / `§7Server: §e{server}` / ``/ (nur Leiter) `§eLinksklick§7: Leitung übertragen` / `§eRechtsklick§7: Rauswerfen` | Leiter: L → `/party promote {name}`, R → `/party kick {name}` (mit Bestätigung) |
| 27–35 | `PURPLE_STAINED_GLASS_PANE` | `§r ` | – |
| 36 | `GOLDEN_HELMET` | `§6Öffentliche Party` / `§7Ab Premium` / `§7Status: {§aAn∣§cAus}` | `/party public` |
| 37 | `IRON_CHESTPLATE` | `§eBeitrittsanfragen` / `§7Status: …` | `/party requests an/aus` |
| 38 | `FIREWORK_ROCKET` | `§aZum Leiter springen` | `/party jump` |
| 39 | `LEAD` | `§aParty nachziehen` / `§7Nur Leiter` | `/party pull` |
| 40 | leer (Seitenwechsel, wenn >18 Mitglieder: `ARROW` `§eNächste Seite`) | | Blättern |
| 41 | `BLAZE_ROD` | `§aSpieler einladen` | Chat-Vorschlag `/party invite ` |
| 42 | leer | | |
| 43 | `PURPLE_CONCRETE` | `§dPartyeinladungen` / `§7Von: §e{alle∣Freunde∣niemand}` | `/partytoggle` (zyklisch) |
| 44 | `PURPLE_CONCRETE` (Leiter: `§cParty auflösen`) | `§cParty verlassen` | `/party leave` bzw. `/party disband` (Bestätigung) |

Ohne Party: Slot 22 `CAKE` `§aParty erstellen` / `§7Lade Spieler mit /party invite ein` → Chat-Vorschlag.

### 5.2 Freunde (Annahme, Stil wie 5.1, Glas `RED_STAINED_GLASS_PANE` nach Präfixfarbe §4)

Titel `Freunde §8({n}/{max})` · 6 Reihen.

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–44 | `PLAYER_HEAD`, Reihenfolge: online Favoriten, online, offline Favoriten, offline (je alphabetisch) | `{nameColor}{name}`[` §e✦` Favorit] / online: `§7Status: §aOnline` / `§7Server: §e{server}` · offline: `§7Status: §cOffline` / `§7Zuletzt online: §e{dd.MM.yyyy}` / `§7Befreundet seit: §e{date}` / `` / `§eLinksklick§7: Nachspringen` / `§eRechtsklick§7: Optionen` | L → `/friend jump {name}`; R → GUI 5.2a |
| 45 | `ARROW` | `§eVorherige Seite` | blättern |
| 47 | `BOOK` (Anzahl = offene Anfragen, max 64) | `§aFreundschaftsanfragen` / `§7Offen: §e{n}` | GUI 5.3 |
| 49 | `COMPARATOR` | `§aEinstellungen` | GUI 5.6 |
| 51 | `NETHER_STAR` | `§eNur Favoriten anzeigen` / Status | Filter |
| 53 | `ARROW` | `§eNächste Seite` | blättern |
| 46,48,50,52 | `RED_STAINED_GLASS_PANE` | `§r ` | – |

5.2a **Freund-Optionen** `{name}` · 3 Reihen: 10 `ENDER_PEARL` `§aNachspringen` → jump · 12 `CAKE` `§dIn Party einladen` → `/party invite` · 14 `NETHER_STAR` `§eFavorit` Status → `/friend favorite` · 16 `BARRIER` `§cFreund entfernen` → Bestätigungs-GUI (11 `LIME_CONCRETE` `§aJa`, 15 `RED_CONCRETE` `§cNein`) · 18 `ARROW` `§7Zurück`.

### 5.3 Freundschaftsanfragen (`/fr`, Annahme)
Titel `Freundschaftsanfragen` · 6 Reihen · 0–44 Köpfe (älteste zuerst): Lore `§7Gesendet: §e{date}` / `` / `§aLinksklick§7: Annehmen` / `§cRechtsklick§7: Ablehnen` · 48 `LIME_CONCRETE` `§aAlle annehmen` → `/friend acceptall` · 50 `RED_CONCRETE` `§cAlle ablehnen` → `/friend denyall` · 45 `ARROW` `§7Zurück`.

### 5.4 Clan (Annahme, Glas `YELLOW_STAINED_GLASS_PANE` nach Tagfarbe §e)
Titel `Clan §8[{TAG}]` · 6 Reihen.

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 4 | `WHITE_BANNER` | `§e{Name} §7[§e{TAG}§7]` / `§7Mitglieder: §e{n}§7/§e{max}` / `§7Level: §e{lvl}` / `§7Gegründet: §e{date}` | – |
| 9–44 | Köpfe nach Rolle (Leiter, Moderatoren, Mitglieder, Testmitglieder), online zuerst | `{nameColor}{name}` / `§7Rolle: §e{Rolle}` / `§7Status: §aOnline∣§cOffline` / `§7ClanWar: §e{CW-Status}` / (Leitung) `§eLinksklick§7: Befördern` `§eRechtsklick§7: Degradieren` `§eShift+Rechtsklick§7: Rauswerfen` | jeweilige `/clan …` |
| 45 | `ARROW` | `§7Zurück` | Profil |
| 47 | `IRON_SWORD` | `§eClanWar-Statistiken` | Chat `/clan cwstats {name}` |
| 49 | `COMPARATOR` | `§aClan-Einstellungen` | GUI 5.5 |
| 51 | `PAPER` | `§eBeitrittsanfragen` / `§7Offen: §e{n}` | Liste (wie 5.3, Befehle `/clan accept/deny`) |
| 53 | `RED_CONCRETE` | `§cClan verlassen` | Bestätigung → `/clan leave` |
Ohne Clan: 22 `WHITE_BANNER` `§aClan erstellen` / `§7/clan create <Name> <Tag>` → Chat-Vorschlag.

### 5.5 Clan-Einstellungen (Annahme) · Titel `Clan-Einstellungen` · 4 Reihen (Schema wie B2)
11 `OAK_DOOR` `§eBeitritt` (Nur Einladung / Einladung + Anfrage) · 13 `NAME_TAG` `§eRänge hervorheben` (Leiter) · 15 `DIAMOND_SWORD` `§eStatistik-Modi` → Unter-GUI je Modus (Icon = Modus-Icon, Status-Farbstoff, Klick `/clan togglestats {mode}`) · Reihe 3 (20/22/24) Status-Farbstoffe · 26 `ENDER_EYE` `§eVor Clan verstecken` (ab Premium) + 35 Status · 27 `ARROW` `§7Zurück`. Beschreibung je Option in Lore (N05: „Claneinstellungen haben Beschreibungen“).

### 5.6 Einstellungen (Titel belegt: `Einstellungen für: {nameColor}{name}`, Thumbnail; Layout Annahme)
6 Reihen; Glas Reihe 0 und 5 `LIGHT_BLUE_STAINED_GLASS_PANE` (wie B2). Icons Reihe 1/3, Status-Farbstoff direkt darunter (Reihe 2/4).

| Icon-Slot | Item | Name (§e) / Lore (§f 2 Zeilen Beschreibung + `§7Aktuell: …`) | Werte (Klick zyklisch) | Status-Slot |
|---|---|---|---|---|
| 10 | `PLAYER_HEAD` | `Freundschaftsanfragen` | Alle / Ab Premium / Niemand | 19 |
| 12 | `ENDER_EYE` | `Online-Status` | Sichtbar / Versteckt | 21 |
| 14 | `PAPER` | `Private Nachrichten` | Alle / Freunde / Niemand | 23 |
| 16 | `CAKE` | `Partyeinladungen` | Alle / Freunde / Niemand | 25 |
| 28 | `ENDER_PEARL` | `Nachspringen` | Freunde / Nur Favoriten / Niemand | 37 |
| 30 | `BELL` | `Freunde-Meldungen` | An / Aus | 39 |
| 32 | `YELLOW_BANNER` | `Clan-Meldungen` | An / Aus | 41 |
| 34 | `OAK_SIGN` | `Globaler Chat` | An / Aus | 43 |
| 49 | `WRITABLE_BOOK` | `§eSprache` / `§7Aktuell: §e{lang}` | → Chat `/language` | – |
| 45 | `ARROW` | `§7Zurück` | Profil | – |
Status-Items: `LIME_DYE` `§aAktiviert`/Wert · `ORANGE_DYE` `§6{eingeschränkter Wert}` · `GRAY_DYE` `§cDeaktiviert`. Änderung sofort in DB + `PROFILE_INVALIDATE`.

### 5.7 Nicknamen – belegt (B2, 2026)
Titel `Nicknamen` (abgeschnitten „Nickn…“, Rest Annahme) · 4 Reihen.

| Slot | Item | Name / Lore | Klick |
|---|---|---|---|
| 0–7, 27–35 | `LIGHT_BLUE_STAINED_GLASS_PANE` | `§r ` | – |
| 8 | `RED_STAINED_GLASS_PANE` (B2: rötlich) | `§cZurück` (Annahme) | Profil |
| 11 | `NAME_TAG` | `§eNickname beim Rundenbeitritt` / `§fLege fest, ob du beim Betreten` / `§feiner Runde einen Nicknamen erhältst` | toggle `nick_on_join` |
| 15 | `PLAYER_HEAD` (Nick-Skin) | `§eNickname nach Rundenende behalten` / `§fBestimme, ob dein Nickname` / `§fnach einer Runde erhalten bleibt` (Original-Tooltip-Struktur belegt) | toggle `nick_keep` |
| 20 | `GRAY_DYE`∣`LIME_DYE` | `§cDeaktiviert`∣`§aAktiviert` | wie 11 |
| 24 | `GRAY_DYE`∣`LIME_DYE` | wie oben | wie 15 |

### 5.8 Sprachauswahl
Kein GUI: `/language` sendet Chatliste (belegt N05 „Auswahl per Klick im Chat“), §7 `lang.*`.

**GUIs gesamt: 11** (5.1, 5.2, 5.2a, Bestätigung, 5.3, 5.4, Clan-Anfragen, 5.5, Statistik-Modi, 5.6, 5.7).

---

## 6. Mechaniken & Zahlen

### 6.1 Rangliste (LuckPerms-Gruppen, Seed-Skript `tools/luckperms-seed.txt`)

| Gewicht | Gruppe | Anzeigename (Scoreboard/`/rank`) | Präfix (Tab/Nametag) | Farbe Präfix=Name | Beleg |
|---|---|---|---|---|---|
| 1000 | `spec` | Spectator (Annahme) | `Spec` | §7 | V (SMP-Tab ganz oben) – Bedeutung offen |
| 900 | `admin` | Administrator | `Admin` | §4 | N15 (§4 2016/2024), V Scoreboard „Administrator“ §4 |
| 850 | `srdev` | Senior-Developer | `SrDev` | §c | V |
| 800 | `dev` | Developer | `Dev` | §c | N15 2017, V |
| 750 | `srcontent` | Senior-Content | `SrContent` | §c | V |
| 700 | `content` | Content | `Content` | §c | N15, V |
| 650 | `srmod` | Senior-Moderator | `SrMod` | §c | N15, V Scoreboard |
| 600 | `mod` | Moderator | `Mod` | §c | N15, V |
| 550 | `sup` | Supporter | `Sup` | §c | N15, V |
| 500 | `builder` | Builder | `Builder` | §2 | Annahme (N05: Builder nicht rot) |
| 450 | `vip` | VIP | `VIP` | §d | V 2026 (Creator; Ära 18 Option `YouTuber` §5) |
| 400 | `supremiumplus` | Supremium+ | `Suprem+` | §d, Name Verlauf `#FF55FF→#55FFFF` (1.21+), Fallback §d | N05, V; Hex Annahme |
| 350 | `supremium` | Supremium | `Suprem` | §b | N05, N15, V |
| 300 | `premiumplus` | Premium Plus | `Premium+` | §e | N15, V |
| 200 | `premium` | Premium | – (kein Präfix) | §6 | N05, N15 |
| 0 | `default` | Spieler | – | §a (Ära 18: §8) | N15 |

- Trenner Präfix↔Name: ` §7| ` (26) bzw. ` §8┃ ` (18). Präfix und Name gleiche Farbe (N15).
- Teamränge-Widerspruch: V zeigt einzelne Team-Präfixe in §b/§d (CityBuild/1.8-Tab). **Entscheidung:** alle Team §c außer Admin §4; konfigurierbar je Gruppe (`meta.color`).
- LuckPerms-Meta je Gruppe: `meta.display`, `meta.color` (§-Code), `meta.gradient` (optional), `prefix.<weight>.<Präfix>`, `meta.party-limit`, `meta.friend-limit`, `meta.clan-slot-bonus`, `meta.joinme-per-day`, `meta.forcemap-per-day`.
- Zeitränge: LuckPerms temporäre Node; Shop setzt über `services:api`. `/rank` liest Ablauf (`expiry`).

### 6.2 Rang-Vorteile (Limits)

| Vorteil | Spieler | Premium | Premium+ | Supremium | Supremium+ | VIP/Team | Beleg |
|---|---|---|---|---|---|---|---|
| Party-Größe (inkl. Leiter, Rang des Leiters zählt) | 3 | 10 | 26 | 64 | 64 | 64 | N05, N17 (Premium+ „25 weitere“) |
| Freunde-Limit | 250 (Annahme) | 500 | 500 | 1000 | 1000 | 1000 | N05 |
| Clan-Slots (Bonus auf Basis 30, Rang des Leiters) | +0 | +2 | +2 | +5 | +5 | +5 | N05 (Basis 30 unverifiziert) |
| Öffentliche Party erstellen | – | ✓ | ✓ | ✓ | ✓ | ✓ | N05 |
| Vor Freunden/Clan verstecken | – | ✓ | ✓ | ✓ | ✓ | ✓ | N05 |
| Freundschaftsanfrage-Filter „Ab Premium“ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N05 |
| Nick | – | – | ✓ | ✓ | ✓ | ✓ | N05 |
| Silent-Lobby | – | – | – | ✓ | ✓ | ✓ | N05 |
| Volle Spielserver betreten | – | – | – | ✓ | ✓ | ✓ | N05 |
| Join-Priorität (Gewicht) | 0 | 0 | 0 | 1 | 2 | 3 | N05 |
| `/joinme` pro Tag | – | – | – | 3 | 3 | ∞ Team | N05 (3 vs 5 → 3) |
| `/forcemap` pro Tag (Spiel-Rahmen) | – | – | – | 10 | 30 | ∞ | N05 |
| Farbiges GG (Spiel-Rahmen) | – | – | – | ✓ | ✓ | ✓ | N05/N15 |
| Lobby: Doppelsprung/Fliegen (Lobby) | – | Doppelsprung | Fliegen | Fliegen | Fliegen | Fliegen | N05/N17 (unsicher) |

### 6.3 Login & Verbindung (Velocity-Events)
1. `PreLoginEvent`: Wartung aktiv und UUID nicht in Whitelist und ohne `retromc.maintenance.bypass` → `screen.maintenance` (LuckPerms-Prüfung erst nach Login möglich → bei Wartung Prüfung in `LoginEvent` wiederholen).
2. `LoginEvent`: aktiven Bann laden (`type='BAN' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>now())`, zusätzlich IP-HMAC in `ban_ip`); Negativ-Cache `ban:none` 60 s. Treffer → `screen.ban.perm` / `screen.ban.temp`.
3. Proxy voll (`online ≥ maxPlayers`): nur Join-Priorität ≥1 bis `hardLimit` (Default `maxPlayers+500`, Annahme), sonst `screen.full`.
4. `PostLoginEvent`: Upsert `core.player` (Name, Name-Historie), `player_ip`; Locale: erster Join → Client-Locale `de*`→`de_DE`, `de_AT/CH`→`de_DE`, sonst `en_US`; `profile:{uuid}` + `presence:{uuid}` schreiben; Mute-Cache füllen.
5. Erstes Ziel (`PlayerChooseInitialServerEvent`): `LobbyBalancer.pickLobby` (velocity-core, Regeln **Spec 08 §6.7**: Template `lobby`, `joinable`, unter `softCap`, wenigste Spieler; Silent-Lobby nie als Ziel).
6. Nach Ankunft: Party-Karenz prüfen (`party:grace:{uuid}` → wieder Mitglied, `party.rejoined`); `FRIEND_ONLINE` an Freunde mit `notify_friends` oder Favorit-`notify`, außer `show_online=false`; `CLAN_ONLINE` an Clanmitglieder mit `notify_clan`, außer `hide_from_clan`; `clan.last_active=now()`.
7. `DisconnectEvent`: presence löschen, `FRIEND_OFFLINE`/`CLAN_OFFLINE` (gleiche Filter), Party: `party:grace` 300 s setzen, nach Ablauf entfernen (Leiter → Nachfolger = ältestes Mitglied, `party.leader.new`); `last_quit`, `playtime_s`.
8. Serverwechsel: keine Proxy-Meldung. `KickedFromServerEvent` vom Spielserver → zurück in Lobby + `connect.kicked` ({server},{reason}); Lobby nicht erreichbar → Disconnect `screen.nolobby`.
9. Versionsprüfung beim Verbinden: `ServerInfo.minProtocol` (z. B. 1.21.11, GunGame/BuildFFA 1.8.9) → `connect.version`.

### 6.4 Freunde
1. `add`: Ziel existiert (`core.player`) → nicht selbst → nicht befreundet → keine offene Anfrage → Ziel ignoriert Absender nicht → Ziel `friend_requests=true` und `friend_req_from` erfüllt (1 = Absender-Gewicht ≥200) → beide unter Limit → Insert `friend_request` → Event `FRIEND_REQUEST` (online: Chat + Klickzeile).
2. Gegenseitige Anfrage → sofort Freundschaft (`FriendPair.of`), beide Anfragen löschen.
3. Max. offene eingehende Anfragen: 100 (Annahme; ältere bleiben, neue → `friend.err.limit.other`). Kein Ablauf.
4. Lobby-Linksklick mit Profil-Item auf Spieler = `/friend add` (lobby ruft Bridge, N05 ältere Mechanik, Option).
5. Online-Zahl im Scoreboard zählt nur Freunde mit presence und `hidden=0`.

### 6.5 Party
1. Erstellung implizit beim ersten `/party invite`; Party-ID = ULID; `maxSize` aus Leiter-Rang beim Erstellen, bei Leiterwechsel neu (überzählige bleiben, keine neuen Einladungen).
2. Einladung gültig **60 s** (Annahme), max 1 offene Einladung pro Ziel und Party.
3. Leiter betritt Spielserver (Queue/Join) → Party-Ticket: alle Mitglieder auf gleichem Proxy-Ziel; Spielrahmen versucht gleiches Team (Fehlermeldung des Modus belegt N15: „kein passendes Team …“).
4. `jump`: Mitglied → Server des Leiters (nur wenn Server-Status joinbar). `pull`: nur aus Lobby/Wartelobby, Mitglieder in laufender Runde werden übersprungen (`party.pull.skipped`).
5. AFK-Wechsel eines Mitglieds → `party.afk.on/.off` an Party (belegt N17).
6. Öffentliche Partys: Liste `party:public`; Filter PREMIUM = Gewicht ≥200; `requireRequest` → Anfrage 60 s, Leiter nimmt mit `/party accept <Spieler>` an.
7. Leere Party (1 Mitglied, keine offene Einladung) → nach 60 s auflösen (Annahme).
8. Partychat geht an alle Mitglieder netzwerkweit (`net:chat` PARTY_CHAT).

### 6.6 Clan
- **Name:** 3–16 Zeichen, Regex `^[A-Za-z0-9_\-]{3,16}$` (Annahme Zeichensatz). **Tag:** 2–5 Zeichen, Regex `^[A-Za-z0-9_$%.,+\-#üÜäÄöÖ]{2,5}$` (belegt N17, ab 2020). Eindeutig case-insensitiv (Index V1).
- **Rollen:** Leiter (1), Moderator, Mitglied, Testmitglied (neu Beigetretene; Rechte = Mitglied ohne Clan-Wars-Lineup, Annahme).
- **Max. Mitglieder:** 30 + Bonus Leiter-Rang (§6.2). Belegt Clans bis 32 Mitglieder (V).
- **Umbenennen:** max 2×/Tag (Europe/Berlin), Log in `clan_rename_log`.
- **Inaktivität:** Job täglich 04:00: `last_active < now()-60 days` → Clan löschen (Ära vor 12/2019: 90 Tage, Option).
- **Clan-Chat:** `/cc`, an alle online Mitglieder; Online/Offline-Meldungen (belegt).
- **Anzeige:** Chat vor dem Namen `§7[§e{TAG}§7] `, Tab/Nametag hinter dem Namen ` §7[§e{TAG}§7]` (belegt). In der Lobby-Chatzeile Clan-Tag ausgeblendet (N05) → **Entscheidung:** Tag im Chat überall anzeigen (V 2024 Lobby zeigt Tags), Option `hideClanInLobbyChat`.
- **Clan-Elo (pro Modus):** Start 1000 (Annahme), Elo-Formel Schach: `E_a = 1/(1+10^((R_b−R_a)/400))`, `Δ = round(K·(S_a−E_a))`, **K = 50**, Nullsumme (Verlierer −Δ). Nur Ranked-CW zählt (FunClanWar nicht).
- **CW-Limit/Tag pro Clan und Modus:** Standard 24 (N16 jüngere Angabe); Option Ära 2020: 20 + 10 (BAC-Queue) + 20 (alle Lineup ≥ Premium) = 50.
- **cwstats:** Elo, Ranking-Platz (`ROW_NUMBER() OVER (PARTITION BY mode ORDER BY elo DESC)`), Spiele, Siege, Niederlagen, Siegesquote `wins/games·100` mit 2 Nachkommastellen, deutsches Komma, ` %`.
- **Clan-XP/Level (`/clanlvl`):** Annahme: Ranked-CW-Sieg +100 XP, Niederlage +25, FunCW +10; Level n benötigt `500·n` XP kumulativ-stufenweise (`xpFür(n)=250·n·(n+1)`).
- **Clan-Statsreset:** 1 ResetToken (Economy), Elo → 1000, games/wins/losses → 0, CWs `hidden=true`.

### 6.7 Private Nachrichten / Ignore
1. Erlaubt, wenn Empfänger `msg_from`: 2 alle / 1 nur Freunde (Default) / 0 niemand; Team darf immer.
2. Ignore blockiert: PN, Party-Einladungen, Freundschaftsanfragen, Clan-Einladungen, Chatzeilen (lokal + global, Filter auf paper-core). Absender erhält generisch `msg.err.denied` (kein Hinweis auf Ignore).
3. `/msg list`: 10 Einträge, 24 h.
4. PN an genickten Spieler: über Nick-Namen adressierbar nur, wenn Absender Freund oder Team; sonst `error.player.offline`.

### 6.8 Global-Chat
- Alle Lobby-Server (mode=lobby, nicht silent) teilen Chat (`net:chat` GLOBAL_CHAT); Empfänger mit `global_chat=false` sehen nur Zeilen ihres Servers. Silent-Lobby: kein Chat-Empfang (Annahme).
- Spielserver: lokal (Spiel-Rahmen).
- Level-Wert kommt von Kern-UI/Stats (`profile.level`).

### 6.9 Report / Guardian
1. Gründe (Schlüssel = Befehlsarg): `hacking`, `chat`, `teaming` (Annahme), `randomkilling` (nur Servertyp TTT oder Replay, belegt), `name` (Annahme), `bugusing` (Annahme).
2. Kein Self-Report, kein Report gegen Team (`report.err.staff`, Annahme), Cooldown gleiches Ziel 300 s, max 10 Reports/Stunde.
3. `chat`: Chatbuffer-Snapshot → `moderation.chatlog` + `chatlog_id`.
4. Team-Benachrichtigung (online, `retromc.report.view`): `report.staff.new` mit [ÜBERNEHMEN] (→ `/goto`).
5. Abschluss → Reporter bekommt `report.result.ok` / `.rejected` (beim nächsten Login, falls offline).
6. Präfix-Wort: Original „Guardian“ → eigen **„Wächter“** (`prefix.report`).

### 6.10 Strafen
- **IDs** (`PunishmentId`): Bann manuell = 11 Zeichen Base62 (`[0-9A-Za-z]{11}`); Bann/Report-Herkunft = `R:` + 8 Zeichen `[0-9A-Z]{8}`; Anticheat = `X:` + 8; Mute = 8 Zeichen `[0-9A-Z]{8}`. Zufall `SecureRandom`, Kollision → neu (Unique-Index).
- **Grund-Stufen (Seed, Annahme außer markiert):**

| Code | Anzeige | Typ | Stufen (1 → 2 → 3) |
|---|---|---|---|
| `hacking` | Hacking | BAN | 30 d (belegt Beispiel) → 90 d → perm |
| `clientmod` | Unerlaubte Clientmodifikation | BAN | 30 d → perm (+ `ac_code`) |
| `ban_evasion` | Bannumgehung | BAN | perm |
| `teaming` | Teaming | BAN | 1 d → 7 d → 30 d |
| `randomkilling` | Randomkilling | BAN | 1 d → 3 d → 7 d |
| `bugusing` | Bugusing | BAN | 7 d → 30 d → perm |
| `name_skin` | Unangemessener Name/Skin | BAN | perm (bis Änderung, manuell entbannen) |
| `chat_respect` | Respektloses und provokantes Verhalten | MUTE | 3 d (belegt) → 7 d → 30 d |
| `chat_insult` | Beleidigung | MUTE | 7 d → 30 d → perm |
| `chat_spam` | Spam | MUTE | 1 d → 3 d → 7 d |
| `chat_ad` | Werbung | MUTE | 14 d → 30 d → perm |
- Stufe = Anzahl früherer, nicht widerrufener Strafen gleichen Codes + 1 (gedeckelt auf letzte Stufe).
- **Restzeit-Format:** absteigend ab größter Einheit ≠ 0 bis Sekunden: `{d}d {h}h {m}m {s}s` (belegt „29d 13h 42m 34s“, „2d 23h 9m 58s“); ohne Tage `13h 42m 34s`.
- Mute wirkt auf: Chat (paper-core), `/msg`, `/r`, `/p`, `/cc`, `/clc`, `/joinme`, Clan-/Party-Namen nicht betroffen. Anzeige beim Schreibversuch `mute.notice` (3 Zeilen).
- Bann während Online → sofortiger Kick mit `screen.ban.kick`.
- **Entbannung:** Antrag über Web (`{appealUrl}`, Default `https://{domain}/entbannung`) → Team `/unban` → `revoked_*` gesetzt, `PUNISH_REVOKED`, Cache löschen.
- **Signierter Chat:** Velocity bricht signierte Chatnachrichten **nie** ab (N08: bricht Signaturkette). Mute/Spam/Ignore/Format für normalen Chat auf paper-core (`AsyncChatEvent`); Proxy prüft nur Proxy-Befehle.

### 6.11 Nick
1. Rechte: `retromc.nick` (§6.2).
2. Nick nur auf Spielservern (`ServerInfo.category=GAME`); in Lobbys Originalname (abgeleitet aus „Nick beim Rundenbeitritt“).
3. Beim Betreten eines Spielservers mit `nick_on_join` → Nick aus `core.nick_pool` (enabled, nicht in `nick:name:*`, nicht als echter Spieler online) → `nick:{uuid}` → paper-core setzt Profil (Name+Skin), Anzeige als Rang **Premium (§6)** ohne Clan-Tag, Level ausgeblendet → `nick.current`.
4. Rundenende: `nick_keep=false` → Nick freigeben; `true` → gleicher Nick für nächste Runde (bis Logout).
5. `/nick` in Wartephase: an/aus für diese Runde; während laufender Runde `nick.err.phase`.
6. Stats/Reports intern immer auf echte UUID; Team sieht in `/nicks` Originalnamen.
7. Pool-Größe ≥ 2× gleichzeitige Nicker (Monitoring-Warnung < 50 frei).

### 6.12 AFK
- Erkennung paper-core: keine Bewegung/Rotation/Chat/Interaktion **300 s** (Annahme) → `afk=1`, Spieler: `afk.on`; erste Aktivität → `afk.off`.
- Proxy: `AFK_CHANGED` → Party-Meldung `party.afk.on/.off` (belegt).
- Tab: Name kursiv/grau `§7§o` (Annahme, V „AFK-Namen“ unscharf).

### 6.13 Einstellungen (Werte)
| Einstellung | Spalte | Werte | Default |
|---|---|---|---|
| Freundschaftsanfragen | `friend_requests` + `friend_req_from` | Alle(2) / Ab Premium(1) / Niemand(0 bzw. friend_requests=false) | Alle |
| Online-Status | `show_online` | sichtbar/versteckt | sichtbar |
| Private Nachrichten | `msg_from` | 2/1/0 | 1 Freunde |
| Partyeinladungen | `party_from` | 2/1/0 | 2 Alle (Annahme) |
| Nachspringen | `jump_from` | 1 Freunde / 2 Favoriten / 0 niemand | 1 |
| Freunde-/Clan-Meldungen | `notify_friends`/`notify_clan` | an/aus | an |
| Vor Clan verstecken | `hide_from_clan` | an/aus (ab Premium) | aus |
| Globaler Chat | `global_chat` | an/aus | an |
| Nick | `nick_on_join`, `nick_keep` | an/aus | aus |
| Sprache | `core.player.locale` | §6.14 | Client |

### 6.14 Sprachen
| Code (Locale) | Name in Liste | Status | Beleg |
|---|---|---|---|
| `de_DE` | Deutsch | Basis, Fallback | N05 |
| `en_US` | English | Pflicht | N05 |
| `bar` | Boarisch | Standard (Dialekt) | N15/N17 (Bairisch) |
| `gsw` | Schwiizerdütsch | Standard (Dialekt) | N15/N17 |
| `tr_TR` | Türkçe | Option | N15 (2022) |
| `ru_RU` | Русский | Option (entfernt laut N05) | N15/N05 |
| cs, el, es, fr, hu, ja, lb, lt, nl, no, pl, pt, sk, sv, zh | – | Option (nur bei Übersetzung) | N15/N17 (LabyMod-Liste) |
- Fallback-Kette: gewählte Sprache → (Dialekt → `de_DE`) → `de_DE`. `Messages.SUPPORTED` erweitern; Dateien `messages_bar.properties`, `messages_gsw.properties`.

### 6.15 Wartung
1. `/maintenance on`: Status in `core.network_setting` + Valkey `maintenance`, Event `MAINTENANCE_CHANGED`; Vorlauf 60 s Countdown-Broadcast (60, 30, 10, 5–1 s, Annahme), dann Kick aller ohne Bypass mit `screen.maintenance`.
2. Bypass: Whitelist-Tabelle oder `retromc.maintenance.bypass` (Team).
3. MOTD-Umschaltung §4.1.

### 6.16 Anti-Spam (Chat auf paper-core, Proxy für `/msg /r /p /cc /clc`)
| Regel | Wert | Ausnahme |
|---|---|---|
| Mindestabstand Nachrichten | 1,5 s (Annahme) | Premium+ 0,75 s, Team keine |
| Wiederholung gleicher Text (normalisiert, case/Leerzeichen) | blockiert innerhalb 30 s | Team |
| Burst | > 5 Nachrichten / 10 s → 10 s Sperre | Team |
| Großbuchstaben | ≥ 6 Buchstaben und > 50 % groß → in Kleinbuchstaben umwandeln | Team |
| Werbung | Regex IPv4/Domain (`\b[\w-]+\.(de|net|com|eu|gg|me|io)\b`, Whitelist eigene Domains) → blockiert + Team-Hinweis `spam.staff.ad` | Team |
| GG/GL | „gg“, „gege“, „gl“ nicht als Wiederholung werten (Rundenende) | – |

### 6.17 `/joinme`
Supremium+, 3×/Tag (Reset 00:00 Europe/Berlin), nur von Spielserver in Wartephase; Broadcast an alle Lobbys: `joinme.broadcast` + [BETRETEN] (→ `/server`-Connect über Bridge). Ära 2019-Option: 1×/Tag und nur bei < 1500 belegten Slots.

---

## 7. Nachrichten-Katalog

Präfixe (Keys `prefix.*`) werden in Formaten als `{p:friends}` usw. eingesetzt. `{name}` = Name in Rangfarbe (`{nameColor}{name}`), `{tag}` Clan-Tag. Klickzeile `click.line` = `{prefix}§7Klicke hier zum: §a§l[{LABEL}]` (Muster belegt N17; mehrere Buttons mit Leerzeichen).

| Key | Format (§, {Platzhalter}) | Standardtext (eigen) | Original-Referenz | Beleg |
|---|---|---|---|---|
| prefix.friends | `§7[§cFreunde§7] ` (LEGACY `§7[§4Freunde§7] `) | – | `§7[§4Freunde§7] ` | belegt (Code 18 §4; V 25/26 §c) |
| prefix.party | `§7[§dParty§7] ` (LEGACY `§7[§5Party§7] `) | – | `§7[§5Party§7] ` | belegt (Code 18 §5; V 25/26 §d) |
| prefix.clan | `§7[§cClans§7] ` | – | `§7[§cClans§7] ` | belegt |
| prefix.nick | `§7[§5NICK§7] ` | – | `[NICK]` lila | belegt |
| prefix.global | `§7[§3Global§7] ` | – | `[Global]` | belegt |
| prefix.report | `§7[§9Wächter§7] ` | – | `[Guardian]` (Farbe unbekannt) | abgeleitet (Farbe Annahme) |
| prefix.lobby | `§6[Lobby] ` | – | `[Lobby]` gold | belegt |
| prefix.joinme | `§7[§bJoinMe§7] ` | – | – | Annahme |
| prefix.staff | `§7[§cTeam§7] ` | – | – | Annahme |
| click.line | `{prefix}§7Klicke hier zum: §a[{LABEL}]` | „Klicke hier zum:“ | „Klicke hier zum: [ANNEHMEN]“ | belegt (funktional) |
| click.accept / click.deny / click.join / click.confirm / click.take | `§a[ANNEHMEN]` / `§c[ABLEHNEN]` / `§a[BETRETEN]` / `§a[BESTÄTIGEN]` / `§a[ÜBERNEHMEN]` | – | [ANNEHMEN], [ABLEHNEN] | belegt/Annahme |
| error.player.unknown | `§cDer Spieler §e{player} §cwurde nicht gefunden.` | gleich | – | Annahme |
| error.player.offline | `§cDer Spieler §e{player} §cist nicht online.` | gleich | – | Annahme |
| error.rank.required | `§cDafür benötigst du mindestens den Rang {rank}§c.` | gleich | – | Annahme |
| general.no-permission | `§cDafür hast du keine Berechtigung.` | vorhanden | – | vorhanden |
| help.friend.1 | Kopf `§7§m----------§r §4Freunde §7§m----------` + Zeilen `§e/friend add <Spieler> §8- §7Freund hinzufügen` … + `§7Weitere Befehle: §e/friend 2` | eigene Zeilen | `/friend`, `/friend 2` | abgeleitet |
| help.friend.2 / help.party.1 / help.party.2 / help.clan.1 / help.clan.2 | analog (Party-Kopf §5, Clan-Kopf §c) | eigene Zeilen | – | abgeleitet |
| friend.online | `{p:friends}{name} §7ist nun §aonline` | `… ist jetzt §aonline` | „… §7ist nun §aonline“ | belegt |
| friend.offline | `{p:friends}{name} §7ist nun §coffline` | `… ist jetzt §coffline` | „ist nun offline“ | belegt |
| friend.spectate | `{p:friends}{name} §eschaut dir jetzt zu` | `§esieht dir jetzt zu` | „schaut dir jetzt zu“ | belegt |
| friend.request.sent | `{p:friends}§7Du hast {name} §7eine Freundschaftsanfrage geschickt.` | gleich | – | Annahme |
| friend.request.received | `{p:friends}{name} §7möchte mit dir befreundet sein.` + `click.line` [ANNEHMEN] [ABLEHNEN] | gleich | – | Annahme |
| friend.added | `{p:friends}§7Du bist jetzt mit {name} §7befreundet.` | gleich | – | Annahme |
| friend.denied | `{p:friends}§7Du hast die Anfrage von {name} §7abgelehnt.` | gleich | – | Annahme |
| friend.acceptall / friend.denyall | `{p:friends}§e{n} §7Anfragen angenommen.` / `… abgelehnt.` | gleich | – | Annahme |
| friend.removed | `{p:friends}§7{name} §7ist nicht mehr dein Freund.` | gleich | – | Annahme |
| friend.list.header | `{p:friends}§7Deine Freunde §8(§a{online}§8/§7{total}§8) §7– Seite §e{page}§7/§e{pages}` | gleich | – | Annahme |
| friend.list.line | `§8» {name} §7– {§aOnline auf §e{server}∣§cOffline}` | gleich | – | Annahme |
| friend.list.empty | `{p:friends}§7Du hast noch keine Freunde. §e/friend add <Spieler>` | gleich | – | Annahme |
| friend.jump.ok | `{p:friends}§7Du springst zu {name}§7 …` | gleich | – | Annahme |
| friend.jump.err.denied | `{p:friends}§c{name} §cerlaubt kein Nachspringen.` | gleich | – | Annahme |
| friend.jump.err.offline / .err.same | `{p:friends}§c{name} §cist offline.` / `§cDu bist bereits auf diesem Server.` | gleich | – | Annahme |
| friend.fav.on / .off | `{p:friends}{name} §7ist jetzt ein §eFavorit§7.` / `… kein Favorit mehr.` | gleich | `/friend favorite` | Annahme |
| friend.online.hidden / .visible | `{p:friends}§7Dein Online-Status ist jetzt §cversteckt§7.` / `§asichtbar§7.` | gleich | toggleonline | Annahme |
| friend.toggle.on / .off | `{p:friends}§7Freundschaftsanfragen sind jetzt §aaktiviert§7.` / `§cdeaktiviert§7.` | gleich | – | Annahme |
| friend.clear.confirm | `{p:friends}§cWillst du wirklich alle §e{n} §cFreunde entfernen? §7Bestätige mit §e/friend clear confirm` | gleich | clear confirm | belegt (Ablauf) |
| friend.clear.done | `{p:friends}§7Deine Freundesliste wurde geleert.` | gleich | – | Annahme |
| friend.err.self / .already / .pending | `§cDu kannst dich nicht selbst hinzufügen.` / `§cIhr seid bereits befreundet.` / `§cDu hast {name} §cbereits eine Anfrage geschickt.` | gleich | – | Annahme |
| friend.err.limit.self / .limit.other | `§cDeine Freundesliste ist voll (§e{max}§c).` / `§c{name} §ckann keine weiteren Freunde annehmen.` | gleich | – | Annahme |
| friend.err.blocked | `§c{name} §cnimmt keine Freundschaftsanfragen an.` | gleich | – | Annahme |
| msg.out | `{p:friends}§aDu §7» {name}§e: {text}` | – | – | abgeleitet (Format wie msg.in) |
| msg.in | `{p:friends}{name} §7» §eDu: {text}` (Option 26 belegt: `{sender} §7» §e{empfänger}: {text}`) | – | „[Freunde] MatteoTheBuilder » Dreix31: …“ | belegt (V) |
| msg.list.header / .line / .empty | `{p:friends}§7Letzte Nachrichten:` / `§8[§7{hh:mm}§8] {name}§7: §f{text}` / `§7Keine Nachrichten.` | gleich | `/msg list` | Annahme |
| msg.err.offline / .denied / .self / .noreply | `§c{player} §cist nicht online.` / `§c{player} §cnimmt keine Nachrichten von dir an.` / `§cDu kannst dir nicht selbst schreiben.` / `§cDu hast niemanden, dem du antworten kannst.` | gleich | – | Annahme |
| ignore.added / .removed | `§7Du ignorierst jetzt §e{player}§7.` / `§7Du ignorierst §e{player} §7nicht mehr.` | gleich | – | Annahme |
| ignore.err.staff / .limit / .already | `§cTeammitglieder können nicht ignoriert werden.` / `§cDu kannst höchstens §e100 §cSpieler ignorieren.` / `§cDu ignorierst {player} §cbereits.` | gleich | – | Annahme |
| ignore.list.header / .line | `§7Ignorierte Spieler (§e{n}§7):` / `§8» §7{player}` | gleich | – | Annahme |
| party.invite.received | `{p:party}{name} §7hat dich in seine Party eingeladen` + `click.line` | `§7lädt dich in eine Party ein` | „§7hat dich in eine Party eingeladen“ | belegt (Struktur) |
| party.invite.sent | `{p:party}§7Du hast {name} §7eingeladen.` | gleich | – | Annahme |
| party.invite.denied | `{p:party}{name} §7hat deine Einladung abgelehnt.` | gleich | – | Annahme |
| party.invite.expired | `{p:party}§7Die Einladung an {name} §7ist abgelaufen.` | gleich | – | Annahme |
| party.joined | `{p:party}{name} §7ist der Party beigetreten` | `ist der Party beigetreten` | 26: „hat die Party betreten“; 18: „ist der Party beigetreten“ | belegt (18-Wortlaut als Standard, da funktional/eigen gegenüber 26) |
| party.left | `{p:party}{name} §7hat die Party verlassen` | gleich (funktional) | „hat die Party verlassen“ | belegt |
| party.kicked / .kicked.you | `{p:party}{name} §7wurde aus der Party entfernt` / `{p:party}§cDu wurdest aus der Party entfernt.` | gleich | – | Annahme |
| party.leader.new | `{p:party}{name} §7leitet jetzt die Party.` | gleich | – | Annahme |
| party.rejoined | `{p:party}§7Du bist wieder in der Party von {name}§7.` | gleich | – | Annahme |
| party.disbanded | `{p:party}§cDie Party wurde aufgelöst.` | gleich | – | Annahme |
| party.afk.on / .off | `{p:party}{name} §7ist jetzt abwesend` / `{p:party}{name} §7ist wieder da` | eigen | „ist nun (nicht mehr) abwesend“ | belegt (Struktur) |
| party.chat | `{p:party}{name}§7: §f{text}` | – | „[Party] Name: Text“ | belegt |
| party.list.header | `{p:party}§eMitglieder der Party von {name} §e({n})` | `§eParty von {name} §e({n} Mitglieder)` | „Mitglieder der Party vom Vorbild (4)“ | belegt (Struktur) |
| party.list.names | `§7{name1}§7, {name2}§7, …` | – | Namen §7/§e | belegt |
| party.jump.ok / party.pull / party.pull.skipped | `{p:party}§7Du springst zum Party-Leiter …` / `{p:party}§7Der Leiter holt die Party zu sich …` / `{p:party}§e{name} §7ist in einer Runde und wurde übersprungen.` | gleich | – | Annahme |
| party.public.on / .off / .filter / .requests | `{p:party}§7Die Party ist jetzt §aöffentlich§7.` / `§cprivat§7.` / `§7Beitritt: §e{alle∣Premium}` / `§7Beitrittsanfragen: §e{an∣aus}` | gleich | Public Party | Annahme |
| party.request.sent / .received | `{p:party}§7Anfrage an {name} §7gesendet.` / `{p:party}{name} §7möchte der Party beitreten` + click.line | gleich | – | Annahme |
| party.toggle.all / .friends / .off | `{p:party}§7Partyeinladungen: §aAlle` / `§eNur Freunde` / `§cNiemand` | gleich | /partytoggle | Annahme |
| party.err.full / .notleader / .noinvite / .inparty / .notpublic / .filter / .nopublic / .already.member / .invite.denied / .leader.same | je `§c…` Klartext (z. B. `§cDie Party ist voll (§e{max}§c).`, `§cNur der Party-Leiter kann das.`) | gleich | – | Annahme |
| clan.online / .offline | `{p:clan}{name} §7ist nun §aonline` / `§coffline` | `ist jetzt` | belegt Struktur | belegt |
| clan.chat | `{p:clan}{name}§7: §f{text}` | – | „[Clans] Name: Text“ | belegt |
| clan.chat.lead | `§7[§cClan-Leitung§7] {name}§7: §f{text}` | – | `/clc` | Annahme (Format) |
| clan.created | `{p:clan}§7Du hast den Clan §e{clan} §7[§e{tag}§7] gegründet.` | gleich | – | Annahme |
| clan.invite.sent / .received | `{p:clan}§7{name} §7wurde eingeladen.` / `{p:clan}§7Der Clan §e{clan} §7lädt dich ein` + click.line (`/clan join {clan}`) | gleich | – | Annahme |
| clan.request.sent / .received / .denied | `{p:clan}§7Anfrage an §e{clan} §7gesendet.` / `{p:clan}{name} §7möchte dem Clan beitreten` + click.line / `{p:clan}§7Anfrage von {name} §7abgelehnt.` | gleich | /clan request | Annahme |
| clan.joined / .left / .kicked | `{p:clan}{name} §7ist dem Clan beigetreten` / `… hat den Clan verlassen` / `… wurde aus dem Clan entfernt` | gleich | – | Annahme |
| clan.promoted / .demoted | `{p:clan}{name} §7ist jetzt §e{role}§7.` | gleich | – | Annahme |
| clan.renamed | `{p:clan}§7Der Clan heißt jetzt §e{clan} §7[§e{tag}§7].` | gleich | – | Annahme |
| clan.deleted | `{p:clan}§cDer Clan wurde aufgelöst.` | gleich | – | Annahme |
| clan.info.* | Block: `§7§m------§r §e{clan} §7[§e{tag}§7] §7§m------` / `§7Leiter: {name}` / `§7Mitglieder: §e{n}§7/§e{max}` / `§7Level: §e{lvl}` / `§7Gegründet: §e{date}` | eigen | – | Annahme |
| clan.list.* | `§7{Rolle}: {name}§7, …` je Rolle | eigen | /clan list | Annahme |
| clan.stats.toggle.on / .off | `{p:clan}§7Statistiken für §e{mode} §7sind jetzt §asichtbar∣§cverborgen§7.` | eigen | „Aktiviert/Deaktiviert einen Spielmodus für deinen Clan“ | belegt (Funktion) |
| clan.cwstats.* | Kopf `{p:clan}§7ClanWar-Statistiken von §e{clan} §7({mode})`; Zeilen `§7Elo§8: §e{elo}`, `§7Platz im Ranking§8: §e#{rank}`, `§7Gespielte Spiele§8: §e{games}`, `§7Siege§8: §e{wins}`, `§7Niederlagen§8: §e{losses}`, `§7Siegesquote§8: §e{rate} %` | eigen | Felder belegt N16 | belegt (Felder) |
| clan.level | `{p:clan}§7Clan-Level §e{lvl} §8(§e{xp}§7/§e{need} XP§8)` | eigen | /clanlvl | Annahme |
| clan.reset.confirm / .done / .err.notoken | `{p:clan}§cAlle ClanWar-Werte in §e{mode} §cwerden gelöscht. Für §e1 Token §cfortfahren?` + `§a[JA] §c[NEIN]` / `{p:clan}§7Die Werte in §e{mode} §7wurden zurückgesetzt.` / `§cDu hast keinen ResetToken.` | eigen | Reset-Dialog [Ja][Nein] (N16) | belegt (Struktur) |
| clan.err.name / .tag / .name.taken / .tag.taken / .inclan / .full / .leader.leave / .rename.limit / .stats.disabled | je `§c…` (z. B. `§cDer Clan-Tag muss 2–5 Zeichen lang sein.`, `§cDu kannst den Clan heute nicht mehr umbenennen (max. 2×).`) | eigen | Regeln belegt | Annahme (Text) |
| nick.current | `{p:nick}§cDein Nickname: §6{nick}` | `Dein Nickname:` | „§cAktueller Nickname: §6{nick}“ | belegt (Farben) |
| nick.auto.on / .off | `{p:nick}§cAutomatischer Nick ist jetzt §aan` / `§caus` | eigen | – | Annahme |
| nick.auto.set | `{p:nick}§cDein automatischer Nickname ist §6{nick}` | eigen | „Der automatische Nickname wurde auf X festgelegt“ | belegt (Struktur) |
| nick.removed | `{p:nick}§cDein Nickname wurde entfernt` | gleich (funktional) | „Dein Nickname wurde entfernt“ | belegt |
| nick.keep.on / .off | `{p:nick}§cNickname behalten: §aan` / `§caus` | eigen | /keepnick | Annahme |
| nick.list.self / .line | `{p:nick}§7{original}§7: §6{nick}` / `{p:nick}§7- {original}§7: §6{nick}` | – | „[NICK] - <Original>: <Nick>“ | belegt |
| nick.err.phase | `{p:nick}§cDas geht während einer laufenden Runde nicht.` | eigen | – | Annahme |
| afk.on / afk.off | `§aDu bist jetzt als abwesend markiert` / `§aDu bist nicht mehr als abwesend markiert` | eigen | „Du wurdest als abwesend markiert“ / „Du bist nun nicht mehr …“ | belegt (Farbe §a) |
| global.chat | `{p:global}§7({level}) {name}§7: §f{text}` | – | „[Global] (Level) Name: Text“ | belegt |
| report.choose | `{p:report}§7Warum möchtest du §e{player} §7melden?` | eigen | – | Annahme |
| report.reason.line | `§8» §e[{Grund}]` (klickbar `/report {player} {code}`) | eigen | Gründe chat/hacking/randomkilling | belegt (Gründe) |
| report.confirm | `{p:report}§7{player} §7wegen §e{reason} §7melden?` + click.line [BESTÄTIGEN] | eigen | `… confirm` | belegt (Ablauf) |
| report.thanks | `{p:report}§aDanke für deine Meldung! Du hörst von uns, sobald sie bearbeitet wurde.` | eigen | „Vielen Dank für deinen Report! Wir informieren dich, sobald es Neuigkeiten gibt“ | belegt (Referenz) |
| report.result.ok / .rejected | `{p:report}§aDeine Meldung gegen §e{player} §awar berechtigt. Danke!` / `{p:report}§7Deine Meldung gegen §e{player} §7führte zu keiner Strafe.` | eigen | – | Annahme |
| report.err.self / .cooldown / .limit / .reason / .staff | `§cDu kannst dich nicht selbst melden.` / `§cDu hast {player} §cgerade erst gemeldet.` / `§cDu hast zu viele Meldungen abgegeben.` / `§cDieser Grund ist hier nicht möglich.` / `§cTeammitglieder kannst du nicht melden.` | eigen | – | Annahme |
| report.staff.new | `{p:report}§e{reporter} §7meldet §c{player} §7(§e{reason}§7) auf §e{server}` + click.line [ÜBERNEHMEN] | eigen | – | Annahme |
| chatlog.created | `§7Chatlog von §e{player} §7erstellt: §e{url}` (klickbar) | eigen | „Der Chatlog … Adresse … https://chatlog…/<ID>“ | belegt (Struktur) |
| chatlog.err.empty | `§cVon {player} §cgibt es keine Nachrichten.` | eigen | – | Annahme |
| screen.ban.perm | Z1 `§cDu wurdest §4PERMANENT §cvom Netzwerk gebannt.` · Z2 `§3Grund: §c{reason} [{id}]` · Z3 leer · Z4 `§aEinen Entbannungsantrag kannst du unter §e{appealUrl} §astellen.` | Z4 eigen | 2016/17-Screen 4 Zeilen | belegt (Layout/Farben) |
| screen.ban.temp | Z1 `§cDu wurdest für §e{duration} §cvom Netzwerk gebannt!` · Z2 `§3Grund: §c{reason} [{id}]` · Z3 leer · Z4 `§3Verbleibende Zeit: §e{remaining}` · Z5 Appeal wie oben | gleich bis Z4 | Zeitbann-Screen | belegt |
| screen.ban.kick | Z1 `§cDu wurdest gebannt.` · Z2 `§3Grund: §c{reason} [{id}]` | gleich (funktional) | „Du wurdest gebannt. / Grund: Hacking [R:…]“ | belegt |
| screen.kick | Z1 `§cDu wurdest vom Netzwerk gekickt.` · Z2 `§3Grund: §c{reason}` | eigen | – | Annahme |
| mute.notice | Z1 `§cDu wurdest für {duration∣§4PERMANENT§c} aus dem Chat gebannt!` · Z2 `§eMutegrund§7: §c{reason} [{id}]` · Z3 `§aVerbleibende Zeit§7: §e{remaining}` | gleich (funktional) | Mute-Screen 2016 | belegt |
| punish.ok / punish.revoked | `§7{type} gegen §e{player} §7verhängt: §e{reason} §8[§7{id}§8] §7({duration})` / `§7Strafe §8[§7{id}§8] §7aufgehoben.` | eigen | – | Annahme |
| history.* | Zeile `§8[§7{id}§8] §e{type} §7{reason} §8| §7{date} §8| {§cAktiv∣§aAbgelaufen∣§7Aufgehoben}` | eigen | – | Annahme |
| screen.maintenance | Z1 `§c§l{BRAND} §cist gerade in Wartung.` · Z2 `§7Versuch es bitte später erneut.` | vorhanden `proxy.maintenance` (anpassen) | – | Annahme |
| maintenance.countdown | `§cWartung beginnt in §e{s} §cSekunden.` | eigen | – | Annahme |
| maintenance.on/.off/.added/.removed | `§7Wartung §aaktiviert`/`§cdeaktiviert`, `§e{player} §7darf während der Wartung joinen.` … | eigen | – | Annahme |
| screen.full | `§cDas Netzwerk ist voll.` / `§7Mit §bSupremium §7kommst du trotzdem rein.` | eigen | „Der Server ist voll.“ | belegt (Anfang) |
| screen.nolobby | `§cGerade ist keine Lobby erreichbar. Versuch es gleich noch einmal.` | eigen | – | Annahme |
| connect.kicked | `§cDie Verbindung zu §e{server} §cwurde getrennt: §7{reason}` | eigen | – | Annahme |
| connect.version | `§cFür diesen Modus brauchst du Version §e{version} §coder neuer.` | eigen | – | Annahme |
| lobby.already / lobby.none | `§cDu bist bereits in einer Lobby.` / = screen.nolobby | eigen | – | Annahme |
| gameteleport.unknown | `§cUnbekannter Spielmodus: §e{key}` | eigen | – | Annahme |
| rank.info / .permanent | `§7Dein Rang: {rankColor}{rank} §7– läuft ab am §e{date} §7(noch §e{remaining}§7)` / `§7Dein Rang: {rankColor}{rank} §7(dauerhaft)` | eigen | /rank zeigt Rang + Restlaufzeit | belegt (Funktion) |
| joinme.broadcast | `{p:joinme}{name} §7spielt §e{mode} §7auf §e{server}` + click.line [BETRETEN] | eigen | – | Annahme |
| joinme.sent / .err.limit / .err.lobby | `§7JoinMe gesendet (§e{used}§7/§e{max}§7 heute).` / `§cDu hast heute kein JoinMe mehr.` / `§cJoinMe geht nur in der Wartelobby einer Runde.` | eigen | – | Annahme |
| lang.list.header / .line / lang.set | `§7Wähle deine Sprache:` / `§8» §e[{name}]` (klickbar) / `§7Sprache geändert: §e{name}` | eigen | Auswahl per Klick | belegt (Mechanik) |
| spam.wait / .repeat / .burst | `§cBitte warte kurz, bevor du wieder schreibst.` / `§cBitte wiederhole dich nicht.` / `§cDu schreibst zu schnell. Warte §e{s} §cSekunden.` | eigen | – | Annahme |
| spam.ad / spam.staff.ad | `§cWerbung ist nicht erlaubt.` / `{p:staff}§e{player} §7(§e{server}§7): §f{text}` | eigen | – | Annahme |
| staff.chat | `{p:staff}{name}§7: §f{text}` | eigen | – | Annahme |
| goto.ok | `§7Du wirst zu §e{player} §7(§e{server}§7) verbunden …` | eigen | – | Annahme |
| admin.reloaded | `§aKonfiguration und Texte neu geladen.` | eigen | – | Annahme |
| tab.header | → Spec 02 §7 (`tab.header.mode`, `tab.header.report`) | – | „Du möchtest einen Spieler melden? /report <Spieler>“ | belegt (Struktur/Farben) |
| tab.footer | → Spec 02 §7 (`tab.footer.1`, `tab.footer.2`) | – | „Füge deine Freunde hinzu, erstelle Partys oder gründe deinen eigenen Clan mit: …“ | belegt (Struktur/Farben) |
| motd.line1 / motd.line2 | §4.1 | – | „⬇ VORBILD.NET ⬇“ | belegt |
| scoreboard.clan.none | → Spec 02 `sb.value.no-clan` (`§7-`); Ära 18 Option `§7Kein Clan` | – | „Kein Clan“ (OCR 18) | belegt (OCR) |

**Meldungen: 130 Katalogzeilen ≈ 215 Schlüssel** (Sammelzeilen `a / .b / .c` einzeln gezählt).

---

## 8. Schnittstellen

### 8.1 Services (core-api, Implementierung in core-data; Velocity und Paper nutzen dieselben)

| Interface | Methoden (Auszug) | Nutzer außerhalb |
|---|---|---|
| `RankService` | `RankInfo rank(UUID)`; `RankInfo` = id, displayName, prefix, legacyColor, gradient?, weight, limits(party, friends, clanBonus, joinmePerDay, forcemapPerDay), flags(nick, silentLobby, publicParty, fullServer, joinPriority, coloredGg) | Kern-UI (Scoreboard „Rang“), Spiel-Rahmen (`forcemap`, GG, volle Server), Lobby (Fortbewegung) |
| `ProfileService` | `Profile profile(UUID)` (aus `profile:{uuid}`), `invalidate(UUID)` | alle Paper-Plugins (Chat, Tab, Nametag) |
| `PresenceService` | `Optional<Presence> presence(UUID)`, `setAfk(UUID,bool)`, `serverOf(UUID)` | Lobby, Spiel-Rahmen |
| `FriendService` | `counts(UUID)→(online,total)`, `list`, `areFriends`, `request`, `accept`, `remove`, `favorite` | Kern-UI (Scoreboard, Profil), Global-Erfolg „10 Freunde“ (Stats) |
| `PartyService` | `partyOf(UUID)`, `members`, `leader`, `isLeader`, `maxSize` | Controller/Queue (Party-Ticket), Spiel-Rahmen (Team-Zuteilung, Tab „[Party]“) |
| `ClanService` | `clanOf(UUID)`, `counts`, `tag`, `recordWar(mode, clanA, clanB, winner, ranked, matchId)`, `elo(clanId, mode)`, `addXp` | `game:clanwar`, Kern-UI, Stats (Global-Erfolg „Clan beitreten“) |
| `PunishmentService` | `activeBan`, `activeMute(UUID)`, `issue(...)`, `revoke(...)` | Anticheat (`origin=AC`, `ac_code`), `services:api` (Entbannung), Paper-Chat |
| `ReportService` | `create`, `close`, `open()` | Spiel-Rahmen (Replay-Kontext), Web |
| `NickService` | `nickOf(UUID)`, `assign(UUID, serverCategory)`, `release(UUID)` | paper-core (Profil-Tausch), Spiel-Rahmen (Rundenende) |
| `SettingsService` | `settings(UUID)`, `set(UUID, key, value)` | paper-core GUIs |
| `IgnoreService` | `ignores(owner, target)` | paper-core Chatfilter |
| `ChatGuard` (Util) | `check(UUID, text, Context)→Verdict(OK, MUTED, SPAM_WAIT, REPEAT, BURST, AD, CAPS_FIXED)` | paper-core, Velocity |
| `EloCalculator` (pure) | `delta(int ra, int rb, double scoreA, int k)` | ClanService, `game:clanwar` |
| `PunishmentId` / `DurationFormat` (pure) | `newBanId()`, `newMuteId()`, `reportBanId()`, `format(Duration)` | Velocity, api |

### 8.2 Events (JSON über `net:*`, Records in `net.retromc.core.api.event`)

| Event | Kanal | Payload | Erzeuger → Verbraucher |
|---|---|---|---|
| `PlayerNetworkJoin` / `PlayerNetworkQuit` | net:social | uuid, name, proxy | Velocity → Lobby/Kern-UI (Spielerzahl), Stats (Spielzeit) |
| `ProfileInvalidate` | net:social | uuid, fields | Velocity/GUI → alle Paper (Tab/Nametag neu) |
| `FriendStatus` (ONLINE/OFFLINE/SPECTATE) | net:social | uuid, targets[] | Velocity / Spiel-Rahmen (Spectate) → Proxy-Zustellung |
| `PartyUpdate` | net:social | partyId, leader, members[], public | Velocity → Controller (Queue), Spiel-Rahmen |
| `PartyWarp` | net:social | partyId, server | Velocity → Controller |
| `ClanUpdate` | net:social | clanId, tag, members[] | Velocity → Paper (Tags) |
| `AfkChanged` | net:social | uuid, afk | paper-core → Velocity (Party-Meldung) |
| `NickChanged` | net:social | uuid, nick?, server | Velocity/paper-core → Spiel-Rahmen |
| `GlobalChat` | net:chat | uuid, server, text, level | paper-core Lobby → alle Lobbys |
| `PrivateMessage` / `PartyChat` / `ClanChat` / `StaffChat` | net:chat | from, to[], text | Velocity → Velocity (Multi-Proxy) |
| `PunishIssued` / `PunishRevoked` / `MuteUpdate` | net:moderation | id, uuid, type, expires | Velocity/api/Anticheat → alle |
| `ReportNew` / `ReportClosed` | net:moderation | id, reporter, target, reason, server | Velocity → Team-Proxy-Zustellung, Web |
| `MaintenanceChanged` | net:server-state | enabled | Velocity → alle |
| ~~`TablistOverride`~~ | – | – | entfällt (Integration): Footer-Override lokal über `TabService.setServerFooter` (Spec 02) |
| `RunProxyCommand` | Plugin-Message `retromc:bridge` | uuid, commandLine | paper-core GUI → Velocity |

### 8.3 Abhängigkeiten zu anderen Bereichen (getrennt baubar)
- **Kern-UI/Stats:** liefert `level`, `playtime`, Coins/Gold; liest `FriendService.counts`, `ClanService.counts`, `RankService.rank`. Bis verfügbar: `level=0`.
- **Spiel-Rahmen:** liest Party (Team-Zuteilung), Nick (Rundenstart/-ende), Rang-Flags; meldet `FriendStatus SPECTATE`, Rundenende → `NickService.release`. Bis verfügbar: Nick nur per `/nick` testbar.
- **Controller/Queue:** konsumiert `PartyUpdate`, Server-Registry `server:*` (state, mode, category, minProtocol, joinable).
- **Lobby:** Profil-Item öffnet GUIs (§5) über `SocialGuiApi.open(player, GuiId)`; Silent-Lobby-Flag aus `RankService`.
- **services:api:** POST `/appeal` (Entbannungsantrag), GET `/chatlog/{id}` (Seite), Shop-Webhook → LuckPerms-Gruppe.
- **Anticheat:** `PunishmentService.issue(uuid, "clientmod", origin=AC, acCode)`.

---

## 9. Abnahmekriterien

| # | Prüfung | Art |
|---|---|---|
| A1 | `ClanTag.valid`: `"ab"`,`"3IX"`,`"G$%.,"`,`"ÄöÜ#"` gültig; `"a"`, `"abcdef"`, `"a b"`, `"<>"` ungültig | Unit |
| A2 | `ClanName.valid` 3–16 Zeichen; Groß-/Kleinschreibung bei Eindeutigkeit ignoriert (DB-Test mit Testcontainers-PG) | Unit + IT |
| A3 | `EloCalculator.delta(1000,1000,1.0,50)=25`; `delta(1200,1000,1.0,50)=12`; `delta(1000,1200,1.0,50)=38`; Summe beider Seiten = 0 | Unit |
| A4 | `DurationFormat.format(29d13h42m34s)="29d 13h 42m 34s"`, `(2d23h9m58s)="2d 23h 9m 58s"`, `(13h0m5s)="13h 0m 5s"`, `(45s)="45s"` | Unit |
| A5 | `PunishmentId`: Bann `^[0-9A-Za-z]{11}$`, Report-Bann `^R:[0-9A-Z]{8}$`, Mute `^[0-9A-Z]{8}$`; 100 000 IDs ohne Duplikat | Unit |
| A6 | Party-Limit: Leiter Spieler → 4. Einladung `party.err.full`; Premium → 10; Supremium → 64 | Unit (RankInfo-Fake) |
| A7 | Freunde-Limit 250/500/1000; gegenseitige Anfrage → sofort befreundet; `FriendPair`-CHECK nie verletzt (Property-Test 10 000 UUIDs) | Unit + IT |
| A8 | Ban-Screen perm/temp exakt Zeilen/Farben wie §7 (Komponenten → Legacy-String-Vergleich) | Unit |
| A9 | Login mit aktivem Bann wird abgelehnt; abgelaufener Bann/revoked lässt durch; IP-Bann greift für Zweitaccount | IT (Velocity-Mock/DB) |
| A10 | Mute blockiert `/msg`, `/r`, `/p`, `/cc` auf Proxy und Chat auf Paper; signierter Chat wird von Velocity nie gecancelt (Code-Review-Regel + Test, dass kein `PlayerChatEvent#setResult(denied)` existiert) | Unit + Review |
| A11 | Ignore: ignorierter Spieler kann keine PN/Party-/Freundesanfrage senden, Absender bekommt nur `msg.err.denied` | Unit |
| A12 | `/r` antwortet an letzten Partner, auch nach Serverwechsel; nach 30 min `msg.err.noreply` | IT (Valkey) |
| A13 | Clan umbenennen: 3. Versuch am selben Tag → `clan.err.rename.limit`; Tageswechsel Europe/Berlin setzt zurück | Unit (Clock-Fake) |
| A14 | Inaktivitätsjob löscht Clan mit `last_active` = 61 Tage, nicht mit 59 Tagen | IT |
| A15 | Party-Leiter offline → nach 300 s neuer Leiter = ältestes Mitglied; Rejoin in Karenz → wieder Mitglied | IT (Clock-Fake) |
| A16 | AFK-Event → Party-Mitglieder erhalten `party.afk.on`, nicht der AFK-Spieler selbst | IT |
| A17 | Tab-Header/-Footer nach Serverwechsel korrekt (`modeColor` je Modus); Backend-Override wird übernommen | manuell + Unit |
| A18 | Scoreboard-Werte: Clan `1/9 [3IX]`-Format, Freunde `32/495`, versteckte Freunde zählen nicht online | Unit |
| A19 | Sprachen: fehlender Key in `bar` → `de_DE`-Text; `en_US` vollständig (Test: alle Keys aus `messages.properties` auch in `_en`) | Unit |
| A20 | Anti-Spam: 2. Nachricht nach 1,0 s blockiert (Spieler), nach 0,8 s erlaubt (Premium+); gleicher Text in 30 s blockiert; „gg“ nie | Unit |
| A21 | Report-Cooldown 300 s pro Ziel, 10/Stunde; `randomkilling` nur auf TTT/Replay | Unit |
| A22 | Wartung: Nicht-Whitelist wird mit `screen.maintenance` abgewiesen, MOTD zeigt Wartungszeile; Team kommt rein | IT |
| A23 | Nick: zwei Spieler erhalten nie denselben Nick (parallel 50 Zuweisungen); Nick nur auf GAME-Servern sichtbar | IT |
| A24 | GUIs: jede Klickaktion erzeugt genau den in §5 genannten Proxy-Befehl (Bridge-Mock); Glas-Slots nicht entnehmbar | Unit (paper-core, MockBukkit) |
| A25 | Alle Befehle aus §3 registriert inkl. Aliase; Tab-Vervollständigung für Spielernamen (online, Freunde zuerst) | Unit |
| A26 | Global-Chat erreicht zweite Lobby < 200 ms (lokal), nicht Silent-Lobby, nicht Spieler mit `global_chat=false` | IT |

---

## 10. Offene Punkte (je mit DEFAULT-ENTSCHEIDUNG)

| # | Offen | Widerspruch/Lücke | DEFAULT |
|---|---|---|---|
| O1 | Freunde-Limit ohne Rang | Forum „500 allgemein“ vs Shop 500/1000 | **250**, konfigurierbar (`meta.friend-limit`) |
| O2 | Party-Größe inkl. Leiter? Premium+ „25 weitere“ | unklar | Limit = **inkl. Leiter**; Premium+ 26 |
| O3 | Clan-Basisgröße 30, Bonus Bezug | „30“ unverifiziert, „Clan-Slots +2/+5“ unklar | 30 + Bonus nach **Leiter**-Rang |
| O4 | Präfix-Farben `[Freunde]`/`[Party]` | Code 18: §4/§5; Video 25/26: §c/§d | **MODERN §c / §d**, LEGACY (`legacyStyle`) §4 / §5 (abgestimmt mit Spec 02 §10 #6) |
| O5 | Team-Rangfarben | einzelne Frames §b/§d für Dev/SrMod/Content | alle Team **§c**, Admin **§4** |
| O6 | „Spec“-Rang (oberster Tab-Eintrag) | Bedeutung unbekannt | Gruppe `spec` §7 Gewicht 1000, nur für unsichtbares Team; nicht vergeben, bis geklärt |
| O7 | Spielerfarbe | §8 (18) vs §a (24+) | **§a**; `legacyStyle` → §8 |
| O8 | Global-Chat-Format 2026 | „[Global] (Lvl)“ (2024) vs „[Lvl] Name“ (2026, unscharf) | **[Global] (Lvl)**; Option `globalFormat26` |
| O9 | `/msg` nur unter Freunden? `/r`, `/ignore` existieren? | unbelegt | `/msg` Default nur Freunde (Einstellung erlaubt alle); `/r`, `/ignore` werden gebaut |
| O10 | PN-Format eigene Sicht | nur Empfängersicht belegt | `§aDu §7» {name}§e: {text}` |
| O11 | Partymenü-Slotfunktionen | nur Items belegt (B1) | Zuordnung §5.1 |
| O12 | Freunde-/Clan-/Einstellungs-GUI-Layouts | unbelegt | Layouts §5.2–5.6 im B1/B2-Stil |
| O13 | AFK-Zeit | unbelegt | **300 s** |
| O14 | Party-Einladung gültig | unbelegt | **60 s**; Party-Karenz offline **300 s** |
| O15 | Clan-Elo Startwert / CW-Limit | 1000 (V1) ; 24 vs 50 | **1000**, **24/Tag/Modus**; Option 2020-Regel (50) |
| O16 | Clan-XP-Formel | unbelegt | §6.6 (100/25/10 XP, `250·n·(n+1)`) |
| O17 | Strafdauern/Stufen | nur 2 Beispiele belegt | Tabelle §6.10, in `moderation.reason` änderbar |
| O18 | Report-Gründe außer chat/hacking/randomkilling | unbelegt | zusätzlich `teaming`, `name`, `bugusing` |
| O19 | Präfixwort „Guardian“ | Markenbegriff | **„Wächter“** (`prefix.report`), Farbe §9 |
| O20 | Nick-Darstellung | nur Addon-Indiz §6 | Nick erscheint als **Premium §6**, ohne Clan-Tag |
| O21 | Nick in Lobby sichtbar? | unbelegt | **nein**, nur GAME-Server |
| O22 | Wartungstexte/-MOTD | unbelegt | §7 `screen.maintenance`, MOTD §4.1 |
| O23 | Anti-Spam-Werte | unbelegt | §6.16 |
| O24 | Ressourcenpaket-Banner im Tab | Pack-Inhalt unbekannt | aus; Header-Zeile 0 erst mit Spec Ressourcenpaket |
| O25 | `/joinme` 3 vs 5 pro Tag | Shop-Widerspruch | **3** |
| O26 | Clan-Tag im Lobby-Chat | N05: ausgeblendet; V 2024: sichtbar | **sichtbar**; Option `hideClanInLobbyChat` |
| O27 | `[-] Name:` im Wartechat (1 Frame) | Bedeutung unklar | ignorieren (kein Platzhalter für „kein Clan“) |
| O28 | Lobby-Scoreboard Rang⇄Spielzeit | Wechsel beobachtet, Intervall unbekannt | **5 s** Wechsel (Kern-UI) |
| O29 | Sprachen 2026 | 19 Codes gelistet, Verfügbarkeit offen | de, en, bar, gsw ausliefern; Rest bei Übersetzung |
| O30 | Multi-Proxy | Vorbild-Setup unbekannt | von Anfang an Multi-Proxy-fähig (alle Zustellungen über `net:*` + `presence.proxy`) |

### 10.1 Integrationskorrekturen (Prüfung 2026-10-21, siehe `00_bauplan_uebersicht.md`)

| # | Widerspruch | Korrektur |
|---|---|---|
| K1 | Marke/Paket `dev.netzwerk`, Plugin `netzwerk-core`, Kanal `netzwerk:bridge`, Nodes `netzwerk.*` | Marke **RetroMC**, Pakete `net.retromc.*`, Velocity-ID `retromc-core`, Plugin-Message `retromc:bridge`, Nodes `retromc.*` (Präfix = `brandId`) |
| K2 | Migrationen `V2__proxy_social`, `V3__seed_reasons` kollidierten mit Spec 02 | `V1_1__proxy_social.sql`, `V1_2__seed_reasons.sql` (Schema `V<Spec>_<n>__name.sql`, Spec 08 W2) |
| K3 | Envelope `{type, ts, origin, payload}` ≠ Spec 08 §6.12 | Envelope nach Spec 08 (`t, v, id, ts, src, re, d`) |
| K4 | Tablist-Header/-Footer doppelt (Proxy hier, paper-core in Spec 02); Header ohne/mit `.net` je Spec | einziger Renderer paper-core (Spec 02 §4.4); Header `§e{brand}.net {modeColor}{serverDisplay}`; `TABLIST_OVERRIDE` entfällt; Footer-Texte = Spec-02-Keys |
| K5 | `profile:{uuid}` mit anderen Feldern/TTL in Spec 02 | eine Definition hier (§2.3), Felder vereinigt, camelCase |
| K6 | Scoreboard-Clanzeile `§b[{TAG}]`/`§7Kein Clan` ≠ Spec 02/03 | `§b{online}§7/{total} §7[§b{tag}§7]`, ohne Clan `§7-` |
| K7 | Party-Suffix ` §5[Party]` ≠ Spec 03/04 | ` §7[§dParty§7]` |
| K8 | `prefix.friends`/`prefix.party` §4/§5 ≠ Spec 02 (§c/§d) | MODERN §c/§d, LEGACY §4/§5 (O4 angepasst) |
| K9 | Tab-Sortierung `{weight:03d}` (3 Stellen bei Gewicht bis 1000) | Spec 02 §6.2 (`setPlayerListOrder` = Gewicht) |
| K10 | Erste Lobby-Wahl doppelt beschrieben (hier und Spec 08 §6.7) | Regeln nur in Spec 08 §6.7 (`LobbyBalancer`) |
| K11 | Spec 02 §6.12 und Spec 03 nutzten eigene Ränge (`elite`, `elite_plus`, `creator`) | **§6.1 hier ist die einzige Rangtabelle**; Elite → `supremium`, Elite+ → `supremiumplus`, Creator → `vip` |
| K12 | `/language` doppelt (hier #67 und Spec 02 §3) | Befehl gehört hierher (Velocity, Sprachen §6.14); Spec 02 rendert nur |
