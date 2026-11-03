package net.retromc.game.framework;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.retromc.game.framework.kill.KillOptions;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jspecify.annotations.Nullable;

/**
 * Bukkit-Ereignisse des Rahmens: Join/Quit, Schutz in Wartelobby und für Zuschauer (Spec 03 §6.8 Nr. 2),
 * Treffer für den Kill-Feed, Map-Grenze der Zuschauer und der Standard-Tod (Ausscheiden), wenn der Modus den Tod
 * nicht selbst abfängt.
 */
final class GameListener implements Listener {

    /** Fallhöhe unter dem Wartelobby-Spawn, ab der zurückteleportiert wird. */
    static final double WAITING_FALL = 30;

    private final Game game;

    GameListener(Game game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        game.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        game.handleQuit(event.getPlayer());
    }

    /** Wartelobby, Zuschauer und Siegerphase: kein Schaden. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (game.lifecycle().waiting()) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && game.maps().ready()) {
                player.teleport(game.maps().waitingSpawn());
            }
            return;
        }
        if (game.state() == GameState.ENDING || game.spectators().isSpectator(player)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && game.maps().ready()) {
                player.teleport(game.maps().spectatorSpawn());
            }
        }
    }

    /** Zuschauer greifen nicht an; Treffer unter Spielern merken (Killer ≤ 10 s). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (attacker != null && game.spectators().isSpectator(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (attacker != null && event.getEntity() instanceof Player victim && game.state() == GameState.INGAME) {
            game.damage().hit(victim.getUniqueId(), attacker.getUniqueId(), Instant.now());
        }
    }

    /** Angreifer (auch Schütze eines Projektils). */
    static @Nullable Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && (game.lifecycle().waiting()
                || game.spectators().isSpectator(player) || game.state() == GameState.ENDING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && blocked(player)) {
            event.setCancelled(true);
        }
    }

    /** Zuschauer und Wartende benutzen keine Blöcke (Truhen, Türen …); Hotbar-Items bleiben nutzbar. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (blocked(event.getPlayer())) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (game.spectators().isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Zuschauer bleiben in den Map-Grenzen (Spec 03 §6.8 Nr. 5); Wartende fallen nicht aus der Lobby. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !game.maps().ready()) {
            return;
        }
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (game.lifecycle().waiting()) {
            Location spawn = game.maps().waitingSpawn();
            if (to.getWorld() == spawn.getWorld() && to.getY() < spawn.getY() - WAITING_FALL) {
                player.teleport(spawn);
            }
            return;
        }
        if (game.spectators().isSpectator(player) && !game.maps().inBounds(to)) {
            player.teleport(game.maps().spectatorSpawn());
        }
    }

    /** Standard-Tod: Kill-Feed und Ausscheiden (Modi mit Respawn fangen den tödlichen Schaden selbst ab). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        Player victim = event.getPlayer();
        if (game.state() != GameState.INGAME || !game.alive(victim.getUniqueId())) {
            return;
        }
        UUID killerId = game.damage().lastAttacker(victim.getUniqueId(), Instant.now()).orElse(null);
        Player killer = killerId == null ? victim.getKiller() : org.bukkit.Bukkit.getPlayer(killerId);
        game.player(victim.getUniqueId()).ifPresent(GamePlayer::addDeath);
        if (killer != null) {
            game.player(killer.getUniqueId()).ifPresent(GamePlayer::addKill);
        }
        int remaining = game.alivePlayers().size() - 1;
        KillOptions options = game.definition().teamMode() == TeamMode.SOLO ? KillOptions.PLAIN
                .withRemaining(remaining) : KillOptions.PLAIN;
        game.killFeed().kill(victim, killer, options);
        org.bukkit.Bukkit.getScheduler().runTask(game.plugin(), () -> game.eliminate(victim, killer));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (game.state() != GameState.WAITING && game.maps().ready()) {
            event.setRespawnLocation(game.maps().spectatorSpawn());
        } else if (game.maps().ready()) {
            event.setRespawnLocation(game.maps().waitingSpawn());
        }
    }

    private boolean blocked(Player player) {
        return game.lifecycle().waiting() || game.state() == GameState.ENDING
                || game.spectators().isSpectator(player);
    }
}
