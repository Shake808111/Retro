package net.retromc.game.framework.map;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.retromc.core.api.map.MapMeta;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Wartelobby in der Map-Welt (z. B. eine Plattform rund 25 Blöcke über {@code buildMaxY}): wird beim Rundenstart
 * entfernt, damit sie im Spiel weder stört noch bebaut wird. Gefunden wird sie ab dem Block unter dem
 * {@code waitingLobby}-Spawn als zusammenhängende Blockgruppe (6er-Nachbarschaft) mit Grenzen für Höhe, Abstand und
 * Größe; reicht die Gruppe darüber hinaus (also in die Map), bleibt alles stehen. Marker
 * {@code "waitingLobbyKeep": true} schaltet das Entfernen ab. Die Suche ist ohne Server testbar ({@link #collect}).
 */
public final class WaitingPlatform {

    /** Höchstzahl der Blöcke einer Wartelobby. */
    public static final int MAX_BLOCKS = 20_000;
    /** Größter waagrechter Abstand zum Spawn. */
    public static final int MAX_RADIUS = 40;
    /** Tiefe unter dem Spawn, bis zu der die Lobby reichen darf. */
    public static final int MAX_DEPTH = 12;
    /** Höhe über dem Spawn, bis zu der die Lobby reichen darf. */
    public static final int MAX_HEIGHT = 16;

    /** Blockabfrage für die Suche ({@code true} = fester Block, gehört ggf. zur Lobby). */
    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    /** Blockposition. */
    public record Pos(int x, int y, int z) {
    }

    private WaitingPlatform() {
    }

    /**
     * Sammelt die Blöcke der Wartelobby ab {@code spawn} (Fuß des Spielers).
     *
     * @return Positionen; leer, wenn unter dem Spawn nichts liegt oder die Gruppe eine Grenze überschreitet
     */
    public static Optional<Set<Pos>> collect(int spawnX, int spawnY, int spawnZ, Solid solid) {
        Pos start = null;
        for (int dy = 1; dy <= 3 && start == null; dy++) {
            if (solid.at(spawnX, spawnY - dy, spawnZ)) {
                start = new Pos(spawnX, spawnY - dy, spawnZ);
            }
        }
        if (start == null) {
            return Optional.empty();
        }
        Set<Pos> found = new LinkedHashSet<>();
        Deque<Pos> open = new ArrayDeque<>();
        open.add(start);
        found.add(start);
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!open.isEmpty()) {
            Pos current = open.poll();
            for (int[] step : steps) {
                Pos next = new Pos(current.x() + step[0], current.y() + step[1], current.z() + step[2]);
                if (found.contains(next) || !solid.at(next.x(), next.y(), next.z())) {
                    continue;
                }
                if (Math.abs(next.x() - spawnX) > MAX_RADIUS || Math.abs(next.z() - spawnZ) > MAX_RADIUS
                        || next.y() < spawnY - MAX_DEPTH || next.y() > spawnY + MAX_HEIGHT
                        || found.size() >= MAX_BLOCKS) {
                    return Optional.empty();
                }
                found.add(next);
                open.add(next);
            }
        }
        return Optional.of(found);
    }

    /**
     * Entfernt die Wartelobby aus der Map-Welt, sofern sie dort liegt (kein {@code waitingWorld}) und nicht mit
     * {@code waitingLobbyKeep} geschützt ist.
     *
     * @return Anzahl entfernter Blöcke
     */
    public static int remove(GameMap map, World world, @Nullable Logger log) {
        MapMeta.Spawn spawn = map.meta().waitingLobby();
        if (spawn == null || map.markers().has("waitingWorld") || (map.markers().has("waitingLobbyKeep")
                && map.markers().get("waitingLobbyKeep").getAsBoolean())) {
            return 0;
        }
        int x = (int) Math.floor(spawn.x());
        int y = (int) Math.floor(spawn.y());
        int z = (int) Math.floor(spawn.z());
        Optional<Set<Pos>> blocks = collect(x, y, z, (bx, by, bz) -> !world.getBlockAt(bx, by, bz).getType()
                .isAir());
        if (blocks.isEmpty()) {
            if (log != null) {
                log.warn("Wartelobby von {} nicht entfernt: keine abgegrenzte Plattform unter dem Spawn", map.id());
            }
            return 0;
        }
        for (Pos pos : blocks.get()) {
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            block.setType(Material.AIR, false);
        }
        return blocks.get().size();
    }
}
