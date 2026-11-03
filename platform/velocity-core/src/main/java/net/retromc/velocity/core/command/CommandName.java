package net.retromc.velocity.core.command;

import com.velocitypowered.api.command.CommandSource;
import java.util.List;
import java.util.Objects;
import org.incendo.cloud.Command;
import org.incendo.cloud.velocity.VelocityCommandManager;

/**
 * Wurzelbefehl mit echten Aliasen (Spec 01 §3: „Kurzbefehle sind echte Aliase“). Features registrieren ihre Befehle
 * über diese Namen; der Abnahmetest A25 prüft die Liste gegen die Spec.
 */
public record CommandName(String name, List<String> aliases) {

    public CommandName {
        Objects.requireNonNull(name, "name");
        aliases = List.copyOf(aliases);
    }

    public static CommandName of(String name, String... aliases) {
        return new CommandName(name, List.of(aliases));
    }

    /** Neuer Builder für diesen Befehl. */
    public Command.Builder<CommandSource> builder(VelocityCommandManager<CommandSource> manager) {
        return manager.commandBuilder(name, aliases.toArray(String[]::new));
    }
}
