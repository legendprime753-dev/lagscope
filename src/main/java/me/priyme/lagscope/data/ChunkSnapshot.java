package me.priyme.lagscope.data;

public final class ChunkSnapshot {
    public final ChunkKey key;
    public final int entities;
    public final int items;
    public final int xpOrbs;
    public final int projectiles;
    public final int villagers;
    public final int armorStands;
    public final int vehicles;
    public final int fallingBlocks;
    public final int tileEntities;
    public final int hoppers;

    public ChunkSnapshot(ChunkKey key, int entities, int items, int xpOrbs, int projectiles, int villagers, int armorStands, int vehicles, int fallingBlocks, int tileEntities, int hoppers) {
        this.key = key;
        this.entities = entities;
        this.items = items;
        this.xpOrbs = xpOrbs;
        this.projectiles = projectiles;
        this.villagers = villagers;
        this.armorStands = armorStands;
        this.vehicles = vehicles;
        this.fallingBlocks = fallingBlocks;
        this.tileEntities = tileEntities;
        this.hoppers = hoppers;
    }
}
