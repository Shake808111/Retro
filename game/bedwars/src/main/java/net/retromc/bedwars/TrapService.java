package net.retromc.bedwars;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.event.TrapTriggerEvent;
import net.retromc.bedwars.trap.TrapType;
import net.retromc.bedwars.upgrade.TeamUpgrades;
import net.retromc.bedwars.upgrade.UpgradeType;
import net.retromc.paper.core.title.TitleService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Fallen und Basis-Effekte (Spec 04 §6.1.7): Eindringen eines Gegners in den Radius um das Bett löst die erste
 * Falle der Warteschlange aus (danach 30 s Sperre); „Basis-Effekte“ geben Mitgliedern im Basis-Radius
 * Regeneration I (L1) bzw. zusätzlich Eile I (L2).
 */
final class TrapService {

    /** Dauer der Basis-Effekte je Auffrischung (Ticks). */
    static final int BASE_EFFECT_TICKS = 60;

    private TrapService() {
    }

    static void check(BedWarsMode mode) {
        Instant now = Instant.now();
        for (BwTeam team : mode.bwTeams()) {
            if (!team.traps().armed(now)) {
                continue;
            }
            Location base = team.base();
            int radius = team.traps().nextRadius();
            Optional<Player> intruder = base.getNearbyPlayers(radius).stream()
                    .filter(player -> mode.playing(player) && !team.team().contains(player.getUniqueId())
                            && !SpecialItems.sneaking(player.getUniqueId()))
                    .findFirst();
            if (intruder.isEmpty()) {
                continue;
            }
            TrapType next = team.traps().traps().getFirst();
            TrapTriggerEvent event = new TrapTriggerEvent(team.team(), next, intruder.get());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                continue;
            }
            team.traps().trigger(now).ifPresent(trap -> trigger(mode, team, trap, intruder.get(), now));
        }
    }

    private static void trigger(BedWarsMode mode, BwTeam owner, TrapType trap, Player intruder, Instant now) {
        int ticks = trap.seconds() * 20;
        switch (trap) {
            case LIGHTNING -> {
                intruder.getWorld().strikeLightningEffect(intruder.getLocation());
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0));
            }
            case BLINDNESS -> {
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 0));
            }
            case WEAKNESS -> mode.teamOf(intruder.getUniqueId()).ifPresent(own -> {
                own.upgrades().weaken(intruder.getUniqueId(), now);
                mode.shop().applyUpgrades(intruder);
                Bukkit.getScheduler().runTaskLater(mode.plugin(), () -> {
                    if (intruder.isOnline()) {
                        mode.shop().applyUpgrades(intruder);
                    }
                }, TeamUpgrades.WEAKNESS.toSeconds() * 20 + 1);
            });
            case REVEAL -> {
                intruder.removePotionEffect(PotionEffectType.INVISIBILITY);
                intruder.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0));
            }
            case MINING_FATIGUE -> intruder.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks,
                    0));
            default -> throw new IllegalStateException(trap.name());
        }
        for (Player member : mode.online(owner)) {
            Locale locale = mode.texts().locale(member);
            Component name = mode.texts().i18n().render(locale, trap.nameKey());
            mode.texts().send(member, "bw.trap.triggered", Placeholder.component("trap", name),
                    mode.texts().name("name", intruder));
            mode.titles(member, "bw.title.trap", "bw.title.trap.sub", TitleService.ticks(10, 40, 10),
                    Placeholder.component("trap", name));
        }
    }

    static void baseEffects(BedWarsMode mode) {
        for (BwTeam team : mode.bwTeams()) {
            int level = team.upgrades().level(UpgradeType.BASE);
            if (level == 0) {
                continue;
            }
            Location base = team.base();
            int radius = mode.config().baseRadius();
            for (Player member : mode.online(team)) {
                if (!mode.playing(member) || member.getWorld() != base.getWorld()
                        || member.getLocation().distanceSquared(base) > (double) radius * radius) {
                    continue;
                }
                member.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, BASE_EFFECT_TICKS, 0, true,
                        false));
                if (level >= 2) {
                    member.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, BASE_EFFECT_TICKS, 0, true,
                            false));
                }
            }
        }
    }
}
