package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

public final class VehicleLimiterModule extends AbstractModule {
    private final LagController controller;

    private final Listener listener = new Listener() {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMove(VehicleMoveEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            Vehicle v = e.getVehicle();
            double max = plugin.getConfig().getDouble("modules.VehicleLimiter.max_speed", controller.getMode() == LagMode.EXTREME ? 0.35 : 0.6);
            Vector vel = v.getVelocity();
            if (vel.lengthSquared() > max * max) {
                Vector n = vel.normalize().multiply(max);
                v.setVelocity(n);
            }
        }
    };

    public VehicleLimiterModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "VehicleLimiter"; }

    @Override
    public String name() { return "Vehicle Limiter"; }

    @Override
    public boolean enabledByDefault() { return false; }

    @Override
    protected void onEnable() { register(listener); }

    @Override
    protected void onDisable() { unregister(listener); }
}
