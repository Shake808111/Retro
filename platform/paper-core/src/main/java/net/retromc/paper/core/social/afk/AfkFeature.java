package net.retromc.paper.core.social.afk;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.retromc.core.api.social.PresenceService;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * AFK-Erkennung (Spec 01 §6.12, O13): keine Bewegung/Drehung, kein Chat, keine Interaktion und kein Befehl für
 * 300 s → {@code presence.afk=1}, {@code afk.on}; erste Aktivität → {@code afk.off}. {@code AFK_CHANGED} sendet der
 * {@link PresenceService}; die Party-Meldungen verschickt der Proxy (A16).
 */
public final class AfkFeature implements CoreFeature<PaperCore>, Listener {

    /** Zeit ohne Aktivität bis AFK. */
    public static final Duration AFK_AFTER = Duration.ofSeconds(300);
    /** Prüfintervall in Ticks (5 s). */
    static final long CHECK_TICKS = 100;

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<UUID> afk = ConcurrentHashMap.newKeySet();
    private @Nullable PaperCore core;
    private @Nullable BukkitTask task;

    @Override
    public String name() {
        return "AFK";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        task = context.plugin().getServer().getScheduler().runTaskTimer(context.plugin(), this::check, CHECK_TICKS,
                CHECK_TICKS);
    }

    @Override
    public void disable(PaperCore context) {
        BukkitTask current = task;
        if (current != null) {
            current.cancel();
        }
        HandlerList.unregisterAll(this);
        lastActivity.clear();
        afk.clear();
        task = null;
        core = null;
    }

    /** Ob der Spieler nach {@code idleMillis} ohne Aktivität als AFK gilt. */
    public static boolean isIdle(long idleMillis) {
        return idleMillis >= AFK_AFTER.toMillis();
    }

    private void check() {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : context.plugin().getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            long last = lastActivity.computeIfAbsent(uuid, ignored -> now);
            if (!afk.contains(uuid) && isIdle(now - last)) {
                afk.add(uuid);
                change(context, player, true);
            }
        }
    }

    private void active(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        PaperCore context = core;
        if (context != null && afk.remove(uuid)) {
            context.mainThread().execute(() -> change(context, player, false));
        }
    }

    private static void change(PaperCore context, Player player, boolean nowAfk) {
        I18n i18n = context.services().get(I18n.class);
        player.sendMessage(i18n.render(i18n.locale(player.getUniqueId()), nowAfk ? "afk.on" : "afk.off"));
        context.services().find(PresenceService.class).ifPresent(presence -> presence.setAfk(player.getUniqueId(),
                nowAfk).exceptionally(error -> {
                    context.log().warn("AFK-Status nicht gespeichert", error);
                    return null;
                }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActivity.remove(event.getPlayer().getUniqueId());
        afk.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() || event.hasChangedOrientation()) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        active(event.getPlayer());
    }
}
