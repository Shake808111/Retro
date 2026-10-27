package net.retromc.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.retromc.core.api.event.AfkChanged;
import net.retromc.core.api.msg.Envelope;
import org.junit.jupiter.api.Test;

class MessageTypeTest {

    private static Set<String> names(NetworkChannel channel) {
        return MessageType.on(channel).stream().map(MessageType::name).collect(Collectors.toSet());
    }

    @Test
    void typenJeKanalWieInDenSpecs() {
        assertEquals(Set.of("FRIEND_ONLINE", "FRIEND_OFFLINE", "FRIEND_SPECTATE", "FRIEND_REQUEST", "FRIEND_ADDED",
                "FRIEND_REMOVED", "PARTY_UPDATE", "PARTY_INVITE", "PARTY_DISBAND", "PARTY_WARP", "CLAN_UPDATE",
                "CLAN_ONLINE", "CLAN_OFFLINE", "PROFILE_INVALIDATE", "NICK_CHANGED", "AFK_CHANGED",
                "PLAYER_NETWORK_JOIN", "PLAYER_NETWORK_QUIT"), names(NetworkChannel.SOCIAL));
        assertEquals(Set.of("GLOBAL_CHAT", "PRIVATE_MSG", "PARTY_CHAT", "CLAN_CHAT", "STAFF_CHAT", "BROADCAST"),
                names(NetworkChannel.CHAT));
        assertEquals(Set.of("PUNISH_ISSUED", "PUNISH_REVOKED", "MUTE_UPDATE", "REPORT_NEW", "REPORT_CLOSED", "KICK"),
                names(NetworkChannel.MODERATION));
        assertEquals(Set.of("SERVER_UP", "SERVER_STATE", "SERVER_DOWN", "PROXY_UP", "PROXY_DOWN",
                "MAINTENANCE_CHANGED"), names(NetworkChannel.SERVER_STATE));
        assertEquals(Set.of("RUN_PROXY_COMMAND", "CONNECT", "CONNECT_MANY", "SEND_LOBBY"),
                names(NetworkChannel.PROXY));
        assertEquals(Set.of("CTL_REQUEST", "CTL_REPLY", "SHUTDOWN", "DRAIN", "RESTART_SCHEDULE", "RESTART_CANCEL",
                "RELOAD_TEMPLATES", "MAPS_CHANGED"), names(NetworkChannel.CONTROLLER));
        assertEquals(Set.of("QUEUE_JOIN", "QUEUE_LEAVE", "MATCH_ASSIGNED", "QUEUE_FAILED"),
                names(NetworkChannel.QUEUE));
        assertEquals(Set.of("DISCORD_LINKED", "DISCORD_UNLINKED", "ROLE_SYNC_REQUEST"), names(NetworkChannel.INTEG));
        assertEquals(Set.of("PROFILE_CHANGED"), names(NetworkChannel.PROFILE));
        assertEquals(Set.of("STATS_UPDATED", "LEADERBOARD_CHANGED"), names(NetworkChannel.STATS));
        assertEquals(58, MessageType.all().size());
    }

    @Test
    void namenUndVersionen() {
        for (MessageType<?> type : MessageType.all()) {
            assertTrue(Envelope.TYPE.matcher(type.name()).matches(), type.name());
            assertEquals(1, type.version(), type.name());
            assertSame(type, MessageType.byName(type.name()).orElseThrow());
        }
        assertEquals(Optional.empty(), MessageType.byName("TABLIST_OVERRIDE"), "entfällt laut Integration");
    }

    @Test
    void typisierungPrueftNameUndNutzlast() {
        Envelope<?> raw = new Envelope<>("AFK_CHANGED", 1, UUID.randomUUID(), 1, "lobby-1", null,
                new AfkChanged(new UUID(1, 1), true));
        assertTrue(MessageType.AFK_CHANGED.typed(raw).d().afk());
        assertThrows(IllegalArgumentException.class, () -> MessageType.NICK_CHANGED.typed(raw));
        assertThrows(ClassCastException.class, () -> MessageType.NICK_CHANGED.cast("text"));
    }
}
