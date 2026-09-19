package me.priyme.lagscope.analysis;
import me.priyme.lagscope.data.ChunkSnapshot;


import java.util.List;

public final class LagMetrics {
    public final double tps1;
    public final double tps5;
    public final double tps15;
    public final double mspt;
    public final long usedMemory;
    public final long maxMemory;
    public final int players;
    public final int loadedChunks;
    public final int loadedEntities;
    public final List<ChunkSnapshot> topChunks;

    public LagMetrics(double tps1, double tps5, double tps15, double mspt, long usedMemory, long maxMemory, int players, int loadedChunks, int loadedEntities, List<ChunkSnapshot> topChunks) {
        this.tps1 = tps1;
        this.tps5 = tps5;
        this.tps15 = tps15;
        this.mspt = mspt;
        this.usedMemory = usedMemory;
        this.maxMemory = maxMemory;
        this.players = players;
        this.loadedChunks = loadedChunks;
        this.loadedEntities = loadedEntities;
        this.topChunks = topChunks;
    }
}
