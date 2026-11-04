package net.retromc.skywars.kit;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.core.api.games.KitStore;
import net.retromc.core.api.games.SkyWarsStore;
import net.retromc.core.api.games.SkyWarsStore.KitSettings;
import net.retromc.game.framework.Game;
import net.retromc.game.framework.GameTexts;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Kit-Besitz, -Auswahl und -Einstellungen der Spieler dieses Servers (Spec 04 §5.9, §5.10, §6.2.5): beim Betreten
 * der Wartelobby aus {@link KitStore}/{@link SkyWarsStore} geladen; Kauf über {@link KitStore#buy} (einmal je
 * Spieler gleichzeitig, idempotent), Auswahl gespeichert. Ohne Wahl gilt die zuletzt gespeicherte, sonst Starter.
 */
public final class KitService {

    /** Geladener Stand eines Spielers. */
    public record Profile(Set<String> owned, @Nullable String selected, KitSettings settings) {

        public Profile {
            owned = Set.copyOf(owned);
            Objects.requireNonNull(settings, "settings");
        }

        static final Profile EMPTY = new Profile(Set.of(), null, KitSettings.DEFAULT);
    }

    private final Game game;
    private final KitCatalog catalog;
    private final boolean classic;
    private final String mode;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> buying = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();
    private final Runnable changed;

    /**
     * @param changed läuft auf dem Main-Thread nach Laden/Ändern (Board, offene Menüs)
     */
    public KitService(Game game, KitCatalog catalog, boolean classic, Runnable changed) {
        this.game = Objects.requireNonNull(game, "game");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.classic = classic;
        this.mode = game.definition().modeId();
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    public KitCatalog catalog() {
        return catalog;
    }

    public Profile profile(UUID player) {
        return profiles.getOrDefault(player, Profile.EMPTY);
    }

    /** Lädt Besitz, Auswahl und Einstellungen (Wartelobby-Join). */
    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        CompletableFuture<Set<String>> owned = game.services().find(KitStore.class)
                .map(store -> store.owned(uuid, mode)).orElseGet(() -> CompletableFuture.completedFuture(Set.of()));
        CompletableFuture<Optional<String>> selected = game.services().find(KitStore.class)
                .map(store -> store.selected(uuid, mode))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        CompletableFuture<KitSettings> settings = game.services().find(SkyWarsStore.class)
                .map(store -> store.kitSettings(uuid))
                .orElseGet(() -> CompletableFuture.completedFuture(KitSettings.DEFAULT));
        CompletableFuture<Profile> profile = owned.thenCombine(selected, (kits, choice) -> new Profile(kits,
                choice.orElse(null), KitSettings.DEFAULT)).thenCombine(settings, (base, values) -> new Profile(
                        base.owned(), base.selected(), values));
        game.mainThread().accept(profile.exceptionally(error -> {
            game.log().warn("Kits von {} nicht geladen: {}", uuid, error.toString());
            return Profile.EMPTY;
        }), "Kits laden", loaded -> {
            profiles.put(uuid, loaded);
            changed.run();
        });
    }

    public void forget(UUID player) {
        profiles.remove(player);
        buying.remove(player);
    }

    /** Besitzt (oder kostenlos). */
    public boolean owns(UUID player, KitDef kit) {
        return kit.free() || profile(player).owned().contains(kit.id());
    }

    /** Im Kit-Menü sichtbare Kits (Preset, frühere Kits, ausgeblendete Kits). */
    public List<KitDef> menu(UUID player) {
        KitSettings settings = profile(player).settings();
        return catalog.available(classic, settings.legacyEnabled()).stream()
                .filter(kit -> !settings.hiddenKits().contains(kit.id())).toList();
    }

    /** Alle wählbaren Kits ohne Ausblendung (Kit-Einstellungen „Kits ausblenden“). */
    public List<KitDef> selectable(UUID player) {
        return catalog.available(classic, profile(player).settings().legacyEnabled());
    }

    /** Gewählte Kit-ID ({@link KitStore#RANDOM} möglich) oder {@code null}. */
    public @Nullable String selectedId(UUID player) {
        return profile(player).selected();
    }

    /**
     * Kit für den Rundenstart (Kit-Regeln 5–6): gewähltes, wenn besessen und wählbar; „Zufällig“ = zufälliges
     * besessenes; sonst Starter.
     */
    public KitDef resolve(UUID player) {
        String selected = profile(player).selected();
        List<KitDef> selectable = selectable(player);
        if (KitStore.RANDOM.equals(selected)) {
            List<KitDef> owned = selectable.stream().filter(kit -> owns(player, kit)).toList();
            return owned.isEmpty() ? catalog.starter() : owned.get(random.nextInt(owned.size()));
        }
        if (selected != null) {
            Optional<KitDef> kit = selectable.stream().filter(candidate -> candidate.id().equals(selected))
                    .findFirst();
            if (kit.isPresent() && owns(player, kit.get())) {
                return kit.get();
            }
        }
        return catalog.starter();
    }

    /** Kit für Anzeigen in der Wartelobby (wie {@link #resolve}, „Zufällig“ = Starter bis zum Start). */
    public KitDef resolveForDisplay(UUID player) {
        String selected = profile(player).selected();
        return KitStore.RANDOM.equals(selected) ? catalog.starter() : resolve(player);
    }

    /** Wählt ein besessenes Kit oder „Zufällig“ (nur in der Wartelobby). */
    public void select(Player player, String kitId) {
        GameTexts texts = game.texts();
        if (!game.lifecycle().waiting()) {
            texts.send(player, "sw.kit.lobbyonly");
            return;
        }
        UUID uuid = player.getUniqueId();
        String id;
        if (KitStore.RANDOM.equalsIgnoreCase(kitId) || "zufall".equalsIgnoreCase(kitId)) {
            id = KitStore.RANDOM;
        } else {
            Optional<KitDef> kit = catalog.byId(kitId).filter(found -> selectable(uuid).contains(found));
            if (kit.isEmpty()) {
                texts.send(player, "sw.kit.unknown");
                return;
            }
            if (!owns(uuid, kit.get())) {
                texts.send(player, "sw.kit.notowned");
                return;
            }
            id = kit.get().id();
        }
        Profile current = profile(uuid);
        profiles.put(uuid, new Profile(current.owned(), id, current.settings()));
        game.services().find(KitStore.class).ifPresent(store -> store.select(uuid, mode, id).exceptionally(error -> {
            game.log().warn("Kit-Auswahl von {} nicht gespeichert: {}", uuid, error.toString());
            return null;
        }));
        texts.send(player, "sw.kit.selected", Placeholder.component("kit", texts.i18n().render(texts.locale(player),
                id.equals(KitStore.RANDOM) ? "sw.kit.random" : "sw.kit." + id)));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        changed.run();
    }

    /** Kauft ein Kit (nach der Bestätigung); ein laufender Kauf blockiert weitere Klicks. */
    public void buy(Player player, KitDef kit) {
        UUID uuid = player.getUniqueId();
        GameTexts texts = game.texts();
        if (owns(uuid, kit)) {
            select(player, kit.id());
            return;
        }
        KitStore store = game.services().find(KitStore.class).orElse(null);
        if (store == null || !buying.add(uuid)) {
            return;
        }
        game.mainThread().accept(store.buy(uuid, mode, kit.id(), kit.price()).whenComplete((done, error) ->
                buying.remove(uuid)).exceptionally(error -> {
                    game.log().warn("Kit-Kauf {} von {} fehlgeschlagen: {}", kit.id(), uuid, error.toString());
                    return null;
                }), "Kit-Kauf", purchase -> {
                    if (purchase == null || !player.isOnline()) {
                        return;
                    }
                    switch (purchase.status()) {
                        case INSUFFICIENT -> texts.send(player, "sw.kit.nocoins", texts.number(texts.locale(player),
                                "n", purchase.missing()));
                        case BOUGHT, OWNED -> {
                            Profile current = profile(uuid);
                            Set<String> owned = new HashSet<>(current.owned());
                            owned.add(kit.id());
                            profiles.put(uuid, new Profile(owned, current.selected(), current.settings()));
                            if (purchase.status() == KitStore.Status.BOUGHT) {
                                texts.send(player, "sw.kit.bought", Placeholder.component("kit", texts.i18n()
                                        .render(texts.locale(player), kit.nameKey())),
                                        texts.number(texts.locale(player), "price", kit.price()));
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                            }
                            select(player, kit.id());
                        }
                        default -> throw new IllegalStateException();
                    }
                });
    }

    /** Ändert die Kit-Einstellungen und speichert sie. */
    public void settings(Player player, KitSettings settings) {
        UUID uuid = player.getUniqueId();
        Profile current = profile(uuid);
        profiles.put(uuid, new Profile(current.owned(), current.selected(), settings));
        game.services().find(SkyWarsStore.class).ifPresent(store -> store.saveKitSettings(uuid, settings)
                .exceptionally(error -> {
                    game.log().warn("Kit-Einstellungen von {} nicht gespeichert: {}", uuid, error.toString());
                    return null;
                }));
        changed.run();
    }
}
