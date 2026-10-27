package net.retromc.core.api.punish;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import net.retromc.core.api.util.DurationFormat;
import org.jspecify.annotations.Nullable;

/**
 * Dauer einer Strafe; {@code duration == null} bedeutet permanent. In {@code moderation.reason.stages_s} steht
 * permanent als {@code -1} (Spec 01 §2.2).
 */
public record PunishmentDuration(@Nullable Duration duration) {

    public static final PunishmentDuration PERMANENT = new PunishmentDuration(null);

    /** Wert für „permanent“ in {@code stages_s} und {@code mute:{uuid}.expiresMs}. */
    public static final long PERMANENT_SECONDS = -1;

    public PunishmentDuration {
        if (duration != null && (duration.isNegative() || duration.isZero())) {
            throw new IllegalArgumentException("Dauer muss positiv sein: " + duration);
        }
    }

    public static PunishmentDuration of(Duration duration) {
        return new PunishmentDuration(duration);
    }

    /** Aus Sekunden; {@code -1} = permanent. */
    public static PunishmentDuration ofSeconds(long seconds) {
        return seconds == PERMANENT_SECONDS ? PERMANENT : new PunishmentDuration(Duration.ofSeconds(seconds));
    }

    /**
     * Dauer-Argument von {@code /ban} und {@code /mute} (Spec 01 §3.6 #77): {@code perm}/{@code permanent} oder
     * {@code 30d}, {@code 12h}, {@code 1d12h} … Ungültig oder 0 → leer.
     */
    public static Optional<PunishmentDuration> parse(String text) {
        String input = text.strip().toLowerCase(Locale.ROOT);
        if (input.equals("perm") || input.equals("permanent")) {
            return Optional.of(PERMANENT);
        }
        return DurationFormat.parse(input)
                .filter(value -> !value.isZero())
                .map(PunishmentDuration::new);
    }

    public boolean isPermanent() {
        return duration == null;
    }

    /** Sekunden; {@code -1} = permanent. */
    public long seconds() {
        return duration == null ? PERMANENT_SECONDS : duration.getSeconds();
    }

    /** Ablauf ab {@code issuedAt}; {@code null} = permanent. */
    public @Nullable Instant expiresAt(Instant issuedAt) {
        return duration == null ? null : issuedAt.plus(duration);
    }
}
