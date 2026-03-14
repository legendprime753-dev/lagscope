package me.priyme.lagscope;

import me.priyme.lagscope.analysis.LagAnalyzer;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.control.LagEnforcer;
import me.priyme.lagscope.listener.LagGuiListener;
import me.priyme.lagscope.manager.CommandManager;
import me.priyme.lagscope.manager.ConfigManager;
import me.priyme.lagscope.manager.ErrorsManager;
import me.priyme.lagscope.manager.HookManager;
import me.priyme.lagscope.manager.MetricsManager;
import me.priyme.lagscope.manager.ModuleManager;
import me.priyme.lagscope.manager.SupportManager;
import me.priyme.lagscope.manager.UpdaterManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class LagScopePlugin extends JavaPlugin {
    private LagAnalyzer analyzer;
    private LagController controller;
    private ConfigManager configManager;
    private ErrorsManager errorsManager;
    private SupportManager supportManager;
    private HookManager hookManager;
    private MetricsManager metricsManager;
    private UpdaterManager updaterManager;
    private CommandManager commandManager;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.errorsManager = new ErrorsManager(this);
        this.supportManager = new SupportManager(this);
        this.hookManager = new HookManager(this);
        this.metricsManager = new MetricsManager(this);
        this.updaterManager = new UpdaterManager(this);

        this.analyzer = new LagAnalyzer(this);
        this.controller = new LagController(this, analyzer);
        this.moduleManager = new ModuleManager(this, analyzer, controller, supportManager);
        this.commandManager = new CommandManager(this, analyzer, controller, moduleManager);

        getServer().getPluginManager().registerEvents(new LagGuiListener(this, analyzer, controller), this);
        getServer().getPluginManager().registerEvents(new LagEnforcer(controller), this);

        moduleManager.enableAll();
        commandManager.register();

        analyzer.start();
        controller.start();
        updaterManager.checkAsync();

        Bukkit.getConsoleSender().sendMessage("[LagScope] enabled");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) moduleManager.disableAll();
        if (controller != null) controller.stop();
        if (analyzer != null) analyzer.stop();
        Bukkit.getConsoleSender().sendMessage("[LagScope] disabled");
    }

    public ConfigManager configManager() { return configManager; }
    public ErrorsManager errors() { return errorsManager; }
    public SupportManager support() { return supportManager; }
    public HookManager hooks() { return hookManager; }
    public MetricsManager metrics() { return metricsManager; }
    public ModuleManager modules() { return moduleManager; }
}
