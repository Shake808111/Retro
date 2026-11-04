package net.retromc.game.framework;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.retromc.game.framework.end.GameResult;
import net.retromc.game.framework.map.GameMap;
import net.retromc.game.framework.phase.CountdownPlan;
import net.retromc.game.framework.team.TeamAllocator;
import net.retromc.game.framework.team.TeamColor;
import net.retromc.game.framework.team.TeamGui;
import net.retromc.paper.core.hotbar.HotbarLayout;
import net.retromc.paper.core.stats.RoundSummary;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Was ein Modus dem Rahmen liefert (Spec 03 §8.5): Teamreihenfolge, Board-Block, Hotbar-Slots 3–6, Siegbedingung,
 * Rundenblock und Basis-Coins. Wartelobby, Countdown, Teams, Zuschauer, Kill-Feed und Rundenende kommen aus dem
 * Rahmen ({@link Game}).
 */
public interface GameMode {

    /** Block 2 des Wartelobby-Boards (Spec 03 §4.2): Label und Wert je Betrachter. */
    interface WaitingBlock {
        String labelKey();

        Component value(Game game, Player viewer, Locale locale);
    }

    /** Teamreihenfolge (Spec 03 §6.6; BedWars Spec 04 §6.1.1). */
    default List<TeamColor> teamOrder() {
        return TeamColor.FRAME_ORDER;
    }

    /** Zuteilungsregel für Spieler ohne Team. */
    default TeamAllocator.Strategy allocation() {
        return TeamAllocator.Strategy.BALANCE;
    }

    /** Restsekunde, bei der Spieler ohne Team verteilt werden (0 = beim Start; BedWars 10, Spec 04 §6.1.2). */
    default int allocateAt() {
        return 0;
    }

    /** Ansagen des Lobby-Countdowns. */
    default Set<Integer> countdownAnnouncements() {
        return CountdownPlan.DEFAULT_ANNOUNCE;
    }

    /** Ersatzschlüssel des Modus für Rahmen-Texte ({@code common.online} → {@code bw.join.online}, Spec 04 §7.3). */
    default Map<String, String> keyOverrides() {
        return Map.of();
    }

    /** Tipps als vollständige Nachrichtenschlüssel; leer = {@code tips.<mode>.N} über {@code common.tip}. */
    default List<String> tipKeys() {
        return List.of();
    }

    /** Block 2 des Wartelobby-Boards; Standard {@code Spieler} / {@code {teams}x{teamSize}}. */
    default WaitingBlock waitingBlock() {
        return new WaitingBlock() {
            @Override
            public String labelKey() {
                return "game.board.players";
            }

            @Override
            public Component value(Game game, Player viewer, Locale locale) {
                return game.texts().i18n().render(locale, "game.board.players.value",
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("teams",
                                Integer.toString(game.variant().teams())),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("size",
                                Integer.toString(game.variant().teamSize())));
            }
        };
    }

    /** Icon von Hotbar-Slot 1 in Teammodi (BedWars {@code red_bed}, Spec 03 §4.10). */
    default Material teamItem() {
        return Material.WHITE_WOOL;
    }

    /** Teamen verboten: Hinweis {@code common.teaming.warn} beim Join (Solo-Modi). */
    default boolean teamingForbidden() {
        return false;
    }

    /** Modusspezifische Wartelobby-Slots (Index 0–8; der Rahmen belegt 0, 1, 6, 7, 8). */
    default void waitingHotbar(Player player, HotbarLayout layout, Locale locale) {
    }

    /** Party-Leiter zieht die Party bei der Teamwahl mit (BedWars Spec 04 §5.3). */
    default boolean partyFollowsLeader() {
        return false;
    }

    /** Darstellung der Team-Wahl. */
    default TeamGui.Style teamGuiStyle() {
        return TeamGui.Style.FRAME;
    }

    /** Zusätzliche Prüfungen der Map (Fehlertexte, leer = gültig). */
    default List<String> validateMap(GameMap map) {
        return List.of();
    }

    /** Welt der Map ist geladen (Spielregeln setzen, Marker lesen). */
    default void onMapReady(GameMap map, World world) {
    }

    /** INGAME: Spieler stehen auf ihren Spawns, Inventare sind leer. */
    void onStart();

    /** Join in INGAME/ENDING; {@code true} = vom Modus übernommen (sonst Zuschauer). */
    default boolean onLateJoin(Player player) {
        return false;
    }

    /** Lebender Spieler verlässt INGAME; {@code true} = vom Modus erledigt (sonst Quit-Meldung + Ausscheiden). */
    default boolean onIngameQuit(Player player, GamePlayer gamePlayer) {
        return false;
    }

    /** Rundenblock eines Spielers (Spec 02 §4.7, Spec 03 §6.10 Nr. 3). */
    RoundSummary summary(GamePlayer player, GameResult result);

    /** Basis-Coins für {@code CoinService.payoutRound}. */
    long baseCoins(GamePlayer player, GameResult result);

    /** Rundenende vor dem Umwandeln in Zuschauer und vor der Statistik (z. B. End-XP gutschreiben). */
    default void onFinish(GameResult result) {
    }

    /**
     * Nach der Statistik-Buchung (z. B. Modus-Level).
     *
     * @param counted {@code true}, wenn die Runde neu verbucht wurde (nicht doppelt, keine Privatrunde)
     */
    default void onEnd(GameResult result, boolean counted) {
    }

    /** Server fährt herunter. */
    default void onDisable() {
    }
}
