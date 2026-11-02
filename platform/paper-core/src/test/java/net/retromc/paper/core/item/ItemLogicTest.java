package net.retromc.paper.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/** Logik des Item-Builders ohne Server (Spec 02 §5.1, §5.5). */
class ItemLogicTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> plain(List<Component> components) {
        return components.stream().map(ItemLogicTest::plain).toList();
    }

    @Test
    void kurzeZeileBleibtUnveraendert() {
        Component line = Component.text("Klicke zum Betreten", NamedTextColor.GRAY);

        assertEquals(List.of(line), LoreWrapper.wrap(line));
    }

    @Test
    void umbruchAnWortgrenzenMitHoechstens38Zeichen() {
        Component line = Component.text("Klicke hier, um deine Auswahl zu bestätigen und das Menü zu schließen.");

        List<String> lines = plain(LoreWrapper.wrap(line));

        assertEquals(List.of("Klicke hier, um deine Auswahl zu", "bestätigen und das Menü zu schließen."), lines);
        assertTrue(lines.stream().allMatch(text -> text.length() <= LoreWrapper.MAX_VISIBLE));
    }

    @Test
    void farbenBleibenUeberDenUmbruchErhalten() {
        Component line = Component.text()
                .append(Component.text("Label: ", NamedTextColor.GRAY))
                .append(Component.text("ein sehr langer Wert", NamedTextColor.WHITE))
                .build();

        List<Component> lines = LoreWrapper.wrap(line, 12);

        assertEquals(List.of("Label: ein", "sehr langer", "Wert"), plain(lines));
        Component second = lines.get(1);
        assertTrue(second.children().stream().allMatch(part -> NamedTextColor.WHITE.equals(part.color())));
        assertEquals(NamedTextColor.GRAY, lines.getFirst().children().getFirst().color());
    }

    @Test
    void zuLangeWoerterWerdenHartGetrennt() {
        assertEquals(List.of("abcde", "fghij", "k"), plain(LoreWrapper.wrap(Component.text("abcdefghijk"), 5)));
    }

    @Test
    void bereicheIgnorierenMehrfacheLeerzeichen() {
        List<int[]> ranges = LoreWrapper.ranges("ab   cd  ef", 4);

        assertEquals(List.of("ab", "cd", "ef"), ranges.stream()
                .map(range -> "ab   cd  ef".substring(range[0], range[1])).toList());
        assertEquals(1, LoreWrapper.ranges("", 38).size());
    }

    @Test
    void nameUndLoreNichtKursivUndUmgebrochen() {
        ItemSpec spec = ItemSpec.builder()
                .name(Component.text("Bestätigen", NamedTextColor.GREEN))
                .lore(List.of(Component.text("Klicke hier, um deine Auswahl zu bestätigen."), Component.empty()))
                .build();

        assertEquals(TextDecoration.State.FALSE, spec.name().decoration(TextDecoration.ITALIC));
        assertEquals(3, spec.lore().size());
        assertTrue(spec.lore().stream()
                .allMatch(line -> line.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE));
        assertEquals("", plain(spec.lore().getLast()));
    }

    @Test
    void ausdruecklichKursivBleibtKursiv() {
        ItemSpec spec = ItemSpec.builder().name(Component.text("x").decorate(TextDecoration.ITALIC)).build();

        assertEquals(TextDecoration.State.TRUE, spec.name().decoration(TextDecoration.ITALIC));
    }

    @Test
    void standardwerteUndSchalter() {
        ItemSpec plainItem = ItemSpec.builder().build();
        assertNull(plainItem.name());
        assertEquals(1, plainItem.amount());
        assertEquals(List.of(), plainItem.lore());

        ItemSpec spec = ItemSpec.builder().amount(64).glow().hideAll().hideTooltip()
                .flag("action", "party:invite").build();
        assertEquals(64, spec.amount());
        assertTrue(spec.glow() && spec.hideAll() && spec.hideTooltip());
        assertEquals(Map.of("action", "party:invite"), spec.flags());
    }

    @Test
    void ungueltigeMengeUndSchluessel() {
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.builder().amount(0));
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.builder().amount(100));
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.builder().flag("Action", "x"));
        assertThrows(IllegalArgumentException.class, () -> ItemSpec.builder().flag("a b", "x"));
    }

    @Test
    void loreErsetztOderHaengtAn() {
        ItemSpec.Builder builder = ItemSpec.builder().lore(List.of(Component.text("a")));
        builder.addLore(List.of(Component.text("b")));
        builder.lore(List.of(Component.text("c")));

        assertEquals(List.of("c"), plain(builder.build().lore()));
    }

    @Test
    void farbvarianten() {
        assertEquals(Optional.of("LIME_STAINED_GLASS_PANE"),
                DyedMaterials.recolor("WHITE_STAINED_GLASS_PANE", "LIME"));
        assertEquals(Optional.of("BLUE_WOOL"), DyedMaterials.recolor("LIGHT_BLUE_WOOL", "BLUE"));
        assertEquals(Optional.of("LIGHT_GRAY_CONCRETE"), DyedMaterials.recolor("GRAY_CONCRETE", "LIGHT_GRAY"));
        assertEquals(Optional.empty(), DyedMaterials.recolor("LEATHER_CHESTPLATE", "RED"));
        assertEquals(Optional.empty(), DyedMaterials.recolor("BLACKSTONE", "RED"));
        assertEquals("LIGHT_BLUE_STAINED_GLASS_PANE", DyedMaterials.glassPane("LIGHT_BLUE"));
        assertThrows(IllegalArgumentException.class, () -> DyedMaterials.recolor("WHITE_WOOL", "TEAL"));
    }
}
