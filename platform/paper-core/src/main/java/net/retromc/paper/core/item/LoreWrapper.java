package net.retromc.paper.core.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;

/**
 * Umbruch von Lore-Zeilen an Wortgrenzen (Spec 02 §5.1: höchstens 38 sichtbare Zeichen je Zeile, Annahme). Farben
 * und Formatierung bleiben je Zeichen erhalten; Leerzeichen an Umbruchstellen entfallen, Wörter über der Breite
 * werden hart getrennt. Eine leere Zeile bleibt eine leere Zeile.
 */
public final class LoreWrapper {

    /** Sichtbare Zeichen je Lore-Zeile. */
    public static final int MAX_VISIBLE = 38;

    /** Ein Textstück mit seinem wirksamen Stil. */
    private record Run(String text, Style style) {
    }

    private LoreWrapper() {
    }

    /** Bricht {@code line} auf {@link #MAX_VISIBLE} Zeichen um. */
    public static List<Component> wrap(Component line) {
        return wrap(line, MAX_VISIBLE);
    }

    /** Bricht {@code line} auf {@code width} sichtbare Zeichen um. */
    public static List<Component> wrap(Component line, int width) {
        if (width < 1) {
            throw new IllegalArgumentException("Breite muss mindestens 1 sein: " + width);
        }
        List<Run> runs = flatten(line);
        StringBuilder plain = new StringBuilder();
        runs.forEach(run -> plain.append(run.text()));
        if (plain.length() <= width) {
            return List.of(line);
        }
        List<Component> lines = new ArrayList<>();
        for (int[] range : ranges(plain.toString(), width)) {
            lines.add(slice(runs, range[0], range[1]));
        }
        return List.copyOf(lines);
    }

    /** Zeilen als Bereiche {@code [start, end)} im reinen Text. */
    static List<int[]> ranges(String text, int width) {
        List<int[]> ranges = new ArrayList<>();
        int position = 0;
        int length = text.length();
        while (position < length) {
            if (!ranges.isEmpty()) {
                while (position < length && text.charAt(position) == ' ') {
                    position++;
                }
                if (position >= length) {
                    break;
                }
            }
            int limit = position + width;
            int end;
            int next;
            if (limit >= length) {
                end = length;
                next = length;
            } else {
                int space = text.lastIndexOf(' ', limit);
                if (space > position) {
                    end = space;
                    next = space + 1;
                } else {
                    end = limit;
                    next = limit;
                }
            }
            while (end > position && text.charAt(end - 1) == ' ') {
                end--;
            }
            ranges.add(new int[] {position, end});
            position = next;
        }
        if (ranges.isEmpty()) {
            ranges.add(new int[] {0, 0});
        }
        return ranges;
    }

    private static List<Run> flatten(Component component) {
        List<Run> runs = new ArrayList<>();
        Deque<Style> styles = new ArrayDeque<>();
        styles.push(Style.empty());
        ComponentFlattener.basic().flatten(component, new FlattenerListener() {
            @Override
            public void pushStyle(Style style) {
                styles.push(style.merge(styles.peek(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
            }

            @Override
            public void component(String text) {
                if (!text.isEmpty()) {
                    runs.add(new Run(text, styles.peek()));
                }
            }

            @Override
            public void popStyle(Style style) {
                styles.pop();
            }
        });
        return runs;
    }

    private static Component slice(List<Run> runs, int start, int end) {
        TextComponent.Builder line = Component.text();
        int offset = 0;
        for (Run run : runs) {
            int runStart = offset;
            int runEnd = offset + run.text().length();
            offset = runEnd;
            int from = Math.max(start, runStart);
            int to = Math.min(end, runEnd);
            if (from < to) {
                line.append(Component.text(run.text().substring(from - runStart, to - runStart), run.style()));
            }
        }
        return line.build();
    }
}
