package net.retromc.core.api.punish;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Strafgrund aus {@code moderation.reason} (Spec 01 §2.2, Seed {@code V1_2__seed_reasons.sql}, Stufen §6.10).
 *
 * @param labelKey i18n-Schlüssel der Anzeige ({@code punish.reason.<code>})
 * @param stagesSeconds Dauer je Stufe in Sekunden, {@code -1} = permanent
 * @param reportScope Server-Modi, auf denen der Grund meldbar ist, z. B. {@code TTT,REPLAY}; {@code null} = überall
 */
public record PunishmentReason(
        String code,
        PunishmentType type,
        String labelKey,
        List<Long> stagesSeconds,
        boolean reportable,
        @Nullable String reportScope) {

    public PunishmentReason {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(labelKey, "labelKey");
        stagesSeconds = List.copyOf(stagesSeconds);
        if (stagesSeconds.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Stufe nötig: " + code);
        }
        for (long seconds : stagesSeconds) {
            if (seconds != PunishmentDuration.PERMANENT_SECONDS && seconds <= 0) {
                throw new IllegalArgumentException("Ungültige Stufendauer " + seconds + " bei " + code);
            }
        }
        if (type != PunishmentType.BAN && type != PunishmentType.MUTE) {
            throw new IllegalArgumentException("Gründe gibt es nur für BAN und MUTE: " + code);
        }
    }

    public int stageCount() {
        return stagesSeconds.size();
    }

    /**
     * Stufe = Anzahl früherer, nicht widerrufener Strafen gleichen Codes + 1, gedeckelt auf die letzte Stufe
     * (Spec 01 §6.10).
     */
    public int stageFor(int previousNotRevoked) {
        if (previousNotRevoked < 0) {
            throw new IllegalArgumentException("Anzahl darf nicht negativ sein");
        }
        return Math.min(previousNotRevoked + 1, stageCount());
    }

    /** Dauer einer Stufe (1-basiert, gedeckelt). */
    public PunishmentDuration durationForStage(int stage) {
        if (stage < 1) {
            throw new IllegalArgumentException("Stufe beginnt bei 1: " + stage);
        }
        return PunishmentDuration.ofSeconds(stagesSeconds.get(Math.min(stage, stageCount()) - 1));
    }

    /** Meldbar auf einem Server dieses Modus ({@code report_scope}, Groß-/Kleinschreibung egal)? */
    public boolean reportableOn(String serverMode) {
        if (!reportable) {
            return false;
        }
        if (reportScope == null || reportScope.isBlank()) {
            return true;
        }
        String mode = serverMode.toUpperCase(Locale.ROOT);
        return Arrays.stream(reportScope.split(",")).map(String::strip).anyMatch(mode::equals);
    }
}
