# Geprüfte Versionen, Toolchain und Build-Protokoll

Stand: **2026-10-20**. Jede Version unten wurde an diesem Tag direkt im Repository bzw. Register abgefragt
(nicht aus dem Gedächtnis übernommen). „Datum“ ist das Veröffentlichungsdatum laut Repository
(`lastUpdated` in `maven-metadata.xml`, `Last-Modified` des Artefakts, oder das Datum im jeweiligen Register).

## Plattform-APIs (repo.papermc.io/repository/maven-public)

| Artefakt | Version | Datum | Prüfung |
|---|---|---|---|
| `io.papermc.paper:paper-api` | `26.3.build.206-stable` | 2026-10-19 | `maven-metadata.xml`: höchster `26.3.build.*-stable`; Jar vorhanden. Das Feld `<latest>` zeigt `26.3-pre-2.build.0-alpha` (Vorabversion, bewusst nicht genutzt). Gradle-Metadaten: `org.gradle.jvm.version = 25`. |
| `com.velocitypowered:velocity-api` | `4.3.0` | 2026-10-18 | `maven-metadata.xml`: `<release>4.3.0</release>`; Git-Commit „Release 4.3.0“ (4d93ece8) auf `dev/4.0.0`. Gradle-Metadaten: `org.gradle.jvm.version = 25`. |
| Paper-Server-Build für Docker (`PAPER_BUILD`) | `206` | 2026-10-19 | fill.papermc.io v3: Build 206, Kanal STABLE |
| Velocity-Server für Docker (`VELOCITY_VERSION`) | `4.3.0` (Build 44) | 2026-10-18 | fill.papermc.io v3: Kanal STABLE |
| `api-version` in `paper-plugin.yml` | `26.3` | – | `gradle.properties` im Repo PaperMC/Paper (`apiVersion=26.3`) und deren Test-Plugin |

## Bibliotheken (Maven Central, repo1.maven.org)

| Artefakt | Version | Datum | Bemerkung |
|---|---|---|---|
| `net.kyori:adventure-bom` (+ `adventure-text-minimessage`, `-serializer-legacy`, `-serializer-plain`) | `5.2.0` | 2026-06-27 | geprüft 2026-10-26: `maven-metadata.xml` `<release>5.2.0</release>`. Genau die BOM-Version, die `paper-api 26.3.build.206-stable` und `velocity-api 4.3.0` importieren (POM `dependencyManagement`). In `core-i18n` nur `compileOnly` + Tests; Paper/Velocity liefern Adventure zur Laufzeit |
| `com.zaxxer:HikariCP` | `7.1.0` | 2026-06-14 | neueste Version |
| `org.postgresql:postgresql` | `42.7.14` | 2026-10-07 | neueste Version |
| `org.flywaydb:flyway-core` | `13.10.3` | 2026-10-15 | neueste Version |
| `org.flywaydb:flyway-database-postgresql` | `13.10.3` | 2026-10-15 | neueste Version |
| `io.lettuce:lettuce-core` | `7.9.0.RELEASE` | 2026-10-14 | neueste stabile; `8.0.0.BETA1` existiert (Beta, nicht genutzt). Bringt Netty 4.2.17 mit. |
| `org.slf4j:slf4j-simple` | `2.0.20` | 2026-09-22 | neueste 2.0.x; `2.1.0-alpha1` nicht genutzt |
| `org.junit:junit-bom` | `6.1.3` | 2026-08-07 | neueste Version (Jupiter und Platform-Launcher über die BOM) |
| `com.github.ben-manes.caffeine:caffeine` | `3.3.1` | 2026-10-13 | geplant, noch ungenutzt |
| `net.luckperms:api` | `5.5` | 2025-05-25 | geplant, noch ungenutzt (neueste Version) |
| `org.incendo:cloud-core` | `2.1.0` | 2026-07-20 | geplant, noch ungenutzt |
| `org.incendo:cloud-paper`, `cloud-velocity` | `2.0.1` | 2026-09-16 | geplant, noch ungenutzt |
| `fr.mrmicky:fastboard` | `2.2.2` | 2026-09-21 | geplant, noch ungenutzt |
| `com.github.stefvanschie.inventoryframework:IF` | `0.12.2` | 2026-10-19 | geplant, noch ungenutzt |
| `net.dv8tion:JDA` | `6.7.0` | 2026-09-20 | geplant, noch ungenutzt |

Nicht im Katalog, weil nicht auf Maven Central bzw. Repository nicht geprüft: PacketEvents, Grim, OldCombatMechanics,
LuckPerms-Plugin, Geyser/Floodgate, ViaVersion & Co. Das sind Server-Plugins, die später als Jars auf die Server kommen
(LÜCKE: Bezugsquelle und Version beim Einbinden prüfen, z. B. über Modrinth oder Hangar).

## Gradle-Plugins (Gradle Plugin Portal)

| Plugin | Version | Datum |
|---|---|---|
| `com.gradleup.shadow` | `9.6.1` | 2026-07-22 (Git-Tag 9.6.1 „Prepare version 9.6.1“) |
| `xyz.jpenilla.run-paper` / `run-velocity` | `3.1.0` | 2026-08-08 |
| `org.gradle.toolchains.foojay-resolver-convention` | `1.0.0` | 2025-05-19 (neueste Version; mit Gradle 9.8.2 erfolgreich genutzt) |

## Toolchain

| Werkzeug | Version | Prüfung |
|---|---|---|
| Gradle (Wrapper) | `9.8.2` | services.gradle.org/versions/current (Build 2026-10-19). Distribution-SHA-256 `21bd5da2…e389607` im Wrapper hinterlegt; Wrapper-Jar-SHA-256 `3b8a2577…ce68e98` stimmt mit dem offiziellen Wert überein. |
| JDK | Eclipse Temurin `25.0.4.1+1` (LTS) | api.adoptium.net (Stand 2026-08-21), SHA-256 `dbb69839…5cf41e` geprüft. Auf dieser Maschine war nur OpenJDK 21 installiert; Paper 26.3 und Velocity 4.3 verlangen Java 25, daher wurde Temurin 25 in den Scratch-Bereich geladen und als `JAVA_HOME` genutzt. |
| Java-Toolchain im Build | `25` | `build.gradle.kts` (alle Module, `--release 25`). Auto-Provisionierung getestet: Gradle auf OpenJDK 21 gestartet → foojay lud Temurin 25.0.4.1 nach `~/.gradle/jdks`, Kompilieren erfolgreich. |

## Docker-Images (Docker Hub)

| Image | Tag | Datum |
|---|---|---|
| `itzg/minecraft-server` | `2026.10.0-java25` | 2026-10-10 |
| `itzg/mc-proxy` | `2026.10.1-java25` | 2026-10-16 |
| `postgres` | `18.6-alpine` | 2026-09-21 |
| `valkey/valkey` | `8.1.10-alpine` | 2026-09-21 (Valkey 8.x laut Notiz 07; `9.1.2` existiert ebenfalls) |
| `flyway/flyway` | `13.10.3-alpine` | 2026-10-15 |

## GitHub Actions (Major-Tags per `git ls-remote` geprüft)

`actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v6`, `actions/upload-artifact@v7`.

## Build-Protokoll 2026-10-20

Befehl: `./gradlew clean build --warning-mode all` mit `JAVA_HOME` = Temurin 25.0.4.1, Gradle 9.8.2.
Ergebnis: **BUILD SUCCESSFUL**, 122 Aufgaben, keine Compiler- oder Gradle-Warnungen, 16 Tests grün.

| Modul | Kompiliert | Tests | Artefakt |
|---|---|---|---|
| `:core:core-api` | ja | 3/3 grün | Bibliothek |
| `:core:core-data` | ja | 2/2 grün | Bibliothek |
| `:core:core-messaging` | ja | 1/1 grün | Bibliothek |
| `:core:core-i18n` | ja | 3/3 grün | Bibliothek |
| `:platform:paper-core` | ja | – | Paper-Plugin, Shadow-Jar ca. 6,5 MB (core-* + HikariCP, PostgreSQL-Treiber, Lettuce, Reactor; ohne Netty/SLF4J) |
| `:platform:velocity-core` | ja | – | Velocity-Plugin, `velocity-plugin.json` erzeugt (`id: netzwerk-core`) |
| `:game:game-framework` | ja | 3/3 grün | Bibliothek (in jedes Modus-Jar eingebettet) |
| `:game:bedwars`, `:game:skywars`, `:game:survivalgames`, `:game:cores`, `:game:ttt`, `:game:gungame`, `:game:endergames`, `:game:masterbuilders`, `:game:clanwar` | ja (je) | – | je ein Paper-Plugin (ca. 7 KB, enthält game-framework) |
| `:lobby` | ja | – | Paper-Plugin |
| `:citybuild` | ja | – | Paper-Plugin |
| `:services:controller` | ja | 1/1 grün | Java-Anwendung |
| `:services:api` | ja | 1/1 grün | Java-Anwendung (`GET /health`, im Test geprüft) |
| `:services:discord-bot` | ja | – | Java-Anwendung (Platzhalter) |
| `:tools:map-exporter` | ja | 2/2 grün | Java-Anwendung |

Zusätzlich geprüft:
- `./gradlew devPlugins` füllt `docker/dev-plugins/{proxy,lobby,game-bedwars}` mit den richtigen Jars.
- Die erzeugten `paper-plugin.yml` lassen sich als YAML lesen (Name `NetzwerkCore`/`NetzwerkLobby`/…, `api-version: '26.3'`, Abhängigkeit auf `NetzwerkCore`).
- Markenwechsel: `./gradlew -PbrandName="Test Marke" …` ergibt Velocity-ID `testmarke-core` und Plugin-Namen `Test MarkeCore`; eine ungültige Marke (`Netz!werk`) bricht mit deutscher Fehlermeldung ab.
- `db/migrations/V1__init.sql`: mit `./gradlew :services:controller:run --args=migrate` gegen ein **lokales PostgreSQL 16.15** (temporärer Testcluster, danach gelöscht) ausgeführt: 1 Migration, 22 Tabellen in 8 Schemas; zweiter Lauf „up to date“. Die Prüfung `CHECK (a < b)` in `social.friendship` verhält sich wie `FriendPair` (PostgreSQL vergleicht UUIDs ohne Vorzeichen). **LÜCKE:** Test gegen PostgreSQL 18.6 steht aus (erster `docker compose up`).
- `docker compose -f docker/docker-compose.dev.yml config` ist gültig; ohne `docker/.env` bricht es mit klarer Meldung ab.

**Nicht geprüft (LÜCKE):** Start der Plugins auf echten Paper-26.3-/Velocity-4.3-Servern. Auf dieser Maschine läuft kein
Docker-Daemon, und der Start eines Servers war nicht Teil des Auftrags. Schließen: `docker compose … up` oder
`./gradlew :lobby:runServer` (siehe README.md, „Start“-Ablauf, Schritt 2).
