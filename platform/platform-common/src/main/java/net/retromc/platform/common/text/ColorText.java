package net.retromc.platform.common.text;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.Nullable;

/**
 * Farben der Specs als Adventure-Bausteine: Rangfarben als §-Code ({@code §4}, Spec 01 §6.1), Modusfarben als
 * MiniMessage-Name ({@code red}, {@code stats.mode.color}), Verläufe {@code #RRGGBB:#RRGGBB} (Supremium+,
 * JumpLeague; nur MODERN, LEGACY fällt auf die Hauptfarbe zurück, Spec 02 §6.1 Nr. 2, §9 #17).
 */
public final class ColorText {

    private static final Map<Character, NamedTextColor> LEGACY = Map.ofEntries(
            Map.entry('0', NamedTextColor.BLACK), Map.entry('1', NamedTextColor.DARK_BLUE),
            Map.entry('2', NamedTextColor.DARK_GREEN), Map.entry('3', NamedTextColor.DARK_AQUA),
            Map.entry('4', NamedTextColor.DARK_RED), Map.entry('5', NamedTextColor.DARK_PURPLE),
            Map.entry('6', NamedTextColor.GOLD), Map.entry('7', NamedTextColor.GRAY),
            Map.entry('8', NamedTextColor.DARK_GRAY), Map.entry('9', NamedTextColor.BLUE),
            Map.entry('a', NamedTextColor.GREEN), Map.entry('b', NamedTextColor.AQUA),
            Map.entry('c', NamedTextColor.RED), Map.entry('d', NamedTextColor.LIGHT_PURPLE),
            Map.entry('e', NamedTextColor.YELLOW), Map.entry('f', NamedTextColor.WHITE));

    private ColorText() {
    }

    /**
     * Farbe aus {@code §x}/{@code &x}, MiniMessage-Name ({@code red}) oder {@code #RRGGBB}; Unbekanntes wird weiß.
     */
    public static TextColor color(String value) {
        String text = Objects.requireNonNull(value, "value").strip();
        if (text.length() == 2 && (text.charAt(0) == '§' || text.charAt(0) == '&')) {
            NamedTextColor named = LEGACY.get(Character.toLowerCase(text.charAt(1)));
            return named == null ? NamedTextColor.WHITE : named;
        }
        if (text.startsWith("#")) {
            TextColor hex = TextColor.fromHexString(text);
            return hex == null ? NamedTextColor.WHITE : hex;
        }
        NamedTextColor named = NamedTextColor.NAMES.value(text.toLowerCase(Locale.ROOT));
        return named == null ? NamedTextColor.WHITE : named;
    }

    /** §-Code einer benannten Farbe ({@code red} → {@code §c}); Hex-Farben ergeben die nächste benannte Farbe. */
    public static String legacyCode(String value) {
        NamedTextColor named = NamedTextColor.nearestTo(color(value));
        return LEGACY.entrySet().stream().filter(entry -> entry.getValue().equals(named)).findFirst()
                .map(entry -> "§" + entry.getKey()).orElse("§f");
    }

    /** Text in einer Farbe (ohne Kursiv). */
    public static Component colored(String color, String text) {
        return Component.text(text, color(color)).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Name in Rang- oder Modusfarbe; mit {@code gradient} und {@code modern} als Verlauf je Zeichen (wie MiniMessage
     * {@code <gradient>}), sonst einfarbig in {@code color}.
     */
    public static Component name(String text, String color, @Nullable String gradient, boolean modern) {
        if (!modern || gradient == null || gradient.isBlank()) {
            return colored(color, text);
        }
        String[] stops = gradient.split(":");
        if (stops.length != 2 || text.isEmpty()) {
            return colored(color, text);
        }
        TextColor from = color(stops[0]);
        TextColor to = color(stops[1]);
        TextComponent.Builder builder = Component.text();
        int[] points = text.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            float progress = points.length == 1 ? 0f : (float) i / (points.length - 1);
            builder.append(Component.text(new String(points, i, 1), TextColor.lerp(progress, from, to)));
        }
        return builder.build().decoration(TextDecoration.ITALIC, false);
    }

    /** Öffnender Farb-Tag für Textschlüssel, z. B. {@code <rank_color>} oder {@code <mode_color>}. */
    public static TagResolver styling(String tag, String color) {
        return TagResolver.resolver(tag, Tag.styling(color(color)));
    }
}
