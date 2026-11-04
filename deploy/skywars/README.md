# SkyWars-Maps einrichten

Maps und Wartelobby sind **fertige Welten**. Der Code baut nichts, er lädt die Welten und richtet sie über
`map.json` ein (Format: Spec 08 §6.11, SkyWars-Marker: Spec 04 §6.2.2 und §8.3). Ablauf, Ordner, Lizenzen und das
Entfernen einer Wartelobby in der Map-Welt sind dieselben wie bei BedWars (`deploy/bedwars/README.md`).

## Ordner

```
<server>/maps/                 (oder NETWORK_MAP_DIR)
  <mapId>/                     fertige Welt + map.json (+ CREDITS.md bei freier Lizenz)
    level.dat
    dimensions/minecraft/overworld/region/   Weltformat ab 26.x (ältere Welten: region/)
  <wartelobby>/                optional: eigene Wartelobby-Welt (ohne map.json)
```

Beim Rundenstart kopiert der Server die Welt nach `map_<mapId>` und lädt sie ohne Speichern. Maps mit Fehlern
werden beim Laden mit `ERROR` im Log deaktiviert. Eine Map gilt nur für die Varianten in `variants` (aktiv:
`8x1`, `16x1`, `8x2`, `4x4`; Spec 04 §10 #25).

## Einrichten im Spiel (`/swsetup`, Recht `retromc.admin.mapsetup`)

1. Welt nach `maps/<mapId>/` legen und den SkyWars-Server starten.
2. `/swsetup create <mapId>` – lädt die Welt und öffnet den Entwurf (eine vorhandene `map.json` wird übernommen,
   ihre Lizenz bleibt).
3. Punkte setzen (Position des Spielers bzw. anvisierter Block):

| Befehl | Eintrag |
|---|---|
| `/swsetup name <Name>` · `builder <Name>` (`builder clear`) | `displayName`, `builders` |
| `/swsetup variants 8x1,8x2` · `players <min> <max>` | Varianten, Spielerzahl |
| `/swsetup bounds 1` und `bounds 2` | Map-Grenzen (zwei Ecken); außerhalb wird nicht gebaut |
| `/swsetup spectator` · `waiting` | Zuschauer-Spawn · Wartelobby-Spawn (andere Welt: zusätzlich `waitingWorld`) |
| `/swsetup kitsettings` | Hologramm „Kit-Einstellungen“ mit Klickfläche in der Wartelobby (optional) |
| `/swsetup island <n>` | Spawn an der eigenen Position zu Insel `n` (je Spieler der Teamgröße einen; Blickrichtung zählt) |
| `/swsetup island <n> clear` | Spawns und Truhen von Insel `n` leeren |
| `/swsetup chest <n>` · `chest mid` | anvisierte Truhe bzw. anvisiertes Fass zu Insel `n` (Tier 1) bzw. zur Mitte (Tier 2) |
| `/swsetup mid 1` und `mid 2` | Ecken der Mitte-Region (Challenge „ohne die Mitte“) |
| `/swsetup border [radius]` | Border-Zentrum an der eigenen Position, Start-Radius (ohne Angabe: halbe Kante der Grenzen) |
| `/swsetup voidy [y]` | Void-Tod unterhalb dieser Höhe |
| `/swsetup info` · `/swsetup save` | Fehlendes anzeigen · `maps/<mapId>/map.json` schreiben |

Insel `n` gehört Team `n` (Solo: Spieler `n`). Beim Speichern entstehen daraus die Rahmen-Marker `teams`
(`"1" … "N"`), `spawns.<n>` (erster Spawn je Insel) und `spawns.solo`.

## Pflichtangaben (werden beim Laden je Variante geprüft)

- Inseln: mindestens so viele wie Teams der Variante, je Insel Spawns ≥ Teamgröße und **2–3** Truhen/Fässer
- Mitte: Region (`mid.min`/`mid.max`) und **4–8** Truhen/Fässer; Schneebälle und Eier gibt es nur dort
- `voidY`, Border-Radius > 0, Zuschauer-Spawn in den Grenzen, Wartelobby-Spawn, mindestens ein Autor
- `license`: `own` oder `cc-by-4.0` / `cc-by-sa-4.0` mit `CREDITS.md` im Map-Ordner

## Runde (Spec 04 §6.2.1)

Teleport in einen Glaskäfig 1 Block über dem Inselboden → 3-2-1 → Käfig auf, Aufbauphase mit 30 s Schutzzeit
(kein PvP-Schaden, Kit-Fähigkeiten gesperrt) → Kampf → Chestrefill Stufe 1/2 bei Kampf + 5:00/10:00 (`events.yml`)
→ Border bei Kampf + 10:00 oder 60 s nach „nur noch 2 Teams“, schrumpft in 180 s auf 10 Blöcke um das
Border-Zentrum. Kits (`kits/skywars.yml`), Loot (`loot/*.yml`) und Challenges (`challenges.yml`) liegen im
Plugin-Ordner und sind änderbar; `/swdebug event <id>` löst ein Event sofort aus (`retromc.admin.debug`).

## Beispiel

`maps/beispiel/map.json` ist eine vollständige Beispiel-Konfiguration mit 8 Inseln (je 2 Spawns, 2–3 Truhen),
Mitte mit 6 Truhen, Border-Radius 90 und Wartelobby über der Mitte (Varianten `8x1`, `8x2`); die Welt dazu gehört
nicht ins Repo. Ein Unit-Test prüft sie gegen Rahmen und SkyWars-Regeln.
