package net.retromc.platform.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.retromc.core.api.util.Registration;
import org.junit.jupiter.api.Test;

/** Registrieren, Abfragen, Abmelden und Spiegelung der Service-Registry. */
class ServiceRegistryTest {

    private interface Greeter {
        String greet();
    }

    private interface Counter {
        int count();
    }

    /** Protokolliert Listener-Aufrufe wie die Spiegelung in den Bukkit-ServicesManager. */
    private static final class Mirror implements ServiceRegistry.Listener {

        private final List<String> calls = new ArrayList<>();

        @Override
        public <T> void registered(Class<T> type, T service) {
            calls.add("+" + type.getSimpleName());
        }

        @Override
        public <T> void unregistered(Class<T> type, T service) {
            calls.add("-" + type.getSimpleName());
        }
    }

    @Test
    void registriertUndLiefertDienste() {
        ServiceRegistry registry = new ServiceRegistry();
        Greeter greeter = () -> "Hallo";
        registry.register(Greeter.class, greeter);

        assertSame(greeter, registry.get(Greeter.class));
        assertTrue(registry.find(Greeter.class).isPresent());
        assertTrue(registry.find(Counter.class).isEmpty());
        assertEquals(List.of(Greeter.class), registry.types());
    }

    @Test
    void fehlenderDienstNenntDieSchnittstelle() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ServiceRegistry().get(Counter.class));

        assertTrue(error.getMessage().contains(Counter.class.getName()));
    }

    @Test
    void jedeSchnittstelleNurEinmal() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(Greeter.class, () -> "a");

        assertThrows(IllegalStateException.class, () -> registry.register(Greeter.class, () -> "b"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void implementierungMussZurSchnittstellePassen() {
        ServiceRegistry registry = new ServiceRegistry();
        Class raw = Counter.class;

        assertThrows(IllegalArgumentException.class, () -> registry.register(raw, (Greeter) () -> "a"));
    }

    @Test
    void registrierungMeldetGenauDieseImplementierungAb() {
        Mirror mirror = new Mirror();
        ServiceRegistry registry = new ServiceRegistry(mirror);
        Greeter first = () -> "erst";
        Registration registration = registry.register(Greeter.class, first);
        registration.close();
        Greeter second = () -> "dann";
        registry.register(Greeter.class, second);

        // Die alte Registrierung darf den neuen Dienst nicht abmelden.
        registration.close();
        assertFalse(registry.unregister(Greeter.class, first));

        assertSame(second, registry.get(Greeter.class));
        assertEquals(List.of("+Greeter", "-Greeter", "+Greeter"), mirror.calls);
    }

    @Test
    void leerenMeldetInUmgekehrterReihenfolgeAb() {
        Mirror mirror = new Mirror();
        ServiceRegistry registry = new ServiceRegistry(mirror);
        registry.register(Greeter.class, () -> "a");
        registry.register(Counter.class, () -> 1);

        registry.clear();

        assertEquals(List.of("+Greeter", "+Counter", "-Counter", "-Greeter"), mirror.calls);
        assertTrue(registry.types().isEmpty());
    }
}
