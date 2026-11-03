package net.retromc.platform.common.social;

import java.util.Objects;
import java.util.function.Supplier;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.event.Broadcast;
import net.retromc.core.api.event.ClanChat;
import net.retromc.core.api.event.ClanUpdate;
import net.retromc.core.api.event.Kick;
import net.retromc.core.api.event.LeaderboardChanged;
import net.retromc.core.api.event.MaintenanceChanged;
import net.retromc.core.api.event.MuteUpdate;
import net.retromc.core.api.event.PartyChat;
import net.retromc.core.api.event.PartyDisband;
import net.retromc.core.api.event.PartyUpdate;
import net.retromc.core.api.event.PartyWarp;
import net.retromc.core.api.event.NickChanged;
import net.retromc.core.api.event.PrivateMessage;
import net.retromc.core.api.event.ProfileChanged;
import net.retromc.core.api.event.ProfileInvalidate;
import net.retromc.core.api.event.PunishIssued;
import net.retromc.core.api.event.PunishRevoked;
import net.retromc.core.api.event.ReportClosed;
import net.retromc.core.api.event.ReportNew;
import net.retromc.core.api.event.StaffChat;
import net.retromc.core.api.event.StatsUpdated;
import net.retromc.core.api.social.PartyInvite;
import net.retromc.core.data.EventSink;
import net.retromc.core.messaging.MessageBus;
import net.retromc.core.messaging.MessageType;
import org.slf4j.Logger;

/**
 * Verbindet die {@link EventSink} der Repositories mit dem Nachrichtenbus ({@code net:*}, Spec 01 §2.4, §8.2). Der
 * Bus kommt bei jedem Ereignis frisch aus der Service-Registry; Sendefehler werden nur protokolliert.
 */
public final class BusEventSink implements EventSink {

    private final Supplier<MessageBus> bus;
    private final Logger log;

    public BusEventSink(Supplier<MessageBus> bus, Logger log) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void publish(Object event) {
        switch (event) {
            case PunishIssued payload -> send(MessageType.PUNISH_ISSUED, payload);
            case PunishRevoked payload -> send(MessageType.PUNISH_REVOKED, payload);
            case MuteUpdate payload -> send(MessageType.MUTE_UPDATE, payload);
            case Kick payload -> send(MessageType.KICK, payload);
            case ReportNew payload -> send(MessageType.REPORT_NEW, payload);
            case ReportClosed payload -> send(MessageType.REPORT_CLOSED, payload);
            case MaintenanceChanged payload -> send(MessageType.MAINTENANCE_CHANGED, payload);
            case NickChanged payload -> send(MessageType.NICK_CHANGED, payload);
            case ClanUpdate payload -> send(MessageType.CLAN_UPDATE, payload);
            case ProfileInvalidate payload -> send(MessageType.PROFILE_INVALIDATE, payload);
            case ProfileChanged payload -> send(MessageType.PROFILE_CHANGED, payload);
            case PrivateMessage payload -> send(MessageType.PRIVATE_MSG, payload);
            case ClanChat payload -> send(MessageType.CLAN_CHAT, payload);
            case StaffChat payload -> send(MessageType.STAFF_CHAT, payload);
            case Broadcast payload -> send(MessageType.BROADCAST, payload);
            case AfkChanged payload -> send(MessageType.AFK_CHANGED, payload);
            case PartyUpdate payload -> send(MessageType.PARTY_UPDATE, payload);
            case PartyDisband payload -> send(MessageType.PARTY_DISBAND, payload);
            case PartyInvite payload -> send(MessageType.PARTY_INVITE, payload);
            case PartyWarp payload -> send(MessageType.PARTY_WARP, payload);
            case PartyChat payload -> send(MessageType.PARTY_CHAT, payload);
            case StatsUpdated payload -> send(MessageType.STATS_UPDATED, payload);
            case LeaderboardChanged payload -> send(MessageType.LEADERBOARD_CHANGED, payload);
            default -> throw new IllegalArgumentException("Kein Nachrichtentyp für " + event.getClass().getName());
        }
    }

    private <P> void send(MessageType<P> type, P payload) {
        try {
            bus.get().publish(type, payload).whenComplete((envelope, error) -> {
                if (error != null) {
                    log.warn("Ereignis {} nicht gesendet", type, error);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Ereignis {} nicht gesendet", type, e);
        }
    }
}
