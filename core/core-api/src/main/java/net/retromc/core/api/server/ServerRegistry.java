package net.retromc.core.api.server;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.retromc.core.api.util.Registration;
import org.jspecify.annotations.Nullable;

/**
 * Lesender Zugriff auf die Server-Registry (Spec 08 §8.1). Die Implementierung (core-data) hält einen lokalen
 * Spiegel aus {@code servers:all}/{@code server:*}, aktualisiert über {@code net:server-state} und einen vollen
 * Abgleich alle 30 s. Die Lesemethoden greifen deshalb nur auf den Spiegel zu (kein Netzwerkzugriff,
 * sicher auf dem Haupt-Thread); {@link #refresh()} gleicht asynchron mit Valkey ab.
 */
public interface ServerRegistry {

    Optional<ServerInfo> get(String name);

    /** Server eines Templates, sortiert nach laufender Nummer. */
    List<ServerInfo> byTemplate(String templateId);

    /** Server einer Kategorie, sortiert nach Template, dann Nummer (Lobbywechsler, Spec 08 §5.4). */
    List<ServerInfo> byCategory(ServerCategory category);

    /** Server eines Modus; {@code variant == null} = alle Varianten (Navigator, Spec 03). */
    List<ServerInfo> byMode(String mode, @Nullable String variant);

    /** Alle bekannten Server. */
    List<ServerInfo> all();

    /** Spieler im Netzwerk ({@code count:online}, Summe aller Proxys). */
    int onlineTotal();

    /** Laufende Proxys ({@code proxies}, {@code proxy:{id}}). */
    List<ProxyInfo> proxies();

    /** Meldet Registrierungen, Änderungen und Abmeldungen; {@link Registration#close()} beendet das Abo. */
    Registration subscribe(Consumer<ServerEvent> listener);

    /** Voller Abgleich mit Valkey (fehlende registrieren, abgelaufene abmelden). */
    CompletableFuture<Void> refresh();
}
