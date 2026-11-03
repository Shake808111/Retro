package net.retromc.paper.core.profile;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.api.social.Profile;
import net.retromc.core.i18n.Language;
import org.jspecify.annotations.Nullable;

/**
 * Profil eines verbundenen Spielers für Chat, Tab, Nametag und Scoreboard (Spec 02 §2.2 {@code profile:{uuid}}):
 * Profilwerte, Rang-Anzeige, Freunde und Clan online/gesamt sowie Sitzungsbeginn im Netzwerk.
 *
 * <p>Ein Nick ({@code profile.nickName}) gilt nur, wo er angewendet wird ({@code nickVisible}, GAME-Server, Spec 01
 * O21): dann erscheint der Spieler als Premium (§6), ohne Clan-Tag und ohne Level (Spec 01 §6.11 Nr. 3, O20).
 *
 * @param rank Rang-Anzeige des echten Rangs
 * @param friends Freunde online/gesamt
 * @param clan Clan-Mitglieder online/gesamt oder {@code null} ohne Clan
 * @param sessionStart Beginn der Netzwerk-Sitzung ({@code presence.since}); die Spielzeit rechnet live
 * @param nickVisible Nick wird auf diesem Server angezeigt
 */
public record ProfileView(Profile profile, RankDisplay rank, OnlineCount friends, @Nullable OnlineCount clan,
        Instant sessionStart, boolean nickVisible) {

    public ProfileView {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(friends, "friends");
        Objects.requireNonNull(sessionStart, "sessionStart");
    }

    /**
     * Spielzeit in Sekunden: Datenbankwert ({@code playtime_s}, bis zum letzten Logout) plus laufende Sitzung
     * (Spec 02 §6.3 „Die Anzeige rechnet live: Session-Start + Datenbankwert“).
     */
    public long playtimeSeconds(Instant now) {
        return profile.playtimeS() + Math.max(0, Duration.between(sessionStart, now).toSeconds());
    }

    /** Name, unter dem andere den Spieler hier sehen. */
    public String displayName() {
        String nick = profile.nickName();
        return nickVisible && nick != null ? nick : profile.name();
    }

    /** Rang, mit dem der Spieler hier angezeigt wird (genickt: Premium). */
    public RankDisplay displayRank() {
        return nickVisible ? RankDisplay.nicked() : rank;
    }

    /** Clan-Tag für Anzeigen; genickt nie. */
    public @Nullable String clanTag() {
        return nickVisible ? null : profile.clanTag();
    }

    /** Wird das globale Level gezeigt? Genickt nicht (Spec 01 §6.11 Nr. 3). */
    public boolean levelVisible() {
        return !nickVisible;
    }

    /** Sprache aus dem Profil ({@code core.player.locale}). */
    public Locale locale() {
        return Language.byCode(profile.locale()).orElse(Language.BASE).locale();
    }
}
