package net.retromc.paper.core.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jspecify.annotations.Nullable;

/**
 * Plattformfreier Inhalt eines Items aus dem {@link ItemBuilder} (Spec 02 §5.5): Name und Lore nicht kursiv, Lore
 * umgebrochen ({@link LoreWrapper}), Menge, Glanz, ausgeblendete Tooltips und PDC-Markierungen.
 *
 * @param name Anzeigename oder {@code null} (Standardname des Materials)
 * @param lore Lore-Zeilen, bereits umgebrochen
 * @param amount Menge 1–99
 * @param glow Verzauberungsglanz erzwingen
 * @param hideAll Attribute, Verzauberungen, Trims und Tooltip-Zusätze ausblenden
 * @param hideTooltip ganzen Tooltip ausblenden (Rahmen-Glas)
 * @param flags PDC-Einträge im Namespace der Marke (Schlüssel → Wert)
 */
public record ItemSpec(@Nullable Component name, List<Component> lore, int amount, boolean glow, boolean hideAll,
        boolean hideTooltip, Map<String, String> flags) {

    /** Größte Stapelgröße. */
    public static final int MAX_AMOUNT = 99;

    /** Erlaubte PDC-Schlüssel (Regeln von {@code NamespacedKey}). */
    public static final Pattern FLAG_KEY = Pattern.compile("[a-z0-9._/-]+");

    public ItemSpec {
        lore = List.copyOf(lore);
        flags = Map.copyOf(flags);
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Menge muss zwischen 1 und 99 liegen: " + amount);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Text ohne Kursivschrift, sofern nicht ausdrücklich gesetzt. */
    static Component upright(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Veränderlicher Bauplan. */
    public static final class Builder {

        private @Nullable Component name;
        private final List<Component> lore = new ArrayList<>();
        private int amount = 1;
        private boolean glow;
        private boolean hideAll;
        private boolean hideTooltip;
        private final Map<String, String> flags = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder name(Component name) {
            this.name = upright(Objects.requireNonNull(name, "name"));
            return this;
        }

        /** Ersetzt die Lore. */
        public Builder lore(List<? extends Component> lines) {
            lore.clear();
            return addLore(lines);
        }

        /** Hängt Zeilen an; jede wird umgebrochen und nicht kursiv. */
        public Builder addLore(List<? extends Component> lines) {
            for (Component line : lines) {
                for (Component wrapped : LoreWrapper.wrap(Objects.requireNonNull(line, "line"))) {
                    lore.add(upright(wrapped));
                }
            }
            return this;
        }

        public Builder amount(int amount) {
            if (amount < 1 || amount > MAX_AMOUNT) {
                throw new IllegalArgumentException("Menge muss zwischen 1 und 99 liegen: " + amount);
            }
            this.amount = amount;
            return this;
        }

        public Builder glow() {
            this.glow = true;
            return this;
        }

        public Builder hideAll() {
            this.hideAll = true;
            return this;
        }

        public Builder hideTooltip() {
            this.hideTooltip = true;
            return this;
        }

        /** PDC-Eintrag {@code <brandId>:<key>} = {@code value}, z. B. {@code flag("action", "party:invite")}. */
        public Builder flag(String key, String value) {
            if (!FLAG_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Ungültiger Schlüssel (a-z, 0-9, . _ / -): '" + key + "'");
            }
            flags.put(key, Objects.requireNonNull(value, "value"));
            return this;
        }

        public ItemSpec build() {
            return new ItemSpec(name, lore, amount, glow, hideAll, hideTooltip, flags);
        }
    }
}
