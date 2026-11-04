# game/bedwars – BedWars

**Gradle-Pfad:** `:game:bedwars` · **Paket:** `net.retromc.bedwars` · **Einstieg:** `BedWarsPlugin`
(`${brandName}BedWars`), `BedWarsMode` · **Status:** Paket 4a (Spec 04 BedWars-Teil)

## Umfang

- Presets über `config.yml → bedwars.preset`: `modern` (Stats-Modus `bedwars`, Hit-Cooldown an) und `classic`
  (`bedwars_classic`, 1.8-Kampf, Bronze, ein Händler, Gold-Vote `/goldvote [save]`).
- Varianten 8x1, 4x2, 4x3, 4x4, 8x2, 8x4, 9x5 (Template `bedwars-<variante>` oder `game.variant`).
- Betten, Respawn 5 s, Final Kills, Ressourcen-Übertragung (Kill/Verlassen), Team-Truhe, Rundenhöchstdauer.
- Generatoren mit Stufenplan (Diamant II 05:00 / III 15:00, Gold II 10:00 / III 25:00), Hologramme.
- Item-Shop (`shop.yml`, 9 Kategorien, Schnellkauf), Shop-Einstellungen, Schnellkauf-Editor, Upgrade-Shop,
  Fallen, Sonder-Items, Board §4.2, Chat `@all`/Team/`[✗]`.
- Map-Setup `/bwsetup` (siehe `deploy/bedwars/README.md`), Beispiel `deploy/bedwars/maps/beispiel/map.json`.
- Daten: `BedWarsStore` (Schnellkauf, Shop-Einstellungen, Gold-Wahl) und `ModeLevelStore` (Modus-Level) aus
  core-data, Migration `V4_1__bedwars_skywars.sql`; beide über die `ServiceRegistry` austauschbar.

Keine Namen, Texte, Maps oder Grafiken vom Vorbild; alle Spielertexte als i18n-Schlüssel (`bw.*`).
