package net.retromc.paper.core.board;

import fr.mrmicky.fastboard.adventure.FastBoard;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.retromc.core.api.rank.Rank;
import net.retromc.core.api.rank.RankDisplay;
import net.retromc.core.api.server.ServerCategory;
import net.retromc.core.api.social.OnlineCount;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.paper.core.event.LocaleChangedEvent;
import net.retromc.paper.core.event.ProfileLoadedEvent;
import net.retromc.paper.core.event.ProfileUpdatedEvent;
import net.retromc.paper.core.profile.PlayerProfiles;
import net.retromc.paper.core.profile.ProfileView;
import net.retromc.platform.common.feature.CoreFeature;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * Sidebar-Dienst auf FastBoard ({@link BoardService}, Spec 02 §4.1, §6.3) und das Lobby-Scoreboard (§4.2) auf
 * Lobby-Servern: Ladezustand beim Join, Werte nach {@code ProfileLoadedEvent}, betroffene Zeilen bei
 * {@code ProfileUpdatedEvent} (Coins, Gold, Rang, Clan, Freunde), Rang ⇄ Spielzeit alle 100 Ticks. FastBoard sendet
 * nur geänderte Zeilen.
 */
public final class BoardFeature implements CoreFeature<PaperCore>, Listener, BoardService {

    /** Angezeigtes Board eines Spielers. */
    private static final class Shown {
        final FastBoard board;
        BoardSpec spec;
        final Map<String, Component> overrides = new HashMap<>();
        boolean lobby;

        Shown(FastBoard board, BoardSpec spec) {
            this.board = board;
            this.spec = spec;
        }
    }

    private final Map<UUID, Shown> boards = new ConcurrentHashMap<>();
    private final List<Registration> registrations = new ArrayList<>();
    private volatile boolean showRank = true;
    private @Nullable PaperCore core;
    private @Nullable BukkitTask toggle;

    @Override
    public String name() {
        return "Scoreboard";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        context.plugin().getServer().getPluginManager().registerEvents(this, context.plugin());
        registrations.add(context.services().registerDefault(BoardService.class, this));
        toggle = Bukkit.getScheduler().runTaskTimer(context.plugin(), this::toggleRank, LobbyBoard.TOGGLE_TICKS,
                LobbyBoard.TOGGLE_TICKS);
        if (lobby(context)) {
            Bukkit.getOnlinePlayers().forEach(this::showLobby);
        }
    }

    @Override
    public void disable(PaperCore context) {
        HandlerList.unregisterAll(this);
        BukkitTask task = toggle;
        if (task != null) {
            task.cancel();
        }
        toggle = null;
        List<Registration> reversed = new ArrayList<>(registrations);
        Collections.reverse(reversed);
        reversed.forEach(Registration::close);
        registrations.clear();
        boards.values().forEach(shown -> shown.board.delete());
        boards.clear();
        core = null;
    }

    private static boolean lobby(PaperCore context) {
        return context.config().category() == ServerCategory.LOBBY;
    }

    // ------------------------------------------------------------------------------------------ BoardService

    @Override
    public void show(Player player, BoardSpec spec) {
        Shown shown = boards.computeIfAbsent(player.getUniqueId(), uuid -> new Shown(new FastBoard(player), spec));
        shown.spec = spec;
        shown.overrides.clear();
        shown.lobby = false;
        render(shown);
    }

    @Override
    public void update(Player player, String lineId) {
        Shown shown = boards.get(player.getUniqueId());
        if (shown == null) {
            return;
        }
        int index = shown.spec.index(lineId);
        if (index >= 0) {
            shown.overrides.remove(lineId);
            send(shown, index, shown.spec.lines().get(index).value().get());
        }
    }

    @Override
    public void update(Player player, String lineId, Component value) {
        Shown shown = boards.get(player.getUniqueId());
        if (shown == null) {
            return;
        }
        int index = shown.spec.index(lineId);
        if (index >= 0) {
            shown.overrides.put(lineId, value);
            send(shown, index, value);
        }
    }

    @Override
    public void refresh(Player player) {
        Shown shown = boards.get(player.getUniqueId());
        if (shown != null) {
            render(shown);
        }
    }

    @Override
    public void hide(Player player) {
        Shown shown = boards.remove(player.getUniqueId());
        if (shown != null) {
            shown.board.delete();
        }
    }

    @Override
    public Optional<BoardSpec> current(Player player) {
        return Optional.ofNullable(boards.get(player.getUniqueId())).map(shown -> shown.spec);
    }

    /** Zeichnet Titel und Zeilen; bei gleicher Zeilenzahl gehen nur geänderte Zeilen hinaus (§9 #14). */
    private static void render(Shown shown) {
        Component title = shown.spec.title().get();
        if (!title.equals(shown.board.getTitle())) {
            shown.board.updateTitle(title);
        }
        List<Component> lines = new ArrayList<>();
        for (BoardSpec.Line line : shown.spec.lines()) {
            Component override = shown.overrides.get(line.id());
            lines.add(override != null ? override : line.value().get());
        }
        if (shown.board.size() == lines.size()) {
            for (int i = 0; i < lines.size(); i++) {
                send(shown, i, lines.get(i));
            }
        } else {
            shown.board.updateLines(lines);
        }
        if (shown.spec.lines().stream().anyMatch(line -> line.score() != null)) {
            for (int i = 0; i < lines.size(); i++) {
                sendScore(shown, i);
            }
        } else if (shown.spec.showScores()) {
            for (int i = 0; i < lines.size(); i++) {
                if (shown.board.getScore(i).isPresent()) {
                    shown.board.removeScore(i);
                }
            }
        } else {
            shown.board.updateScores(Collections.nCopies(lines.size(), Component.empty()));
        }
    }

    /** Sendet nur, wenn sich die Zeile ändert (kein Flackern, §9 #14). */
    private static void send(Shown shown, int index, Component value) {
        if (index >= shown.board.size() || !Objects.equals(shown.board.getLine(index), value)) {
            shown.board.updateLine(index, value);
        }
        sendScore(shown, index);
    }

    /** Eigene Score-Anzeige einer Zeile (Spec 04 §4.2), nur bei Änderung. */
    private static void sendScore(Shown shown, int index) {
        if (index >= shown.spec.lines().size() || index >= shown.board.size()) {
            return;
        }
        java.util.function.Supplier<Component> score = shown.spec.lines().get(index).score();
        if (score == null) {
            return;
        }
        Component value = score.get();
        if (!Objects.equals(shown.board.getScore(index).orElse(null), value)) {
            shown.board.updateScore(index, value);
        }
    }

    // ------------------------------------------------------------------------------------------ Lobby-Board

    private void showLobby(Player player) {
        PaperCore context = core;
        if (context == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        I18n i18n = context.services().get(I18n.class);
        BoardSpec spec = LobbyBoard.spec(i18n, () -> i18n.locale(uuid), () -> state(context, uuid), () -> showRank);
        show(player, spec);
        boards.get(uuid).lobby = true;
    }

    private static LobbyBoard.State state(PaperCore context, UUID uuid) {
        Optional<ProfileView> view = context.services().find(PlayerProfiles.class).flatMap(p -> p.view(uuid));
        if (view.isEmpty()) {
            return new LobbyBoard.State(false, RankDisplay.of(Rank.DEFAULT), 0, 0, 0, null, null,
                    new OnlineCount(0, 0));
        }
        ProfileView loaded = view.get();
        return new LobbyBoard.State(true, loaded.displayRank(), loaded.playtimeSeconds(Instant.now()),
                loaded.profile().coins(), loaded.profile().gold(), loaded.clan(), loaded.clanTag(), loaded.friends());
    }

    private void toggleRank() {
        showRank = !showRank;
        for (Map.Entry<UUID, Shown> entry : boards.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && entry.getValue().lobby) {
                update(player, "rank_label");
                update(player, "rank_value");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PaperCore context = core;
        if (context != null && lobby(context)) {
            showLobby(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hide(event.getPlayer());
    }

    @EventHandler
    public void onLoaded(ProfileLoadedEvent event) {
        refresh(event.player());
    }

    @EventHandler
    public void onUpdated(ProfileUpdatedEvent event) {
        refresh(event.player());
    }

    @EventHandler
    public void onLocale(LocaleChangedEvent event) {
        refresh(event.player());
    }
}
