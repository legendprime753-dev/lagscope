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

import java.util.Collection;

public final class LagClearer {
    private final Plugin plugin;

    public LagClearer(Plugin plugin) {
        this.plugin = plugin;
    }

    public ClearResult clearAllWorlds(boolean aggressive) {
        ClearPolicy policy = ClearPolicy.fromConfig(plugin, aggressive);
        ClearResult total = new ClearResult();
        for (World w : plugin.getServer().getWorlds()) {
            ClearResult r = clearWorldInner(w, policy);
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
        return clearWorldInner(world, ClearPolicy.fromConfig(plugin, aggressive));
    }

    private ClearResult clearWorldInner(World world, ClearPolicy policy) {
        ClearResult res = new ClearResult();
        
        // PAPER API: getEntitiesByClasses ist extrem optimiert, da Paper Entitäten nach Typ filtert
        // Wir fragen nur Entitäten ab, die überhaupt für einen Clear infrage kommen. Mobs und Spieler werden komplett ignoriert.
        Collection<Entity> targets = world.getEntitiesByClasses(
                Item.class, ExperienceOrb.class, Projectile.class, 
                TNTPrimed.class, FallingBlock.class, Vehicle.class, ArmorStand.class
        );

        for (Entity e : targets) {
            if (removeEntity(e, policy)) tally(res, e);
        }
        return res;
    }

    public ClearResult clearChunk(Chunk chunk, boolean aggressive) {
        ClearPolicy policy = ClearPolicy.fromConfig(plugin, aggressive);
        ClearResult res = new ClearResult();
        for (Entity e : chunk.getEntities()) {
            if (removeEntity(e, policy)) tally(res, e);
        }
        return res;
    }

    public ClearResult clearRadius(Location center, double radius, boolean aggressive) {
        if (center == null || center.getWorld() == null) return new ClearResult();
        ClearPolicy policy = ClearPolicy.fromConfig(plugin, aggressive);
        ClearResult res = new ClearResult();
        
        // PAPER API: Nutzt das optimierte Spatial-Hashing von Paper, anstatt alle Entities der Welt zu berechnen
        Collection<Entity> targets = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        
        for (Entity e : targets) {
            if (removeEntity(e, policy)) tally(res, e);
        }
        return res;
    }

    private boolean removeEntity(Entity e, ClearPolicy policy) {
        if (policy.keepNamed && e.customName() != null) return false;
        if (policy.keepTamed && e instanceof Tameable t && t.isTamed()) return false;

        if (e instanceof Item) {
            if (!policy.removeItems) return false;
            e.remove();
            return true;
        }
        if (e instanceof ExperienceOrb) {
            if (!policy.removeXp) return false;
            e.remove();
            return true;
        }
        if (e instanceof Projectile) {
            if (!policy.removeProj) return false;
            e.remove();
            return true;
        }
        if (e instanceof TNTPrimed) {
            if (!policy.removeTnt) return false;
            e.remove();
            return true;
        }
        if (e instanceof FallingBlock) {
            if (!policy.removeFalling) return false;
            e.remove();
            return true;
        }
        if (e instanceof Vehicle) {
            if (!policy.removeVehicles) return false;
            e.remove();
            return true;
        }
        if (policy.aggressive && e instanceof ArmorStand as) {
            if (policy.keepArms && as.hasArms()) return false;
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

    private static final class ClearPolicy {
        final boolean aggressive;
        final boolean removeItems;
        final boolean removeXp;
        final boolean removeProj;
        final boolean removeTnt;
        final boolean removeFalling;
        final boolean removeVehicles;
        final boolean keepNamed;
        final boolean keepTamed;
        final boolean keepArms;

        private ClearPolicy(
                boolean aggressive,
                boolean removeItems,
                boolean removeXp,
                boolean removeProj,
                boolean removeTnt,
                boolean removeFalling,
                boolean removeVehicles,
                boolean keepNamed,
                boolean keepTamed,
                boolean keepArms
        ) {
            this.aggressive = aggressive;
            this.removeItems = removeItems;
            this.removeXp = removeXp;
            this.removeProj = removeProj;
            this.removeTnt = removeTnt;
            this.removeFalling = removeFalling;
            this.removeVehicles = removeVehicles;
            this.keepNamed = keepNamed;
            this.keepTamed = keepTamed;
            this.keepArms = keepArms;
        }

        static ClearPolicy fromConfig(Plugin plugin, boolean aggressive) {
            return new ClearPolicy(
                    aggressive,
                    plugin.getConfig().getBoolean("clear.remove-items", true),
                    plugin.getConfig().getBoolean("clear.remove-xp-orbs", true),
                    plugin.getConfig().getBoolean("clear.remove-projectiles", true),
                    plugin.getConfig().getBoolean("clear.remove-primed-tnt", true),
                    plugin.getConfig().getBoolean("clear.remove-falling-blocks", false),
                    plugin.getConfig().getBoolean("clear.remove-vehicles", false),
                    plugin.getConfig().getBoolean("clear.keep-named-entities", true),
                    plugin.getConfig().getBoolean("clear.keep-tamed", true),
                    plugin.getConfig().getBoolean("clear.keep-armorstands-with-arms", true)
            );
        }
    }
}
