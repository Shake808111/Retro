package net.retromc.core.api.rank;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Rang eines Spielers aus LuckPerms (Spec 01 §8.1 {@code RankService.rank}).
 *
 * @param id LuckPerms-Gruppe (z. B. {@code supremium})
 * @param displayName Anzeigename aus {@code meta.display}
 * @param prefix Präfix für Tab/Nametag oder {@code null}
 * @param legacyColor §-Farbcode von Präfix und Name (Rückfall, wenn kein Verlauf darstellbar ist)
 * @param gradient Verlauf {@code #RRGGBB:#RRGGBB} oder {@code null}
 * @param weight Gewicht 0–1000
 * @param expiresAt Ablauf eines Zeitrangs (LuckPerms-Expiry), {@code null} = dauerhaft ({@code /rank})
 */
public record RankInfo(
        String id,
        String displayName,
        @Nullable String prefix,
        String legacyColor,
        @Nullable String gradient,
        int weight,
        RankLimits limits,
        RankFlags flags,
        @Nullable Instant expiresAt) {

    public RankInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(legacyColor, "legacyColor");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(flags, "flags");
        if (weight < 0) {
            throw new IllegalArgumentException("weight darf nicht negativ sein");
        }
    }

    /** Standardwerte eines Rangs aus der Tabelle (dauerhaft). */
    public static RankInfo of(Rank rank) {
        return new RankInfo(rank.group(), rank.displayName(), rank.prefix(), rank.color(), rank.gradient(),
                rank.weight(), rank.defaultLimits(), rank.defaultFlags(), null);
    }

    public boolean isPermanent() {
        return expiresAt == null;
    }

    /** Restlaufzeit eines Zeitrangs; leer bei dauerhaftem Rang. */
    public Optional<Duration> remaining(Instant now) {
        if (expiresAt == null) {
            return Optional.empty();
        }
        Duration left = Duration.between(now, expiresAt);
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }

    public boolean isTeam() {
        return weight >= Rank.TEAM_WEIGHT;
    }

    /** „X+“-Prüfung der Specs: Gewicht ≥ {@code minWeight}. */
    public boolean isAtLeast(int minWeight) {
        return weight >= minWeight;
    }
}
