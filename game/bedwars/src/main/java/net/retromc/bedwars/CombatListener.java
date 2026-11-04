package net.retromc.bedwars;

import java.time.Instant;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;

/**
 * Kampf und Tod in BedWars (Spec 04 §6.1.4, §6.1.8): tödlicher Schaden und Void werden abgefangen (kein
 * Vanilla-Tod), Rettungskapsel, kein Schaden unter Teamkameraden, gesperrte Zweithand (Slot 40).
 */
final class CombatListener implements Listener {

    /** Inventar-Slot der Zweithand. */
    static final int OFFHAND_SLOT = 40;

    private final BedWarsMode mode;

    CombatListener(BedWarsMode mode) {
        this.mode = mode;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || mode.game().state() != GameState.INGAME
                || !mode.playing(victim)) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = attacker(byEntity);
            if (attacker != null && !attacker.equals(victim) && teammates(attacker, victim)) {
                event.setCancelled(true);
                return;
            }
            if (attacker != null && !attacker.equals(victim)) {
                mode.game().damage().hit(victim.getUniqueId(), attacker.getUniqueId(), Instant.now());
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            mode.die(victim, true);
            return;
        }
        if (victim.getHealth() - event.getFinalDamage() > 0) {
            return;
        }
        event.setCancelled(true);
        if (SpecialItems.capsule(mode, victim)) {
            return;
        }
        mode.die(victim, false);
    }

    /** Sicherheitsnetz: ein trotzdem eintretender Tod (z. B. {@code /kill}) läuft über denselben Ablauf. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (mode.game().state() == GameState.INGAME && mode.playing(victim)) {
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

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == OFFHAND_SLOT) {
            event.setCancelled(true);
        }
    }

    private boolean teammates(Player a, Player b) {
        GameTeam team = mode.game().teams().teamOf(a.getUniqueId());
        return team != null && team.contains(b.getUniqueId()) && mode.game().variant().teamSize() > 1;
    }

    static @Nullable Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
