package net.retromc.core.api.social;

/**
 * Online/gesamt für Scoreboard-Zeilen „Freunde“ {@code §a{online}§7/{total}} und „Clan“ (Spec 01 §4.3,
 * Spec 02 §4.2). Online zählt nur Spieler mit Präsenz und {@code hidden=0} (Spec 01 §6.4 Nr. 5).
 */
public record OnlineCount(int online, int total) {

    public static final OnlineCount NONE = new OnlineCount(0, 0);

    public OnlineCount {
        if (online < 0 || total < 0 || online > total) {
            throw new IllegalArgumentException("Es gilt 0 ≤ online ≤ total: " + online + "/" + total);
        }
    }
}
