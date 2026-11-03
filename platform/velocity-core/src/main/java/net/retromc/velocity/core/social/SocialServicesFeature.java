package net.retromc.velocity.core.social;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.retromc.core.api.social.PlayerLogin;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.social.ProfileService;
import net.retromc.platform.common.config.EnvReader;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.social.SocialConfig;
import net.retromc.platform.common.social.SocialServices;
import net.retromc.velocity.core.VelocityCore;
import org.jspecify.annotations.Nullable;

/**
 * Standard-Dienste für Social und Moderation auf dem Proxy ({@link SocialServices}) und Spielerdaten beim
 * Login/Logout über {@link ProfileService} (Spec 01 §6.3 Nr. 4 und 7). Online heißt: auf diesem Proxy oder mit
 * Präsenz ({@link PresenceService}, falls ein Dienst sie bereitstellt).
 */
public final class SocialServicesFeature implements CoreFeature<VelocityCore> {

    private final Map<UUID, Instant> sessions = new ConcurrentHashMap<>();
    private @Nullable SocialServices registered;
    private @Nullable Social social;
    private @Nullable SocialConfig config;

    @Override
    public String name() {
        return "Social-Dienste";
    }

    @Override
    public void enable(VelocityCore core) {
        SocialConfig settings = SocialConfig.read(EnvReader.system());
        Social helper = new Social(core);
        SocialServices.Platform platform = new SocialServices.Platform(
                player -> core.proxy().getPlayer(player).isPresent()
                        ? CompletableFuture.completedFuture(true)
                        : core.services().find(PresenceService.class)
                                .map(presence -> presence.presence(player).thenApply(Optional::isPresent)
                                        .exceptionally(error -> false))
                                .orElseGet(() -> CompletableFuture.completedFuture(false)),
                name -> core.proxy().getPlayer(name).isPresent(), Set.of(settings.domain()), Clock.systemUTC());
        registered = SocialServices.register(core.services(), platform, core.log());
        config = settings;
        social = helper;
        core.proxy().getEventManager().register(core.plugin(), this);
    }

    @Override
    public void disable(VelocityCore core) {
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        SocialServices current = registered;
        if (current != null) {
            current.close();
        }
        registered = null;
        social = null;
        config = null;
        sessions.clear();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Social helper = social;
        SocialConfig settings = config;
        if (helper == null || settings == null) {
            return;
        }
        Player player = event.getPlayer();
        Instant now = Instant.now();
        sessions.put(player.getUniqueId(), now);
        PlayerLogin login = new PlayerLogin(player.getUniqueId(), player.getUsername(),
                settings.ip(player.getRemoteAddress().getAddress()).orElse(null),
                player.getEffectiveLocale() == null ? "de_DE" : player.getEffectiveLocale().toString(),
                helper.core().config().name(), now);
        helper.log(helper.get(ProfileService.class).recordLogin(login), "Login von " + player.getUsername());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Social helper = social;
        Instant joined = sessions.remove(event.getPlayer().getUniqueId());
        if (helper == null || joined == null) {
            return;
        }
        Instant now = Instant.now();
        helper.log(helper.get(ProfileService.class).recordQuit(event.getPlayer().getUniqueId(), now,
                Duration.between(joined, now).toSeconds()), "Logout von " + event.getPlayer().getUsername());
    }
}
