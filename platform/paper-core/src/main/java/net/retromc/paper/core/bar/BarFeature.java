package net.retromc.paper.core.bar;

import io.lettuce.core.api.StatefulRedisConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.core.i18n.UiEra;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.config.EnvKeys;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Bossbar- und Actionbar-Dienst (Spec 02 §4.5, §4.6): Bossbar des wichtigsten Kanals, Actionbar nach Priorität mit
 * erneutem Senden alle 40 Ticks und auf Lobbys der Newsticker {@code §e+ {color}§l{TITLE}: §f{url}}, der alle 8 s
 * durch {@code ui:news} rotiert (Liste vom Lobby-Modul, jede Minute neu gelesen). Lobbys in der Ära {@code LEGACY}
 * ({@link EnvKeys#UI_ERA}) zeigen die News stattdessen als Bossbar {@code §e§l{TITLE}} im Kanal {@code news}
 * (Spec 02 §4.5, Spec 03 §4.4 „Lobby Ära 18“).
 */
public final class BarFeature implements CoreFeature<PaperCore>, Listener, BossbarService, ActionbarService {

    /** Kanal des Newstickers. */
    static final String TICKER = "ticker";
    private static final int STEP_TICKS = 10;
    private static final int NEWS_RELOAD_TICKS = 1_200;

    /** Bossbars eines Spielers. */
    private static final class Bars {
        final Map<String, BossBarSpec> specs = new ConcurrentHashMap<>();
        @Nullable BossBar shown;
    }

    /** Actionbar eines Spielers. */
    private static final class Bar {
        final ActionbarQueue queue = new ActionbarQueue();
        @Nullable Component last;
        long sentAt = Long.MIN_VALUE;
    }

    private final Map<UUID, Bars> bossbars = new ConcurrentHashMap<>();
    private final Map<UUID, Bar> actionbars = new ConcurrentHashMap<>();
    private final List<Registration> registrations = new ArrayList<>();
    private volatile List<NewsItem> explicitNews = List.of();
    private volatile List<NewsItem> storedNews = List.of();
    private @Nullable PaperCore core;
    private @Nullable BukkitTask task;
    private @Nullable StatefulRedisConnection<String, String> valkey;
    private UiEra era = UiEra.MODERN;
    private long tick;
    private int newsIndex;

    @Override
    public String name() {
        return "Bossbar/Actionbar";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        EnvReader env = EnvReader.system();
        era = env.choice(EnvKeys.UI_ERA, UiEra.class).orElse(UiEra.MODERN);
        env.problems().forEach(problem -> context.log().warn("{}", problem));
        if (context.config().category() == ServerCategory.LOBBY) {
            valkey = context.services().get(ValkeyConnector.class).connect();
        }
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registrations.add(context.services().registerDefault(BossbarService.class, this));
        registrations.add(context.services().registerDefault(ActionbarService.class, this));
        task = Bukkit.getScheduler().runTaskTimer(context.plugin(), this::step, STEP_TICKS, STEP_TICKS);
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        BukkitTask running = task;
        if (running != null) {
            running.cancel();
        }
        task = null;
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        registrations.clear();
        for (Map.Entry<UUID, Bars> entry : bossbars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            BossBar shown = entry.getValue().shown;
            if (player != null && shown != null) {
                player.hideBossBar(shown);
            }
        }
        bossbars.clear();
        actionbars.clear();
        StatefulRedisConnection<String, String> connection = valkey;
        if (connection != null) {
            connection.close();
        }
        valkey = null;
        core = null;
    }

    // ------------------------------------------------------------------------------------------ Bossbar

    @Override
    public void show(Player player, String channel, BossBarSpec spec) {
        bossbars.computeIfAbsent(player.getUniqueId(), uuid -> new Bars()).specs.put(channel, spec);
        updateBossbar(player);
    }

    @Override
    public void hide(Player player, String channel) {
        Bars bars = bossbars.get(player.getUniqueId());
        if (bars != null) {
            bars.specs.remove(channel);
            updateBossbar(player);
        }
    }

    private void updateBossbar(Player player) {
        Bars bars = bossbars.get(player.getUniqueId());
        if (bars == null) {
            return;
        }
        Optional<BossBarSpec> active = BossbarService.active(bars.specs.keySet()).map(bars.specs::get);
        BossBar shown = bars.shown;
        if (active.isEmpty()) {
            if (shown != null) {
                player.hideBossBar(shown);
            }
            bars.shown = null;
            return;
        }
        BossBarSpec spec = active.get();
        if (shown == null) {
            shown = BossBar.bossBar(spec.title(), spec.progress(), spec.color(), spec.overlay());
            bars.shown = shown;
            player.showBossBar(shown);
        } else {
            shown.name(spec.title());
            shown.progress(spec.progress());
            shown.color(spec.color());
            shown.overlay(spec.overlay());
        }
    }

    // ------------------------------------------------------------------------------------------ Actionbar

    @Override
    public void set(Player player, String channel, Priority priority, Component text, int durationTicks) {
        Bar bar = actionbars.computeIfAbsent(player.getUniqueId(), uuid -> new Bar());
        synchronized (bar) {
            bar.queue.set(channel, priority, text, tick, durationTicks);
        }
        send(player, bar, true);
    }

    @Override
    public void clear(Player player, String channel) {
        Bar bar = actionbars.get(player.getUniqueId());
        if (bar != null) {
            synchronized (bar) {
                bar.queue.clear(channel);
            }
            send(player, bar, true);
        }
    }

    @Override
    public void ticker(List<NewsItem> items) {
        explicitNews = List.copyOf(items);
    }

    /** Sendet den aktuellen Text, wenn er sich geändert hat oder 40 Ticks vergangen sind. */
    private void send(Player player, Bar bar, boolean force) {
        Optional<Component> current;
        synchronized (bar) {
            current = bar.queue.current(tick);
        }
        if (current.isEmpty()) {
            if (bar.last != null) {
                player.sendActionBar(Component.empty());
                bar.last = null;
            }
            return;
        }
        boolean changed = !current.get().equals(bar.last);
        if (changed || force || tick - bar.sentAt >= RESEND_TICKS) {
            player.sendActionBar(current.get());
            bar.last = current.get();
            bar.sentAt = tick;
        }
    }

    private void step() {
        tick += STEP_TICKS;
        PaperCore context = core;
        if (context == null) {
            return;
        }
        if (valkey != null && tick % NEWS_RELOAD_TICKS == STEP_TICKS) {
            reloadNews(context);
        }
        if (tick % NewsItem.ROTATE_TICKS == 0) {
            rotateNews(context);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Bar bar = actionbars.get(player.getUniqueId());
            if (bar != null) {
                send(player, bar, false);
            }
        }
    }

    private void reloadNews(PaperCore context) {
        StatefulRedisConnection<String, String> connection = valkey;
        if (connection == null) {
            return;
        }
        connection.async().lrange(ValkeyKeys.uiNews(), 0, -1).toCompletableFuture()
                .thenAccept(entries -> storedNews = NewsItem.parseAll(entries))
                .exceptionally(error -> {
                    context.log().warn("Newsticker ui:news nicht gelesen", error);
                    return null;
                });
    }

    private void rotateNews(PaperCore context) {
        List<NewsItem> news = explicitNews.isEmpty() ? storedNews : explicitNews;
        if (news.isEmpty() || context.config().category() != ServerCategory.LOBBY) {
            return;
        }
        NewsItem item = news.get(Math.floorMod(newsIndex++, news.size()));
        I18n i18n = context.services().get(I18n.class);
        boolean bossbar = newsInBossbar(era);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (bossbar) {
                show(player, NEWS, BossBarSpec.news(i18n, i18n.locale(player.getUniqueId()), item.title()));
            } else {
                set(player, TICKER, Priority.TICKER, item.render(i18n, i18n.locale(player.getUniqueId())), 0);
            }
        }
    }

    /** Newsticker als Bossbar ({@code LEGACY}, Spec 03 §4.4) statt in der Actionbar ({@code MODERN}). */
    public static boolean newsInBossbar(UiEra era) {
        return era == UiEra.LEGACY;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossbars.remove(event.getPlayer().getUniqueId());
        actionbars.remove(event.getPlayer().getUniqueId());
    }
}
