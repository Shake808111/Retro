package net.retromc.paper.core.gui;

/** Feste Slots des Bestätigungsdialogs (Spec 02 §5.2). */
public final class ConfirmLayout {

    /** Reihen des Dialogs. */
    public static final int ROWS = 3;
    /** Grüner Farbstoff „Bestätigen“. */
    public static final int CONFIRM = 11;
    /** Info-Item des Aufrufers. */
    public static final int INFO = 13;
    /** Roter Farbstoff „Abbrechen“. */
    public static final int CANCEL = 15;
    /** Höchstlänge der Frage im Titel (sichtbare Zeichen). */
    public static final int MAX_TITLE = 32;

    private ConfirmLayout() {
    }
}
