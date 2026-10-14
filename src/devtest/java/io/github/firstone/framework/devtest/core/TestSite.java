package io.github.firstone.framework.devtest.core;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The area of the test world where tests build, and helpers to build in it
 *
 * <p>Coordinates are relative to the site's origin: {@code y = 0} is the first block above the floor, the floor
 * itself is {@code y = -1}. The site spans x and z from {@value #MIN} to {@value #MAX}; before every test it is
 * cleared to an empty stone floor ({@link #reset}).</p>
 */
public final class TestSite {

    /** The site's origin in the world (on top of the flat world's grass) */
    public static final BlockPos ORIGIN = new BlockPos(0, -60, 0);

    /** Lowest x and z of the site */
    public static final int MIN = -16;

    /** Highest x and z of the site */
    public static final int MAX = 128;

    /** Height cleared above the floor */
    private static final int HEIGHT = 16;

    private final TestContext t;

    TestSite(TestContext t) {
        this.t = t;
    }

    /** Clears the whole site: air above, a stone floor */
    void reset() {
        fill(MIN, 0, MIN, MAX, HEIGHT, MAX, "air");
        floor(MIN, MIN, MAX, MAX, "stone");
    }

    /**
     * Fills a box with a block, like {@code /fill} (any size; a box that is already filled is fine)
     *
     * @param x1    first corner x
     * @param y1    first corner y
     * @param z1    first corner z
     * @param x2    second corner x
     * @param y2    second corner y
     * @param z2    second corner z
     * @param block the block, as in commands (e.g. {@code packed_ice}, {@code snow[layers=1]})
     */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        this.t.onServer(level -> {
            BlockState state;
            try {
                state = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), block, false)
                    .blockState();
            } catch (CommandSyntaxException e) {
                throw new TestFailure("Unknown block '" + block + "': " + e.getMessage());
            }
            List<BlockPos> changed = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(x1, y1, z1), ORIGIN.offset(x2, y2, z2))) {
                // like /fill: tell the clients, then update the neighbors once everything is placed
                if (level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
                    changed.add(pos.immutable());
                }
            }
            for (BlockPos pos : changed) {
                level.blockUpdated(pos, state.getBlock());
            }
            return null;
        });
    }

    /**
     * Sets one block
     *
     * @param x     x
     * @param y     y
     * @param z     z
     * @param block the block, as in commands
     */
    public void set(int x, int y, int z, String block) {
        fill(x, y, z, x, y, z, block);
    }

    /**
     * Replaces the floor ({@code y = -1}) of a rectangle, e.g. with ice
     *
     * @param x1    first corner x
     * @param z1    first corner z
     * @param x2    second corner x
     * @param z2    second corner z
     * @param block the block
     */
    public void floor(int x1, int z1, int x2, int z2, String block) {
        fill(x1, -1, z1, x2, -1, z2, block);
    }

    /**
     * Makes a pool one block deep, sunk into the floor: its water's surface is about 0.11 below {@code y = 0}, and
     * the floor around it is its rim
     *
     * @param x1 first corner x
     * @param z1 first corner z
     * @param x2 second corner x
     * @param z2 second corner z
     */
    public void pool(int x1, int z1, int x2, int z2) {
        fill(x1, -2, z1, x2, -2, z2, "stone");
        floor(x1, z1, x2, z2, "water");
    }

    /**
     * Builds a stone wall on the floor
     *
     * @param x1     first corner x
     * @param z1     first corner z
     * @param x2     second corner x
     * @param z2     second corner z
     * @param height height in blocks
     */
    public void wall(int x1, int z1, int x2, int z2, int height) {
        fill(x1, 0, z1, x2, height - 1, z2, "stone");
    }
}
