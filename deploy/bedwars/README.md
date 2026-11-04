# BedWars-Maps einrichten

Maps und Wartelobby sind **fertige Welten**. Der Code baut nichts, er lädt die Welten und richtet sie über
`map.json` ein (Format: Spec 08 §6.11, BedWars-Marker: Spec 04 §6.1.8).

## Ordner

```
<server>/maps/                 (oder NETWORK_MAP_DIR)
  <mapId>/                     fertige Welt: level.dat, region/, … + map.json
  <wartelobby>/                optional: eigene Wartelobby-Welt (ohne map.json)
```

Beim Rundenstart kopiert der Server die Welt nach `map_<mapId>` (ohne `playerdata`, `session.lock` …) und lädt sie
ohne Speichern. Maps mit Fehlern werden beim Laden mit `ERROR` im Log deaktiviert.

## Einrichten im Spiel (`/bwsetup`, Recht `retromc.admin.mapsetup`)

1. Welt nach `maps/<mapId>/` legen und den BedWars-Server starten.
2. `/bwsetup create <mapId>` – lädt die Welt und öffnet den Entwurf (eine vorhandene `map.json` wird übernommen).
3. Punkte setzen (Position des Spielers bzw. anvisierter Block):

| Befehl | Eintrag |
|---|---|
| `/bwsetup name <Name>` · `builder <Name>` (`builder clear`) | `displayName`, `builders` (Autor) |
| `/bwsetup variants 4x2,4x3,4x4` · `players <min> <max>` | Varianten, Spielerzahl |
| `/bwsetup bounds 1` und `bounds 2` | Map-Grenzen (zwei Ecken) |
| `/bwsetup spectator` | Zuschauer-Spawn |
| `/bwsetup waiting` | Wartelobby-Spawn; in einer anderen Welt zusätzlich `waitingWorld` |
| `/bwsetup spawn <team>` | Team-Spawn |
| `/bwsetup bed <team>` | Bett (anvisiertes Bett, Kopf und Fuß) |
| `/bwsetup shop <team>` · `upgrade <team>` | Item- und Upgrade-Händler |
| `/bwsetup chest <team>` | Team-Truhe (anvisierte Truhe) |
| `/bwsetup spawner <copper\|iron\|gold\|diamond\|bronze> [team]` | Spawner (Basis mit Team, Mitte/Inseln ohne) |
| `/bwsetup buildheight [y]` · `voidy [y]` | Bauhöhe, Void-Tod |
| `/bwsetup teams red,blue,…` | optionale feste Teamliste |
| `/bwsetup info` · `/bwsetup save` | fehlende Punkte anzeigen · `maps/<mapId>/map.json` schreiben |

Teams: `red`, `blue`, `yellow`, `green`, `pink`, `aqua`, `orange`, `purple`, `cyan`, `magenta`, `brown`,
`dark_green` (Reihenfolge Spec 04 §6.1.1; ohne feste Liste gelten die ersten N der Variante).

## Pflichtangaben (werden beim Laden geprüft)

- je Team: Spawn, Bett, Item-Händler, Upgrade-Händler (nicht Classic), Team-Truhe, Kupfer- und Eisen-Spawner
  (Classic: Bronze und Eisen)
- global: mindestens 4 Diamant-Spawner (nicht Classic), mindestens 1 Gold-Spawner ohne Team, `buildMaxY`,
  Zuschauer-Spawn in den Grenzen, Wartelobby-Spawn, mindestens ein Autor, `license: "own"`

`maps/beispiel/map.json` ist eine vollständige Beispiel-Konfiguration für 4 Teams (Varianten 4x2, 4x3, 4x4);
die Welt dazu gehört nicht ins Repo.
