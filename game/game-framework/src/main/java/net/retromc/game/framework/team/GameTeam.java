package net.retromc.game.framework.team;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.text.format.TextColor;
import net.retromc.platform.common.text.ColorText;
import org.jspecify.annotations.Nullable;

/**
 * Ein Team der Runde (Spec 03 §6.6). Farbteams tragen eine {@link TeamColor}, nummerierte Teams ({@code Team #n})
 * nur eine Nummer.
 */
public final class GameTeam {

    private final int index;
    private final String id;
    private final @Nullable TeamColor color;
    private final int capacity;
    private final List<UUID> members = new CopyOnWriteArrayList<>();

    public GameTeam(int index, String id, @Nullable TeamColor color, int capacity) {
        this.index = index;
        this.id = Objects.requireNonNull(id, "id");
        this.color = color;
        this.capacity = capacity;
    }

    /** Reihenfolge-Nummer ab 0 (Board, Tab, Zuteilung). */
    public int index() {
        return index;
    }

    /** Team-ID wie in {@code map.json} ({@code red} … bzw. {@code 1} …). */
    public String id() {
        return id;
    }

    public @Nullable TeamColor color() {
        return color;
    }

    public int capacity() {
        return capacity;
    }

    public List<UUID> members() {
        return List.copyOf(members);
    }

    public int size() {
        return members.size();
    }

    public boolean full() {
        return members.size() >= capacity;
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    void add(UUID player) {
        if (!members.contains(player)) {
            members.add(player);
        }
    }

    void remove(UUID player) {
        members.remove(player);
    }

    void clear() {
        members.clear();
    }

    /** Farbe als §-Code/Hex ({@code §f} bei nummerierten Teams). */
    public String colorCode() {
        return color != null ? color.color() : "§f";
    }

    public TextColor textColor() {
        return ColorText.color(colorCode());
    }

    /** Textschlüssel des Namens; nummerierte Teams {@code team.name.numbered} mit {@code <n>}. */
    public String nameKey() {
        return color != null ? color.nameKey() : "team.name.numbered";
    }

    /** Blockfarbe ({@code DyeColor}-Name), {@code WHITE} bei nummerierten Teams. */
    public String dye() {
        return color != null ? color.dye() : "WHITE";
    }

    @Override
    public String toString() {
        return "Team[" + id + "]";
    }
}
