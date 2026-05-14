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
    
    private long lastWarningTime = 0; // Für den Cooldown

    public LagController(Plugin plugin, LagAnalyzer analyzer) {
        this.plugin = plugin;
        this.analyzer = analyzer;
        this.cleaner = new LagClearer(plugin);
        this.mode = LagMode.parse(plugin.getConfig().getString("mode", "MEDIUM"));
        this.cachedSettings = ModeSettings.fromConfig(plugin, this.mode);
    }

    public LagMode getMode() { return mode; }
    public LagClearer cleaner() { return cleaner; }

    public void setMode(LagMode mode) {
        if (mode == null || this.mode == mode) return;
        this.mode = mode;
        plugin.getConfig().set("mode", mode.name());
        plugin.saveConfig();
        this.cachedSettings = ModeSettings.fromConfig(plugin, this.mode);
    }

    public void reload() {
        plugin.reloadConfig();
        this.mode = LagMode.parse(plugin.getConfig().getString("mode", "MEDIUM"));
        this.cachedSettings = ModeSettings.fromConfig(plugin, this.mode);
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("auto.enabled", true)) return;
        int interval = Math.max(10, plugin.getConfig().getInt("auto.interval-seconds", 60));
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

        boolean isLagging = m.tps1 < tpsTrigger || (m.mspt > msptTrigger);

        if (isLagging) {
            notifyAdmins(m); // Warnung senden
            executeCleanup(m); // Lag bekämpfen
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
                if (w != null) cleaner.clearChunk(w.getChunkAt(cs.key.x, cs.key.z), s.aggressive);
            }
            if (s.chunkClearTileThreshold > 0 && cs.tileEntities >= s.chunkClearTileThreshold) {
                World w = Bukkit.getWorld(cs.key.world);
                if (w != null) cleaner.clearChunk(w.getChunkAt(cs.key.x, cs.key.z), s.aggressive);
            }
        }
    }
    
    // ... Die ModeSettings Klasse bleibt unverändert (hier weggelassen für Übersichtlichkeit) ...
    // ... public static final class ModeSettings { ... }
}
