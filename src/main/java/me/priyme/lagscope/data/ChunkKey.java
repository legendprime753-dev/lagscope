package me.priyme.lagscope.data;

import org.bukkit.World;

import java.util.Objects;

public final class ChunkKey {
    public final String world;
    public final int x;
    public final int z;

    public ChunkKey(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public static ChunkKey of(World w, int x, int z) {
        return new ChunkKey(w.getName(), x, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkKey ck)) return false;
        return x == ck.x && z == ck.z && Objects.equals(world, ck.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + z;
    }
}
