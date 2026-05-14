package me.priyme.lagscope.control;

import me.priyme.lagscope.LagMode;
import me.priyme.lagscope.analysis.LagAnalyzer;
import me.priyme.lagscope.analysis.LagMetrics;
import me.priyme.lagscope.data.ChunkSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class LagController {
    private final Plugin plugin;
    private final LagAnalyzer analyzer;
    private final LagClearer cleaner;
    private BukkitTask task;
    private LagMode mode;
    private volatile ModeSettings cachedSettings;
    
    private long lastWarningTime = 0;

    public LagController(Plugin plugin, LagAnalyzer analyzer) {
        this.plugin = plugin;
        this.analyzer = analyzer;
        this.cleaner = new LagClearer(plugin);
        this.mode = LagMode.parse(plugin.getConfig().getString("mode", "MEDIUM"));
        this.cachedSettings = ModeSettings.fromConfig(plugin, this.mode);
    }

    public LagMode getMode() {
        return mode;
    }

    public void setMode(LagMode mode) {
        if (mode == null) return;
        if (this.mode != mode) {
            this.mode = mode;
            plugin.getConfig().set("mode", mode.name());
            plugin.saveConfig();
        }
        this.cachedSettings = ModeSettings.fromConfig(plugin, this.mode);
    }

    public void reload() {
        plugin.reloadConfig();
        this.mode = LagMode.parse(plugin.getConfig().getString("mode", "MEDIUM"));
        this.cachedSettings = ModeSettings.fromConfig(plugin, this.mode);
    }

    public LagClearer cleaner() {
        return cleaner;
    }

    public void start() {
        stop();
        boolean enabled = plugin.getConfig().getBoolean("auto.enabled", true);
        if (!enabled) return;
        
        // Task läuft häufiger (alle 5 Sekunden), um Lags schneller zu erkennen
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (mode == LagMode.OFF) return;

        LagMetrics m = analyzer.getLatest();
        double tpsTrigger = plugin.getConfig().getDouble("auto.tps-trigger", 18.5);
        double msptTrigger = plugin.getConfig().getDouble("auto.mspt-trigger", 45.0);

        boolean isLagging = m.tps1 < tpsTrigger || (m.mspt > 0 && m.mspt > msptTrigger);

        if (isLagging) {
            notifyAdmins(m);
            executeCleanup(m);
        }
    }

    private void notifyAdmins(LagMetrics m) {
        long now = System.currentTimeMillis();
        if (now - lastWarningTime < 60000) return; // Nur alle 60 Sekunden warnen

        String reason = determineLagReason(m);
        String message = ChatColor.RED + "⚠ [LagScope] TPS Drop erkannt! (" + String.format("%.1f", m.tps1) + ")\n" 
                       + ChatColor.YELLOW + "» Ursache: " + ChatColor.WHITE + reason;

        Bukkit.broadcast(message, "lagscope.notify");
        lastWarningTime = now;
    }

    private String determineLagReason(LagMetrics m) {
        if (!m.topChunks.isEmpty()) {
            ChunkSnapshot worstChunk = m.topChunks.get(0);
            if (worstChunk.entities > 400) {
                return "Extreme Entity-Dichte (" + worstChunk.entities + " Entities) in Chunk " 
                        + worstChunk.key.x + "," + worstChunk.key.z + " (" + worstChunk.key.world + ")";
            }
            if (worstChunk.tileEntities > 300) {
                return "Massive TileEntities (" + worstChunk.tileEntities + ") in Chunk " 
                        + worstChunk.key.x + "," + worstChunk.key.z + ". Mögliche Redstone/Hopper-Clock!";
            }
        }
        
        double memoryUsagePercent = (double) m.usedMemory / m.maxMemory * 100;
        if (memoryUsagePercent > 90.0) {
            return "RAM fast voll! (" + String.format("%.1f", memoryUsagePercent) + "%)";
        }

        if (m.loadedEntities > 5000) {
            return "Zu viele globale Entities geladen (" + m.loadedEntities + ").";
        }

        return "Unbekannt (Wahrscheinlich große World-Generation oder schweres Plugin).";
    }

    private void executeCleanup(LagMetrics m) {
        ModeSettings s = cachedSettings;
        if (s.clearItemThresholdPerWorld > 0) {
            for (World w : Bukkit.getWorlds()) {
                if (countItemsAtLeast(w, s.clearItemThresholdPerWorld)) {
                    cleaner.clearWorld(w, s.aggressive);
                }
            }
        }

        for (ChunkSnapshot cs : m.topChunks) {
            if (s.chunkClearEntityThreshold > 0 && cs.entities >= s.chunkClearEntityThreshold) {
                World w = Bukkit.getWorld(cs.key.world);
                if (w != null) {
                    cleaner.clearChunk(w.getChunkAt(cs.key.x, cs.key.z), s.aggressive);
                }
            }
            if (s.chunkClearTileThreshold > 0 && cs.tileEntities >= 0 && cs.tileEntities >= s.chunkClearTileThreshold) {
                World w = Bukkit.getWorld(cs.key.world);
                if (w != null) {
                    cleaner.clearChunk(w.getChunkAt(cs.key.x, cs.key.z), s.aggressive);
                }
            }
        }
    }

    public ModeSettings currentSettings() {
        return cachedSettings;
    }

    private static boolean countItemsAtLeast(World w, int threshold) {
        if (threshold <= 0) return false;
        int items = 0;
        for (Entity e : w.getEntities()) {
            if (e instanceof org.bukkit.entity.Item) {
                items++;
                if (items >= threshold) return true;
            }
        }
        return false;
    }

    public static final class ModeSettings {
        public final boolean aggressive;
        public final int clearItemThresholdPerWorld;
        public final int chunkClearEntityThreshold;
        public final int chunkClearTileThreshold;

        public final boolean limitNaturalSpawns;
        public final boolean limitSpawnerSpawns;
        public final int maxMonstersPerChunk;
        public final int maxAnimalsPerChunk;
        public final int maxVillagersPerChunk;

        public final int maxItemsPerChunk;
        public final int maxXpOrbsPerChunk;
        public final int maxProjectilesPerChunk;

        public final int maxPrimedTntPerChunk;
        public final int maxExplosionsPerSecondGlobal;
        public final int maxArrowsPerPlayerPerSecond;
        public final int maxTridentsPerPlayerPerSecond;

        public ModeSettings(
                boolean aggressive,
                int clearItemThresholdPerWorld,
                int chunkClearEntityThreshold,
                int chunkClearTileThreshold,
                boolean limitNaturalSpawns,
                boolean limitSpawnerSpawns,
                int maxMonstersPerChunk,
                int maxAnimalsPerChunk,
                int maxVillagersPerChunk,
                int maxItemsPerChunk,
                int maxXpOrbsPerChunk,
                int maxProjectilesPerChunk,
                int maxPrimedTntPerChunk,
                int maxExplosionsPerSecondGlobal,
                int maxArrowsPerPlayerPerSecond,
                int maxTridentsPerPlayerPerSecond
        ) {
            this.aggressive = aggressive;
            this.clearItemThresholdPerWorld = clearItemThresholdPerWorld;
            this.chunkClearEntityThreshold = chunkClearEntityThreshold;
            this.chunkClearTileThreshold = chunkClearTileThreshold;

            this.limitNaturalSpawns = limitNaturalSpawns;
            this.limitSpawnerSpawns = limitSpawnerSpawns;
            this.maxMonstersPerChunk = maxMonstersPerChunk;
            this.maxAnimalsPerChunk = maxAnimalsPerChunk;
            this.maxVillagersPerChunk = maxVillagersPerChunk;

            this.maxItemsPerChunk = maxItemsPerChunk;
            this.maxXpOrbsPerChunk = maxXpOrbsPerChunk;
            this.maxProjectilesPerChunk = maxProjectilesPerChunk;

            this.maxPrimedTntPerChunk = maxPrimedTntPerChunk;
            this.maxExplosionsPerSecondGlobal = maxExplosionsPerSecondGlobal;
            this.maxArrowsPerPlayerPerSecond = maxArrowsPerPlayerPerSecond;
            this.maxTridentsPerPlayerPerSecond = maxTridentsPerPlayerPerSecond;
        }

        public static ModeSettings fromConfig(Plugin plugin, LagMode mode) {
            String base = "modes." + mode.name() + ".";
            boolean aggressive = plugin.getConfig().getBoolean(base + "aggressive", mode == LagMode.HIGH || mode == LagMode.EXTREME);
            int itemTh = plugin.getConfig().getInt(base + "clear-item-threshold-per-world", 0);
            int entTh = plugin.getConfig().getInt(base + "chunk-clear-entity-threshold", 0);
            int tileTh = plugin.getConfig().getInt(base + "chunk-clear-tile-threshold", 0);

            boolean limitNatural = plugin.getConfig().getBoolean(base + "enforce.limit-natural-spawns", true);
            boolean limitSpawner = plugin.getConfig().getBoolean(base + "enforce.limit-spawner-spawns", false);
            int monsters = plugin.getConfig().getInt(base + "enforce.max-monsters-per-chunk", 0);
            int animals = plugin.getConfig().getInt(base + "enforce.max-animals-per-chunk", 0);
            int villagers = plugin.getConfig().getInt(base + "enforce.max-villagers-per-chunk", 0);

            int items = plugin.getConfig().getInt(base + "enforce.max-items-per-chunk", 0);
            int xp = plugin.getConfig().getInt(base + "enforce.max-xp-orbs-per-chunk", 0);
            int proj = plugin.getConfig().getInt(base + "enforce.max-projectiles-per-chunk", 0);

            int tnt = plugin.getConfig().getInt(base + "enforce.max-primed-tnt-per-chunk", 0);
            int explosions = plugin.getConfig().getInt(base + "enforce.max-explosions-per-second-global", 0);
            int arrows = plugin.getConfig().getInt(base + "enforce.max-arrows-per-player-per-second", 0);
            int tridents = plugin.getConfig().getInt(base + "enforce.max-tridents-per-player-per-second", 0);

            return new ModeSettings(
                    aggressive,
                    itemTh,
                    entTh,
                    tileTh,
                    limitNatural,
                    limitSpawner,
                    monsters,
                    animals,
                    villagers,
                    items,
                    xp,
                    proj,
                    tnt,
                    explosions,
                    arrows,
                    tridents
            );
        }
    }
}
