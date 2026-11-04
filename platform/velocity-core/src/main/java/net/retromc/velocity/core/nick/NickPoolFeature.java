package net.retromc.velocity.core.nick;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.social.NickPoolService;
import net.retromc.core.api.social.NickPoolService.Fill;
import net.retromc.core.api.social.NickPoolService.State;
import net.retromc.core.api.social.NickPoolService.Stats;
import net.retromc.core.api.social.NickService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.data.nick.JdkHttpTransport;
import net.retromc.core.data.nick.NickPoolRepository;
import net.retromc.core.data.nick.NickPoolSettings;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.social.NickPoolConfig;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Nick-Pool auf dem Proxy (Spec 01 §6.11 Nr. 3 und 7): Standard-{@link NickPoolService} ({@link NickPoolRepository}
 * mit Mojang/MineSkin über HTTP, Einstellungen aus {@link NickPoolConfig}), Admin-Befehl
 * {@code /nickpool fill <anzahl>|list [seite]|disable <name>|stats} mit Recht {@code retromc.nick.pool} und die
 * Monitoring-Warnung: alle 10 Minuten Log-Warnung, wenn weniger als {@link NickService#POOL_WARN_FREE} Nicks frei
 * sind, beim Unterschreiten zusätzlich eine Meldung an Admins online.
 */
public final class NickPoolFeature implements CoreFeature<VelocityCore> {

    public static final CommandName NICKPOOL = CommandName.of("nickpool");
    public static final List<CommandName> COMMANDS = List.of(NICKPOOL);
    /** Pflege des Pools (Admin). */
    public static final String PERMISSION = Social.permission("nick.pool");
    /** Namen je Seite in {@code /nickpool list}. */
    static final int PAGE_SIZE = 15;
    static final long MONITOR_MINUTES = 10;

    private final List<Registration> registrations = new ArrayList<>();
    private @Nullable Social social;
    private @Nullable ExecutorService executor;
    private @Nullable StatefulRedisConnection<String, String> connection;
    private @Nullable ScheduledTask monitor;
    private volatile boolean lowNotified;

    @Override
    public String name() {
        return "Nick-Pool";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        EnvReader env = EnvReader.system();
        NickPoolSettings settings = NickPoolConfig.read(env);
        env.problems().forEach(problem -> core.log().warn("Nick-Pool: {}", problem));
        ExecutorService fills = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon()
                .name(BuildInfo.BRAND_ID + "-nickpool").factory());
        executor = fills;
        StatefulRedisConnection<String, String> valkey = core.services().get(ValkeyConnector.class).connect();
        connection = valkey;
        NickPoolRepository pool = new NickPoolRepository(core.services().get(AsyncDatabase.class), valkey.async(),
                name -> core.proxy().getPlayer(name).isPresent(), settings, new JdkHttpTransport(), Thread::sleep,
                Clock.systemUTC(), RandomGenerator.getDefault(), fills);
        registrations.add(core.services().registerDefault(NickPoolService.class, pool));
        core.log().info("Nick-Pool: {}", settings);

        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        var nickpool = NICKPOOL.builder(manager).permission(PERMISSION);
        manager.command(nickpool.handler(ctx -> stats(ctx.sender())));
        manager.command(nickpool.literal("stats").handler(ctx -> stats(ctx.sender())));
        manager.command(nickpool.literal("fill").required("count", integerParser(1, NickPoolService.MAX_FILL))
                .handler(ctx -> fill(ctx.sender(), ctx.<Integer>get("count"))));
        manager.command(nickpool.literal("list").optional("page", integerParser(1))
                .handler(ctx -> list(ctx.sender(), ctx.<Integer>optional("page").orElse(1))));
        manager.command(nickpool.literal("disable").required("name", stringParser())
                .handler(ctx -> disable(ctx.sender(), ctx.get("name"))));
        monitor = core.proxy().getScheduler().buildTask(core.plugin(), this::monitor)
                .delay(1, TimeUnit.MINUTES).repeat(MONITOR_MINUTES, TimeUnit.MINUTES).schedule();
    }

    @Override
    public void disable(VelocityCore core) {
        ScheduledTask task = monitor;
        if (task != null) {
            task.cancel();
        }
        monitor = null;
        registrations.forEach(Registration::close);
        registrations.clear();
        ExecutorService fills = executor;
        if (fills != null) {
            fills.shutdownNow();
        }
        executor = null;
        StatefulRedisConnection<String, String> valkey = connection;
        if (valkey != null) {
            valkey.close();
        }
        connection = null;
        social = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Nick-Pool ist nicht aktiv");
        }
        return current;
    }

    private NickPoolService pool() {
        return social().get(NickPoolService.class);
    }

    void fill(CommandSource sender, int count) {
        Social helper = social();
        helper.send(sender, "nickpool.fill.started", Social.unparsed("n", count));
        helper.then(sender, pool().fill(count), fill -> {
            TagResolver numbers = TagResolver.resolver(Social.unparsed("n", fill.requested()),
                    Social.unparsed("added", fill.added()), Social.unparsed("skins", fill.skins()),
                    Social.unparsed("new", fill.skinsAdded()), Social.unparsed("known", fill.known()),
                    Social.unparsed("filtered", fill.filtered()), Social.unparsed("taken", fill.taken()));
            for (String key : fillKeys(fill)) {
                helper.send(sender, key, numbers);
            }
            if (fill.added() > 0) {
                helper.then(sender, pool().stats(), stats -> warnIfLow(helper, sender, stats));
            }
        });
    }

    /** Antworten auf einen Füllvorgang in Reihenfolge. */
    static List<String> fillKeys(Fill fill) {
        return switch (fill.status()) {
            case BUSY -> List.of("nickpool.err.busy");
            case NO_SKINS -> List.of("nickpool.err.noskins");
            case DONE -> List.of("nickpool.fill.done", "nickpool.fill.rejected");
            case RATE_LIMITED -> fill.skins() == 0 ? List.of("nickpool.fill.ratelimit")
                    : List.of("nickpool.fill.done", "nickpool.fill.rejected", "nickpool.fill.ratelimit");
        };
    }

    void list(CommandSource sender, int page) {
        Social helper = social();
        NickPoolService pool = pool();
        helper.then(sender, pool.stats(), stats -> {
            int pages = pages(stats.total());
            int shown = Math.min(page, pages);
            helper.then(sender, pool.list((shown - 1) * PAGE_SIZE, PAGE_SIZE), entries -> {
                helper.send(sender, "nickpool.list.header", Social.unparsed("total", stats.total()),
                        Social.unparsed("page", shown), Social.unparsed("pages", pages));
                for (NickPoolService.Entry entry : entries) {
                    helper.send(sender, "nickpool.list.line", Social.unparsed("name", entry.name()),
                            Placeholder.component("state", helper.text(sender, stateKey(entry.state()))));
                }
            });
        });
    }

    /** Seitenzahl, mindestens 1. */
    static int pages(int total) {
        return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static String stateKey(State state) {
        return switch (state) {
            case FREE -> "nickpool.state.free";
            case IN_USE -> "nickpool.state.used";
            case DISABLED -> "nickpool.state.disabled";
        };
    }

    void disable(CommandSource sender, String name) {
        Social helper = social();
        helper.then(sender, pool().disable(name), done -> helper.send(sender, done ? "nickpool.disabled"
                : "nickpool.err.unknown", Social.unparsed("name", name)));
    }

    void stats(CommandSource sender) {
        Social helper = social();
        helper.then(sender, pool().stats(), stats -> {
            helper.send(sender, "nickpool.stats", Social.unparsed("total", stats.total()),
                    Social.unparsed("enabled", stats.enabled()), Social.unparsed("free", stats.free()),
                    Social.unparsed("used", stats.inUse()), Social.unparsed("skins", stats.skins()));
            warnIfLow(helper, sender, stats);
        });
    }

    private static void warnIfLow(Social helper, CommandSource sender, Stats stats) {
        if (stats.low()) {
            helper.send(sender, "nickpool.warn.low", Social.unparsed("free", stats.free()),
                    Social.unparsed("min", NickService.POOL_WARN_FREE));
        }
    }

    /** Monitoring (Spec 01 §6.11 Nr. 7): Log-Warnung bei jedem Lauf, Admins beim Unterschreiten. */
    private void monitor() {
        Social helper = social;
        if (helper == null) {
            return;
        }
        helper.log(pool().stats().thenAccept(stats -> {
            if (!stats.low()) {
                lowNotified = false;
                return;
            }
            helper.core().log().warn("Nick-Pool: nur noch {} freie Nicks (Warnung unter {}), /nickpool fill",
                    stats.free(), NickService.POOL_WARN_FREE);
            if (!lowNotified) {
                lowNotified = true;
                for (Player admin : helper.core().proxy().getAllPlayers()) {
                    if (admin.hasPermission(PERMISSION)) {
                        warnIfLow(helper, admin, stats);
                    }
                }
            }
        }), "Nick-Pool-Monitoring");
    }
}
