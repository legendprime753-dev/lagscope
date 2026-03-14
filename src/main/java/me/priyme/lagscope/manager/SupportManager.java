package me.priyme.lagscope.manager;

import me.priyme.lagscope.LagScopePlugin;
import org.bukkit.Bukkit;

public final class SupportManager {
    private final boolean paper;

    public SupportManager(LagScopePlugin plugin) {
        this.paper = isClassPresent("io.papermc.paper.configuration.Configuration") || isClassPresent("com.destroystokyo.paper.PaperConfig");
    }

    public boolean isPaper() {
        return paper;
    }

    public String serverVersion() {
        return Bukkit.getBukkitVersion();
    }

    private boolean isClassPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
