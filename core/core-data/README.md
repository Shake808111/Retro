# core/core-data – Datenzugriff

**Gradle-Pfad:** `:core:core-data` · **Paket:** `net.retromc.core.data` · **Einstieg:** `Database`, `DatabaseConfig`, `ValkeyConnector`, `ValkeyKeys` · **Status:** Gerüst

## Verantwortung

Zugriff auf die beiden Datenspeicher aus Notiz 07, Abschnitt 4:

- **PostgreSQL** (dauerhafte Daten: Spieler, Wirtschaft, Soziales, Strafen) über den Verbindungspool HikariCP.
  Das Schema entsteht nur über Flyway-Migrationen in `db/migrations/` (eine pro Änderung, nie nachträglich editieren).
- **Valkey** (Präsenz, Partys, Queues, Bestenlisten, Cooldowns) über den Redis-kompatiblen Client Lettuce.
  `ValkeyKeys` enthält die Schlüssel-Konventionen der Notiz (`presence:<uuid>`, `server:<name>`, `queue:<variant>`,
  `party:<id>`, `party:<id>:members`, `player:<uuid>:party`, Bestenlisten `lb:<mode>:30d|all`; vollständig nach
  Spec 01 §2.3, Spec 02 §2.2, Spec 08 §2.2).
- Konfiguration über Umgebungsvariablen `NETWORK_DB_URL`, `NETWORK_DB_USER`, `NETWORK_DB_PASSWORD`,
  `NETWORK_DB_POOL_SIZE`, `NETWORK_VALKEY_URL` (gesetzt in `docker/docker-compose.dev.yml`).

## Testhilfen (testFixtures, Spec 08 §6.16)

`testFixtures(project(":core:core-data"))` im Source-Set `integrationTest` einbinden:

- `TestDatabases`: geteilte Container `postgres:18.6-alpine` und `valkey/valkey:8.1.10-alpine` (Testcontainers),
  `freshDatabase()` / `migratedDatabase()` (eigene Datenbank je Test, Flyway aus `db/migrations`), `valkeyUrl()`,
  `flushValkey()`.
- `TestTransactions`: `rollbackAfter`, `committed`, `count`, `exists`.
- `TestExecutors`: Einzel-Thread-Executor, `asyncDatabase`, `await` mit Zeitlimit.

Integrationstests: `./gradlew :core:core-data:integrationTest` (alle Migrationen in Reihenfolge, Seed, Valkey).

Geplant (Notiz 07, Abschnitt 4): jOOQ oder JDBI für Repositories, Caffeine als lokaler Cache,
Caching-Regel „Profil beim Login laden, Wirtschaft write-through, Statistik write-behind“.

## Welches Vorbild-Feature wird nachgebaut?

Die Persistenz hinter Profilen, Statistiken und Coins (Notiz 05, Abschnitt 4: netzwerkweite Währung „Coins“,
Level, Statistik-Reset). Wie Vorbild selbst speichert, ist nur teilweise bekannt (Cassandra und MySQL im
Web-Backend laut Notiz 06; Spielserver-Seite: LÜCKE).

Quellen: `research_notes/`.
