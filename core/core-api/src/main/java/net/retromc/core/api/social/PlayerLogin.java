package net.retromc.core.api.social;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.retromc.core.api.util.IpHmac;
import org.jspecify.annotations.Nullable;

/**
 * Daten eines Logins für {@code ProfileService.recordLogin} (Spec 01 §6.3 Schritt 4): Upsert {@code core.player}
 * (Name, Namens-Historie), {@code core.player_ip}, Sprache beim ersten Join, {@code profile:{uuid}}.
 *
 * @param ip HMAC der IP-Adresse (nie die Adresse selbst) oder {@code null}
 * @param clientLocale Sprache des Clients, z. B. {@code de_at}; nur beim ersten Join ausgewertet
 */
public record PlayerLogin(UUID uuid, String name, @Nullable IpHmac ip, String clientLocale, String proxy, Instant at) {

    public PlayerLogin {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(clientLocale, "clientLocale");
        Objects.requireNonNull(proxy, "proxy");
        Objects.requireNonNull(at, "at");
    }
}
