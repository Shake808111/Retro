package net.retromc.paper.core.gui;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Entscheidung im Bestätigungsdialog (Spec 02 §5.2): genau eine von „Bestätigen“ und „Abbrechen“ zählt; Schließen
 * ohne Entscheidung (ESC) gilt als „Abbrechen“.
 */
public final class ConfirmState {

    /** Ergebnis des Dialogs. */
    public enum Outcome {
        CONFIRMED,
        CANCELLED
    }

    private @Nullable Outcome outcome;

    /** {@code true}, wenn damit bestätigt wurde (erste Entscheidung). */
    public synchronized boolean confirm() {
        return decide(Outcome.CONFIRMED);
    }

    /** {@code true}, wenn damit abgebrochen wurde (erste Entscheidung). */
    public synchronized boolean cancel() {
        return decide(Outcome.CANCELLED);
    }

    /** Fenster geschlossen; {@code true}, wenn das als Abbruch zählt (noch keine Entscheidung). */
    public synchronized boolean closed() {
        return decide(Outcome.CANCELLED);
    }

    /** Die Entscheidung, falls gefallen. */
    public synchronized Optional<Outcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    private boolean decide(Outcome decision) {
        if (outcome != null) {
            return false;
        }
        outcome = decision;
        return true;
    }
}
