package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.ClearResult;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;


public final class EntityCleanerModule extends AbstractModule {
    private final LagController controller;
    private int taskId = -1;

    public EntityCleanerModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() {
        return "EntityCleaner";
    }

    @Override
    public String name() {
        return "Entity Cleaner";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        long intervalTicks = plugin.getConfig().getLong("modules.EntityCleaner.interval_ticks", 20L * 60L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (controller.getMode() == LagMode.OFF) return;
            boolean aggressive = controller.getMode() == LagMode.HIGH || controller.getMode() == LagMode.EXTREME;
            long maxItemAge = plugin.getConfig().getLong("modules.EntityCleaner.max_item_age_ticks", aggressive ? 20L * 60L * 3L : 20L * 60L * 10L);
            long maxProjAge = plugin.getConfig().getLong("modules.EntityCleaner.max_projectile_age_ticks", aggressive ? 20L * 15L : 20L * 45L);
            long maxXpAge = plugin.getConfig().getLong("modules.EntityCleaner.max_xp_age_ticks", aggressive ? 20L * 30L : 20L * 90L);

            int removed = 0;
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e instanceof Item item) {
                        if (item.getTicksLived() >= maxItemAge) {
                            item.remove();
                            removed++;
                        }
                        continue;
                    }
                    if (e instanceof Projectile proj) {
                        if (proj.getTicksLived() >= maxProjAge) {
                            proj.remove();
                            removed++;
                        }
                        continue;
                    }
                    if (e instanceof ExperienceOrb orb) {
                        if (orb.getTicksLived() >= maxXpAge) {
                            orb.remove();
                            removed++;
                        }
                    }
                }
            }

            if (aggressive && plugin.getConfig().getBoolean("modules.EntityCleaner.aggressive_burst", true)) {
                ClearResult r = controller.cleaner().clearAllWorlds(true);
                removed += r.total();
            }

            if (removed > 0 && plugin.getConfig().getBoolean("modules.EntityCleaner.announce_to_console", false)) {
                Bukkit.getLogger().info("[LagScope] WorldCleaner removed " + removed + " entities");
            }
        }, intervalTicks, intervalTicks);
    }

    @Override
    protected void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
