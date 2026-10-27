package net.retromc.core.messaging;

import java.util.Arrays;
import java.util.Optional;

/**
 * Netzwerkweite Pub/Sub-Kanäle {@code net:<bereich>} (Spec 00 §4.2). Alle Nachrichten nutzen den Envelope aus
 * Spec 08 §6.12. Quellen: Spec 01 §2.4, Spec 02 §2.2, Spec 08 §2.4 (dort auch die Kanäle der Spiel-Specs).
 */
public enum NetworkChannel {
    /** Chat über Server hinweg (Spec 01). */
    CHAT("net:chat"),
    /** Freunde, Party, Clan, Profil-Invalidierung, Nick, AFK (Spec 01). */
    SOCIAL("net:social"),
    /** Strafen, Reports, Kicks (Spec 01). */
    MODERATION("net:moderation"),
    /** Warteschlange und Zuteilung (Spec 08). */
    QUEUE("net:queue"),
    /** Server- und Proxy-Lebenszyklus, Wartung (Spec 08, Spec 01). */
    SERVER_STATE("net:server-state"),
    /** Befehle an Velocity: Verbinden, Lobby, Proxy-Befehle (Spec 01, Spec 08). */
    PROXY("net:proxy"),
    /** Profilwerte geändert (Spec 02). */
    PROFILE("net:profile"),
    /** Statistik und Bestenlisten (Spec 02). */
    STATS("net:stats"),
    /** Controller-Steuerung und Anfrage/Antwort (Spec 08). */
    CONTROLLER("net:controller"),
    /** Discord-Verknüpfung und Rollen-Sync (Spec 08). */
    INTEG("net:integ"),
    /** Private Runden (Spec 03). */
    PRIVATE("net:private"),
    /** Rundenende {@code GAME_ENDED} (Spec 03). */
    GAME("net:game"),
    /** Duels-Anfragen, Turniere, Revanche (Spec 06). */
    DUELS("net:duels"),
    /** ClanWar-Herausforderungen und -Runden (Spec 05). */
    CLANWAR("net:clanwar"),
    /** CityBuild-Chat-Kanäle (Spec 07). */
    CB_CHAT("net:cb-chat"),
    /** CityBuild-Ereignisse (Spec 07). */
    CB_EVENTS("net:cb-events"),
    /** SMP-Ereignisse: Kopfgeld, Bestellungen (Spec 07). */
    SMP_EVENTS("net:smp-events");

    private final String key;

    NetworkChannel(String key) {
        this.key = key;
    }

    /** Kanalname in Valkey. */
    public String key() {
        return key;
    }

    public static Optional<NetworkChannel> byKey(String key) {
        return Arrays.stream(values()).filter(channel -> channel.key.equals(key)).findFirst();
    }
}
