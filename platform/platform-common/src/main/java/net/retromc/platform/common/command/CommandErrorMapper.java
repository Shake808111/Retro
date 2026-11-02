package net.retromc.platform.common.command;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.exception.handling.ExceptionController;
import org.incendo.cloud.exception.parsing.NumberParseException;
import org.incendo.cloud.exception.parsing.ParserException;

/**
 * Ordnet Ausnahmen von Incendo Cloud einem {@link CommandError} aus dem Textkatalog zu. Die englischen Standardtexte
 * von Cloud erreichen so nie einen Spieler.
 */
public final class CommandErrorMapper {

    /** Falsche Syntax; Platzhalter {@code syntax}. */
    public static final String USAGE = "error.command.usage";
    /** Befehl nur für Spieler. */
    public static final String PLAYER_ONLY = "error.command.player-only";
    /** Ungültiges Argument; Platzhalter {@code input}. */
    public static final String ARGUMENT = "error.command.argument";
    /** Ungültiges Argument ohne bekannte Eingabe. */
    public static final String INVALID = "error.command.invalid";
    /** Keine Zahl; Platzhalter {@code input}. */
    public static final String NUMBER = "error.command.number";
    /** Zahl außerhalb des Bereichs; Platzhalter {@code input}, {@code min}, {@code max}. */
    public static final String RANGE = "error.command.range";
    /** Unbekannter Befehl. */
    public static final String UNKNOWN = "error.command.unknown";
    /** Fehler beim Ausführen. */
    public static final String FAILED = "error.command.failed";
    /** Keine Berechtigung (Spec 01 §7). */
    public static final String NO_PERMISSION = "general.no-permission";
    /** Spieler nicht gefunden; Platzhalter {@code player} (Spec 01 §7). */
    public static final String PLAYER_UNKNOWN = "error.player.unknown";

    /** Cloud-Caption für unbekannte Spieler (cloud-bukkit und cloud-velocity). */
    static final String CAPTION_PLAYER = "argument.parse.failure.player";
    /** Cloud-Caption für unbekannte Offline-Spieler (cloud-bukkit). */
    static final String CAPTION_OFFLINE_PLAYER = "argument.parse.failure.offlineplayer";

    private final Predicate<Type> playerType;

    /**
     * @param playerType erkennt den Spielertyp der Plattform unter den verlangten Absendertypen
     */
    public CommandErrorMapper(Predicate<Type> playerType) {
        this.playerType = Objects.requireNonNull(playerType, "playerType");
    }

    /** Der Text zu einer Ausnahme; unbekannte Ausnahmen gelten als Ausführungsfehler. */
    public CommandError map(Throwable exception) {
        Throwable error = ExceptionController.unwrapCompletionException(exception);
        return switch (error) {
            case InvalidSyntaxException syntax -> new CommandError(USAGE, Map.of("syntax", syntax.correctSyntax()));
            case NoPermissionException ignored -> new CommandError(NO_PERMISSION);
            case InvalidCommandSenderException sender -> sender.requiredSenderTypes().stream().anyMatch(playerType)
                    ? new CommandError(PLAYER_ONLY)
                    : new CommandError(NO_PERMISSION);
            case ArgumentParseException argument -> argument(argument.getCause());
            case NoSuchCommandException ignored -> new CommandError(UNKNOWN);
            default -> new CommandError(FAILED);
        };
    }

    private static CommandError argument(Throwable cause) {
        if (cause instanceof NumberParseException number) {
            return number.hasMin() && number.hasMax()
                    ? new CommandError(RANGE, Map.of("input", number.input(),
                            "min", String.valueOf(number.range().min()), "max", String.valueOf(number.range().max())))
                    : new CommandError(NUMBER, Map.of("input", number.input()));
        }
        if (!(cause instanceof ParserException parser)) {
            return new CommandError(INVALID);
        }
        Map<String, String> variables = new HashMap<>();
        for (CaptionVariable variable : parser.captionVariables()) {
            variables.put(variable.key(), variable.value());
        }
        String input = variables.get("input");
        if (input == null || input.isEmpty()) {
            return new CommandError(INVALID);
        }
        String caption = parser.errorCaption().key();
        if (caption.equals(CAPTION_PLAYER) || caption.equals(CAPTION_OFFLINE_PLAYER)) {
            return new CommandError(PLAYER_UNKNOWN, Map.of("player", input));
        }
        return new CommandError(ARGUMENT, Map.of("input", input));
    }
}
