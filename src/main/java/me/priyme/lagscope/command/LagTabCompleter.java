package me.priyme.lagscope.command;

import me.priyme.lagscope.manager.ModuleManager;
import me.priyme.lagscope.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LagTabCompleter implements TabCompleter {
    private final ModuleManager moduleManager;

    public LagTabCompleter(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix(Arrays.asList("info","clear","mode","report","reload","module","modules"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("module") || args[0].equalsIgnoreCase("modules"))) {
            return prefix(Arrays.asList("list","enable","disable","toggle"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("module") || args[0].equalsIgnoreCase("modules"))) {
            String action = args[1].toLowerCase();
            if (action.equals("enable") || action.equals("disable") || action.equals("toggle")) {
                List<String> ids = new ArrayList<>();
                for (Module m : moduleManager.all()) ids.add(m.id());
                return prefix(ids, args[2]);
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return prefix(Arrays.asList("OFF","LOW","MEDIUM","HIGH","EXTREME"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clear")) {
            return prefix(Arrays.asList("chunk","radius"), args[1]);
        }
        return List.of();
    }

    private static List<String> prefix(List<String> opts, String in) {
        String s = in == null ? "" : in.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : opts) {
            if (o.toLowerCase().startsWith(s)) out.add(o);
        }
        return out;
    }
}
