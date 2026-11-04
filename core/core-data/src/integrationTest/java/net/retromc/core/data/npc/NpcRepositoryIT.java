package net.retromc.core.data.npc;

import static net.retromc.core.data.testing.TestExecutors.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.retromc.core.api.npc.NpcStore.StoredNpc;
import net.retromc.core.data.social.DataFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Dauerhafte NPCs aus {@code /npc create} ({@code core.npc}, {@code V2_3}): überleben einen Neustart. */
class NpcRepositoryIT {

    DataFixture fixture;
    NpcRepository store;

    @BeforeEach
    void setUp() {
        fixture = new DataFixture();
        store = new NpcRepository(fixture.async);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void npcsUeberlebenDenNeustart() {
        UUID admin = UUID.randomUUID();
        StoredNpc withSkin = new StoredNpc(0, "lobby", "world", 10.5, 64, -3.25, 90f, 10f, "<gold>Händler",
                "Steve", "dGV4dHVyZXM=", "c2lnbmF0dXJl", true, admin);
        StoredNpc plain = new StoredNpc(0, "lobby", "world", 0, 70, 0, 0f, 0f, null, null, null, null, false, null);
        StoredNpc other = new StoredNpc(0, "lobby-bedwars", "world", 1, 2, 3, 0f, 0f, null, null, null, null, true,
                null);
        StoredNpc first = await(store.create(withSkin));
        StoredNpc second = await(store.create(plain));
        await(store.create(other));
        assertTrue(first.id() > 0 && second.id() > first.id());
        assertEquals(withSkin.withId(first.id()), first);
        // "Neustart": neue Repository-Instanz liest dieselben NPCs der Servergruppe
        NpcRepository restarted = new NpcRepository(fixture.async);
        List<StoredNpc> lobby = await(restarted.list("lobby"));
        assertEquals(List.of(first, second), lobby);
        assertEquals("dGV4dHVyZXM=", lobby.getFirst().skinValue(), "Skin ohne Quellspieler wieder ladbar");
        assertEquals(1, await(restarted.list("lobby-bedwars")).size(), "Gruppen getrennt");
        assertTrue(await(restarted.delete(first.id())));
        assertFalse(await(restarted.delete(first.id())));
        assertEquals(List.of(second), await(store.list("lobby")));
        assertThrows(IllegalArgumentException.class, () -> new StoredNpc(0, "", "world", 0, 0, 0, 0f, 0f, null,
                null, null, null, true, null));
    }
}
