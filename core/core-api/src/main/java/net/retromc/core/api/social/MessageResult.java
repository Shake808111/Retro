package net.retromc.core.api.social;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Ergebnis von {@code /msg} und {@code /r} (Spec 01 §3.2). Mute und Spam prüft der Aufrufer vorher
 * ({@code PunishmentService.activeMute}, {@code ChatGuard}).
 *
 * @param recipient Empfänger bei {@link Outcome#DELIVERED}
 */
public record MessageResult(Outcome outcome, @Nullable UUID recipient) {

    /** Mögliche Ergebnisse. */
    public enum Outcome {
        /** {@code msg.out}/{@code msg.in}. */
        DELIVERED,
        /** {@code msg.err.self}. */
        SELF,
        /** {@code msg.err.offline} (auch genickte Ziele für Nicht-Freunde, Spec 01 §6.7 Nr. 4). */
        OFFLINE,
        /** {@code msg_from} oder Ignorieren verbietet es; Absender sieht nur {@code msg.err.denied}. */
        DENIED,
        /** {@code /r} ohne Partner der letzten 30 min ({@code msg.err.noreply}). */
        NO_REPLY_TARGET
    }

    public MessageResult {
        Objects.requireNonNull(outcome, "outcome");
        if ((outcome == Outcome.DELIVERED) != (recipient != null)) {
            throw new IllegalArgumentException("recipient genau bei DELIVERED angeben");
        }
    }
}
