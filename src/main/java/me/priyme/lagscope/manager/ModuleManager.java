package me.priyme.lagscope.manager;

import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.analysis.LagAnalyzer;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.module.Module;
import me.priyme.lagscope.module.impl.*;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public final class ModuleManager {
    private final LagScopePlugin plugin;
    private final LagAnalyzer analyzer;
    private final LagController controller;
    private final SupportManager support;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(LagScopePlugin plugin, LagAnalyzer analyzer, LagController controller, SupportManager support) {
        this.plugin = plugin;
        this.analyzer = analyzer;
        this.controller = controller;
        this.support = support;
        registerBuiltins();
    }

    private void registerBuiltins() {
        add(new ConsoleCleanerModule(plugin));
        add(new EntityCleanerModule(plugin, controller));
        add(new EntityLimiterModule(plugin, controller));
        add(new ExplosionLimiterModule(plugin, controller));
        add(new HopperOptimizerModule(plugin, controller));
        add(new RedstoneLimiterModule(plugin, controller));
        add(new ActionLimiterModule(plugin, controller));
        add(new VehicleLimiterModule(plugin, controller));
        add(new FastLeafDecayModule(plugin, controller));
        add(new MobAIOptimizerModule(plugin, controller));
        add(new LagProtectionModule(plugin, analyzer, controller));
    }

    private void add(Module module) {
        modules.put(module.id().toLowerCase(Locale.ROOT), module);
    }

    public Collection<Module> all() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public Module get(String id) {
        if (id == null) return null;
        return modules.get(id.toLowerCase(Locale.ROOT));
    }

    public void enableAll() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("modules");
        for (Module m : modules.values()) {
            boolean enabled = sec == null ? m.enabledByDefault() : sec.getBoolean(m.id() + ".enabled", m.enabledByDefault());
            if (enabled) {
                try { m.enable(); } catch (Throwable t) { plugin.errors().log(t); }
            }
        }
    }

    public void disableAll() {
        for (Module m : modules.values()) {
            if (m.isEnabled()) {
                try { m.disable(); } catch (Throwable t) { plugin.errors().log(t); }
            }
        }
    }

    public boolean setEnabled(String id, boolean enabled) {
        Module m = get(id);
        if (m == null) return false;
        if (enabled && !m.isEnabled()) m.enable();
        if (!enabled && m.isEnabled()) m.disable();
        plugin.getConfig().set("modules." + m.id() + ".enabled", enabled);
        plugin.saveConfig();
        return true;
    }

    public LagController controller() {
        return controller;
    }
}
