# platform/paper-core – Kern-Plugin für Paper

**Gradle-Pfad:** `:platform:paper-core` · **Paket:** `dev.netzwerk.paper.core` · **Einstieg:** `PaperCorePlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst

## Verantwortung

Läuft auf **jedem** Paper-Server (Lobby, Spielserver, CityBuild) und bündelt die `core-*`-Module im Shadow-Jar.
Alle anderen Paper-Plugins hängen per `paper-plugin.yml` (`join-classpath: true`) davon ab und kompilieren nur dagegen.

Geplant laut Notiz 07, Abschnitt 6: Profil laden, Scoreboard und Tablist, GUIs, Cosmetics, Kampfprofile,
Anticheat-Hooks, Agones-SDK-Client. Bibliotheken laut Notiz 07, Abschnitt 5: FastBoard (Scoreboard),
InventoryFramework (GUIs), Incendo Cloud (Befehle), PacketEvents (Pakete) – Versionen im Katalog geprüft.

Lokal testen: `./gradlew :platform:paper-core:runServer` (lädt Paper 26.3; die Minecraft-EULA muss in `run/eula.txt`
selbst akzeptiert werden).

## Welches GommeHD-Feature wird nachgebaut?

Die netzwerkweite Oberfläche auf allen Servern: Scoreboard, Tablist, Chat-Präfixe, Bossbar, Einstellungen
(Notiz 05, Abschnitt 6 – exakte GommeHD-Layouts sind dort als nicht verifiziert markiert, LÜCKE),
Cosmetics (Notiz 05, Abschnitt 5) und 1.8-Kampf über austauschbare Kampfprofile (Notiz 07, Abschnitt 1).

Quellen: `research_notes/GommeHD Analyse und Nachbau/`.
