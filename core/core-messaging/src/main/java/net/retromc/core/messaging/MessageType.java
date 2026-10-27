package net.retromc.core.messaging;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.event.Broadcast;
import net.retromc.core.api.event.ClanChat;
import net.retromc.core.api.event.ClanMemberStatus;
import net.retromc.core.api.event.ClanUpdate;
import net.retromc.core.api.event.Connect;
import net.retromc.core.api.event.FriendNotice;
import net.retromc.core.api.event.FriendStatus;
import net.retromc.core.api.event.GlobalChat;
import net.retromc.core.api.event.Kick;
import net.retromc.core.api.event.LeaderboardChanged;
import net.retromc.core.api.event.MaintenanceChanged;
import net.retromc.core.api.event.MuteUpdate;
import net.retromc.core.api.event.NickChanged;
import net.retromc.core.api.event.PartyChat;
import net.retromc.core.api.event.PartyDisband;
import net.retromc.core.api.event.PartyUpdate;
import net.retromc.core.api.event.PartyWarp;
import net.retromc.core.api.event.PlayerNetworkJoin;
import net.retromc.core.api.event.PlayerNetworkQuit;
import net.retromc.core.api.event.PrivateMessage;
import net.retromc.core.api.event.ProfileChanged;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.event.PunishIssued;
import net.retromc.core.api.event.PunishRevoked;
import net.retromc.core.api.event.ReportClosed;
import net.retromc.core.api.event.ReportNew;
import net.retromc.core.api.event.RunProxyCommand;
import net.retromc.core.api.event.StaffChat;
import net.retromc.core.api.event.StatsUpdated;
import net.retromc.core.api.msg.Envelope;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.social.PartyInvite;
import net.retromc.core.messaging.payload.ConnectMany;
import net.retromc.core.messaging.payload.CtlReply;
import net.retromc.core.messaging.payload.CtlRequest;
import net.retromc.core.messaging.payload.DiscordLink;
import net.retromc.core.messaging.payload.MapsChanged;
import net.retromc.core.messaging.payload.MatchAssigned;
import net.retromc.core.messaging.payload.ProxyStatus;
import net.retromc.core.messaging.payload.QueueFailed;
import net.retromc.core.messaging.payload.QueueTicket;
import net.retromc.core.messaging.payload.ReloadTemplates;
import net.retromc.core.messaging.payload.RestartSchedule;
import net.retromc.core.messaging.payload.RoleSyncRequest;
import net.retromc.core.messaging.payload.SendLobby;
import net.retromc.core.messaging.payload.ServerCommand;
import net.retromc.core.messaging.payload.ServerDown;

/**
 * Nachrichtentyp: Name im Envelope-Feld {@code t}, Kanal und Nutzlast-Record ({@code d}). Typen aus Spec 01 §2.4/§8.2,
 * Spec 02 §2.2/§8.3 und Spec 08 §2.4/§8.3; Version jeweils 1. Mehrere Typen dürfen dieselbe Nutzlast haben
 * (z. B. {@code FRIEND_ONLINE}/{@code FRIEND_OFFLINE}).
 */
public final class MessageType<P> {

    private static final Map<String, MessageType<?>> BY_NAME = new LinkedHashMap<>();

    // net:social (Spec 01 §2.4, §8.2)
    public static final MessageType<FriendStatus> FRIEND_ONLINE = define("FRIEND_ONLINE", NetworkChannel.SOCIAL,
            FriendStatus.class);
    public static final MessageType<FriendStatus> FRIEND_OFFLINE = define("FRIEND_OFFLINE", NetworkChannel.SOCIAL,
            FriendStatus.class);
    /** Status SPECTATE aus {@code FriendStatus} (Spec 01 §8.2), in §2.4 nicht aufgeführt. */
    public static final MessageType<FriendStatus> FRIEND_SPECTATE = define("FRIEND_SPECTATE", NetworkChannel.SOCIAL,
            FriendStatus.class);
    public static final MessageType<FriendNotice> FRIEND_REQUEST = define("FRIEND_REQUEST", NetworkChannel.SOCIAL,
            FriendNotice.class);
    public static final MessageType<FriendNotice> FRIEND_ADDED = define("FRIEND_ADDED", NetworkChannel.SOCIAL,
            FriendNotice.class);
    public static final MessageType<FriendNotice> FRIEND_REMOVED = define("FRIEND_REMOVED", NetworkChannel.SOCIAL,
            FriendNotice.class);
    public static final MessageType<PartyUpdate> PARTY_UPDATE = define("PARTY_UPDATE", NetworkChannel.SOCIAL,
            PartyUpdate.class);
    public static final MessageType<PartyInvite> PARTY_INVITE = define("PARTY_INVITE", NetworkChannel.SOCIAL,
            PartyInvite.class);
    public static final MessageType<PartyDisband> PARTY_DISBAND = define("PARTY_DISBAND", NetworkChannel.SOCIAL,
            PartyDisband.class);
    public static final MessageType<PartyWarp> PARTY_WARP = define("PARTY_WARP", NetworkChannel.SOCIAL,
            PartyWarp.class);
    public static final MessageType<ClanUpdate> CLAN_UPDATE = define("CLAN_UPDATE", NetworkChannel.SOCIAL,
            ClanUpdate.class);
    public static final MessageType<ClanMemberStatus> CLAN_ONLINE = define("CLAN_ONLINE", NetworkChannel.SOCIAL,
            ClanMemberStatus.class);
    public static final MessageType<ClanMemberStatus> CLAN_OFFLINE = define("CLAN_OFFLINE", NetworkChannel.SOCIAL,
            ClanMemberStatus.class);
    public static final MessageType<ProfileInvalidate> PROFILE_INVALIDATE = define("PROFILE_INVALIDATE",
            NetworkChannel.SOCIAL, ProfileInvalidate.class);
    public static final MessageType<NickChanged> NICK_CHANGED = define("NICK_CHANGED", NetworkChannel.SOCIAL,
            NickChanged.class);
    public static final MessageType<AfkChanged> AFK_CHANGED = define("AFK_CHANGED", NetworkChannel.SOCIAL,
            AfkChanged.class);
    public static final MessageType<PlayerNetworkJoin> PLAYER_NETWORK_JOIN = define("PLAYER_NETWORK_JOIN",
            NetworkChannel.SOCIAL, PlayerNetworkJoin.class);
    public static final MessageType<PlayerNetworkQuit> PLAYER_NETWORK_QUIT = define("PLAYER_NETWORK_QUIT",
            NetworkChannel.SOCIAL, PlayerNetworkQuit.class);

    // net:chat (Spec 01 §2.4, §8.2)
    public static final MessageType<GlobalChat> GLOBAL_CHAT = define("GLOBAL_CHAT", NetworkChannel.CHAT,
            GlobalChat.class);
    public static final MessageType<PrivateMessage> PRIVATE_MSG = define("PRIVATE_MSG", NetworkChannel.CHAT,
            PrivateMessage.class);
    public static final MessageType<PartyChat> PARTY_CHAT = define("PARTY_CHAT", NetworkChannel.CHAT,
            PartyChat.class);
    public static final MessageType<ClanChat> CLAN_CHAT = define("CLAN_CHAT", NetworkChannel.CHAT, ClanChat.class);
    public static final MessageType<StaffChat> STAFF_CHAT = define("STAFF_CHAT", NetworkChannel.CHAT,
            StaffChat.class);
    public static final MessageType<Broadcast> BROADCAST = define("BROADCAST", NetworkChannel.CHAT, Broadcast.class);

    // net:moderation (Spec 01 §2.4, §8.2)
    public static final MessageType<PunishIssued> PUNISH_ISSUED = define("PUNISH_ISSUED", NetworkChannel.MODERATION,
            PunishIssued.class);
    public static final MessageType<PunishRevoked> PUNISH_REVOKED = define("PUNISH_REVOKED",
            NetworkChannel.MODERATION, PunishRevoked.class);
    public static final MessageType<MuteUpdate> MUTE_UPDATE = define("MUTE_UPDATE", NetworkChannel.MODERATION,
            MuteUpdate.class);
    public static final MessageType<ReportNew> REPORT_NEW = define("REPORT_NEW", NetworkChannel.MODERATION,
            ReportNew.class);
    public static final MessageType<ReportClosed> REPORT_CLOSED = define("REPORT_CLOSED", NetworkChannel.MODERATION,
            ReportClosed.class);
    public static final MessageType<Kick> KICK = define("KICK", NetworkChannel.MODERATION, Kick.class);

    // net:server-state (Spec 08 §2.4, §8.3; MAINTENANCE_CHANGED Spec 01)
    public static final MessageType<ServerInfo> SERVER_UP = define("SERVER_UP", NetworkChannel.SERVER_STATE,
            ServerInfo.class);
    public static final MessageType<ServerInfo> SERVER_STATE = define("SERVER_STATE", NetworkChannel.SERVER_STATE,
            ServerInfo.class);
    public static final MessageType<ServerDown> SERVER_DOWN = define("SERVER_DOWN", NetworkChannel.SERVER_STATE,
            ServerDown.class);
    public static final MessageType<ProxyStatus> PROXY_UP = define("PROXY_UP", NetworkChannel.SERVER_STATE,
            ProxyStatus.class);
    public static final MessageType<ProxyStatus> PROXY_DOWN = define("PROXY_DOWN", NetworkChannel.SERVER_STATE,
            ProxyStatus.class);
    public static final MessageType<MaintenanceChanged> MAINTENANCE_CHANGED = define("MAINTENANCE_CHANGED",
            NetworkChannel.SERVER_STATE, MaintenanceChanged.class);

    // net:proxy (Spec 01 §2.4, Spec 08 §2.4)
    public static final MessageType<RunProxyCommand> RUN_PROXY_COMMAND = define("RUN_PROXY_COMMAND",
            NetworkChannel.PROXY, RunProxyCommand.class);
    public static final MessageType<Connect> CONNECT = define("CONNECT", NetworkChannel.PROXY, Connect.class);
    public static final MessageType<ConnectMany> CONNECT_MANY = define("CONNECT_MANY", NetworkChannel.PROXY,
            ConnectMany.class);
    public static final MessageType<SendLobby> SEND_LOBBY = define("SEND_LOBBY", NetworkChannel.PROXY,
            SendLobby.class);

    // net:controller (Spec 08 §2.4, §8.3)
    public static final MessageType<CtlRequest> CTL_REQUEST = define("CTL_REQUEST", NetworkChannel.CONTROLLER,
            CtlRequest.class);
    public static final MessageType<CtlReply> CTL_REPLY = define("CTL_REPLY", NetworkChannel.CONTROLLER,
            CtlReply.class);
    public static final MessageType<ServerCommand> SHUTDOWN = define("SHUTDOWN", NetworkChannel.CONTROLLER,
            ServerCommand.class);
    public static final MessageType<ServerCommand> DRAIN = define("DRAIN", NetworkChannel.CONTROLLER,
            ServerCommand.class);
    public static final MessageType<RestartSchedule> RESTART_SCHEDULE = define("RESTART_SCHEDULE",
            NetworkChannel.CONTROLLER, RestartSchedule.class);
    public static final MessageType<RestartSchedule> RESTART_CANCEL = define("RESTART_CANCEL",
            NetworkChannel.CONTROLLER, RestartSchedule.class);
    public static final MessageType<ReloadTemplates> RELOAD_TEMPLATES = define("RELOAD_TEMPLATES",
            NetworkChannel.CONTROLLER, ReloadTemplates.class);
    public static final MessageType<MapsChanged> MAPS_CHANGED = define("MAPS_CHANGED", NetworkChannel.CONTROLLER,
            MapsChanged.class);

    // net:queue (Spec 08 §2.4, §8.3)
    public static final MessageType<QueueTicket> QUEUE_JOIN = define("QUEUE_JOIN", NetworkChannel.QUEUE,
            QueueTicket.class);
    public static final MessageType<QueueTicket> QUEUE_LEAVE = define("QUEUE_LEAVE", NetworkChannel.QUEUE,
            QueueTicket.class);
    public static final MessageType<MatchAssigned> MATCH_ASSIGNED = define("MATCH_ASSIGNED", NetworkChannel.QUEUE,
            MatchAssigned.class);
    public static final MessageType<QueueFailed> QUEUE_FAILED = define("QUEUE_FAILED", NetworkChannel.QUEUE,
            QueueFailed.class);

    // net:integ (Spec 08 §2.4, §8.3)
    public static final MessageType<DiscordLink> DISCORD_LINKED = define("DISCORD_LINKED", NetworkChannel.INTEG,
            DiscordLink.class);
    public static final MessageType<DiscordLink> DISCORD_UNLINKED = define("DISCORD_UNLINKED", NetworkChannel.INTEG,
            DiscordLink.class);
    public static final MessageType<RoleSyncRequest> ROLE_SYNC_REQUEST = define("ROLE_SYNC_REQUEST",
            NetworkChannel.INTEG, RoleSyncRequest.class);

    // net:profile, net:stats (Spec 02 §2.2, §8.3)
    public static final MessageType<ProfileChanged> PROFILE_CHANGED = define("PROFILE_CHANGED",
            NetworkChannel.PROFILE, ProfileChanged.class);
    public static final MessageType<StatsUpdated> STATS_UPDATED = define("STATS_UPDATED", NetworkChannel.STATS,
            StatsUpdated.class);
    public static final MessageType<LeaderboardChanged> LEADERBOARD_CHANGED = define("LEADERBOARD_CHANGED",
            NetworkChannel.STATS, LeaderboardChanged.class);

    /** Aktuelle Version aller hier definierten Typen. */
    public static final int VERSION = 1;

    private final String name;
    private final NetworkChannel channel;
    private final Class<P> payloadType;
    private final int version;

    private MessageType(String name, NetworkChannel channel, Class<P> payloadType, int version) {
        this.name = name;
        this.channel = channel;
        this.payloadType = payloadType;
        this.version = version;
    }

    private static <P> MessageType<P> define(String name, NetworkChannel channel, Class<P> payloadType) {
        if (!Envelope.TYPE.matcher(name).matches()) {
            throw new IllegalArgumentException("Typname muss GROSS_MIT_UNTERSTRICH sein: " + name);
        }
        MessageType<P> type = new MessageType<>(name, channel, payloadType, VERSION);
        if (BY_NAME.putIfAbsent(name, type) != null) {
            throw new IllegalStateException("Typ doppelt definiert: " + name);
        }
        return type;
    }

    /** Name im Feld {@code t}. */
    public String name() {
        return name;
    }

    public NetworkChannel channel() {
        return channel;
    }

    public Class<P> payloadType() {
        return payloadType;
    }

    /** Version im Feld {@code v}. */
    public int version() {
        return version;
    }

    /** Prüft und castet eine Nutzlast. */
    public P cast(Object payload) {
        return payloadType.cast(Objects.requireNonNull(payload, "payload"));
    }

    /** Typisiert einen dekodierten Envelope dieses Typs. */
    public Envelope<P> typed(Envelope<?> envelope) {
        if (!name.equals(envelope.t())) {
            throw new IllegalArgumentException("Envelope " + envelope.t() + " ist nicht vom Typ " + name);
        }
        return new Envelope<>(envelope.t(), envelope.v(), envelope.id(), envelope.ts(), envelope.src(), envelope.re(),
                cast(envelope.d()));
    }

    public static Optional<MessageType<?>> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /** Alle Typen in Definitionsreihenfolge. */
    public static Collection<MessageType<?>> all() {
        return Collections.unmodifiableCollection(BY_NAME.values());
    }

    /** Typen eines Kanals. */
    public static List<MessageType<?>> on(NetworkChannel channel) {
        return BY_NAME.values().stream().filter(type -> type.channel == channel).toList();
    }

    @Override
    public String toString() {
        return name + "@" + channel.key();
    }
}
