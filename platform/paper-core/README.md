# platform/paper-core – Kern-Plugin für Paper

**Gradle-Pfad:** `:platform:paper-core` · **Paket:** `net.retromc.paper.core` · **Einstieg:** `PaperCorePlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Plattform-Gerüst (F1b)

## Verantwortung

Läuft auf **jedem** Paper-Server (Lobby, Spielserver, CityBuild) und bündelt die `core-*`-Module im Shadow-Jar.
Alle anderen Paper-Plugins hängen per `paper-plugin.yml` (`join-classpath: true`) davon ab und kompilieren nur dagegen.

Geplant laut Notiz 07, Abschnitt 6: Profil laden, Scoreboard und Tablist, GUIs, Cosmetics, Kampfprofile,
Anticheat-Hooks, Agones-SDK-Client. Bibliotheken laut Notiz 07, Abschnitt 5: FastBoard (Scoreboard),
InventoryFramework (GUIs), Incendo Cloud (Befehle), PacketEvents (Pakete) – Versionen im Katalog geprüft.

## Aufbau (F1b)

| Paket | Inhalt |
|---|---|
| `net.retromc.paper.core` | `PaperCorePlugin` (Lebenszyklus Spec 08 §6.2, Feature-Liste, `services()`), Kontext `PaperCore` |
| `.config` | `ServerConfig`: Servername, Template, Kategorie, Adresse, Port, DB (Pool Standard 5), Valkey aus `NETWORK_*` |
| `.service` | `BukkitServiceMirror`: Registry → Bukkit-`ServicesManager` (Standard `Low`, Ersatz `Highest`) |
| `.lang` | `TextFeature`: Standard-`I18n` (Katalog, Client-Sprache) |
| `.command` | `CommandFeature`, `CommandService`: cloud-paper, Fehlermeldungen aus dem Katalog (`error.command.*`) |
| `.bridge` | `BridgeFeature`, `ProxyBridge`, `PluginMessageBridge`: `RUN_COMMAND` zum Proxy, `OPEN_GUI` vom Proxy |
| `.gui` | GUI-Framework Spec 02 §5.1–5.3 auf InventoryFramework: `GuiService`, `ChestGui`, `PagedGui<T>`, `ConfirmGui`, Raster/Rahmen/Blättern |
| `.item` | `ItemBuilder` Spec 02 §5.5, `LoreWrapper` (38 Zeichen), `DyedMaterials` |

Neues Teilsystem = eine Zeile in `PaperCorePlugin.features()`. Dienste nur über die Registry holen
(`PaperCorePlugin.services().get(...)`); jeder ist per `services().register(...)` ersetzbar (z. B. Ränge, Profile,
Texte oder Datenbank aus einem bestehenden Kern-Plugin). Keine Flyway-Migration auf Spielservern.

Lokal testen: `./gradlew :platform:paper-core:runServer` (lädt Paper 26.3; die Minecraft-EULA muss in `run/eula.txt`
selbst akzeptiert werden).

## Welches Vorbild-Feature wird nachgebaut?

Die netzwerkweite Oberfläche auf allen Servern: Scoreboard, Tablist, Chat-Präfixe, Bossbar, Einstellungen
(Notiz 05, Abschnitt 6 – exakte Vorbild-Layouts sind dort als nicht verifiziert markiert, LÜCKE),
Cosmetics (Notiz 05, Abschnitt 5) und 1.8-Kampf über austauschbare Kampfprofile (Notiz 07, Abschnitt 1).

Quellen: `research_notes/`.
