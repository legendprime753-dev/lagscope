package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.analysis.SecondCounter;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RedstoneLimiterModule extends AbstractModule {
    private final LagController controller;
    private final Map<Long, SecondCounter> toggles = new ConcurrentHashMap<>();

    private final Listener listener = new Listener() {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onRedstone(BlockRedstoneEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            int maxToggles = plugin.getConfig().getInt("modules.RedstoneLimiter.max_toggles_per_second", controller.getMode() == LagMode.EXTREME ? 6 : 12);
            if (maxToggles <= 0) return;
            long key = pack(e.getBlock().getLocation());
            SecondCounter c = toggles.computeIfAbsent(key, k -> new SecondCounter());
            if (!c.tryIncrement(System.currentTimeMillis() / 1000L, maxToggles)) {
                e.setNewCurrent(0);
            }
        }
    };

    public RedstoneLimiterModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "RedstoneLimiter"; }

    @Override
    public String name() { return "Redstone Limiter"; }

    @Override
    public boolean enabledByDefault() { return true; }

    @Override
    protected void onEnable() { register(listener); }

    @Override
    protected void onDisable() {
        unregister(listener);
        toggles.clear();
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
