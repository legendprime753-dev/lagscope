package me.priyme.lagscope.control;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.analysis.SecondCounter;
import org.bukkit.Chunk;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
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
        
        // Nutze Paper API, um zu prüfen, ob der Chunk bereits überladen ist, 
        // BEVOR wir durch alle Entities iterieren!
        if (e instanceof Villager && s.maxVillagersPerChunk > 0) {
            if (exceedsTypeInChunk(e.getLocation().getChunk(), EntityType.VILLAGER, s.maxVillagersPerChunk)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(e instanceof Creature)) return;

        if (isMonster(type)) {
            if (s.maxMonstersPerChunk > 0 && exceedsLivingInChunk(e.getLocation().getChunk(), true, s.maxMonstersPerChunk)) {
                event.setCancelled(true);
            }
        } else {
            if (s.maxAnimalsPerChunk > 0 && exceedsLivingInChunk(e.getLocation().getChunk(), false, s.maxAnimalsPerChunk)) {
                event.setCancelled(true);
            }
        }
    }

    // ... (onEntitySpawn, onProjectileLaunch, etc. bleiben gleich) ...

    private static boolean isMonster(EntityType type) {
        // Optimiert: Nutze Switch oder Paper's interne Kategorien, wenn möglich. 
        // String-Contains auf Enums ist extrem langsam!
        return switch (type) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, SLIME, PILLAGER, VINDICATOR, 
                 EVOKER, RAVAGER, WITCH, GUARDIAN, ENDERMAN, BLAZE, GHAST, 
                 SHULKER, PHANTOM, DROWNED, HUSK, STRAY, WITHER, PIGLIN, 
                 HOGLIN, MAGMA_CUBE, VEX, WARDEN -> true;
            default -> false;
        };
    }

    private static boolean exceedsTypeInChunk(Chunk chunk, EntityType type, int max) {
        if (max <= 0) return false;
        int count = 0;
        // Durchbricht die Schleife SOFORT, wenn das Limit erreicht ist!
        for (Entity e : chunk.getEntities()) {
            if (e.getType() == type) {
                count++;
                if (count >= max) return true;
            }
        }
        return false;
    }

    private static boolean exceedsLivingInChunk(Chunk chunk, boolean monsters, int max) {
        if (max <= 0) return false;
        int count = 0;
        for (Entity e : chunk.getEntities()) {
            EntityType t = e.getType();
            if (!t.isAlive() || e instanceof Player || e instanceof Villager) continue;
            
            if (isMonster(t) == monsters) {
                count++;
                if (count >= max) return true;
            }
        }
        return false;
    }
}
