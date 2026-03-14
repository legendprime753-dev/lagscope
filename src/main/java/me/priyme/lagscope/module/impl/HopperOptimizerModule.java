package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Location;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HopperOptimizerModule extends AbstractModule {
    private final LagController controller;
    private final Map<Long, Long> lastMoveByPos = new ConcurrentHashMap<>();
    private final Listener listener = new Listener() {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onMove(InventoryMoveItemEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            if (!(e.getSource().getHolder() instanceof Hopper source)) return;
            Location l = source.getLocation();
            if (l == null) return;
            int minMs = plugin.getConfig().getInt("modules.HopperOptimizer.min_interval_ms", controller.getMode() == LagMode.EXTREME ? 250 : 120);
            long key = pack(l);
            long now = System.currentTimeMillis();
            Long prev = lastMoveByPos.put(key, now);
            if (prev != null && (now - prev) < minMs) {
                e.setCancelled(true);
            }
        }
    };

    public HopperOptimizerModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "HopperOptimizer"; }

    @Override
    public String name() { return "Hopper Optimizer"; }

    @Override
    public boolean enabledByDefault() { return true; }

    @Override
    protected void onEnable() {
        register(listener);
    }

    @Override
    protected void onDisable() {
        unregister(listener);
        lastMoveByPos.clear();
    }

    private static long pack(Location l) {
        long w = 0;
        if (l.getWorld() != null) {
            w = l.getWorld().getUID().getMostSignificantBits() ^ l.getWorld().getUID().getLeastSignificantBits();
        }
        long x = (l.getBlockX() & 0x3FFFFFFL);
        long y = (l.getBlockY() & 0xFFFL);
        long z = (l.getBlockZ() & 0x3FFFFFFL);
        return (w ^ (x << 38) ^ (z << 12) ^ y);
    }
}
