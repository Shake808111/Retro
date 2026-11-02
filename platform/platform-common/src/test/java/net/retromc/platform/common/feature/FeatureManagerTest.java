package net.retromc.platform.common.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.retromc.platform.common.feature.FeatureStatus.State;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reihenfolge, umgekehrtes Deaktivieren und Fehlerisolation des Feature-Musters. */
class FeatureManagerTest {

    private static final Logger LOG = LoggerFactory.getLogger(FeatureManagerTest.class);

    /** Kontext der Tests: Protokoll der Aufrufe. */
    private record Journal(List<String> calls) {

        Journal() {
            this(new ArrayList<>());
        }
    }

    private record Step(String name, boolean failOnEnable, boolean failOnDisable) implements CoreFeature<Journal> {

        static Step ok(String name) {
            return new Step(name, false, false);
        }

        @Override
        public void enable(Journal journal) throws Exception {
            journal.calls().add("+" + name);
            if (failOnEnable) {
                throw new Exception("Start von " + name + " misslungen");
            }
        }

        @Override
        public void disable(Journal journal) {
            journal.calls().add("-" + name);
            if (failOnDisable) {
                throw new IllegalStateException("Stopp von " + name + " misslungen");
            }
        }
    }

    @Test
    void aktiviertInReihenfolgeUndDeaktiviertUmgekehrt() {
        Journal journal = new Journal();
        FeatureManager<Journal> manager = new FeatureManager<>(List.of(Step.ok("a"), Step.ok("b"), Step.ok("c")), LOG);

        manager.enableAll(journal);
        manager.disableAll(journal);

        assertEquals(List.of("+a", "+b", "+c", "-c", "-b", "-a"), journal.calls());
        assertTrue(manager.statuses().stream().allMatch(status -> status.state() == State.DISABLED));
    }

    @Test
    void fehlerEinesFeaturesStopptDieAnderenNicht() {
        Journal journal = new Journal();
        FeatureManager<Journal> manager = new FeatureManager<>(
                List.of(Step.ok("a"), new Step("b", true, false), Step.ok("c")), LOG);

        manager.enableAll(journal);

        assertTrue(manager.isEnabled("a"));
        assertFalse(manager.isEnabled("b"));
        assertTrue(manager.isEnabled("c"));
        FeatureStatus failed = manager.statuses().get(1);
        assertEquals(State.FAILED, failed.state());
        assertInstanceOf(Exception.class, failed.error());

        manager.disableAll(journal);
        // Das fehlgeschlagene Feature wird nicht deaktiviert.
        assertEquals(List.of("+a", "+b", "+c", "-c", "-a"), journal.calls());
        assertEquals(State.FAILED, manager.statuses().get(1).state());
    }

    @Test
    void fehlendeKlasseGiltAlsFehlschlagDesFeatures() {
        CoreFeature<Journal> broken = new CoreFeature<>() {
            @Override
            public String name() {
                return "kaputt";
            }

            @Override
            public void enable(Journal journal) {
                throw new NoClassDefFoundError("net/luckperms/api/LuckPerms");
            }
        };
        Journal journal = new Journal();
        FeatureManager<Journal> manager = new FeatureManager<>(List.of(broken, Step.ok("danach")), LOG);

        manager.enableAll(journal);

        assertEquals(State.FAILED, manager.statuses().getFirst().state());
        assertTrue(manager.isEnabled("danach"));
    }

    @Test
    void fehlerBeimDeaktivierenHaeltDieUebrigenNichtAuf() {
        Journal journal = new Journal();
        FeatureManager<Journal> manager = new FeatureManager<>(
                List.of(Step.ok("a"), new Step("b", false, true), Step.ok("c")), LOG);
        manager.enableAll(journal);

        manager.disableAll(journal);

        assertEquals(List.of("+a", "+b", "+c", "-c", "-b", "-a"), journal.calls());
        FeatureStatus b = manager.statuses().get(1);
        assertEquals(State.DISABLED, b.state());
        assertInstanceOf(IllegalStateException.class, b.error());
        assertNull(manager.statuses().getFirst().error());
    }

    @Test
    void zweitesDeaktivierenIstWirkungslos() {
        Journal journal = new Journal();
        FeatureManager<Journal> manager = new FeatureManager<>(List.of(Step.ok("a")), LOG);
        manager.enableAll(journal);
        manager.disableAll(journal);
        manager.disableAll(journal);

        assertEquals(List.of("+a", "-a"), journal.calls());
    }

    @Test
    void aktiviertNurEinmal() {
        Journal journal = new Journal();
        FeatureManager<Journal> manager = new FeatureManager<>(List.of(Step.ok("a")), LOG);
        manager.enableAll(journal);

        assertThrows(IllegalStateException.class, () -> manager.enableAll(journal));
    }

    @Test
    void zustandVorDemStartIstPending() {
        FeatureManager<Journal> manager = new FeatureManager<>(List.of(Step.ok("a"), Step.ok("b")), LOG);

        assertEquals(List.of(new FeatureStatus("a", State.PENDING, null), new FeatureStatus("b", State.PENDING, null)),
                manager.statuses());
    }

    @Test
    void namenSindEindeutig() {
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureManager<>(List.of(Step.ok("a"), Step.ok("a")), LOG));
        assertThrows(IllegalArgumentException.class, () -> new FeatureManager<>(List.of(Step.ok(" ")), LOG));
    }
}
