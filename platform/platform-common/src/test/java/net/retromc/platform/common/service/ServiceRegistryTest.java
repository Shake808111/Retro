package net.retromc.platform.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.retromc.core.api.util.Registration;
import net.retromc.platform.common.service.ServiceRegistry.Kind;
import org.junit.jupiter.api.Test;

/** Standard, Ersatz, Abmelden und Spiegelung der Service-Registry. */
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
        public <T> void registered(Class<T> type, T service, Kind kind) {
            calls.add("+" + type.getSimpleName() + "/" + kind);
        }

        @Override
        public <T> void unregistered(Class<T> type, T service, Kind kind) {
            calls.add("-" + type.getSimpleName() + "/" + kind);
        }
    }

    private final Mirror mirror = new Mirror();
    private final ServiceRegistry registry = new ServiceRegistry(mirror);

    @Test
    void standardIstAktivSolangeNichtsErsetztIst() {
        Greeter core = () -> "Kern";
        registry.registerDefault(Greeter.class, core);

        assertSame(core, registry.get(Greeter.class));
        assertFalse(registry.isOverridden(Greeter.class));
        assertTrue(registry.find(Counter.class).isEmpty());
        assertEquals(List.of(Greeter.class), registry.types());
    }

    @Test
    void ersatzHatVorrangUndAbmeldenStelltDenStandardWiederHer() {
        Greeter core = () -> "Kern";
        Greeter foreign = () -> "Fremd";
        registry.registerDefault(Greeter.class, core);

        Registration override = registry.register(Greeter.class, foreign);
        assertSame(foreign, registry.get(Greeter.class));
        assertTrue(registry.isOverridden(Greeter.class));

        override.close();
        assertSame(core, registry.get(Greeter.class));
        assertEquals(List.of("+Greeter/DEFAULT", "+Greeter/OVERRIDE", "-Greeter/OVERRIDE"), mirror.calls);
    }

    @Test
    void ersatzOhneStandardIstMoeglich() {
        Counter foreign = () -> 3;
        registry.register(Counter.class, foreign);

        assertSame(foreign, registry.get(Counter.class));
    }

    @Test
    void supplierLiefertImmerDieAktuelleImplementierung() {
        Supplier<Greeter> greeter = registry.supplier(Greeter.class);
        registry.registerDefault(Greeter.class, () -> "Kern");
        assertEquals("Kern", greeter.get().greet());

        registry.register(Greeter.class, () -> "Fremd");

        assertEquals("Fremd", greeter.get().greet());
    }

    @Test
    void fehlenderDienstNenntDieSchnittstelle() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> registry.get(Counter.class));

        assertTrue(error.getMessage().contains(Counter.class.getName()));
    }

    @Test
    void jeArtNurEineImplementierung() {
        registry.registerDefault(Greeter.class, () -> "a");
        registry.register(Greeter.class, () -> "b");

        assertThrows(IllegalStateException.class, () -> registry.registerDefault(Greeter.class, () -> "c"));
        assertThrows(IllegalStateException.class, () -> registry.register(Greeter.class, () -> "d"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void implementierungMussZurSchnittstellePassen() {
        Class raw = Counter.class;

        assertThrows(IllegalArgumentException.class, () -> registry.registerDefault(raw, (Greeter) () -> "a"));
    }

    @Test
    void alteRegistrierungMeldetKeineNeueAb() {
        Greeter first = () -> "erst";
        Registration registration = registry.registerDefault(Greeter.class, first);
        registration.close();
        Greeter second = () -> "dann";
        registry.registerDefault(Greeter.class, second);

        registration.close();

        assertFalse(registry.unregister(Greeter.class, first));
        assertSame(second, registry.get(Greeter.class));
    }

    @Test
    void leerenMeldetInUmgekehrterReihenfolgeAb() {
        registry.registerDefault(Greeter.class, () -> "a");
        registry.registerDefault(Counter.class, () -> 1);
        registry.register(Counter.class, () -> 2);

        registry.clear();

        assertEquals(List.of("+Greeter/DEFAULT", "+Counter/DEFAULT", "+Counter/OVERRIDE", "-Counter/OVERRIDE",
                "-Counter/DEFAULT", "-Greeter/DEFAULT"), mirror.calls);
        assertTrue(registry.types().isEmpty());
    }
}
