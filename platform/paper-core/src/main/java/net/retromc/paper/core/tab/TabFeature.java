package net.retromc.paper.core.tab;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.stats.StatsMode;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.event.LocaleChangedEvent;
import net.retromc.paper.core.event.ProfileLoadedEvent;
import net.retromc.paper.core.event.ProfileUpdatedEvent;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.paper.core.profile.ProfileView;
import net.retromc.paper.core.stats.ModeCatalog;
import net.retromc.paper.core.stats.ServerMode;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jspecify.annotations.Nullable;

/**
 * Tablist auf jedem Paper-Server (Spec 02 §4.4, §6.2): Header/Footer in der Sprache des Spielers, Einträge im
 * Lobby-Format (Rang-Präfix, Rangfarbe, Clan-Tag; genickt Premium ohne Clan-Tag) oder vom Spiel-Rahmen, Sortierung
 * über {@code setPlayerListOrder(rankWeight)}. Neu gezeichnet bei Profil-, Sprach- und Ressourcenpaket-Änderungen.
 */
public final class TabFeature implements CoreFeature<PaperCore>, Listener, TabService {

    private final Map<UUID, Component[]> fixed = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<EntryProvider> providers = new CopyOnWriteArrayList<>();
    private volatile @Nullable Function<Locale, List<Component>> serverFooter;
    private volatile @Nullable String serverDisplay;
    private @Nullable PaperCore core;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Tablist";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registration = context.services().registerDefault(TabService.class, this);
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        providers.clear();
        fixed.clear();
        core = null;
    }

    @Override
    public void setHeaderFooter(Player player, Component header, Component footer) {
        fixed.put(player.getUniqueId(), new Component[] {header, footer});
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    @Override
    public void clearHeaderFooter(Player player) {
        fixed.remove(player.getUniqueId());
        refresh(player);
    }

    @Override
    public void setServerFooter(Function<Locale, List<Component>> extraLines) {
        serverFooter = extraLines;
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    @Override
    public void clearServerFooter() {
        serverFooter = null;
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    @Override
    public void setServerDisplay(String display) {
        serverDisplay = display;
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    @Override
    public Registration registerEntryProvider(EntryProvider provider) {
        providers.add(provider);
        Bukkit.getOnlinePlayers().forEach(this::refresh);
        return () -> {
            providers.remove(provider);
            Bukkit.getOnlinePlayers().forEach(this::refresh);
        };
    }

    @Override
    public void refresh(Player player) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        I18n i18n = context.services().get(I18n.class);
        Component[] own = fixed.get(player.getUniqueId());
        if (own != null) {
            player.sendPlayerListHeaderAndFooter(own[0], own[1]);
        } else {
            Locale locale = i18n.locale(player.getUniqueId());
            boolean banner = player.getResourcePackStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
            Function<Locale, List<Component>> footer = serverFooter;
            player.sendPlayerListHeaderAndFooter(TabFormat.header(i18n, locale, identity(context), banner),
                    TabFormat.footer(i18n, locale, footer == null ? null : footer.apply(locale),
                            context.config().name()));
        }
        Entry entry = entry(context, i18n, player);
        player.playerListName(entry.name());
        player.setPlayerListOrder(entry.order());
    }

    /** Server-Anzeige mit Modus aus dem Katalog; ein Modul kann {@code serverDisplay} ersetzen. */
    private ServerIdentity identity(PaperCore context) {
        Optional<ModeCatalog> catalog = context.services().find(ModeCatalog.class);
        Optional<StatsMode> mode = catalog.flatMap(modes -> ServerMode.resolve(context.config().template(),
                context.config().category(), key -> modes.mode(key).isPresent()).mode().flatMap(modes::mode));
        ServerIdentity identity = ServerIdentity.of(context.config().name(), context.config().template(),
                context.config().category(), mode, found -> Messages.plain(Messages.DEFAULT_LOCALE, found.i18nKey(),
                        Map.of()));
        String display = serverDisplay;
        return display == null ? identity : new ServerIdentity(display, identity.color());
    }

    private Entry entry(PaperCore context, I18n i18n, Player player) {
        for (EntryProvider provider : providers) {
            Optional<Entry> entry = provider.entry(player);
            if (entry.isPresent()) {
                return entry.get();
            }
        }
        Optional<ProfileView> view = context.services().find(PlayerProfiles.class)
                .flatMap(profiles -> profiles.view(player.getUniqueId()));
        RankDisplay rank = view.map(ProfileView::displayRank).orElseGet(() -> RankDisplay.of(Rank.DEFAULT));
        String name = view.map(ProfileView::displayName).orElse(player.getName());
        return new Entry(TabFormat.lobbyEntry(i18n, Messages.DEFAULT_LOCALE, rank, name,
                view.map(ProfileView::clanTag).orElse(null)), rank.weight());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fixed.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onLoaded(ProfileLoadedEvent event) {
        refresh(event.player());
    }

    @EventHandler
    public void onUpdated(ProfileUpdatedEvent event) {
        refresh(event.player());
    }

    @EventHandler
    public void onLocale(LocaleChangedEvent event) {
        refresh(event.player());
    }

    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            refresh(event.getPlayer());
        }
    }
}
