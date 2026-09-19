package me.priyme.lagscope.manager;

import me.priyme.lagscope.LagScopePlugin;
import org.bukkit.Bukkit;

public final class HookManager {
    private final boolean spark;
    private final boolean placeholderApi;

    public HookManager(LagScopePlugin plugin) {
        this.spark = Bukkit.getPluginManager().getPlugin("spark") != null;
        this.placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean hasSpark() {
        return spark;
    }

    public boolean hasPlaceholderApi() {
        return placeholderApi;
    }
}
