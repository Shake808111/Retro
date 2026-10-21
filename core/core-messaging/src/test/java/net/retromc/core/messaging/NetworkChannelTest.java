package net.retromc.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NetworkChannelTest {

    @Test
    void kanaeleEntsprechenNotiz07() {
        Set<String> keys = Arrays.stream(NetworkChannel.values()).map(NetworkChannel::key).collect(Collectors.toSet());
        assertEquals(Set.of("net:chat", "net:social", "net:moderation", "net:queue", "net:server-state"), keys);
        assertTrue(keys.stream().allMatch(key -> key.startsWith("net:")));
    }
}
