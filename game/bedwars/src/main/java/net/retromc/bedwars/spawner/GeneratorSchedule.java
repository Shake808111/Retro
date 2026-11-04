package net.retromc.bedwars.spawner;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Stufenplan eines Generators (Spec 04 §6.1.3, §9 #2, U1): Takt je Stufe und Wechselzeit ab Rundenstart. Das Board
 * zeigt die <em>nächste</em> Stufe mit Restzeit, nach der letzten Stufe {@code III Max.}.
 *
 * @param resource Ressource
 * @param tiers    Stufen in Reihenfolge (Stufe I beginnt bei 0)
 */
public record GeneratorSchedule(Resource resource, List<Tier> tiers) {

    /**
     * Eine Stufe.
     *
     * @param from     Beginn ab Rundenstart
     * @param interval Takt (ein Item je Intervall)
     */
    public record Tier(Duration from, Duration interval) {

        public Tier {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(interval, "interval");
            if (interval.isNegative() || interval.isZero()) {
                throw new IllegalArgumentException("Takt muss > 0 sein");
            }
        }
    }

    /**
     * Stand zu einer Rundenzeit.
     *
     * @param tier     aktuelle Stufe (1-basiert)
     * @param shown    angezeigte Stufe (die nächste, bzw. die letzte bei {@code max})
     * @param untilNext Zeit bis zur nächsten Stufe ({@code ZERO} bei {@code max})
     * @param max      letzte Stufe erreicht
     */
    public record State(int tier, int shown, Duration untilNext, boolean max) {
    }

    public GeneratorSchedule {
        Objects.requireNonNull(resource, "resource");
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty() || !tiers.getFirst().from().isZero()) {
            throw new IllegalArgumentException("Stufe I muss bei 0 beginnen");
        }
    }

    /** Diamant: I 25 s, II ab 05:00 20 s, III ab 15:00 15 s. */
    public static GeneratorSchedule diamond() {
        return new GeneratorSchedule(Resource.DIAMOND, List.of(new Tier(Duration.ZERO, Duration.ofSeconds(25)),
                new Tier(Duration.ofMinutes(5), Duration.ofSeconds(20)),
                new Tier(Duration.ofMinutes(15), Duration.ofSeconds(15))));
    }

    /** Gold: I 35 s, II ab 10:00 25 s, III ab 25:00 15 s. */
    public static GeneratorSchedule gold() {
        return new GeneratorSchedule(Resource.GOLD, List.of(new Tier(Duration.ZERO, Duration.ofSeconds(35)),
                new Tier(Duration.ofMinutes(10), Duration.ofSeconds(25)),
                new Tier(Duration.ofMinutes(25), Duration.ofSeconds(15))));
    }

    /** Fester Takt ohne Stufen (Kupfer 1 s, Eisen 8 s, Classic). */
    public static GeneratorSchedule fixed(Resource resource, Duration interval) {
        return new GeneratorSchedule(resource, List.of(new Tier(Duration.ZERO, interval)));
    }

    public State at(Duration elapsed) {
        int current = 0;
        for (int i = 0; i < tiers.size(); i++) {
            if (elapsed.compareTo(tiers.get(i).from()) >= 0) {
                current = i;
            }
        }
        boolean max = current == tiers.size() - 1;
        if (max) {
            return new State(current + 1, current + 1, Duration.ZERO, true);
        }
        return new State(current + 1, current + 2, tiers.get(current + 1).from().minus(elapsed), false);
    }

    /** Takt der Stufe zu {@code elapsed}. */
    public Duration interval(Duration elapsed) {
        return tiers.get(at(elapsed).tier() - 1).interval();
    }

    /** Ob bei {@code elapsed} genau eine neue Stufe beginnt (Sekundengenau, für {@code bw.spawner.tier}). */
    public int tierStartingAt(Duration elapsed) {
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).from().toSeconds() == elapsed.toSeconds()) {
                return i + 1;
            }
        }
        return 0;
    }

    /** Römische Stufe I–III. */
    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(tier);
        };
    }

    /** {@code mm:ss}. */
    public static String clock(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
}
