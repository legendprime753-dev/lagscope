package me.priyme.lagscope.listener;
import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.LagScopePlugin;
import me.priyme.lagscope.analysis.LagAnalyzer;
import me.priyme.lagscope.analysis.LagMetrics;
import me.priyme.lagscope.control.ClearResult;
import me.priyme.lagscope.control.LagController;
import me.priyme.lagscope.data.ChunkKey;
import me.priyme.lagscope.gui.LagGui;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

public final class LagGuiListener implements Listener {
    private final LagScopePlugin plugin;
    private final LagAnalyzer analyzer;
    private final LagController controller;

    public LagGuiListener(LagScopePlugin plugin, LagAnalyzer analyzer, LagController controller) {
        this.plugin = plugin;
        this.analyzer = analyzer;
        this.controller = controller;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof LagGui.LagGuiHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= inv.getSize()) return;
        ChunkKey key = holder.getKeyForSlot(slot);
        if (key == null) {
            if (slot == 8) {
                p.sendMessage(Component.text("Current mode: ", NamedTextColor.GRAY).append(Component.text(controller.getMode().name(), NamedTextColor.YELLOW)));
            }
            return;
        }

        World w = Bukkit.getWorld(key.world);
        if (w == null) return;
        Chunk c = w.getChunkAt(key.x, key.z);

        switch (e.getClick()) {
            case LEFT, SHIFT_LEFT -> {
                if (!p.hasPermission("lagscope.tp")) {
                    p.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return;
                }
                Location loc = new Location(w, key.x * 16 + 8, w.getHighestBlockYAt(key.x * 16 + 8, key.z * 16 + 8) + 1, key.z * 16 + 8);
                p.teleport(loc);
                p.sendMessage(Component.text("Teleported to chunk ", NamedTextColor.GRAY)
                        .append(Component.text(key.x + "," + key.z, NamedTextColor.GREEN)));
            }
            case RIGHT, SHIFT_RIGHT -> {
                boolean aggressive = controller.getMode() == LagMode.HIGH || controller.getMode() == LagMode.EXTREME;
                ClearResult r = controller.cleaner().clearChunk(c, aggressive);
                p.sendMessage(Component.text("Cleared in chunk: ", NamedTextColor.GRAY)
                        .append(Component.text(r.total() + " entities", NamedTextColor.GREEN)));
                refresh(p);
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof LagGui.LagGuiHolder) {
        }
    }

    private void refresh(Player p) {
        LagMetrics m = analyzer.getLatest();
        int rows = plugin.getConfig().getInt("gui.rows", 6);
        String title = plugin.getConfig().getString("gui.title", "LagScope");
        p.openInventory(LagGui.create(p, m, controller.getMode(), rows, title));
    }
}
