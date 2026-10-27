package net.retromc.core.messaging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.retromc.core.api.punish.PunishmentType;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.server.ServerInfo;
import net.retromc.core.api.server.ServerState;
import net.retromc.core.api.social.PartyInvite;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.messaging.payload.ConnectMany;
import net.retromc.core.messaging.payload.CtlLine;
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

/** Je Nachrichtentyp eine Beispiel-Nutzlast (Zeitpunkte auf Millisekunden genau, wie im JSON). */
final class Samples {

    static final UUID A = UUID.fromString("0f2b6c1e-1111-4222-8333-944455556666");
    static final UUID B = UUID.fromString("ffffffff-0000-4000-8000-000000000001");
    static final Instant TS = Instant.ofEpochMilli(1_761_550_000_123L);
    static final String PARTY = "01JB7Z3Y8Q4M5N6P7R8S9T0V1W";

    private Samples() {
    }

    static ServerInfo server(ServerState state) {
        return new ServerInfo("bedwars-4x4-12", "bedwars-4x4", "bedwars", "4x4", ServerCategory.GAME, state, 3, 16, 2,
                "farmland", "net-bedwars-4x4-12", 25565, 774, false, 0, false, "BedWars", "1.21.11+", "m-77", TS);
    }

    static Map<MessageType<?>, Object> all() {
        Map<MessageType<?>, Object> samples = new LinkedHashMap<>();
        samples.put(MessageType.FRIEND_ONLINE, new FriendStatus(A, FriendStatus.Status.ONLINE, List.of(B)));
        samples.put(MessageType.FRIEND_OFFLINE, new FriendStatus(A, FriendStatus.Status.OFFLINE, List.of(B)));
        samples.put(MessageType.FRIEND_SPECTATE, new FriendStatus(A, FriendStatus.Status.SPECTATE, List.of()));
        samples.put(MessageType.FRIEND_REQUEST, new FriendNotice(A, B));
        samples.put(MessageType.FRIEND_ADDED, new FriendNotice(B, A));
        samples.put(MessageType.FRIEND_REMOVED, new FriendNotice(A, B));
        samples.put(MessageType.PARTY_UPDATE, new PartyUpdate(PARTY, A, List.of(A, B), true));
        samples.put(MessageType.PARTY_INVITE, new PartyInvite(PARTY, A, B, TS.plusSeconds(60)));
        samples.put(MessageType.PARTY_DISBAND, new PartyDisband(PARTY, List.of(A, B)));
        samples.put(MessageType.PARTY_WARP, new PartyWarp(PARTY, "bedwars-4x4-12"));
        samples.put(MessageType.CLAN_UPDATE, new ClanUpdate(42, "3IX", List.of(A)));
        samples.put(MessageType.CLAN_ONLINE, new ClanMemberStatus(A, 42, List.of(B)));
        samples.put(MessageType.CLAN_OFFLINE, new ClanMemberStatus(A, 42, List.of()));
        samples.put(MessageType.PROFILE_INVALIDATE, new ProfileInvalidate(A, List.of("clanTag", "clanId")));
        samples.put(MessageType.NICK_CHANGED, new NickChanged(A, "Tarnname", "bedwars-4x4-12"));
        samples.put(MessageType.AFK_CHANGED, new AfkChanged(A, true));
        samples.put(MessageType.PLAYER_NETWORK_JOIN, new PlayerNetworkJoin(A, "Spieler_1", "proxy-1"));
        samples.put(MessageType.PLAYER_NETWORK_QUIT, new PlayerNetworkQuit(A, "Spieler_1", "proxy-1"));
        samples.put(MessageType.GLOBAL_CHAT, new GlobalChat(A, "lobby-1", "Hallo \"alle\" <3 äöü", 17));
        samples.put(MessageType.PRIVATE_MSG, new PrivateMessage(A, List.of(B), "na?"));
        samples.put(MessageType.PARTY_CHAT, new PartyChat(A, List.of(A, B), "los"));
        samples.put(MessageType.CLAN_CHAT, new ClanChat(A, List.of(B), "cw?", true));
        samples.put(MessageType.STAFF_CHAT, new StaffChat(A, List.of(B), "schau mal"));
        samples.put(MessageType.BROADCAST, new Broadcast("joinme.broadcast",
                Map.of("name", "Spieler_1", "mode", "BedWars", "server", "bedwars-4x4-12"),
                "/server bedwars-4x4-12", true));
        samples.put(MessageType.PUNISH_ISSUED, new PunishIssued("aZ3kL9mN0pQ", A, PunishmentType.BAN,
                TS.plusSeconds(86_400)));
        samples.put(MessageType.PUNISH_REVOKED, new PunishRevoked("aZ3kL9mN0pQ", A, PunishmentType.BAN, null));
        samples.put(MessageType.MUTE_UPDATE, new MuteUpdate("AB12CD34", A, PunishmentType.MUTE, null));
        samples.put(MessageType.REPORT_NEW, new ReportNew("RP12AB34", A, B, "hacking", "bedwars-4x4-12"));
        samples.put(MessageType.REPORT_CLOSED, new ReportClosed("RP12AB34", A, B, "hacking", "bedwars-4x4-12", true));
        samples.put(MessageType.KICK, new Kick(B, "Bitte neu verbinden"));
        samples.put(MessageType.SERVER_UP, server(ServerState.BOOTING));
        samples.put(MessageType.SERVER_STATE, server(ServerState.STARTING));
        samples.put(MessageType.SERVER_DOWN, new ServerDown("bedwars-4x4-12", "MATCH_END"));
        samples.put(MessageType.PROXY_UP, new ProxyStatus("proxy-1"));
        samples.put(MessageType.PROXY_DOWN, new ProxyStatus("proxy-1"));
        samples.put(MessageType.MAINTENANCE_CHANGED, new MaintenanceChanged(true));
        samples.put(MessageType.RUN_PROXY_COMMAND, new RunProxyCommand(A, "party promote Spieler_2"));
        samples.put(MessageType.CONNECT, new Connect(A, "lobby-2"));
        samples.put(MessageType.CONNECT_MANY, new ConnectMany(List.of(A, B), "bedwars-4x4-12"));
        samples.put(MessageType.SEND_LOBBY, new SendLobby(List.of(A, B), "server.stopping.moved", "bedwars"));
        samples.put(MessageType.CTL_REQUEST, new CtlRequest(A, "start", List.of("bedwars-4x4", "2")));
        samples.put(MessageType.CTL_REPLY, new CtlReply(true, List.of(
                new CtlLine("cloud.start.ok", Map.of("count", "2", "template", "bedwars-4x4")))));
        samples.put(MessageType.SHUTDOWN, new ServerCommand("bedwars-4x4-12", "ADMIN"));
        samples.put(MessageType.DRAIN, new ServerCommand("lobby-3", "IDLE"));
        samples.put(MessageType.RESTART_SCHEDULE, new RestartSchedule("cb-plot-1", TS.plusSeconds(300), "DAILY"));
        samples.put(MessageType.RESTART_CANCEL, new RestartSchedule("cb-plot-1", null, null));
        samples.put(MessageType.RELOAD_TEMPLATES, new ReloadTemplates());
        samples.put(MessageType.MAPS_CHANGED, new MapsChanged("bedwars"));
        samples.put(MessageType.QUEUE_JOIN, new QueueTicket("t-1", List.of(A, B), PARTY, "bedwars", "4x4", null));
        samples.put(MessageType.QUEUE_LEAVE, new QueueTicket("t-1", List.of(A), null, "duels", "uhc", 1234));
        samples.put(MessageType.MATCH_ASSIGNED, new MatchAssigned("t-1", "bedwars-4x4-12", "m-77", null));
        samples.put(MessageType.QUEUE_FAILED, new QueueFailed("t-1", List.of(A, B), QueueFailed.Reason.PARTY_TOO_BIG));
        samples.put(MessageType.DISCORD_LINKED, new DiscordLink(A, 123_456_789_012_345_678L));
        samples.put(MessageType.DISCORD_UNLINKED, new DiscordLink(A, 123_456_789_012_345_678L));
        samples.put(MessageType.ROLE_SYNC_REQUEST, new RoleSyncRequest(A));
        samples.put(MessageType.PROFILE_CHANGED, new ProfileChanged(A, List.of(ProfileChanged.COINS)));
        samples.put(MessageType.STATS_UPDATED, new StatsUpdated(ModeKey.BEDWARS, List.of(A, B)));
        samples.put(MessageType.LEADERBOARD_CHANGED, new LeaderboardChanged(ModeKey.SKYWARS, StatPeriod.DAYS_30));
        return samples;
    }
}
