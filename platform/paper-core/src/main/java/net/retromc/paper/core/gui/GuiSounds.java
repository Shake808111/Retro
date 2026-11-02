package net.retromc.paper.core.gui;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

/** Klänge der GUIs (Spec 02 §5.1, Annahme): Klick, Fehler, Erfolg. */
public final class GuiSounds {

    /** Klick: {@code ui.button.click}, Lautstärke 0,5, Tonhöhe 1,0. */
    public static final Sound CLICK = Sound.sound(Key.key("ui.button.click"), Sound.Source.UI, 0.5f, 1.0f);
    /** Fehler: {@code entity.villager.no}. */
    public static final Sound ERROR = Sound.sound(Key.key("entity.villager.no"), Sound.Source.UI, 1.0f, 1.0f);
    /** Erfolg: {@code entity.experience_orb.pickup}. */
    public static final Sound SUCCESS = Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.UI,
            1.0f, 1.0f);

    private GuiSounds() {
    }

    public static void click(Audience audience) {
        audience.playSound(CLICK);
    }

    public static void error(Audience audience) {
        audience.playSound(ERROR);
    }

    public static void success(Audience audience) {
        audience.playSound(SUCCESS);
    }
}
