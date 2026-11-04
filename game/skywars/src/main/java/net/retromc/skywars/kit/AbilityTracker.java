package net.retromc.skywars.kit;

/**
 * Nutzungen und Abklingzeit einer Kit-Fähigkeit in einer Runde (Spec 04 §4.6, §6.2.5 Kit-Regel 3: in der Schutzzeit
 * gesperrt). Zeit in Millisekunden, ohne Server testbar.
 */
public final class AbilityTracker {

    /** Ausgang eines Nutzungsversuchs. */
    public enum Result {
        /** Ausgelöst. */
        OK,
        /** Schutzzeit läuft ({@code sw.ability.protection}). */
        PROTECTION,
        /** Abklingzeit läuft ({@code sw.ability.cooldown}). */
        COOLDOWN,
        /** Keine Nutzungen mehr ({@code sw.ability.empty}). */
        EMPTY
    }

    /**
     * Ergebnis.
     *
     * @param usesLeft verbleibende Nutzungen (−1 = unbegrenzt)
     * @param cooldown Restsekunden der Abklingzeit (aufgerundet)
     */
    public record Use(Result result, int usesLeft, int cooldown) {
    }

    private final int uses;
    private final long cooldownMillis;
    private int used;
    private long readyAt;
    private boolean readyAnnounced = true;

    /**
     * @param uses            Nutzungen je Runde (0 = unbegrenzt)
     * @param cooldownSeconds Abklingzeit
     */
    public AbilityTracker(int uses, int cooldownSeconds) {
        this.uses = uses;
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    /** Verbleibende Nutzungen (−1 = unbegrenzt). */
    public int usesLeft() {
        return uses <= 0 ? -1 : Math.max(0, uses - used);
    }

    /** Restsekunden der Abklingzeit (aufgerundet, 0 = bereit). */
    public int cooldown(long now) {
        long rest = readyAt - now;
        return rest <= 0 ? 0 : (int) ((rest + 999) / 1000);
    }

    /** Prüft einen Versuch, ohne ihn zu buchen. */
    public Use check(long now, boolean protection) {
        if (protection) {
            return new Use(Result.PROTECTION, usesLeft(), cooldown(now));
        }
        if (uses > 0 && used >= uses) {
            return new Use(Result.EMPTY, 0, 0);
        }
        int rest = cooldown(now);
        if (rest > 0) {
            return new Use(Result.COOLDOWN, usesLeft(), rest);
        }
        return new Use(Result.OK, usesLeft(), 0);
    }

    /** Bucht eine Nutzung (nach {@link #check} = OK). */
    public Use use(long now, boolean protection) {
        Use check = check(now, protection);
        if (check.result() != Result.OK) {
            return check;
        }
        used++;
        readyAt = now + cooldownMillis;
        readyAnnounced = cooldownMillis <= 0;
        return new Use(Result.OK, usesLeft(), cooldown(now));
    }

    /**
     * {@code true} genau einmal, wenn die Abklingzeit abgelaufen ist und noch Nutzungen bleiben
     * ({@code sw.ability.ready}).
     */
    public boolean becameReady(long now) {
        if (readyAnnounced || cooldown(now) > 0) {
            return false;
        }
        readyAnnounced = true;
        return uses <= 0 || used < uses;
    }
}
