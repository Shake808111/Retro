package net.retromc.core.api.msg;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.retromc.core.api.BuildInfo;

/**
 * Plugin-Message über den Kanal {@code <brandId>:bridge} (heute {@code retromc:bridge}, Spec 01 §2.4):
 * {@code RUN_COMMAND <commandLine>} (Paper → Velocity, GUI-Klick führt einen Proxy-Befehl als Spieler aus) und
 * {@code OPEN_GUI <guiId> <arg>} (Velocity → Paper). Format: UTF-8-Text {@code <AKTION> <argument>}.
 */
public record BridgeMessage(Action action, String argument) {

    /** Kanalname mit Namespace der Marke (Spec 00 §4.2). */
    public static final String CHANNEL = BuildInfo.BRAND_ID + ":bridge";

    /** Aktionen der Bridge. */
    public enum Action {
        RUN_COMMAND,
        OPEN_GUI
    }

    public BridgeMessage {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(argument, "argument");
        if (argument.isBlank()) {
            throw new IllegalArgumentException("Argument darf nicht leer sein");
        }
    }

    /** Befehlszeile ohne führenden Schrägstrich, z. B. {@code party promote Name}. */
    public static BridgeMessage runCommand(String commandLine) {
        String line = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        return new BridgeMessage(Action.RUN_COMMAND, line);
    }

    /** GUI öffnen; {@code arg} darf leer sein. */
    public static BridgeMessage openGui(String guiId, String arg) {
        if (guiId.isBlank() || guiId.contains(" ")) {
            throw new IllegalArgumentException("GUI-ID ohne Leerzeichen erwartet: '" + guiId + "'");
        }
        return new BridgeMessage(Action.OPEN_GUI, arg.isBlank() ? guiId : guiId + " " + arg);
    }

    public byte[] encode() {
        return (action.name() + " " + argument).getBytes(StandardCharsets.UTF_8);
    }

    public static BridgeMessage decode(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        int space = text.indexOf(' ');
        if (space <= 0) {
            throw new IllegalArgumentException("Ungültige Bridge-Nachricht");
        }
        return new BridgeMessage(Action.valueOf(text.substring(0, space)), text.substring(space + 1));
    }
}
