package net.retromc.core.api.punish;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Anzeige-IDs der Strafen (Spec 01 §6.10): manueller Bann 11 Zeichen Base62, Bann aus Report {@code R:} + 8,
 * Anticheat {@code X:} + 8, Mute 8 Zeichen {@code [0-9A-Z]}. Zufall aus {@link SecureRandom}; bei Kollision erzeugt
 * der Aufrufer eine neue ID (Unique-Index auf {@code public_id}).
 */
public final class PunishmentId {

    public static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static final Pattern BAN = Pattern.compile("[0-9A-Za-z]{11}");
    public static final Pattern REPORT_BAN = Pattern.compile("R:[0-9A-Z]{8}");
    public static final Pattern ANTICHEAT_BAN = Pattern.compile("X:[0-9A-Z]{8}");
    public static final Pattern MUTE = Pattern.compile("[0-9A-Z]{8}");
    /** Report-ID ({@code moderation.report.public_id VARCHAR(12)}); Format wie Mute (Annahme, Spec 01 offen). */
    public static final Pattern REPORT = MUTE;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PunishmentId() {
    }

    public static String newBanId() {
        return random(BASE62, 11, RANDOM);
    }

    public static String reportBanId() {
        return "R:" + random(BASE36, 8, RANDOM);
    }

    public static String anticheatBanId() {
        return "X:" + random(BASE36, 8, RANDOM);
    }

    public static String newMuteId() {
        return random(BASE36, 8, RANDOM);
    }

    public static String newReportId() {
        return random(BASE36, 8, RANDOM);
    }

    /**
     * ID passend zu Art und Herkunft: BAN manuell/automatisch → Base62(11), BAN aus Report → {@code R:},
     * BAN Anticheat → {@code X:}; MUTE, KICK und WARN → 8 Zeichen {@code [0-9A-Z]}.
     */
    public static String forPunishment(PunishmentType type, PunishmentOrigin origin) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(origin, "origin");
        if (type != PunishmentType.BAN) {
            return newMuteId();
        }
        return switch (origin) {
            case MANUAL, AUTO -> newBanId();
            case REPORT -> reportBanId();
            case AC -> anticheatBanId();
        };
    }

    /**
     * Ob der Text die Form einer Straf-ID hat ({@code /unban <Spieler|ID>}). Spielernamen aus 11 Buchstaben/Ziffern
     * oder 8 Großbuchstaben/Ziffern haben dieselbe Form; der Befehl sucht deshalb zuerst die ID und dann den Namen.
     */
    public static boolean isPunishmentId(String text) {
        return BAN.matcher(text).matches() || REPORT_BAN.matcher(text).matches()
                || ANTICHEAT_BAN.matcher(text).matches() || MUTE.matcher(text).matches();
    }

    static String random(String alphabet, int length, RandomGenerator random) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = alphabet.charAt(random.nextInt(alphabet.length()));
        }
        return new String(out);
    }
}
