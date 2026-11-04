package net.retromc.core.api.npc;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * Dauerhafte NPCs aus {@code /npc create} ({@code core.npc}, Migration {@code V2_3}): je Servergruppe Ort, Name und
 * Skin. Der Server lädt beim Start die NPCs seiner Gruppe und legt sie neu an.
 */
public interface NpcStore {

    /**
     * Ein gespeicherter NPC.
     *
     * @param id            {@code BIGSERIAL}; 0 = noch nicht gespeichert
     * @param serverGroup   Template der Server, die den NPC zeigen (z. B. {@code lobby})
     * @param name          Name über dem Kopf als MiniMessage oder {@code null}
     * @param skinName      Name des Spielers, dessen Skin übernommen wurde, oder {@code null} (Standard-Skin)
     * @param skinValue     {@code textures}-Property (Base64) oder {@code null}
     * @param skinSignature Signatur der Property oder {@code null}
     * @param lookAt        Kopf folgt dem nächsten Spieler
     */
    record StoredNpc(long id, String serverGroup, String world, double x, double y, double z, float yaw, float pitch,
            @Nullable String name, @Nullable String skinName, @Nullable String skinValue,
            @Nullable String skinSignature, boolean lookAt, @Nullable UUID createdBy) {

        public StoredNpc {
            Objects.requireNonNull(serverGroup, "serverGroup");
            Objects.requireNonNull(world, "world");
            if (serverGroup.isEmpty() || serverGroup.length() > 32 || world.isEmpty() || world.length() > 64) {
                throw new IllegalArgumentException("Servergruppe 1–32, Welt 1–64 Zeichen");
            }
            if (skinName != null && skinName.length() > 16) {
                throw new IllegalArgumentException("Skin-Name höchstens 16 Zeichen: " + skinName);
            }
        }

        /** Derselbe NPC mit vergebener ID. */
        public StoredNpc withId(long newId) {
            return new StoredNpc(newId, serverGroup, world, x, y, z, yaw, pitch, name, skinName, skinValue,
                    skinSignature, lookAt, createdBy);
        }
    }

    /** Speichert einen neuen NPC und liefert ihn mit vergebener ID. */
    CompletableFuture<StoredNpc> create(StoredNpc npc);

    /** Alle NPCs einer Servergruppe, nach ID. */
    CompletableFuture<List<StoredNpc>> list(String serverGroup);

    /** Entfernt einen NPC; {@code true}, wenn es ihn gab. */
    CompletableFuture<Boolean> delete(long id);
}
