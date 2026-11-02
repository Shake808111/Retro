# platform/platform-common – gemeinsame Bausteine der Kern-Plugins

**Gradle-Pfad:** `:platform:platform-common` · **Paket:** `net.retromc.platform.common` · **Status:** Plattform-Gerüst (F1b)

## Verantwortung

Plattformfreie Teile, die `paper-core` und `velocity-core` gleich brauchen (Spec 00 §4.1: Logik ohne Server
testbar). Kein Paper- oder Velocity-Code; in beide Plugin-Jars eingebettet.

| Paket | Inhalt |
|---|---|
| `.feature` | `CoreFeature<C>`, `FeatureManager<C>`: geordnete Aktivierung, Deaktivierung umgekehrt, Fehlerisolation |
| `.service` | `ServiceRegistry`: je Schnittstelle Standard (Kern) und Ersatz (anderes Plugin), Ersatz hat Vorrang |
| `.config` | `EnvReader`, `EnvKeys`, `NodeConfig`: `NETWORK_*`-Variablen (Spec 08 §6.15), alle Probleme auf einmal |
| `.connect` | `NetworkConnections`, `Retry`: PostgreSQL → Valkey → Bus, 3 Versuche à 2 s (Spec 08 §6.2) |
| `.concurrent` | `AsyncExecutor` (virtuelle Threads), `MainThreadExecutor` (Rückkehr auf den Haupt-Thread) |
| `.bridge` | `BridgeCodec`, `OpenGuiRequest`: Plugin-Message `retromc:bridge` mit Größen-, Richtungs- und Zeichenprüfung |
| `.command` | `CommandErrorMapper`, `CommandErrorHandlers`, `CommandError`: Cloud-Fehler → `error.command.*` |
| `.text` | `I18n` (Spec 02 §8.1), `CatalogI18n` (Katalog aus core-i18n, Client-Sprache) |

## Andocken an ein bestehendes Kern-Plugin

Ränge (`RankService`), Profile (`ProfileService`), Texte (`I18n`) und Datenbankzugang (`DataSource`,
`AsyncDatabase`, `ValkeyConnector`, `MessageBus`) holen alle Features nur über die `ServiceRegistry`. Ein Adapter
ersetzt sie mit `services().register(Typ.class, implementierung)`; `Registration.close()` stellt den Standard wieder
her. Nutzer halten keine Implementierung fest, sondern verwenden `get(...)` oder `supplier(...)`.

## Tests

Unit: Feature-Reihenfolge und Fehlerisolation, Registry, Konfiguration, Wiederholung, Executor, Bridge-Rundlauf,
Cloud-Fehler mit echtem `CommandManager`, Katalog-Texte. Integration (Testcontainers): `NetworkConnectionsIT`.
