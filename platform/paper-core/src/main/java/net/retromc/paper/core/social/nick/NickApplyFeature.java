package net.retromc.paper.core.social.nick;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.retromc.core.api.event.NickChanged;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.social.Nick;
import net.retromc.core.api.social.NickService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
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
 * Nick-Anwendung auf Spielservern (Spec 01 §6.11 Nr. 2–3, O20/O21): Profil mit Nick-Name und -Skin
 * ({@code nick:{uuid}}), Anzeige als Premium (§6) ohne Clan-Tag in Chatname, Tab und Nametag. In Lobbys und auf
 * dauerhaften Servern bleibt der Originalname. Auslöser: Beitritt und {@code NICK_CHANGED}.
 */
public final class NickApplyFeature implements CoreFeature<PaperCore>, Listener {

    /** Scoreboard-Team für die Nametag-Farbe der Nicks. */
    static final String TEAM = "retromc_nick";

    private final Map<UUID, PlayerProfile> originals = new ConcurrentHashMap<>();
    private @Nullable PaperCore core;
    private @Nullable Registration subscription;

    @Override
    public String name() {
        return "Nick";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        if (!applies(context.config().category())) {
            return;
        }
        subscription = context.services().get(MessageBus.class).subscribe(MessageType.NICK_CHANGED,
                envelope -> changed(envelope.d())).join();
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        Registration current = subscription;
        if (current != null) {
            current.close();
        }
        subscription = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            reset(player);
        }
        originals.clear();
        core = null;
    }

    /** Nick nur auf GAME-Servern sichtbar (A23, O21). */
    public static boolean applies(ServerCategory category) {
        return category == ServerCategory.GAME;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        originals.remove(event.getPlayer().getUniqueId());
    }

    private void changed(NickChanged change) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        if (change.nick() == null) {
            context.mainThread().execute(() -> Optional.ofNullable(Bukkit.getPlayer(change.uuid())).ifPresent(
                    this::reset));
        } else {
            load(change.uuid());
        }
    }

    private void load(UUID uuid) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        context.mainThread().accept(context.services().get(NickService.class).nickOf(uuid), "Nick laden", nick -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                return;
            }
            if (nick.isPresent()) {
                apply(player, nick.get());
            } else {
                reset(player);
            }
        });
    }

    void apply(Player player, Nick nick) {
        originals.putIfAbsent(player.getUniqueId(), player.getPlayerProfile().clone());
        PlayerProfile profile = Bukkit.createProfileExact(player.getUniqueId(), nick.name());
        profile.setProperty(new ProfileProperty("textures", nick.texture(), nick.signature()));
        player.setPlayerProfile(profile);
        Component display = Component.text(nick.name(), NamedTextColor.GOLD);
        player.displayName(display);
        player.playerListName(display);
        Team team = team(player.getServer().getScoreboardManager().getMainScoreboard());
        team.addEntry(nick.name());
    }

    void reset(Player player) {
        PlayerProfile original = originals.remove(player.getUniqueId());
        if (original == null) {
            return;
        }
        String nickName = player.getName();
        player.setPlayerProfile(original);
        player.displayName(null);
        player.playerListName(null);
        Team team = player.getServer().getScoreboardManager().getMainScoreboard().getTeam(TEAM);
        if (team != null) {
            team.removeEntry(nickName);
        }
    }

    /** Team mit Premium-Farbe §6 ohne Präfix/Suffix (kein Clan-Tag, O20). */
    private static Team team(Scoreboard scoreboard) {
        Team team = scoreboard.getTeam(TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM);
            team.color(NamedTextColor.GOLD);
        }
        return team;
    }

    /** Anzeigefarbe eines Nicks (Rang Premium, §6). */
    public static String displayColor() {
        return Rank.PREMIUM.color();
    }
}
