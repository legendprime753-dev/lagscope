package me.priyme.lagscope.command;
import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.analysis.LagAnalyzer;
import me.priyme.lagscope.analysis.LagMetrics;
import me.priyme.lagscope.control.ClearResult;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.data.ChunkSnapshot;
import me.priyme.lagscope.gui.LagGui;
import me.priyme.lagscope.manager.ModuleManager;
import me.priyme.lagscope.module.Module;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

public final class LagCommand implements CommandExecutor {
    private final LagScopePlugin plugin;
    private final LagAnalyzer analyzer;
    private final LagController controller;
    private final ModuleManager moduleManager;

    public LagCommand(LagScopePlugin plugin, LagAnalyzer analyzer, LagController controller, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.analyzer = analyzer;
        this.controller = controller;
        this.moduleManager = moduleManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lagscope.use")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player p) {
                LagMetrics m = analyzer.getLatest();
                int rows = plugin.getConfig().getInt("gui.rows", 6);
                String title = plugin.getConfig().getString("gui.title", "LagScope");
                p.openInventory(LagGui.create(p, m, controller.getMode(), rows, title));
            } else {
                sender.sendMessage(Component.text("Use /lag info or /lag report from console.", NamedTextColor.GRAY));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "module", "modules" -> {
                if (!sender.hasPermission("lagscope.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length == 1 || args[1].equalsIgnoreCase("list")) {
                    sender.sendMessage(Component.text("Modules:", NamedTextColor.YELLOW));
                    for (Module m : moduleManager.all()) {
                        sender.sendMessage(Component.text("- " + m.id() + " = " + (m.isEnabled() ? "ENABLED" : "DISABLED"), m.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
                    }
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /lag module <enable|disable|toggle> <id>", NamedTextColor.GRAY));
                    return true;
                }
                String action = args[1].toLowerCase();
                String id = args[2];
                Module m = moduleManager.get(id);
                if (m == null) {
                    sender.sendMessage(Component.text("Unknown module: " + id, NamedTextColor.RED));
                    return true;
                }
                boolean target;
                if (action.equals("enable")) target = true;
                else if (action.equals("disable")) target = false;
                else if (action.equals("toggle")) target = !m.isEnabled();
                else {
                    sender.sendMessage(Component.text("Usage: /lag module <enable|disable|toggle> <id>", NamedTextColor.GRAY));
                    return true;
                }
                moduleManager.setEnabled(m.id(), target);
                sender.sendMessage(Component.text(m.id() + " " + (target ? "enabled" : "disabled"), target ? NamedTextColor.GREEN : NamedTextColor.RED));
                return true;
            }
            case "info" -> {
                LagMetrics m = analyzer.getLatest();
                sender.sendMessage(Component.text("TPS 1/5/15: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.2f / %.2f / %.2f", m.tps1, m.tps5, m.tps15), NamedTextColor.GREEN)));
                sender.sendMessage(Component.text("MSPT: ", NamedTextColor.GRAY)
                        .append(Component.text(m.mspt > 0 ? String.format("%.2f", m.mspt) : "n/a", NamedTextColor.GREEN)));
                sender.sendMessage(Component.text("Players: ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(m.players), NamedTextColor.GREEN)));
                sender.sendMessage(Component.text("Loaded chunks/entities: ", NamedTextColor.GRAY)
                        .append(Component.text(m.loadedChunks + " / " + m.loadedEntities, NamedTextColor.GREEN)));
                sender.sendMessage(Component.text("Mode: ", NamedTextColor.GRAY)
                        .append(Component.text(controller.getMode().name(), NamedTextColor.YELLOW)));
                return true;
            }
            case "mode" -> {
                if (!sender.hasPermission("lagscope.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /lag mode <OFF|LOW|MEDIUM|HIGH|EXTREME>", NamedTextColor.GRAY));
                    return true;
                }
                LagMode mode = LagMode.parse(args[1]);
                controller.setMode(mode);
                sender.sendMessage(Component.text("Mode set to ", NamedTextColor.GRAY).append(Component.text(mode.name(), NamedTextColor.YELLOW)));
                return true;
            }
            case "clear" -> {
                if (!sender.hasPermission("lagscope.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }

                boolean aggressive = controller.getMode() == LagMode.HIGH || controller.getMode() == LagMode.EXTREME;

                if (args.length == 1) {
                    ClearResult r = controller.cleaner().clearAllWorlds(aggressive);
                    sender.sendMessage(Component.text("Cleared: ", NamedTextColor.GRAY).append(Component.text(r.total() + " entities", NamedTextColor.GREEN)));
                    return true;
                }

                String scope = args[1].toLowerCase();
                if (scope.equals("chunk")) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text("Only players.", NamedTextColor.RED));
                        return true;
                    }
                    ClearResult r = controller.cleaner().clearChunk(p.getLocation().getChunk(), aggressive);
                    sender.sendMessage(Component.text("Cleared in chunk: ", NamedTextColor.GRAY).append(Component.text(r.total() + " entities", NamedTextColor.GREEN)));
                    return true;
                }

                if (scope.equals("radius")) {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text("Only players.", NamedTextColor.RED));
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(Component.text("Usage: /lag clear radius <blocks>", NamedTextColor.GRAY));
                        return true;
                    }
                    double r;
                    try {
                        r = Double.parseDouble(args[2]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
                        return true;
                    }
                    r = Math.max(1, Math.min(512, r));
                    ClearResult res = controller.cleaner().clearRadius(p.getLocation(), r, aggressive);
                    sender.sendMessage(Component.text("Cleared within " + (int) r + " blocks: ", NamedTextColor.GRAY)
                            .append(Component.text(res.total() + " entities", NamedTextColor.GREEN)));
                    return true;
                }

                sender.sendMessage(Component.text("Usage: /lag clear [chunk|radius <blocks>]", NamedTextColor.GRAY));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("lagscope.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadConfig();
                moduleManager.disableAll();
                controller.reload();
                moduleManager.enableAll();
                analyzer.start();
                controller.start();
                sender.sendMessage(Component.text("LagScope reloaded.", NamedTextColor.GREEN));
                return true;
            }
            case "report" -> {
                if (!sender.hasPermission("lagscope.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                File dir = new File(plugin.getDataFolder(), "reports");
                dir.mkdirs();
                String name = "report-" + Instant.now().toString().replace(':', '-') + ".txt";
                File f = new File(dir, name);
                try {
                    LagMetrics m = analyzer.getLatest();
                    StringBuilder sb = new StringBuilder();
                    sb.append("LagScope Report\n");
                    sb.append("Time: ").append(Instant.now()).append("\n");
                    sb.append("Mode: ").append(controller.getMode().name()).append("\n");
                    sb.append(String.format("TPS 1/5/15: %.2f / %.2f / %.2f\n", m.tps1, m.tps5, m.tps15));
                    sb.append("MSPT: ").append(m.mspt > 0 ? String.format("%.2f", m.mspt) : "n/a").append("\n");
                    sb.append("Players: ").append(m.players).append("\n");
                    sb.append("Loaded chunks: ").append(m.loadedChunks).append("\n");
                    sb.append("Loaded entities: ").append(m.loadedEntities).append("\n\n");
                    sb.append("Top chunks:\n");
                    for (ChunkSnapshot cs : m.topChunks) {
                        String tiles = cs.tileEntities >= 0 ? String.valueOf(cs.tileEntities) : "n/a";
                        String hoppers = cs.hoppers >= 0 ? String.valueOf(cs.hoppers) : "n/a";
                        sb.append(cs.key).append(" entities=").append(cs.entities)
                                .append(" items=").append(cs.items)
                                .append(" xp=").append(cs.xpOrbs)
                                .append(" proj=").append(cs.projectiles)
                                .append(" villagers=").append(cs.villagers)
                                .append(" armorstands=").append(cs.armorStands)
                                .append(" tiles=").append(tiles)
                                .append(" hoppers=").append(hoppers)
                                .append("\n");
                    }
                    sb.append("\nWorld entity counts:\n");
                    for (World w : Bukkit.getWorlds()) {
                        sb.append(w.getName()).append(" entities=").append(w.getEntities().size()).append(" loadedChunks=").append(w.getLoadedChunks().length).append("\n");
                    }
                    Files.writeString(f.toPath(), sb.toString(), StandardCharsets.UTF_8);
                    sender.sendMessage(Component.text("Saved report: " + f.getName(), NamedTextColor.GREEN));
                } catch (Exception ex) {
                    sender.sendMessage(Component.text("Failed to write report.", NamedTextColor.RED));
                }
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("/lag", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/lag info", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/lag clear [chunk|radius <blocks>]", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/lag mode <OFF|LOW|MEDIUM|HIGH|EXTREME>", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/lag module list", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/lag module <enable|disable|toggle> <id>", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/lag report", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/lag reload", NamedTextColor.GRAY));
                return true;
            }
        }
    }
}
