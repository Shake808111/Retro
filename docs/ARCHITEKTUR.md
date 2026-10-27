# Architektur

Dieses Dokument beschreibt das Projektgerüst. Grundlage sind die Recherche-Notizen unter
`research_notes/`, vor allem **Notiz 07** (Technik-Stack, Recht) und
**Notiz 05** (netzwerkweite Systeme und UI). Verweise wie „Notiz 07 §4“ meinen Abschnitt 4 dieser Notiz.
Geprüfte Versionen: [`VERSIONEN.md`](VERSIONEN.md).

## 1. Leitentscheidungen (aus Notiz 07)

| Bereich | Entscheidung | Quelle |
|---|---|---|
| Proxy | Velocity 4.3, `modern` Player-Info-Forwarding, Mehrversionen-Plugins (ViaVersion/-Backwards/-Rewind) auf den Paper-Servern, nicht auf dem Proxy | Notiz 07 §1 |
| Server | Paper 26.3 auf Java 25 für Lobby, Spielmodi und CityBuild; kein eigener Paper-Fork am Anfang | Notiz 07 §2 |
| 1.8-Kampf | Paper 26.3 + OldCombatMechanics oder eigenes Modul hinter einer Schnittstelle `CombatProfile` | Notiz 07 §1 |
| Orchestrierung | Kubernetes (k3s) + Agones + eigener Controller; **eine Runde pro Pod**; Alternative CloudNet v4 | Notiz 07 §3 |
| Daten | PostgreSQL (dauerhaft) + Valkey (Präsenz, Partys, Queues, Bestenlisten, Pub/Sub); Flyway-Migrationen | Notiz 07 §4 |
| Wiederverwenden | LuckPerms, Adventure/MiniMessage, PacketEvents, Cloud, FastBoard, InventoryFramework, Grim, OCM, PlotSquared + FAWE, Geyser/Floodgate | Notiz 07 §5 |
| Selbst bauen | Spielmodi auf gemeinsamem `game-framework`, Netzwerksysteme (Freunde, Party, Clan, Moderation) | Notiz 07 §5 |
| Build | Gradle (Kotlin DSL), Versionskatalog, Java-25-Toolchain, run-paper/run-velocity, GitHub Actions, itzg-Images für Dev/Test | Notiz 07 §6 |
| Recht | eigene Marke, keine Vorbild-Namen/Texte/Karten/Grafiken; nur kosmetische Käufe | Notiz 07 §9 |

## 2. Laufzeit-Topologie (Entwicklungsumgebung)

```
                 Spieler (Java-Client)
                         │ :25565
                 ┌───────▼────────┐
                 │ Velocity 4.3   │  velocity-core
                 └──┬──────────┬──┘
        modern fwd  │          │  modern fwd
          ┌─────────▼──┐   ┌───▼───────────────┐
          │ Paper lobby│   │ Paper bedwars-1   │   je: paper-core + lobby bzw. + bedwars
          └─────┬──────┘   └───┬───────────────┘
                │              │
        ┌───────▼──────────────▼───────┐
        │ PostgreSQL 18   │  Valkey 8  │   (+ einmaliger Flyway-Lauf "migrate")
        └──────────────────────────────┘
```

Produktiv (später, Notiz 07 §3): mehrere Velocity-Instanzen hinter DDoS-Schutz, Lobbys und Spielserver als
Agones-Fleets, Controller vergibt Server, CityBuild als dauerhafter Server. Kubernetes-Manifeste sind **noch nicht**
angelegt (geplant unter `deploy/k8s/`, Notiz 07 §6).

## 3. Module und Abhängigkeiten

```
core-api ◄── core-data ◄──┐
   ▲  ▲      core-messaging◄┤
   │  └───── core-i18n ◄────┤
   │                        ├── platform/paper-core   (Shadow-Jar enthält alle core-*)
   │                        ├── platform/velocity-core(Shadow-Jar enthält alle core-*)
   │                        └── services/controller   (+ Flyway)
   │
game-framework (compileOnly: paper-api, core-api)
   ▲
game/<modus>  (implementation: game-framework → eingebettet; compileOnly: paper-core)
lobby, citybuild  (compileOnly: paper-core)
```

| Modul | Art | Läuft auf | Liefert |
|---|---|---|---|
| `core/core-api` | Bibliothek, reines Java | überall | `BuildInfo` (generiert), Server-Registry-Typen, `FriendPair` |
| `core/core-data` | Bibliothek | Plugins, Dienste | HikariCP-Pool, Lettuce-Verbindung, Valkey-Schlüssel |
| `core/core-messaging` | Bibliothek | Plugins, Dienste | `MessageBus` über Valkey Pub/Sub, Kanäle `net:*` |
| `core/core-i18n` | Bibliothek | Plugins | MiniMessage-Texte de_DE/en_US, `<brand>` |
| `platform/paper-core` | Paper-Plugin | jedem Paper-Server | Netzwerk-Kern, stellt core-* bereit |
| `platform/velocity-core` | Velocity-Plugin | Proxy | Login, Soziales, Routing, MOTD |
| `game/game-framework` | Bibliothek | Spielserver | Rundenablauf, Kampfprofil, Basisklasse `MiniGamePlugin` |
| `game/*` (9 Modi) | Paper-Plugins | Spielserver | je ein Modus |
| `lobby`, `citybuild` | Paper-Plugins | Lobby / CityBuild | – |
| `services/controller` | Java-Anwendung | eigener Dienst | Queue, Matchmaking, Registry, Migrationen |
| `services/api` | Java-Anwendung | eigener Dienst | Web-API (Platzhalter, `/health`) |
| `services/discord-bot` | Java-Anwendung | eigener Dienst | Platzhalter |
| `tools/map-exporter` | Java-Anwendung | Entwickler/CI | Kartenprüfung |
| `web/` | kein Gradle-Modul | – | Next.js später |

**Klassenpfad auf Paper-Servern:** Nur `paper-core` bündelt die core-Bibliotheken. Lobby, CityBuild und die Modi
deklarieren in `paper-plugin.yml` eine Abhängigkeit auf `<Marke>Core` mit `load: BEFORE`, `required: true` und
`join-classpath: true` und kompilieren nur gegen `paper-core` (`compileOnly`). So gibt es jede core-Klasse genau einmal.

## 4. Konventionen

- **Marke:** einzig `brandName` in `gradle.properties`. Daraus entstehen `brandId` (Kleinbuchstaben/Ziffern),
  die Klasse `net.retromc.core.api.BuildInfo`, Plugin-Namen (`${brandName}Core`, `${brandName}Lobby`,
  `${brandName}BedWars` …), die Velocity-ID (`<brandId>-core`) und der Platzhalter `<brand>` in Texten.
  Der Build prüft die Zeichen (Paper-Plugin-Namen, Velocity-IDs).
- **Pakete:** `net.retromc.<modul>`, Bindestriche werden zu Punkten: `core-api` → `net.retromc.core.api`,
  `paper-core` → `net.retromc.paper.core`, `bedwars` → `net.retromc.bedwars`, `discord-bot` → `net.retromc.discord.bot`.
- **Technische Namen** (Datenbank `network`, Docker-Dienste, Umgebungsvariablen `NETWORK_*`) sind markenneutral,
  damit ein Markenwechsel keine Daten- oder Infrastruktur-Änderung braucht.
- **Konfiguration** der Plugins und Dienste über Umgebungsvariablen: `NETWORK_SERVER_NAME`, `NETWORK_DB_URL`,
  `NETWORK_DB_USER`, `NETWORK_DB_PASSWORD`, `NETWORK_DB_POOL_SIZE`, `NETWORK_VALKEY_URL`, `NETWORK_API_PORT`.
- **Texte:** nur über `core-i18n`, Deutsch zuerst; eigene Formulierungen.
- **Schema-Änderungen:** nur als neue Flyway-Datei `db/migrations/V<n>__<beschreibung>.sql`.

## 5. Daten

- **PostgreSQL**, ein Schema pro Bereich: `core`, `social`, `stats`, `economy`, `cosmetics`, `moderation`, `store`,
  `integ`, dazu `luckperms` (Tabellen legt LuckPerms selbst an). Inhalt: `db/migrations/V1__init.sql`, übernommen aus
  der Schema-Skizze in Notiz 07 §4.
- **Valkey-Schlüssel** (`core-data/ValkeyKeys`): `presence:<uuid>` (HASH, TTL 60 s), `server:<name>` (HASH),
  `queue:<variant>` (ZSET), `party:<id>` / `party:<id>:members` / `player:<uuid>:party`, Bestenlisten
  `lb:<mode>:<key>:<alltime|YYYY-MM>` (ZSET, nachts aus SQL neu aufgebaut).
- **Kanäle** (`core-messaging/NetworkChannel`): `net:chat`, `net:social`, `net:moderation`, `net:queue`, `net:server-state`.
- **Caching-Regel** (Notiz 07 §4): Profil beim Login (Proxy) nach Valkey + lokal, Wirtschaft write-through (immer
  SQL-Transaktion, Ledger mit `idem_key`), Statistik write-behind (gesammelt am Rundenende).
- **Datenschutz** (Notiz 07 §9 E): IP-Adressen nur als HMAC (`core.player_ip.ip_hmac`), Aufbewahrungsjobs für
  IP-Daten und Chatlogs sind noch zu bauen.

## 6. Build und Werkzeuge

- Gradle 9.8.2 (Wrapper), Kotlin DSL, Versionskatalog `gradle/libs.versions.toml`, Configuration Cache und Build Cache an.
- Gemeinsame Konventionen stehen in der **Wurzel-`build.gradle.kts`** (`subprojects { … }`): Java-25-Toolchain,
  UTF-8, `-parameters`, `-Xlint:all`, JUnit 6, Shadow-Jar als Plugin-Artefakt (ohne Netty/SLF4J, Service-Dateien
  zusammengeführt), `paper-plugin.yml`-Platzhalter, run-paper/run-velocity, Aufgabe `devPlugins`.
- CI: `.github/workflows/build.yml` (JDK 25, `./gradlew build`, Plugin-Jars als Artefakt).

## 7. Abweichungen vom Layout in Notiz 07 §6 (bewusst)

| Notiz 07 | Hier | Grund |
|---|---|---|
| `build-logic/` mit Convention-Plugins | Konventionen in der Wurzel-`build.gradle.kts` | Vorgabe für dieses Gerüst; Umzug in `build-logic/` ist möglich, sobald die Konventionen wachsen |
| `deploy/docker/`, `deploy/k8s/` | `docker/docker-compose.dev.yml`; k8s fehlt | Vorgabe für dieses Gerüst; Produktiv-Images und Agones-Fleets folgen |
| `core-data` | `core-data` (gleich) | – |
| Spielmodi `bedwars/ skywars/ survivalgames/ cores/ ttt/ gungame/ endergames/ masterbuilders/ clanwar/` | unverändert übernommen | – |

Hinweise zur Modusliste:
- **Beim Vorbild am 07.06.2024 abgeschaltet** (nur noch private Runden): SurvivalGames/QuickSG, Cores, EnderGames
  (Notiz 02 Q6, Notiz 03 §1). Sie stehen trotzdem im Layout von Notiz 07.
- **Beim Vorbild aktiv, aber nicht im Layout von Notiz 07** (Notiz 03 §1): Duels/Game1vs1, ItemChaos, PartyGames,
  MoneyMaker, SMP, JumpLeague, BuildFFA, FFA Hardcore, SkillTraining. Bei Bedarf als `game/<modus>` nach dem Muster
  der vorhandenen Modi ergänzen (Eintrag in `settings.gradle.kts`).
- **Moderation/Strafen** ist kein eigenes Modul: Durchsetzung beim Login in `velocity-core`, Entbannungsanträge in
  `services/api`, Daten in `moderation.*` (Notiz 07 §5 „Punishments and reports“).

## 8. Offene Punkte (LÜCKEN) und wie sie sich schließen lassen

| # | Offener Punkt | Schließen durch |
|---|---|---|
| 1 | Plugins wurden noch auf keinem echten Paper-26.3-/Velocity-4.3-Server gestartet (kein Docker-Daemon auf der Build-Maschine) | `docker compose -f docker/docker-compose.dev.yml up` oder `./gradlew :lobby:runServer`, Log auf „aktiv (Gerüst)“ prüfen |
| 2 | Netty: Lettuce 7.9 erwartet Netty 4.2.17; das Shadow-Jar nutzt die Netty-Version der Plattform. Velocity 4.3 bringt 4.2.19 mit (Commit „Bump netty to 4.2.19.Final“); die Netty-Version von Paper 26.3 ist unbekannt | Smoke-Test mit echter Valkey-Verbindung auf beiden Plattformen |
| 3 | Gebündelte Bibliotheken (HikariCP, PostgreSQL-Treiber, Lettuce, Reactor) sind **nicht relocated**; Konflikte mit fremden Plugins möglich | vor dem ersten Produktivbetrieb `relocate(...)` im Shadow-Jar ergänzen und erneut testen |
| 4 | Velocity-3.x→4.0-API-Änderungen sind nicht dokumentiert (Notiz 07 §1, Gaps); der Stub kompiliert gegen 4.3.0 | PaperMC-Doku bzw. Velocity-Commits ab 4.0.0 lesen, sobald Proxy-Funktionen gebaut werden |
| 5 | Migration nur gegen PostgreSQL 16.15 getestet, Compose nutzt 18.6 | erster Lauf des Dienstes `migrate` in der Dev-Umgebung |
| 6 | MockBukkit-Unterstützung für Paper 26.3 unverifiziert (Notiz 07 §6, Gaps) → bisher keine Plugin-Unit-Tests | Maven-Metadaten von MockBukkit prüfen, dann Testabhängigkeit ergänzen |
| 7 | Kampfprofile der Modi sind vorläufig (`LEGACY_1_8` laut Notiz 07 §1; Vorbild selbst: BedWars 2.0 modern, Classic 1.8 – Notiz 02 Q1/Q8; TTT/MasterBuilders ohne Festlegung) | Entscheidung pro Modusvariante |
| 8 | Server-Plugins (LuckPerms, Geyser/Floodgate, Via*, Grim, OCM, PlotSquared/FAWE) sind noch nicht eingebunden; Versionen nicht geprüft | beim jeweiligen Meilenstein über Modrinth/Hangar/GitHub-Releases prüfen |
| 9 | Kubernetes/Agones, Produktiv-Images (`eclipse-temurin:25-jre`), Renovate fehlen | eigener Meilenstein „Betrieb“ |
| 10 | Exakte Vorbild-UI (Scoreboard, Tablist, MOTD, Hotbar-Slots) ist laut Notiz 05 §6 nicht verifiziert | nur zur Orientierung nötig; eigenes Design entwerfen |
