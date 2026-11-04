package net.retromc.core.api.punish;

import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

/** Werte des Anti-Spam (Spec 01 §6.16, O23); Logik im {@link ChatGuard}. */
public final class SpamRules {

    /** Mindestabstand zwischen Nachrichten. */
    public static final Duration MIN_INTERVAL = Duration.ofMillis(1500);

    /** Mindestabstand ab Premium+ (Gewicht 300). */
    public static final Duration MIN_INTERVAL_PREMIUM_PLUS = Duration.ofMillis(750);

    public static final int PREMIUM_PLUS_WEIGHT = 300;

    /** Gleicher (normalisierter) Text ist so lange gesperrt. */
    public static final Duration REPEAT_WINDOW = Duration.ofSeconds(30);

    /** Mehr als so viele Nachrichten im Burst-Fenster lösen die Sperre aus. */
    public static final int BURST_MESSAGES = 5;
    public static final Duration BURST_WINDOW = Duration.ofSeconds(10);
    public static final Duration BURST_LOCK = Duration.ofSeconds(10);

    /** Großbuchstaben: ab 6 Buchstaben und mehr als 50 % groß → Kleinbuchstaben. */
    public static final int CAPS_MIN_LETTERS = 6;
    public static final double CAPS_RATIO = 0.5;

    /** Werbung: Domains (eigene Domains stehen auf einer Whitelist). */
    public static final Pattern AD_DOMAIN = Pattern.compile("\\b[\\w-]+\\.(de|net|com|eu|gg|me|io)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Werbung: IPv4-Adressen. */
    public static final Pattern AD_IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /** Zählen nie als Wiederholung (Rundenende). */
    public static final Set<String> REPEAT_EXEMPT = Set.of("gg", "gege", "gl");

    /**
     * Wortliste des Filters (Wortstämme, Kleinschreibung, nach Leetspeak-Angleichung verglichen): Beleidigungen,
     * Sexuelles, NS-Bezüge und Team-/Systembezeichnungen. Der Chat blockiert danach nicht (Spec 01 §6.16 kennt keinen
     * Wortfilter); genutzt für erzeugte Namen (Nick-Pool, Spec 01 §6.11).
     */
    public static final Set<String> BLOCKED_WORDS = Set.of("fuck", "fick", "shit", "scheis", "bitch", "hure",
            "nutte", "fotze", "wichs", "wixx", "arsch", "penis", "vagina", "porn", "sex", "cock", "dick", "cunt",
            "pussy", "slut", "whore", "rape", "vergewalt", "nazi", "hitler", "adolf", "fuehrer", "fuhrer", "heil",
            "kkk", "isis", "jihad", "terror", "nigg", "neger", "kanake", "kike", "spast", "behindert", "mongo",
            "schwuchtel", "faggot", "retard", "admin", "owner", "moderator", "support", "staff", "developer",
            "console", "official", "system", "server", "nick");

    /** Zahlenfolgen mit NS-Bezug (im Original verglichen, ohne Leetspeak-Angleichung). */
    public static final Set<String> BLOCKED_NUMBERS = Set.of("88", "1488", "18");

    private SpamRules() {
    }

    /** Mindestabstand je Rang. */
    public static Duration minInterval(int rankWeight) {
        return rankWeight >= PREMIUM_PLUS_WEIGHT ? MIN_INTERVAL_PREMIUM_PLUS : MIN_INTERVAL;
    }
}
