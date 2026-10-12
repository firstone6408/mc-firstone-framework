package io.github.firstone.framework.features.animatium.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * A 1.7.10 block dust particle ({@code EntityBlockDustFX}), the dust a landing entity throws up
 *
 * <p>It looks like the 1.21.1 block particle ({@link TerrainParticle}: a quarter of the block's particle texture, half
 * size, same gravity, friction and life), but moves like the 1.7.10 one:</p>
 * <ul>
 *   <li>it starts with exactly the given motion (1.21.1 adds a random push and rescales it);</li>
 *   <li>its position is the middle of its box ({@code yOffset = height / 2}), so it rests on the ground instead of
 *       sinking half into it;</li>
 *   <li>it moves like a 1.7.10 entity ({@code Entity.moveEntity}): blocks stop it axis by axis (up and down, then x,
 *       then z) and only the blocked directions lose their speed, so after landing it keeps sliding along the ground.
 *       A 1.21.1 particle stops for good once the ground stops it;</li>
 *   <li>its color is the block's own, without the biome ({@code getRenderColor}; none for grass blocks), and its light
 *       is taken at its own place ({@code Entity.getBrightnessForRender}).</li>
 * </ul>
 */
public final class LegacyBlockDustParticle extends TerrainParticle {

    /** Gray the particle is drawn with before the block's color (1.7.10 and 1.21.1) */
    private static final float GRAY = 0.6F;

    /** Height in the box, as a share of it, where 1.7.10 took an entity's light */
    private static final double LIGHT_HEIGHT = 0.66;

    /**
     * Creates a dust particle of a block with an exact motion
     *
     * @param level the client level
     * @param x     x of the particle's middle
     * @param y     y of the particle's middle
     * @param z     z of the particle's middle
     * @param xd    motion along x
     * @param yd    motion along y
     * @param zd    motion along z
     * @param state the block whose texture it shows
     * @param pos   where the block is
     */
    public LegacyBlockDustParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                   BlockState state, BlockPos pos) {
        super(level, x, y, z, xd, yd, zd, state, pos);
        this.setParticleSpeed(xd, yd, zd);
        if (state.is(Blocks.GRASS_BLOCK)) {
            this.setColor(GRAY, GRAY, GRAY);
        } else {
            int color = Minecraft.getInstance().getBlockColors().getColor(state, null, null, 0);
            this.setColor(GRAY * (color >> 16 & 0xFF) / 255.0F, GRAY * (color >> 8 & 0xFF) / 255.0F,
                GRAY * (color & 0xFF) / 255.0F);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        double halfWidth = this.bbWidth / 2.0;
        double halfHeight = this.bbHeight / 2.0;
        this.setBoundingBox(new AABB(x - halfWidth, y - halfHeight, z - halfWidth, x + halfWidth, y + halfHeight,
            z + halfWidth));
    }

    @Override
    protected void setLocationFromBoundingbox() {
        AABB box = this.getBoundingBox();
        this.x = (box.minX + box.maxX) / 2.0;
        this.y = box.minY + this.bbHeight / 2.0;
        this.z = (box.minZ + box.maxZ) / 2.0;
    }

    @Override
    public void move(double dx, double dy, double dz) {
        AABB box = this.getBoundingBox();
        double x = dx;
        double y = dy;
        double z = dz;
        if (this.hasPhysics && (dx != 0.0 || dy != 0.0 || dz != 0.0)) {
            List<VoxelShape> blocks = new ArrayList<>();
            this.level.getBlockCollisions(null, box.expandTowards(dx, dy, dz)).forEach(blocks::add);
            y = Shapes.collide(Direction.Axis.Y, box, blocks, y);
            box = box.move(0.0, y, 0.0);
            x = Shapes.collide(Direction.Axis.X, box, blocks, x);
            box = box.move(x, 0.0, 0.0);
            z = Shapes.collide(Direction.Axis.Z, box, blocks, z);
            box = box.move(0.0, 0.0, z);
        } else {
            box = box.move(dx, dy, dz);
        }
        this.setBoundingBox(box);
        this.setLocationFromBoundingbox();
        this.onGround = y != dy && dy < 0.0;
        if (x != dx) {
            this.xd = 0.0;
        }
        if (y != dy) {
            this.yd = 0.0;
        }
        if (z != dz) {
            this.zd = 0.0;
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        BlockPos pos = BlockPos.containing(this.x, this.getBoundingBox().minY + this.bbHeight * LIGHT_HEIGHT, this.z);
        boolean loaded = this.level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()));
        return loaded ? LevelRenderer.getLightColor(this.level, pos) : 0;
    }
}
