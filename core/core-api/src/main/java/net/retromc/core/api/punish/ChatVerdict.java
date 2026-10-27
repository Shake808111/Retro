package net.retromc.core.api.punish;

/** Urteil des {@link ChatGuard} (Spec 01 §8.1, Regeln §6.16). */
public enum ChatVerdict {
    OK,
    /** Spieler ist gemutet ({@code mute.notice}). */
    MUTED,
    /** Mindestabstand unterschritten ({@code spam.wait}). */
    SPAM_WAIT,
    /** Gleicher Text innerhalb 30 s ({@code spam.repeat}). */
    REPEAT,
    /** Mehr als 5 Nachrichten in 10 s → 10 s Sperre ({@code spam.burst}). */
    BURST,
    /** Werbung ({@code spam.ad}, Team-Hinweis {@code spam.staff.ad}). */
    AD,
    /** Zu viele Großbuchstaben: Nachricht wird in Kleinbuchstaben gesendet. */
    CAPS_FIXED;

    /** Ob die Nachricht (ggf. korrigiert) gesendet wird. */
    public boolean allowed() {
        return this == OK || this == CAPS_FIXED;
    }
}
