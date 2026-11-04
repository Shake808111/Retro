package net.retromc.bedwars;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.map.MapMeta;
import net.retromc.core.i18n.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Händler (Spec 04 §4.9, §4.10): Item-Händler (VILLAGER, Beruf FARMER) und Upgrade-Händler (ZOMBIE_VILLAGER) je
 * Team, je Map überschreibbar ({@code markers.npc}), unverwundbar, ohne KI, Kopf folgt dem nächsten Spieler, darüber
 * {@code §e§lITEMS} bzw. {@code §e§lUPGRADES} (TextDisplay, Größe 2,0). Auch der „Mobile Shop“ nutzt diese Händler.
 */
final class NpcSpawner {

    /** PDC-Schlüssel der Händler-Art. */
    static final NamespacedKey KEY = new NamespacedKey(BuildInfo.BRAND_ID, "bw_npc");
    static final String SHOP = "shop";
    static final String UPGRADE = "upgrade";
    /** Sichtweite für die Kopfdrehung. */
    static final double LOOK_RANGE = 8;

    private static final List<Entity> ENTITIES = new CopyOnWriteArrayList<>();
    private static @Nullable BukkitTask look;

    private NpcSpawner() {
    }

    static void spawn(BedWarsMode mode) {
        for (BwTeam team : mode.bwTeams()) {
            String id = team.team().id();
            mode.map().shop(id).ifPresent(spawn -> spawnNpc(mode, location(mode, spawn), SHOP));
            if (!mode.classic()) {
                mode.map().upgrade(id).ifPresent(spawn -> spawnNpc(mode, location(mode, spawn), UPGRADE));
            }
        }
        look = Bukkit.getScheduler().runTaskTimer(mode.plugin(), NpcSpawner::look, 5L, 5L);
    }

    private static Location location(BedWarsMode mode, MapMeta.Spawn spawn) {
        return mode.game().location(spawn);
    }

    /** Händler mit Hologramm; liefert die Entities (für den Mobilen Shop). */
    static List<Entity> spawnNpc(BedWarsMode mode, Location location, String kind) {
        String fallback = kind.equals(SHOP) ? "VILLAGER" : "ZOMBIE_VILLAGER";
        EntityType type;
        try {
            type = EntityType.valueOf(mode.map().npc(kind, fallback));
        } catch (IllegalArgumentException e) {
            type = EntityType.valueOf(fallback);
        }
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setInvulnerable(true);
            living.setSilent(true);
            living.setCollidable(false);
            living.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Villager villager) {
            villager.setProfession(Villager.Profession.FARMER);
        }
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, kind);
        String key = kind.equals(SHOP) ? "bw.holo.items" : "bw.holo.upgrades";
        TextDisplay text = location.getWorld().spawn(location.clone().add(0, entity.getHeight() + 0.9, 0),
                TextDisplay.class, display -> {
                    display.text(mode.texts().i18n().render(Messages.DEFAULT_LOCALE, key));
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setPersistent(false);
                    display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                            new Vector3f(2f, 2f, 2f), new AxisAngle4f()));
                });
        ENTITIES.add(entity);
        ENTITIES.add(text);
        return List.of(entity, text);
    }

    static Optional<String> kind(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    private static void look() {
        for (Entity entity : ENTITIES) {
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                continue;
            }
            Player nearest = null;
            double best = LOOK_RANGE * LOOK_RANGE;
            for (Player player : living.getLocation().getNearbyPlayers(LOOK_RANGE)) {
                double distance = player.getLocation().distanceSquared(living.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            if (nearest != null) {
                Location from = living.getLocation();
                Location to = nearest.getEyeLocation();
                double dx = to.getX() - from.getX();
                double dz = to.getZ() - from.getZ();
                double dy = to.getY() - living.getEyeLocation().getY();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                living.setRotation(yaw, pitch);
            }
        }
    }

    static void remove() {
        BukkitTask task = look;
        if (task != null) {
            task.cancel();
        }
        look = null;
        ENTITIES.forEach(Entity::remove);
        ENTITIES.clear();
    }

    /** Entfernt einzelne Entities (Mobiler Shop nach Ablauf). */
    static void remove(List<Entity> entities) {
        entities.forEach(Entity::remove);
        ENTITIES.removeAll(entities);
    }
}
