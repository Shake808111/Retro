package net.retromc.core.api.social;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Rolle im Clan, Spalte {@code social.clan_member.role} mit {@code CHECK (role IN (-1,0,1,2))} (Spec 01 §2.2,
 * §6.6). Testmitglieder haben die Rechte eines Mitglieds ohne Clan-Wars-Lineup.
 */
public enum ClanRole {
    TRIAL(-1),
    MEMBER(0),
    MODERATOR(1),
    LEADER(2);

    private final int code;

    ClanRole(int code) {
        this.code = code;
    }

    /** Wert in der Datenbank. */
    public int code() {
        return code;
    }

    /** i18n-Schlüssel des Rollennamens ({@code clan.role.<name>}). */
    public String i18nKey() {
        return "clan.role." + name().toLowerCase(Locale.ROOT);
    }

    public static ClanRole ofCode(int code) {
        return byCode(code).orElseThrow(() -> new IllegalArgumentException("Unbekannte Clan-Rolle: " + code));
    }

    public static Optional<ClanRole> byCode(int code) {
        return Arrays.stream(values()).filter(role -> role.code == code).findFirst();
    }

    public boolean isAtLeast(ClanRole other) {
        return code >= other.code;
    }

    /** Einladen und Anfragen bearbeiten: Moderator+ (Spec 01 §3.4 #42, #45). */
    public boolean canInvite() {
        return isAtLeast(MODERATOR);
    }

    /** Rauswerfen nur von Moderator+ und nur niedrigere Rollen (Spec 01 §3.4 #48). */
    public boolean canKick(ClanRole target) {
        return isAtLeast(MODERATOR) && code > target.code;
    }

    /** Leitungs-Chat {@code /clc}: Moderator+. */
    public boolean canUseLeadChat() {
        return isAtLeast(MODERATOR);
    }

    /** Nächsthöhere Rolle; MODERATOR → LEADER ist eine Übertragung mit Bestätigung (#49). */
    public Optional<ClanRole> promoted() {
        return switch (this) {
            case TRIAL -> Optional.of(MEMBER);
            case MEMBER -> Optional.of(MODERATOR);
            case MODERATOR -> Optional.of(LEADER);
            case LEADER -> Optional.empty();
        };
    }

    /** Eine Stufe runter (#50); der Leiter kann nicht degradiert werden. */
    public Optional<ClanRole> demoted() {
        return switch (this) {
            case MODERATOR -> Optional.of(MEMBER);
            case MEMBER -> Optional.of(TRIAL);
            case TRIAL, LEADER -> Optional.empty();
        };
    }
}
