package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class FastLeafDecayModule extends AbstractModule {
    private final LagController controller;
    private final Listener listener = new Listener() {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            Material type = e.getBlock().getType();
            if (!type.name().endsWith("_LOG") && !type.name().endsWith("_WOOD")) return;
            int radius = plugin.getConfig().getInt("modules.FastLeafDecay.radius", controller.getMode() == LagMode.EXTREME ? 5 : 4);
            int maxPerTick = plugin.getConfig().getInt("modules.FastLeafDecay.max_per_tick", controller.getMode() == LagMode.EXTREME ? 128 : 64);
            Block origin = e.getBlock();
            Bukkit.getScheduler().runTask(plugin, () -> decay(origin, radius, maxPerTick));
        }
    };

    public FastLeafDecayModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "FastLeafDecay"; }

    @Override
    public String name() { return "Fast Leaf Decay"; }

    @Override
    public boolean enabledByDefault() { return false; }

    @Override
    protected void onEnable() { register(listener); }

    @Override
    protected void onDisable() { unregister(listener); }

    private void decay(Block origin, int radius, int maxPerTick) {
        World w = origin.getWorld();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        ArrayDeque<Block> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = w.getBlockAt(ox + dx, oy + dy, oz + dz);
                    if (isLeaf(b.getType())) q.add(b);
                }
            }
        }
        if (q.isEmpty()) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (n < maxPerTick && !q.isEmpty()) {
                    Block b = q.poll();
                    if (b == null) break;
                    long key = (((long) b.getX()) & 0x3FFFFFFL) << 38 | (((long) b.getZ()) & 0x3FFFFFFL) << 12 | (((long) b.getY()) & 0xFFFL);
                    if (!seen.add(key)) continue;
                    Material t = b.getType();
                    if (!isLeaf(t)) continue;
                    b.breakNaturally();
                    n++;
                }
                if (q.isEmpty()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean isLeaf(Material m) {
        String n = m.name();
        return n.endsWith("_LEAVES") || n.equals("AZALEA_LEAVES") || n.equals("FLOWERING_AZALEA_LEAVES");
    }
}
