package me.priyme.lagscope.analysis;
import me.priyme.lagscope.data.ChunkKey;
import me.priyme.lagscope.data.ChunkSnapshot;


import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class LagAnalyzer {
    private final Plugin plugin;
    private BukkitTask task;
    private final AtomicReference<LagMetrics> latest = new AtomicReference<>();

    public LagAnalyzer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int refresh = Math.max(1, plugin.getConfig().getInt("analysis.refresh-seconds", 5));
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::runScan, 20L, 20L * refresh);
        runScan();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public LagMetrics getLatest() {
        LagMetrics m = latest.get();
        if (m == null) return new LagMetrics(20.0, 20.0, 20.0, -1.0, 0L, 0L, Bukkit.getOnlinePlayers().size(), 0, 0, List.of());
        return m;
    }

    private void runScan() {
        double[] tps = readTps();
        double mspt = readMspt();

        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory();
        long usedMem = rt.totalMemory() - rt.freeMemory();

        int players = Bukkit.getOnlinePlayers().size();

        int loadedChunks = 0;
        int loadedEntities = 0;

        int topN = Math.max(1, plugin.getConfig().getInt("analysis.top-chunks", 12));
        List<ChunkSnapshot> chunkSnapshots = new ArrayList<>();

        boolean incNether = plugin.getConfig().getBoolean("analysis.include-nether", true);
        boolean incEnd = plugin.getConfig().getBoolean("analysis.include-the-end", true);

        for (World w : Bukkit.getWorlds()) {
            if (!incNether && w.getEnvironment() == World.Environment.NETHER) continue;
            if (!incEnd && w.getEnvironment() == World.Environment.THE_END) continue;

            Chunk[] chunks = w.getLoadedChunks();
            loadedChunks += chunks.length;

            for (Chunk c : chunks) {
                Entity[] ents = c.getEntities();
                loadedEntities += ents.length;

                int items = 0;
                int xp = 0;
                int proj = 0;
                int vill = 0;
                int armor = 0;
                int veh = 0;
                int fall = 0;

                for (Entity e : ents) {
                    if (e instanceof Item) items++;
                    else if (e instanceof ExperienceOrb) xp++;
                    else if (e instanceof Projectile) proj++;
                    else if (e instanceof Villager) vill++;
                    else if (e instanceof ArmorStand) armor++;
                    else if (e instanceof Vehicle) veh++;
                    else if (e instanceof FallingBlock) fall++;
                }

                BlockState[] tiles = safeTileEntities(c);
                int tileCount = tiles.length;
                int hoppers = 0;
                for (BlockState bs : tiles) {
                    if (bs instanceof Hopper) hoppers++;
                }

                ChunkKey key = ChunkKey.of(w, c.getX(), c.getZ());
                chunkSnapshots.add(new ChunkSnapshot(key, ents.length, items, xp, proj, vill, armor, veh, fall, tileCount, hoppers));
            }
        }

        chunkSnapshots.sort(Comparator.comparingInt((ChunkSnapshot cs) -> cs.entities).reversed());
        if (chunkSnapshots.size() > topN) chunkSnapshots = new ArrayList<>(chunkSnapshots.subList(0, topN));

        latest.set(new LagMetrics(
                clampTps(tps[0]),
                clampTps(tps[1]),
                clampTps(tps[2]),
                mspt,
                usedMem,
                maxMem,
                players,
                loadedChunks,
                loadedEntities,
                List.copyOf(chunkSnapshots)
        ));
    }

    private static double clampTps(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 20.0;
        return Math.max(0.0, Math.min(20.0, v));
    }

    private double[] readTps() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getTPS");
            Object r = m.invoke(Bukkit.getServer());
            if (r instanceof double[] arr && arr.length >= 3) return new double[]{arr[0], arr[1], arr[2]};
        } catch (Throwable ignored) {
        }
        return new double[]{20.0, 20.0, 20.0};
    }

    private double readMspt() {
        try {
            Method m = Bukkit.getServer().getClass().getMethod("getAverageTickTime");
            Object r = m.invoke(Bukkit.getServer());
            if (r instanceof Double d) return d;
            if (r instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {
        }
        return -1.0;
    }

    private BlockState[] safeTileEntities(Chunk c) {
        try {
            return c.getTileEntities();
        } catch (Throwable t) {
            try {
                Method m = c.getClass().getMethod("getTileEntities", boolean.class);
                Object r = m.invoke(c, false);
                if (r instanceof BlockState[] arr) return arr;
            } catch (Throwable ignored) {
            }
        }
        return new BlockState[0];
    }
}
