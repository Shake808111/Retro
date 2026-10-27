package net.retromc.core.api.punish;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Report-Gründe; der Schlüssel ist das Befehlsargument von {@code /report <Spieler> <Grund>} (Spec 01 §6.9 Nr. 1,
 * O18). {@code randomkilling} ist nur auf TTT- und Replay-Servern zulässig.
 */
public enum ReportReason {
    HACKING("hacking", Set.of()),
    /** Legt einen Chatlog-Snapshot an (Spec 01 §6.9 Nr. 3). */
    CHAT("chat", Set.of()),
    TEAMING("teaming", Set.of()),
    RANDOMKILLING("randomkilling", Set.of("ttt", "replay")),
    NAME("name", Set.of()),
    BUGUSING("bugusing", Set.of());

    private final String key;
    private final Set<String> modes;

    ReportReason(String key, Set<String> modes) {
        this.key = key;
        this.modes = modes;
    }

    /** Befehlsargument und Wert in {@code moderation.report.reason}. */
    public String key() {
        return key;
    }

    /** Zulässig auf einem Server dieses Modus (leere Menge = überall). */
    public boolean allowedOn(String serverMode) {
        return modes.isEmpty() || modes.contains(serverMode.toLowerCase(Locale.ROOT));
    }

    public boolean needsChatlog() {
        return this == CHAT;
    }

    public static Optional<ReportReason> byKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(reason -> reason.key.equals(lower)).findFirst();
    }

    /** Klickliste von {@code /report <Spieler>}: nur die am Server zulässigen Gründe. */
    public static List<ReportReason> allowedFor(String serverMode) {
        return Arrays.stream(values()).filter(reason -> reason.allowedOn(serverMode)).toList();
    }
}
