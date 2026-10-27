package net.retromc.core.api.punish;

import java.util.Objects;

/**
 * Kontext einer Chat-Prüfung: wo geschrieben wird und welche Ausnahmen gelten (Spec 01 §6.16: Premium+ 0,75 s,
 * Team ohne Spam-Regeln; „gg“/„gege“/„gl“ zählen nicht als Wiederholung).
 *
 * @param rankWeight Gewicht des Rangs (Premium+ = 300)
 * @param team Teammitglied (keine Spam-Regeln, Mute gilt trotzdem)
 */
public record ChatContext(Source source, int rankWeight, boolean team) {

    /** Herkunft der Nachricht; Proxy prüft {@code /msg /r /p /cc /clc}, paper-core den normalen Chat. */
    public enum Source {
        CHAT,
        PRIVATE_MESSAGE,
        PARTY_CHAT,
        CLAN_CHAT,
        CLAN_LEAD_CHAT,
        JOINME
    }

    public ChatContext {
        Objects.requireNonNull(source, "source");
    }
}
