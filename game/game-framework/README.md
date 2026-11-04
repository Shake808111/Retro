# game/game-framework – Spiel-Rahmen aller Minigames

**Gradle-Pfad:** `:game:game-framework` · **Paket:** `net.retromc.game.framework` · **Einstieg:** `MiniGamePlugin`
(Basisklasse der Modus-Plugins), `GameMode` (Schnittstelle des Modus), `Game` (laufende Runde) · **Status:** Paket 4a

## Verantwortung (Spec 03 §6.5–§6.13, §8.1)

Bibliothek, die in jedes Modus-Plugin eingebettet wird. Der Modus liefert nur `GameDefinition`, `GameMode`
(Teamreihenfolge, Board-Block, Hotbar-Slots 3–6, Rundenblock, Basis-Coins); alles andere kommt aus dem Rahmen:

| Paket | Inhalt |
|---|---|
| `phase` | `CountdownPlan` (60 s, Ansagen, voll//start → 10 s, Abbruch, Schutz ≤ 5 s), `RestartPlan` (20 s), `PhaseTimer` |
| `waiting` | `WaitingRoom`: Join-Ablauf, Hotbar §4.10, Board §4.2, Actionbar, Tipps (30 s), Countdown im XP-Balken, `/start` |
| `team` | `TeamColor`, `TeamAllocator` (BALANCE/PREFER_FILLED, Partys), `TeamRegistry`, `TeamGui` (§5.10 bzw. Modus-Stil) |
| `map` | `MapSelection` (Auswahl, Voting, `/forcemap` mit `ForcemapPolicy`), `GameMap`, `MapValidator`, `MapDraft` (Setup) |
| `spectate` | `SpectatorService`: Zuschauer-Zustand, Hotbar §4.11, Teleporter §5.12, Einstellungen §5.13, „Nächste Runde“ |
| `kill` | `KillFeed`, `KillOptions`, `HeartLine`, `DamageTracker` (Killer ≤ 10 s) |
| `end` | `RoundEnd`: Sieg, Title, Feuerwerk, `StatsService.submit` genau einmal (`SubmitOnce`), Block → Restart → Coins, `SEND_LOBBY`, `GAME_ENDED`, Server-Stopp |
| `server` | `ServerStatePublisher` (nur über `ServerLifecycle`), `PrivateRoundContext` |
| `chat`, `command`, `event` | Runden-Chat, `/start /forcemap /fm /game /team /teleporter`, Bukkit-Events §8.1 |
| `shop`, `kit` | `Purchase` (Bezahlen mit Inventar-Währung), `Kit` |

Maps sind fertige Welten mit `map.json` (Spec 08 §6.11), geladen über den paper-core-Dienst `MapService`; die
Wartelobby liegt auf der Map (`waitingLobby`) oder in einer eigenen Welt (`markers.waitingWorld`).

Alle Kern-Dienste (I18n, GUI, Board, Hotbar, Stats, Coins, Server-Registry …) kommen bei jedem Gebrauch aus der
`ServiceRegistry` von paper-core und sind austauschbar; die laufende Runde ist dort als `Game` registriert.
