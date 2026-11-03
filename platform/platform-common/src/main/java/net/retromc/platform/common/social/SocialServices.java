package net.retromc.platform.common.social;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import net.retromc.core.api.punish.ChatGuard;
import net.retromc.core.api.punish.PunishmentService;
import net.retromc.core.api.punish.ReportService;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.server.MaintenanceService;
import net.retromc.core.api.server.NetworkSettingService;
import net.retromc.core.api.social.ClanService;
import net.retromc.core.api.social.FriendService;
import net.retromc.core.api.social.IgnoreService;
import net.retromc.core.api.social.JoinMeService;
import net.retromc.core.api.social.NickService;
import net.retromc.core.api.social.PartyService;
import net.retromc.core.api.social.PresenceService;
import net.retromc.core.api.social.PrivateMessageService;
import net.retromc.core.api.social.ProfileService;
import net.retromc.core.api.social.SettingsService;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.data.punish.PunishmentRepository;
import net.retromc.core.data.punish.ReportRepository;
import net.retromc.core.data.punish.SpamFilter;
import net.retromc.core.data.punish.ValkeyChatGuard;
import net.retromc.core.data.server.MaintenanceRepository;
import net.retromc.core.data.server.NetworkSettingRepository;
import net.retromc.core.data.social.ClanRepository;
import net.retromc.core.data.social.FriendRepository;
import net.retromc.core.data.social.IgnoreRepository;
import net.retromc.core.data.social.NickRepository;
import net.retromc.core.data.social.PlayerRepository;
import net.retromc.core.data.social.PrivacyRepository;
import net.retromc.core.data.social.ValkeyJoinMe;
import net.retromc.core.data.social.ValkeyPartyService;
import net.retromc.core.data.social.ValkeyPresenceService;
import net.retromc.core.data.social.ValkeyPrivateMessages;
import net.retromc.core.messaging.MessageBus;
import net.retromc.platform.common.service.ServiceRegistry;
import org.slf4j.Logger;

/**
 * Registriert die Standard-Dienste für Social und Moderation (Spec 01 §8.1) in der Service-Registry eines
 * Kern-Plugins: Ränge (LuckPerms), Profile, Präsenz, Freunde, Party, JoinMe, Einstellungen, Ignorieren, Clans,
 * Nicks, private Nachrichten, Strafen, Reports, Chat-Prüfung, Wartung und Netzwerk-Einstellungen. Datenbank,
 * Valkey und Bus kommen
 * aus der Registry; die Dienste holen einander über {@link ServiceRegistry#supplier}, damit ein Adapter jeden
 * einzelnen per {@code register(...)} ersetzen kann (z. B. Ränge/Profile eines bestehenden Netzwerk-Kerns).
 */
public final class SocialServices implements AutoCloseable {

    /** Plattform-Angaben für die Dienste. */
    public record Platform(Function<UUID, CompletableFuture<Boolean>> online, Predicate<String> onlineName,
            Set<String> ownDomains, Clock clock) {

        public Platform {
            Objects.requireNonNull(online, "online");
            Objects.requireNonNull(onlineName, "onlineName");
            ownDomains = Set.copyOf(ownDomains);
            Objects.requireNonNull(clock, "clock");
        }
    }

    private final StatefulRedisConnection<String, String> connection;
    private final List<Registration> registrations;

    private SocialServices(StatefulRedisConnection<String, String> connection, List<Registration> registrations) {
        this.connection = connection;
        this.registrations = registrations;
    }

    /**
     * Öffnet eine Valkey-Verbindung und registriert alle Standard-Dienste.
     *
     * @throws IllegalStateException wenn Datenbank, Valkey oder Bus nicht registriert sind
     */
    public static SocialServices register(ServiceRegistry services, Platform platform, Logger log) {
        AsyncDatabase database = services.get(AsyncDatabase.class);
        services.get(MessageBus.class);
        StatefulRedisConnection<String, String> connection = services.get(ValkeyConnector.class).connect();
        RedisAsyncCommands<String, String> valkey = connection.async();
        EventSink events = new BusEventSink(services.supplier(MessageBus.class), log);
        Clock clock = platform.clock();
        List<Registration> registrations = new ArrayList<>();
        try {
            RankService ranks = LuckPermsRankService.create().orElseGet(() -> {
                log.warn("LuckPerms nicht gefunden: alle Spieler gelten als Rang 'default'");
                return LuckPermsRankService.everyoneDefault();
            });
            registrations.add(services.registerDefault(RankService.class, ranks));
            registrations.add(services.registerDefault(ProfileService.class, new PlayerRepository(database, valkey,
                    services.supplier(RankService.class), events)));
            registrations.add(services.registerDefault(PresenceService.class, new ValkeyPresenceService(valkey,
                    events)));
            registrations.add(services.registerDefault(FriendService.class, new FriendRepository(database,
                    services.supplier(PresenceService.class), services.supplier(RankService.class),
                    services.supplier(IgnoreService.class), services.supplier(SettingsService.class), clock)));
            registrations.add(services.registerDefault(PartyService.class, new ValkeyPartyService(valkey,
                    services.supplier(RankService.class), services.supplier(SettingsService.class),
                    services.supplier(FriendService.class), services.supplier(IgnoreService.class),
                    platform.online(), events, clock)));
            registrations.add(services.registerDefault(JoinMeService.class, new ValkeyJoinMe(valkey, clock)));
            registrations.add(services.registerDefault(SettingsService.class,
                    new PrivacyRepository(database, valkey, events)));
            registrations.add(services.registerDefault(IgnoreService.class, new IgnoreRepository(database, valkey,
                    services.supplier(RankService.class))));
            registrations.add(services.registerDefault(ClanService.class, new ClanRepository(database, valkey,
                    services.supplier(RankService.class), services.supplier(IgnoreService.class), events, clock)));
            registrations.add(services.registerDefault(NickService.class, new NickRepository(database, valkey,
                    platform.onlineName(), clock)));
            registrations.add(services.registerDefault(PunishmentService.class, new PunishmentRepository(database,
                    valkey, events, clock)));
            registrations.add(services.registerDefault(ReportService.class, new ReportRepository(database, valkey,
                    services.supplier(RankService.class), events, clock)));
            registrations.add(services.registerDefault(ChatGuard.class, new ValkeyChatGuard(valkey,
                    services.supplier(PunishmentService.class), new SpamFilter(platform.ownDomains()), clock)));
            registrations.add(services.registerDefault(PrivateMessageService.class, new ValkeyPrivateMessages(valkey,
                    services.supplier(FriendService.class), services.supplier(SettingsService.class),
                    services.supplier(IgnoreService.class), services.supplier(RankService.class), platform.online(),
                    events, clock)));
            registrations.add(services.registerDefault(MaintenanceService.class, new MaintenanceRepository(database,
                    valkey, events, clock)));
            registrations.add(services.registerDefault(NetworkSettingService.class,
                    new NetworkSettingRepository(database)));
        } catch (RuntimeException e) {
            Collections.reverse(registrations);
            registrations.forEach(Registration::close);
            connection.close();
            throw e;
        }
        return new SocialServices(connection, List.copyOf(registrations));
    }

    /** Meldet die Dienste ab (umgekehrte Reihenfolge) und schließt die Valkey-Verbindung. */
    @Override
    public void close() {
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        connection.close();
    }
}
