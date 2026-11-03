package net.retromc.game.framework.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.retromc.core.api.map.MapMeta;
import net.retromc.game.framework.CombatProfile;
import net.retromc.game.framework.GameVariant;
import net.retromc.game.framework.TeamMode;
import net.retromc.game.framework.team.TeamColor;
import org.junit.jupiter.api.Test;

/** Spec 03 §9 #23: {@code MapValidator} deaktiviert Maps ohne ausreichende Spawns. */
class MapValidatorTest {

    private static final MapMeta.Bounds BOUNDS = new MapMeta.Bounds(new MapMeta.Position(-100, 0, -100),
            new MapMeta.Position(100, 200, 100));
    private static final MapMeta.Spawn CENTER = new MapMeta.Spawn(0, 100, 0, 0, 0);

    private static GameMap map(String markers, boolean waiting) {
        return new GameMap(new MapMeta("test", "Test", "bedwars", List.of("4x1"), 1, List.of("Bauteam"), "own", 2,
                16, BOUNDS, CENTER, waiting ? CENTER : null, markers));
    }

    private static final String SPAWN = "{\"x\":1,\"y\":64,\"z\":1}";

    @Test
    void vollstaendigeTeamMapIstGueltig() {
        GameMap map = map("{\"spawns\":{\"red\":" + SPAWN + ",\"blue\":" + SPAWN + ",\"yellow\":" + SPAWN
                + ",\"green\":" + SPAWN + "},\"combat\":\"LEGACY_1_8\",\"icon\":\"red_bed\"}", true);
        assertEquals(List.of(), MapValidator.validate(map, TeamMode.TEAMS, GameVariant.parse("4x1"),
                TeamColor.BEDWARS_ORDER, false));
        assertEquals(CombatProfile.LEGACY_1_8, map.combat().orElseThrow());
        assertEquals("red_bed", map.icon());
    }

    @Test
    void fehlendeSpawnsDeaktivieren() {
        GameMap map = map("{\"spawns\":{\"red\":" + SPAWN + ",\"blue\":" + SPAWN + "}}", true);
        List<String> errors = MapValidator.validate(map, TeamMode.TEAMS, GameVariant.parse("4x1"),
                TeamColor.BEDWARS_ORDER, false);
        assertEquals(List.of("spawns.yellow fehlt", "spawns.green fehlt"), errors);
    }

    @Test
    void soloBrauchtGenugSpawns() {
        GameMap map = map("{\"spawns\":{\"solo\":[" + SPAWN + "," + SPAWN + "]}}", true);
        List<String> errors = MapValidator.validate(map, TeamMode.SOLO, GameVariant.parse("4x1"), List.of(), false);
        assertEquals(List.of("spawns.solo: 2 < maxPlayers 4"), errors);
    }

    @Test
    void wartelobbyVarianteUndGrenzen() {
        GameMap map = map("{\"spawns\":{\"red\":{\"x\":500,\"y\":64,\"z\":0}}}", false);
        List<String> errors = MapValidator.validate(map, TeamMode.TEAMS, GameVariant.parse("8x1"),
                TeamColor.BEDWARS_ORDER, false);
        assertTrue(errors.contains("Variante 8x1 nicht unterstützt"));
        assertTrue(errors.contains("waitingLobby fehlt und es gibt keine Wartelobby-Welt"));
        assertTrue(errors.contains("spawns.red außerhalb von bounds"));
        assertTrue(MapValidator.validate(map, TeamMode.TEAMS, GameVariant.parse("8x1"), TeamColor.BEDWARS_ORDER,
                true).stream().noneMatch(error -> error.startsWith("waitingLobby")));
    }

    @Test
    void expliziteTeamliste() {
        GameMap map = map("{\"teams\":[\"pink\",\"cyan\"],\"spawns\":{\"pink\":" + SPAWN + ",\"cyan\":" + SPAWN
                + "}}", true);
        assertEquals(List.of("pink", "cyan"), MapValidator.teamIds(map, GameVariant.parse("2x1"),
                TeamColor.BEDWARS_ORDER));
        assertTrue(MapValidator.validate(map, TeamMode.TEAMS, GameVariant.parse("4x1"), TeamColor.BEDWARS_ORDER,
                false).contains("teams: 2 < 4 Teams der Variante"));
    }
}
