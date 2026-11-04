package net.retromc.core.data.punish;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import net.retromc.core.api.punish.ChatCheck;
import net.retromc.core.api.punish.ChatContext;
import net.retromc.core.api.punish.ChatVerdict;
import net.retromc.core.api.punish.SpamRules;
import org.jspecify.annotations.Nullable;

/**
 * Regeln des Anti-Spam (Spec 01 §6.16) als reine Funktion über einem {@link State}; die Speicherung übernimmt
 * {@link ValkeyChatGuard} ({@code spam:{uuid}}, 30 s). Reihenfolge: Burst-Sperre → Mindestabstand → Wiederholung →
 * Werbung → Burst → Großbuchstaben. Teammitglieder sind von allen Regeln ausgenommen.
 */
public final class SpamFilter {

    private final Set<String> ownDomains;

    /**
     * @param ownDomains eigene Domains (Werbe-Whitelist), z. B. {@code retromc.net}; Vergleich ohne
     *     Groß-/Kleinschreibung, Subdomains eingeschlossen
     */
    public SpamFilter(Set<String> ownDomains) {
        this.ownDomains = ownDomains.stream().map(domain -> domain.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Zustand je Spieler.
     *
     * @param lastText zuletzt gesendeter Text (normalisiert)
     * @param lastAt Zeitpunkt der letzten gesendeten Nachricht
     * @param burstStart Beginn des laufenden Burst-Fensters
     * @param burstCount gesendete Nachrichten im Fenster
     * @param lockedUntil Ende einer Burst-Sperre
     */
    public record State(@Nullable String lastText, @Nullable Instant lastAt, @Nullable Instant burstStart,
            int burstCount, @Nullable Instant lockedUntil) {

        public static final State EMPTY = new State(null, null, null, 0, null);

        /** HASH-Felder für {@code spam:{uuid}}. */
        public Map<String, String> toHash() {
            Map<String, String> hash = new LinkedHashMap<>();
            if (lastText != null) {
                hash.put("lastText", lastText);
            }
            put(hash, "lastAt", lastAt);
            put(hash, "burstStart", burstStart);
            hash.put("burst", Integer.toString(burstCount));
            put(hash, "lockedUntil", lockedUntil);
            return hash;
        }

        public static State fromHash(@Nullable Map<String, String> hash) {
            if (hash == null || hash.isEmpty()) {
                return EMPTY;
            }
            return new State(hash.get("lastText"), instant(hash.get("lastAt")), instant(hash.get("burstStart")),
                    Integer.parseInt(hash.getOrDefault("burst", "0")), instant(hash.get("lockedUntil")));
        }

        private static void put(Map<String, String> hash, String field, @Nullable Instant value) {
            if (value != null) {
                hash.put(field, Long.toString(value.toEpochMilli()));
            }
        }

        private static @Nullable Instant instant(@Nullable String millis) {
            return millis == null || millis.isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(millis));
        }
    }

    /** Ergebnis mit neuem Zustand. */
    public record Decision(ChatCheck check, State state) {
    }

    public Decision check(State state, String text, ChatContext context, Instant now) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(text, "text");
        if (context.team()) {
            return new Decision(ChatCheck.ok(text), state);
        }
        if (state.lockedUntil() != null && now.isBefore(state.lockedUntil())) {
            return blocked(ChatVerdict.BURST, text, Duration.between(now, state.lockedUntil()), state);
        }
        Duration interval = SpamRules.minInterval(context.rankWeight());
        if (state.lastAt() != null && Duration.between(state.lastAt(), now).compareTo(interval) < 0) {
            return blocked(ChatVerdict.SPAM_WAIT, text, interval.minus(Duration.between(state.lastAt(), now)), state);
        }
        String normalized = normalize(text);
        if (normalized.equals(state.lastText()) && !SpamRules.REPEAT_EXEMPT.contains(normalized)
                && state.lastAt() != null
                && Duration.between(state.lastAt(), now).compareTo(SpamRules.REPEAT_WINDOW) < 0) {
            return blocked(ChatVerdict.REPEAT, text, Duration.ZERO, state);
        }
        if (isAdvertising(text)) {
            return blocked(ChatVerdict.AD, text, Duration.ZERO, state);
        }
        boolean windowOpen = state.burstStart() != null
                && Duration.between(state.burstStart(), now).compareTo(SpamRules.BURST_WINDOW) < 0;
        Instant burstStart = windowOpen ? state.burstStart() : now;
        int burstCount = (windowOpen ? state.burstCount() : 0) + 1;
        if (burstCount > SpamRules.BURST_MESSAGES) {
            Instant lockedUntil = now.plus(SpamRules.BURST_LOCK);
            return blocked(ChatVerdict.BURST, text, SpamRules.BURST_LOCK,
                    new State(state.lastText(), state.lastAt(), burstStart, burstCount, lockedUntil));
        }
        State next = new State(normalized, now, burstStart, burstCount, null);
        if (tooManyCaps(text)) {
            return new Decision(new ChatCheck(ChatVerdict.CAPS_FIXED, text.toLowerCase(Locale.ROOT), Duration.ZERO),
                    next);
        }
        return new Decision(ChatCheck.ok(text), next);
    }

    /** Vergleichsform für Wiederholungen: Kleinbuchstaben, Leerzeichen zusammengefasst. */
    public static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Enthält der Text ein Wort aus {@link SpamRules#BLOCKED_WORDS} oder {@code extra} (Leetspeak angeglichen:
     * {@code 0→o 1→i 3→e 4→a 5→s 7→t @→a $→s}, ohne {@code _ - .} und Leerzeichen) oder eine Zahlenfolge aus
     * {@link SpamRules#BLOCKED_NUMBERS}?
     */
    public static boolean containsBlockedWord(String text, Set<String> extra) {
        String raw = text.toLowerCase(Locale.ROOT);
        for (String number : SpamRules.BLOCKED_NUMBERS) {
            if (raw.contains(number)) {
                return true;
            }
        }
        StringBuilder folded = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '0' -> folded.append('o');
                case '1' -> folded.append('i');
                case '3' -> folded.append('e');
                case '4', '@' -> folded.append('a');
                case '5', '$' -> folded.append('s');
                case '7' -> folded.append('t');
                case '_', '-', '.', ' ' -> {
                }
                default -> folded.append(c);
            }
        }
        String plain = folded.toString();
        for (String word : SpamRules.BLOCKED_WORDS) {
            if (plain.contains(word)) {
                return true;
            }
        }
        for (String word : extra) {
            String clean = word.strip().toLowerCase(Locale.ROOT);
            if (!clean.isEmpty() && (plain.contains(clean) || raw.contains(clean))) {
                return true;
            }
        }
        return false;
    }

    /** ≥ 6 Buchstaben und mehr als 50 % davon groß. */
    public static boolean tooManyCaps(String text) {
        int letters = 0;
        int upper = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        return letters >= SpamRules.CAPS_MIN_LETTERS && upper > letters * SpamRules.CAPS_RATIO;
    }

    /** IPv4-Adresse oder fremde Domain. */
    public boolean isAdvertising(String text) {
        if (SpamRules.AD_IPV4.matcher(text).find()) {
            return true;
        }
        Matcher domains = SpamRules.AD_DOMAIN.matcher(text);
        while (domains.find()) {
            if (!isOwn(domains.group().toLowerCase(Locale.ROOT), text, domains.start())) {
                return true;
            }
        }
        return false;
    }

    private boolean isOwn(String match, String text, int start) {
        int from = start;
        while (from > 0 && (Character.isLetterOrDigit(text.charAt(from - 1)) || text.charAt(from - 1) == '.'
                || text.charAt(from - 1) == '-')) {
            from--;
        }
        String host = text.substring(from, start).toLowerCase(Locale.ROOT) + match;
        return ownDomains.stream().anyMatch(own -> host.equals(own) || host.endsWith("." + own));
    }

    private static Decision blocked(ChatVerdict verdict, String text, Duration retryAfter, State state) {
        Duration wait = retryAfter.isNegative() ? Duration.ZERO : retryAfter;
        return new Decision(new ChatCheck(verdict, text, wait), state);
    }
}
