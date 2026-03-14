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
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;


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
                    removed += purgeOverflow(c, s);
                }
            }

            if (removed > 0 && plugin.getConfig().getBoolean("modules.EntityLimiter.log_to_console", false)) {
                Bukkit.getLogger().info("[LagScope] EntityLimiter purged " + removed + " entities");
            }
        }, interval, interval);
    }

    private int purgeOverflow(Chunk chunk, LagController.ModeSettings s) {
        int maxItems = s.maxItemsPerChunk;
        int maxProjectiles = s.maxProjectilesPerChunk;
        int maxXpOrbs = s.maxXpOrbsPerChunk;
        int maxVillagers = s.maxVillagersPerChunk;
        if (maxItems <= 0 && maxProjectiles <= 0 && maxXpOrbs <= 0 && maxVillagers <= 0) return 0;

        Entity[] entities = chunk.getEntities();
        int items = 0;
        int projectiles = 0;
        int xp = 0;
        int villagers = 0;
        for (Entity e : entities) {
            if (e instanceof Item) items++;
            else if (e instanceof Projectile) projectiles++;
            else if (e instanceof ExperienceOrb) xp++;
            else if (e.getType() == EntityType.VILLAGER) villagers++;
        }

        int removeItems = maxItems > 0 ? Math.max(0, items - maxItems) : 0;
        int removeProjectiles = maxProjectiles > 0 ? Math.max(0, projectiles - maxProjectiles) : 0;
        int removeXp = maxXpOrbs > 0 ? Math.max(0, xp - maxXpOrbs) : 0;
        int removeVillagers = maxVillagers > 0 ? Math.max(0, villagers - maxVillagers) : 0;

        if (removeItems == 0 && removeProjectiles == 0 && removeXp == 0 && removeVillagers == 0) return 0;
        int removed = 0;
        for (Entity e : entities) {
            if (removeItems > 0 && e instanceof Item) {
                e.remove();
                removeItems--;
                removed++;
                continue;
            }
            if (removeProjectiles > 0 && e instanceof Projectile) {
                e.remove();
                removeProjectiles--;
                removed++;
                continue;
            }
            if (removeXp > 0 && e instanceof ExperienceOrb) {
                e.remove();
                removeXp--;
                removed++;
                continue;
            }
            if (removeVillagers > 0 && e.getType() == EntityType.VILLAGER) {
                if (e.getCustomName() != null) continue;
                e.remove();
                removeVillagers--;
                removed++;
            }
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
