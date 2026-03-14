package me.priyme.lagscope.manager;

import me.priyme.lagscope.LagScopePlugin;
import org.bukkit.Bukkit;

public final class ErrorsManager {
    private final LagScopePlugin plugin;

    public ErrorsManager(LagScopePlugin plugin) {
        this.plugin = plugin;
    }

    public void log(Throwable t) {
        Bukkit.getLogger().warning("[LagScope] " + t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "(no message)" : t.getMessage()));
        if (plugin.getConfig().getBoolean("debug.stacktraces", false)) {
            t.printStackTrace();
        }
    }
}
