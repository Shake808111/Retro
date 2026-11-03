package net.retromc.paper.core.holo;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.retromc.core.api.util.Registration;
import net.retromc.paper.core.PaperCore;
import net.retromc.platform.common.feature.CoreFeature;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Standard-{@link HologramService}: eine {@link TextDisplay} je Hologramm (Zeilen durch Zeilenumbruch, zur Kamera
 * gedreht, ohne Hintergrund), nicht persistent. Persönliche Hologramme sind nur für ihren Betrachter sichtbar.
 */
public final class HologramFeature implements CoreFeature<PaperCore>, HologramService {

    private final Set<Display> displays = ConcurrentHashMap.newKeySet();
    private @Nullable PaperCore core;
    private @Nullable Registration registration;

    @Override
    public String name() {
        return "Hologramme";
    }

    @Override
    public void enable(PaperCore context) {
        core = context;
        registration = context.services().registerDefault(HologramService.class, this);
    }

    @Override
    public void disable(PaperCore context) {
        Registration current = registration;
        if (current != null) {
            current.close();
        }
        registration = null;
        displays.forEach(Display::remove);
        displays.clear();
        core = null;
    }

    private Plugin plugin() {
        PaperCore context = core;
        if (context == null) {
            throw new IllegalStateException("Hologramme nicht aktiv");
        }
        return context.plugin();
    }

    @Override
    public Hologram personal(Player viewer, Location location, List<Component> lines) {
        Plugin plugin = plugin();
        TextDisplay display = spawn(location, lines, false);
        viewer.showEntity(plugin, display);
        return new Shown(display, null);
    }

    @Override
    public Hologram global(Location location, Supplier<List<Component>> lines) {
        return new Shown(spawn(location, lines.get(), true), lines);
    }

    private TextDisplay spawn(Location location, List<Component> lines, boolean visible) {
        Objects.requireNonNull(location.getWorld(), "Welt des Hologramms");
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setVisibleByDefault(visible);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setShadowed(true);
            entity.text(join(lines));
        });
        displays.add(display);
        return display;
    }

    private static Component join(List<Component> lines) {
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** Angezeigtes Hologramm. */
    private final class Shown implements Hologram {

        private final TextDisplay display;
        private final @Nullable Supplier<List<Component>> source;

        Shown(TextDisplay display, @Nullable Supplier<List<Component>> source) {
            this.display = display;
            this.source = source;
        }

        @Override
        public void update(List<Component> lines) {
            Component text = join(lines);
            if (!text.equals(display.text())) {
                display.text(text);
            }
        }

        @Override
        public void refresh() {
            Supplier<List<Component>> lines = source;
            if (lines != null) {
                update(lines.get());
            }
        }

        @Override
        public void remove() {
            displays.remove(display);
            display.remove();
        }

        @Override
        public Location location() {
            return display.getLocation();
        }
    }
}
