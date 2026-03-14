package me.priyme.lagscope.control;
import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.analysis.SecondCounter;


import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LagEnforcer implements Listener {
    private final LagController controller;
    private final Map<UUID, SecondCounter> arrowPerPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, SecondCounter> tridentPerPlayer = new ConcurrentHashMap<>();
    private final SecondCounter explosionsGlobal = new SecondCounter();

    private static final EnumSet<CreatureSpawnEvent.SpawnReason> NATURAL_REASONS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL,
            CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.PATROL,
            CreatureSpawnEvent.SpawnReason.RAID,
            CreatureSpawnEvent.SpawnReason.VILLAGE_INVASION,
            CreatureSpawnEvent.SpawnReason.DROWNED,
            CreatureSpawnEvent.SpawnReason.BEEHIVE,
            CreatureSpawnEvent.SpawnReason.BREEDING
    );

    public LagEnforcer(LagController controller) {
        this.controller = controller;
    }

    private static long nowSecond() {
        return System.currentTimeMillis() / 1000L;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (controller.getMode() == LagMode.OFF) return;
        LagController.ModeSettings s = controller.currentSettings();

        boolean isSpawner = event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER;
        boolean isNatural = NATURAL_REASONS.contains(event.getSpawnReason());

        if (isNatural && !s.limitNaturalSpawns) return;
        if (isSpawner && !s.limitSpawnerSpawns) return;

        Entity e = event.getEntity();
        EntityType type = e.getType();
        if (!type.isAlive() || !type.isSpawnable()) return;
        Chunk chunk = e.getLocation().getChunk();

        if (e instanceof Villager) {
            if (s.maxVillagersPerChunk > 0 && exceedsTypeInChunk(chunk, EntityType.VILLAGER, s.maxVillagersPerChunk)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(e instanceof Creature)) return;

        if (isMonster(type)) {
            if (s.maxMonstersPerChunk > 0 && exceedsLivingInChunk(chunk, true, s.maxMonstersPerChunk)) {
                event.setCancelled(true);
            }
        } else {
            if (s.maxAnimalsPerChunk > 0 && exceedsLivingInChunk(chunk, false, s.maxAnimalsPerChunk)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (controller.getMode() == LagMode.OFF) return;
        LagController.ModeSettings s = controller.currentSettings();
        Entity e = event.getEntity();
        Chunk chunk = e.getLocation().getChunk();

        if (e instanceof Item) {
            if (s.maxItemsPerChunk > 0 && exceedsClassInChunk(chunk, Item.class, s.maxItemsPerChunk)) {
                event.setCancelled(true);
            }
            return;
        }
        if (e instanceof ExperienceOrb) {
            if (s.maxXpOrbsPerChunk > 0 && exceedsClassInChunk(chunk, ExperienceOrb.class, s.maxXpOrbsPerChunk)) {
                event.setCancelled(true);
            }
            return;
        }
        if (e instanceof Projectile) {
            if (s.maxProjectilesPerChunk > 0 && exceedsClassInChunk(chunk, Projectile.class, s.maxProjectilesPerChunk)) {
                event.setCancelled(true);
            }
            return;
        }
        if (e instanceof TNTPrimed) {
            if (s.maxPrimedTntPerChunk > 0 && exceedsClassInChunk(chunk, TNTPrimed.class, s.maxPrimedTntPerChunk)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (controller.getMode() == LagMode.OFF) return;
        LagController.ModeSettings s = controller.currentSettings();
        Projectile p = event.getEntity();

        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        long sec = nowSecond();
        if (p instanceof Arrow) {
            if (s.maxArrowsPerPlayerPerSecond > 0) {
                SecondCounter c = arrowPerPlayer.computeIfAbsent(player.getUniqueId(), k -> new SecondCounter());
                if (!c.tryIncrement(sec, s.maxArrowsPerPlayerPerSecond)) {
                    event.setCancelled(true);
                }
            }
            return;
        }
        if (p instanceof Trident) {
            if (s.maxTridentsPerPlayerPerSecond > 0) {
                SecondCounter c = tridentPerPlayer.computeIfAbsent(player.getUniqueId(), k -> new SecondCounter());
                if (!c.tryIncrement(sec, s.maxTridentsPerPlayerPerSecond)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (controller.getMode() == LagMode.OFF) return;
        LagController.ModeSettings s = controller.currentSettings();
        if (s.maxExplosionsPerSecondGlobal <= 0) return;
        if (!explosionsGlobal.tryIncrement(nowSecond(), s.maxExplosionsPerSecondGlobal)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (controller.getMode() == LagMode.OFF) return;
        LagController.ModeSettings s = controller.currentSettings();
        if (s.maxExplosionsPerSecondGlobal <= 0) return;
        if (!explosionsGlobal.tryIncrement(nowSecond(), s.maxExplosionsPerSecondGlobal)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        arrowPerPlayer.remove(id);
        tridentPerPlayer.remove(id);
    }

    private static boolean isMonster(EntityType type) {
        String n = type.name();
        return n.contains("ZOMBIE") || n.contains("SKELETON") || n.contains("CREEPER") || n.contains("SPIDER") || n.contains("SLIME")
                || n.contains("PILLAGER") || n.contains("VINDICATOR") || n.contains("EVOKER") || n.contains("RAVAGER")
                || n.contains("WITCH") || n.contains("GUARDIAN") || n.contains("ENDERMAN") || n.contains("BLAZE")
                || n.contains("GHAST") || n.contains("SHULKER") || n.contains("PHANTOM") || n.contains("DROWNED")
                || n.contains("HUSK") || n.contains("STRAY") || n.contains("WITHER") || n.contains("PIGLIN")
                || n.contains("HOGLIN") || n.contains("MAGMA") || n.contains("VEX") || n.contains("WARDEN");
    }

    private static boolean exceedsTypeInChunk(Chunk chunk, EntityType type, int max) {
        if (max <= 0) return false;
        int c = 0;
        for (Entity e : chunk.getEntities()) {
            if (e.getType() == type && ++c >= max) return true;
        }
        return false;
    }

    private static boolean exceedsLivingInChunk(Chunk chunk, boolean monsters, int max) {
        if (max <= 0) return false;
        int c = 0;
        for (Entity e : chunk.getEntities()) {
            EntityType t = e.getType();
            if (!t.isAlive()) continue;
            if (e instanceof Player) continue;
            if (e instanceof Villager) continue;
            if (monsters) {
                if (isMonster(t) && ++c >= max) return true;
            } else {
                if (!isMonster(t) && ++c >= max) return true;
            }
        }
        return false;
    }

    private static <T> boolean exceedsClassInChunk(Chunk chunk, Class<T> cls, int max) {
        if (max <= 0) return false;
        int c = 0;
        for (Entity e : chunk.getEntities()) {
            if (cls.isInstance(e) && ++c >= max) return true;
        }
        return false;
    }
}
