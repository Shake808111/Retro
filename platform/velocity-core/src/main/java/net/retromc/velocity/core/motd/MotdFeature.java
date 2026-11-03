package net.retromc.velocity.core.motd;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.server.MaintenanceService;
import net.retromc.core.api.server.NetworkSettingService;
import net.retromc.core.api.server.ServerRegistry;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Serverliste (Spec 01 §4.1): MOTD aus {@link MotdBuilder}, Spielerzahl {@code online/10000} (online aus der
 * Server-Registry, sonst dieser Proxy), Wartungs-Umschaltung über {@code MAINTENANCE_CHANGED}. {@code /netreload}
 * (#88) lädt {@code core.network_setting} und die Texte neu.
 */
public final class MotdFeature implements CoreFeature<VelocityCore> {

    public static final CommandName NETRELOAD = CommandName.of("netreload");
    public static final List<CommandName> COMMANDS = List.of(NETRELOAD);

    private final List<Registration> subscriptions = new ArrayList<>();
    private volatile MotdSettings settings = MotdSettings.DEFAULT;
    private volatile boolean maintenance;
    private @Nullable Social social;

    @Override
    public String name() {
        return "MOTD";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        manager.command(NETRELOAD.builder(manager).permission(Social.permission("admin.reload"))
                .handler(ctx -> helper.then(ctx.sender(), reload(), ignored -> {
                    Messages.reload();
                    helper.send(ctx.sender(), "admin.reloaded");
                })));
        subscriptions.add(core.services().get(MessageBus.class).subscribe(MessageType.MAINTENANCE_CHANGED,
                envelope -> maintenance = envelope.d().enabled()).join());
        core.proxy().getEventManager().register(core.plugin(), this);
        reload().join();
    }

    @Override
    public void disable(VelocityCore core) {
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        subscriptions.forEach(Registration::close);
        subscriptions.clear();
        social = null;
    }

    /** MOTD-Einstellungen und Wartungsstatus neu laden. */
    CompletableFuture<Void> reload() {
        Social helper = social;
        if (helper == null) {
            return CompletableFuture.completedFuture(null);
        }
        return helper.get(NetworkSettingService.class).get(NetworkSettingService.MOTD)
                .thenAccept(json -> settings = MotdSettings.parse(json.orElse(null)))
                .thenCompose(ignored -> helper.get(MaintenanceService.class).state())
                .thenAccept(state -> maintenance = state.enabled());
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        MotdBuilder builder = new MotdBuilder(helper.get(I18n.class));
        MotdSettings current = settings;
        boolean closed = maintenance;
        ProtocolVersion client = event.getConnection().getProtocolVersion();
        ServerPing ping = event.getPing();
        int online = helper.services().find(ServerRegistry.class).map(ServerRegistry::onlineTotal)
                .orElseGet(() -> helper.core().proxy().getPlayerCount());
        MotdBuilder.Version version = builder.version(Messages.DEFAULT_LOCALE, current, closed,
                client.getProtocol(), ping.getVersion().getProtocol(), ping.getVersion().getName());
        event.setPing(ping.asBuilder()
                .description(builder.description(Messages.DEFAULT_LOCALE, current, closed))
                .onlinePlayers(online)
                .maximumPlayers(current.maxPlayers())
                .version(new ServerPing.Version(version.protocol(), version.name()))
                .build());
    }
}
