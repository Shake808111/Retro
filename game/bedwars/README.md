# game/bedwars – BedWars

**Gradle-Pfad:** `:game:bedwars` · **Paket:** `dev.netzwerk.bedwars` · **Einstieg:** `BedWarsPlugin` (Paper-Plugin, `paper-plugin.yml`) · **Status:** Gerüst (Stub)

## Verantwortung

Paper-Plugin für den Modus BedWars (BedWars: Teams, Betten, Ressourcen-Spawner, Shop, Upgrades). Baut auf `game/game-framework` auf (Rundenablauf, Teams, Karten, Zuschauer, Kits, Shops, Statistik-Senke) und läuft zusammen mit `platform/paper-core` auf einem Spielserver; laut Notiz 07 (Abschnitt 3) eine Runde pro Server/Pod.

## Welches GommeHD-Feature wird nachgebaut?

GommeHD betreibt zwei BedWars-Produkte (Notiz 02, Q1):
- **„BedWars“ (BedWars 2.0)**, gestartet im Juli 2021: vier Ressourcen (Kupfer, Eisen, Gold, Diamanten),
  zwei Villager (Item-Shop und Upgrade-Shop), Upgrades und Fallen gegen Diamanten,
  moderne Minecraft-Version mit dem 1.9+-Angriffs-Cooldown.
- **„Classic BedWars“**: der ursprüngliche Modus von 2014 mit Bronze/Eisen/Gold und einem Shop, bleibt bei 1.8-PvP.
- Stand der Quellen: letzter gefundener offizieller Balance-Patch Mai 2025 (Notiz 02, Q1).

Details für die Umsetzung: Spawner (Notiz 02, Q2), Shop und Preise (Q3), Regeln/Upgrades/Fallen (Q4),
Kampf (Q8), Ranking und Statistik (Q9).

## Kampfprofil

Platzhalter `LEGACY_1_8` nach Notiz 07, Abschnitt 1. **Offen:** GommeHDs BedWars 2.0 nutzt modernen Kampf, Classic BedWars 1.8-Kampf (Notiz 02, Q1/Q8) – Kampfprofil pro Variante festlegen.


Quellen: `research_notes/GommeHD Analyse und Nachbau/` (Notiz-Nummern wie angegeben). Keine Namen, Texte,
Karten oder Grafiken von GommeHD übernehmen (Notiz 07, Abschnitt 9 B).
