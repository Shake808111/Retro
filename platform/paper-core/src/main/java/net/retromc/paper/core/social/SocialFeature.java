package net.retromc.paper.core.social;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.bridge.ProxyBridge;
import net.retromc.paper.core.social.gui.SocialGuiPlans;
import net.retromc.paper.core.social.gui.SocialGuiService;
import net.retromc.paper.core.social.gui.SocialGuis;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.social.SocialConfig;
import net.retromc.platform.common.social.SocialServices;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

/**
 * Social auf Paper: Standard-Dienste ({@link SocialServices}, dieselben wie auf dem Proxy) und die Social-GUIs aus
 * Spec 01 §5 ({@link SocialGuiService}, geöffnet über {@code OPEN_GUI} vom Proxy oder von der Lobby).
 */
public final class SocialFeature implements CoreFeature<PaperCore>, Listener {

    /** GUIs, die der Proxy über {@code OPEN_GUI} öffnen darf. */
    static final List<String> GUI_IDS = List.of(SocialGuiPlans.PARTY, SocialGuiPlans.FRIENDS,
            SocialGuiPlans.FRIEND_REQUESTS, SocialGuiPlans.CLAN, SocialGuiPlans.CLAN_SETTINGS,
            SocialGuiPlans.SETTINGS, SocialGuiPlans.NICK);

    private final List<Registration> registrations = new ArrayList<>();
    private @Nullable SocialServices services;
    private @Nullable SocialGuis guis;

    @Override
    public String name() {
        return "Social";
    }

    @Override
    public void enable(PaperCore core) {
        SocialConfig config = SocialConfig.read(EnvReader.system());
        SocialServices.Platform platform = new SocialServices.Platform(
                player -> Bukkit.getPlayer(player) != null ? CompletableFuture.completedFuture(true)
                        : core.services().find(PresenceService.class)
                                .map(presence -> presence.presence(player).thenApply(found -> found.isPresent())
                                        .exceptionally(error -> false))
                                .orElseGet(() -> CompletableFuture.completedFuture(false)),
                name -> Bukkit.getPlayerExact(name) != null, Set.of(config.domain()), Clock.systemUTC());
        services = SocialServices.register(core.services(), platform, core.log());
        SocialGuis social = new SocialGuis(core);
        guis = social;
        registrations.add(core.services().registerDefault(SocialGuiService.class, social));
        ProxyBridge bridge = core.services().get(ProxyBridge.class);
        for (String id : GUI_IDS) {
            registrations.add(bridge.onOpenGui(id, (player, argument) ->
                    core.services().get(SocialGuiService.class).open(player, id, argument)));
        }
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
    }

    @Override
    public void disable(PaperCore core) {
        HandlerList.unregisterAll(this);
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        registrations.clear();
        SocialServices current = services;
        if (current != null) {
            current.close();
        }
        services = null;
        guis = null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SocialGuis current = guis;
        if (current != null) {
            current.forget(event.getPlayer().getUniqueId());
        }
    }
}
