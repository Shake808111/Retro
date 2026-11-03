package net.retromc.paper.core.board;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.i18n.Formats;
import net.retromc.platform.common.text.ColorText;
import net.retromc.platform.common.text.I18n;
import org.jspecify.annotations.Nullable;

/**
 * Lobby-Scoreboard {@code MODERN} (Spec 02 §4.2): 15 Zeilen, Scores 14…0, Titel {@code §f§l{BRAND_UPPER}.NET}.
 *
 * <pre>
 * 14 (leer)          9 §e{coins}        4 Clan
 * 13 Rang ⇄ Spielzeit 8 (leer)          3 §b{online}§7/{total} §7[§b{tag}§7] | §7-
 * 12 {rang} ⇄ §6{h} Stunden 7 Gold     2 (leer)
 * 11 (leer)          6 §6{gold}         1 Freunde
 * 10 Coins           5 (leer)           0 §a{online}§7/{total}
 * </pre>
 *
 * Bis die Daten geladen sind, zeigen die Werte {@code §70} (§4.1 Ladezustand). Rang ⇄ Spielzeit wechselt alle
 * 100 Ticks (§6.3).
 */
public final class LobbyBoard {

    /** Zeilen-IDs von oben (Score 14) nach unten (Score 0). */
    public static final List<String> LINE_IDS = List.of("blank1", "rank_label", "rank_value", "blank2",
            "coins_label", "coins", "blank3", "gold_label", "gold", "blank4", "clan_label", "clan", "blank5",
            "friends_label", "friends");
    /** Takt des Wechsels Rang ⇄ Spielzeit in Ticks. */
    public static final int TOGGLE_TICKS = 100;

    private LobbyBoard() {
    }

    /**
     * Werte des Boards.
     *
     * @param loaded Profil geladen (sonst Ladezustand {@code §70})
     * @param rank Rang-Anzeige (Spieler ohne Rang: {@code §aSpieler})
     * @param playtimeSeconds Spielzeit live (Datenbank + Sitzung)
     * @param clan Clan online/gesamt oder {@code null}
     */
    public record State(boolean loaded, RankDisplay rank, long playtimeSeconds, long coins, long gold,
            @Nullable OnlineCount clan, @Nullable String clanTag, OnlineCount friends) {

        public State {
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(friends, "friends");
        }
    }

    /** Bauplan mit Suppliern; {@code showRank} wählt Rang oder Spielzeit in Zeile 13/12. */
    public static BoardSpec spec(I18n i18n, Supplier<Locale> locale, Supplier<State> state, BooleanSupplier showRank) {
        return BoardSpec.builder()
                .title(() -> title(i18n, locale.get()))
                .blank()
                .line("rank_label", () -> i18n.render(locale.get(), showRank.getAsBoolean() ? "sb.label.rank"
                        : "sb.label.playtime"))
                .line("rank_value", () -> rankValue(i18n, locale.get(), state.get(), showRank.getAsBoolean()))
                .blank()
                .line("coins_label", () -> i18n.render(locale.get(), "sb.label.coins"))
                .line("coins", () -> number(i18n, locale.get(), state.get(), "sb.value.coins", state.get().coins()))
                .blank()
                .line("gold_label", () -> i18n.render(locale.get(), "sb.label.gold"))
                .line("gold", () -> number(i18n, locale.get(), state.get(), "sb.value.gold", state.get().gold()))
                .blank()
                .line("clan_label", () -> i18n.render(locale.get(), "sb.label.clan"))
                .line("clan", () -> clan(i18n, locale.get(), state.get()))
                .blank()
                .line("friends_label", () -> i18n.render(locale.get(), "sb.label.friends"))
                .line("friends", () -> friends(i18n, locale.get(), state.get()))
                .showScores(true)
                .build();
    }

    public static Component title(I18n i18n, Locale locale) {
        return i18n.render(locale, "sb.title");
    }

    /** Ladezustand {@code §70}. */
    public static Component loading() {
        return Component.text("0", NamedTextColor.GRAY);
    }

    static Component rankValue(I18n i18n, Locale locale, State state, boolean showRank) {
        if (!state.loaded()) {
            return loading();
        }
        if (showRank) {
            RankDisplay rank = state.rank();
            return i18n.render(locale, "sb.value.rank", ColorText.styling("rank_color", rank.color()),
                    Placeholder.component("rank_name", ColorText.name(rank.displayName(), rank.color(),
                            rank.gradient(), true)));
        }
        long hours = Formats.playtimeHours(state.playtimeSeconds());
        return i18n.render(locale, hours == 1 ? "sb.value.playtime.one" : "sb.value.playtime",
                Placeholder.unparsed("n", Formats.of(locale).integer(hours)));
    }

    static Component number(I18n i18n, Locale locale, State state, String key, long value) {
        return state.loaded() ? i18n.render(locale, key, Placeholder.unparsed("n", Formats.of(locale).integer(value)))
                : loading();
    }

    static Component clan(I18n i18n, Locale locale, State state) {
        if (!state.loaded()) {
            return loading();
        }
        OnlineCount clan = state.clan();
        String tag = state.clanTag();
        if (clan == null || tag == null) {
            return i18n.render(locale, "sb.value.no-clan");
        }
        return i18n.render(locale, "sb.value.clan", Placeholder.unparsed("online", Integer.toString(clan.online())),
                Placeholder.unparsed("total", Integer.toString(clan.total())), Placeholder.unparsed("tag", tag));
    }

    static Component friends(I18n i18n, Locale locale, State state) {
        if (!state.loaded()) {
            return loading();
        }
        return i18n.render(locale, "sb.value.friends",
                Placeholder.unparsed("online", Integer.toString(state.friends().online())),
                Placeholder.unparsed("total", Integer.toString(state.friends().total())));
    }
}
