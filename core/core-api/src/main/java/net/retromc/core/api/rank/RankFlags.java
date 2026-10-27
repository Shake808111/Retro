package net.retromc.core.api.rank;

import java.util.Objects;

/**
 * Schalter-Vorteile eines Rangs (Spec 01 §6.2, Spec 01 §8.1 {@code RankInfo.flags}).
 *
 * @param nick Nick erlaubt (Recht {@code <brandId>.nick}, ab Premium+)
 * @param silentLobby Silent-Lobby (ab Supremium)
 * @param publicParty öffentliche Party erstellen (ab Premium)
 * @param fullServer volle Spielserver betreten (ab Supremium)
 * @param joinPriority Join-Priorität 0–3 (Proxy voll, Spec 01 §6.3 Schritt 3)
 * @param coloredGg farbiges GG im Chat (ab Supremium, Spec 02 §6.1 Schritt 4)
 * @param hideStatus vor Freunden/Clan verstecken (ab Premium, Einstellung {@code hide_from_clan})
 * @param booster Coin-Boost für die ganze Runde (Supremium, Supremium+, Spec 02 §6.11)
 * @param lobbyMovement Doppelsprung/Fliegen in der Lobby
 */
public record RankFlags(
        boolean nick,
        boolean silentLobby,
        boolean publicParty,
        boolean fullServer,
        int joinPriority,
        boolean coloredGg,
        boolean hideStatus,
        boolean booster,
        LobbyMovement lobbyMovement) {

    public RankFlags {
        Objects.requireNonNull(lobbyMovement, "lobbyMovement");
        if (joinPriority < 0) {
            throw new IllegalArgumentException("joinPriority darf nicht negativ sein");
        }
    }
}
