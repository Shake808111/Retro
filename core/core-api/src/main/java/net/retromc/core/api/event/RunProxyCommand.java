package net.retromc.core.api.event;

import java.util.Objects;
import java.util.UUID;

/** Befehl als Spieler auf dem Proxy ausführen ({@code RUN_PROXY_COMMAND}, net:proxy; Spec 01 §2.4, §8.2): Rückfall, wenn die Plugin-Message {@code <brandId>:bridge} nicht geht. */
public record RunProxyCommand(UUID uuid, String commandLine) {

    public RunProxyCommand {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(commandLine, "commandLine");
    }
}
