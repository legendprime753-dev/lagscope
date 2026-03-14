package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

public final class ExplosionLimiterModule extends AbstractModule {
    private final LagController controller;
    private final Listener listener = new Listener() {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPrime(ExplosionPrimeEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            float maxPower = (float) plugin.getConfig().getDouble("modules.ExplosionLimiter.max_power", controller.getMode() == LagMode.EXTREME ? 1.5 : 3.0);
            if (e.getRadius() > maxPower) e.setRadius(maxPower);
            if (plugin.getConfig().getBoolean("modules.ExplosionLimiter.reduce_fire", true)) e.setFire(false);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onExplode(EntityExplodeEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            int maxBlocks = plugin.getConfig().getInt("modules.ExplosionLimiter.max_blocks", controller.getMode() == LagMode.EXTREME ? 8 : 20);
            if (maxBlocks >= 0 && e.blockList().size() > maxBlocks) {
                e.blockList().subList(maxBlocks, e.blockList().size()).clear();
            }
            float yield = (float) plugin.getConfig().getDouble("modules.ExplosionLimiter.max_yield", controller.getMode() == LagMode.EXTREME ? 0.0 : 0.2);
            if (e.getYield() > yield) e.setYield(yield);
        }
    };

    public ExplosionLimiterModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "ExplosionLimiter"; }

    @Override
    public String name() { return "Explosion Limiter"; }

    @Override
    public boolean enabledByDefault() { return true; }

    @Override
    protected void onEnable() { register(listener); }

    @Override
    protected void onDisable() { unregister(listener); }
}
