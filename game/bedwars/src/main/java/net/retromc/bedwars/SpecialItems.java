package net.retromc.bedwars;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.retromc.bedwars.shop.ShopItems;
import net.retromc.game.framework.GameState;
import net.retromc.game.framework.team.GameTeam;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Sonder-Items des Shops (Spec 04 §6.1.6 Spezial/Explosives, §6.1.8 Abklingzeiten): Rettungsplattform,
 * Rettungskapsel, Teleporter, Mobiler Shop, Portal, Freezer, Fallende Brücke, Sneaker, Bettbauer Plus, Brücken-Ei,
 * TNT (zündet beim Setzen), Super-TNT, C4 mit Fernzünder, Sprengweste, Feuerball und Windkanone. Zahlen aus
 * {@code config.yml → bedwars.items}.
 */
final class SpecialItems implements Listener {

    /** Sneaker-Schutz je Spieler (keine Fallen-Auslösung). */
    private static final Map<UUID, Instant> SNEAKERS = new ConcurrentHashMap<>();

    private final BedWarsMode mode;
    private final Map<UUID, List<Block>> portals = new ConcurrentHashMap<>();
    private final Map<UUID, List<Block>> c4 = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> teleports = new ConcurrentHashMap<>();

    SpecialItems(BedWarsMode mode) {
        this.mode = mode;
    }

    static boolean sneaking(UUID player) {
        Instant until = SNEAKERS.get(player);
        return until != null && Instant.now().isBefore(until);
    }

    // ------------------------------------------------------------------------------------------ Rechtsklick

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || mode.game().state() != GameState.INGAME
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Optional<String> id = ShopItems.id(item);
        if (id.isEmpty() || !mode.playing(player)) {
            return;
        }
        String special = mode.catalog().item(id.get()).map(found -> found.special()).orElse(null);
        if (special == null && !id.get().equals(DETONATOR)) {
            return;
        }
        String kind = special == null ? DETONATOR : special;
        boolean handled = switch (kind) {
            case "platform" -> cooled(player, "platform") && platform(player);
            case "teleporter" -> teleporter(player);
            case "mobile_shop" -> mobileShop(player);
            case "freezer" -> cooled(player, "freezer") && freezer(player);
            case "bridge" -> bridge(player);
            case "sneaker" -> sneaker(player);
            case "bed_builder" -> bedBuilder(player);
            case "fireball" -> fireball(player);
            case "wind_cannon" -> cooled(player, "wind_cannon") && windCannon(player);
            case DETONATOR -> detonate(player);
            default -> false;
        };
        if (handled || kind.equals("mobile_shop") || kind.equals("bed_builder") || kind.equals("sneaker")
                || kind.equals("freezer") || kind.equals("bridge")) {
            event.setCancelled(true);
        }
        if (handled && !kind.equals(DETONATOR) && !kind.equals("wind_cannon")) {
            consume(player);
        }
    }

    /** Prüft und startet die Abklingzeit ({@code bw.item.cooldown}). */
    private boolean cooled(Player player, String item) {
        Instant now = Instant.now();
        long left = mode.cooldowns().remaining(player.getUniqueId(), item, now);
        if (left > 0) {
            mode.texts().send(player, mode.texts().count("bw.item.cooldown", left), Placeholder.unparsed("n",
                    Long.toString(left)));
            return false;
        }
        mode.cooldowns().start(player.getUniqueId(), item, mode.config().cooldown(item), now);
        return true;
    }

    private static void consume(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private Material wool(Player player) {
        return ShopItems.material(mode.shop().dye(player) + "_WOOL");
    }

    /** Setzt einen Block, wenn Bauregeln es erlauben, und merkt ihn als abbaubar. */
    private boolean build(Block block, Material material) {
        if (!block.getType().isAir() || block.getY() > mode.map().buildMaxY()
                || !mode.game().maps().inBounds(block.getLocation().add(0.5, 0, 0.5))) {
            return false;
        }
        block.setType(material);
        mode.markPlaced(block, true);
        return true;
    }

    private void removeLater(List<Block> blocks, double seconds) {
        Bukkit.getScheduler().runTaskLater(mode.plugin(), () -> blocks.forEach(block -> {
            if (mode.placed(block)) {
                block.setType(Material.AIR);
                mode.markPlaced(block, false);
            }
        }), Math.round(seconds * 20));
    }

    /** Rettungsplattform: 5×5 ohne Ecken aus Teamglas unter dem Spieler, verschwindet nach einigen Sekunden. */
    private boolean platform(Player player) {
        Location base = player.getLocation().clone().subtract(0, 2, 0);
        Material glass = ShopItems.material(mode.shop().dye(player) + "_STAINED_GLASS");
        List<Block> blocks = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                Block block = base.clone().add(dx, 0, dz).getBlock();
                if (build(block, glass)) {
                    blocks.add(block);
                }
            }
        }
        player.setFallDistance(0);
        removeLater(blocks, mode.config().item("platform-seconds", 10));
        return true;
    }

    /** Teleporter: nach 6 s zurück zum Team-Spawn. */
    private boolean teleporter(Player player) {
        UUID uuid = player.getUniqueId();
        if (teleports.containsKey(uuid)) {
            return false;
        }
        long ticks = Math.round(mode.config().item("teleporter-seconds", 6) * 20);
        teleports.put(uuid, Bukkit.getScheduler().runTaskLater(mode.plugin(), () -> {
            teleports.remove(uuid);
            if (mode.playing(player)) {
                mode.teamOf(uuid).ifPresent(team -> player.teleport(team.spawn()));
            }
        }, ticks));
        return true;
    }

    /** Mobiler Shop: Item-Händler an der Position für 60 s. */
    private boolean mobileShop(Player player) {
        List<Entity> npc = NpcSpawner.spawnNpc(mode, player.getLocation(), NpcSpawner.SHOP);
        Bukkit.getScheduler().runTaskLater(mode.plugin(), () -> NpcSpawner.remove(npc),
                Math.round(mode.config().item("mobile-shop-seconds", 60) * 20));
        return true;
    }

    /** Freezer: Gegner im Umkreis 3 s eingefroren. */
    private boolean freezer(Player player) {
        double radius = mode.config().item("freezer-radius", 3);
        int ticks = (int) Math.round(mode.config().item("freezer-seconds", 3) * 20);
        GameTeam own = mode.game().teams().teamOf(player.getUniqueId());
        for (Player other : player.getLocation().getNearbyPlayers(radius)) {
            if (other.equals(player) || !mode.playing(other) || (own != null && own.contains(other.getUniqueId()))) {
                continue;
            }
            other.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS,
                    ticks, 254, false, false));
            other.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST,
                    ticks, 128, false, false));
            other.setVelocity(new Vector());
        }
        return true;
    }

    /** Fallende Brücke: 12 Blöcke lange Treppe abwärts in Blickrichtung. */
    private boolean bridge(Player player) {
        BlockFace face = player.getFacing();
        Block start = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        Material material = wool(player);
        int length = (int) mode.config().item("bridge-length", 12);
        boolean any = false;
        for (int i = 1; i <= length; i++) {
            Block block = start.getRelative(face, i).getRelative(BlockFace.DOWN, i - 1);
            any |= build(block, material);
        }
        return any;
    }

    private boolean sneaker(Player player) {
        long seconds = Math.round(mode.config().item("sneaker-seconds", 30));
        SNEAKERS.put(player.getUniqueId(), Instant.now().plusSeconds(seconds));
        return true;
    }

    /** Bettbauer Plus: Teamwolle um und auf das eigene Bett. */
    private boolean bedBuilder(Player player) {
        BwTeam team = mode.teamOf(player.getUniqueId()).orElse(null);
        if (team == null || !team.bedAlive() || team.bedHead() == null) {
            return false;
        }
        Material material = wool(player);
        boolean any = false;
        for (Location part : new Location[] {team.bedHead(), team.bedFoot()}) {
            if (part == null) {
                continue;
            }
            Block bed = part.getBlock();
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                    BlockFace.UP}) {
                any |= build(bed.getRelative(face), material);
            }
        }
        return any;
    }

    private boolean fireball(Player player) {
        Fireball fireball = player.launchProjectile(Fireball.class, player.getEyeLocation().getDirection());
        fireball.setYield(2f);
        fireball.setIsIncendiary(false);
        return true;
    }

    /** Windkanone: Windkugel auf den nächsten Gegner bis 10 Blöcke (braucht Windkugeln). */
    private boolean windCannon(Player player) {
        ItemStack charge = null;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == Material.WIND_CHARGE) {
                charge = stack;
                break;
            }
        }
        if (charge == null) {
            mode.texts().send(player, "bw.item.nocharge");
            return false;
        }
        charge.setAmount(charge.getAmount() - 1);
        double range = mode.config().item("wind-cannon-range", 10);
        GameTeam own = mode.game().teams().teamOf(player.getUniqueId());
        Player target = null;
        double best = range * range;
        for (Player other : player.getLocation().getNearbyPlayers(range)) {
            if (other.equals(player) || !mode.playing(other) || (own != null && own.contains(other.getUniqueId()))
                    || !player.hasLineOfSight(other)) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(player.getLocation());
            if (distance < best) {
                best = distance;
                target = other;
            }
        }
        Vector direction = target == null ? player.getEyeLocation().getDirection()
                : target.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize();
        player.launchProjectile(WindCharge.class, direction);
        return true;
    }

    // ------------------------------------------------------------------------------------------ Setzen

    /** Portal, TNT, Super-TNT und C4 werden beim Setzen umgesetzt. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (mode.game().state() != GameState.INGAME) {
            return;
        }
        Optional<String> special = ShopItems.id(event.getItemInHand()).flatMap(mode.catalog()::item)
                .map(item -> item.special());
        if (special.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        switch (special.get()) {
            case "tnt", "super_tnt" -> {
                block.setType(Material.AIR);
                mode.markPlaced(block, false);
                block.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5), TNTPrimed.class, tnt -> {
                    tnt.setFuseTicks(60);
                    tnt.setSource(player);
                    if (special.get().equals("super_tnt")) {
                        tnt.setYield((float) mode.config().item("super-tnt-power", 6));
                    }
                });
            }
            case "c4" -> {
                c4.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>()).add(block);
                ItemStack detonator = ItemStack.of(Material.LEVER);
                detonator.editMeta(meta -> {
                    meta.customName(mode.texts().i18n().render(mode.texts().locale(player), "bw.item.detonator")
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                    meta.getPersistentDataContainer().set(ShopItems.ITEM,
                            org.bukkit.persistence.PersistentDataType.STRING, DETONATOR);
                });
                if (!player.getInventory().containsAtLeast(detonator, 1)) {
                    player.getInventory().addItem(detonator);
                }
            }
            case "portal" -> portals.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>()).add(block);
            case "mobile_shop", "bed_builder", "sneaker", "freezer", "bridge" -> event.setCancelled(true);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        portals.values().forEach(list -> list.remove(event.getBlock()));
        c4.values().forEach(list -> list.remove(event.getBlock()));
    }

    /** Fernzünder: alle eigenen C4 explodieren. */
    private boolean detonate(Player player) {
        List<Block> blocks = c4.remove(player.getUniqueId());
        player.getInventory().removeItem(player.getInventory().getItemInMainHand());
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        for (Block block : blocks) {
            if (block.getType() == Material.TNT) {
                block.setType(Material.AIR);
                mode.markPlaced(block, false);
                block.getWorld().createExplosion(block.getLocation().add(0.5, 0.5, 0.5), 4f, false, true, player);
            }
        }
        return true;
    }

    /** Portal: wer auf einen Rahmen tritt, kommt am anderen heraus (Abklingzeit 10 s). */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || mode.game().state() != GameState.INGAME) {
            return;
        }
        Player player = event.getPlayer();
        Block below = event.getTo().getBlock().getRelative(BlockFace.DOWN);
        if (below.getType() != Material.END_PORTAL_FRAME || !mode.playing(player)) {
            return;
        }
        for (List<Block> pair : portals.values()) {
            int index = pair.indexOf(below);
            int partner = index ^ 1;
            if (index < 0 || partner >= pair.size()) {
                continue;
            }
            Block other = pair.get(partner);
            Instant now = Instant.now();
            if (mode.cooldowns().remaining(player.getUniqueId(), "portal", now) > 0) {
                return;
            }
            mode.cooldowns().start(player.getUniqueId(), "portal", mode.config().cooldown("portal"), now);
            player.teleport(other.getLocation().add(0.5, 1, 0.5).setDirection(player.getLocation().getDirection()));
            return;
        }
    }

    // ------------------------------------------------------------------------------------------ Brücken-Ei

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Egg egg) || !(egg.getShooter() instanceof Player player)
                || mode.game().state() != GameState.INGAME) {
            return;
        }
        if (!"egg_bridge".equals(ShopItems.id(player.getInventory().getItemInMainHand()).flatMap(
                mode.catalog()::item).map(item -> item.special()).orElse(null))) {
            return;
        }
        Material material = wool(player);
        int length = (int) mode.config().item("egg-bridge-length", 20);
        Location start = player.getLocation();
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(mode.plugin(), () -> {
            if (!egg.isValid() || egg.getLocation().distance(start) > length) {
                task[0].cancel();
                return;
            }
            Block center = egg.getLocation().getBlock().getRelative(BlockFace.DOWN, 2);
            build(center, material);
        }, 2L, 1L);
    }

    @EventHandler
    public void onHatch(PlayerEggThrowEvent event) {
        if (mode.game().state() == GameState.INGAME) {
            event.setHatching(false);
        }
    }

    // ------------------------------------------------------------------------------------------ Tod

    /** Rettungskapsel: rettet vor tödlichem Treffer (2 Herzen, 5 Blöcke nach oben auf eine Plattform). */
    static boolean capsule(BedWarsMode mode, Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            boolean match = ShopItems.id(stack).flatMap(mode.catalog()::item)
                    .map(item -> "capsule".equals(item.special())).orElse(false);
            if (!match) {
                continue;
            }
            stack.setAmount(stack.getAmount() - 1);
            player.setHealth(4.0);
            player.setFallDistance(0);
            player.setFireTicks(0);
            Location up = player.getLocation().clone().add(0, 5, 0);
            Material glass = ShopItems.material(mode.shop().dye(player) + "_STAINED_GLASS");
            List<Block> blocks = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = up.clone().add(dx, -1, dz).getBlock();
                    if (block.getType().isAir()) {
                        block.setType(glass);
                        mode.markPlaced(block, true);
                        blocks.add(block);
                    }
                }
            }
            player.teleport(up);
            Bukkit.getScheduler().runTaskLater(mode.plugin(), () -> blocks.forEach(block -> {
                block.setType(Material.AIR);
                mode.markPlaced(block, false);
            }), Math.round(mode.config().item("platform-seconds", 10) * 20));
            return true;
        }
        return false;
    }

    /** Sprengweste: explodiert beim Tod des Trägers. */
    static void explodeVest(BedWarsMode mode, Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        boolean vest = ShopItems.id(chest).flatMap(mode.catalog()::item).map(item -> "vest".equals(item.special()))
                .orElse(false);
        if (vest) {
            player.getWorld().createExplosion(player.getLocation(), 3f, false, true, player);
        }
    }

    /** Markierung des Fernzünders. */
    static final String DETONATOR = "detonator";
}
