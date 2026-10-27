package net.retromc.core.api.punish;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Prüft Chat- und Proxy-Nachrichten auf Mute und Spam (Spec 01 §6.16, §8.1; Zustand {@code spam:{uuid}}, 30 s).
 * Velocity bricht signierte Chatnachrichten nie ab; der normale Chat wird auf paper-core geprüft (§6.10).
 */
public interface ChatGuard {

    CompletableFuture<ChatCheck> check(UUID player, String text, ChatContext context);
}
