package net.retromc.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NetworkChannelTest {

    @Test
    void kanaeleAusSpec01Spec02UndSpec08() {
        Set<String> keys = Arrays.stream(NetworkChannel.values()).map(NetworkChannel::key).collect(Collectors.toSet());
        assertEquals(Set.of(
                // Spec 01 §2.4
                "net:social", "net:chat", "net:moderation", "net:server-state", "net:proxy",
                // Spec 02 §2.2
                "net:profile", "net:stats",
                // Spec 08 §2.4
                "net:controller", "net:queue", "net:integ",
                "net:private", "net:game", "net:duels", "net:clanwar", "net:cb-chat", "net:cb-events",
                "net:smp-events"), keys);
        assertEquals(NetworkChannel.values().length, keys.size());
        assertTrue(keys.stream().allMatch(key -> key.matches("net:[a-z-]+")));
        assertEquals(Optional.of(NetworkChannel.SERVER_STATE), NetworkChannel.byKey("net:server-state"));
        assertEquals(Optional.empty(), NetworkChannel.byKey("cb:chat"));
    }
}
