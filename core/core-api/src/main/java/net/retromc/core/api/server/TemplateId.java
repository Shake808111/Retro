package net.retromc.core.api.server;

import java.util.Collection;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ID eines Server-Templates {@code ^[a-z0-9-]{2,24}$} = Präfix des Servernamens (Spec 08 §6.5, §6.6).
 * Servernamen lauten {@code {templateId}-{n}} mit der kleinsten freien Zahl {@code n ≥ 1}.
 */
public record TemplateId(String value) {

    public static final Pattern PATTERN = Pattern.compile("[a-z0-9-]{2,24}");

    /** Servernamen: nur {@code [a-z0-9-]}, höchstens 48 Zeichen (Spec 08 §6.6 Nr. 1). */
    public static final Pattern SERVER_NAME = Pattern.compile("[a-z0-9-]{1,48}");

    private static final Pattern NUMBERED = Pattern.compile("(.+)-([1-9][0-9]{0,8})");

    // Standard-Templates (Spec 08 §6.5)
    public static final TemplateId LOBBY = new TemplateId("lobby");
    public static final TemplateId PREMIUM_LOBBY = new TemplateId("premiumlobby");
    public static final TemplateId SILENT_LOBBY = new TemplateId("silentlobby");

    public TemplateId {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Ungültige Template-ID: '" + value + "'");
        }
    }

    public static TemplateId of(String value) {
        return new TemplateId(value);
    }

    /** Modus-Lobby {@code lobby-{mode}} (Spec 08 §6.5, Ziel von {@code SEND_LOBBY{mode}}). */
    public static TemplateId modeLobby(String mode) {
        return new TemplateId("lobby-" + mode);
    }

    /** Servername {@code {templateId}-{n}}. */
    public String serverName(int number) {
        if (number < 1) {
            throw new IllegalArgumentException("Servernummer muss ≥ 1 sein: " + number);
        }
        String name = value + "-" + number;
        if (!SERVER_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Servername zu lang: " + name);
        }
        return name;
    }

    /** Laufende Nummer eines Servernamens dieses Templates, z. B. {@code bedwars-4x4-12} → 12. */
    public OptionalInt numberOf(String serverName) {
        Matcher matcher = NUMBERED.matcher(serverName);
        if (matcher.matches() && matcher.group(1).equals(value)) {
            return OptionalInt.of(Integer.parseInt(matcher.group(2)));
        }
        return OptionalInt.empty();
    }

    /** Laufende Nummer am Ende eines beliebigen Servernamens ({@code lobby-3} → 3, {@code lobby} → leer). */
    public static OptionalInt trailingNumber(String serverName) {
        Matcher matcher = NUMBERED.matcher(serverName);
        return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(2))) : OptionalInt.empty();
    }

    /** Kleinste freie Nummer ≥ 1 (Spec 08 §6.6 Nr. 2). */
    public static int lowestFreeNumber(Collection<Integer> used) {
        int candidate = 1;
        while (used.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    @Override
    public String toString() {
        return value;
    }
}
