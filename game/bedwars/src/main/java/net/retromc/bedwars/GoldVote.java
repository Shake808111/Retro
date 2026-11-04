package net.retromc.bedwars;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.games.BedWarsStore;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.item.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Classic Gold-Vote (Spec 04 §5.8, §6.1.3, §10 #27): Kein/Wenig/Normal/Viel Gold (aus/60/30/15 s), Mehrheit,
 * Gleichstand → Normal; {@code /goldvote save} speichert die Wahl als Standard für künftige Runden.
 */
public final class GoldVote {

    /** Gold-Spawnrate. */
    public enum Rate {
        NONE("none", 0, Material.BARRIER, 1),
        LOW("low", 60, Material.GOLD_NUGGET, 3),
        NORMAL("normal", 30, Material.GOLD_INGOT, 5),
        HIGH("high", 15, Material.GOLD_BLOCK, 7);

        private final String id;
        private final int seconds;
        private final Material icon;
        private final int slot;

        Rate(String id, int seconds, Material icon, int slot) {
            this.id = id;
            this.seconds = seconds;
            this.icon = icon;
            this.slot = slot;
        }

        public String id() {
            return id;
        }

        /** Takt in Sekunden (0 = kein Gold). */
        public int seconds() {
            return seconds;
        }

        public static Optional<Rate> byId(String id) {
            for (Rate rate : values()) {
                if (rate.id.equalsIgnoreCase(id)) {
                    return Optional.of(rate);
                }
            }
            return Optional.empty();
        }
    }

    private final BedWarsMode mode;
    private final Map<UUID, Rate> votes = new ConcurrentHashMap<>();
    private volatile Rate result = Rate.NORMAL;

    GoldVote(BedWarsMode mode) {
        this.mode = mode;
    }

    /** Gewinner: Mehrheit, Gleichstand oder keine Stimme → Normal. */
    public static Rate winner(Map<UUID, Rate> votes) {
        Map<Rate, Integer> counts = new EnumMap<>(Rate.class);
        votes.values().forEach(rate -> counts.merge(rate, 1, Integer::sum));
        int best = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (best == 0) {
            return Rate.NORMAL;
        }
        java.util.List<Rate> top = counts.entrySet().stream().filter(entry -> entry.getValue() == best)
                .map(Map.Entry::getKey).toList();
        return top.size() == 1 ? top.getFirst() : Rate.NORMAL;
    }

    public Rate result() {
        return result;
    }

    public int votes(Rate rate) {
        return (int) votes.values().stream().filter(rate::equals).count();
    }

    /** Übernimmt die gespeicherte Wahl beim Join (still). */
    public void join(Player player) {
        if (!mode.classic()) {
            return;
        }
        mode.game().services().find(BedWarsStore.class).ifPresent(store -> mode.game().mainThread().accept(
                store.goldVote(player.getUniqueId()).exceptionally(error -> Optional.empty()), "Gold-Wahl",
                saved -> saved.flatMap(Rate::byId).ifPresent(rate -> votes.putIfAbsent(player.getUniqueId(),
                        rate))));
    }

    public void open(Player player) {
        if (!mode.classic()) {
            mode.texts().send(player, "bw.goldvote.wrong");
            return;
        }
        Locale locale = mode.texts().locale(player);
        Component title = mode.texts().i18n().render(locale, "bw.goldvote.title");
        mode.game().services().get(GuiService.class).open(player, new Gui(title, 1) {
            @Override
            protected void draw(GuiCanvas canvas) {
                for (Rate rate : Rate.values()) {
                    ItemBuilder item = ItemBuilder.of(rate.icon).texts(mode.texts().i18n(), canvas.locale())
                            .nameKey("bw.goldvote.item." + rate.id);
                    if (rate.seconds > 0) {
                        item.loreKey("bw.goldvote.interval", Placeholder.unparsed("n",
                                Integer.toString(rate.seconds)));
                    }
                    item.loreKey("bw.goldvote.votes", Placeholder.unparsed("n", Integer.toString(votes(rate))));
                    if (votes.get(player.getUniqueId()) == rate) {
                        item.glow();
                    }
                    canvas.button(rate.slot, item.build(), click -> {
                        vote(click.player(), rate);
                        refresh();
                    });
                }
                canvas.button(8, ItemBuilder.of(Material.WRITABLE_BOOK).texts(mode.texts().i18n(), canvas.locale())
                        .nameKey("bw.goldvote.save.item").build(), click -> save(click.player()));
            }
        });
    }

    public void vote(Player player, Rate rate) {
        if (!mode.classic() || !mode.game().lifecycle().waiting()) {
            mode.texts().send(player, "bw.goldvote.wrong");
            return;
        }
        votes.put(player.getUniqueId(), rate);
        mode.texts().send(player, "bw.goldvote.voted", Placeholder.component("rate", rateName(player, rate)));
    }

    public void save(Player player) {
        if (!mode.classic()) {
            mode.texts().send(player, "bw.goldvote.wrong");
            return;
        }
        Rate rate = votes.get(player.getUniqueId());
        if (rate == null) {
            mode.texts().send(player, "bw.goldvote.none");
            return;
        }
        mode.game().services().find(BedWarsStore.class).ifPresent(store -> mode.game().mainThread().accept(
                store.saveGoldVote(player.getUniqueId(), rate.id), "Gold-Wahl speichern",
                ignored -> mode.texts().send(player, "bw.goldvote.saved")));
    }

    /** Wertet beim Start aus und setzt den Gold-Takt (nur Classic). */
    public void finish() {
        if (!mode.classic()) {
            return;
        }
        result = winner(Map.copyOf(votes));
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            mode.texts().send(player, "bw.goldvote.result", Placeholder.component("rate", rateName(player, result)));
        }
    }

    private Component rateName(Player player, Rate rate) {
        return mode.texts().i18n().render(mode.texts().locale(player), "bw.goldvote.rate." + rate.id);
    }
}
