# game/skywars – SkyWars

**Gradle-Pfad:** `:game:skywars` · **Paket:** `net.retromc.skywars` · **Einstieg:** `SkyWarsPlugin` (Paper-Plugin
`${brandName}SkyWars`) · **Status:** umgesetzt nach Spec 04 (SkyWars-Teil) auf dem Spiel-Rahmen (Spec 03)

## Aufbau

| Paket | Inhalt |
|---|---|
| `skywars` | `SkyWarsPlugin` (Preset `modern`/`classic`, Solo = `TeamMode.SOLO`, Teams = `NUMBERED`), `SkyWarsMode` (Ablauf, Käfig, Tod, Rundenende), `SkyWarsConfig`, Kampf-/Welt-Listener, Wartelobby-Chat mit Level, `/kit`, `/swdebug` |
| `phase` | `RoundPlan`: Käfig 3-2-1, Schutzzeit 30 s, Kampf, Border-Start (Kampf + 600 s bzw. 60 s nach 2 Teams) |
| `kit` | `KitCatalog` (`kits/skywars.yml`), `KitService` (Besitz/Kauf/Auswahl über `KitStore`, Einstellungen über `SkyWarsStore`), `AbilityTracker`, `Abilities`, Kit-Menü (§5.9), Kit-Einstellungen (§5.10) |
| `chest` | `LootTables` (`loot/skywars_t1/t2/refill1/refill2.yml`), `ChestService` (Erstfüllung mit Garantien, Chestrefill) |
| `event` | `KitAbilityUseEvent`, `ChestRefillEvent`, `BorderStartEvent`, `ChallengeResultEvent`, `GlobalEventSchedule` (`events.yml`) |
| `border` | `BorderService` (Vanilla-Weltgrenze, 1 Schaden/s außerhalb) |
| `challenge` | `Challenge`, `ChallengeTracker`, `ChallengeSet` (`challenges.yml`), `ChallengeService` (Bossbar) |
| `board` | Ingame-Board (§4.3), Map-Menü (§5.11), `LevelTag` (Modus-Level) |
| `map`, `setup` | `SkyWarsMap` (Inseln, Mitte, Border, Void), `/swsetup` |

Daten: `games.kit_owned`, `games.kit_selected` (Migration `V4_2`), `games.sw_kit_settings`, `games.mode_level`
(`V4_1`); Kauf über das Coin-Journal mit Idempotenz-Schlüssel `kit:{mode}:{kit}:{uuid}`.

Einrichtung der Maps: `deploy/skywars/README.md`. Tests: `src/test` (Spec 04 §9 #11–#16, #18 ohne Server;
#15 zusätzlich als Integrationstest `SkyWarsRepositoryIT` in core-data).
