package net.retromc.core.api.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Profilwerte geändert ({@code PROFILE_CHANGED}, net:profile; Spec 02 §8.3): alle Server/Proxy → alle.
 * {@code fields} aus {@link #COINS}, {@link #GOLD}, {@link #RANK}, {@link #LOCALE}, {@link #CLAN}.
 */
public record ProfileChanged(UUID uuid, List<String> fields) {

    public static final String COINS = "coins";
    public static final String GOLD = "gold";
    public static final String RANK = "rank";
    public static final String LOCALE = "locale";
    public static final String CLAN = "clan";

    public ProfileChanged {
        Objects.requireNonNull(uuid, "uuid");
        fields = List.copyOf(fields);
    }
}
