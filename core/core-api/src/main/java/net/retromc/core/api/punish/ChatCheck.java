package net.retromc.core.api.punish;

import java.time.Duration;
import java.util.Objects;

/**
 * Ergebnis einer Chat-Prüfung.
 *
 * @param text zu sendender Text (bei {@link ChatVerdict#CAPS_FIXED} in Kleinbuchstaben)
 * @param retryAfter Wartezeit bei {@link ChatVerdict#SPAM_WAIT}/{@link ChatVerdict#BURST} ({@code {s}} in {@code spam.burst}),
 *             sonst {@link Duration#ZERO}
 */
public record ChatCheck(ChatVerdict verdict, String text, Duration retryAfter) {

    public ChatCheck {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(retryAfter, "retryAfter");
    }

    public static ChatCheck ok(String text) {
        return new ChatCheck(ChatVerdict.OK, text, Duration.ZERO);
    }
}
