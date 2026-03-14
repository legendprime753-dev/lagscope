package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.analysis.LagAnalyzer;
import me.priyme.lagscope.analysis.LagMetrics;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.AbstractModule;
import org.bukkit.Bukkit;

public final class LagProtectionModule extends AbstractModule {
    private final LagAnalyzer analyzer;
    private final LagController controller;
    private int taskId = -1;
    private LagMode lastApplied = null;

    public LagProtectionModule(LagScopePlugin plugin, LagAnalyzer analyzer, LagController controller) {
        super(plugin);
        this.analyzer = analyzer;
        this.controller = controller;
    }

    @Override
    public String id() { return "LagProtection"; }

    @Override
    public String name() { return "Lag Protection"; }

    @Override
    public boolean enabledByDefault() { return true; }

    @Override
    protected void onEnable() {
        long interval = plugin.getConfig().getLong("modules.LagProtection.interval_ticks", 40L);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            boolean auto = plugin.getConfig().getBoolean("modules.LagProtection.auto_mode", true);
            if (!auto) {
                if (lastApplied != null) {
                    controller.setMode(lastApplied);
                    lastApplied = null;
                }
                return;
            }

            LagMetrics m = analyzer.getLatest();
            double tps = m.tps1;
            double mspt = m.mspt;

            double lowTps = plugin.getConfig().getDouble("modules.LagProtection.low_tps", 17.0);
            double hardTps = plugin.getConfig().getDouble("modules.LagProtection.hard_tps", 14.0);
            double highMspt = plugin.getConfig().getDouble("modules.LagProtection.high_mspt", 50.0);
            LagMode desired = null;
            if (tps <= hardTps || (mspt > 0 && mspt >= highMspt * 1.35)) desired = LagMode.EXTREME;
            else if (tps <= lowTps || (mspt > 0 && mspt >= highMspt)) desired = LagMode.HIGH;

            if (desired != null && controller.getMode().ordinal() < desired.ordinal()) {
                if (lastApplied == null) lastApplied = controller.getMode();
                controller.setMode(desired);
                if (plugin.getConfig().getBoolean("modules.LagProtection.run_clear", true)) {
                    controller.cleaner().clearAllWorlds(true);
                }
                return;
            }

            if (desired == null && lastApplied != null) {
                controller.setMode(lastApplied);
                lastApplied = null;
            }
        }, interval, interval);
    }

    @Override
    protected void onDisable() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        if (lastApplied != null) {
            controller.setMode(lastApplied);
            lastApplied = null;
        }
    }
}
