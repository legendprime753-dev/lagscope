package me.priyme.lagscope.module;

import me.priyme.lagscope.LagScopePlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public abstract class AbstractModule implements Module {
    protected final LagScopePlugin plugin;
    private boolean enabled;

    protected AbstractModule(LagScopePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public final void enable() {
        if (enabled) return;
        enabled = true;
        onEnable();
    }

    @Override
    public final void disable() {
        if (!enabled) return;
        enabled = false;
        onDisable();
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    protected final void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    protected final void unregister(Listener listener) {
        HandlerList.unregisterAll(listener);
    }

    protected abstract void onEnable();

    protected abstract void onDisable();
}
