package net.retromc.paper.core.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link NpcService} auf der Vanilla-Entity {@link Mannequin} (Spielerfigur mit Skin, ohne KI): nicht
 * persistent, unverwundbar, unbeweglich; Rechts-/Linksklick lösen die Handler aus, alle 5 Ticks dreht sich der Kopf
 * zum nächsten Spieler.
 */
public final class NpcFeature implements CoreFeature<PaperCore>, Listener, NpcService {

    /** Reichweite für den Blick zum Spieler. */
    static final double LOOK_RANGE = 8.0;
    private static final long LOOK_TICKS = 5;

    private final Map<UUID, Spawned> npcs = new ConcurrentHashMap<>();
    private @Nullable Registration registration;
    private @Nullable BukkitTask look;

    @Override
    public String name() {
        return "NPCs";
    }

    @Override
    public void enable(PaperCore context) {
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        look = Bukkit.getScheduler().runTaskTimer(context.plugin(), this::lookAtPlayers, LOOK_TICKS, LOOK_TICKS);
        registration = context.services().registerDefault(NpcService.class, this);
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        BukkitTask task = look;
        if (task != null) {
            task.cancel();
        }
        look = null;
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        npcs.values().forEach(npc -> npc.entity.remove());
        npcs.clear();
    }

    @Override
    public Npc spawn(NpcSpec spec) {
        Location location = spec.location();
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("NPC ohne Welt");
        }
        Mannequin entity = location.getWorld().spawn(location, Mannequin.class, mannequin -> {
            mannequin.setPersistent(false);
            mannequin.setInvulnerable(true);
            mannequin.setImmovable(true);
            mannequin.setAI(false);
            mannequin.setSilent(true);
            mannequin.setCollidable(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setDescription(null);
            if (spec.skin() != null) {
                mannequin.setProfile(ResolvableProfile.resolvableProfile(spec.skin()));
            }
            if (spec.name() != null) {
                mannequin.customName(spec.name());
                mannequin.setCustomNameVisible(true);
            }
        });
        Spawned npc = new Spawned(entity, spec);
        npcs.put(entity.getUniqueId(), npc);
        return npc;
    }

    private void lookAtPlayers() {
        for (Spawned npc : npcs.values()) {
            if (!npc.spec.lookAtPlayers() || !npc.entity.isValid()) {
                continue;
            }
            Location eye = npc.entity.getEyeLocation();
            Player nearest = null;
            double best = LOOK_RANGE * LOOK_RANGE;
            for (Player player : npc.entity.getWorld().getPlayers()) {
                double distance = player.getLocation().distanceSquared(npc.entity.getLocation());
                if (distance <= best) {
                    best = distance;
                    nearest = player;
                }
            }
            if (nearest != null) {
                Vector direction = nearest.getEyeLocation().toVector().subtract(eye.toVector());
                Location facing = eye.clone().setDirection(direction);
                npc.entity.setRotation(facing.getYaw(), facing.getPitch());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        Spawned npc = npcs.get(event.getRightClicked().getUniqueId());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        Consumer<Player> handler = npc.spec.onRightClick();
        if (handler != null && event.getHand() == EquipmentSlot.HAND) {
            handler.accept(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        Spawned npc = npcs.get(event.getEntity().getUniqueId());
        if (npc == null) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player) {
            Consumer<Player> handler = npc.spec.onLeftClick();
            if (handler != null) {
                handler.accept(player);
            }
        }
    }

    /** Ein NPC. */
    private final class Spawned implements Npc {

        private final Mannequin entity;
        private final NpcSpec spec;

        Spawned(Mannequin entity, NpcSpec spec) {
            this.entity = entity;
            this.spec = spec;
        }

        @Override
        public Entity entity() {
            return entity;
        }

        @Override
        public void teleport(Location location) {
            entity.teleport(location);
        }

        @Override
        public void remove() {
            npcs.remove(entity.getUniqueId());
            entity.remove();
        }
    }
}
