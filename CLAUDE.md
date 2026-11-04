# RetroMC – Übergabe und Arbeitsregeln für Claude

Diese Datei wird in jeder Claude-Code-Sitzung automatisch geladen. Sie enthält alles, was eine neue Sitzung
(z. B. auf dem PC des Nutzers) braucht, um nahtlos weiterzumachen.

## 1. Projekt in einem Absatz

Eigenes Minecraft-Netzwerk **RetroMC**: 1:1-Nachbau der Mechaniken, Zahlen, Abläufe und UI-Strukturen eines
großen deutschen Minigame-Netzwerks (Lobby, BedWars, SkyWars, SurvivalGames, EnderGames, TTT, MasterBuilders,
Cores, GunGame, CityBuild/SMP u. a.). **Nicht** übernommen werden Name, Logo, Maps, Code oder eigene Texte des
Vorbilds. Marke ausschließlich `RetroMC` (aus `gradle.properties` `brandName`), Paket `net.retromc`, Domain
`retromc.net` nur Platzhalter. Den Namen des Vorbilds nirgends ins Repo schreiben (`git grep -i 'g[o]mme'` muss 0
sein) – das Repo ist **öffentlich**.

## 2. Wo steht was

| Pfad | Inhalt |
|---|---|
| `docs/spec/00_bauplan_uebersicht.md` | Gesamtplan: Ownership §1, Modulgraph §2, Phasen §3, **Konventionen §4**, aufgelöste Widersprüche §5, Nutzer-Entscheidungen E1–E11 §6 |
| `docs/spec/01`–`08` | Verbindliche Bau-Specs je Bereich (01 Proxy/Social, 02 Kern-UI/Stats, 03 Lobby/Spielrahmen, 04 BedWars/SkyWars, 05 SG/EnderGames/Klassiker, 06 weitere Minigames, 07 CityBuild/SMP, 08 Infrastruktur). Jede Spec: §1 Umfang, §2 Daten, §3 Befehle, §4 Anzeige, §5 GUIs, §6 Mechaniken/Zahlen, §7 Texte, §8 Schnittstellen, §9 Abnahme, §10 offene Punkte |
| `docs/ARCHITEKTUR.md`, `docs/VERSIONEN.md` | Architektur, geprüfte Bibliotheksversionen |
| `core/` | `core-api` (Verträge/DTOs/Events), `core-data` (Postgres/Valkey, `ValkeyKeys`, Testhilfen), `core-messaging` (Bus, Envelope, RequestReply), `core-i18n` (Textkatalog de/en/bar/gsw), `core-stats` |
| `platform/paper-core`, `platform/velocity-core` | Basis-Plugins RetroMCCore (Paper) und retromc-core (Velocity) |
| `game/game-framework`, `game/bedwars` | Spiel-Rahmen (Spec 03) und BedWars (Spec 04); übrige `game/*`, `lobby/`, `citybuild/` noch Gerüste |
| `db/migrations/` | Flyway: V1, V1_1, V1_2, V1_3, V2_1, V2_2, V4_1, V8_1 (+ später V3_1, V5_1 … V7_1; `outOfOrder`) |
| `deploy/bedwars/` | Map-Setup-Anleitung (`/bwsetup`) und Beispiel-`map.json` |
| `deploy/lobby/` | Lobby-Map-Einrichtung (Statuen, Kisten, Hologramme), `warps.yml` (Navigator-Ziele), Map-Credits |
| `docker/` | Compose für Entwicklung (Postgres 18.6, Valkey 8.1.10, Velocity) |

## 3. Stand (bitte bei jedem Meilenstein aktualisieren)

- **Fertig in `main`:** Specs 00–08; F1a Verträge (core-api, core-messaging, core-data inkl. Testcontainers-
  Testhilfen, 5 Migrationen, `ValkeyKeys`); F2 Textkatalog; Lobby-Map-Einrichtung; F1b Plattform-Gerüste
  (neues Modul `platform/platform-common`: Feature-Muster, Service-Registry mit Standard/Ersatz, Env-Config,
  Verbindungen, Executor, Bridge-Codec, Cloud-Fehler, `I18n`; paper-core: Cloud-Commands, Bridge, GUI-Framework
  mit ChestGui/PagedGui/ConfirmGui, ItemBuilder; velocity-core: Commands, Bridge). Voller Build grün.
- **Paket 2 Social/Moderation (`p2/social`, in `main`):** core-data Repos (Clan, Freunde, Party, Präsenz, Ignore,
  Einstellungen, Profile, Nick, PN, JoinMe, Strafen, Reports/Chatlog, Spam-Filter, Wartung, Netz-Einstellungen) mit
  ITs; platform-common `social` (Standard-Dienste `SocialServices`, `BusEventSink`, LuckPerms-Ränge,
  `ModerationTexts`); velocity-core Features Freunde, Party, JoinMe, Msg/Ignore, Clans, Reports/`/goto`, Strafen
  (`/ban /mute /kick /warn /unban /unmute /history`, Login-Sperre), Wartung, MOTD/`/netreload`, Nick, Präsenz;
  paper-core `social` (GUIs §5.1–5.7 als testbare `GuiPlan`s, Chat-Schutz, Nick-Anwendung, AFK). Offen:
  Clan-/Party-Meldungen außer Chat nur auf dem Proxy
  des Auslösers; Login (`/l`, Lobby-Wahl), Ränge-Befehle, Global-Chat, `/tc` noch nicht gebaut.
- **Paket 3 Kern-UI/Stats (`p3/ui-stats`, in `main`, Spec 02 §9 #1–22 abgehakt):** core-api `StatsStore`,
  `StatsMode`, `StatWindow`, `economy` (`WalletService`), `NetworkLevelService`, `DisplayAnchorStore`; core-stats
  (Punkteformel, Ranking-Score, Fenster, Coin-Boost, `StatsReport`, Regeln, Gleichstand); core-data
  `StatsRepository` (Rundenende idempotent, ZSETs `lb:{mode}:30d|all`, Rollover/Reconcile/Partitionen, Reset),
  `WalletRepository`, `DisplayAnchorRepository`, `NetworkLevelRepository`, Profilfelder Coins/Gold/Level/verborgen;
  Migration V2_2 (Modus-Icon); platform-common `StatsServices`, `ColorText`, `PlayerLocales`; velocity-core
  `/language` (`/sprache`); paper-core Features Profile, Chat-Format, Tab, Nametags, Scoreboard (FastBoard, Lobby
  §4.2), Bossbar/Actionbar/Newsticker, Titles, Hotbar, Coins (`/coins`, `/tokens`, Boost), Statistik (`/stats`,
  `/statsall`, `/statsd`, `/togglestats`, `/top`, `/resetstats`/`/ja`, `/statsadmin`, Profil-GUI, Jobs),
  Hologramme/Wände (`/statswall`), NPCs (Mannequin). Nick-Anzeige (Premium, ohne Clan-Tag/Level) über `ProfileView`.
  Offen: Party-Kennung im Tab je Betrachter (`[Party]`, braucht Pakete), LEGACY-Ära je Spieler (`ui.era`) nur in den
  Formatern, Spielzeit-Zwischenspeicherung alle 5 min (Proxy bucht die Sitzung beim Logout).
- **Paket 4a Spielrahmen + BedWars (`p4/bedwars`, in `main`):** paper-core `server` (`ServerLifecycle` mit
  Registrierung/Heartbeat 5 s/TTL 15 s/`SERVER_STATE`, `PlayerTransfer` → `SEND_LOBBY`/`CONNECT`, `QueueClient`
  mit `player:{uuid}:ticket`), `map` (`MapService`/`MapFeature`: fertige Welten `maps/<id>/` + `map.json`,
  `MapJson`), `BoardSpec`-Scores je Zeile, `NametagService.nameColor`; core-messaging `GAME_ENDED`; Flyway
  `outOfOrder` (V3_1 … V7_1 nach V8_1). game-framework (Spec 03 §6.5–§6.13 ohne Hub-Lobby): `MiniGamePlugin`/
  `GameMode`/`Game`, Wartelobby + Countdown (`CountdownPlan`, `PhaseTimer`), Teams (`TeamAllocator` BALANCE/
  PREFER_FILLED, `TeamGui`), Map-Auswahl/`/forcemap`/Voting/`MapDraft`, Zuschauer, Kill-Feed, Rundenende
  (`SubmitOnce`, Block → Restart → Coins, `SEND_LOBBY`, `GAME_ENDED`, Stopp), Server-Status, Privatrunden-Kontext,
  Runden-Chat, `/start /fm /game /team /teleporter`. BedWars (Spec 04 BedWars-Teil): modern + Classic-Preset,
  Betten/Respawn/Final Kills, Generatoren, Shop (`shop.yml`) + Schnellkauf + Shop-Einstellungen, Upgrades, Fallen,
  Sonder-Items, Board §4.2, Gold-Vote, `/bwsetup` (Map-Setup), Migration `V4_1`, `BedWarsStore`/`ModeLevelStore`.
  Texte `common.*`, `game.*`, `bw.*` (de/en/Soll spec03/spec04). Maps/Wartelobby kommen als fertige Welten
  (`deploy/bedwars/README.md`, Beispiel `deploy/bedwars/maps/beispiel/map.json`). Offen: Ranked-Option,
  Achievements (brauchen Spec-05-Infrastruktur), `/pr kick|max|close`, Valkey-Caches `bw:qs`/`bw:ss`/`lvl`,
  Teamkameraden durch Wände, Party-Suffix im Tab, Wartelobby-Händler-NPC.
- **Paket 5 Fixes + Nick-Pool (`p5/fixes-nick`, in `main`):** Fehler aus dem ersten Server-Test behoben
  (Hikari `setDriverClassName`, Shadow behält `netty-resolver-dns`/`netty-codec-dns` für Lettuce,
  `MessageRenderer.renderLines` trennt erst nach dem Rendern → `splitLines`). Erreichbar gemacht: `/friend`,
  `/party`, `/p`, `/clan` ohne Argument öffnen ihr Menü über `OPEN_GUI` (Hilfe: `help`/`1`/`2`), `/stats` ohne
  Modus öffnet das Statistik-Profil, News-Bossbar in Ära LEGACY (`NETWORK_UI_ERA`), `/npc create|list|remove`
  (`retromc.npc.admin`, nicht dauerhaft). Keine Vanilla-Join/Quit-Meldungen; Erst-Sprache aus den
  Client-Einstellungen (`SettingsService.initialLocale`, nur erste Sitzung); Freunde-GUI-Pfeile nur bei Bedarf.
  Nick-Pool (Spec 01 §6.11 Nr. 7–8): core-data `nick` (`NickNameGenerator`, `NickNameFilter` über
  `SpamFilter`/`SpamRules.BLOCKED_WORDS`, `MojangApi` Namens-API + Sessionserver `?unsigned=false`,
  `MineSkinApi` optional, `RequestPacer` 1,1 s/429-Sperre, `NickPoolRepository`), Migration V1_3
  (`core.nick_skin`), velocity-core `/nickpool fill|list|disable|stats` (`retromc.nick.pool`), Monitoring < 50
  frei, `/nicks` fürs Team (eigene Zeile zuerst), Auto-Nick meldet `nick.auto.set`, Reports/Chatlog/`/goto` lösen
  Nicks auf die echte UUID auf. Betrieb: `NETWORK_NICK_SKIN_UUIDS` (Quell-UUIDs) setzen, optional
  `NETWORK_MINESKIN_KEY` (nur in `docker/.env`), dann `/nickpool fill 200`. Offen: echte Quell-UUIDs/Skins als
  Betriebsdaten, LEGACY-Ära je Spieler (`ui.era`), Lobby-Plugin.
- **Andocken:** Ränge, Profile, Texte und Datenbankzugang nur über die `ServiceRegistry` holen
  (`PaperCorePlugin.services()` / `VelocityCorePlugin.services()`); ein Adapter an ein bestehendes Kern-Plugin
  ersetzt sie per `register(...)`. Neue Teilsysteme = eine Zeile in `features()` der Plugin-Hauptklasse.
- **Als Nächstes – Phase 1 Stränge** (je ein eigener Branch `p1/<name>`, danach mergen):
  - **A** Rest von Spec 01: Login/Lobby-Wahl (`/l`, `/hub`, `/gameteleport`), `/rank`, `/settings`
    (Befehl), globaler Chat, `/tc` (Social, Moderation, Freunde, Party, Nick, Wartung, MOTD: Paket 2, fertig;
    `/language`: Paket 3, fertig).
  - **B** paper-core UI/Stats nach Spec 02: erledigt in Paket 3.
  - **C** Plattform-Teile Spec 08 (ServerRegistry Heartbeat 5 s/TTL 15 s, Lifecycle, PlayerTransfer,
    LobbyBalancer, /server /send /whereami /glist /cloud, infra-Repos).
  - **D** paper-core Social: erledigt in Paket 2 (GUIs §5, Mute/Spam/Ignore, Nick-Anwendung, AFK).
  - Grenzen: Chat-**Format**/Tab/Nametag = B; Chat-**Durchsetzung**, Social-Tab-Anteile, Nick-Anwendung = D.
- **Paket 4b SkyWars** auf dem Spiel-Rahmen (`SpectatorService`, `KillFeed` mit Kit/Killer-Leben, `kit.Kit`,
  Loot, Events, Challenges; Tabellen `games.sw_*` stehen schon in `V4_1`; Setup analog `/bwsetup` mit `MapDraft`).
- **Danach:** Merge + voller Build + Abnahmeprüfung (Spec 01 §9 A1–A26, Spec 02 §9 #1–22, Spec 08 §9 #1–3,
  #6–7, #10, #14, #17–18, #21), Smoke-Test lokal (Velocity + Lobby + Postgres + Valkey: Login, /friend, /party,
  Tab, Scoreboard). Dann Phase 2 (Lobby-Plugin inkl. Navigator/Kompass mit `deploy/lobby/warps.yml`).
- Nutzer-Entscheidungen E1–E11 (Spec 00 §6) stehen auf Default, außer: Repo = öffentlich `Shake808111/Retro`.

## 4. Arbeitsweise (Wünsche des Nutzers – verbindlich)

- **Sprache:** Deutsch. Nach jedem Schritt kurzer, konkreter Zwischenbericht (was gebaut, Dateien/Zeilen,
  Tests, Abweichungen). Nie lange still sein.
- **Qualität:** „Es muss perfekt werden.“ Exakt nach Spec, vollständige Tests, keine TODOs.
- **Plugin-Code** (alles, was Plugins/Spiellogik schreibt): stärkstes Modell mit Reasoning-Effort **sehr hoch
  (xhigh)**. Nebenarbeiten (Maps, Doku, Skripte, Textkatalog-Einträge): **mittel**.
- **Token sparen** (der Nutzer hat mehrfach das Limit erreicht – das hat Priorität):
  - Specs **nie komplett lesen**, nur die nötigen Abschnitte gezielt per Grep/Offset.
  - **Höchstens ein Agent gleichzeitig** (zwei nur, wenn sie völlig getrennte Dateien bearbeiten).
  - Keine separaten Planungs-/Kritik-Agent-Schichten; die Specs sind der Plan. Eine gezielte Prüfung am Ende
    jeder Phase.
  - Gradle nur für das betroffene Modul (`./gradlew :core:core-data:test`), voller Build einmal am Ende.
  - Keine ganzen Dateien/Projekt erneut einlesen, wenn der Inhalt bekannt ist.
  - Nach einem Limit-Abbruch: Agent per Nachricht fortsetzen statt neu starten.
- **Vorschaubilder** aus dem echten Spiel an den Nutzer schicken, wo sinnvoll.
- **Rangfarben** exakt nach Spec 01 §6.1 (Admin §4, Team §c, Builder §2, VIP §d, Supremium+ §d mit Verlauf
  #FF55FF→#55FFFF, Supremium §b, Premium Plus §e, Premium §6, Spieler §a).

## 5. Konventionen (Kurzfassung von Spec 00 §4)

Java 25, Gradle Kotlin DSL, Paper 26.3, Velocity 4.3.0. DTOs als `record`, kein Lombok, keine Wildcard-Imports,
Zeilen ≤ 120. DB/Valkey async (`CompletableFuture`), Bukkit-API nur auf dem Main-Thread, Logik plattformfrei und
testbar. Rechte `retromc.<bereich>.<recht>`, Kanäle `net:<bereich>`, Plugin-Messages `retromc:bridge`,
Migrationen `V<Spec>_<n>__name.sql`. Texte nur über i18n-Schlüssel (MiniMessage, `<brand>`-Platzhalter).
Tests: JUnit, Testcontainers (postgres:18.6-alpine, valkey/valkey:8.1.10-alpine), MockBukkit nur hinter Tag
`mockbukkit`. Neue Bibliotheken: Version auf Maven Central prüfen (≥ 2 Wochen alt), in `docs/VERSIONEN.md`.

Build: `./gradlew build` (Windows: `gradlew.bat build`). Docker muss laufen (Testcontainers).

## 6. Git

- Commit-Betreff `<bereich>: <was>`, Deutsch, ≤ 72 Zeichen. Arbeit auf Branches `p1/<name>`, dann in `main`.
- Commits mit der Claude-Attribution der jeweiligen Sitzung abschließen.
- Nach jedem fertigen Schritt pushen, damit der Nutzer den Stand auf GitHub sieht.

## 7. Sicherheit

- Keine Zugangsdaten/Cookies/Passwörter in Projektdateien oder Commits.
- Keinen Bot-Schutz von Webseiten umgehen (keine Captcha-/Cloudflare-Tricks, kein UA-Spoofing, keine Proxies).
  Blockt eine Seite, andere Quelle nehmen; Captchas löst nur der Nutzer selbst.
- Keine Maps/Texte/Grafiken des Vorbilds übernehmen; fremde Maps nur mit passender Lizenz und Credits.

## 8. Lokal auf dem PC des Nutzers (Windows)

Voraussetzungen: Git, Java 25 (Temurin), Docker Desktop, Minecraft Java 26.3.
1. `git clone https://github.com/Shake808111/Retro` und darin Claude Code starten.
2. Lobby-Welt (CC BY 4.0, „Factions Hub“ von NetherGames, Credits in `deploy/lobby/README.md`) aus dem
   GitHub-Release `lobby-welt` laden; falls nicht vorhanden, aus
   https://github.com/NetherGamesMC/assets (`Factions/worlds/Hub`, Bedrock-Format → mit amulet-core nach Java
   konvertieren, nur Regionsdateien übernehmen) und `deploy/lobby/setup-lobby.txt` per RCON anwenden.
3. Dev-Netz: `docker/docker-compose.dev.yml` (Postgres, Valkey, Velocity); Paper-Lobby lokal mit
   `offline-mode=false` hinter Velocity (Modern Forwarding). In Minecraft mit `localhost` verbinden.
