package net.retromc.paper.core.bridge;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import net.retromc.core.api.msg.BridgeMessage;
import net.retromc.core.api.util.Registration;
import net.retromc.platform.common.bridge.BridgeCodec;
import net.retromc.platform.common.bridge.BridgeFormatException;
import net.retromc.platform.common.bridge.OpenGuiRequest;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.slf4j.Logger;

/**
 * {@link ProxyBridge} über Bukkit-Plugin-Messages. Eingehend wird nur {@code OPEN_GUI} angenommen; der Proxy leitet
 * den Kanal nie vom Client weiter (siehe {@link BridgeCodec}).
 */
public final class PluginMessageBridge implements ProxyBridge, PluginMessageListener {

    private final Plugin plugin;
    private final Executor mainThread;
    private final Logger log;
    private final Map<String, BiConsumer<Player, String>> guiHandlers = new ConcurrentHashMap<>();

    public PluginMessageBridge(Plugin plugin, Executor mainThread, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean runProxyCommand(Player player, String commandLine) {
        byte[] data = BridgeCodec.encode(BridgeMessage.runCommand(commandLine));
        if (!player.isOnline()) {
            return false;
        }
        player.sendPluginMessage(plugin, BridgeCodec.CHANNEL, data);
        return true;
    }

    @Override
    public Registration onOpenGui(String guiId, BiConsumer<Player, String> handler) {
        if (guiId.isBlank() || guiId.contains(" ")) {
            throw new IllegalArgumentException("GUI-ID ohne Leerzeichen erwartet: '" + guiId + "'");
        }
        Objects.requireNonNull(handler, "handler");
        if (guiHandlers.putIfAbsent(guiId, handler) != null) {
            throw new IllegalStateException("Für GUI '" + guiId + "' gibt es schon einen Handler");
        }
        return () -> guiHandlers.remove(guiId, handler);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BridgeCodec.CHANNEL.equals(channel)) {
            return;
        }
        OpenGuiRequest request;
        try {
            request = OpenGuiRequest.of(BridgeCodec.decode(message, BridgeCodec.TO_SERVER));
        } catch (BridgeFormatException e) {
            log.warn("Bridge-Nachricht für {} verworfen: {}", player.getName(), e.getMessage());
            return;
        }
        BiConsumer<Player, String> handler = guiHandlers.get(request.guiId());
        if (handler == null) {
            log.warn("Keine GUI '{}' für OPEN_GUI registriert", request.guiId());
            return;
        }
        mainThread.execute(() -> {
            if (player.isOnline()) {
                handler.accept(player, request.argument());
            }
        });
    }
}
