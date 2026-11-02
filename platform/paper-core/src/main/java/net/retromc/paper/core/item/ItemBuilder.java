package net.retromc.paper.core.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.retromc.core.api.BuildInfo;
import net.retromc.platform.common.text.I18n;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Fluent-Builder für Items (Spec 02 §5.5): {@code of(Material)}, {@code name}, {@code lore}, {@code loreKey},
 * {@code amount}, {@code glow}, {@code skull}, {@code color}, {@code hideAll}, {@code flag}, {@code build}. Name und
 * Lore sind standardmäßig nicht kursiv, Lore wird nach {@link LoreWrapper} umgebrochen.
 *
 * <p>Texte über Schlüssel ({@link #nameKey}, {@link #loreKey}) brauchen vorher {@link #texts(I18n, Locale)}; in GUIs
 * liefert {@code GuiCanvas.item(Material)} einen so vorbereiteten Builder. {@link #build()} nur auf dem Haupt-Thread.
 */
public final class ItemBuilder {

    private final Material material;
    private final ItemSpec.Builder spec = ItemSpec.builder();
    private @Nullable I18n i18n;
    private Locale locale = Locale.ROOT;
    private @Nullable UUID skullOwner;
    private @Nullable String skullTextures;
    private @Nullable DyeColor color;

    private ItemBuilder(Material material) {
        this.material = Objects.requireNonNull(material, "material");
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    /** Textquelle und Sprache für {@link #nameKey} und {@link #loreKey}. */
    public ItemBuilder texts(I18n i18n, Locale locale) {
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        this.locale = Objects.requireNonNull(locale, "locale");
        return this;
    }

    public ItemBuilder name(Component name) {
        spec.name(name);
        return this;
    }

    /** Name aus dem Textkatalog. */
    public ItemBuilder nameKey(String key, TagResolver... args) {
        spec.name(texts().render(locale, key, args));
        return this;
    }

    /** Ersetzt die Lore. */
    public ItemBuilder lore(List<? extends Component> lines) {
        spec.lore(lines);
        return this;
    }

    /** Ersetzt die Lore. */
    public ItemBuilder lore(Component... lines) {
        return lore(Arrays.asList(lines));
    }

    /** Hängt die Zeilen eines Katalogtexts an ({@code <newline>} trennt Zeilen). */
    public ItemBuilder loreKey(String key, TagResolver... args) {
        spec.addLore(texts().renderLines(locale, key, args));
        return this;
    }

    public ItemBuilder amount(int amount) {
        spec.amount(amount);
        return this;
    }

    /** Verzauberungsglanz ohne Verzauberung. */
    public ItemBuilder glow() {
        spec.glow();
        return this;
    }

    /** Spielerkopf eines Spielers (nur {@code PLAYER_HEAD}). */
    public ItemBuilder skull(UUID owner) {
        this.skullOwner = Objects.requireNonNull(owner, "owner");
        return this;
    }

    /** Spielerkopf mit Textur ({@code textures}-Wert in Base64, nur {@code PLAYER_HEAD}). */
    public ItemBuilder skull(String textures) {
        if (textures.isBlank()) {
            throw new IllegalArgumentException("Textur darf nicht leer sein");
        }
        this.skullTextures = textures;
        return this;
    }

    /** Farbe: Farbvariante des Materials (Glas, Wolle …), sonst Leder- oder Trankfarbe. */
    public ItemBuilder color(DyeColor color) {
        this.color = Objects.requireNonNull(color, "color");
        return this;
    }

    /** Blendet Attribute, Verzauberungen, Trims und Tooltip-Zusätze aus. */
    public ItemBuilder hideAll() {
        spec.hideAll();
        return this;
    }

    /** Blendet den ganzen Tooltip aus (Rahmen-Glas, Spec 02 §5.1). */
    public ItemBuilder hideTooltip() {
        spec.hideTooltip();
        return this;
    }

    /** PDC-Markierung {@code <brandId>:<key>}, z. B. {@code flag("action", "party:invite")}. */
    public ItemBuilder flag(String key, String value) {
        spec.flag(key, value);
        return this;
    }

    /** Baut das Item. */
    public ItemStack build() {
        ItemSpec content = spec.build();
        Material recolored = recolored();
        ItemStack stack = ItemStack.of(recolored != null ? recolored : material, content.amount());
        boolean tint = color != null && recolored == null;
        stack.editMeta(meta -> apply(meta, content, tint));
        return stack;
    }

    /** Farbvariante des Materials zu {@link #color}, falls es eine gibt. */
    private @Nullable Material recolored() {
        DyeColor dye = color;
        if (dye == null) {
            return null;
        }
        return DyedMaterials.recolor(material.name(), dye.name()).map(Material::matchMaterial).orElse(null);
    }

    private void apply(ItemMeta meta, ItemSpec content, boolean tint) {
        Component name = content.name();
        if (name != null) {
            meta.customName(name);
        }
        if (!content.lore().isEmpty()) {
            meta.lore(content.lore());
        }
        if (content.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (content.hideAll()) {
            meta.addItemFlags(ItemFlag.values());
        }
        if (content.hideTooltip()) {
            meta.setHideTooltip(true);
        }
        for (Map.Entry<String, String> flag : content.flags().entrySet()) {
            meta.getPersistentDataContainer().set(new NamespacedKey(BuildInfo.BRAND_ID, flag.getKey()),
                    PersistentDataType.STRING, flag.getValue());
        }
        applySkull(meta);
        if (tint) {
            applyColor(meta);
        }
    }

    private void applySkull(ItemMeta meta) {
        UUID owner = skullOwner;
        String textures = skullTextures;
        if (owner == null && textures == null) {
            return;
        }
        if (!(meta instanceof SkullMeta skull)) {
            throw new IllegalStateException("skull(...) nur für Spielerköpfe, nicht für " + material);
        }
        // Ohne Besitzer eine feste UUID aus der Textur, damit Clients den Kopf zwischenspeichern können.
        UUID id = owner != null ? owner : UUID.nameUUIDFromBytes(textures.getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createProfile(id);
        if (textures != null) {
            profile.setProperty(new ProfileProperty("textures", textures));
        }
        skull.setPlayerProfile(profile);
    }

    private void applyColor(ItemMeta meta) {
        DyeColor dye = Objects.requireNonNull(color, "color");
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(dye.getColor());
        } else if (meta instanceof PotionMeta potion) {
            potion.setColor(dye.getColor());
        } else {
            throw new IllegalStateException("Material " + material + " ist nicht färbbar");
        }
    }

    private I18n texts() {
        I18n texts = i18n;
        if (texts == null) {
            throw new IllegalStateException("texts(I18n, Locale) vor nameKey/loreKey aufrufen");
        }
        return texts;
    }
}
