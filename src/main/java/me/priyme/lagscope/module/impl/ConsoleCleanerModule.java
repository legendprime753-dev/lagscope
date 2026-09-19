package me.priyme.lagscope.module.impl;

import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.module.AbstractModule;

import java.util.List;
import java.util.Objects;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class ConsoleCleanerModule extends AbstractModule {
    private Filter previous;

    public ConsoleCleanerModule(LagScopePlugin plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ConsoleCleaner";
    }

    @Override
    public String name() {
        return "Console Cleaner";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        Logger root = Logger.getLogger("");
        previous = root.getFilter();
        List<String> patterns = plugin.getConfig().getStringList("modules.ConsoleCleaner.suppress_contains");
        root.setFilter(new Filter() {
            @Override
            public boolean isLoggable(LogRecord record) {
                if (record == null) return true;
                String msg = record.getMessage();
                if (msg == null) return delegate(record);
                for (String p : patterns) {
                    if (p == null || p.isBlank()) continue;
                    if (msg.contains(p)) return false;
                }
                return delegate(record);
            }

            private boolean delegate(LogRecord record) {
                Filter prev = previous;
                return prev == null || prev.isLoggable(record);
            }
        });
    }

    @Override
    protected void onDisable() {
        Logger.getLogger("").setFilter(previous);
        previous = null;
    }
}
