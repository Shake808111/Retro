package net.retromc.core.data.nick;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.social.Nick;
import net.retromc.core.api.social.NickPoolService;
import net.retromc.core.api.social.NickPoolService.FillStatus;
import net.retromc.core.api.social.NickPoolService.State;
import net.retromc.core.data.Jdbc;
import net.retromc.core.data.social.DataFixture;
import net.retromc.core.data.social.NickRepository;
import net.retromc.core.data.testing.TestTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** {@code core.nick_pool}/{@code core.nick_skin} mit echter Datenbank und Valkey, HTTP als Attrappe. */
@Tag("integration")
class NickPoolRepositoryIT {

    private static final UUID SOURCE = UUID.fromString("0a0b0c0d-1e1f-4a2b-8c3d-4e5f6a7b8c9d");
    private DataFixture data;

    @BeforeEach
    void start() {
        data = new DataFixture();
    }

    @AfterEach
    void stop() {
        data.close();
    }

    private final AtomicInteger sessionCalls = new AtomicInteger();
    private final AtomicInteger nameCalls = new AtomicInteger();

    private final HttpTransport http = (uri, headers) -> {
        String last = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
        if (uri.getHost().startsWith("sessionserver")) {
            sessionCalls.incrementAndGet();
            return HttpTransport.Response.of(200, "{\"id\":\"" + last + "\",\"properties\":[{\"name\":\"textures\","
                    + "\"value\":\"wert\",\"signature\":\"signatur\"}]}");
        }
        nameCalls.incrementAndGet();
        return HttpTransport.Response.of(404, "");
    };

    private NickPoolRepository repository(List<Runnable> queue) {
        NickPoolSettings settings = new NickPoolSettings(List.of(SOURCE), true, Duration.ZERO, null, List.of(), 5,
                Set.of());
        return new NickPoolRepository(data.async, data.valkey, name -> false, settings, http, duration -> { },
                data.clock, new SplittableRandom(11), queue == null ? Runnable::run : queue::add);
    }

    @Test
    void fuellenListenSperrenUndZaehlen() {
        NickPoolRepository pool = repository(null);
        NickPoolService.Fill fill = await(pool.fill(12));
        assertEquals(FillStatus.DONE, fill.status());
        assertEquals(12, fill.added());
        assertEquals(1, fill.skinsAdded());
        assertEquals(1, sessionCalls.get());
        assertEquals(12, nameCalls.get());
        assertEquals(12, TestTransactions.count(data.database.dataSource(), "SELECT count(*) FROM core.nick_pool"
                + " WHERE source = 'GENERATED' AND skin_id IS NOT NULL AND texture_value = 'wert'"
                + " AND texture_signature = 'signatur'"));

        // Skins werden nicht erneut geholt; Namen bleiben eindeutig.
        assertEquals(5, await(pool.fill(5)).added());
        assertEquals(1, sessionCalls.get());
        List<NickPoolService.Entry> entries = await(pool.list(0, 100));
        assertEquals(17, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.state() == State.FREE));
        assertEquals(entries.stream().map(entry -> entry.name().toLowerCase()).sorted().toList(),
                entries.stream().map(entry -> entry.name().toLowerCase()).toList());
        assertEquals(5, await(pool.list(12, 100)).size());

        // Vergabe über den NickService: Name gilt als vergeben.
        NickRepository nicks = new NickRepository(data.async, data.valkey, name -> false, data.clock);
        UUID player = data.player("NickPoolSpieler");
        Nick nick = await(nicks.assign(player, ServerCategory.GAME)).orElseThrow();
        assertEquals("wert", nick.texture());
        NickPoolService.Stats stats = await(pool.stats());
        assertEquals(new NickPoolService.Stats(17, 17, 16, 1, 1), stats);
        assertTrue(stats.low());
        assertTrue(await(pool.list(0, 100)).stream().anyMatch(entry -> entry.name().equals(nick.name())
                && entry.state() == State.IN_USE));

        // Sperren: nicht mehr vergeben, gezählt als gesperrt.
        String other = entries.stream().map(NickPoolService.Entry::name).filter(name -> !name.equals(nick.name()))
                .findFirst().orElseThrow();
        assertTrue(await(pool.disable(other.toUpperCase())));
        assertFalse(await(pool.disable("GibtEsNicht")));
        assertFalse(await(pool.disable("ungültig!")));
        assertEquals(16, await(pool.stats()).enabled());
        assertTrue(await(pool.list(0, 100)).stream().anyMatch(entry -> entry.name().equals(other)
                && entry.state() == State.DISABLED));
        await(nicks.release(player));
    }

    @Test
    void bekannteNamenAusSpielerHistorieUndPool() {
        NickPoolRepository pool = repository(null);
        data.player("EchterSpieler");
        UUID renamed = data.player("NeuerName");
        TestTransactions.committed(data.database.dataSource(), connection -> Jdbc.update(connection,
                "INSERT INTO core.player_name_history (uuid, name, seen_at) VALUES (?, 'AlterName', now())",
                renamed));
        pool.addSkins("MANUAL", List.of(new SkinTexture("hand", "v", "s")));
        assertTrue(pool.addName("PoolName", pool.enabledSkins().getFirst()));
        assertEquals(Set.of("echterspieler", "altername", "poolname"),
                pool.knownNames(List.of("echterspieler", "altername", "poolname", "frei123")));
        assertFalse(pool.addName("POOLNAME", pool.enabledSkins().getFirst()), "case-insensitiv eindeutig");
    }

    @Test
    void nurEinFuellvorgangGleichzeitig() {
        List<Runnable> queue = new ArrayList<>();
        NickPoolRepository pool = repository(queue);
        var first = pool.fill(2);
        assertTrue(pool.filling());
        assertEquals(FillStatus.BUSY, await(pool.fill(2)).status());
        queue.forEach(Runnable::run);
        assertEquals(FillStatus.DONE, await(first).status());
        assertFalse(pool.filling());
        assertTrue(pool.fill(0).isCompletedExceptionally());
        assertTrue(pool.fill(NickPoolService.MAX_FILL + 1).isCompletedExceptionally());
    }
}
