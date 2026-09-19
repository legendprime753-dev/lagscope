package me.priyme.lagscope.control;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Vehicle;
import org.bukkit.plugin.Plugin;

public final class LagClearer {
    private final Plugin plugin;

    public LagClearer(Plugin plugin) {
        this.plugin = plugin;
    }

    public ClearResult clearAllWorlds(boolean aggressive) {
        ClearResult total = new ClearResult();
        for (World w : plugin.getServer().getWorlds()) {
            ClearResult r = clearWorld(w, aggressive);
            total.items += r.items;
            total.xpOrbs += r.xpOrbs;
            total.projectiles += r.projectiles;
            total.fallingBlocks += r.fallingBlocks;
            total.vehicles += r.vehicles;
            total.armorStands += r.armorStands;
        }
        return total;
    }

    public ClearResult clearWorld(World world, boolean aggressive) {
        ClearResult res = new ClearResult();
        for (Entity e : world.getEntities()) {
            if (removeEntity(e, aggressive, null, -1)) tally(res, e);
        }
        return res;
    }

    public ClearResult clearChunk(Chunk chunk, boolean aggressive) {
        ClearResult res = new ClearResult();
        for (Entity e : chunk.getEntities()) {
            if (removeEntity(e, aggressive, null, -1)) tally(res, e);
        }
        return res;
    }

    public ClearResult clearRadius(Location center, double radius, boolean aggressive) {
        ClearResult res = new ClearResult();
        double r2 = radius * radius;
        for (Entity e : center.getWorld().getEntities()) {
            if (e.getLocation().distanceSquared(center) <= r2) {
                if (removeEntity(e, aggressive, center, r2)) tally(res, e);
            }
        }
        return res;
    }

    private boolean removeEntity(Entity e, boolean aggressive, Location center, double r2) {
        boolean removeItems = plugin.getConfig().getBoolean("clear.remove-items", true);
        boolean removeXp = plugin.getConfig().getBoolean("clear.remove-xp-orbs", true);
        boolean removeProj = plugin.getConfig().getBoolean("clear.remove-projectiles", true);
        boolean removeTnt = plugin.getConfig().getBoolean("clear.remove-primed-tnt", true);
        boolean removeFalling = plugin.getConfig().getBoolean("clear.remove-falling-blocks", false);
        boolean removeVehicles = plugin.getConfig().getBoolean("clear.remove-vehicles", false);
        boolean keepNamed = plugin.getConfig().getBoolean("clear.keep-named-entities", true);
        boolean keepTamed = plugin.getConfig().getBoolean("clear.keep-tamed", true);
        boolean keepArms = plugin.getConfig().getBoolean("clear.keep-armorstands-with-arms", true);

        if (keepNamed && e.customName() != null) return false;
        if (keepTamed && e instanceof Tameable t && t.isTamed()) return false;

        if (e instanceof Item) {
            if (!removeItems) return false;
            e.remove();
            return true;
        }
        if (e instanceof ExperienceOrb) {
            if (!removeXp) return false;
            e.remove();
            return true;
        }
        if (e instanceof Projectile) {
            if (!removeProj) return false;
            e.remove();
            return true;
        }
        if (e instanceof TNTPrimed) {
            if (!removeTnt) return false;
            e.remove();
            return true;
        }
        if (e instanceof FallingBlock) {
            if (!removeFalling) return false;
            e.remove();
            return true;
        }
        if (e instanceof Vehicle) {
            if (!removeVehicles) return false;
            e.remove();
            return true;
        }
        if (aggressive && e instanceof ArmorStand as) {
            if (keepArms && as.hasArms()) return false;
            as.remove();
            return true;
        }
        return false;
    }

    private void tally(ClearResult res, Entity e) {
        if (e instanceof Item) res.items++;
        else if (e instanceof ExperienceOrb) res.xpOrbs++;
        else if (e instanceof Projectile) res.projectiles++;
        else if (e instanceof FallingBlock) res.fallingBlocks++;
        else if (e instanceof Vehicle) res.vehicles++;
        else if (e instanceof ArmorStand) res.armorStands++;
    }
}
