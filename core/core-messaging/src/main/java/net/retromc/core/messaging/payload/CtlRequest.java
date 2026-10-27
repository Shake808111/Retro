package net.retromc.core.messaging.payload;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code CTL_REQUEST {actor, cmd, args[]}} (net:controller; Spec 08 §8.3): Velocity → Controller ({@code /cloud}).
 *
 * @param actor ausführender Spieler; {@code null} = CLI/System
 * @param cmd Unterbefehl, z. B. {@code list}, {@code start}
 */
public record CtlRequest(@Nullable UUID actor, String cmd, List<String> args) {

    public CtlRequest {
        Objects.requireNonNull(cmd, "cmd");
        args = List.copyOf(args);
    }
}
