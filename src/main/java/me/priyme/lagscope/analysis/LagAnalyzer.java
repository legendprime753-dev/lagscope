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
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReference;

public final class LagAnalyzer {
    private static final BlockState[] EMPTY_TILES = new BlockState[0];
    private final Plugin plugin;
    private BukkitTask task;
    private final AtomicReference<LagMetrics> latest = new AtomicReference<>();
    private Method tpsMethod;
    private boolean tpsMethodChecked;
    private Method msptMethod;
    private boolean msptMethodChecked;
    private Method tileEntitiesMethod;
    private boolean tileEntitiesMethodChecked;

    private int chunksPerTick;
    private int refreshTicks;
    private int waitTicks;
    private int cycleTicks;
    private ScanState scanState;

    public LagAnalyzer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int refresh = Math.max(1, plugin.getConfig().getInt("analysis.refresh-seconds", 5));
        stop();
        boolean incremental = plugin.getConfig().getBoolean("analysis.incremental.enabled", true);
        if (incremental) {
            chunksPerTick = Math.max(1, plugin.getConfig().getInt("analysis.incremental.chunks-per-tick", 40));
            refreshTicks = Math.max(1, refresh) * 20;
            waitTicks = 0;
            cycleTicks = 0;
            scanState = null;
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickIncremental, 1L, 1L);
        } else {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::runScan, 20L, 20L * refresh);
            runScan();
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        scanState = null;
    }

    public LagMetrics getLatest() {
        LagMetrics m = latest.get();
        if (m == null) return new LagMetrics(20.0, 20.0, 20.0, -1.0, 0L, 0L, Bukkit.getOnlinePlayers().size(), 0, 0, List.of());
        return m;
    }

    private void runScan() {
        ScanState state = createScanState();
        for (World w : state.worlds) {
            for (Chunk c : w.getLoadedChunks()) {
                processChunk(c, state);
            }
        }
        publish(state);
    }

    private void tickIncremental() {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (scanState == null) {
            scanState = createScanState();
            cycleTicks = 0;
        }
        cycleTicks++;
        int processed = 0;
        while (processed < chunksPerTick) {
            if (!scanState.processNextChunk()) {
                publish(scanState);
                scanState = null;
                waitTicks = Math.max(0, refreshTicks - cycleTicks);
                return;
            }
            processed++;
        }
        cycleTicks++;
    }

    private ScanState createScanState() {
        boolean incNether = plugin.getConfig().getBoolean("analysis.include-nether", true);
        boolean incEnd = plugin.getConfig().getBoolean("analysis.include-the-end", true);
        List<World> worlds = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            if (!incNether && w.getEnvironment() == World.Environment.NETHER) continue;
            if (!incEnd && w.getEnvironment() == World.Environment.THE_END) continue;
            worlds.add(w);
        }

        int topN = Math.max(1, plugin.getConfig().getInt("analysis.top-chunks", 12));
        boolean includeTiles = plugin.getConfig().getBoolean("analysis.count-tile-entities", true);
        boolean includeHoppers = includeTiles && plugin.getConfig().getBoolean("analysis.count-hoppers", true);
        return new ScanState(worlds, topN, includeTiles, includeHoppers);
    }

    private void processChunk(Chunk c, ScanState state) {
        state.loadedChunks++;
        Entity[] ents = c.getEntities();
        int entityCount = ents.length;
        state.loadedEntities += entityCount;

        boolean candidate = state.topChunks.size() < state.topN || (state.topChunks.peek() != null && entityCount > state.topChunks.peek().entities);
        if (!candidate) return;

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

        int tileCount = -1;
        int hoppers = -1;
        if (state.includeTiles) {
            BlockState[] tiles = safeTileEntities(c);
            tileCount = tiles.length;
            if (state.includeHoppers) {
                int hopperCount = 0;
                for (BlockState bs : tiles) {
                    if (bs instanceof Hopper) hopperCount++;
                }
                hoppers = hopperCount;
            }
        }

        ChunkKey key = ChunkKey.of(c.getWorld(), c.getX(), c.getZ());
        ChunkSnapshot snapshot = new ChunkSnapshot(key, entityCount, items, xp, proj, vill, armor, veh, fall, tileCount, hoppers);
        if (state.topChunks.size() < state.topN) {
            state.topChunks.add(snapshot);
        } else {
            state.topChunks.poll();
            state.topChunks.add(snapshot);
        }
    }

    private void publish(ScanState state) {
        double[] tps = readTps();
        double mspt = readMspt();

        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory();
        long usedMem = rt.totalMemory() - rt.freeMemory();

        int players = Bukkit.getOnlinePlayers().size();

        List<ChunkSnapshot> chunkSnapshots = new ArrayList<>(state.topChunks);
        chunkSnapshots.sort(Comparator.comparingInt((ChunkSnapshot cs) -> cs.entities).reversed());

        latest.set(new LagMetrics(
                clampTps(tps[0]),
                clampTps(tps[1]),
                clampTps(tps[2]),
                mspt,
                usedMem,
                maxMem,
                players,
                state.loadedChunks,
                state.loadedEntities,
                List.copyOf(chunkSnapshots)
        ));
    }

    private final class ScanState {
        private final List<World> worlds;
        private final int topN;
        private final boolean includeTiles;
        private final boolean includeHoppers;
        private final PriorityQueue<ChunkSnapshot> topChunks;
        private int worldIndex;
        private Chunk[] chunks;
        private int chunkIndex;
        private int loadedChunks;
        private int loadedEntities;

        private ScanState(List<World> worlds, int topN, boolean includeTiles, boolean includeHoppers) {
            this.worlds = worlds;
            this.topN = topN;
            this.includeTiles = includeTiles;
            this.includeHoppers = includeHoppers;
            this.topChunks = new PriorityQueue<>(Comparator.comparingInt((ChunkSnapshot cs) -> cs.entities));
        }

        private boolean processNextChunk() {
            while (worldIndex < worlds.size()) {
                if (chunks == null || chunkIndex >= chunks.length) {
                    World w = worlds.get(worldIndex++);
                    chunks = w.getLoadedChunks();
                    chunkIndex = 0;
                    if (chunks.length == 0) continue;
                }
                Chunk c = chunks[chunkIndex++];
                processChunk(c, this);
                return true;
            }
            return false;
        }
    }

    private static double clampTps(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 20.0;
        return Math.max(0.0, Math.min(20.0, v));
    }

    private double[] readTps() {
        if (!tpsMethodChecked) {
            tpsMethodChecked = true;
            try {
                tpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            } catch (Throwable ignored) {
                tpsMethod = null;
            }
        }
        if (tpsMethod != null) {
            try {
                Object r = tpsMethod.invoke(Bukkit.getServer());
                if (r instanceof double[] arr && arr.length >= 3) return new double[]{arr[0], arr[1], arr[2]};
            } catch (Throwable ignored) {
            }
        }
        return new double[]{20.0, 20.0, 20.0};
    }

    private double readMspt() {
        if (!msptMethodChecked) {
            msptMethodChecked = true;
            try {
                msptMethod = Bukkit.getServer().getClass().getMethod("getAverageTickTime");
            } catch (Throwable ignored) {
                msptMethod = null;
            }
        }
        if (msptMethod != null) {
            try {
                Object r = msptMethod.invoke(Bukkit.getServer());
                if (r instanceof Double d) return d;
                if (r instanceof Number n) return n.doubleValue();
            } catch (Throwable ignored) {
            }
        }
        return -1.0;
    }

    private BlockState[] safeTileEntities(Chunk c) {
        try {
            return c.getTileEntities();
        } catch (Throwable t) {
            if (!tileEntitiesMethodChecked) {
                tileEntitiesMethodChecked = true;
                try {
                    tileEntitiesMethod = c.getClass().getMethod("getTileEntities", boolean.class);
                } catch (Throwable ignored) {
                    tileEntitiesMethod = null;
                }
            }
            if (tileEntitiesMethod != null) {
                try {
                    Object r = tileEntitiesMethod.invoke(c, false);
                    if (r instanceof BlockState[] arr) return arr;
                } catch (Throwable ignored) {
                }
            }
        }
        return EMPTY_TILES;
    }
}
