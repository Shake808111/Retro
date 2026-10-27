package net.retromc.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.UUID;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatPeriod;
import net.retromc.core.data.testing.TestDatabases;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Valkey-Container der testFixtures: Verbindung, Bestenliste unter {@code lb:{mode}:{period}}, Leeren. */
@Tag("integration")
class ValkeyIT {

    @Test
    void bestenlisteRundlauf() {
        TestDatabases.flushValkey();
        String key = ValkeyKeys.leaderboard(ModeKey.BEDWARS, StatPeriod.DAYS_30);
        String player = UUID.randomUUID().toString();
        try (ValkeyConnector connector = TestDatabases.valkeyConnector();
                StatefulRedisConnection<String, String> connection = connector.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            commands.zincrby(key, 12.5, player);
            commands.zincrby(key, 2.5, player);
            assertEquals(15.0, commands.zscore(key, player));
            TestDatabases.flushValkey();
            assertTrue(commands.keys("lb:*").isEmpty());
        }
    }
}
