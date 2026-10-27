package net.retromc.core.api.social;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Präsenz {@code presence:{uuid}} (HASH, TTL 60 s, Heartbeat 20 s; Spec 01 §2.3): Proxy, Server, seit wann,
 * AFK ({@code afk} 0/1, Spec 01 §6.12), aktiver Nick und {@code hidden} ({@code show_online=false}).
 */
public record Presence(
        UUID uuid,
        String proxy,
        String server,
        Instant since,
        boolean afk,
        @Nullable String nick,
        boolean hidden) {

    public static final String PROXY = "proxy";
    public static final String SERVER = "server";
    public static final String SINCE = "since";
    public static final String AFK = "afk";
    public static final String NICK = "nick";
    public static final String HIDDEN = "hidden";

    public Presence {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(since, "since");
    }

    /** Für Freunde/Clan als online sichtbar (Spec 01 §6.4 Nr. 5). */
    public boolean visible() {
        return !hidden;
    }

    /** HASH-Felder; {@code nick} leer, wenn kein Nick aktiv ist. */
    public Map<String, String> toHash() {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put(PROXY, proxy);
        hash.put(SERVER, server);
        hash.put(SINCE, Long.toString(since.toEpochMilli()));
        hash.put(AFK, afk ? "1" : "0");
        hash.put(NICK, nick == null ? "" : nick);
        hash.put(HIDDEN, hidden ? "1" : "0");
        return hash;
    }

    public static Presence fromHash(UUID uuid, Map<String, String> hash) {
        String proxy = hash.get(PROXY);
        String server = hash.get(SERVER);
        String since = hash.get(SINCE);
        if (proxy == null || server == null || since == null) {
            throw new IllegalArgumentException("presence-Hash unvollständig für " + uuid);
        }
        String nick = hash.get(NICK);
        return new Presence(uuid, proxy, server, Instant.ofEpochMilli(Long.parseLong(since)),
                "1".equals(hash.get(AFK)), nick == null || nick.isEmpty() ? null : nick, "1".equals(hash.get(HIDDEN)));
    }
}
