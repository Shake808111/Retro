package net.retromc.platform.common.command;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.exception.handling.ExceptionController;
import org.slf4j.Logger;

/**
 * Registriert Fehlerbehandler für alle Ausnahmen, die Cloud beim Parsen und Ausführen wirft. Die Meldung kommt aus
 * dem {@link CommandErrorMapper}; Ausführungsfehler werden zusätzlich mit Stacktrace protokolliert.
 */
public final class CommandErrorHandlers {

    private static final List<Class<? extends Throwable>> HANDLED = List.of(
            InvalidSyntaxException.class,
            NoPermissionException.class,
            InvalidCommandSenderException.class,
            ArgumentParseException.class,
            NoSuchCommandException.class,
            CommandExecutionException.class);

    private CommandErrorHandlers() {
    }

    /**
     * Installiert die Behandler; später registrierte Behandler haben in Cloud Vorrang vor den Standardbehandlern der
     * Plattform.
     *
     * @param reply sendet die Meldung an den Absender (Sprache wählt die Plattform)
     */
    public static <C> void install(CommandManager<C> manager, CommandErrorMapper mapper,
            BiConsumer<C, CommandError> reply, Logger log) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(log, "log");
        ExceptionController<C> controller = manager.exceptionController();
        for (Class<? extends Throwable> type : HANDLED) {
            register(controller, type, mapper, reply, log);
        }
    }

    private static <C, T extends Throwable> void register(ExceptionController<C> controller, Class<T> type,
            CommandErrorMapper mapper, BiConsumer<C, CommandError> reply, Logger log) {
        controller.registerHandler(type, context -> {
            if (context.exception() instanceof CommandExecutionException execution) {
                log.error("Befehl '{}' fehlgeschlagen", context.context().rawInput().input(), execution.getCause());
            }
            reply.accept(context.context().sender(), mapper.map(context.exception()));
        });
    }
}
