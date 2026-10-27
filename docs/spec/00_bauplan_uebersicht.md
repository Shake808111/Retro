# Spec 00 – Bauplan-Übersicht (RetroMC)

Stand 2026-10-21 · Grundlage: Specs `01_proxy_social.md` bis `08_infrastruktur_dienste.md` (nach Integrationsprüfung) und das Projektgerüst `/home/user/netzwerk` (Marke **RetroMC**, `brandName=RetroMC`, `group=net.retromc`, Pakete `net.retromc.*`, Paper 26.3, Velocity 4.3.0, Java 25).
Zweck: ein Dokument zum Loslegen. Es legt fest, **wer was besitzt**, **in welcher Reihenfolge** gebaut wird, **welche Regeln für alle Module gelten** und **was nur der Nutzer entscheiden kann**.

---

## 1. Zuständigkeiten (bei Widerspruch gilt der Eigentümer)

| Thema | Eigentümer | Nutzer (lesen/aufrufen) |
|---|---|---|
| Ränge, Rangfarben, Präfixe, Gewichte, Rang-Vorteile | **Spec 01 §6.1/§6.2** (einzige Rangtabelle) | 02 (Anzeige), 03–07 |
| Freunde, Party, Clan (inkl. Clan-Elo K=50, `social.clan_war`), PN, Report, Strafen, Nick, AFK, Sprache (`/language`), Wartung, MOTD | Spec 01 | 02, 03, 05 (ClanWar), 07 |
| `profile:{uuid}`, `presence:{uuid}` | Spec 01 §2.3 | alle |
| Tablist (Header/Footer/Einträge/Sortierung), Scoreboard-API, Bossbar, Actionbar, Titles, GUI-Framework, Hologramme, NPCs, Nametags, Chat-Format | **Spec 02** (paper-core, einziger Renderer) | 01, 03–07 |
| Statistiken, Bestenlisten, Coins/Gold/ResetToken, globales Level (`NetworkLevelService`) | Spec 02 | 03–07, API |
| Lobby, Spiel-Rahmen (Phasen, Countdown, Teams, Zuschauer, Kill-Feed, Rundenende, Private Runden), `common.*`-Texte, `PhaseTimer`, `TeamAllocator`, Framework-Events | **Spec 03** | 04, 05, 06 |
| BedWars, SkyWars | Spec 04 | – |
| SurvivalGames, EnderGames, **TTT, Cores, MasterBuilders, ClanWar** | Spec 05 | 06 (nur Ergänzung) |
| Duels, PartyGames, Bingo, ItemChaos, LavaEscape, JumpLeague, GunGame, BuildFFA, Hide'n'Seek, historische Modi | Spec 06 | – |
| CityBuild, SMP | Spec 07 | – |
| Server-Registry `server:{name}`, Zustände, Templates, Namen, Lobby-Balancing, Queue/Zuteilung, `SEND_LOBBY`, Envelope, Map-Pipeline (`infra.map`, `map.json`, `MapService`), Controller, API, Discord-Bot, Docker, CI, Versionen, Recht | **Spec 08** | alle |

---

## 2. Modulgraph

Pfeil = „hängt ab von“. `(c)` = compileOnly (Laufzeit über `join-classpath` von `${brandName}Core`), `(s)` = eingebettet (Shadow). **NEU** = in `settings.gradle.kts` zu ergänzen.

```
                         core-api
                 ┌─────────┼─────────────┬──────────────┐
            core-i18n   core-data     core-stats NEU   (BuildInfo, DTOs, Events, ServerInfo)
                 │          │            │
                 │     core-messaging    │
                 └────┬─────┴────────────┘
          ┌───────────┴─────────────┐
   platform:paper-core (s: core-*)   platform:velocity-core (s: core-*)
          │   (Spec 02 + 01-GUIs + 08-Lifecycle)      (Spec 01 + 08-Registry/Balancer)
          │
   ┌──────┼───────────────┬────────────────────────┬──────────────┐
 lobby(c) game:game-framework (c: paper-core)   citybuild(c)      smp NEU (c)
          │   (s) in jedes Modus-Jar              └─ citybuild-core NEU (s, reines Java)
          │
   game:bedwars · game:skywars · game:survivalgames · game:endergames · game:ttt · game:cores
   game:masterbuilders · game:clanwar · game:gungame · game:duels NEU · game:duels-lobby NEU (→ duels)
   game:partygames NEU · game:bingo NEU · game:itemchaos NEU · game:lavaescape NEU
   game:jumpleague NEU · game:buildffa NEU · game:hideandseek NEU · game:legacy-modes NEU

 services:controller → core-api, core-data, core-messaging (+ Flyway, SnakeYAML)
 services:api        → core-api, core-data, core-stats (lesend), Gson
 services:discord-bot→ core-api, core-data, core-messaging, JDA
 tools:map-exporter  → core-api, core-data (nur upload), Gson; liest mapschema/<mode>.json aus Modus-Jars
```

Laufzeit-Kopplung (keine Gradle-Abhängigkeit): alle Server ↔ Valkey-Kanäle `net:*` (Envelope Spec 08 §6.12), Paper → Velocity über Plugin-Message `retromc:bridge`, Controller ↔ Server über `net:controller`/`net:queue`/`net:proxy`.

---

## 3. Bau-Reihenfolge in Phasen

Größen sind Schätzungen für Produktivcode + Tests + Ressourcen (Texte, YAML, SQL); Tests ≈ 30 % der Zeilen. Gerüst heute: 44 Java-Dateien, ≈ 1.200 Zeilen.

### Phase 1 – Kern + Proxy (Specs 01, 02, 08-Plattformteile)
| | |
|---|---|
| Module | `core-api`, `core-data`, `core-messaging`, `core-i18n`, **NEU** `core-stats`, `platform:paper-core`, `platform:velocity-core`, `db/migrations` |
| Liefergegenstände | Envelope-Codec + `NetworkChannel` (alle `net:*`), `ValkeyKeys`, `ServerInfo`/`ServerState`/`ServerRegistry`, `ServerLifecycle` + Heartbeat, `PlayerTransfer`, `LobbyBalancer`; Migrationen `V1_1__proxy_social`, `V1_2__seed_reasons`, `V2_1__core_ui_stats`, `V8_1__infra`; alle 88 Befehle aus Spec 01 (Freunde, PN, Party, Clan, Report, Strafen, Nick, Sprache, Wartung), Social-GUIs, Login/Ban/MOTD; paper-core-Dienste aus Spec 02 §8.1 (I18n, Chat, Tab, Board, Bossbar, Actionbar, Title, GUI, Hotbar, Coins, Stats, RankDisplay, Hologram, **Npc**, **Nametag**, **NetworkLevel**), Lobby-Scoreboard-Daten, `/stats`-Familie, ResetToken |
| Abnahme | Spec 01 §9 A1–A26, Spec 02 §9 #1–22, Spec 08 §9 #1–3, #6–7, #10, #14, #17–18, #21; Flyway-Reihenfolge `V1 → V1_1 → V1_2 → V2_1 → V8_1` grün; Smoke: Velocity + Lobby + Valkey + Postgres, Login, `/friend`, `/party`, Tab/Scoreboard korrekt |
| Größe | ≈ 270 Dateien, ≈ 27.000 Zeilen (01 ≈ 14.000, 02 ≈ 9.000, 08-Plattform ≈ 4.000) |
| Parallel | 3 Stränge nach gemeinsamem Start (core-api-DTOs + Envelope + `ValkeyKeys` zuerst, ≈ 1 Woche): **A** velocity-core (Spec 01 Proxy-Teil), **B** paper-core UI/Stats (Spec 02), **C** Registry/Lifecycle/Balancer (Spec 08). Social-GUIs (01 §5) nach B |

### Phase 2 – Spiel-Rahmen + Lobby (Spec 03)
| | |
|---|---|
| Module | `game:game-framework`, `lobby`, Controller-**Kern** aus `services:controller` (Ticket-Matching §6.8 + `StaticLauncher`, ohne Docker-Start) |
| Liefergegenstände | `GameLifecycle`/`PhaseTimer`/`CountdownPlan`, `WaitingRoom`, `TeamAllocator`, `MapSelection` + paper-core `MapService`, `SpectatorService`, `KillFeed`, `RoundEnd`, `common.*`-Katalog; Lobby: Hotbar, Navigator (`navigator.yml`), Lobbywechsler, Spieler verstecken, NPC-Join, Tagesbelohnung/Lotterie/Vote-Keys, Private Runden, `/lobbysetup`; Migration `V3_1__lobby_game` |
| Abnahme | Spec 03 §9 #1–24; Spec 08 §9 #9 (Queue), #10 (Reservierung); Ende-zu-Ende: NPC-Rechtsklick → Ticket → Testmodus „dummy“ startet → Rundenende → `SEND_LOBBY` in Modus-Lobby |
| Größe | ≈ 160 Dateien, ≈ 16.000 Zeilen (Rahmen ≈ 8.000, Lobby ≈ 7.000, Controller-Kern ≈ 1.000) |
| Parallel | Rahmen und Lobby getrennt (Lobby braucht nur `QueueClient`/`ServerRegistry`); Controller-Kern parallel; ein Dummy-Modus (`game:gungame`-Stub) dient als Testziel |

### Phase 3 – BedWars + SkyWars (Spec 04)
| | |
|---|---|
| Module | `game:bedwars`, `game:skywars`; Framework-Pakete `spawner`, `loot`, `kit`, `level`, `achievement` |
| Liefergegenstände | Shop/Schnellkauf/Upgrades/Fallen/Generatoren/Betten, Classic-Preset, SkyWars-Kits/Truhen/Border/Events/Challenges, `mapschema/bedwars.json`/`skywars.json`, Migration `V4_1__bedwars_skywars`, je ≥ 2 eigene Test-Maps |
| Abnahme | Spec 04 §9 #1–18 + U1–U10; Stats-Formeln (Spec 02 §9 #2) |
| Größe | ≈ 170 Dateien, ≈ 17.000 Zeilen |
| Parallel | BedWars und SkyWars vollständig parallel (gemeinsame Framework-Pakete zuerst, ≈ 3 Tage) |

### Phase 4 – CityBuild + SMP (Spec 07)
| | |
|---|---|
| Module | `citybuild`, **NEU** `citybuild-core`, **NEU** `smp`; Templates `cb-*`, `smp-*` (Spec 08 §6.5) |
| Liefergegenstände | Plotwelten/Plot-Generator, Economy (`cb.ledger`), Shops, Jobs, Level/Prestige, Gilden, Talismane, Bosse, Weltall, Crates, Booster, Quests, Tutorial, Warps/Homes, Chat-Kanäle (`net:cb-chat`); SMP (Wirtschaft, Kopfgeld, Kampf, `/sell`, Warteschlange); Migration `V7_1__citybuild_smp` |
| Abnahme | Spec 07 §9 #1–36 |
| Größe | ≈ 320 Dateien, ≈ 33.000 Zeilen (größte Phase; `citybuild-core` ≈ 4.000 Zeilen reine Formeln) |
| Parallel | 4 Stränge: Plot+Shop, Economy+Jobs+Level, Gilden+Talismane+Bosse+Weltall, SMP; `citybuild-core` zuerst |

### Phase 5 – SurvivalGames, EnderGames, Klassiker (Spec 05)
| | |
|---|---|
| Module | `game:survivalgames`, `game:endergames`, `game:ttt`, `game:cores`, `game:masterbuilders`, `game:clanwar`, velocity-core `clanwar` |
| Liefergegenstände | SG (Refill, Deathmatch), EG (49 Kits), TTT (Rollen, Karma, Pässe, Shop, Tester), Cores, MasterBuilders (Themen, Bewertung), ClanWar (`/cw`, Elo über `ClanService.recordWar`); Migration `V5_1__klassiker` |
| Abnahme | Spec 05 §9 #1–27 inkl. Unit-Tests |
| Größe | ≈ 230 Dateien, ≈ 22.000 Zeilen (EG-Kits allein ≈ 5.000) |
| Parallel | SG+EG, TTT, Cores+MB, ClanWar – 4 Stränge |

### Phase 6 – Weitere Minigames (Spec 06)
| | |
|---|---|
| Module | **NEU** `game:duels`, `game:duels-lobby`, `game:partygames`, `game:bingo`, `game:itemchaos`, `game:lavaescape`, `game:jumpleague`, `game:buildffa`, `game:hideandseek`, `game:legacy-modes`; `game:gungame` ausbauen |
| Liefergegenstände | Reihenfolge Spec 06: Duels (Elo/Saison/Turniere/Arenen) → PartyGames (Brett + Minispiel-API) → Bingo → ItemChaos → JumpLeague → GunGame → BuildFFA → LavaEscape → historische Modi (nur Private Runden); Migration `V6_1__minigames` |
| Abnahme | Spec 06 §9.1–9.6 |
| Größe | ≈ 290 Dateien, ≈ 28.000 Zeilen (Duels ≈ 7.000, PartyGames ≈ 7.000) |
| Parallel | jeder Modus ein Strang; Duels und PartyGames zuerst (P1) |

### Phase 7 – Dienste (Spec 08)
| | |
|---|---|
| Module | `services:controller` (vollständig), `services:api`, `services:discord-bot`, `tools:map-exporter`, `deploy/` (Templates, Dockerfiles), CI |
| Liefergegenstände | `DockerCliLauncher`, Skalierung/Drain/Neustart, Leader-Wahl, `/cloud`-Befehle + GUIs, Jobs (Aufbewahrung), REST-API `/v1/*`, Discord-Link/Rollen-Sync/Status, Map-Pipeline validate/pack/upload, Images, CI-Jobs `build`/`integration`/`smoke`, Renovate |
| Abnahme | Spec 08 §9 #4–5, #8, #11–13, #15–16, #19–20 |
| Größe | ≈ 120 Dateien, ≈ 11.000 Zeilen |
| Parallel | Controller, API, Bot, map-exporter unabhängig. **Vorziehen erlaubt:** map-exporter `validate` ab Phase 3 (Map-Bau), CI-`build` ab Phase 1 |

**Summe:** ≈ 1.560 Dateien, ≈ 154.000 Zeilen. Kritischer Pfad: Phase 1 → 2 → 3. Phasen 4, 5, 6 hängen nur von 1+2 ab (Phase 4 nur von 1) und können nach Phase 2 parallel laufen.

---

## 4. Gemeinsame Konventionen (gelten für alle Specs)

### 4.1 Code-Stil
- Java 25, Gradle Kotlin DSL, Versionen nur in `gradle/libs.versions.toml`, Repositories nur in `settings.gradle.kts` (vorhanden).
- `.editorconfig` + Checkstyle (Spec 08 §1.1): UTF-8, LF, 4 Leerzeichen, Zeilenlänge 120, keine Wildcard-Imports.
- DTOs als `record`; keine Lombok-Abhängigkeit; `@Nullable` aus JSpecify (Annahme, in VER eintragen); keine `null`-Collections.
- Bukkit-API nur auf dem Haupt-Thread; DB/Valkey nur asynchron (`CompletableFuture`), Rückkehr per `Bukkit.getScheduler().runTask`.
- Logik in plattformfreien Klassen (testbar ohne Server), Bukkit-/Velocity-Code als dünner Adapter.
- Keine Marke im Code: Name nur aus `BuildInfo`/`brandName`; CI-Grep auf `g[o]mme|g[o]mmehd` außerhalb `docs/` (Spec 08 §6.19).

### 4.2 Paketstruktur und Namen
| Element | Regel | Beispiel |
|---|---|---|
| Paket | `net.retromc.<modul>[.<bereich>]`; öffentliche API in `.api`, Bukkit-Events in `.event` | `net.retromc.paper.core.gui`, `net.retromc.bedwars.event` |
| Plugin-Name | `${brandName}<Modul>` | `RetroMCCore`, `RetroMCBedWars` |
| Velocity-ID | `<brandId>-core` | `retromc-core` |
| Rechte (LuckPerms) | `retromc.<bereich>.<recht>` | `retromc.punish.ban`, `retromc.game.start` |
| Valkey-Schlüssel | `<bereich>:<id>[:<teil>]`, Kleinbuchstaben | `party:{id}:members`, `lb:{mode}:30d` |
| Pub/Sub-Kanal | `net:<bereich>`; Typen GROSS_MIT_UNTERSTRICH; Envelope Spec 08 §6.12 | `net:queue` / `QUEUE_JOIN` |
| Plugin-Message / PDC / Font | Namespace `retromc:` | `retromc:bridge`, `retromc:action`, `retromc:banner` |
| Migration | `db/migrations/V<Spec>_<n>__<name>.sql`; nie eine ausgeführte Migration ändern | `V4_1__bedwars_skywars.sql` |
| DB-Schema | je Bereich: `core`, `social`, `moderation`, `stats`, `economy`, `integ`, `infra`, `lobby`, `games`, `duels`, `pg`, `bingo`, `jl`, `ic`, `cb`, `smp` | – |
| Servername | `{templateId}-{n}` | `lobby-3`, `bedwars-4x4-12`, `cb-farm-2` |

Migrationsliste: `V1__init` · `V1_1__proxy_social` · `V1_2__seed_reasons` · `V2_1__core_ui_stats` · `V3_1__lobby_game` · `V4_1__bedwars_skywars` · `V5_1__klassiker` · `V6_1__minigames` · `V7_1__citybuild_smp` · `V8_1__infra`.

### 4.3 Texte, i18n-Keys, Platzhalter
- Alle Spielertexte nur in `core-i18n` (`messages.properties` = `de_DE`, dazu `messages_en.properties`, `_bar`, `_gsw`); Spec-Notation `§x`/`{x}` wird MiniMessage `<farbe>`/`<x>`.
- Keys: Kleinbuchstaben mit Punkten, `<bereich>.<unterbereich>.<name>`. Präfixe `prefix.<x>`. Rahmen `common.*` (Spec 03), Kern `stats.*`, `top.*`, `sb.*`, `tab.*`, `coins.*`, `gui.*` (Spec 02), Social `friend.*`, `party.*`, `clan.*`, `msg.*`, `punish.*`, `lang.*` (Spec 01), Modi mit Kürzel (`bw.*`, `sw.*`, `sg.*`, `eg.*`, `ttt.*`, `cores.*`, `mb.*`, `cw.*`, `duels.*`, `pg.*` …), Infra `cloud.*`, `server.*`, `discord.*` (Spec 08). Ein Key wird genau in einer Spec definiert.
- Platzhalter Marke: `{brand}`/`<brand>` = `RetroMC`, `{BRAND_UPPER}` = `RETROMC`, Domain `retromc.net` (Platzhalter).
- Spalte „Original-Referenz“ in den Katalogen ist nur Quelle und wird nie ausgeliefert.

### 4.4 Verbindliche Formate (Kurzreferenz)
| Element | Format | Quelle |
|---|---|---|
| Rangfarben/-präfixe | Tabelle Spec 01 §6.1 (Admin §4, Team §c, Builder §2, VIP §d, Supremium+ Verlauf/§d, Supremium §b, Premium Plus §e, Premium §6, Spieler §a / Legacy §8) | 01 |
| Tab-Eintrag | `[{farbe}{präfix} §7| ]{farbe}{name}[ §7[§e{tag}§7]][ §7[§dParty§7]]`; Legacy-Trenner ` §8┃ ` | 01/02 |
| Chat | `[Kanal][Level][Kontext][§7[§e{tag}§7] ]{nameColor}{name}§7: §f{msg}` (kein Rangpräfix) | 02 §6.1 |
| Modus-Präfix | `§f[{modeColor}{Modus}§f] ` (alle Rundenmodi) | 02 §7 |
| System-Präfix | `§7[{farbe}{Name}§7] `; Social: `[Freunde]` §c, `[Party]` §d, `[Clans]` §c (Legacy §4/§5); `§6[Lobby] ` | 01 §7 |
| Textfarben | Fließtext §7, Zahlen §e, positiv §a, Fehler §c | 02 §6.1 |
| `modeColor` | BedWars §c, SkyWars §b, Duels §9, TTT §c, CityBuild §6, SMP §d, Lobby §a, Silent Lobby §c; übrige je Modus-Spec | 01 §4.2 |
| Scoreboard-Titel | `§f§l{BRAND_UPPER}.NET` | 02 §4.1 |
| Tab-Header | Z1 `§e{brand}.net {modeColor}{serverDisplay}`, Z2 `tab.header.report`, Footer `tab.footer.1/.2` | 02 §4.4, 08 §4 |
| Zahlen | de_DE Tausenderpunkt; `/stats`-Dezimal Punkt (`84.85 %`), Scoreboard Komma (`69,49 %`), Hologramm `27,59%` | 02 §6.8 |
| Dauer/Restzeit | `29d 13h 42m 34s`; Countdown `mm:ss` | 01 §6.10, 02 §6.8 |
| Countdown/Restart | Ansagen 60, 50, 40, 30, 20, 10, 5–1 (Singular bei 1); Restart 20 s, dann Stopp | 03 §6.5/§6.10 |
| Rundenblock | `§7-= §eStatistiken dieser Runde §7=-` … `§8----------------------` (22) | 02/03 |

### 4.5 Tests
- Unit: JUnit 6 für alle reinen Klassen (Formeln, Formatierer, Allokatoren, Codecs); jede Spec §9 ist Abnahmeliste.
- Integration: Testcontainers (`postgres:18.6-alpine`, `valkey/valkey:8.1.10-alpine`), Source-Set `integrationTest`, Flyway mit allen Migrationen.
- MockBukkit: erst nach Versionsprüfung für Paper 26.3 (Eintrag in `docs/VERSIONEN.md`); bis dahin laufen solche Tests nur mit Gradle-Tag `mockbukkit` und blockieren den Build nicht (Spec 08 O6).
- Smoke: `docker compose` (Velocity, Lobby, Controller) im CI-Job `smoke`.
- Katalogtest je Modul: jeder Key in `de_DE` und `en_US`, keine offenen `{`-Platzhalter.

### 4.6 Commit- und Branch-Regeln
- Ein Commit = eine logische Änderung, Build und Tests grün (`./gradlew build`).
- Betreff: `<bereich>: <was, Imperativ, deutsch>` (≤ 72 Zeichen), z. B. `proxy: Freundesliste mit Seiten`; im Text die Spec-Stelle (`Spec 01 §3.1`).
- Branches je Phase/Strang: `phase-1/velocity-social`, `phase-3/bedwars`; Merge per PR, CI muss grün sein.
- Migrationen, Rangtabelle, Envelope und `ValkeyKeys` nur mit Hinweis im PR-Titel (`[schema]`), weil alle Bereiche betroffen sind.
- Keine Geheimnisse im Repo (`.env` nur als `.env.example`); keine fremden Maps/Texte/Logos.

---

## 5. Bei der Integration korrigierte Widersprüche (Details in Abschnitt 10.1 jeder Spec)

| # | Widerspruch | Entscheidung |
|---|---|---|
| 1 | Marke/Paket: Specs nutzten `dev.netzwerk`/„Netzwerk“, Spec 08 W1 wollte zurückbenennen | **RetroMC / `net.retromc.*`** (Nutzer-Entscheidung, im Gerüst umgesetzt); Nodes `retromc.*`, `retromc:bridge`, Images/Map-Repo `retromc-*`; W1 verworfen |
| 2 | Doppelte Flyway-Versionen (`V2__` mehrfach, `V<n>` offen) | Schema `V<Spec>_<n>` überall |
| 3 | Rang „Elite/Elite+/Creator“ (Spec 02/03) fehlt in Spec 01 §6.1 | Elite → Supremium, Elite+ → Supremium+, Creator → VIP; Spec 02 §6.12 übernimmt Spec 01 (Gewichte 0–1000) |
| 4 | Tablist doppelt gerendert (Proxy + paper-core), Header mit/ohne `.net`/`§l`, `TABLIST_OVERRIDE` | nur paper-core; `§e{brand}.net {modeColor}{serverDisplay}`; Override entfällt |
| 5 | Scoreboard-Titel `{BRAND_UPPER}` vs. `{BRAND_UPPER}.NET` | `§f§l{BRAND_UPPER}.NET` |
| 6 | Modus-Präfix-Klammern §f / §7 / §8 | §f (Rundenmodi), §7 (Systeme) |
| 7 | Social-Präfixe §4/§5 vs. §c/§d | MODERN §c/§d, LEGACY §4/§5 |
| 8 | `profile:{uuid}`, Server-Status (`game:<server>:state`, eigene Kategorien, `phase`, `players`) mehrfach definiert | je eine Definition (01 §2.3, 08 §2.2) |
| 9 | Envelope-Format (01 vs. 08) | Spec 08 §6.12 |
| 10 | Map-Registry doppelt (`game.map`+`map.yml` vs. `infra.map`+`map.json`), zwei `MapService` | `infra.map` + `map.json`; Rahmen-Klasse `MapSelection` |
| 11 | Rückkehr in die Lobby `RETURN_TO_LOBBY` vs. `SEND_LOBBY` | `SEND_LOBBY {uuids, reasonKey, mode?}` + Templates `lobby-{mode}` |
| 12 | Stats: Spec 05/06 auf gelöschten `stats.total/monthly`, Monats-Bestenlisten | Spec-02-Modell (`daily`/`alltime`/`extra`, `lb:{mode}:30d|all`) |
| 13 | ClanWar: `games.cw_match` + K=32 vs. `social.clan_war` + K=50 | `social.clan_war`, `social.clan_elo`, K=50 |
| 14 | TTT/MasterBuilders doppelt in 05 und 06 (Tabellen, Zahlen, Kampfprofil) | Spec 05 führend |
| 15 | `common.*`, `/start`, `/forcemap`, Hotbars Wartelobby/Zuschauer in 04/05/06 abweichend | Spec 03 verbindlich |
| 16 | Framework-/Kern-API-Namen (`TeamService`, `CountdownService`, `SidebarService`, `StatsSink`, `EconomyService`, `GameWinEvent` …) | Namen aus Spec 02/03 |
| 17 | Fehlende Anbieter (`NetworkLevelService`, `NpcService`, `NametagService`) | in Spec 02 §8.1 ergänzt |
| 18 | CityBuild/SMP: `cb:*`-Kanäle, eigener AFK-Key, Templates fehlten in 08 | `net:cb-chat`/`net:cb-events`/`net:smp-events`, `presence.afk`, Templates `cb-*`/`smp-*` |
| 19 | Queue-Serverwahl (08 „meiste Spieler“ vs. 03 „STARTING zuerst“) | STARTING mit kleinstem Countdown → WAITING mit meisten Spielern |
| 20 | MockBukkit (01–03 planen Tests, 08 „kein MockBukkit“) | Tag `mockbukkit` nach Versionsprüfung |

---

## 6. Entscheidungen, die nur der Nutzer treffen kann (mit Default)

| # | Entscheidung | Default, falls nichts gesagt wird |
|---|---|---|
| E1 | **Markenname** und Domain | `RetroMC` (gesetzt, `gradle.properties` `brandName`); Domain `retromc.net` nur Platzhalter – Verfügbarkeit/Markenrecht prüfen, sonst nur `brandName` ändern |
| E2 | **GitHub-Repo** (Konto/Organisation, privat/öffentlich) | privates Repo `<dein-konto>/retromc` für Code, separates privates `<dein-konto>/retromc-maps` (Git LFS), Images in `ghcr.io/<dein-konto>/retromc-*` |
| E3 | **Hosting** | Entwicklung lokal mit Docker Compose; Staging/Produktion ein Hetzner-Dedicated-Server (Docker + `DockerCliLauncher`), Maps in Hetzner Object Storage; Kubernetes/Agones erst ab ≈ 500 gleichzeitigen Spielern |
| E4 | **Bedrock (Geyser/Floodgate)** ja/nein | **nein** zum Start (keine Spec deckt Bedrock-UI ab); später als Option auf dem Proxy |
| E5 | **Versionen** (Server und unterstützte Clients) | Paper 26.3 (Build 206), Velocity 4.3.0, Java 25; Clients 1.21.11+ für alles, 1.8.9 für GunGame/BuildFFA über ViaVersion/-Backwards/-Rewind auf den Paper-Servern |
| E6 | Rangnamen (Original-nah „Premium/Supremium“ vs. eigene) | Namen aus Spec 01 §6.1; vor Launch markenrechtlich prüfen, Änderung nur über LuckPerms-Meta |
| E7 | Standard-Ära (2025/26 vs. 1.8-Stil) | E26/MODERN überall; `legacyStyle` nur als Option |
| E8 | Umfang zum Start (welche Phasen vor dem ersten öffentlichen Test) | Phasen 1–3 + Phase 7 minimal (Controller, CI) = erster spielbarer Stand; CityBuild danach |
| E9 | Shop/Zahlungsanbieter für Ränge und Kosmetik | keiner bis Launch; nur kosmetische Vorteile/Ränge im Rahmen der Minecraft-Nutzungsrichtlinien |
| E10 | Discord-Server und Bot | Bot gebaut, aber nur mit `--profile bot` aktiv; Rollen-Zuordnung leer |
| E11 | Recht (Impressum, Datenschutz, DSA) | vor Launch anwaltlich prüfen; technische Pflichten aus Spec 08 §6.19 ab Phase 1 |

---

## 7. Nächste Schritte
1. Nutzer bestätigt E1–E5 (oder übernimmt die Defaults).
2. Phase 1 starten: `core-api`-DTOs, Envelope, `ValkeyKeys`, Migrationen `V1_1`, `V1_2`, `V2_1`, `V8_1`; danach Stränge A/B/C.
3. `settings.gradle.kts` um die NEU-Module erweitern, sobald die jeweilige Phase beginnt (nicht vorher, damit der Build grün bleibt).
