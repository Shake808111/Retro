package net.retromc.bedwars.board;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.BedWarsMode;
import net.retromc.bedwars.BwTeam;
import net.retromc.bedwars.shop.ShopItems;
import net.retromc.bedwars.spawner.GeneratorSchedule;
import net.retromc.bedwars.spawner.Resource;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.team.GameTeam;
import net.retromc.paper.core.board.BoardService;
import net.retromc.paper.core.board.BoardSpec;
import net.retromc.platform.common.text.ColorText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Ingame-Board (Spec 04 §4.2): Kopf mit Map und nächster Diamant-/Gold-Stufe, darunter je Team {@code ✔/✘} mit der
 * Zahl lebender Spieler als Score. Kopf-Scores {@code maxTeamSize+6 … +1}, Teamzeilen wie Vanilla sortiert
 * ({@link BoardScores}); Classic zeigt die Teamzeilen im Stil {@code §c❤}/{@code §7✘} ohne Generatorzeilen.
 */
public final class BedWarsBoard {

    private final BedWarsMode mode;

    public BedWarsBoard(BedWarsMode mode) {
        this.mode = mode;
    }

    /** Zeigt allen Spielern das Ingame-Board. */
    public void start() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    public void show(Player player) {
        mode.game().services().find(BoardService.class).ifPresent(board -> board.show(player, spec(player)));
    }

    public void refresh() {
        mode.game().services().find(BoardService.class).ifPresent(board ->
                Bukkit.getOnlinePlayers().forEach(board::refresh));
    }

    private BoardSpec spec(Player viewer) {
        GameTexts texts = mode.texts();
        UUID uuid = viewer.getUniqueId();
        Supplier<Locale> locale = () -> texts.i18n().locale(uuid);
        List<Integer> header = BoardScores.header(mode.game().variant().teamSize());
        BoardSpec.Builder builder = BoardSpec.builder().title(() -> texts.i18n().render(locale.get(), "sb.title"))
                .blank(score(header.get(0)))
                .line("map", () -> texts.i18n().render(locale.get(), mode.config().mapColon() ? "bw.board.map.colon"
                        : "bw.board.map", Placeholder.unparsed("map", mode.map().map().displayName())),
                        score(header.get(1)))
                .blank(score(header.get(2)));
        if (mode.classic()) {
            builder.blank(score(header.get(3))).blank(score(header.get(4)));
        } else {
            builder.line("diamond", () -> generator(locale.get(), Resource.DIAMOND), score(header.get(3)))
                    .line("gold", () -> generator(locale.get(), Resource.GOLD), score(header.get(4)));
        }
        builder.blank(score(header.get(5)));
        int teams = mode.game().teams().teams().size();
        for (int position = 0; position < teams; position++) {
            int index = position;
            builder.line("team" + index, () -> teamLine(viewer, locale.get(), index),
                    () -> ShopItems.score(sorted(viewer).get(index).alive()));
        }
        return builder.build();
    }

    private static Supplier<Component> score(int value) {
        Component text = ShopItems.score(value);
        return () -> text;
    }

    private Component generator(Locale locale, Resource resource) {
        Duration elapsed = mode.game().elapsed();
        GeneratorSchedule.State state = mode.generators().state(resource, elapsed);
        String base = resource == Resource.DIAMOND ? "bw.board.diamond" : "bw.board.gold";
        return mode.texts().i18n().render(locale, state.max() ? base + ".max" : base,
                Placeholder.unparsed("tier", GeneratorSchedule.roman(state.shown())),
                Placeholder.unparsed("time", GeneratorSchedule.clock(state.untilNext())));
    }

    private List<BoardScores.TeamLine> sorted(Player viewer) {
        List<BoardScores.TeamLine> lines = new ArrayList<>();
        for (BwTeam team : mode.bwTeams()) {
            int alive = (int) team.team().members().stream().filter(mode.game()::alive).count();
            lines.add(new BoardScores.TeamLine(team.team().index(), alive, team.bedAlive()));
        }
        GameTeam own = mode.game().teams().teamOf(viewer.getUniqueId());
        return BoardScores.sort(lines, own == null ? -1 : own.index());
    }

    private Component teamLine(Player viewer, Locale locale, int position) {
        List<BoardScores.TeamLine> sorted = sorted(viewer);
        if (position >= sorted.size()) {
            return Component.empty();
        }
        BoardScores.TeamLine line = sorted.get(position);
        GameTeam team = mode.game().teams().byIndex(line.index()).orElseThrow();
        String key = mode.classic() ? (line.bed() ? "bw.board.legacy.alive" : "bw.board.legacy.dead")
                : (line.bed() ? "bw.board.team.alive" : "bw.board.team.dead");
        String shortKey = "team.short." + team.id();
        Component name = mode.texts().i18n().has(shortKey) ? mode.texts().i18n().render(locale, shortKey)
                : mode.texts().teamName(locale, team);
        return mode.texts().i18n().render(locale, key, Placeholder.component("team", name),
                ColorText.styling("team_color", team.colorCode()));
    }
}
