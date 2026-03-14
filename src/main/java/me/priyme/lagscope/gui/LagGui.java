package me.priyme.lagscope.gui;
import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.analysis.LagMetrics;
import me.priyme.lagscope.data.ChunkKey;
import me.priyme.lagscope.data.ChunkSnapshot;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class LagGui {
    private static final DecimalFormat DF2 = new DecimalFormat("0.00");

    public static Inventory create(Player viewer, LagMetrics m, LagMode mode, int rows, String title) {
        int size = Math.min(6, Math.max(1, rows)) * 9;
        LagGuiHolder holder = new LagGuiHolder();
        Inventory inv = Bukkit.createInventory(holder, size, Component.text(title));

        inv.setItem(0, infoItem(Material.CLOCK, Component.text("TPS", NamedTextColor.AQUA), List.of(
                Component.text("1m: " + DF2.format(m.tps1)),
                Component.text("5m: " + DF2.format(m.tps5)),
                Component.text("15m: " + DF2.format(m.tps15))
        )));

        inv.setItem(1, infoItem(Material.COMPARATOR, Component.text("MSPT", NamedTextColor.AQUA), List.of(
                Component.text(m.mspt > 0 ? (DF2.format(m.mspt) + " ms") : "n/a")
        )));

        inv.setItem(2, infoItem(Material.REDSTONE, Component.text("Memory", NamedTextColor.AQUA), List.of(
                Component.text("Used: " + humanBytes(m.usedMemory)),
                Component.text("Max: " + humanBytes(m.maxMemory))
        )));

        inv.setItem(3, infoItem(Material.PLAYER_HEAD, Component.text("Players", NamedTextColor.AQUA), List.of(
                Component.text(String.valueOf(m.players))
        )));

        inv.setItem(4, infoItem(Material.GRASS_BLOCK, Component.text("Loaded", NamedTextColor.AQUA), List.of(
                Component.text("Chunks: " + m.loadedChunks),
                Component.text("Entities: " + m.loadedEntities)
        )));

        inv.setItem(8, infoItem(Material.NETHER_STAR, Component.text("Mode", NamedTextColor.GOLD), List.of(
                Component.text(mode.name(), NamedTextColor.YELLOW),
                Component.text("/lag mode <OFF|LOW|MEDIUM|HIGH|EXTREME>", NamedTextColor.GRAY)
        )));

        int slot = 9;
        for (ChunkSnapshot cs : m.topChunks) {
            if (slot >= size) break;
            ItemStack it = chunkItem(cs);
            inv.setItem(slot, it);
            holder.slotToChunkKey.add(cs.key);
            holder.slotIndex.add(slot);
            slot++;
        }

        holder.viewer = viewer.getUniqueId();
        return inv;
    }

    private static ItemStack chunkItem(ChunkSnapshot cs) {
        Material mat = cs.entities >= 300 ? Material.TNT : (cs.entities >= 200 ? Material.BLAZE_POWDER : Material.PAPER);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(cs.key.world, NamedTextColor.GRAY));
        lore.add(Component.text("x: " + cs.key.x + "  z: " + cs.key.z, NamedTextColor.GRAY));
        lore.add(Component.text("Entities: " + cs.entities, NamedTextColor.WHITE));
        lore.add(Component.text("Items: " + cs.items + "  XP: " + cs.xpOrbs, NamedTextColor.WHITE));
        lore.add(Component.text("Projectiles: " + cs.projectiles + "  Falling: " + cs.fallingBlocks, NamedTextColor.WHITE));
        lore.add(Component.text("Villagers: " + cs.villagers + "  ArmorStands: " + cs.armorStands, NamedTextColor.WHITE));
        String tiles = cs.tileEntities >= 0 ? String.valueOf(cs.tileEntities) : "n/a";
        String hoppers = cs.hoppers >= 0 ? String.valueOf(cs.hoppers) : "n/a";
        lore.add(Component.text("Tiles: " + tiles + "  Hoppers: " + hoppers, NamedTextColor.WHITE));
        lore.add(Component.text("Left click: teleport (perm)", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Right click: clear entities", NamedTextColor.DARK_GRAY));
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("Chunk " + cs.key.x + "," + cs.key.z, NamedTextColor.GREEN));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack infoItem(Material mat, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static String humanBytes(long b) {
        double v = b;
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return DF2.format(v) + " " + u[i];
    }

    public static final class LagGuiHolder implements InventoryHolder {
        public java.util.UUID viewer;
        public final List<Integer> slotIndex = new ArrayList<>();
        public final List<ChunkKey> slotToChunkKey = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return null;
        }

        public ChunkKey getKeyForSlot(int slot) {
            for (int i = 0; i < slotIndex.size(); i++) {
                if (slotIndex.get(i) == slot) return slotToChunkKey.get(i);
            }
            return null;
        }
    }
}
