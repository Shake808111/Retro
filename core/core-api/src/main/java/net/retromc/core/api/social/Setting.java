package net.retromc.core.api.social;

import java.util.List;

/**
 * Einstellungen der GUI „Einstellungen“ und der Nick-GUI (Spec 01 §5.6, §5.7, Werte §6.13). Werte sind Zahlen wie in
 * {@code social.privacy}; Schalter nutzen 1 = an, 0 = aus. {@link #cycle()} ist die Klick-Reihenfolge der GUI.
 */
public enum Setting {
    /** Alle(2) / Ab Premium(1) / Niemand(0 = {@code friend_requests=false}); Spalten friend_requests + friend_req_from. */
    FRIEND_REQUESTS(List.of(2, 1, 0), 2, 0),
    /** {@code show_online}: sichtbar(1) / versteckt(0). */
    SHOW_ONLINE(List.of(1, 0), 1, 0),
    /** {@code msg_from}: Alle(2) / Freunde(1) / Niemand(0). */
    MSG_FROM(List.of(2, 1, 0), 1, 0),
    /** {@code party_from}: Alle(2) / Freunde(1) / Niemand(0). */
    PARTY_FROM(List.of(2, 1, 0), 2, 0),
    /** {@code jump_from}: Freunde(1) / Nur Favoriten(2) / Niemand(0). */
    JUMP_FROM(List.of(1, 2, 0), 1, 0),
    /** {@code notify_friends}: Freunde-Meldungen an/aus. */
    NOTIFY_FRIENDS(List.of(1, 0), 1, 0),
    /** {@code notify_clan}: Clan-Meldungen an/aus. */
    NOTIFY_CLAN(List.of(1, 0), 1, 0),
    /** {@code hide_from_clan}: vor dem Clan verstecken, ab Premium (Gewicht 200). */
    HIDE_FROM_CLAN(List.of(0, 1), 0, 200),
    /** {@code global_chat}: globaler Chat an/aus. */
    GLOBAL_CHAT(List.of(1, 0), 1, 0),
    /** {@code nick_on_join}: Nick beim Rundenbeitritt (Nick-Recht ab Premium+, Gewicht 300). */
    NICK_ON_JOIN(List.of(0, 1), 0, 300),
    /** {@code nick_keep}: Nick nach Rundenende behalten (Nick-Recht ab Premium+). */
    NICK_KEEP(List.of(0, 1), 0, 300);

    private final List<Integer> cycle;
    private final int defaultValue;
    private final int requiredWeight;

    Setting(List<Integer> cycle, int defaultValue, int requiredWeight) {
        this.cycle = cycle;
        this.defaultValue = defaultValue;
        this.requiredWeight = requiredWeight;
    }

    /** Erlaubte Werte in Klick-Reihenfolge. */
    public List<Integer> cycle() {
        return cycle;
    }

    /** Standard laut Spec 01 §6.13. */
    public int defaultValue() {
        return defaultValue;
    }

    /** Mindest-Ranggewicht zum Ändern (0 = alle). */
    public int requiredWeight() {
        return requiredWeight;
    }

    public boolean isToggle() {
        return cycle.size() == 2;
    }

    public boolean allows(int value) {
        return cycle.contains(value);
    }

    /** Nächster Wert beim GUI-Klick; unbekannte Werte springen auf den ersten. */
    public int next(int current) {
        int index = cycle.indexOf(current);
        return cycle.get(index < 0 ? 0 : (index + 1) % cycle.size());
    }
}
