package net.retromc.skywars.kit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.paper.core.bar.ActionbarService;
import net.retromc.skywars.SkyWarsMode;
import net.retromc.skywars.SwPlayer;
import net.retromc.skywars.event.KitAbilityUseEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wolf;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * Kit-Fähigkeiten (Spec 04 §6.2.5, §4.6): Rechtsklick auf das Fähigkeits-Item löst aus, in der Schutzzeit gesperrt;
 * Nutzungen/Abklingzeit über {@link AbilityTracker}, Actionbar {@code sw.ability.*}. Passiv: Engel (Goldener Apfel je
 * Kill), Jäger (+1 Herz je Kill, max. +5), Goldsucher (doppelte Erze), Stampfer (Schockwelle nach Sturz ≥ 4 Blöcke).
 * Beschworene Helfer (Plagegeist, Bienen, Wölfe) greifen nur Gegner an; ihre Treffer zählen für den Besitzer.
 */
public final class Abilities implements Listener {

    /** Actionbar-Kanal der Kit-Fähigkeit. */
    public static final String CHANNEL = "kit";
    /** Höchstzahl Extraherzen des Jägers. */
    static final int HUNTER_MAX = 5;
    /** Plagegeist: Angriff höchstens alle 10 s, halber Schaden, Lebensdauer 30 s (AN). */
    static final long VEX_ATTACK_MILLIS = 10_000;
    static final int VEX_LIFETIME_TICKS = 30 * 20;
    static final int BEE_LIFETIME_TICKS = 20 * 20;
    static final double STOMP_MIN_FALL = 4.0;

    private final SkyWarsMode mode;
    /** Helfer → Besitzer. */
    private final Map<UUID, UUID> summoned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> vexHits = new ConcurrentHashMap<>();
    private final List<Block> clouds = new ArrayList<>();

    public Abilities(SkyWarsMode mode) {
        this.mode = mode;
    }

    // ------------------------------------------------------------------------------------------ Auslösen

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        Optional<String> kitId = KitItems.abilityKit(event.getItem());
        if (kitId.isEmpty()) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        SwPlayer sw = mode.sw(player.getUniqueId());
        KitDef kit = sw.kit();
        if (!mode.playing(player) || kit == null || !kit.id().equals(kitId.get()) || kit.ability() == null) {
            return;
        }
        trigger(player, sw, kit, kit.ability());
    }

    /** Versucht die Fähigkeit auszulösen; {@code true} = ausgelöst. */
    boolean trigger(Player player, SwPlayer sw, KitDef kit, KitDef.Ability ability) {
        AbilityTracker tracker = sw.ability();
        if (tracker == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        AbilityTracker.Use check = tracker.check(now, mode.protection());
        switch (check.result()) {
            case PROTECTION -> {
                actionbar(player, "sw.ability.protection", 40, ActionbarService.Priority.TEMPORARY);
                return false;
            }
            case COOLDOWN -> {
                cooldown(player, check.cooldown());
                return false;
            }
            case EMPTY -> {
                actionbar(player, "sw.ability.empty", 40, ActionbarService.Priority.TEMPORARY);
                return false;
            }
            default -> {
            }
        }
        int left = check.usesLeft() < 0 ? -1 : check.usesLeft() - 1;
        KitAbilityUseEvent event = new KitAbilityUseEvent(player, kit.id(), left);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        AbilityTracker.Use used = tracker.use(now, false);
        perform(player, ability.type());
        if (ability.consume()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (KitItems.abilityKit(hand).isPresent()) {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
        if (used.usesLeft() >= 0) {
            actionbar(player, mode.texts().count("sw.ability.uses", used.usesLeft()), 40,
                    ActionbarService.Priority.TEMPORARY, Placeholder.unparsed("n",
                            Integer.toString(used.usesLeft())));
        }
        return true;
    }

    private void perform(Player player, AbilityType type) {
        switch (type) {
            case DEVIL -> devil(player);
            case PHANTOM -> {
                Vector direction = player.getLocation().getDirection().normalize().multiply(1.6);
                direction.setY(Math.max(direction.getY(), 0.5));
                player.setVelocity(direction);
                player.setFallDistance(0);
                sound(player, Sound.ENTITY_PHANTOM_FLAP);
            }
            case GOAT -> {
                Vector look = player.getLocation().getDirection().setY(0).normalize();
                for (Player target : mode.enemies(player, 4)) {
                    Vector to = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() > 0 && to.normalize().dot(look) > 0.5) {
                        push(player, target, 2.2, 0.6);
                        target.damage(1.0, player);
                    }
                }
                sound(player, Sound.ENTITY_GOAT_RAM_IMPACT);
            }
            case NINJA -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 5 * 20, 0, false, false));
                sound(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE);
            }
            case POSEIDON -> {
                for (Player target : mode.enemies(player, 6)) {
                    push(player, target, 1.5, 0.4);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                }
                player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation(), 200, 3, 0.5, 3);
                sound(player, Sound.ENTITY_GENERIC_SPLASH);
            }
            case CLOUD -> cloud(player);
            case DOG_WHISPERER -> player.getWorld().spawn(player.getLocation(), Wolf.class, wolf -> {
                wolf.setTamed(true);
                wolf.setOwner(player);
                wolf.setPersistent(false);
                summoned.put(wolf.getUniqueId(), player.getUniqueId());
            });
            case AIRBENDER -> {
                for (Player target : mode.enemies(player, 5)) {
                    push(player, target, 2.0, 0.8);
                }
                player.getWorld().spawnParticle(Particle.GUST, player.getLocation(), 10, 2, 0.5, 2);
                sound(player, Sound.ENTITY_BREEZE_WIND_BURST);
            }
            case NIGHT_MAGE -> {
                for (Player target : mode.enemies(player, 6)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3 * 20, 0));
                }
                sound(player, Sound.ENTITY_WITHER_AMBIENT);
            }
            case BEES -> bees(player);
            default -> {
            }
        }
    }

    private void devil(Player player) {
        Vex vex = player.getWorld().spawn(player.getEyeLocation().add(0, 1, 0), Vex.class, spawned -> {
            spawned.setLimitedLifetime(true);
            spawned.setLimitedLifetimeTicks(VEX_LIFETIME_TICKS);
            spawned.setPersistent(false);
        });
        summoned.put(vex.getUniqueId(), player.getUniqueId());
        nearestEnemy(player, 16).ifPresent(vex::setTarget);
        sound(player, Sound.ENTITY_EVOKER_PREPARE_SUMMON);
    }

    private void bees(Player player) {
        for (int i = 0; i < 3; i++) {
            Bee bee = player.getWorld().spawn(player.getEyeLocation(), Bee.class, spawned -> {
                spawned.setAnger(BEE_LIFETIME_TICKS);
                spawned.setPersistent(false);
            });
            summoned.put(bee.getUniqueId(), player.getUniqueId());
            nearestEnemy(player, 16).ifPresent(bee::setTarget);
            Bukkit.getScheduler().runTaskLater(mode.plugin(), () -> {
                summoned.remove(bee.getUniqueId());
                bee.remove();
            }, BEE_LIFETIME_TICKS);
        }
        sound(player, Sound.ENTITY_BEE_LOOP_AGGRESSIVE);
    }

    private void cloud(Player player) {
        Location feet = player.getLocation().subtract(0, 1, 0);
        List<Block> placed = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = feet.clone().add(dx, 0, dz).getBlock();
                if (block.getType().isAir()) {
                    block.setType(Material.WHITE_WOOL, false);
                    placed.add(block);
                }
            }
        }
        player.setFallDistance(0);
        clouds.addAll(placed);
        sound(player, Sound.BLOCK_WOOL_PLACE);
        Bukkit.getScheduler().runTaskLater(mode.plugin(), () -> {
            for (Block block : placed) {
                if (block.getType() == Material.WHITE_WOOL) {
                    block.setType(Material.AIR, false);
                }
            }
            clouds.removeAll(placed);
        }, 5 * 20L);
    }

    private void push(Player source, Player target, double strength, double up) {
        Vector away = target.getLocation().toVector().subtract(source.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = source.getLocation().getDirection().setY(0);
        }
        target.setVelocity(away.normalize().multiply(strength).setY(up));
        mode.game().damage().hit(target.getUniqueId(), source.getUniqueId(), Instant.now());
    }

    private Optional<Player> nearestEnemy(Player player, double radius) {
        Player best = null;
        double distance = Double.MAX_VALUE;
        for (Player enemy : mode.enemies(player, radius)) {
            double value = enemy.getLocation().distanceSquared(player.getLocation());
            if (value < distance) {
                distance = value;
                best = enemy;
            }
        }
        return Optional.ofNullable(best);
    }

    private static void sound(Player player, Sound sound) {
        player.getWorld().playSound(player.getLocation(), sound, 1f, 1f);
    }

    // ------------------------------------------------------------------------------------------ Actionbar

    /** Jede Sekunde: Abklingzeit anzeigen bzw. „Ab jetzt nutzbar“, sobald sie abgelaufen ist. */
    public void second() {
        long now = System.currentTimeMillis();
        for (Player player : mode.game().alivePlayers()) {
            SwPlayer sw = mode.sw(player.getUniqueId());
            AbilityTracker tracker = sw.ability();
            KitDef kit = sw.kit();
            if (tracker == null || kit == null || mode.protection()) {
                continue;
            }
            int rest = tracker.cooldown(now);
            if (rest > 0) {
                cooldown(player, rest);
            } else if (tracker.becameReady(now)) {
                ready(player, tracker);
            }
        }
    }

    /** Ende der Schutzzeit: allen mit Fähigkeit „Ab jetzt nutzbar“ zeigen. */
    public void announceReady() {
        for (Player player : mode.game().alivePlayers()) {
            AbilityTracker tracker = mode.sw(player.getUniqueId()).ability();
            if (tracker != null) {
                ready(player, tracker);
            }
        }
    }

    private void ready(Player player, AbilityTracker tracker) {
        int uses = tracker.usesLeft();
        if (uses < 0) {
            actionbar(player, "sw.ability.ready.unlimited", 60, ActionbarService.Priority.TEMPORARY);
        } else {
            actionbar(player, mode.texts().count("sw.ability.ready", uses), 60, ActionbarService.Priority.TEMPORARY,
                    Placeholder.unparsed("n", Integer.toString(uses)));
        }
    }

    private void cooldown(Player player, int seconds) {
        actionbar(player, mode.texts().count("sw.ability.cooldown", seconds), 25, ActionbarService.Priority.COOLDOWN,
                Placeholder.unparsed("s", Integer.toString(seconds)));
    }

    private void actionbar(Player player, String key, int ticks, ActionbarService.Priority priority,
            net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        mode.game().services().find(ActionbarService.class).ifPresent(bar -> bar.set(player, CHANNEL, priority,
                mode.texts().i18n().render(mode.texts().locale(player), key, args), ticks));
    }

    // ------------------------------------------------------------------------------------------ Passiv

    /** Kill des Spielers (Engel, Jäger). */
    public void onKill(Player killer) {
        SwPlayer sw = mode.sw(killer.getUniqueId());
        KitDef kit = sw.kit();
        KitDef.Ability ability = kit == null ? null : kit.ability();
        if (ability == null) {
            return;
        }
        if (ability.type() == AbilityType.ANGEL) {
            killer.getInventory().addItem(ItemStack.of(Material.GOLDEN_APPLE)).values()
                    .forEach(rest -> killer.getWorld().dropItem(killer.getLocation(), rest));
        } else if (ability.type() == AbilityType.HUNTER && sw.addHeart(HUNTER_MAX)) {
            AttributeInstance health = killer.getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(health.getBaseValue() + 2.0);
                killer.setHealth(Math.min(health.getValue(), killer.getHealth() + 2.0));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        KitDef kit = mode.sw(player.getUniqueId()).kit();
        if (kit == null || kit.ability() == null || kit.ability().type() != AbilityType.GOLD_DIGGER
                || !mode.playing(player) || !event.getBlock().getType().name().endsWith("_ORE")) {
            return;
        }
        Location at = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player)) {
            event.getBlock().getWorld().dropItemNaturally(at, drop);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)
                || !mode.playing(player) || player.getFallDistance() < STOMP_MIN_FALL) {
            return;
        }
        SwPlayer sw = mode.sw(player.getUniqueId());
        KitDef kit = sw.kit();
        if (kit == null || kit.ability() == null || kit.ability().type() != AbilityType.STOMPER) {
            return;
        }
        if (!trigger(player, sw, kit, kit.ability())) {
            return;
        }
        for (Player target : mode.enemies(player, 3)) {
            target.damage(3.0, player);
        }
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 3, 1, 0.2, 1);
        sound(player, Sound.ENTITY_GENERIC_EXPLODE);
    }

    // ------------------------------------------------------------------------------------------ Helfer

    /** Besitzer eines beschworenen Helfers (für Treffer-Zuordnung) oder {@code null}. */
    public @Nullable UUID owner(Entity entity) {
        UUID owner = summoned.get(entity.getUniqueId());
        if (owner == null && entity instanceof Wolf wolf && wolf.isTamed()) {
            return wolf.getOwnerUniqueId();
        }
        return owner;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        UUID owner = owner(event.getEntity());
        if (owner == null || !(event.getTarget() instanceof Player target)) {
            return;
        }
        Player master = Bukkit.getPlayer(owner);
        if (master == null || target.equals(master) || mode.teammates(master, target)) {
            event.setCancelled(true);
            if (master != null && event.getEntity() instanceof Mob mob) {
                nearestEnemy(master, 16).ifPresent(mob::setTarget);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHelperHit(EntityDamageByEntityEvent event) {
        UUID owner = owner(event.getDamager());
        if (owner == null || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player master = Bukkit.getPlayer(owner);
        if (master == null || victim.equals(master) || mode.teammates(master, victim) || mode.protection()) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Vex) {
            long now = System.currentTimeMillis();
            Long last = vexHits.get(event.getDamager().getUniqueId());
            if (last != null && now - last < VEX_ATTACK_MILLIS) {
                event.setCancelled(true);
                return;
            }
            vexHits.put(event.getDamager().getUniqueId(), now);
            event.setDamage(event.getDamage() / 2.0);
        }
        mode.game().damage().hit(victim.getUniqueId(), owner, Instant.now());
    }

    /** Entfernt alle Helfer und Wolkenblöcke (Rundenende). */
    public void clear() {
        for (UUID id : summoned.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living) {
                living.remove();
            }
        }
        summoned.clear();
        vexHits.clear();
        for (Block block : clouds) {
            if (block.getType() == Material.WHITE_WOOL) {
                block.setType(Material.AIR, false);
            }
        }
        clouds.clear();
    }
}
