package net.retromc.lobby;

import java.util.Locale;
import java.util.Objects;
import net.kyori.adventure.text.format.TextDecoration;
import net.retromc.paper.core.hotbar.HotbarLayout;
import net.retromc.paper.core.hotbar.HotbarService;
import net.retromc.paper.core.item.ItemBuilder;
import net.retromc.paper.core.social.gui.SocialGuiPlans;
import net.retromc.paper.core.social.gui.SocialGuiService;
import net.retromc.platform.common.service.ServiceRegistry;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Lobby-Hotbar Slot 9 (Spec 03 §4.9): eigener Kopf {@code §eProfil §7(Rechtsklick)} öffnet das Profil-Menü
 * (Spec 01 §4.5: Profil → Untermenüs Freunde, Party, Clan, Einstellungen). Die übrigen Lobby-Items folgen mit dem
 * Lobby-Plugin (Phase 2).
 */
final class ProfileItem implements Listener {

    /** Hotbar-Slot 9 (Index 8). */
    static final int SLOT = 8;

    private final Plugin plugin;
    private final ServiceRegistry services;

    ProfileItem(Plugin plugin, ServiceRegistry services) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "services");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // einen Tick später: nach dem Laden von Profil und Sprache durch paper-core
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                apply(player);
            }
        });
    }

    /** Setzt das Profil-Item in die Hotbar. */
    void apply(Player player) {
        services.find(HotbarService.class).ifPresent(hotbar -> hotbar.apply(player, layout(player)));
    }

    private HotbarLayout layout(Player player) {
        I18n i18n = services.get(I18n.class);
        Locale locale = i18n.locale(player.getUniqueId());
        ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD).skull(player.getUniqueId())
                .name(i18n.render(locale, "item.profile").decoration(TextDecoration.ITALIC, false)).hideAll()
                .build();
        return HotbarLayout.create().slot(SLOT, head, (clicker, right) -> services.find(SocialGuiService.class)
                .ifPresent(guis -> guis.open(clicker, SocialGuiPlans.PROFILE, SocialGuiPlans.BLANK)));
    }
}
