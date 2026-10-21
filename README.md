# RetroMC – Projektgerüst

Eigenes Minecraft-Netzwerk (Lobby, Minispiele, CityBuild) nach dem Vorbild der Spielmodi und Systeme von GommeHD.net,
**unter eigener Marke** und mit eigenem Code. Die Marke heißt „RetroMC“ (siehe [Marke umbenennen](#marke-umbenennen)).

Grundlage sind die Recherche-Notizen in `/home/user/research_notes/GommeHD Analyse und Nachbau/` (01–07).
Architektur: [`docs/ARCHITEKTUR.md`](docs/ARCHITEKTUR.md) · Geprüfte Versionen und Build-Protokoll: [`docs/VERSIONEN.md`](docs/VERSIONEN.md).

**Stand:** Gerüst. Alle Module kompilieren, 16 Unit-Tests laufen grün, die Datenbank-Migration wurde gegen PostgreSQL getestet.
Spiellogik gibt es noch nicht.

## Technik auf einen Blick

| Teil | Wahl |
|---|---|
| Sprache / Toolchain | Java 25 (Eclipse Temurin), Gradle 9.8.2 (Wrapper, Kotlin DSL) |
| Proxy | Velocity 4.3.0 |
| Server | Paper 26.3 (Build 206), Plugin-Format `paper-plugin.yml` |
| Daten | PostgreSQL (HikariCP, Flyway), Valkey (Lettuce) |
| Lokale Umgebung | Docker Compose mit `itzg/minecraft-server` und `itzg/mc-proxy` |

## Struktur

```
netzwerk/
├── build.gradle.kts            gemeinsame Konventionen (Java 25, Shadow, run-paper, devPlugins)
├── settings.gradle.kts         Modulliste, Repositories
├── gradle.properties           brandName, group, version  ← Marke hier ändern
├── gradle/libs.versions.toml   Versionskatalog (alle Versionen geprüft)
├── core/
│   ├── core-api/               reine Typen, generierte BuildInfo (Marke, Version)
│   ├── core-data/              PostgreSQL-Pool, Valkey, Schlüssel-Konventionen
│   ├── core-messaging/         netzwerkweite Nachrichten (Valkey Pub/Sub)
│   └── core-i18n/              Texte de_DE/en_US (MiniMessage)
├── platform/
│   ├── paper-core/             Kern-Plugin für jeden Paper-Server
│   └── velocity-core/          Kern-Plugin für den Proxy
├── game/
│   ├── game-framework/         Rundenablauf, Kampfprofile, Basisklasse der Modi
│   └── bedwars/ skywars/ survivalgames/ cores/ ttt/ gungame/ endergames/ masterbuilders/ clanwar/
├── lobby/                      Lobby-Plugin
├── citybuild/                  CityBuild-Plugin
├── services/
│   ├── controller/             Queue/Matchmaking, Registry, DB-Migrationen
│   ├── api/                    Web-API (Platzhalter)
│   └── discord-bot/            Discord-Bot (Platzhalter)
├── tools/map-exporter/         Kartenprüfung
├── web/                        Website (später, Next.js)
├── db/migrations/V1__init.sql  Datenbankschema (Flyway)
├── docker/                     docker-compose.dev.yml, Velocity- und Paper-Konfiguration
├── docs/                       ARCHITEKTUR.md, VERSIONEN.md
└── .github/workflows/build.yml CI
```

Jedes Modul hat eine eigene `README.md` mit Verantwortung und dem GommeHD-Feature, das es nachbaut (mit Quellenangabe).

## Bauen

Voraussetzung: ein installiertes JDK, auf dem Gradle läuft (am 2026-10-20 getestet mit 21 und 25). Kompiliert wird
immer mit **JDK 25**; fehlt es, lädt Gradle es über die Toolchain-Provisionierung (foojay) nach `~/.gradle/jdks`
(mit JDK 21 als Gradle-JVM getestet: Temurin 25.0.4.1 wurde automatisch geladen).

```bash
./gradlew build          # alles kompilieren und testen
./gradlew devPlugins     # Plugin-Jars für Docker nach docker/dev-plugins/ kopieren
```

Plugin-Jars liegen danach unter `<modul>/build/libs/<modul>-<version>.jar` (das `-plain.jar` ist nur das Jar ohne
eingebettete Abhängigkeiten).

## Entwicklungsumgebung starten

Voraussetzung: Docker mit Compose v2.

```bash
cp docker/.env.example docker/.env
#   MINECRAFT_EULA=TRUE  nur, wenn du die Minecraft-EULA akzeptierst
#   VELOCITY_SECRET=...  zufällig, z. B. openssl rand -hex 24
#   POSTGRES_PASSWORD=...
./gradlew devPlugins
docker compose -f docker/docker-compose.dev.yml up
```

Dann im Minecraft-Client (Version 26.3) mit `localhost:25565` verbinden. Die Umgebung startet:
PostgreSQL 18.6, einen einmaligen Flyway-Lauf (`migrate`), Valkey 8.1, Velocity 4.3.0, eine Paper-Lobby und einen
BedWars-Spielserver. Nach Code-Änderungen: `./gradlew devPlugins`, dann
`docker compose -f docker/docker-compose.dev.yml restart lobby game-bedwars-1 velocity`.

Ohne Docker lässt sich ein einzelner Server starten:

```bash
./gradlew :lobby:runServer                       # Paper 26.3 + paper-core + lobby (EULA in run/eula.txt bestätigen)
./gradlew :game:bedwars:runServer                # Paper 26.3 + paper-core + bedwars
./gradlew :platform:velocity-core:runVelocity    # Velocity 4.3.0
./gradlew :services:controller:run --args=migrate   # Migrationen (NETWORK_DB_* setzen)
```

## Marke umbenennen

1. In `gradle.properties` **nur `brandName`** ändern (erlaubt: Buchstaben, Ziffern, Leerzeichen, `_`, `.`, `-`;
   ohne Leerzeichen sehen Plugin-Namen besser aus).
2. `./gradlew build` – automatisch angepasst werden: Plugin-Namen in allen `paper-plugin.yml`
   (z. B. `NetzwerkCore` → `<Marke>Core`), Velocity-Plugin-ID (`<marke>-core`), `BuildInfo.BRAND_NAME`/`BRAND_ID`,
   alle Texte mit `<brand>`, der Gradle-Projektname.
3. Nicht betroffen (bewusst markenneutral): Datenbankname, Docker-Dienste, Umgebungsvariablen `NETWORK_*`.

Optional, nur falls auch die Java-Pakete umbenannt werden sollen: `group` in `gradle.properties` ändern, die Pakete
`net.retromc.*` per IDE-Refactoring umbenennen und danach die Einträge `main:` in allen `paper-plugin.yml` sowie
`mainClass` in den `services/*`- und `tools/*`-Builddateien anpassen.

Vor dem Start prüfen (Notiz 07 §9): Marke in DPMAregister und EUIPO eSearch suchen; keine Namen, Texte, Karten oder
Grafiken von GommeHD übernehmen.

## „Start“-Ablauf

Wenn das Kommando **„Start“** kommt, beginnt die Umsetzung in dieser Reihenfolge:

1. **Grundlage prüfen:** `java -version` zeigt 25 (oder Gradle-Toolchain lädt es), `./gradlew build` ist grün,
   `git status` ist sauber. Versionen in `docs/VERSIONEN.md` bei Bedarf auffrischen (Paper veröffentlicht oft neue Builds).
2. **Smoke-Test der Umgebung** (schließt LÜCKE 1, 2 und 5 aus `docs/ARCHITEKTUR.md`): `docker/.env` anlegen,
   `./gradlew devPlugins`, `docker compose -f docker/docker-compose.dev.yml up`; in den Logs müssen „Core … aktiv (Gerüst)“,
   „Lobby aktiv“ und „BedWars bereit“ erscheinen und der Flyway-Dienst `migrate` mit Version 1 enden; mit dem Client verbinden.
3. **Arbeitszweig** von `main` anlegen, z. B. `feature/m1-profil-und-login`.
4. **Meilensteine** (Vorschlag, abgeleitet aus den Abhängigkeiten in Notiz 07; Reihenfolge nach Bedarf ändern):
   - **M1 Profil & Login:** `core.player` beim Login schreiben (velocity-core), Präsenz `presence:<uuid>` in Valkey,
     Ban-Prüfung gegen `moderation.punishment`, Texte über core-i18n.
   - **M2 Lobby:** Hotbar und Navigator, Scoreboard/Tablist (paper-core, FastBoard), NPC-Joinsystem als Queue-Anfrage.
   - **M3 Erste Runde:** Controller-Queue, Server-Registry `server:<name>`, `game-framework` (Teams, Karten, Zuschauer,
     Statistik-Senke) und **BedWars** bis zu einer spielbaren Runde.
   - **M4 Soziales:** Freunde, Party, Clan, private Nachrichten (velocity-core + core-messaging), Befehlsmuster `/<system>`, `/<system> 2`.
   - **M5 Ausbau:** weitere Modi, CityBuild (PlotSquared, Vault-Brücke), Web-API, Website, Discord-Bot, Betrieb (Agones, Images).
5. **Pro Meilenstein:** Tests schreiben, `./gradlew build` grün, Smoke-Test in Docker, Commit auf dem Zweig, danach Merge nach `main`.

## Lizenz und Recht

Noch keine Lizenz festgelegt. Rechtliche Punkte (Minecraft-EULA, Impressum, DSGVO, Jugendschutz, Store) stehen in
Notiz 07 §9 und müssen vor dem Start von einer Anwältin bzw. einem Anwalt geprüft werden.
Nicht offizielles Minecraft-Produkt; nicht von Mojang oder Microsoft genehmigt oder mit ihnen verbunden.
