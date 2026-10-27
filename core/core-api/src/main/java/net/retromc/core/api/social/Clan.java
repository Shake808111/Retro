package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.util.LevelCurve;
import org.jspecify.annotations.Nullable;

/**
 * Clan aus {@code social.clan} (V1 + V1_1, Spec 01 §2.2). Die V1-Spalte {@code elo} ist veraltet; Elo je Modus
 * steht in {@code social.clan_elo}.
 *
 * @param owner Leiter (V1-Spalte {@code owner})
 * @param showRoles Rangunterschiede für Mitglieder sichtbar
 * @param lastActive letzter Login eines Mitglieds (Inaktivitäts-Job, 60 Tage)
 */
public record Clan(
        long id,
        String name,
        String tag,
        UUID owner,
        Instant createdAt,
        ClanJoinMode joinMode,
        boolean showRoles,
        long xp,
        Instant lastActive,
        @Nullable String description) {

    public Clan {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(joinMode, "joinMode");
        Objects.requireNonNull(lastActive, "lastActive");
        if (xp < 0) {
            throw new IllegalArgumentException("xp darf nicht negativ sein");
        }
    }

    /** Clan-Level nach {@code xpFür(n) = 250·n·(n+1)} (Spec 01 §6.6). */
    public LevelCurve.Progress level() {
        return LevelCurve.progress(xp);
    }
}
