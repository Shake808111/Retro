package net.retromc.paper.core.profile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.social.ClanService;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.event.LocaleChangedEvent;
import net.retromc.paper.core.event.ProfileLoadedEvent;
import net.retromc.paper.core.event.ProfileUpdatedEvent;
import net.retromc.paper.core.social.nick.NickApplyFeature;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.PlayerLocales;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

/**
 * Profile der verbundenen Spieler ({@link PlayerProfiles}, {@link RankDisplayService}; Spec 02 §2.2, §6.3, §8.1):
 * beim Join laden ({@code ProfileLoadedEvent}), danach ereignisgetrieben neu laden ({@code ProfileUpdatedEvent}):
 * {@code PROFILE_CHANGED} (Coins, Gold, Rang, Sprache, Clan), {@code PROFILE_INVALIDATE}, {@code NICK_CHANGED},
 * Clan- und Freundes-Ereignisse aus {@code net:social} und Rangänderungen in LuckPerms. Eine neue Sprache löst
 * {@code LocaleChangedEvent} aus (Spec 02 §6.13 Nr. 3).
 */
public final class ProfileFeature implements CoreFeature<PaperCore>, Listener, PlayerProfiles, RankDisplayService {

    /** Feld für Freunde online/gesamt (kein Profilfeld). */
    public static final String FRIENDS = "friends";
    /** Versuche, wenn {@code core.player} beim ersten Join noch nicht geschrieben ist (Proxy bucht parallel). */
    static final int LOAD_ATTEMPTS = 5;
    static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    private final Map<UUID, ProfileView> views = new ConcurrentHashMap<>();
    private final List<Registration> registrations = new ArrayList<>();
    private @Nullable PaperCore core;
    private @Nullable EventSubscription<UserDataRecalculateEvent> luckPerms;

    @Override
    public String name() {
        return "Profile";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        MessageBus bus = context.services().get(MessageBus.class);
        registrations.add(bus.subscribe(MessageType.PROFILE_CHANGED,
                envelope -> refresh(envelope.d().uuid(), envelope.d().fields())).join());
        registrations.add(bus.subscribe(MessageType.PROFILE_INVALIDATE,
                envelope -> refresh(envelope.d().uuid(), envelope.d().fields())).join());
        registrations.add(bus.subscribe(MessageType.NICK_CHANGED,
                envelope -> refresh(envelope.d().uuid(), List.of(Profile.NICK_NAME))).join());
        registrations.add(bus.subscribe(MessageType.CLAN_UPDATE,
                envelope -> envelope.d().members().forEach(member -> refresh(member, List.of(Profile.CLAN_TAG))))
                .join());
        registrations.add(bus.subscribe(MessageType.CLAN_ONLINE,
                envelope -> envelope.d().targets().forEach(target -> refresh(target, List.of(Profile.CLAN_ID))))
                .join());
        registrations.add(bus.subscribe(MessageType.CLAN_OFFLINE,
                envelope -> envelope.d().targets().forEach(target -> refresh(target, List.of(Profile.CLAN_ID))))
                .join());
        registrations.add(bus.subscribe(MessageType.FRIEND_ONLINE,
                envelope -> envelope.d().targets().forEach(target -> refresh(target, List.of(FRIENDS)))).join());
        registrations.add(bus.subscribe(MessageType.FRIEND_OFFLINE,
                envelope -> envelope.d().targets().forEach(target -> refresh(target, List.of(FRIENDS)))).join());
        registrations.add(bus.subscribe(MessageType.FRIEND_ADDED, envelope -> {
            refresh(envelope.d().actor(), List.of(FRIENDS));
            refresh(envelope.d().target(), List.of(FRIENDS));
        }).join());
        registrations.add(bus.subscribe(MessageType.FRIEND_REMOVED, envelope -> {
            refresh(envelope.d().actor(), List.of(FRIENDS));
            refresh(envelope.d().target(), List.of(FRIENDS));
        }).join());
        subscribeLuckPerms(context);
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registrations.add(context.services().registerDefault(PlayerProfiles.class, this));
        registrations.add(context.services().registerDefault(RankDisplayService.class, this));
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player.getUniqueId(), Set.of(), true, 1);
        }
    }

    /** Rangänderungen in LuckPerms (z. B. {@code /rank}) zeichnen Chat, Tab und Scoreboard neu. */
    private void subscribeLuckPerms(PaperCore context) {
        try {
            luckPerms = LuckPermsProvider.get().getEventBus().subscribe(context.plugin(),
                    UserDataRecalculateEvent.class, event -> {
                        UUID uuid = event.getUser().getUniqueId();
                        if (views.containsKey(uuid)) {
                            refresh(uuid, List.of(Profile.RANK));
                        }
                    });
        } catch (IllegalStateException | NoClassDefFoundError e) {
            context.log().warn("LuckPerms nicht verfügbar: Rangänderungen erscheinen erst nach Neuverbindung");
        }
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        EventSubscription<UserDataRecalculateEvent> subscription = luckPerms;
        if (subscription != null) {
            subscription.close();
        }
        luckPerms = null;
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        registrations.clear();
        views.clear();
        core = null;
    }

    @Override
    public Optional<ProfileView> view(UUID player) {
        return Optional.ofNullable(views.get(player));
    }

    @Override
    public RankDisplay of(UUID player) {
        ProfileView view = views.get(player);
        return view == null ? RankDisplay.of(Rank.DEFAULT) : view.rank();
    }

    @Override
    public CompletableFuture<Optional<ProfileView>> refresh(UUID player, Collection<String> fields) {
        if (Bukkit.getPlayer(player) == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return load(player, Set.copyOf(fields), false, LOAD_ATTEMPTS);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId(), Set.of(), true, LOAD_ATTEMPTS);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        views.remove(uuid);
        PaperCore context = core;
        if (context != null) {
            context.services().find(PlayerLocales.class).ifPresent(locales -> locales.remove(uuid));
        }
    }

    /** Lädt Profil, Rang, Freunde, Clan und Präsenz und meldet das Ergebnis auf dem Haupt-Thread. */
    private CompletableFuture<Optional<ProfileView>> load(UUID uuid, Set<String> fields, boolean initial,
            int attempts) {
        PaperCore context = core;
        if (context == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<ProfileView>> view = fetch(context, uuid);
        view.thenAccept(found -> {
            if (found.isEmpty() && attempts > 1 && Bukkit.getPlayer(uuid) != null) {
                CompletableFuture.delayedExecutor(RETRY_DELAY.toMillis(), TimeUnit.MILLISECONDS)
                        .execute(() -> load(uuid, fields, initial, attempts - 1));
            }
        });
        context.mainThread().accept(view, "Profil laden", found -> found.ifPresent(loaded -> publish(context,
                uuid, loaded, fields, initial)));
        return view;
    }

    private static CompletableFuture<Optional<ProfileView>> fetch(PaperCore context, UUID uuid) {
        CompletableFuture<Optional<Profile>> profile = context.services().get(ProfileService.class).profile(uuid);
        return profile.thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<ProfileView>empty());
            }
            Profile loaded = found.get();
            CompletableFuture<RankDisplay> rank = context.services().get(RankService.class).rank(uuid)
                    .thenApply(RankDisplayService::of);
            CompletableFuture<OnlineCount> friends = context.services().get(FriendService.class).counts(uuid)
                    .exceptionally(error -> new OnlineCount(0, 0));
            Long clanId = loaded.clanId();
            CompletableFuture<@Nullable OnlineCount> clan = clanId == null
                    ? CompletableFuture.completedFuture(null)
                    : context.services().get(ClanService.class).counts(clanId).exceptionally(error -> null);
            CompletableFuture<Instant> since = context.services().get(PresenceService.class).presence(uuid)
                    .thenApply(presence -> presence.map(value -> value.since()).orElseGet(Instant::now))
                    .exceptionally(error -> Instant.now());
            boolean nickVisible = loaded.nickName() != null && NickApplyFeature.applies(context.config().category());
            return CompletableFuture.allOf(rank, friends, clan, since).thenApply(ignored -> Optional.of(
                    new ProfileView(loaded, rank.join(), friends.join(), clan.join(), since.join(), nickVisible)));
        });
    }

    private void publish(PaperCore context, UUID uuid, ProfileView view, Set<String> fields, boolean initial) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || core == null) {
            return;
        }
        ProfileView previous = views.put(uuid, view);
        boolean localeChanged = context.services().find(PlayerLocales.class)
                .map(locales -> locales.put(uuid, view.profile().locale())).orElse(false);
        if (previous == null && initial) {
            Bukkit.getPluginManager().callEvent(new ProfileLoadedEvent(player, view));
            return;
        }
        Bukkit.getPluginManager().callEvent(new ProfileUpdatedEvent(player, view, fields));
        if (localeChanged) {
            Bukkit.getPluginManager().callEvent(new LocaleChangedEvent(player, view.locale()));
        }
    }
}
