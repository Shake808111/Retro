package net.retromc.core.api.social;

/**
 * Ergebnis von {@code /joinme} (Spec 01 §3.5 #66, §6.17): {@code joinme.sent} mit {used}/{max} oder
 * {@code joinme.err.limit}.
 *
 * @param max Tageslimit des Rangs, {@code RankLimits.UNLIMITED} = unbegrenzt
 */
public record JoinMeResult(boolean allowed, int used, int max) {

    public JoinMeResult {
        if (used < 0) {
            throw new IllegalArgumentException("used darf nicht negativ sein");
        }
    }
}
