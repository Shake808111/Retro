package net.retromc.core.api.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * ULID (26 Zeichen Crockford-Base32: 48 Bit Millisekunden + 80 Bit Zufall), z. B. die Party-ID (Spec 01 §6.5).
 * Lexikografische Reihenfolge entspricht der Erzeugungszeit (auf Millisekunden genau).
 */
public final class Ulid {

    public static final int LENGTH = 26;
    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Erstes Zeichen höchstens '7', sonst liefe der 48-Bit-Zeitstempel über. */
    public static final Pattern PATTERN = Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}");

    private static final long MAX_TIME = (1L << 48) - 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    /** Neue ULID zur aktuellen Zeit mit kryptografisch starkem Zufall. */
    public static String next() {
        return generate(System.currentTimeMillis(), RANDOM);
    }

    /** ULID für einen festen Zeitpunkt (Tests, Nachbildung). */
    public static String generate(long epochMillis, Random random) {
        if (epochMillis < 0 || epochMillis > MAX_TIME) {
            throw new IllegalArgumentException("Zeitstempel außerhalb von 48 Bit: " + epochMillis);
        }
        Objects.requireNonNull(random, "random");
        char[] out = new char[LENGTH];
        long time = epochMillis;
        for (int i = 9; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int) (time & 31));
            time >>>= 5;
        }
        for (int i = 10; i < LENGTH; i++) {
            out[i] = ALPHABET.charAt(random.nextInt(32));
        }
        return new String(out);
    }

    public static boolean isValid(@Nullable String value) {
        return value != null && PATTERN.matcher(value).matches();
    }

    /** Zeitpunkt der Erzeugung aus den ersten 10 Zeichen. */
    public static Instant timestamp(String ulid) {
        if (!isValid(ulid)) {
            throw new IllegalArgumentException("Keine ULID: " + ulid);
        }
        long time = 0;
        for (int i = 0; i < 10; i++) {
            time = (time << 5) | ALPHABET.indexOf(ulid.charAt(i));
        }
        return Instant.ofEpochMilli(time);
    }
}
