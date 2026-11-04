package net.retromc.game.framework.spectate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GamePlayer;
import net.retromc.game.framework.GameTexts;
import net.retromc.game.framework.event.SpectatorEvent;
import net.retromc.game.framework.kill.HeartLine;
import net.retromc.paper.core.bar.ActionbarService;
import net.retromc.paper.core.gui.Gui;
import net.retromc.paper.core.gui.GuiCanvas;
import net.retromc.paper.core.gui.GuiService;
import net.retromc.paper.core.hotbar.HotbarLayout;
import net.retromc.paper.core.hotbar.HotbarService;
import net.retromc.paper.core.item.ItemBuilder;
import net.retromc.paper.core.server.PlayerTransfer;
import net.retromc.paper.core.server.QueueClient;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Zuschauer (Spec 03 §6.8, §4.11, §5.12, §5.13): ADVENTURE mit Flug, unsichtbar für Lebende, keine Kollision,
 * unverwundbar, kein Aufheben; Herzen und Hunger bleiben sichtbar (kein Vanilla-Zuschauermodus). Hotbar 1 Teleporter,
 * 7 Einstellungen, 8 Nächste Runde, 9 Zurück zur Lobby.
 */
public final class SpectatorService {

    /** Actionbar-Kanal des Rahmens. */
    public static final String ACTIONBAR = "game";
    /** Fluggeschwindigkeiten der Stufen Kein Speed, I, II, III (Vanilla 0,1). */
    static final float[] SPEEDS = {0.1f, 0.15f, 0.2f, 0.3f};

    /** Einstellungen eines Zuschauers (GUI 5.13). */
    static final class Settings {
        int speed;
        boolean nightVision;
        boolean showOthers = true;
    }

    private final Game game;
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Settings> settings = new ConcurrentHashMap<>();

    public SpectatorService(Game game) {
        this.game = Objects.requireNonNull(game, "game");
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public boolean isSpectator(UUID player) {
        return spectators.contains(player);
    }

    public Set<UUID> spectators() {
        return Set.copyOf(spectators);
    }

    /**
     * Macht {@code player} zum Zuschauer.
     *
     * @param message {@code common.spectator} senden (nicht beim Rundenende)
     */
    public void makeSpectator(Player player, SpectatorReason reason, boolean message) {
        UUID uuid = player.getUniqueId();
        spectators.add(uuid);
        settings.putIfAbsent(uuid, new Settings());
        game.player(uuid).ifPresent(gamePlayer -> {
            if (gamePlayer.state() == GamePlayer.State.PLAYING) {
                gamePlayer.state(GamePlayer.State.SPECTATOR);
            }
        });
        player.closeInventory();
        player.getInventory().clear();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.setCanPickupItems(false);
        player.setFireTicks(0);
        player.setFallDistance(0);
        applySettings(player);
        updateVisibility();
        GameTexts texts = game.texts();
        Locale locale = texts.locale(player);
        game.services().find(ActionbarService.class).ifPresent(bar -> bar.set(player, ACTIONBAR,
                ActionbarService.Priority.PERSISTENT, texts.i18n().render(locale, "game.actionbar.spectator"), 0));
        hotbar(player);
        if (message) {
            texts.send(player, "common.spectator");
        }
        game.refreshTab(player);
        Bukkit.getPluginManager().callEvent(new SpectatorEvent(player, reason));
    }

    /** Hebt den Zuschauer-Zustand auf (z. B. BedWars-Respawn). */
    public void revive(Player player) {
        UUID uuid = player.getUniqueId();
        spectators.remove(uuid);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setCanPickupItems(true);
        player.setFlySpeed(SPEEDS[0]);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        game.services().find(HotbarService.class).ifPresent(hotbar -> hotbar.clear(player));
        game.services().find(ActionbarService.class).ifPresent(bar -> bar.clear(player, ACTIONBAR));
        updateVisibility();
        game.refreshTab(player);
    }

    /** Vergisst einen Spieler (Server verlassen). */
    public void forget(UUID player) {
        spectators.remove(player);
        settings.remove(player);
    }

    /** Lebende sehen keine Zuschauer; Zuschauer sehen Lebende und – je Einstellung – andere Zuschauer. */
    public void updateVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            boolean viewerSpectates = isSpectator(viewer);
            Settings viewerSettings = settings.get(viewer.getUniqueId());
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) {
                    continue;
                }
                boolean visible = !isSpectator(target)
                        || (viewerSpectates && (viewerSettings == null || viewerSettings.showOthers));
                if (visible) {
                    viewer.showPlayer(game.plugin(), target);
                } else {
                    viewer.hidePlayer(game.plugin(), target);
                }
            }
        }
    }

    private void hotbar(Player player) {
        GameTexts texts = game.texts();
        Locale locale = texts.locale(player);
        HotbarLayout layout = HotbarLayout.create()
                .slot(0, item(texts, locale, Material.COMPASS, "game.hotbar.teleporter"),
                        (clicker, right) -> openTeleporter(clicker))
                .slot(6, item(texts, locale, Material.NETHER_STAR, "game.hotbar.settings"),
                        (clicker, right) -> openSettings(clicker))
                .slot(7, item(texts, locale, Material.LIME_DYE, "game.hotbar.next"),
                        (clicker, right) -> nextRound(clicker))
                .slot(8, item(texts, locale, Material.WHITE_DYE, "game.hotbar.lobby"),
                        (clicker, right) -> game.services().get(PlayerTransfer.class)
                                .toLobby(List.of(clicker.getUniqueId()), "game.left",
                                        game.definition().modeId()));
        game.services().find(HotbarService.class).ifPresent(hotbar -> hotbar.apply(player, layout));
    }

    /** Hotbar-Item {@code {Name} §7(Rechtsklick)}. */
    public static org.bukkit.inventory.ItemStack item(GameTexts texts, Locale locale, Material material,
            String key) {
        Component name = Component.textOfChildren(texts.i18n().render(locale, key),
                texts.i18n().render(locale, "item.hint.right")).decoration(TextDecoration.ITALIC, false);
        return ItemBuilder.of(material).name(name).hideAll().build();
    }

    /** „Nächste Runde“: Ticket gleiche Variante; zweiter Klick → {@code common.spectator.next.already}. */
    public void nextRound(Player player) {
        QueueClient queue = game.services().get(QueueClient.class);
        game.mainThread().accept(queue.join(List.of(player.getUniqueId()), game.definition().modeId(),
                game.variant().key(), null, "SPECTATOR_NEXT"), "Nächste Runde", result -> {
                    if (result == QueueClient.JoinResult.ALREADY) {
                        game.texts().send(player, "common.spectator.next.already");
                    }
                });
    }

    // ------------------------------------------------------------------------------------------ GUI 5.12

    public void openTeleporter(Player player) {
        if (!isSpectator(player)) {
            game.texts().send(player, "game.spectator.not");
            return;
        }
        Locale locale = game.texts().locale(player);
        List<Player> alive = game.alivePlayers();
        int rows = Math.max(1, Math.min(6, (alive.size() + 8) / 9));
        Component title = game.texts().i18n().render(locale, "game.spectate.title");
        game.services().get(GuiService.class).open(player, new Gui(title, rows) {
            @Override
            protected void draw(GuiCanvas canvas) {
                GameTexts texts = game.texts();
                List<Player> current = game.alivePlayers();
                for (int i = 0; i < current.size() && i < canvas.grid().size(); i++) {
                    Player target = current.get(i);
                    canvas.button(i, ItemBuilder.of(Material.PLAYER_HEAD).skull(target.getUniqueId())
                            .texts(texts.i18n(), canvas.locale())
                            .nameKey("game.spectate.name", texts.name("name", target))
                            .loreKey("game.spectate.lore", Placeholder.component("hearts",
                                    HeartLine.component(target.getHealth()))).build(), click -> {
                                        click.player().closeInventory();
                                        if (target.isOnline()) {
                                            click.player().teleport(target);
                                            game.texts().send(click.player(), "common.spectator.tp",
                                                    texts.name("name", target),
                                                    Placeholder.unparsed("target", target.getName()),
                                                    Placeholder.unparsed("player", click.player().getName()));
                                        }
                                    });
                }
            }
        });
    }

    // ------------------------------------------------------------------------------------------ GUI 5.13

    public void openSettings(Player player) {
        Locale locale = game.texts().locale(player);
        Component title = game.texts().i18n().render(locale, "game.spectate.settings.title");
        game.services().get(GuiService.class).open(player, new Gui(title, 3) {
            @Override
            protected void draw(GuiCanvas canvas) {
                Settings own = settings.computeIfAbsent(player.getUniqueId(), id -> new Settings());
                GameTexts texts = game.texts();
                for (int level = 0; level < SPEEDS.length; level++) {
                    int chosen = level;
                    ItemBuilder boots = ItemBuilder.of(Material.LEATHER_BOOTS).texts(texts.i18n(), canvas.locale())
                            .nameKey("game.spectate.speed." + level).hideAll();
                    if (own.speed == level) {
                        boots.glow();
                    }
                    canvas.button(10 + level, boots.build(), click -> {
                        own.speed = chosen;
                        applySettings(player);
                        refresh();
                    });
                }
                canvas.button(15, ItemBuilder.of(Material.ENDER_EYE).texts(texts.i18n(), canvas.locale())
                        .nameKey("game.spectate.night", mark(texts, canvas.locale(), own.nightVision)).build(),
                        click -> {
                            own.nightVision = !own.nightVision;
                            applySettings(player);
                            refresh();
                        });
                canvas.button(16, ItemBuilder.of(Material.GLASS).texts(texts.i18n(), canvas.locale())
                        .nameKey("game.spectate.others", mark(texts, canvas.locale(), own.showOthers)).build(),
                        click -> {
                            own.showOthers = !own.showOthers;
                            updateVisibility();
                            refresh();
                        });
            }
        });
    }

    private static net.kyori.adventure.text.minimessage.tag.resolver.TagResolver mark(GameTexts texts,
            Locale locale, boolean on) {
        return Placeholder.component("mark", texts.i18n().render(locale, on ? "game.mark.on" : "game.mark.off"));
    }

    private void applySettings(Player player) {
        Settings own = settings.computeIfAbsent(player.getUniqueId(), id -> new Settings());
        player.setFlySpeed(SPEEDS[own.speed]);
        if (own.nightVision) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION,
                    0, false, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }
}
