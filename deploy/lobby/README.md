# Hub-Map der Hauptlobby (Testaufbau)

## Map

| | |
|---|---|
| Name | „Factions Hub“ (Burg auf schwebender Insel, Thronhalle mit rotem Teppich) |
| Autor | NetherGames Network (Bedrock-Netzwerk, geschlossen am 28.06.2026) |
| Quelle | <https://github.com/NetherGamesMC/assets> → `Factions/worlds/Hub` (Commit `5cfda13`) |
| Lizenz | Creative Commons Attribution 4.0 International (CC BY 4.0) |
| Credit (Pflicht) | „Lobby-Map: Factions Hub von NetherGames (github.com/NetherGamesMC/assets), CC BY 4.0, für Java konvertiert und verändert (Statuen, Kisten, Hologramme)“ |

Die Map-Dateien liegen **nicht** im Repo. Den Credit-Text in Server-Infos (z. B. `/credits`, Website) übernehmen.

## Einrichtung

1. Welt holen: `git clone --depth 1 https://github.com/NetherGamesMC/assets` und `Factions/worlds/Hub` kopieren
   (Bedrock-LevelDB). Eine leere `levelname.txt` mit `Hub` anlegen.
2. Nach Java konvertieren, z. B. mit `amulet-core` (Python):
   `amulet.load_level(src).save(AnvilFormat(dst))` mit `dst.create_and_open("java", (1, 21, 4))`.
3. Paper 26.3 einmal mit leerer Flat-Welt `lobby` starten und stoppen
   (`level-type=minecraft:flat`, Generator nur Luft; `allow-nether=false`, `allow-end: false` in `bukkit.yml`).
   Danach `lobby/dimensions/minecraft/overworld/region/` durch die konvertierten `region/*.mca` ersetzen.
   Den `level.dat` aus der Konvertierung **nicht** verwenden (defekte `WorldGenSettings`).
   Paper rechnet die Chunks beim Laden auf 26.3 hoch.
4. Server starten, `setup-lobby.txt` per RCON abspielen (eine Zeile = ein Befehl, `#` = Kommentar).
   Das Skript ist idempotent: Es entfernt zuerst alle Entities mit dem Tag `retromc_lobby`.

## Aufbau (Koordinaten, Overworld)

| Element | Position |
|---|---|
| Weltspawn | `-14 62 34`, Blick nach Norden (Halleneingang, zum Thron) |
| Spawn-Hologramm „Willkommen auf RetroMC“ | `-13.5 66.2 12.5` (über dem Teppich vor dem Thron) |
| Statuen (Mannequin auf Sockel, Hologramm 3 Zeilen nach Spec 03 §4.7 `NPC_JOIN`) | linke Reihe x=`-17.5`, rechte Reihe x=`-9.5`, z=`14.5/18.5/22.5/26.5/30.5`, y=`63` |
| Reihenfolge links (Nord→Süd) | BedWars, SurvivalGames, TTT, CityBuild, GunGame |
| Reihenfolge rechts | SkyWars, EnderGames, MasterBuilders, Cores, JumpLeague |
| Tägliche Belohnung (Truhe auf Goldblock) | `-17 62 33` |
| Coin-Truhe | `-11 62 33` |
| Item-Truhe (Endertruhe auf Smaragdblock) | `-17 62 35` |

Welt-Regeln: Zeit fest auf 6000, Wetter klar, `spawn_mobs`/`spawn_patrols`/`spawn_wandering_traders` aus,
kein Fall-/Feuer-/Ertrinkschaden, `difficulty peaceful`, `defaultgamemode adventure`.
Die Statuen und Hologramme sind Platzhalter: Im Betrieb ersetzt das Lobby-Plugin sie über `NpcService`/
`HologramService` (Spec 03 §6.1) und schreibt die Spielerzahl in Zeile 2.
