package net.retromc.paper.core.bridge;

import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.bridge.BridgeCodec;
import net.retromc.platform.common.feature.CoreFeature;
import org.bukkit.plugin.messaging.Messenger;
import org.jspecify.annotations.Nullable;

/** Registriert den Kanal {@code <brandId>:bridge} in beide Richtungen und den Dienst {@link ProxyBridge}. */
public final class BridgeFeature implements CoreFeature<PaperCore> {

    private @Nullable PluginMessageBridge bridge;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Bridge";
    }

    @Override
    public void enable(PaperCore core) {
        PluginMessageBridge messages = new PluginMessageBridge(core.plugin(), core.mainThread(), core.log());
        Messenger messenger = core.plugin().getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(core.plugin(), BridgeCodec.CHANNEL);
        messenger.registerIncomingPluginChannel(core.plugin(), BridgeCodec.CHANNEL, messages);
        bridge = messages;
        registration = core.services().registerDefault(ProxyBridge.class, messages);
    }

    @Override
    public void disable(PaperCore core) {
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        PluginMessageBridge messages = bridge;
        if (messages != null) {
            Messenger messenger = core.plugin().getServer().getMessenger();
            messenger.unregisterIncomingPluginChannel(core.plugin(), BridgeCodec.CHANNEL, messages);
            messenger.unregisterOutgoingPluginChannel(core.plugin(), BridgeCodec.CHANNEL);
        }
        registration = null;
        bridge = null;
    }
}
