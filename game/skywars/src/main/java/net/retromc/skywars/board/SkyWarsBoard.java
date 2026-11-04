package net.retromc.skywars.board;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.GameTexts;
import net.retromc.paper.core.board.BoardService;
import net.retromc.paper.core.board.BoardSpec;
import net.retromc.skywars.SkyWarsMode;
import net.retromc.skywars.kit.KitDef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Ingame-Board (Spec 04 §4.3, Scores 11..0): {@code Map}/{@code {map}}, {@code Kills}/{@code {kills}},
 * {@code Spieler}/{@code {lebendeSpieler}}, {@code Kit}/{@code {kit}} mit Leerzeilen; Zuschauer sehen dasselbe Board
 * (eigene Kills). Option {@code board.labelColon} hängt {@code :} an die Bezeichnungen.
 */
public final class SkyWarsBoard {

    private final SkyWarsMode mode;

    public SkyWarsBoard(SkyWarsMode mode) {
        this.mode = mode;
    }

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
        return BoardSpec.builder().title(() -> texts.i18n().render(locale.get(), "sb.title"))
                .blank()
                .line("map", () -> label(locale.get(), "sw.board.map"))
                .line("map-value", () -> texts.i18n().render(locale.get(), "sw.board.map.value",
                        Placeholder.unparsed("map", mode.game().maps().current().displayName())))
                .blank()
                .line("kills", () -> label(locale.get(), "sw.board.kills"))
                .line("kills-value", () -> texts.i18n().render(locale.get(), "sw.board.kills.value",
                        Placeholder.unparsed("kills", Integer.toString(mode.game().player(uuid)
                                .map(GamePlayer::kills).orElse(0)))))
                .blank()
                .line("players", () -> label(locale.get(), "sw.board.players"))
                .line("players-value", () -> texts.i18n().render(locale.get(), "sw.board.players.value",
                        Placeholder.unparsed("n", Integer.toString(mode.game().alivePlayers().size()))))
                .blank()
                .line("kit", () -> label(locale.get(), "sw.board.kit"))
                .line("kit-value", () -> kitValue(locale.get(), uuid))
                .build();
    }

    /** Bezeichnung, mit Option {@code board.labelColon} als {@code Map:}. */
    public Component label(Locale locale, String key) {
        Component label = mode.texts().i18n().render(locale, key);
        return mode.config().labelColon() ? label.append(mode.texts().i18n().render(locale, "sw.board.colon"))
                : label;
    }

    /** {@code §a{kit}}: Kit der Runde bzw. in der Wartelobby die Auswahl. */
    public Component kitValue(Locale locale, UUID player) {
        KitDef kit = mode.sw(player).kit();
        Component name = kit != null ? mode.texts().i18n().render(locale, kit.nameKey())
                : net.retromc.skywars.kit.KitGui.selectionName(mode.texts(), locale, mode.kits(), player);
        return mode.texts().i18n().render(locale, "sw.board.kit.value", Placeholder.component("kit", name));
    }
}
