package net.retromc.core.i18n;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Zahl-, Dauer- und Datumsformate je Sprache (Spec 02 §6.8, Spec 01 §6.10, Spec 00 §4.4).
 *
 * <ul>
 *   <li>Ganze Zahlen: {@code de_DE} (und Dialekte) mit Tausenderpunkt {@code 10.560}, {@code en_US} mit Komma.</li>
 *   <li>{@code /stats}-Dezimalzahlen immer mit Punkt ({@code 84.85 %}), in allen Sprachen (Spec 02 §6.13 Nr. 5).</li>
 *   <li>Scoreboard {@code 69,49 %} und Hologramm {@code 27,59%} mit dem Dezimalzeichen der Sprache.</li>
 *   <li>Rundung überall HALF_UP auf 2 Nachkommastellen.</li>
 *   <li>Datum/Uhrzeit immer in {@link #ZONE Europe/Berlin}.</li>
 * </ul>
 *
 * <p>Instanzen sind unveränderlich und threadsicher.
 */
public final class Formats {

    /** Zeitzone für alle Datums- und Uhrzeitangaben. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final Map<Language, Formats> BY_LANGUAGE = new EnumMap<>(Language.class);

    static {
        for (Language language : Language.values()) {
            BY_LANGUAGE.put(language, new Formats(language));
        }
    }

    private final Language language;
    private final char groupingSeparator;
    private final char decimalSeparator;
    private final DateTimeFormatter date;

    private Formats(Language language) {
        this.language = language;
        boolean english = language == Language.EN_US;
        this.groupingSeparator = english ? ',' : '.';
        this.decimalSeparator = english ? '.' : ',';
        this.date = DateTimeFormatter.ofPattern(english ? "MM/dd/yyyy" : "dd.MM.yyyy", Locale.ROOT);
    }

    public static Formats of(Language language) {
        return BY_LANGUAGE.get(Objects.requireNonNull(language, "language"));
    }

    /** Formate zur Sprache des Locale ({@link Language#of(Locale)}). */
    public static Formats of(Locale locale) {
        return of(Language.of(locale));
    }

    public Language language() {
        return language;
    }

    // ------------------------------------------------------------------ Werte (Spec 02 §6.8, Spalte "Formel")

    /** K/D = Kills / Deaths, bei 0 Deaths = Kills (Spec 02 O16); 2 Nachkommastellen, HALF_UP. */
    public static BigDecimal kd(long kills, long deaths) {
        requireNotNegative(kills, "kills");
        requireNotNegative(deaths, "deaths");
        if (deaths == 0) {
            return BigDecimal.valueOf(kills).setScale(SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(kills).divide(BigDecimal.valueOf(deaths), SCALE, RoundingMode.HALF_UP);
    }

    /** Siegesquote in Prozent = 100 · Siege / Spiele, bei 0 Spielen 0; 2 Nachkommastellen, HALF_UP. */
    public static BigDecimal winrate(long wins, long games) {
        requireNotNegative(wins, "wins");
        requireNotNegative(games, "games");
        if (games == 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(wins).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(games), SCALE, RoundingMode.HALF_UP);
    }

    /** Spielzeit in ganzen Stunden: {@code floor(s / 3600)} (Spec 02 §6.8). */
    public static long playtimeHours(long playtimeSeconds) {
        requireNotNegative(playtimeSeconds, "playtimeSeconds");
        return playtimeSeconds / 3600;
    }

    // ------------------------------------------------------------------ Ganze Zahlen

    /** Ganze Zahl mit Tausendertrenner der Sprache: {@code 10.560}, {@code 1.355.470} (en_US {@code 10,560}). */
    public String integer(long value) {
        return group(value, groupingSeparator);
    }

    /** Wie {@link #integer(long)}; {@link UiEra#LEGACY}: Komma als Tausendertrenner ({@code 11,877}). */
    public String integer(long value, UiEra era) {
        return era == UiEra.LEGACY ? group(value, ',') : integer(value);
    }

    /** Ganze Zahl im Hologramm: MODERN wie {@link #integer(long)}, LEGACY ohne Trenner. */
    public String hologramInteger(long value, UiEra era) {
        return era == UiEra.LEGACY ? Long.toString(value) : integer(value);
    }

    // ------------------------------------------------------------------ Dezimalzahlen

    /** {@code /stats}-Dezimalformat {@code #.##} mit Punkt (alle Sprachen): {@code 11.21}, {@code 1.88}, {@code 0}. */
    public String statsDecimal(BigDecimal value) {
        return plain(value.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros());
    }

    /** K/D für {@code /stats} und Hologramme ({@code #.##}, Punkt): {@code 11.21}, {@code 1.33}, {@code 0}. */
    public String statsKd(long kills, long deaths) {
        return statsDecimal(kd(kills, deaths));
    }

    /** Siegesquote für {@code /stats}: {@code 84.85 %}, {@code 25 %}, {@code 0 %}. */
    public String statsWinrate(long wins, long games) {
        return statsDecimal(winrate(wins, games)) + " %";
    }

    /** K/D im Hologramm: {@code #.##} mit Punkt wie {@code /stats} ({@code 1.33}, Spec 02 §6.8). */
    public String hologramKd(long kills, long deaths) {
        return statsKd(kills, deaths);
    }

    /** Siegesquote im Hologramm: {@code #,##} mit Dezimalzeichen der Sprache, ohne Leerzeichen: {@code 27,59%}. */
    public String hologramWinrate(long wins, long games) {
        BigDecimal value = winrate(wins, games).stripTrailingZeros();
        return plain(value).replace('.', decimalSeparator) + "%";
    }

    /** Feste 2 Nachkommastellen mit Dezimalzeichen der Sprache: {@code 69,49}, {@code 0,00}. */
    public String fixed2(BigDecimal value) {
        return plain(value.setScale(SCALE, RoundingMode.HALF_UP)).replace('.', decimalSeparator);
    }

    /** Siegesquote im Scoreboard ({@code 0,00} + {@code " %"}): {@code 69,49 %}, {@code 84,21 %}. */
    public String scoreboardWinrate(long wins, long games) {
        return fixed2(winrate(wins, games)) + " %";
    }

    // ------------------------------------------------------------------ Dauer und Zeit

    /**
     * Restzeit absteigend ab der größten Einheit ≠ 0 bis Sekunden (Spec 01 §6.10): {@code 29d 13h 42m 34s},
     * {@code 13h 0m 5s}, {@code 45s}. Bruchteile von Sekunden fallen weg, negative Dauern gelten als 0.
     */
    public String duration(Duration duration) {
        long total = Math.max(0, Objects.requireNonNull(duration, "duration").getSeconds());
        long days = total / 86_400;
        long hours = total % 86_400 / 3_600;
        long minutes = total % 3_600 / 60;
        long seconds = total % 60;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m " + seconds + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /** Countdown {@code mm:ss}, ab einer Stunde {@code hh:mm:ss} (Spec 02 §6.8); negative Werte gelten als 0. */
    public String countdown(long seconds) {
        long total = Math.max(0, seconds);
        long hours = total / 3_600;
        long minutes = total % 3_600 / 60;
        long rest = total % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, rest);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, rest);
    }

    /** Wie {@link #countdown(long)}; Bruchteile von Sekunden fallen weg. */
    public String countdown(Duration duration) {
        return countdown(Objects.requireNonNull(duration, "duration").getSeconds());
    }

    /** Datum in Europe/Berlin: {@code dd.MM.yyyy} ({@code en_US}: {@code MM/dd/yyyy}). */
    public String date(Instant instant) {
        return date.format(instant.atZone(ZONE));
    }

    /** Uhrzeit in Europe/Berlin: {@code HH:mm}. */
    public String time(Instant instant) {
        return TIME.format(instant.atZone(ZONE));
    }

    /** Datum und Uhrzeit in Europe/Berlin: {@code dd.MM.yyyy HH:mm}. */
    public String dateTime(Instant instant) {
        return date(instant) + " " + time(instant);
    }

    /** Saison-Zeitraum {@code MMM-MMM yyyy} mit englischen Monatskürzeln: {@code Jul-Sep 2026} (Jahr des Endes). */
    public String season(YearMonth start, YearMonth end) {
        return start.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + "-"
                + end.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + end.getYear();
    }

    // ------------------------------------------------------------------ Hilfen

    private static String plain(BigDecimal value) {
        String text = value.toPlainString();
        return "-0".equals(text) ? "0" : text;
    }

    private static String group(long value, char separator) {
        String digits = Long.toString(value);
        boolean negative = digits.startsWith("-");
        if (negative) {
            digits = digits.substring(1);
        }
        StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        int lead = digits.length() % 3 == 0 ? 3 : digits.length() % 3;
        out.append(digits, 0, lead);
        for (int i = lead; i < digits.length(); i += 3) {
            out.append(separator).append(digits, i, i + 3);
        }
        return negative ? "-" + out : out.toString();
    }

    private static void requireNotNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " darf nicht negativ sein: " + value);
        }
    }
}
