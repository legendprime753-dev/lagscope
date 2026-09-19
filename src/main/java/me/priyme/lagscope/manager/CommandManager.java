package me.priyme.lagscope.manager;

import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.analysis.LagAnalyzer;
import me.priyme.lagscope.command.LagCommand;
import me.priyme.lagscope.command.LagTabCompleter;
import me.priyme.lagscope.control.LagController;

public final class CommandManager {
    private final LagScopePlugin plugin;
    private final LagAnalyzer analyzer;
    private final LagController controller;
    private final ModuleManager moduleManager;

    public CommandManager(LagScopePlugin plugin, LagAnalyzer analyzer, LagController controller, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.analyzer = analyzer;
        this.controller = controller;
        this.moduleManager = moduleManager;
    }

    public void register() {
        if (plugin.getCommand("lag") != null) {
            plugin.getCommand("lag").setExecutor(new LagCommand(plugin, analyzer, controller, moduleManager));
            plugin.getCommand("lag").setTabCompleter(new LagTabCompleter(moduleManager));
        }
    }
}
