package me.priyme.lagscope.control;

public final class ClearResult {
    public int items;
    public int xpOrbs;
    public int projectiles;
    public int fallingBlocks;
    public int vehicles;
    public int armorStands;

    public int total() {
        return items + xpOrbs + projectiles + fallingBlocks + vehicles + armorStands;
    }
}
