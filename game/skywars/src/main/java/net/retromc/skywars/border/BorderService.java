package net.retromc.skywars.border;

import java.util.Objects;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Border der Runde (Spec 04 §6.2.1 Nr. 7, §10 #22): Vanilla-Weltgrenze um das Zentrum der Map; beim Start schrumpft
 * sie von {@code border.radius} in {@code shrink} Sekunden auf {@code endRadius} Blöcke; außerhalb 1 Schaden je Sekunde
 * (eigener Takt, die Vanilla-Grenze macht keinen Schaden).
 */
public final class BorderService {

    private final int shrinkSeconds;
    private final double endRadius;
    private final double damage;
    private @Nullable WorldBorder border;
    private boolean started;

    public BorderService(int shrinkSeconds, double endRadius, double damage) {
        this.shrinkSeconds = shrinkSeconds;
        this.endRadius = endRadius;
        this.damage = damage;
    }

    /** Setzt die Grenze beim Rundenstart (noch ohne Schrumpfen). */
    public void prepare(World world, double centerX, double centerZ, double radius) {
        WorldBorder target = Objects.requireNonNull(world, "world").getWorldBorder();
        target.setCenter(centerX, centerZ);
        target.setSize(Math.max(2 * endRadius, 2 * radius));
        target.setDamageAmount(0);
        target.setDamageBuffer(0);
        target.setWarningDistance(5);
        border = target;
        started = false;
    }

    public boolean started() {
        return started;
    }

    /** Startet das Schrumpfen. */
    public void start() {
        WorldBorder target = border;
        if (target == null || started) {
            return;
        }
        started = true;
        target.changeSize(2 * endRadius, shrinkSeconds * 20L);
    }

    /** 1 Hz: Schaden außerhalb der Grenze. */
    public void damage(Iterable<Player> alive) {
        WorldBorder target = border;
        if (target == null || !started) {
            return;
        }
        for (Player player : alive) {
            if (player.getWorld().equals(target.getWorld()) && !target.isInside(player.getLocation())) {
                player.damage(damage);
            }
        }
    }

    /** Setzt die Grenze zurück (Rundenende/Abschalten). */
    public void reset() {
        WorldBorder target = border;
        if (target != null) {
            target.setSize(target.getMaxSize());
        }
        border = null;
        started = false;
    }
}
