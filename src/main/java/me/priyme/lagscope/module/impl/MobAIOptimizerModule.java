package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class MobAIOptimizerModule extends AbstractModule {
    private final LagController controller;
    private int taskId = -1;

    public MobAIOptimizerModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "MobAIOptimizer"; }

    @Override
    public String name() { return "Mob AI Optimizer"; }

    @Override
    public boolean enabledByDefault() { return false; }

    @Override
    protected void onEnable() {
        long interval = plugin.getConfig().getLong("modules.MobAIOptimizer.interval_ticks", 20L * 10L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (controller.getMode() == LagMode.OFF) return;
            int minDistance = plugin.getConfig().getInt("modules.MobAIOptimizer.player_distance", 28);
            int minDistanceSq = minDistance * minDistance;
            int maxPerChunk = plugin.getConfig().getInt("modules.MobAIOptimizer.max_ai_mobs_per_chunk", controller.getMode() == LagMode.EXTREME ? 12 : 24);
            for (World w : Bukkit.getWorlds()) {
                java.util.List<Player> players = w.getPlayers();
                for (Chunk c : w.getLoadedChunks()) {
                    int ai = 0;
                    for (org.bukkit.entity.Entity e : c.getEntities()) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (le instanceof Player) continue;
                        if (le.getType().name().contains("VILLAGER")) continue;
                        if (nearPlayer(le, players, minDistanceSq)) {
                            if (!le.hasAI()) le.setAI(true);
                            continue;
                        }
                        if (ai < maxPerChunk) {
                            if (!le.hasAI()) {
                                ai++;
                                continue;
                            }
                            ai++;
                        } else {
                            if (le.hasAI()) le.setAI(false);
                        }
                    }
                }
            }
        }, interval, interval);
    }

    private boolean nearPlayer(LivingEntity e, java.util.List<Player> players, int distSq) {
        for (Player p : players) {
            if (!p.isOnline()) continue;
            if (p.getLocation().distanceSquared(e.getLocation()) <= distSq) return true;
        }
        return false;
    }

    @Override
    protected void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
