package net.retromc.platform.common.stats;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.retromc.core.api.economy.WalletService;
import net.retromc.core.api.rank.RankService;
import net.retromc.core.api.stats.DisplayAnchorStore;
import net.retromc.core.api.stats.NetworkLevelService;
import net.retromc.core.api.stats.StatsStore;
import net.retromc.core.api.util.Registration;
import net.retromc.core.data.AsyncDatabase;
import net.retromc.core.data.EventSink;
import net.retromc.core.data.ValkeyConnector;
import net.retromc.core.data.economy.WalletRepository;
import net.retromc.core.data.stats.DisplayAnchorRepository;
import net.retromc.core.data.stats.NetworkLevelRepository;
import net.retromc.core.data.stats.StatsRepository;
import net.retromc.core.messaging.MessageBus;
import net.retromc.platform.common.service.ServiceRegistry;
import net.retromc.platform.common.social.BusEventSink;
import org.slf4j.Logger;

/**
 * Registriert die Standard-Datendienste von Spec 02 in der Service-Registry: {@link StatsStore},
 * {@link WalletService}, {@link DisplayAnchorStore} und {@link NetworkLevelService}. Datenbank, Valkey und Bus kommen
 * aus der Registry, Ränge über {@link ServiceRegistry#supplier} (ersetzbar). Jeder Dienst ist einzeln per
 * {@code register(...)} austauschbar.
 */
public final class StatsServices implements AutoCloseable {

    private final StatefulRedisConnection<String, String> connection;
    private final List<Registration> registrations;

    private StatsServices(StatefulRedisConnection<String, String> connection, List<Registration> registrations) {
        this.connection = connection;
        this.registrations = registrations;
    }

    /**
     * Öffnet eine Valkey-Verbindung und registriert die Dienste.
     *
     * @throws IllegalStateException wenn Datenbank, Valkey, Bus oder Ränge nicht registriert sind
     */
    public static StatsServices register(ServiceRegistry services, Clock clock, Logger log) {
        AsyncDatabase database = services.get(AsyncDatabase.class);
        services.get(MessageBus.class);
        services.get(RankService.class);
        StatefulRedisConnection<String, String> connection = services.get(ValkeyConnector.class).connect();
        RedisAsyncCommands<String, String> valkey = connection.async();
        EventSink events = new BusEventSink(services.supplier(MessageBus.class), log);
        List<Registration> registrations = new ArrayList<>();
        try {
            registrations.add(services.registerDefault(StatsStore.class, new StatsRepository(database, valkey,
                    services.supplier(RankService.class), events, clock)));
            registrations.add(services.registerDefault(WalletService.class, new WalletRepository(database, valkey,
                    events, clock)));
            registrations.add(services.registerDefault(DisplayAnchorStore.class,
                    new DisplayAnchorRepository(database)));
            registrations.add(services.registerDefault(NetworkLevelService.class,
                    new NetworkLevelRepository(database, events, clock)));
        } catch (RuntimeException e) {
            Collections.reverse(registrations);
            registrations.forEach(Registration::close);
            connection.close();
            throw e;
        }
        return new StatsServices(connection, List.copyOf(registrations));
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
