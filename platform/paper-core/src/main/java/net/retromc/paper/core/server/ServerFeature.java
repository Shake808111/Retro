package net.retromc.paper.core.server;

import io.lettuce.core.Range;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.event.Connect;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import net.retromc.core.api.server.TemplateId;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.data.ValkeyKeys;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.core.messaging.payload.QueueTicket;
import net.retromc.core.messaging.payload.SendLobby;
import net.retromc.core.messaging.payload.ServerDown;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.config.ServerConfig;
import net.retromc.platform.common.feature.CoreFeature;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Registrierung, Heartbeat und Abmeldung des eigenen Servers (Spec 08 §6.2 Nr. 3–5, 8) als Standard für
 * {@link ServerLifecycle}, {@link PlayerTransfer} und {@link QueueClient}. Lobbys und Dauerserver gehen nach dem
 * Start auf ONLINE, Spielserver bleiben BOOTING, bis der Spiel-Rahmen WAITING setzt.
 */
public final class ServerFeature implements CoreFeature<PaperCore>, ServerLifecycle, PlayerTransfer, QueueClient,
        Listener {

    /** Heartbeat in Ticks (5 s). */
    static final long HEARTBEAT_TICKS = ValkeyKeys.SERVER_HEARTBEAT.toSeconds() * 20;
    /** Wartezeit zwischen {@code SEND_LOBBY} und {@code Bukkit.shutdown()} (Spec 08 §6.10 Nr. 1). */
    static final long SHUTDOWN_DELAY_TICKS = 40;

    private final Map<String, String> extras = new ConcurrentHashMap<>();
    private volatile @Nullable ServerInfo self;
    private volatile long lastPublish;
    private volatile int matches;
    private @Nullable PaperCore core;
    private @Nullable StatefulRedisConnection<String, String> valkey;
    private @Nullable BukkitTask heartbeat;
    private final List<Registration> registrations = new ArrayList<>();

    @Override
    public String name() {
        return "Server-Registry";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        valkey = context.services().get(ValkeyConnector.class).connect();
        ServerConfig config = context.config();
        self = initial(config);
        RedisAsyncCommands<String, String> redis = redis();
        redis.sadd(ValkeyKeys.serversAll(), config.name());
        redis.sadd(ValkeyKeys.serversByTemplate(config.template()), config.name());
        write(true);
        bus().publish(MessageType.SERVER_UP, info());
        heartbeat = Bukkit.getScheduler().runTaskTimer(context.plugin(), this::beat, HEARTBEAT_TICKS,
                HEARTBEAT_TICKS);
        if (config.category() != ServerCategory.GAME) {
            // Erster Tick nach dem Start = alle Plugins geladen (ServerLoadEvent STARTUP, Spec 08 §6.2 Nr. 4).
            Bukkit.getScheduler().runTask(context.plugin(), () -> setState(ServerState.ONLINE));
        }
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registrations.add(context.services().registerDefault(ServerLifecycle.class, this));
        registrations.add(context.services().registerDefault(PlayerTransfer.class, this));
        registrations.add(context.services().registerDefault(QueueClient.class, this));
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        BukkitTask task = heartbeat;
        if (task != null) {
            task.cancel();
        }
        registrations.forEach(Registration::close);
        registrations.clear();
        ServerInfo current = info();
        if (current.state().canTransitionTo(ServerState.STOPPING)) {
            self = with(current, ServerState.STOPPING);
            bus().publish(MessageType.SERVER_STATE, info());
        }
        List<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        if (!online.isEmpty()) {
            pending.add(toLobby(online, "server.stopping.moved", null));
        }
        pending.add(bus().publish(MessageType.SERVER_DOWN, new ServerDown(current.name(), "STOPPING")));
        RedisAsyncCommands<String, String> redis = redis();
        pending.add(redis.del(ValkeyKeys.server(current.name())).toCompletableFuture());
        pending.add(redis.srem(ValkeyKeys.serversAll(), current.name()).toCompletableFuture());
        pending.add(redis.srem(ValkeyKeys.serversByTemplate(context.config().template()), current.name())
                .toCompletableFuture());
        try {
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            context.log().warn("Abmeldung aus der Server-Registry unvollständig: {}", e.toString());
        }
        StatefulRedisConnection<String, String> connection = valkey;
        if (connection != null) {
            connection.close();
        }
        valkey = null;
        heartbeat = null;
        core = null;
    }

    // ------------------------------------------------------------------------------------------ ServerLifecycle

    @Override
    public ServerInfo self() {
        return info();
    }

    @Override
    public synchronized void setState(ServerState state) {
        ServerInfo current = info();
        if (current.state() == state) {
            return;
        }
        if (!current.state().canTransitionTo(state)) {
            context().log().warn("Server-Zustand {} -> {} nicht erlaubt (Spec 08 §6.1)", current.state(), state);
            return;
        }
        self = with(current, state);
        write(false);
        doPublish();
    }

    @Override
    public synchronized void setMap(@Nullable String map) {
        ServerInfo c = info();
        self = new ServerInfo(c.name(), c.template(), c.mode(), c.variant(), c.category(), c.state(), c.online(),
                c.max(), c.reserved(), map, c.host(), c.port(), c.minProtocol(), c.silent(), c.rankMin(),
                c.privateRound(), c.displayName(), c.versionLabel(), c.matchId(), c.startedAt());
        write(false);
    }

    @Override
    public synchronized void setMatchId(@Nullable String matchId) {
        ServerInfo c = info();
        self = new ServerInfo(c.name(), c.template(), c.mode(), c.variant(), c.category(), c.state(), c.online(),
                c.max(), c.reserved(), c.map(), c.host(), c.port(), c.minProtocol(), c.silent(), c.rankMin(),
                c.privateRound(), c.displayName(), c.versionLabel(), matchId, c.startedAt());
        write(false);
    }

    @Override
    public synchronized void setPrivateRound(boolean privateRound) {
        ServerInfo c = info();
        self = new ServerInfo(c.name(), c.template(), c.mode(), c.variant(), c.category(), c.state(), c.online(),
                c.max(), c.reserved(), c.map(), c.host(), c.port(), c.minProtocol(), c.silent(), c.rankMin(),
                privateRound, c.displayName(), c.versionLabel(), c.matchId(), c.startedAt());
        write(false);
    }

    @Override
    public synchronized void describe(String mode, String variant, String displayName, int max) {
        ServerInfo c = info();
        self = new ServerInfo(c.name(), c.template(), mode, variant, c.category(), c.state(), c.online(), max,
                c.reserved(), c.map(), c.host(), c.port(), c.minProtocol(), c.silent(), c.rankMin(),
                c.privateRound(), displayName, c.versionLabel(), c.matchId(), c.startedAt());
        write(false);
    }

    @Override
    public void setField(String field, @Nullable String value) {
        Objects.requireNonNull(field, "field");
        String key = ValkeyKeys.server(info().name());
        if (value == null) {
            extras.remove(field);
            redis().hdel(key, field);
        } else {
            extras.put(field, value);
            redis().hset(key, field, value);
        }
    }

    @Override
    public void publish() {
        long now = System.currentTimeMillis();
        if (now - lastPublish < 1000) {
            return;
        }
        refreshOnline();
        write(false);
        doPublish();
    }

    @Override
    public void incrementMatches() {
        matches++;
        setField("matches", Integer.toString(matches));
    }

    @Override
    public void requestShutdown(String reason) {
        PaperCore context = context();
        context.log().info("Server wird beendet: {}", reason);
        setState(ServerState.STOPPING);
        List<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        if (!online.isEmpty()) {
            toLobby(online, "server.stopping.moved", null);
        }
        Bukkit.getScheduler().runTaskLater(context.plugin(), Bukkit::shutdown, SHUTDOWN_DELAY_TICKS);
    }

    // ------------------------------------------------------------------------------------------ PlayerTransfer

    @Override
    public CompletableFuture<Void> toServer(Player player, String server) {
        return bus().publish(MessageType.CONNECT, new Connect(player.getUniqueId(), server)).thenApply(e -> null);
    }

    @Override
    public CompletableFuture<Void> toLobby(Collection<UUID> players, String reasonKey, @Nullable String mode) {
        return bus().publish(MessageType.SEND_LOBBY, new SendLobby(List.copyOf(players), reasonKey, mode))
                .thenApply(e -> null);
    }

    // ------------------------------------------------------------------------------------------ QueueClient

    @Override
    public CompletableFuture<JoinResult> join(List<UUID> players, String mode, String variant,
            @Nullable String partyId, String source) {
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Ticket ohne Spieler");
        }
        String ticket = UUID.randomUUID().toString();
        String guard = ValkeyKeys.playerTicket(players.getFirst());
        SetArgs args = SetArgs.Builder.nx().ex(ValkeyKeys.TICKET_TTL.toSeconds());
        return redis().set(guard, ticket, args).toCompletableFuture().thenCompose(ok -> {
            if (!"OK".equals(ok)) {
                return CompletableFuture.completedFuture(JoinResult.ALREADY);
            }
            QueueTicket payload = new QueueTicket(ticket, players, partyId, mode, variant, null);
            return bus().publish(MessageType.QUEUE_JOIN, payload).thenApply(sent -> JoinResult.QUEUED);
        });
    }

    @Override
    public CompletableFuture<Void> leave(UUID player) {
        String guard = ValkeyKeys.playerTicket(player);
        return redis().getdel(guard).toCompletableFuture().thenCompose(ticket -> {
            if (ticket == null) {
                return CompletableFuture.completedFuture(null);
            }
            ServerInfo c = info();
            QueueTicket payload = new QueueTicket(ticket, List.of(player), null, c.mode(), c.variant(), null);
            return bus().publish(MessageType.QUEUE_LEAVE, payload).thenApply(sent -> null);
        });
    }

    // ------------------------------------------------------------------------------------------ Spielerzahl

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        onlineChanged(0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onlineChanged(-1);
    }

    private void onlineChanged(int correction) {
        ServerInfo c = info();
        int online = Math.max(0, Bukkit.getOnlinePlayers().size() + correction);
        self = withOnline(c, online);
        redis().hset(ValkeyKeys.server(c.name()), Map.of(ServerInfo.F_ONLINE, Integer.toString(online),
                ServerInfo.F_JOINABLE, info().joinable() ? "1" : "0"));
        if (c.category() == ServerCategory.GAME) {
            publish();
        }
    }

    // ------------------------------------------------------------------------------------------ intern

    private void beat() {
        refreshOnline();
        ServerInfo c = info();
        redis().zcount(ValkeyKeys.reserve(c.name()), Range.create(System.currentTimeMillis(),
                Double.POSITIVE_INFINITY)).thenAccept(count -> {
                    ServerInfo now = info();
                    self = new ServerInfo(now.name(), now.template(), now.mode(), now.variant(), now.category(),
                            now.state(), now.online(), now.max(), count == null ? 0 : count.intValue(), now.map(),
                            now.host(), now.port(), now.minProtocol(), now.silent(), now.rankMin(),
                            now.privateRound(), now.displayName(), now.versionLabel(), now.matchId(),
                            now.startedAt());
                    write(true);
                });
    }

    private void refreshOnline() {
        if (Bukkit.isPrimaryThread()) {
            self = withOnline(info(), Bukkit.getOnlinePlayers().size());
        }
    }

    /** HSET aller Felder (+ Zusatzfelder) und EXPIRE 15 s. */
    private void write(boolean full) {
        ServerInfo c = info();
        Map<String, String> hash = new LinkedHashMap<>(c.toHash());
        if (full) {
            hash.putAll(extras);
            hash.put(ServerInfo.F_BUILD, BuildInfo.VERSION);
        }
        String key = ValkeyKeys.server(c.name());
        RedisAsyncCommands<String, String> redis = redis();
        redis.hset(key, hash);
        redis.expire(key, ValkeyKeys.SERVER_TTL.toSeconds());
    }

    private void doPublish() {
        lastPublish = System.currentTimeMillis();
        bus().publish(MessageType.SERVER_STATE, info());
    }

    private static ServerInfo initial(ServerConfig config) {
        TemplateId template = config.template();
        String id = template.value();
        String mode;
        String variant = ServerInfo.DEFAULT_VARIANT;
        int dash = id.indexOf('-');
        if (config.category() == ServerCategory.LOBBY) {
            mode = id.startsWith("lobby-") ? id.substring("lobby-".length()) : "lobby";
        } else if (dash > 0) {
            mode = id.substring(0, dash);
            variant = id.substring(dash + 1);
        } else {
            mode = id;
        }
        boolean silent = id.equals(TemplateId.SILENT_LOBBY.value());
        int rankMin = id.equals(TemplateId.PREMIUM_LOBBY.value()) ? 200 : 0;
        String display = mode.isEmpty() ? id : mode.substring(0, 1).toUpperCase(Locale.ROOT) + mode.substring(1);
        return new ServerInfo(config.name(), id, mode, variant, config.category(), ServerState.BOOTING, 0,
                Bukkit.getMaxPlayers(), 0, null, config.host(), config.port(), 0, silent, rankMin, false, display,
                null, null, Instant.now());
    }

    private static ServerInfo with(ServerInfo c, ServerState state) {
        return new ServerInfo(c.name(), c.template(), c.mode(), c.variant(), c.category(), state, c.online(), c.max(),
                c.reserved(), c.map(), c.host(), c.port(), c.minProtocol(), c.silent(), c.rankMin(),
                c.privateRound(), c.displayName(), c.versionLabel(), c.matchId(), c.startedAt());
    }

    private static ServerInfo withOnline(ServerInfo c, int online) {
        return new ServerInfo(c.name(), c.template(), c.mode(), c.variant(), c.category(), c.state(), online,
                c.max(), c.reserved(), c.map(), c.host(), c.port(), c.minProtocol(), c.silent(), c.rankMin(),
                c.privateRound(), c.displayName(), c.versionLabel(), c.matchId(), c.startedAt());
    }

    private ServerInfo info() {
        ServerInfo current = self;
        if (current == null) {
            throw new IllegalStateException("Server-Registry nicht aktiv");
        }
        return current;
    }

    private PaperCore context() {
        PaperCore context = core;
        if (context == null) {
            throw new IllegalStateException("Server-Registry nicht aktiv");
        }
        return context;
    }

    private RedisAsyncCommands<String, String> redis() {
        StatefulRedisConnection<String, String> connection = valkey;
        if (connection == null) {
            throw new IllegalStateException("Server-Registry ohne Valkey");
        }
        return connection.async();
    }

    private MessageBus bus() {
        return context().services().get(MessageBus.class);
    }
}
