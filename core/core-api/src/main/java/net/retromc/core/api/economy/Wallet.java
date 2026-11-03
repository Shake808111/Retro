package net.retromc.core.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Kontostand ({@code economy.wallet}, Spec 02 §2.1); ohne Zeile alles 0. */
public record Wallet(UUID uuid, long coins, long gold, long resetTokens) {

    public Wallet {
        Objects.requireNonNull(uuid, "uuid");
        if (coins < 0 || gold < 0 || resetTokens < 0) {
            throw new IllegalArgumentException("Negativer Kontostand");
        }
    }

    public static Wallet empty(UUID uuid) {
        return new Wallet(uuid, 0, 0, 0);
    }

    public long balance(Currency currency) {
        return switch (currency) {
            case COINS -> coins;
            case GOLD -> gold;
            case RESET_TOKEN -> resetTokens;
        };
    }
}
