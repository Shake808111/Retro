package net.retromc.paper.core.social.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.social.Setting;
import org.jspecify.annotations.Nullable;

/**
 * Plattformfreier Bauplan einer Social-GUI (Spec 01 §5): Titel, Reihen und je Slot Material, Texte und Klick. Die
 * Pläne entstehen in {@link SocialGuiPlans} und sind ohne Server testbar (A24); {@code PlanGui} zeichnet sie.
 *
 * @param titleKey i18n-Schlüssel des Titels
 * @param rows Reihen (1–6)
 */
public record GuiPlan(String titleKey, TagResolver titleArgs, int rows, Map<Integer, Slot> slots) {

    public GuiPlan {
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(titleArgs, "titleArgs");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("1–6 Reihen: " + rows);
        }
        slots = Map.copyOf(slots);
        for (int slot : slots.keySet()) {
            if (slot < 0 || slot >= rows * 9) {
                throw new IllegalArgumentException("Slot außerhalb: " + slot);
            }
        }
    }

    public Optional<Slot> slot(int index) {
        return Optional.ofNullable(slots.get(index));
    }

    /**
     * Ein Slot.
     *
     * @param material Bukkit-Material ({@code PLAYER_HEAD}, {@code PURPLE_STAINED_GLASS_PANE} …)
     * @param name fertiger Name (z. B. Spielername in Rangfarbe) statt {@code nameKey}
     * @param head Besitzer eines Spielerkopfs
     * @param texture Textur eines Kopfs (Nick-Skin)
     */
    public record Slot(String material, @Nullable String nameKey, @Nullable Component name, List<String> loreKeys,
            TagResolver args, @Nullable UUID head, @Nullable String texture, int amount, Click click) {

        public Slot {
            Objects.requireNonNull(material, "material");
            loreKeys = List.copyOf(loreKeys);
            Objects.requireNonNull(args, "args");
            Objects.requireNonNull(click, "click");
        }

        /** Glasscheibe ohne Namen ({@code §r }). */
        public static Slot glass(String material) {
            return new Slot(material, null, null, List.of(), TagResolver.empty(), null, null, 1, Click.NONE);
        }

        public boolean isGlass() {
            return material.endsWith("_STAINED_GLASS_PANE") && nameKey == null && name == null;
        }
    }

    /** Klickwirkung eines Slots. */
    public sealed interface Click {

        Click NONE = new None();

        /** Keine Wirkung (Glas, Info). */
        record None() implements Click {
        }

        /**
         * Proxy-Befehle über {@code RUN_COMMAND} (ohne {@code /}) je Klickart; {@code null} = keine Wirkung.
         *
         * @param confirmKey Frage einer Bestätigungs-GUI vor dem Ausführen oder {@code null}
         */
        record Commands(@Nullable String left, @Nullable String right, @Nullable String shiftRight,
                @Nullable String confirmKey) implements Click {

            public static Commands any(String command) {
                return new Commands(command, command, command, null);
            }

            public static Commands confirmed(String command, String confirmKey) {
                return new Commands(command, command, command, confirmKey);
            }

            /** Befehl für eine Klickart. */
            public @Nullable String forClick(boolean left, boolean shift) {
                if (left) {
                    return this.left;
                }
                return shift && shiftRight != null ? shiftRight : right;
            }
        }

        /** Linksklick und Rechtsklick mit verschiedener Wirkung (Freunde: Nachspringen / Optionen). */
        record Split(Click left, Click right) implements Click {
        }

        /** Andere Social-GUI öffnen. */
        record Open(String guiId, String argument) implements Click {
        }

        /** Einstellung zyklisch umschalten ({@code SettingsService.cycle}) und neu zeichnen. */
        record Toggle(Setting setting) implements Click {
        }

        /** Aktion der GUI selbst (z. B. Beitrittsmodus des Clans, Blättern, Filter). */
        record Action(String id) implements Click {
        }

        /** GUI schließen und einen Befehl als klickbaren Chat-Vorschlag senden. */
        record Suggest(String command) implements Click {
        }
    }

    /** Hilfsbau für Pläne. */
    static final class Builder {

        private final String titleKey;
        private final TagResolver titleArgs;
        private final int rows;
        private final Map<Integer, Slot> slots = new TreeMap<>();

        Builder(String titleKey, TagResolver titleArgs, int rows) {
            this.titleKey = titleKey;
            this.titleArgs = titleArgs;
            this.rows = rows;
        }

        Builder glass(String material, int... indices) {
            for (int index : indices) {
                slots.put(index, Slot.glass(material));
            }
            return this;
        }

        Builder glassRange(String material, int fromInclusive, int toInclusive) {
            for (int index = fromInclusive; index <= toInclusive; index++) {
                slots.put(index, Slot.glass(material));
            }
            return this;
        }

        Builder item(int index, String material, String nameKey, List<String> lore, TagResolver args, Click click) {
            slots.put(index, new Slot(material, nameKey, null, lore, args, null, null, 1, click));
            return this;
        }

        Builder slot(int index, Slot slot) {
            slots.put(index, slot);
            return this;
        }

        GuiPlan build() {
            return new GuiPlan(titleKey, titleArgs, rows, slots);
        }
    }

    /** Lore-Schlüssel als Liste (leere Einträge überspringen). */
    static List<String> lore(@Nullable String... keys) {
        List<String> result = new ArrayList<>();
        for (String key : keys) {
            if (key != null) {
                result.add(key);
            }
        }
        return result;
    }
}
