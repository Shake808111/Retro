package net.retromc.game.framework.kill;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Letzter Schadensverursacher je Opfer (Spec 03 §6.9: Killer = letzter Treffer ≤ 10 s; Spec 04 §6.1.4 Nr. 4: dann
 * zählen auch Void und Fallschaden als Kill). Ohne Bukkit testbar.
 */
public final class DamageTracker {

    /** Fenster, in dem ein Treffer als Kill zählt. */
    public static final Duration WINDOW = Duration.ofSeconds(10);

    private record Hit(UUID attacker, Instant at) {
    }

    private final Map<UUID, Hit> hits = new ConcurrentHashMap<>();
    private final Duration window;

    public DamageTracker() {
        this(WINDOW);
    }

    public DamageTracker(Duration window) {
        this.window = Objects.requireNonNull(window, "window");
    }

    /** Merkt einen Treffer; Treffer auf sich selbst zählen nicht. */
    public void hit(UUID victim, UUID attacker, Instant at) {
        if (!victim.equals(attacker)) {
            hits.put(victim, new Hit(attacker, at));
        }
    }

    /** Letzter Angreifer innerhalb des Fensters. */
    public Optional<UUID> lastAttacker(UUID victim, Instant now) {
        Hit hit = hits.get(victim);
        if (hit == null || Duration.between(hit.at(), now).compareTo(window) > 0) {
            return Optional.empty();
        }
        return Optional.of(hit.attacker());
    }

    /** Vergisst die Treffer auf {@code victim} (nach Tod/Respawn). */
    public void clear(UUID victim) {
        hits.remove(victim);
    }

    /** Vergisst alles über einen Spieler (Opfer und Angreifer). */
    public void forget(UUID player) {
        hits.remove(player);
        hits.values().removeIf(hit -> hit.attacker().equals(player));
    }
}
