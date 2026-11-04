package net.retromc.core.data.nick;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.retromc.core.api.BuildInfo;
import net.retromc.core.api.util.PlayerName;
import net.retromc.core.data.punish.SpamFilter;

/**
 * Wortfilter für erzeugte Nicknamen über den vorhandenen Chat-Filter ({@link SpamFilter}): Wortliste und
 * Zahlenfolgen ({@link SpamFilter#containsBlockedWord}), Werbung ({@link SpamFilter#isAdvertising}), zu viele
 * Großbuchstaben ({@link SpamFilter#tooManyCaps}), dazu die Marke und zusätzliche Wörter aus der Konfiguration
 * ({@code NETWORK_NICK_BLOCKED_WORDS}).
 */
public final class NickNameFilter {

    private final SpamFilter chatFilter;
    private final Set<String> extra;

    public NickNameFilter(SpamFilter chatFilter, Set<String> extraWords) {
        this.chatFilter = Objects.requireNonNull(chatFilter, "chatFilter");
        this.extra = Set.copyOf(extraWords);
    }

    /** Ob der Name als Nick erlaubt ist (gültiger Spielername und kein Filtertreffer). */
    public boolean allowed(String name) {
        if (!PlayerName.valid(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return !lower.contains(BuildInfo.BRAND_ID) && !SpamFilter.containsBlockedWord(name, extra)
                && !chatFilter.isAdvertising(name) && !SpamFilter.tooManyCaps(name);
    }
}
