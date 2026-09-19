package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;


public final class EntityLimiterModule extends AbstractModule {
    private final LagController controller;
    private int taskId = -1;

    public EntityLimiterModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "EntityLimiter"; }

    @Override
    public String name() { return "Entity Limiter"; }

    @Override
    public boolean enabledByDefault() { return true; }

    @Override
    protected void onEnable() {
        long interval = plugin.getConfig().getLong("modules.EntityLimiter.interval_ticks", 20L * 15L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (controller.getMode() == LagMode.OFF) return;
            boolean purge = plugin.getConfig().getBoolean("modules.EntityLimiter.overflow_purge", true);
            if (!purge) return;
            LagController.ModeSettings s = controller.currentSettings();

            int removed = 0;
            for (World w : Bukkit.getWorlds()) {
                for (Chunk c : w.getLoadedChunks()) {
                    if (s.maxItemsPerChunk > 0) removed += purgeOverflow(c, EntityType.ITEM, s.maxItemsPerChunk);
                    if (s.maxProjectilesPerChunk > 0) removed += purgeClassOverflow(c, org.bukkit.entity.Projectile.class, s.maxProjectilesPerChunk);
                    if (s.maxXpOrbsPerChunk > 0) removed += purgeClassOverflow(c, org.bukkit.entity.ExperienceOrb.class, s.maxXpOrbsPerChunk);
                    if (s.maxVillagersPerChunk > 0) removed += purgeTypeOverflow(c, EntityType.VILLAGER, s.maxVillagersPerChunk, true);
                }
            }

            if (removed > 0 && plugin.getConfig().getBoolean("modules.EntityLimiter.log_to_console", false)) {
                Bukkit.getLogger().info("[LagScope] EntityLimiter purged " + removed + " entities");
            }
        }, interval, interval);
    }

    private int purgeOverflow(Chunk chunk, EntityType type, int max) {
        return purgeTypeOverflow(chunk, type, max, false);
    }

    private int purgeTypeOverflow(Chunk chunk, EntityType type, int max, boolean keepNamed) {
        Entity[] entities = chunk.getEntities();
        int count = 0;
        for (Entity e : entities) if (e.getType() == type) count++;
        if (count <= max) return 0;
        int toRemove = count - max;
        int removed = 0;
        for (Entity e : entities) {
            if (removed >= toRemove) break;
            if (e.getType() != type) continue;
            if (keepNamed && e.getCustomName() != null) continue;
            e.remove();
            removed++;
        }
        return removed;
    }

    private <T> int purgeClassOverflow(Chunk chunk, Class<T> cls, int max) {
        Entity[] entities = chunk.getEntities();
        int count = 0;
        for (Entity e : entities) if (cls.isInstance(e)) count++;
        if (count <= max) return 0;
        int toRemove = count - max;
        int removed = 0;
        for (Entity e : entities) {
            if (removed >= toRemove) break;
            if (!cls.isInstance(e)) continue;
            e.remove();
            removed++;
        }
        return removed;
    }

    @Override
    protected void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
