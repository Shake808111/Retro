package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Freund in Listen und GUI (Spec 01 §3.1 #10, §5.2).
 *
 * @param since befreundet seit ({@code social.friendship.since})
 * @param favorite vom Besitzer als Favorit markiert ({@code social.friend_favorite})
 * @param server aktueller Server, {@code null} = offline oder versteckt ({@code show_online=false})
 * @param lastSeen letzter Logout ({@code core.player.last_quit}) oder {@code null}
 */
public record FriendEntry(
        UUID uuid,
        String name,
        Instant since,
        boolean favorite,
        @Nullable String server,
        @Nullable Instant lastSeen) {

    private static final Comparator<FriendEntry> BY_NAME =
            Comparator.comparing(FriendEntry::name, String.CASE_INSENSITIVE_ORDER);

    /** GUI §5.2: online Favoriten, online, offline Favoriten, offline (je alphabetisch). */
    public static final Comparator<FriendEntry> GUI_ORDER = Comparator
            .comparing((FriendEntry entry) -> !entry.online())
            .thenComparing(entry -> !entry.favorite())
            .thenComparing(BY_NAME);

    /** Chatliste {@code /friend list}: online zuerst, dann alphabetisch. */
    public static final Comparator<FriendEntry> LIST_ORDER = Comparator
            .comparing((FriendEntry entry) -> !entry.online())
            .thenComparing(BY_NAME);

    public FriendEntry {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(since, "since");
    }

    public boolean online() {
        return server != null;
    }
}
