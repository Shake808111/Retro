# core/core-stats – Statistik-Logik

**Gradle-Pfad:** `:core:core-stats` · **Paket:** `net.retromc.core.stats` · **Status:** angelegt (Inhalt folgt im Strang Kern-UI/Stats)

## Verantwortung

Reines Java ohne Paper und ohne externe Bibliotheken (Spec 02 §1): Punkteformeln je Modus aus
`stats.mode.points_formula`, K/D und Siegesquote mit den Formaten aus Spec 02 §6.8, das rollierende
30-Tage-Fenster (§6.5), der Ranking-Score `points × 2^21 + min(wins, 2^21−1)` (§6.6) und die Bausteine der
`/stats`-Ausgabe. Datentypen (`ModeKey`, `StatPeriod`, `StatLine`, `MatchResult`, `PlayerMatchStats`) liegen in
`core-api` (Paket `net.retromc.core.api.stats`).

Nutzer: `platform:paper-core` (Befehle, Wände, Hologramme) und `services:api` (lesend).
