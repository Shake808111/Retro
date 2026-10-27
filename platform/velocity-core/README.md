# platform/velocity-core – Kern-Plugin für den Proxy

**Gradle-Pfad:** `:platform:velocity-core` · **Paket:** `net.retromc.velocity.core` · **Einstieg:** `VelocityCorePlugin` (`@Plugin`, Velocity 4.3) · **Status:** Gerüst

## Verantwortung

Velocity-Plugin (Notiz 07, Abschnitte 1, 4 und 6):
- Login-Prüfung (Banns aus `moderation.punishment`, Mute-Status über Valkey-Cache);
- Freunde-, Party- und Clan-Befehle, private Nachrichten, netzwerkweiter Chat;
- Routing in Lobby und Spiele, MOTD und Wartungsmodus;
- Profil beim Login nach Valkey laden, Präsenz `presence:<uuid>` pflegen.

Plugin-ID und Name kommen aus `BuildInfo` (also aus `brandName` in `gradle.properties`); der Velocity-
Annotation-Prozessor erzeugt daraus `velocity-plugin.json`.

Lokal testen: `./gradlew :platform:velocity-core:runVelocity` (lädt Velocity 4.3.0).

## Welches Vorbild-Feature wird nachgebaut?

Die proxyweiten Systeme vom Vorbild (Notiz 05, Abschnitte 3 und 7): `/friend` (Kurzform `/fr`), `/party` mit
Kurzformen (`/p`, `/pi`, `/pa` …), `/clan`, `/msg`, `/l` zurück in die Lobby, `/nick`, `/report`; jeweils mit
Hilfeseite über `/<system>` und `/<system> 2`. Rang-abhängige Limits (z. B. Partygröße) laut Notiz 05, Abschnitt 3.
Durchsetzung der Strafen nach dem Vorbild des Regelwerks in Notiz 06, Abschnitt 2.

Quellen: `research_notes/`.
