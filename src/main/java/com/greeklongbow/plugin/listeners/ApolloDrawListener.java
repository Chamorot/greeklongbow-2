package com.greeklongbow.plugin.listeners;

import com.greeklongbow.plugin.GreekLongbowPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class ApolloDrawListener implements Listener {

    // PDC key stamped on the arrow entity so we can identify it in flight
    private final NamespacedKey APOLLO_ARROW_KEY;
    // PDC key on the player to persist cooldown across restarts
    private final NamespacedKey COOLDOWN_PDC_KEY;

    private static final long   COOLDOWN_MS           = 35_000L;
    private static final long   BOSSBAR_TICK_INTERVAL = 20L;
    // Apollo's Draw fixed damage: 3 hearts = 6 half-hearts
    private static final double APOLLO_DAMAGE         = 6.0;
    // How far ahead to ray-trace for a lock-on target on activation (blocks)
    private static final double LOCK_ON_RANGE         = 48.0;
    // How aggressively the arrow steers (0.0–1.0; higher = snappier turn)
    private static final double HOMING_STRENGTH       = 0.35;
    // Arrow speed multiplier applied each tick to maintain velocity
    private static final double ARROW_SPEED           = 3.2;
    // Homing tracking interval in ticks
    private static final long   HOMING_TICK           = 1L;
    // Max homing duration in ticks before the arrow gives up and flies straight
    private static final int    MAX_HOMING_TICKS      = 120; // 6 seconds

    private final GreekLongbowPlugin plugin;

    // Per-player state
    private final Map<UUID, Long>       cooldownExpiry  = new HashMap<>();
    private final Map<UUID, BossBar>    playerBossBars  = new HashMap<>();
    private final Map<UUID, BukkitTask> playerTasks     = new HashMap<>();
    // Players who activated ability, awaiting next shot — maps to the locked target
    private final Map<UUID, UUID>       pendingTarget   = new HashMap<>(); // playerUUID → targetUUID
    // Arrow homing tasks: arrowUUID → task
    private final Map<UUID, BukkitTask> arrowTasks      = new HashMap<>();

    public ApolloDrawListener(GreekLongbowPlugin plugin) {
        this.plugin           = plugin;
        this.APOLLO_ARROW_KEY = new NamespacedKey(plugin, "apollo_arrow");
        this.COOLDOWN_PDC_KEY = new NamespacedKey(plugin, "greeklongbow_cooldown");
    }

    // -------------------------------------------------------------------------
    // Offhand activation — lock onto the aimed target
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onOffhandActivate(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!plugin.getGreekLongbowItem().isGreekLongbow(mainHand)) return;

        var action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
         && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();
        long remaining = cooldownExpiry.getOrDefault(uuid, 0L) - now;

        if (remaining > 0) {
            long seconds = (remaining + 999) / 1000;
            player.sendActionBar(
                Component.text("\u2600 Apollo's Draw on cooldown \u2014 " + seconds + "s")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return;
        }

        // Toggle off if already pending
        if (pendingTarget.containsKey(uuid)) {
            pendingTarget.remove(uuid);
            player.sendActionBar(
                Component.text("\u2600 Apollo's Draw deactivated.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            );
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        // Ray-trace to find a lock-on target the player is looking at
        LivingEntity lockedTarget = findLookTarget(player);

        if (lockedTarget == null) {
            player.sendActionBar(
                Component.text("\u2600 No target in range. Look at an enemy to lock on.")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
            );
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return;
        }

        // Store the locked target UUID — the arrow will home to this entity
        pendingTarget.put(uuid, lockedTarget.getUniqueId());

        // Visual/audio feedback
        player.showTitle(Title.title(
            Component.text("\u2600 APOLLO'S DRAW")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true),
            Component.text("Target locked \u2014 loose your arrow!")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false),
            Title.Times.times(
                Duration.ofMillis(100),
                Duration.ofMillis(1100),
                Duration.ofMillis(400)
            )
        ));

        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.7f, 1.4f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f);

        // Lock-on particles around the target
        lockedTarget.getWorld().spawnParticle(Particle.DUST,
            lockedTarget.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 100, 255), 2.0f));
        lockedTarget.getWorld().spawnParticle(Particle.ENCHANT,
            lockedTarget.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 1.0);
        lockedTarget.getWorld().playSound(lockedTarget.getLocation(),
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.5f);

        // Action bar hint
        player.sendActionBar(
            Component.text("\u2600 Locked onto " + lockedTarget.getName() + ". Draw and loose!")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    // -------------------------------------------------------------------------
    // Intercept the bow shot — if pending, tag the arrow and start homing
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getGreekLongbowItem().isGreekLongbow(event.getBow())) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        UUID uuid = player.getUniqueId();
        UUID targetUuid = pendingTarget.remove(uuid);
        if (targetUuid == null) return; // Normal arrow

        // Resolve the target entity
        LivingEntity target = resolveTarget(targetUuid, player);
        if (target == null) {
            // Target logged off or died — fire a normal arrow, no cooldown consumed
            player.sendActionBar(
                Component.text("\u2600 Target lost. Arrow fires normally.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            );
            return;
        }

        // Tag the arrow
        arrow.getPersistentDataContainer().set(APOLLO_ARROW_KEY, PersistentDataType.BYTE, (byte) 1);

        // Start cooldown
        long newExpiry = System.currentTimeMillis() + COOLDOWN_MS;
        cooldownExpiry.put(uuid, newExpiry);
        player.getPersistentDataContainer().set(COOLDOWN_PDC_KEY, PersistentDataType.LONG, newExpiry);
        startBossBar(player);

        // Begin homing
        startHoming(arrow, target, player);

        player.sendActionBar(
            Component.text("\u2600 Apollo's arrow is loosed!")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.5f);
    }

    // -------------------------------------------------------------------------
    // Homing logic — runs every tick, aggressively steers toward the target
    // -------------------------------------------------------------------------

    private void startHoming(Arrow arrow, LivingEntity target, Player shooter) {
        UUID arrowUuid = arrow.getUniqueId();
        final int[] tickCount = {0};

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tickCount[0]++;

            // Arrow gone or hit something
            if (!arrow.isValid() || arrow.isOnGround()) {
                cancelArrowTask(arrowUuid);
                return;
            }

            // Max homing duration exceeded — fly straight from here
            if (tickCount[0] > MAX_HOMING_TICKS) {
                cancelArrowTask(arrowUuid);
                return;
            }

            // Target dead, removed, or in a different world
            if (!target.isValid() || target.isDead()) {
                cancelArrowTask(arrowUuid);
                return;
            }

            Location arrowLoc  = arrow.getLocation();
            Location targetLoc = target.getLocation().add(0, target.getHeight() * 0.6, 0);

            // Direction from arrow to target
            Vector toTarget = targetLoc.toVector().subtract(arrowLoc.toVector());
            double dist = toTarget.length();

            // If we're very close — force an immediate hit
            if (dist < 0.6) {
                cancelArrowTask(arrowUuid);
                arrow.remove();
                applyApolloHit(target, shooter, arrowLoc);
                return;
            }

            toTarget.normalize();

            // Blend current velocity with the target direction (LERP)
            Vector current = arrow.getVelocity().normalize();
            Vector newDir  = current.multiply(1.0 - HOMING_STRENGTH)
                                    .add(toTarget.multiply(HOMING_STRENGTH))
                                    .normalize()
                                    .multiply(ARROW_SPEED);

            arrow.setVelocity(newDir);

            // Purple/gold particle trail
            arrow.getWorld().spawnParticle(Particle.DUST, arrowLoc, 6, 0.05, 0.05, 0.05, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 100, 255), 1.8f));
            arrow.getWorld().spawnParticle(Particle.ENCHANT, arrowLoc, 3, 0.1, 0.1, 0.1, 0.3);

        }, 1L, HOMING_TICK);

        arrowTasks.put(arrowUuid, task);
    }

    private void applyApolloHit(LivingEntity target, Player shooter, Location hitLoc) {
        target.damage(APOLLO_DAMAGE, shooter);

        target.getWorld().spawnParticle(Particle.EXPLOSION, hitLoc, 1);
        target.getWorld().spawnParticle(Particle.DUST,
            hitLoc.clone().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 100, 255), 3.0f));
        target.getWorld().spawnParticle(Particle.ENCHANT,
            hitLoc.clone().add(0, 1, 0), 25, 0.4, 0.7, 0.4, 1.0);
        target.getWorld().playSound(hitLoc, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 0.8f);
        target.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 1.9f);

        shooter.sendActionBar(
            Component.text("\u2600 Apollo's arrow found its mark!")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false)
        );
    }

    // -------------------------------------------------------------------------
    // When the homing arrow hits a block — particle burst, remove task
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowLand(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.getPersistentDataContainer().has(APOLLO_ARROW_KEY, PersistentDataType.BYTE)) return;

        cancelArrowTask(arrow.getUniqueId());

        Location loc = arrow.getLocation();
        arrow.getWorld().spawnParticle(Particle.DUST, loc, 25, 0.3, 0.3, 0.3, 0,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 80, 220), 1.8f));
        arrow.getWorld().spawnParticle(Particle.ENCHANT, loc, 12, 0.2, 0.2, 0.2, 0.5);
        arrow.getWorld().playSound(loc, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.5f, 1.6f);
    }

    // -------------------------------------------------------------------------
    // Ray-trace helper — find the living entity the player is looking at
    // -------------------------------------------------------------------------

    private LivingEntity findLookTarget(Player player) {
        RayTraceResult result = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            LOCK_ON_RANGE,
            entity -> entity instanceof LivingEntity
                   && entity.getEntityId() != player.getEntityId()
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity le)) return null;
        return le;
    }

    private LivingEntity resolveTarget(UUID targetUuid, Player shooter) {
        for (org.bukkit.entity.Entity e : shooter.getWorld().getEntities()) {
            if (e.getUniqueId().equals(targetUuid) && e instanceof LivingEntity le) {
                return le;
            }
        }
        return null;
    }

    private void cancelArrowTask(UUID arrowUuid) {
        BukkitTask t = arrowTasks.remove(arrowUuid);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    // -------------------------------------------------------------------------
    // Boss bar — cooldown display
    // -------------------------------------------------------------------------

    private BossBar getOrCreateBossBar(UUID uuid) {
        return playerBossBars.computeIfAbsent(uuid, k -> BossBar.bossBar(
            Component.text("\u2600 Apollo's Draw Cooldown")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.BOLD, true),
            1.0f,
            BossBar.Color.PURPLE,
            BossBar.Overlay.NOTCHED_10
        ));
    }

    private void startBossBar(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask old = playerTasks.remove(uuid);
        if (old != null && !old.isCancelled()) old.cancel();

        BossBar bar = getOrCreateBossBar(uuid);
        player.showBossBar(bar);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now       = System.currentTimeMillis();
            long expiry    = cooldownExpiry.getOrDefault(uuid, 0L);
            long remaining = expiry - now;

            if (remaining <= 0) {
                player.hideBossBar(bar);
                BukkitTask self = playerTasks.remove(uuid);
                if (self != null) self.cancel();

                player.sendActionBar(
                    Component.text("\u2600 Apollo's Draw ready!")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                );
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                return;
            }

            long seconds   = (remaining + 999) / 1000;
            float progress = Math.min(1.0f, (float) remaining / COOLDOWN_MS);

            bar.name(
                Component.text("\u2600 Apollo's Draw \u2014 " + seconds + "s")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true)
            );
            bar.progress(progress);
            bar.color(progress > 0.4f ? BossBar.Color.PURPLE : BossBar.Color.RED);

        }, 1L, BOSSBAR_TICK_INTERVAL);

        playerTasks.put(uuid, task);
    }

    private void hideBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        cooldownExpiry.remove(uuid);
        BossBar bar = playerBossBars.remove(uuid);
        if (bar != null) player.hideBossBar(bar);
        BukkitTask task = playerTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    // -------------------------------------------------------------------------
    // Join / Quit
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        var storage = plugin.getOwnerStorage();
        if (!storage.isClaimed()) return;
        if (!uuid.equals(storage.getOwnerUuid())) return;

        long saved = player.getPersistentDataContainer()
            .getOrDefault(COOLDOWN_PDC_KEY, PersistentDataType.LONG, 0L);
        cooldownExpiry.put(uuid, saved);

        if (saved > System.currentTimeMillis()) {
            startBossBar(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingTarget.remove(event.getPlayer().getUniqueId());
        hideBossBar(event.getPlayer());
    }

    // -------------------------------------------------------------------------
    // Cleanup on plugin disable
    // -------------------------------------------------------------------------

    public void cleanupAll() {
        List<UUID> uuids = new ArrayList<>(playerBossBars.keySet());
        for (UUID uuid : uuids) {
            Player online = plugin.getServer().getPlayer(uuid);
            BossBar bar = playerBossBars.remove(uuid);
            if (bar != null && online != null) online.hideBossBar(bar);
            BukkitTask task = playerTasks.remove(uuid);
            if (task != null && !task.isCancelled()) task.cancel();
        }
        for (BukkitTask task : arrowTasks.values()) {
            if (!task.isCancelled()) task.cancel();
        }
        arrowTasks.clear();
        playerTasks.clear();
        pendingTarget.clear();
    }
}
