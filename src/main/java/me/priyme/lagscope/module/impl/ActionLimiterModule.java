package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionLimiterModule extends AbstractModule {
    private final LagController controller;
    private final Map<UUID, Long> lastFirework = new ConcurrentHashMap<>();

    private final Listener listener = new Listener() {
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onInteract(PlayerInteractEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            Player p = e.getPlayer();
            if (!p.isGliding()) return;
            ItemStack item = e.getItem();
            if (item == null) return;
            if (item.getType() != Material.FIREWORK_ROCKET) return;
            int minMs = plugin.getConfig().getInt("modules.ActionLimiter.firework_cooldown_ms", controller.getMode() == LagMode.EXTREME ? 650 : 350);
            long now = System.currentTimeMillis();
            long last = lastFirework.getOrDefault(p.getUniqueId(), 0L);
            if ((now - last) < minMs) {
                e.setCancelled(true);
                return;
            }
            lastFirework.put(p.getUniqueId(), now);
            int cdTicks = plugin.getConfig().getInt("modules.ActionLimiter.firework_item_cooldown_ticks", controller.getMode() == LagMode.EXTREME ? 10 : 5);
            if (cdTicks > 0) {
                p.setCooldown(Material.FIREWORK_ROCKET, cdTicks);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onMove(PlayerMoveEvent e) {
            if (controller.getMode() == LagMode.OFF) return;
            Player p = e.getPlayer();
            if (!p.isGliding()) return;
            double max = plugin.getConfig().getDouble("modules.ActionLimiter.elytra_max_speed", controller.getMode() == LagMode.EXTREME ? 1.35 : 2.15);
            Vector v = p.getVelocity();
            if (v.lengthSquared() > max * max) {
                p.setVelocity(v.normalize().multiply(max));
            }
        }
    };

    public ActionLimiterModule(LagScopePlugin plugin, LagController controller) {
        super(plugin);
        this.controller = controller;
    }

    @Override
    public String id() { return "ActionLimiter"; }

    @Override
    public String name() { return "Action Limiter"; }

    @Override
    public boolean enabledByDefault() { return false; }

    @Override
    protected void onEnable() { register(listener); }

    @Override
    protected void onDisable() {
        unregister(listener);
        lastFirework.clear();
    }
}
