package net.retromc.skywars;

import java.time.Instant;
import java.util.UUID;
import net.retromc.game.framework.GameState;
import net.retromc.skywars.challenge.ChallengeTracker;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jspecify.annotations.Nullable;

/**
 * Kampf und Tod in SkyWars (Spec 04 §6.2.1–§6.2.2): im Käfig kein Schaden außer Void, in der Schutzzeit kein
 * PvP-Schaden, kein Schaden unter Teamkameraden; Void-Tod unter {@code voidY}; tödlicher Schaden wird abgefangen
 * (kein Vanilla-Tod) – außer ein Totem der Unsterblichkeit (Engel-Kit) greift. Killer = letzter Treffer ≤ 10 s.
 */
final class CombatListener implements Listener {

    private final SkyWarsMode mode;

    CombatListener(SkyWarsMode mode) {
        this.mode = mode;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || mode.game().state() != GameState.INGAME) {
            return;
        }
        if (!mode.playing(victim)) {
            event.setCancelled(true);
            return;
        }
        boolean voidDamage = event.getCause() == EntityDamageEvent.DamageCause.VOID;
        if (mode.caged() && !voidDamage) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            UUID attacker = attacker(byEntity);
            if (attacker != null && !attacker.equals(victim.getUniqueId())) {
                Player source = org.bukkit.Bukkit.getPlayer(attacker);
                if (mode.protection() || (source != null && mode.teammates(source, victim))) {
                    event.setCancelled(true);
                    return;
                }
                mode.game().damage().hit(victim.getUniqueId(), attacker, Instant.now());
                if (source != null && byEntity.getDamager().equals(source)
                        && source.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")) {
                    ChallengeTracker tracker = mode.sw(attacker).challenge();
                    if (tracker != null) {
                        mode.challenges().report(source, tracker.swordUsed());
                    }
                }
            }
        }
        if (voidDamage) {
            event.setCancelled(true);
            mode.die(victim, true);
            return;
        }
        if (victim.getHealth() - event.getFinalDamage() > 0 || holdsTotem(victim)) {
            return;
        }
        event.setCancelled(true);
        mode.die(victim, false);
    }

    /** Sicherheitsnetz: ein trotzdem eintretender Tod (z. B. {@code /kill}) läuft über denselben Ablauf. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (mode.playing(victim)) {
            event.setCancelled(true);
            mode.die(victim, false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (mode.game().state() != GameState.INGAME || event.getTo().getY() >= mode.map().voidY()) {
            return;
        }
        Player player = event.getPlayer();
        if (mode.playing(player)) {
            mode.die(player, true);
        }
    }

    private static boolean holdsTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    /** Angreifer: Spieler, Schütze eines Projektils oder Besitzer eines Kit-Helfers. */
    private @Nullable UUID attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player.getUniqueId();
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter.getUniqueId();
        }
        return mode.abilities().owner(event.getDamager());
    }
}
