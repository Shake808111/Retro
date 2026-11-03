package net.retromc.game.framework.kill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Herzzeile (Spec 03 §6.9): immer 10 Zeichen, {@code floor(hp / 2)} × {@code §c♥}, danach {@code §f♡}
 * (19 HP → 9 rot + 1 leer).
 */
public final class HeartLine {

    /** Anzahl Zeichen. */
    public static final int LENGTH = 10;

    private HeartLine() {
    }

    /** Zeile in §-Notation. */
    public static String of(double hp) {
        int full = (int) Math.clamp(Math.floor(hp / 2), 0, LENGTH);
        return "§c♥".repeat(full) + "§f♡".repeat(LENGTH - full);
    }

    /** Zeile als Komponente. */
    public static Component component(double hp) {
        return LegacyComponentSerializer.legacySection().deserialize(of(hp));
    }
}
