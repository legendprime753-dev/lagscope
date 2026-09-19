package me.priyme.lagscope.manager;

import me.priyme.lagscope.LagScopePlugin;

public final class ConfigManager {
    private final LagScopePlugin plugin;

    public ConfigManager(LagScopePlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }
}
