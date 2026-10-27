package net.retromc.core.api.social;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aktiver Nick {@code nick:{uuid}} (HASH {@code name, texture, signature, since}, Spec 01 §2.3, §6.11) aus
 * {@code core.nick_pool}. Stats und Reports laufen intern immer auf die echte UUID.
 */
public record Nick(UUID uuid, String name, String texture, String signature, Instant since) {

    public static final String NAME = "name";
    public static final String TEXTURE = "texture";
    public static final String SIGNATURE = "signature";
    public static final String SINCE = "since";

    public Nick {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(since, "since");
    }

    public Map<String, String> toHash() {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(NAME, name);
        hash.put(TEXTURE, texture);
        hash.put(SIGNATURE, signature);
        hash.put(SINCE, Long.toString(since.toEpochMilli()));
        return hash;
    }

    public static Nick fromHash(UUID uuid, Map<String, String> hash) {
        String since = hash.get(SINCE);
        String name = hash.get(NAME);
        String texture = hash.get(TEXTURE);
        String signature = hash.get(SIGNATURE);
        if (name == null || texture == null || signature == null || since == null) {
            throw new IllegalArgumentException("nick-Hash unvollständig für " + uuid);
        }
        return new Nick(uuid, name, texture, signature, Instant.ofEpochMilli(Long.parseLong(since)));
    }
}
