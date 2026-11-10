package com.bedrockmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class Box {
    public final ServerLevel level;
    public final int cx, y0, cz;      // y0 = floor level
    public int size, height;
    public BlockState wall = Blocks.GLASS.defaultBlockState();
    public BlockState fill = Blocks.DIRT.defaultBlockState();
    public BlockState layerBlock = Blocks.STONE.defaultBlockState();
    public int layerCount = 0;

    private static final int FLAGS = 2;

    public Box(ServerLevel level, int cx, int y0, int cz, int size, int height) {
        this.level = level; this.cx = cx; this.y0 = y0; this.cz = cz;
        this.size = size; this.height = height;
    }

    public int x0() { return cx - size / 2; }
    public int x1() { return x0() + size - 1; }
    public int z0() { return cz - size / 2; }
    public int z1() { return z0() + size - 1; }
    public int top() { return y0 + height; }

    public boolean inRing(BlockPos p) {
        return p.getX() >= x0() - 1 && p.getX() <= x1() + 1 && p.getZ() >= z0() - 1 && p.getZ() <= z1() + 1;
    }
    public boolean isInterior(BlockPos p) {
        return p.getX() >= x0() && p.getX() <= x1() && p.getZ() >= z0() && p.getZ() <= z1()
                && p.getY() > y0 && p.getY() <= top();
    }
    public boolean isFrame(BlockPos p) {
        return inRing(p) && p.getY() >= y0 && p.getY() <= top() && !isInterior(p);
    }
    public boolean aboveBox(BlockPos p) { return inRing(p) && p.getY() > top(); }

    public void buildFrame() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0() - 1; x <= x1() + 1; x++)
            for (int z = z0() - 1; z <= z1() + 1; z++)
                for (int y = y0; y <= top(); y++) {
                    m.set(x, y, z);
                    if (isFrame(m) && !level.getBlockState(m).equals(wall)) level.setBlock(m, wall, FLAGS);
                }
    }

    public void clearFrame() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0() - 1; x <= x1() + 1; x++)
            for (int z = z0() - 1; z <= z1() + 1; z++)
                for (int y = y0; y <= top(); y++) {
                    m.set(x, y, z);
                    if (isFrame(m)) level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                }
    }

    public void clearInterior() {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0(); x <= x1(); x++)
            for (int z = z0(); z <= z1(); z++)
                for (int y = y0 + 1; y <= top(); y++) {
                    m.set(x, y, z);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                }
    }

    public void resize(int newSize, int newHeight) {
        newSize = Math.max(3, Math.min(63, newSize));
        newHeight = Math.max(9, Math.min(21, newHeight));
        int ox0 = x0() - 1, ox1 = x1() + 1, oz0 = z0() - 1, oz1 = z1() + 1, otop = top();
        clearFrame();
        size = newSize; height = newHeight;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = ox0; x <= ox1; x++)
            for (int z = oz0; z <= oz1; z++)
                for (int y = y0 + 1; y <= otop; y++) {
                    m.set(x, y, z);
                    if (!isInterior(m) && !level.getBlockState(m).isAir())
                        level.setBlock(m, Blocks.AIR.defaultBlockState(), FLAGS);
                }
        buildFrame();
    }

    private BlockState stateForRow(int dy) { return dy <= layerCount ? layerBlock : fill; }

    public int fillRows(int rows, boolean replace) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= Math.min(rows, height); dy++) {
            BlockState s = stateForRow(dy);
            for (int x = x0(); x <= x1(); x++)
                for (int z = z0(); z <= z1(); z++) {
                    m.set(x, y0 + dy, z);
                    if (replace || level.getBlockState(m).isAir()) { level.setBlock(m, s, FLAGS); n++; }
                }
        }
        return n;
    }

    public int fillCount(int count, boolean replace) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= height; dy++) {
            BlockState s = stateForRow(dy);
            for (int x = x0(); x <= x1(); x++)
                for (int z = z0(); z <= z1(); z++) {
                    if (n >= count) return n;
                    m.set(x, y0 + dy, z);
                    if (replace || level.getBlockState(m).isAir()) { level.setBlock(m, s, FLAGS); n++; }
                }
        }
        return n;
    }
}
