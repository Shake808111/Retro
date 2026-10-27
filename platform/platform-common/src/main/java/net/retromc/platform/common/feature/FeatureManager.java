package net.retromc.platform.common.feature;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.retromc.platform.common.feature.FeatureStatus.State;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Geordnete Liste von {@link CoreFeature}s mit Fehlerisolation.
 *
 * <ul>
 *   <li>{@link #enableAll} aktiviert in Listenreihenfolge; schlägt ein Feature fehl (Ausnahme oder fehlende Klasse),
 *       wird das protokolliert und mit dem nächsten weitergemacht.</li>
 *   <li>{@link #disableAll} deaktiviert nur erfolgreich aktivierte Features, in umgekehrter Reihenfolge; auch hier
 *       hält ein Fehler die übrigen nicht auf.</li>
 * </ul>
 *
 * @param <C> Kontext der Plattform
 */
public final class FeatureManager<C> {

    private final List<CoreFeature<? super C>> features;
    private final Logger log;
    private final Map<String, FeatureStatus> status = new LinkedHashMap<>();
    private final Deque<CoreFeature<? super C>> enabled = new ArrayDeque<>();
    private boolean started;

    public FeatureManager(List<? extends CoreFeature<? super C>> features, Logger log) {
        this.features = List.copyOf(features);
        this.log = Objects.requireNonNull(log, "log");
        Set<String> names = new HashSet<>();
        for (CoreFeature<? super C> feature : this.features) {
            String name = Objects.requireNonNull(feature.name(), "name");
            if (name.isBlank() || !names.add(name)) {
                throw new IllegalArgumentException("Feature-Name leer oder doppelt: '" + name + "'");
            }
            status.put(name, new FeatureStatus(name, State.PENDING, null));
        }
    }

    /** Aktiviert alle Features einmalig in Listenreihenfolge. */
    public synchronized void enableAll(C context) {
        Objects.requireNonNull(context, "context");
        if (started) {
            throw new IllegalStateException("Features wurden bereits aktiviert");
        }
        started = true;
        for (CoreFeature<? super C> feature : features) {
            long start = System.nanoTime();
            try {
                feature.enable(context);
                enabled.push(feature);
                update(feature, State.ENABLED, null);
                log.info("Feature {} aktiv ({} ms)", feature.name(),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            } catch (Exception | LinkageError e) {
                update(feature, State.FAILED, e);
                log.error("Feature {} konnte nicht aktiviert werden und bleibt aus", feature.name(), e);
            }
        }
    }

    /** Deaktiviert die aktiven Features in umgekehrter Reihenfolge. Mehrfacher Aufruf ist wirkungslos. */
    public synchronized void disableAll(C context) {
        Objects.requireNonNull(context, "context");
        while (!enabled.isEmpty()) {
            CoreFeature<? super C> feature = enabled.pop();
            try {
                feature.disable(context);
                update(feature, State.DISABLED, null);
            } catch (Exception | LinkageError e) {
                update(feature, State.DISABLED, e);
                log.error("Feature {} ließ sich nicht sauber beenden", feature.name(), e);
            }
        }
    }

    /** Zustände aller Features in Listenreihenfolge. */
    public synchronized List<FeatureStatus> statuses() {
        return List.copyOf(status.values());
    }

    /** {@code true}, wenn das Feature mit diesem Namen gerade aktiv ist. */
    public synchronized boolean isEnabled(String name) {
        FeatureStatus current = status.get(name);
        return current != null && current.state() == State.ENABLED;
    }

    private void update(CoreFeature<? super C> feature, State state, @Nullable Throwable error) {
        status.put(feature.name(), new FeatureStatus(feature.name(), state, error));
    }
}
