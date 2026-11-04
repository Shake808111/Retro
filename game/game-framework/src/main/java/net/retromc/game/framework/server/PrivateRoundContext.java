package net.retromc.game.framework.server;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Privatrunde dieses Servers (Spec 03 §6.11, §8.1 {@code PrivateRoundContext}): der Controller startet den Server
 * mit {@code PRIVATE_ID} (und {@code PRIVATE_OWNER}). Besitzer dürfen {@code /start} ohne Mindestspieler und
 * {@code /forcemap} ohne Limit; Privatrunden werden nicht gewertet ({@code round.private}).
 */
public final class PrivateRoundContext {

    /** Umgebungsvariable der Privatrunden-ID. */
    public static final String ENV_ID = "PRIVATE_ID";
    /** Umgebungsvariable des Besitzers (UUID). */
    public static final String ENV_OWNER = "PRIVATE_OWNER";

    /** Eine Privatrunde. */
    public record PrivateRound(String id, @Nullable UUID owner) {
    }

    private final @Nullable PrivateRound round;

    public PrivateRoundContext(@Nullable PrivateRound round) {
        this.round = round;
    }

    /** Liest {@code PRIVATE_ID}/{@code PRIVATE_OWNER}; ungültige Besitzer-UUID = kein Besitzer. */
    public static PrivateRoundContext fromEnv(Function<String, @Nullable String> env) {
        String id = env.apply(ENV_ID);
        if (id == null || id.isBlank()) {
            return new PrivateRoundContext(null);
        }
        UUID owner = null;
        String raw = env.apply(ENV_OWNER);
        if (raw != null && !raw.isBlank()) {
            try {
                owner = UUID.fromString(raw.strip());
            } catch (IllegalArgumentException e) {
                owner = null;
            }
        }
        return new PrivateRoundContext(new PrivateRound(id.strip(), owner));
    }

    public Optional<PrivateRound> current() {
        return Optional.ofNullable(round);
    }

    public boolean isOwner(Player player) {
        return round != null && player.getUniqueId().equals(round.owner());
    }
}
