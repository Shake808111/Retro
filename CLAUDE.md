# RetroMC – Übergabe und Arbeitsregeln für Claude

Diese Datei wird in jeder Claude-Code-Sitzung automatisch geladen. Sie enthält alles, was eine neue Sitzung
(z. B. auf dem PC des Nutzers) braucht, um nahtlos weiterzumachen.

## 1. Projekt in einem Absatz

Eigenes Minecraft-Netzwerk **RetroMC**: 1:1-Nachbau der Mechaniken, Zahlen, Abläufe und UI-Strukturen eines
großen deutschen Minigame-Netzwerks (Lobby, BedWars, SkyWars, SurvivalGames, EnderGames, TTT, MasterBuilders,
Cores, GunGame, CityBuild/SMP u. a.). **Nicht** übernommen werden Name, Logo, Maps, Code oder eigene Texte des
Vorbilds. Marke ausschließlich `RetroMC` (aus `gradle.properties` `brandName`), Paket `net.retromc`, Domain
`retromc.net` nur Platzhalter. Den Namen des Vorbilds nirgends ins Repo schreiben (`git grep -i gomme` muss 0
sein) – das Repo ist **öffentlich**.

## 2. Wo steht was

| Pfad | Inhalt |
|---|---|
| `docs/spec/00_bauplan_uebersicht.md` | Gesamtplan: Ownership §1, Modulgraph §2, Phasen §3, **Konventionen §4**, aufgelöste Widersprüche §5, Nutzer-Entscheidungen E1–E11 §6 |
| `docs/spec/01`–`08` | Verbindliche Bau-Specs je Bereich (01 Proxy/Social, 02 Kern-UI/Stats, 03 Lobby/Spielrahmen, 04 BedWars/SkyWars, 05 SG/EnderGames/Klassiker, 06 weitere Minigames, 07 CityBuild/SMP, 08 Infrastruktur). Jede Spec: §1 Umfang, §2 Daten, §3 Befehle, §4 Anzeige, §5 GUIs, §6 Mechaniken/Zahlen, §7 Texte, §8 Schnittstellen, §9 Abnahme, §10 offene Punkte |
| `docs/ARCHITEKTUR.md`, `docs/VERSIONEN.md` | Architektur, geprüfte Bibliotheksversionen |
| `core/` | `core-api` (Verträge/DTOs/Events), `core-data` (Postgres/Valkey, `ValkeyKeys`, Testhilfen), `core-messaging` (Bus, Envelope, RequestReply), `core-i18n` (Textkatalog de/en/bar/gsw), `core-stats` |
| `platform/paper-core`, `platform/velocity-core` | Basis-Plugins RetroMCCore (Paper) und retromc-core (Velocity) |
| `game/*`, `lobby/`, `citybuild/` | Spielmodi, Lobby, CityBuild (noch Gerüste) |
| `db/migrations/` | Flyway: V1, V1_1, V1_2, V2_1, V8_1 (+ später V3_1 … V7_1) |
| `deploy/lobby/` | Lobby-Map-Einrichtung (Statuen, Kisten, Hologramme), `warps.yml` (Navigator-Ziele), Map-Credits |
| `docker/` | Compose für Entwicklung (Postgres 18.6, Valkey 8.1.10, Velocity) |

## 3. Stand (bitte bei jedem Meilenstein aktualisieren)

- **Fertig in `main`:** Specs 00–08; F1a Verträge (core-api, core-messaging, core-data inkl. Testcontainers-
  Testhilfen, 5 Migrationen, `ValkeyKeys`); F2 Textkatalog; Lobby-Map-Einrichtung. Voller Build grün.
- **In Arbeit:** F1b Plattform-Gerüste auf Branch `p1/plattform` (Feature-Muster, Service-Registry, Env-Config,
  Cloud-Commands, Bridge-Codec, GUI-Framework mit ConfirmGui/PagedGui, ItemBuilder). Wenn der Branch noch nicht
  fertig/gemergt ist: Stand prüfen (`git log origin/p1/plattform`), fertigstellen, Build grün, in `main` mergen.
- **Als Nächstes – Phase 1 Stränge** (je ein eigener Branch `p1/<name>`, danach mergen):
  - **A** velocity-core Social nach Spec 01 (Login, MOTD, Ränge, Freunde, /msg, Ignore, Party, Clan, Report,
    Punish, Nick, Sprache, Einstellungen, AFK, Wartung, globaler Chat, Anti-Spam, JoinMe) + Repos + IT-Tests.
  - **B** paper-core UI/Stats nach Spec 02 (/stats 30 Tage, /statsall, /statsd, /togglestats, /top, /resetstats,
    /coins, /tokens, /language, /statsadmin, /statswall; Scoreboard, Tab, Bossbar, Actionbar, Title,
    Chat-Format, Hologramme, NPC, Nametag, NetworkLevel; core-stats-Formeln; Stats/Wallet/Profile-Repos).
  - **C** Plattform-Teile Spec 08 (ServerRegistry Heartbeat 5 s/TTL 15 s, Lifecycle, PlayerTransfer,
    LobbyBalancer, /server /send /whereami /glist /cloud, infra-Repos).
  - **D** paper-core Social: GUIs aus Spec 01 §5, Mute/Spam/Ignore-Durchsetzung, Nick-Anwendung, AFK-Erkennung.
  - Grenzen: Chat-**Format**/Tab/Nametag = B; Chat-**Durchsetzung**, Social-Tab-Anteile, Nick-Anwendung = D.
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
