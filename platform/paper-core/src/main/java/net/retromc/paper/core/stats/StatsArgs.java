package net.retromc.paper.core.stats;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import net.retromc.core.api.stats.ModeKey;
import net.retromc.core.api.stats.StatWindow;
import net.retromc.core.api.stats.StatsMode;
import org.jspecify.annotations.Nullable;

/**
 * Argumente von {@code /stats}, {@code /statsall} und {@code /statsd} ohne Server (Spec 02 §3): Modus = Modus des
 * Servers; auf Servern ohne Modus steht er vorne ({@code /stats <Modus> [Spieler|#Platz]}, Annahme O14). Ziel ist
 * leer (eigene Stats), ein Name oder {@code #N} (Spieler auf Platz N).
 */
public final class StatsArgs {

    private StatsArgs() {
    }

    /**
     * Geprüfte Anfrage.
     *
     * @param target leer = eigene Stats, {@code #N} oder Name
     */
    public record Request(ModeKey mode, StatWindow window, String target) {

        public Request {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(target, "target");
        }

        /** Platz bei {@code #N}. */
        public OptionalInt rank() {
            if (!target.startsWith("#")) {
                return OptionalInt.empty();
            }
            try {
                int position = Integer.parseInt(target.substring(1));
                return position >= 1 ? OptionalInt.of(position) : OptionalInt.empty();
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }
    }

    /**
     * Ergebnis: Anfrage oder Fehlerschlüssel mit Argument ({@code stats.need-mode}, {@code stats.unknown-mode},
     * {@code stats.days-invalid}).
     */
    public record Parsed(@Nullable Request request, @Nullable String error, String argument) {

        static Parsed ok(Request request) {
            return new Parsed(request, null, "");
        }

        static Parsed error(String key, String argument) {
            return new Parsed(null, key, argument);
        }
    }

    /** {@code /stats} und {@code /statsall}. */
    public static Parsed stats(String input, StatWindow window, Optional<ModeKey> serverMode,
            Function<String, Optional<StatsMode>> modes) {
        List<String> args = split(input);
        if (serverMode.isPresent()) {
            return Parsed.ok(new Request(serverMode.get(), window, args.isEmpty() ? "" : args.getFirst()));
        }
        if (args.isEmpty()) {
            return Parsed.error("stats.need-mode", "");
        }
        Optional<StatsMode> mode = modes.apply(args.getFirst());
        if (mode.isEmpty()) {
            return Parsed.error("stats.unknown-mode", args.getFirst());
        }
        return Parsed.ok(new Request(mode.get().key(), window, args.size() > 1 ? args.get(1) : ""));
    }

    /** {@code /statsd <Tage> [Spieler]} bzw. ohne Server-Modus {@code /statsd <Modus> <Tage> [Spieler]}. */
    public static Parsed days(String input, Optional<ModeKey> serverMode,
            Function<String, Optional<StatsMode>> modes) {
        List<String> args = split(input);
        ModeKey mode;
        int next = 0;
        if (serverMode.isPresent()) {
            mode = serverMode.get();
        } else {
            if (args.isEmpty()) {
                return Parsed.error("stats.need-mode", "");
            }
            Optional<StatsMode> found = modes.apply(args.getFirst());
            if (found.isEmpty()) {
                return Parsed.error("stats.unknown-mode", args.getFirst());
            }
            mode = found.get().key();
            next = 1;
        }
        if (args.size() <= next) {
            return Parsed.error("stats.days-invalid", "");
        }
        int days;
        try {
            days = Integer.parseInt(args.get(next));
        } catch (NumberFormatException e) {
            return Parsed.error("stats.days-invalid", args.get(next));
        }
        if (!StatWindow.validDays(days)) {
            return Parsed.error("stats.days-invalid", args.get(next));
        }
        return Parsed.ok(new Request(mode, StatWindow.lastDays(days), args.size() > next + 1 ? args.get(next + 1)
                : ""));
    }

    static List<String> split(String input) {
        String text = input.strip();
        return text.isEmpty() ? List.of() : Arrays.asList(text.split("\\s+"));
    }
}
