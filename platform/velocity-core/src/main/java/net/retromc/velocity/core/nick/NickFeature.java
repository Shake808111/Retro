package net.retromc.velocity.core.nick;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.event.NickChanged;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import net.retromc.core.api.social.Nick;
import net.retromc.core.api.social.NickService;
import net.retromc.core.api.social.PlayerRef;
import net.retromc.core.api.social.PrivacySettings;
import net.retromc.core.api.social.Profile;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.social.Setting;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.velocity.core.VelocityCore;
import net.retromc.velocity.core.command.CommandName;
import net.retromc.velocity.core.command.CommandService;
import net.retromc.velocity.core.social.Social;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.jspecify.annotations.Nullable;

/**
 * Nicks auf dem Proxy (Spec 01 §3.5 #69–72, §6.11): {@code /nick}, {@code /unnick}, {@code /nicks},
 * {@code /keepnick}; Zuweisung beim Betreten eines GAME-Servers mit {@code nick_on_join}, Freigabe beim Wechsel auf
 * andere Server ohne {@code nick_keep} und beim Logout. {@code NICK_CHANGED} meldet paper-core, welchen Namen und
 * Skin es setzen soll; Stats und Reports laufen weiter auf die echte UUID.
 */
public final class NickFeature implements CoreFeature<VelocityCore> {

    public static final CommandName NICK = CommandName.of("nick");
    public static final CommandName UNNICK = CommandName.of("unnick");
    public static final CommandName NICKS = CommandName.of("nicks");
    public static final CommandName KEEPNICK = CommandName.of("keepnick");
    public static final List<CommandName> COMMANDS = List.of(NICK, UNNICK, NICKS, KEEPNICK);

    /** Nick-Recht (Premium+, Supremium, Supremium+, VIP, Team; Spec 01 §6.2). */
    public static final String PERMISSION = Social.permission("nick");

    private @Nullable Social social;

    @Override
    public String name() {
        return "Nick";
    }

    @Override
    public void enable(VelocityCore core) {
        Social helper = new Social(core);
        social = helper;
        VelocityCommandManager<CommandSource> manager = core.services().get(CommandService.class).manager();
        manager.command(NICK.builder(manager).senderType(Player.class).permission(PERMISSION)
                .handler(ctx -> nick(ctx.sender())));
        manager.command(UNNICK.builder(manager).senderType(Player.class).permission(PERMISSION)
                .handler(ctx -> unnick(ctx.sender())));
        manager.command(NICKS.builder(manager).senderType(Player.class).permission(PERMISSION)
                .handler(ctx -> nicks(ctx.sender())));
        manager.command(KEEPNICK.builder(manager).senderType(Player.class).permission(PERMISSION)
                .handler(ctx -> keep(ctx.sender())));
        core.proxy().getEventManager().register(core.plugin(), this);
    }

    @Override
    public void disable(VelocityCore core) {
        core.proxy().getEventManager().unregisterListener(core.plugin(), this);
        social = null;
    }

    private Social social() {
        Social current = social;
        if (current == null) {
            throw new IllegalStateException("Nick ist nicht aktiv");
        }
        return current;
    }

    /** Laufende Runde: Nick nur in der Wartephase umschaltbar (Spec 01 §6.11 Nr. 5). */
    static boolean roundRunning(Optional<ServerInfo> info) {
        return info.map(ServerInfo::state).filter(state -> state == ServerState.INGAME || state == ServerState.ENDING)
                .isPresent();
    }

    void nick(Player player) {
        Social helper = social();
        Optional<String> server = Social.serverName(player);
        ServerCategory category = server.map(helper::category).orElse(ServerCategory.LOBBY);
        if (category != ServerCategory.GAME) {
            helper.then(player, helper.get(SettingsService.class).cycle(player.getUniqueId(), Setting.NICK_ON_JOIN),
                    settings -> helper.send(player, settings.nickOnJoin() ? "nick.auto.on" : "nick.auto.off"));
            return;
        }
        if (roundRunning(helper.registryInfo(server.get()))) {
            helper.send(player, "nick.err.phase");
            return;
        }
        NickService nicks = helper.get(NickService.class);
        helper.then(player, nicks.nickOf(player.getUniqueId()), current -> {
            if (current.isPresent()) {
                release(player, server.get(), true);
            } else {
                assign(player, server.get(), true);
            }
        });
    }

    void unnick(Player player) {
        Social helper = social();
        helper.then(player, helper.get(NickService.class).nickOf(player.getUniqueId()), current -> {
            if (current.isEmpty()) {
                helper.send(player, "nick.err.none");
            } else {
                release(player, Social.serverName(player).orElse(""), true);
            }
        });
    }

    void nicks(Player player) {
        Social helper = social();
        NickService nicks = helper.get(NickService.class);
        helper.then(player, helper.isTeam(player.getUniqueId()), team -> {
            if (!team) {
                helper.then(player, nicks.nickOf(player.getUniqueId()), nick -> {
                    if (nick.isEmpty()) {
                        helper.send(player, "nick.err.none");
                    } else {
                        helper.send(player, "nick.list.self", Social.unparsed("original", player.getUsername()),
                                Social.unparsed("nick", nick.get().name()));
                    }
                });
                return;
            }
            List<Player> sameServer = new ArrayList<>(Social.serverName(player)
                    .flatMap(name -> helper.core().proxy().getServer(name))
                    .map(server -> List.copyOf(server.getPlayersConnected())).orElse(List.of()));
            if (sameServer.stream().noneMatch(other -> other.getUniqueId().equals(player.getUniqueId()))) {
                sameServer.add(player);
            }
            helper.then(player, nicks.nicks(sameServer.stream().map(Player::getUniqueId).toList()), map -> {
                List<PlayerRef> refs = sameServer.stream().map(other -> new PlayerRef(other.getUniqueId(),
                        other.getUsername())).toList();
                for (NickLine line : teamLines(new PlayerRef(player.getUniqueId(), player.getUsername()), refs, map)) {
                    helper.send(player, line.key(), Social.unparsed("original", line.original()),
                            Social.unparsed("nick", line.nick()));
                }
            });
        });
    }

    /** Eine Zeile von {@code /nicks}. */
    record NickLine(String key, String original, String nick) {
    }

    /**
     * {@code /nicks} für das Team (Spec 01 §6.11 Nr. 6, Recherche: „[NICK] Original: Nick“ für sich selbst, darunter
     * „[NICK] - Original: Nick“ je genicktem Spieler des Servers): eigene Zeile zuerst, dann alphabetisch nach
     * Originalnamen; ohne Nicks {@code nick.err.none}.
     */
    static List<NickLine> teamLines(PlayerRef viewer, List<PlayerRef> sameServer, Map<UUID, Nick> nicks) {
        List<NickLine> lines = new ArrayList<>();
        Nick own = nicks.get(viewer.uuid());
        if (own != null) {
            lines.add(new NickLine("nick.list.self", viewer.name(), own.name()));
        }
        sameServer.stream().filter(other -> !other.uuid().equals(viewer.uuid()) && nicks.containsKey(other.uuid()))
                .sorted(Comparator.comparing(PlayerRef::name, String.CASE_INSENSITIVE_ORDER))
                .forEach(other -> lines.add(new NickLine("nick.list.line", other.name(),
                        nicks.get(other.uuid()).name())));
        if (lines.isEmpty()) {
            lines.add(new NickLine("nick.err.none", "", ""));
        }
        return List.copyOf(lines);
    }

    void keep(Player player) {
        Social helper = social();
        helper.then(player, helper.get(SettingsService.class).cycle(player.getUniqueId(), Setting.NICK_KEEP),
                settings -> helper.send(player, settings.nickKeep() ? "nick.keep.on" : "nick.keep.off"));
    }

    /**
     * Nick aus dem Pool. {@code manual} ({@code /nick} in der Wartephase): Antwort {@code nick.current}, leerer Pool
     * {@code nick.err.pool}; automatisch beim Rundenbeitritt ({@code nick_on_join}): {@code nick.auto.set}
     * („Der automatische Nickname wurde auf … festgelegt“, Recherche N15/N17).
     */
    private void assign(Player player, String server, boolean manual) {
        Social helper = social();
        NickService nicks = helper.get(NickService.class);
        helper.then(player, nicks.assign(player.getUniqueId(), ServerCategory.GAME), nick -> {
            if (nick.isEmpty()) {
                if (manual) {
                    helper.send(player, "nick.err.pool");
                } else {
                    helper.core().log().warn("Nick-Pool leer: kein automatischer Nick für {}", player.getUsername());
                }
                return;
            }
            publish(player.getUniqueId(), nick.get().name(), server);
            helper.send(player, manual ? "nick.current" : "nick.auto.set", Social.unparsed("nick",
                    nick.get().name()));
            helper.log(nicks.freeCount().thenAccept(free -> {
                if (free < NickService.POOL_WARN_FREE) {
                    helper.core().log().warn("Nick-Pool: nur noch {} freie Namen", free);
                }
            }), "Nick-Pool prüfen");
        });
    }

    private void release(Player player, String server, boolean reply) {
        Social helper = social();
        helper.then(player, helper.get(NickService.class).release(player.getUniqueId()), ignored -> {
            publish(player.getUniqueId(), null, server);
            if (reply) {
                helper.send(player, "nick.removed");
            }
        });
    }

    private void publish(UUID player, @Nullable String nick, String server) {
        Social helper = social();
        helper.log(helper.get(MessageBus.class).publish(MessageType.NICK_CHANGED,
                new NickChanged(player, nick, server)), "NICK_CHANGED senden");
        helper.log(helper.get(ProfileService.class).invalidate(player, List.of(Profile.NICK_NAME)),
                "Profil nach Nick-Wechsel");
    }

    /**
     * Serverwechsel: auf GAME-Servern Nick nach {@code nick_on_join} (vorhandener bleibt), sonst ohne
     * {@code nick_keep} freigeben (Spec 01 §6.11 Nr. 2–4).
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Social helper = social;
        if (helper == null) {
            return;
        }
        Player player = event.getPlayer();
        String server = event.getServer().getServerInfo().getName();
        boolean fromOtherGame = event.getPreviousServer()
                .map(previous -> previous.getServerInfo().getName())
                .filter(previous -> !previous.equals(server) && helper.category(previous) == ServerCategory.GAME)
                .isPresent();
        ServerCategory category = helper.category(server);
        helper.log(helper.get(SettingsService.class).settings(player.getUniqueId())
                .thenCombine(helper.get(NickService.class).nickOf(player.getUniqueId()), Current::new)
                .thenAccept(current -> onSwitch(player, server, category, fromOtherGame, current)),
                "Nick beim Serverwechsel");
    }

    /**
     * Regeln beim Serverwechsel (Spec 01 §6.11 Nr. 2–4): nach einer Runde ohne {@code nick_keep} wird der Nick
     * freigegeben; auf einem GAME-Server erhält ein Spieler mit {@code nick_on_join} einen (neuen) Nick, ein
     * behaltener Nick wird weiter angezeigt; außerhalb von GAME-Servern bleibt nur ein behaltener Nick reserviert.
     */
    private void onSwitch(Player player, String server, ServerCategory category, boolean fromOtherGame,
            Current current) {
        boolean keep = current.settings().nickKeep();
        if (category != ServerCategory.GAME) {
            if (current.nick().isPresent() && !keep) {
                release(player, server, false);
            }
            return;
        }
        if (current.nick().isPresent() && (keep || !fromOtherGame)) {
            publish(player.getUniqueId(), current.nick().get().name(), server);
            social().send(player, "nick.current", Social.unparsed("nick", current.nick().get().name()));
            return;
        }
        Social helper = social();
        CompletableFuture<Void> released = current.nick().isPresent()
                ? helper.get(NickService.class).release(player.getUniqueId())
                : CompletableFuture.completedFuture(null);
        helper.log(released.thenRun(() -> {
            if (current.settings().nickOnJoin() && player.hasPermission(PERMISSION)) {
                assign(player, server, false);
            } else if (current.nick().isPresent()) {
                publish(player.getUniqueId(), null, server);
            }
        }), "Nick nach Rundenwechsel");
    }

    /** Einstellungen und Nick beim Serverwechsel. */
    private record Current(PrivacySettings settings, Optional<Nick> nick) {
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Social helper = social;
        if (helper != null) {
            helper.log(helper.get(NickService.class).release(event.getPlayer().getUniqueId()), "Nick freigeben");
        }
    }
}
