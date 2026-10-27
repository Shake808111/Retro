package net.retromc.core.api.punish;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.retromc.core.api.util.IpHmac;
import org.jspecify.annotations.Nullable;

/**
 * Strafen (Spec 01 §3.6 #77–82, §6.3 Schritt 2, §6.10, §8.1). Sendet {@code PUNISH_ISSUED}, {@code PUNISH_REVOKED},
 * {@code MUTE_UPDATE} und {@code KICK} und pflegt die Caches {@code mute:{uuid}} und {@code ban:none:{uuid}}.
 * Genutzt von Velocity (Login, Befehle), paper-core (Chat-Mute), Anticheat und {@code services:api} (Entbannung).
 */
public interface PunishmentService {

    /** {@code reason_code} eines Kicks ({@code /kick <Spieler> <Grund-Text>}, freier Text in {@code reason_text}). */
    String KICK_REASON_CODE = "kick";

    /**
     * Aktiver Bann: {@code type='BAN' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > now())},
     * zusätzlich IP-Bann über {@code moderation.ban_ip}; Negativ-Cache 60 s.
     */
    CompletableFuture<Optional<Punishment>> activeBan(UUID player, @Nullable IpHmac ip);

    /** Aktiver Mute (Cache {@code mute:{uuid}}). */
    CompletableFuture<Optional<Punishment>> activeMute(UUID player);

    /** Bann oder Mute verhängen; Stufe nach {@link PunishmentReason#stageFor}, ID nach {@link PunishmentId}. */
    CompletableFuture<Punishment> issue(PunishmentRequest request);

    /** Kick vom Netzwerk verbuchen und {@code KICK} senden. */
    CompletableFuture<Punishment> kick(UUID player, String reasonText, @Nullable UUID actor, @Nullable String server);

    /** Aktive Strafe des Typs aufheben ({@code /unban <Spieler>}, {@code /unmute <Spieler>}). */
    CompletableFuture<Optional<Punishment>> revokeActive(UUID player, PunishmentType type, UUID actor, String note);

    /** Strafe per Anzeige-ID aufheben ({@code /unban <ID>}). */
    CompletableFuture<Optional<Punishment>> revoke(String publicId, UUID actor, String note);

    CompletableFuture<Optional<Punishment>> byPublicId(String publicId);

    /** {@code /history}: alle Strafen, neueste zuerst. */
    CompletableFuture<List<Punishment>> history(UUID player);

    /** Alle Gründe aus {@code moderation.reason}. */
    CompletableFuture<List<PunishmentReason>> reasons();

    CompletableFuture<Optional<PunishmentReason>> reason(String code);
}
