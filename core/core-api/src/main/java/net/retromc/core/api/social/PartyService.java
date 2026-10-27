package net.retromc.core.api.social;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Party (Spec 01 §3.3, §5.1, §6.5, §8.1). Genutzt von Velocity (Befehle), paper-core (Partymenü), Controller/Queue
 * (Party-Ticket) und Spiel-Rahmen (Team-Zuteilung, Tab „[Party]“). Ereignisse {@code PARTY_UPDATE},
 * {@code PARTY_INVITE}, {@code PARTY_DISBAND} sendet die Implementierung.
 */
public interface PartyService {

    /** Gültigkeit einer Einladung (Spec 01 O14). */
    Duration INVITE_TTL = Duration.ofSeconds(60);

    /** Gültigkeit einer Beitrittsanfrage an eine öffentliche Party (§6.5 Nr. 6). */
    Duration REQUEST_TTL = Duration.ofSeconds(60);

    /** Offline-Karenz {@code party:grace:{uuid}} (Spec 01 O14). */
    Duration OFFLINE_GRACE = Duration.ofSeconds(300);

    /** Party mit nur einem Mitglied und ohne offene Einladung wird danach aufgelöst (§6.5 Nr. 7). */
    Duration ABANDONED_AFTER = Duration.ofSeconds(60);

    /** Lebensdauer der Party-Schlüssel ab letzter Aktivität (Spec 01 §2.3). */
    Duration IDLE_TTL = Duration.ofMinutes(30);

    CompletableFuture<Optional<Party>> partyOf(UUID player);

    CompletableFuture<Optional<Party>> party(String partyId);

    /** Einladen; ohne Party wird eine erstellt (Leiter = Einladender). */
    CompletableFuture<PartyResult> invite(UUID inviter, UUID target);

    /** Einladung von {@code inviter} annehmen. */
    CompletableFuture<PartyResult> accept(UUID target, UUID inviter);

    CompletableFuture<PartyResult> deny(UUID target, UUID inviter);

    /** Offene Einladungen an den Spieler (abgelaufene fehlen). */
    CompletableFuture<List<PartyInvite>> invites(UUID target);

    /** Verlassen; der Leiter übergibt an das älteste Mitglied, die letzte Person löst auf. */
    CompletableFuture<PartyResult> leave(UUID player);

    CompletableFuture<PartyResult> kick(UUID leader, UUID target);

    /** Leitung sofort übertragen; {@code maxSize} wird aus dem Rang des neuen Leiters neu bestimmt. */
    CompletableFuture<PartyResult> promote(UUID leader, UUID target);

    CompletableFuture<PartyResult> disband(UUID leader);

    /** Öffentlich/privat ({@code /party public}, ab Premium). */
    CompletableFuture<PartyResult> setPublic(UUID leader, boolean publicParty);

    CompletableFuture<PartyResult> setPublicFilter(UUID leader, PublicFilter filter);

    /** Beitritt nur per Anfrage ({@code /party requests an|aus}). */
    CompletableFuture<PartyResult> setRequireRequest(UUID leader, boolean requireRequest);

    /** Öffentlicher Party des Spielers {@code member} beitreten oder Anfrage stellen ({@code /party join}). */
    CompletableFuture<PartyResult> join(UUID player, UUID member);

    /** Zufällige öffentliche Party mit Platz ({@code /party random}). */
    CompletableFuture<PartyResult> joinRandom(UUID player);

    /** Beitrittsanfrage annehmen ({@code /party accept <Spieler>} durch den Leiter). */
    CompletableFuture<PartyResult> acceptRequest(UUID leader, UUID requester);

    /** Offene Beitrittsanfragen an die Party des Leiters. */
    CompletableFuture<List<UUID>> joinRequests(UUID leader);

    /** Öffentliche Partys ({@code party:public}), älteste zuerst. */
    CompletableFuture<List<Party>> publicParties();

    /** Disconnect: Karenz {@code party:grace} setzen (Spec 01 §6.3 Schritt 7). */
    CompletableFuture<Void> markOffline(UUID player);

    /** Login in der Karenz: wieder Mitglied ({@code party.rejoined}); leer, wenn keine Karenz bestand. */
    CompletableFuture<Optional<Party>> rejoin(UUID player);

    /** Karenz abgelaufen: entfernen, ggf. neuer Leiter = ältestes Mitglied. */
    CompletableFuture<PartyResult> expireOffline(UUID player);

    /** Auflösen, wenn nur noch ein Mitglied ohne offene Einladung übrig ist; liefert, ob aufgelöst wurde. */
    CompletableFuture<Boolean> disbandIfAbandoned(String partyId);
}
