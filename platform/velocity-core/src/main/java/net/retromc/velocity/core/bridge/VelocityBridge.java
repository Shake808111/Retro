package net.retromc.velocity.core.bridge;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.util.Objects;
import java.util.Optional;
import net.retromc.core.api.msg.BridgeMessage;
import net.retromc.platform.common.bridge.BridgeCodec;
import net.retromc.platform.common.bridge.BridgeFormatException;
import net.retromc.platform.common.bridge.OpenGuiRequest;
import org.slf4j.Logger;

/**
 * {@link ServerBridge} und Empfang von {@code RUN_COMMAND}. Nachrichten auf dem Kanal werden nie weitergeleitet und
 * nur von Server-Verbindungen angenommen; vom Client gesendete werden verworfen, damit niemand {@code OPEN_GUI} an
 * einen Server fälschen kann.
 */
public final class VelocityBridge implements ServerBridge {

    /** Kanal {@code <brandId>:bridge}. */
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(BridgeCodec.CHANNEL);

    private final ProxyServer proxy;
    private final Logger log;

    public VelocityBridge(ProxyServer proxy, Logger log) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean openGui(Player player, String guiId, String argument) {
        byte[] data = BridgeCodec.encode(new OpenGuiRequest(guiId, argument).toMessage());
        Optional<ServerConnection> server = player.getCurrentServer();
        return server.isPresent() && server.get().sendPluginMessage(CHANNEL, data);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            log.warn("Bridge-Nachricht vom Client verworfen");
            return;
        }
        Player player = connection.getPlayer();
        BridgeMessage message;
        try {
            message = BridgeCodec.decode(event.getData(), BridgeCodec.TO_PROXY);
        } catch (BridgeFormatException e) {
            log.warn("Bridge-Nachricht von {} für {} verworfen: {}", connection.getServerInfo().getName(),
                    player.getUsername(), e.getMessage());
            return;
        }
        String commandLine = message.argument();
        proxy.getCommandManager().executeAsync(player, commandLine).whenComplete((found, error) -> {
            if (error != null) {
                log.error("Proxy-Befehl '{}' für {} fehlgeschlagen", commandLine, player.getUsername(), error);
            } else if (!found) {
                log.warn("Proxy-Befehl '{}' für {} ist unbekannt", commandLine, player.getUsername());
            }
        });
    }
}
