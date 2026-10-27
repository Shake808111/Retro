# services/api – Web-API (Platzhalter)

**Gradle-Pfad:** `:services:api` · **Paket:** `net.retromc.api` · **Einstieg:** `ApiApplication` (Java-Anwendung, `GET /health`) · **Status:** Gerüst (Stub)

## Verantwortung

REST-Schnittstelle (Notiz 07, Abschnitt 7) für die Website (`web/`): Spielerprofile und Statistiken, Bestenlisten (aus Valkey-ZSETs), Clans, Entbannungsanträge, Staff-Werkzeuge mit OIDC, Webhook für den Store (idempotent auf `store.purchase.tx_id`). Bisher nur `GET /health`.

## Welches Vorbild-Feature wird nachgebaut?

Vorbild prüft Strafen und Entbannungsanträge über **Guardian** (Strafen-Portal), pro Strafe ist ein Entbannungsantrag möglich (Notiz 06, Abschnitt 2). Eigene Statistik- und Profilseiten nach Notiz 07, Abschnitt 7 (`/player/<name>`, `/leaderboards/<mode>`, `/clans/<tag>`, `/entbannung`).


Quellen: `research_notes/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken vom Vorbild übernehmen (Notiz 07, Abschnitt 9 B).
