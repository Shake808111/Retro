package net.retromc.game.framework.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameTexts;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

/**
 * Team-Wahl „Wähle dein Team“ (Spec 03 §5.10; Modus-Darstellung z. B. BedWars Spec 04 §5.3). Zeichnet den
 * aktuellen Stand bei jedem Öffnen und nach {@link #refresh()}.
 */
public final class TeamGui extends Gui {

    /**
     * Darstellung.
     *
     * @param compact    {@code true}: Slots 0…n−1, Reihen = ⌈Teams/9⌉ (Spec 04 §5.3); sonst Layout §5.10
     * @param nameKey    Itemname
     * @param countKey   Zeile „{cur}/{size} Spieler“ oder {@code null}
     * @param memberKey  Zeile je Mitglied
     * @param freeKey    Zeile je freiem Platz oder {@code null}
     * @param joinKey    letzte Zeile „beitreten“
     * @param ownKey     letzte Zeile im eigenen Team oder {@code null} (dann {@code joinKey})
     * @param fullKey    letzte Zeile bei vollem Team
     * @param glowOwn    eigenes Team mit Glanz
     * @param leave      Knopf „Team verlassen“ in der Mitte der letzten Reihe
     */
    public record Style(boolean compact, String nameKey, @Nullable String countKey, String memberKey,
            @Nullable String freeKey, String joinKey, @Nullable String ownKey, String fullKey, boolean glowOwn,
            boolean leave) {

        /** Rahmen-Standard (Spec 03 §5.10). */
        public static final Style FRAME = new Style(false, "game.team.item", null, "game.team.member",
                "game.team.free", "game.team.join", "game.team.own", "game.team.full", false, true);
    }

    private final Game game;
    private final Style style;

    private TeamGui(Game game, Style style, Component title, int rows) {
        super(title, rows);
        this.game = game;
        this.style = style;
    }

    /** GUI für {@code locale}. */
    public static TeamGui create(Game game, Locale locale) {
        Style style = game.mode().teamGuiStyle();
        int teams = game.teams().teams().size();
        int rows = style.compact() ? Math.max(1, Math.min(6, (teams + 8) / 9)) : TeamGuiLayout.of(teams).rows();
        Component title = game.texts().i18n().render(locale, "game.team.title");
        return new TeamGui(game, style, title, rows);
    }

    @Override
    protected void draw(GuiCanvas canvas) {
        GameTexts texts = game.texts();
        Locale locale = canvas.locale();
        UUID viewer = canvas.viewer().getUniqueId();
        List<GameTeam> teams = game.teams().teams();
        List<Integer> slots = style.compact() ? compactSlots(teams.size()) : TeamGuiLayout.of(teams.size()).slots();
        for (int i = 0; i < teams.size() && i < slots.size(); i++) {
            GameTeam team = teams.get(i);
            boolean own = team.contains(viewer);
            List<Component> lore = new ArrayList<>();
            if (style.countKey() != null) {
                lore.add(texts.i18n().render(locale, style.countKey(), Placeholder.unparsed("cur",
                        Integer.toString(team.size())), Placeholder.unparsed("size",
                        Integer.toString(team.capacity()))));
            }
            for (UUID member : team.members()) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(member);
                String name = offline.getName() == null ? member.toString() : offline.getName();
                lore.add(texts.i18n().render(locale, style.memberKey(), texts.name("name", member, name)));
            }
            if (style.freeKey() != null) {
                for (int free = team.size(); free < team.capacity(); free++) {
                    lore.add(texts.i18n().render(locale, style.freeKey()));
                }
            }
            lore.add(Component.empty());
            String last = own && style.ownKey() != null ? style.ownKey()
                    : team.full() && !own ? style.fullKey() : style.joinKey();
            lore.add(texts.i18n().render(locale, last));
            // nummerierte Teams: Icon des Modus (SkyWars WHITE_BANNER, Spec 04 §5.12)
            Material wool = team.color() == null ? game.mode().teamItem()
                    : Material.matchMaterial(team.color().wool());
            ItemBuilder item = ItemBuilder.of(wool == null ? Material.WHITE_WOOL : wool)
                    .name(texts.i18n().render(locale, style.nameKey(), texts.team(locale, team),
                            Placeholder.unparsed("n", Integer.toString(team.size())),
                            Placeholder.unparsed("size", Integer.toString(team.capacity())))
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(lore).hideAll();
            if (team.color() == null) {
                item.amount(Math.min(64, team.index() + 1));
            }
            if (own && style.glowOwn()) {
                item.glow();
            }
            canvas.button(slots.get(i), item.build(), click -> game.chooseTeam(click.player(), team));
        }
        if (style.leave() && !style.compact()) {
            int slot = TeamGuiLayout.of(teams.size()).leave();
            canvas.button(slot, ItemBuilder.of(Material.BARRIER).texts(texts.i18n(), locale)
                    .nameKey("game.team.leave").loreKey("game.team.leave.lore").build(),
                    click -> game.leaveTeam(click.player()));
        }
    }

    private static List<Integer> compactSlots(int teams) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < teams; i++) {
            slots.add(i);
        }
        return slots;
    }
}
