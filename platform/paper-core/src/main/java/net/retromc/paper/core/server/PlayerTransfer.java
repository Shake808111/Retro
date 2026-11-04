package net.retromc.paper.core.server;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Spieler verschieben (Spec 08 §8.1 {@code PlayerTransfer}) über {@code net:proxy}: {@code CONNECT} bzw.
 * {@code SEND_LOBBY {uuids, reasonKey, mode?}}; der Proxy wählt die Lobby ({@code LobbyBalancer}), bei {@code mode}
 * bevorzugt eine Modus-Lobby, Partys bleiben zusammen.
 */
public interface PlayerTransfer {

    CompletableFuture<Void> toServer(Player player, String server);

    CompletableFuture<Void> toLobby(Collection<UUID> players, String reasonKey, @Nullable String mode);
}
