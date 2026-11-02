package net.retromc.velocity.core.bridge;

import net.retromc.core.api.util.Registration;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.velocity.core.VelocityCore;
import org.jspecify.annotations.Nullable;

/** Registriert den Kanal {@code <brandId>:bridge} und den Dienst {@link ServerBridge}. */
public final class BridgeFeature implements CoreFeature<VelocityCore> {

    private @Nullable VelocityBridge bridge;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Bridge";
    }

    @Override
    public void enable(VelocityCore core) {
        VelocityBridge messages = new VelocityBridge(core.proxy(), core.log());
        core.proxy().getChannelRegistrar().register(VelocityBridge.CHANNEL);
        core.proxy().getEventManager().register(core.plugin(), messages);
        bridge = messages;
        registration = core.services().registerDefault(ServerBridge.class, messages);
    }

    @Override
    public void disable(VelocityCore core) {
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        VelocityBridge messages = bridge;
        if (messages != null) {
            core.proxy().getEventManager().unregisterListener(core.plugin(), messages);
            core.proxy().getChannelRegistrar().unregister(VelocityBridge.CHANNEL);
        }
        registration = null;
        bridge = null;
    }
}
