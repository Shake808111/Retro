# platform/velocity-core – Kern-Plugin für den Proxy

**Gradle-Pfad:** `:platform:velocity-core` · **Paket:** `net.retromc.velocity.core` · **Einstieg:** `VelocityCorePlugin` (`@Plugin`, Velocity 4.3) · **Status:** Plattform-Gerüst (F1b)

## Verantwortung

Velocity-Plugin (Notiz 07, Abschnitte 1, 4 und 6):
- Login-Prüfung (Banns aus `moderation.punishment`, Mute-Status über Valkey-Cache);
- Freunde-, Party- und Clan-Befehle, private Nachrichten, netzwerkweiter Chat;
- Routing in Lobby und Spiele, MOTD und Wartungsmodus;
- Profil beim Login nach Valkey laden, Präsenz `presence:<uuid>` pflegen.

Plugin-ID und Name kommen aus `BuildInfo` (also aus `brandName` in `gradle.properties`); der Velocity-
Annotation-Prozessor erzeugt daraus `velocity-plugin.json`.

## Aufbau (F1b)

`VelocityCorePlugin` (`@Dependency luckperms`, Lebenszyklus Spec 08 §6.3) startet PostgreSQL → Valkey →
Nachrichtenbus (je 3 Versuche, sonst Proxy-Stopp) und dann die Features aus `features()`: `TextFeature`
(Standard-`I18n`), `CommandFeature` (cloud-velocity, Fehler aus dem Katalog), `BridgeFeature` (`ServerBridge`:
`OPEN_GUI` an den Server; `RUN_COMMAND` vom Server wird als Spieler ausgeführt, vom Client gesendete Bridge-Nachrichten
werden verworfen und nie weitergeleitet). Dienste über `VelocityCorePlugin.services()`, jeder ersetzbar.

Lokal testen: `./gradlew :platform:velocity-core:runVelocity` (lädt Velocity 4.3.0).

## Welches Vorbild-Feature wird nachgebaut?

Die proxyweiten Systeme vom Vorbild (Notiz 05, Abschnitte 3 und 7): `/friend` (Kurzform `/fr`), `/party` mit
Kurzformen (`/p`, `/pi`, `/pa` …), `/clan`, `/msg`, `/l` zurück in die Lobby, `/nick`, `/report`; jeweils mit
Hilfeseite über `/<system>` und `/<system> 2`. Rang-abhängige Limits (z. B. Partygröße) laut Notiz 05, Abschnitt 3.
Durchsetzung der Strafen nach dem Vorbild des Regelwerks in Notiz 06, Abschnitt 2.

Quellen: `research_notes/`.
