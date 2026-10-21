package net.retromc.core.messaging;

/**
 * Netzwerkweite Kanäle, Namen aus Notiz 07, Abschnitt 4 ("Channels or streams").
 */
public enum NetworkChannel {
    CHAT("net:chat"),
    SOCIAL("net:social"),
    MODERATION("net:moderation"),
    QUEUE("net:queue"),
    SERVER_STATE("net:server-state");

    private final String key;

    NetworkChannel(String key) {
        this.key = key;
    }

    /** Kanalname in Valkey. */
    public String key() {
        return key;
    }
}
