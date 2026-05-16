package me.priyme.lagscope.analysis;

import me.priyme.lagscope.data.ChunkKey;
import me.priyme.lagscope.data.ChunkSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReference;

public final class LagAnalyzer {
    private final Plugin plugin;
    private BukkitTask task;
    private final AtomicReference<LagMetrics> latest = new AtomicReference<>();

    private int chunksPerTick;
    private int refreshTicks;
    private int waitTicks;
    private int cycleTicks;
    private ScanState scanState;
    
    // Wiederverwendbarer Comparator, um GC-Allokationen bei jedem Sortieren zu vermeiden
    private static final Comparator<ChunkSnapshot> CHUNK_COMPARATOR = 
            Comparator.comparingInt((ChunkSnapshot cs) -> cs.entities).reversed();

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
        if (m == null) return new LagMetrics(20.0, 20.0, 20.0, 0.0, 0L, 0L, Bukkit.getOnlinePlayers().size(), 0, 0, Collections.emptyList());
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

        // Fast-Exit
        boolean isFull = state.topChunks.size() >= state.topN;
        if (isFull && state.topChunks.peek() != null && entityCount <= state.topChunks.peek().entities) {
            return; 
        }

        int items = 0, xp = 0, proj = 0, vill = 0, armor = 0, veh = 0, fall = 0;

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
            // PAPER API: Schneller Zugriff auf TileEntities
            BlockState[] tiles = c.getTileEntities(); 
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
        
        state.topChunks.add(snapshot);
        if (state.topChunks.size() > state.topN) {
            state.topChunks.poll(); 
        }
    }

    private void publish(ScanState state) {
        // PAPER API: Nutzt direkte Server-Metriken statt eigene Berechnungen oder NMS-Hooks
        double[] tps = Bukkit.getServer().getTPS(); 
        double mspt = Bukkit.getServer().getAverageTickTime();

        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory();
        long usedMem = rt.totalMemory() - rt.freeMemory();

        int players = Bukkit.getOnlinePlayers().size();

        List<ChunkSnapshot> chunkSnapshots = new ArrayList<>(state.topChunks);
        chunkSnapshots.sort(CHUNK_COMPARATOR);

        latest.set(new LagMetrics(
                tps[0], tps[1], tps[2], mspt, usedMem, maxMem, players,
                state.loadedChunks, state.loadedEntities, List.copyOf(chunkSnapshots)
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
}
