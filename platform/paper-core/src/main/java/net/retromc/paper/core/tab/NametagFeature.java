package net.retromc.paper.core.tab;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.util.Registration;
import net.retromc.core.i18n.Messages;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.event.ProfileLoadedEvent;
import net.retromc.paper.core.event.ProfileUpdatedEvent;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.paper.core.profile.ProfileView;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jspecify.annotations.Nullable;

/**
 * Nametags über Scoreboard-Teams des Haupt-Scoreboards (Spec 02 §4.4, §6.2, §8.1): ein Team je Spieler mit
 * Rangfarbe, Präfix {@code {rankColor}{prefix} §7| } und Clan-Tag als Suffix; genickt Premium ohne Clan-Tag. Der
 * Teamname {@code "%04d%s"} ({@code 9999 − Gewicht}, Name) sortiert die Tablist älterer Clients (ViaVersion).
 */
public final class NametagFeature implements CoreFeature<PaperCore>, Listener, NametagService {

    private final Map<UUID, String> teams = new ConcurrentHashMap<>();
    private final Map<UUID, Component> prefixes = new ConcurrentHashMap<>();
    private final Map<UUID, Component> suffixes = new ConcurrentHashMap<>();
    private @Nullable PaperCore core;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Nametags";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registration = context.services().registerDefault(NametagService.class, this);
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        teams.values().forEach(name -> Optional.ofNullable(scoreboard.getTeam(name)).ifPresent(Team::unregister));
        teams.clear();
        prefixes.clear();
        suffixes.clear();
        core = null;
    }

    @Override
    public void prefix(Player player, Component prefix) {
        prefixes.put(player.getUniqueId(), prefix);
        apply(player);
    }

    @Override
    public void suffix(Player player, Component suffix) {
        suffixes.put(player.getUniqueId(), suffix);
        apply(player);
    }

    @Override
    public void reset(Player player) {
        prefixes.remove(player.getUniqueId());
        suffixes.remove(player.getUniqueId());
        apply(player);
    }

    /** Legt das Team des Spielers (neu) an; ein geänderter Name oder Rang wechselt das Team. */
    void apply(Player player) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Optional<ProfileView> view = context.services().find(PlayerProfiles.class).flatMap(p -> p.view(uuid));
        RankDisplay rank = view.map(ProfileView::displayRank).orElseGet(() -> RankDisplay.of(Rank.DEFAULT));
        I18n i18n = context.services().get(I18n.class);
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = NametagService.teamName(rank.weight(), player.getName());
        String old = teams.put(uuid, name);
        if (old != null && !old.equals(name)) {
            Optional.ofNullable(scoreboard.getTeam(old)).ifPresent(Team::unregister);
        }
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        team.color(NamedTextColor.nearestTo(ColorText.color(rank.color())));
        team.prefix(prefixes.getOrDefault(uuid, TabFormat.rankPrefix(i18n, Messages.DEFAULT_LOCALE, rank)));
        team.suffix(suffixes.getOrDefault(uuid, TabFormat.clanSuffix(i18n, Messages.DEFAULT_LOCALE,
                view.map(ProfileView::clanTag).orElse(null))));
        for (String entry : team.getEntries()) {
            if (!entry.equals(player.getName())) {
                team.removeEntry(entry);
            }
        }
        team.addEntry(player.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onLoaded(ProfileLoadedEvent event) {
        apply(event.player());
    }

    @EventHandler
    public void onUpdated(ProfileUpdatedEvent event) {
        apply(event.player());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        prefixes.remove(uuid);
        suffixes.remove(uuid);
        String name = teams.remove(uuid);
        if (name != null) {
            Optional.ofNullable(Bukkit.getScoreboardManager().getMainScoreboard().getTeam(name))
                    .ifPresent(Team::unregister);
        }
    }
}
