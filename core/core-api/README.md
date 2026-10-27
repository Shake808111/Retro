# core/core-api – gemeinsame Typen

**Gradle-Pfad:** `:core:core-api` · **Paket:** `net.retromc.core.api` · **Einstieg:** `BuildInfo` (generiert), `server.ServerState`, `server.ServerInfo`, `social.FriendPair` · **Status:** Gerüst

## Verantwortung

Reines Java ohne Paper-/Velocity-Abhängigkeit (Notiz 07, Abschnitt 6): DTOs, IDs, Ereignisse, Schnittstellen,
die Proxy, Paper-Server und Dienste gemeinsam nutzen.

- `BuildInfo` wird beim Build aus `gradle.properties` erzeugt (Vorlage: `src/template/java/...`). Damit stehen
  Marke und Version als Compile-Zeit-Konstanten bereit, z. B. für die Velocity-Annotation `@Plugin`.
- `ServerState`/`ServerInfo` bilden den Valkey-Hash `server:<name>` ab (Notiz 07, Abschnitt 4).
- `FriendPair` normalisiert Freundespaare in **PostgreSQL-Reihenfolge** für `social.friendship` mit `CHECK (a < b)`.
  Java vergleicht UUIDs mit Vorzeichen, PostgreSQL byteweise ohne – ein Unit-Test sichert das ab.

## Welches Vorbild-Feature wird nachgebaut?

Kein einzelnes Feature, sondern die gemeinsame Grundlage für die netzwerkweiten Systeme Freunde, Party und Clan
(Notiz 05, Abschnitt 3) und die Server-Registry hinter dem NPC-Joinsystem der Lobby (Notiz 05, Abschnitt 1).

Quellen: `research_notes/`.
