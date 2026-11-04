package net.retromc.core.api.social;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * Einstellungen der Spieler (Spec 01 §5.6, §5.7, §6.13, §8.1). Jede Änderung geht sofort in die Datenbank und
 * löst {@code PROFILE_INVALIDATE} aus; {@code show_online} wird zusätzlich in {@code presence.hidden} gespiegelt.
 * Rangprüfungen ({@link Setting#requiredWeight()}) macht der Aufrufer.
 */
public interface SettingsService {

    /** Einstellungen; ohne Zeile in {@code social.privacy} gelten {@link PrivacySettings#DEFAULTS}. */
    CompletableFuture<PrivacySettings> settings(UUID player);

    /** Atomar ändern (Lesen, Funktion anwenden, Schreiben in einer Transaktion); liefert den neuen Stand. */
    CompletableFuture<PrivacySettings> update(UUID player, UnaryOperator<PrivacySettings> change);

    /** Wert setzen ({@code /partytoggle alle|freunde|aus}, GUI). */
    default CompletableFuture<PrivacySettings> set(UUID player, Setting setting, int value) {
        return update(player, current -> current.with(setting, value));
    }

    /** Nächster Wert im Klick-Zyklus (GUI §5.6, {@code /partytoggle} ohne Argument, Nick-GUI §5.7). */
    default CompletableFuture<PrivacySettings> cycle(UUID player, Setting setting) {
        return update(player, current -> current.cycle(setting));
    }

    /**
     * Sprache setzen ({@code /language}, Spec 01 #67): {@code core.player.locale} → {@code profile:{uuid}.locale}
     * → {@code PROFILE_CHANGED} mit Feld {@code locale} (Spec 02 §6.13 Nr. 3).
     */
    CompletableFuture<Void> setLocale(UUID player, String locale);

    /**
     * Sprache aus der Client-Sprache beim ersten Join (Spec 01 §6.3 Nr. 4: {@code de*} → {@code de_DE}, sonst
     * {@code en_US}). Wirkt nur in der ersten Sitzung ({@code first_join = last_join}) und nur, wenn sich die Sprache
     * ändert; dann wie {@link #setLocale(UUID, String)}. Der Proxy ruft das auf, sobald der Client seine Einstellungen
     * schickt – beim Login ist die Client-Sprache oft noch unbekannt.
     *
     * @return {@code true}, wenn die Sprache gesetzt wurde
     */
    CompletableFuture<Boolean> initialLocale(UUID player, String clientLocale);
}
