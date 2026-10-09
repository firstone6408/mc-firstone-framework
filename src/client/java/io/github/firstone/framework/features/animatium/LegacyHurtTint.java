package io.github.firstone.framework.features.animatium;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the red hurt tint the way 1.7.10 did
 *
 * <p>1.7.10 {@code RendererLivingEntity.doRender} drew the model and every render pass (armor, wool…) a second time:
 * texture and lightmap off, color {@code (brightness, 0, 0, 0.4)}, alpha blending, depth test EQUAL (only where the
 * model was drawn), directional lighting on, where {@code brightness} is the block brightness at the entity. Without
 * the lightmap the red stays bright at night. Held items, carried blocks, skulls and the cape were drawn outside that
 * pass and stayed untinted. 1.21.1 instead mixes about 30 % red into each model before the lightmap darkens it.</p>
 *
 * <p>How it is reproduced (render thread only):</p>
 * <ol>
 *   <li>The 1.21.1 overlay red is removed ({@code getOverlayCoords} no longer reports "hurt").</li>
 *   <li>While a hurt entity renders, its buffers are wrapped by {@link #begin}: every entity-model quad (body, layers,
 *       armor) is copied, except while {@link #suspend() suspended} for items, blocks, skulls and similar.</li>
 *   <li>{@link #end} draws one red pass over the copied quads; quads drawn by several layers with the same geometry
 *       (a villager's outfit over its body) are drawn once, so every entity gets a single 40 % red layer.</li>
 * </ol>
 */
public final class LegacyHurtTint {

    /** Opacity of the 1.7.10 red pass ({@code glColor4f(…, 0.4F)}) */
    private static final int RED_ALPHA = Math.round(0.4F * 255.0F);

    /** Plain white texture: 1.7.10 disabled texturing for the red pass */
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    /** Red pass for normal entity render types */
    private static final RenderType BODY_PASS = new PassType("firstone_legacy_hurt", false);

    /** Red pass for render types drawn with the armor depth offset, so the EQUAL depth test matches them */
    private static final RenderType OFFSET_PASS = new PassType("firstone_legacy_hurt_offset", true);

    /** Whether a render type uses the armor depth offset (cached, render types are long-lived) */
    private static final Map<RenderType, Boolean> USES_OFFSET = new IdentityHashMap<>();

    /** Entity whose geometry is being collected, or null */
    private static Capture active;

    /** Greater than 0 while geometry must not be collected (items, blocks, skulls…) */
    private static int suspended;

    private LegacyHurtTint() {}

    /**
     * Tells whether the 1.7.10 hurt tint is used
     *
     * @return true if "Legacy Hurt Tint" is on
     */
    public static boolean enabled() {
        return AnimatiumFeature.getConfig().legacyHurtTint;
    }

    /**
     * Starts collecting a hurt entity and returns the buffers to render it with
     *
     * @param entity  the entity about to be rendered
     * @param buffers the buffers it would be rendered with
     * @return collecting buffers, or {@code buffers} when the entity is not tinted
     */
    public static MultiBufferSource begin(LivingEntity entity, MultiBufferSource buffers) {
        if (active != null && active.entity == entity) {
            active.depth++;
            return buffers;
        }
        if (!enabled() || (entity.hurtTime <= 0 && entity.deathTime <= 0) || entity.isInvisible()) {
            return buffers;
        }
        Capture capture = new Capture(entity, buffers, active);
        active = capture;
        suspended = 0;
        return type -> capture.wrap(type, buffers.getBuffer(type));
    }

    /**
     * Ends collecting an entity and draws its red pass
     *
     * @param entity the entity that finished rendering
     */
    public static void end(LivingEntity entity) {
        Capture capture = active;
        if (capture == null || capture.entity != entity) {
            return;
        }
        if (capture.depth > 0) {
            capture.depth--;
            return;
        }
        active = capture.previous;
        suspended = 0;
        int color = color(entity);
        capture.normal.emit(capture.buffers, BODY_PASS, color);
        capture.offset.emit(capture.buffers, OFFSET_PASS, color);
    }

    /**
     * Stops collecting until {@link #resume()} (used while items, blocks, skulls, capes… are drawn)
     */
    public static void suspend() {
        if (active != null) {
            suspended++;
        }
    }

    /**
     * Undoes one {@link #suspend()}
     */
    public static void resume() {
        if (active != null && suspended > 0) {
            suspended--;
        }
    }

    /**
     * Returns the red pass color: red = 1.7.10 {@code Entity.getBrightness} (block light at 66 % of the entity's
     * height, including night darkening of sky light), alpha = 0.4
     */
    private static int color(LivingEntity entity) {
        Level level = entity.level();
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getBoundingBox().minY + entity.getBbHeight() * 0.66, entity.getZ());
        float brightness = level.isLoaded(pos)
            ? LightTexture.getBrightness(level.dimensionType(), level.getMaxLocalRawBrightness(pos))
            : 0.0F;
        int red = Math.round(Math.min(1.0F, Math.max(0.0F, brightness)) * 255.0F);
        return RED_ALPHA << 24 | red << 16;
    }

    /** True if the render type is drawn with the armor depth offset ({@code view_offset_z_layering}) */
    private static boolean usesOffset(RenderType type) {
        return USES_OFFSET.computeIfAbsent(type, t -> t.toString().contains("view_offset_z_layering"));
    }

    /** Geometry collected for one entity */
    private static final class Capture {
        final LivingEntity entity;
        final MultiBufferSource buffers;
        final Capture previous;
        final Quads normal = new Quads();
        final Quads offset = new Quads();
        int depth;

        Capture(LivingEntity entity, MultiBufferSource buffers, Capture previous) {
            this.entity = entity;
            this.buffers = buffers;
            this.previous = previous;
        }

        VertexConsumer wrap(RenderType type, VertexConsumer target) {
            if (type.format() != DefaultVertexFormat.NEW_ENTITY || type.mode() != VertexFormat.Mode.QUADS) {
                return target;
            }
            return new CopyingConsumer(target, usesOffset(type) ? this.offset : this.normal);
        }
    }

    /** Collected quads: position and normal of each vertex, four vertices per quad */
    private static final class Quads {
        private static final int FLOATS = 6;
        private float[] data = new float[FLOATS * 4 * 64];
        private int vertices;

        void add(float x, float y, float z, float nx, float ny, float nz) {
            if ((this.vertices + 1) * FLOATS > this.data.length) {
                this.data = Arrays.copyOf(this.data, this.data.length * 2);
            }
            int i = this.vertices++ * FLOATS;
            this.data[i] = x;
            this.data[i + 1] = y;
            this.data[i + 2] = z;
            this.data[i + 3] = nx;
            this.data[i + 4] = ny;
            this.data[i + 5] = nz;
        }

        void setLastNormal(float nx, float ny, float nz) {
            if (this.vertices > 0) {
                int i = (this.vertices - 1) * FLOATS;
                this.data[i + 3] = nx;
                this.data[i + 4] = ny;
                this.data[i + 5] = nz;
            }
        }

        /** Draws every quad once; quads with the same corners (several layers on one model) are skipped */
        void emit(MultiBufferSource buffers, RenderType pass, int color) {
            int quads = this.vertices / 4;
            if (quads == 0) {
                return;
            }
            VertexConsumer out = buffers.getBuffer(pass);
            LongOpenHashSet seen = new LongOpenHashSet(quads);
            for (int q = 0; q < quads; q++) {
                int start = q * 4 * FLOATS;
                if (!seen.add(cornersKey(start))) {
                    continue;
                }
                for (int i = start; i < start + 4 * FLOATS; i += FLOATS) {
                    out.addVertex(this.data[i], this.data[i + 1], this.data[i + 2], color, 0.0F, 0.0F,
                        OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, this.data[i + 3], this.data[i + 4], this.data[i + 5]);
                }
            }
        }

        private long cornersKey(int start) {
            long hash = 1125899906842597L;
            for (int i = start; i < start + 4 * FLOATS; i += FLOATS) {
                hash = 31 * hash + Float.floatToIntBits(this.data[i]);
                hash = 31 * hash + Float.floatToIntBits(this.data[i + 1]);
                hash = 31 * hash + Float.floatToIntBits(this.data[i + 2]);
            }
            return hash;
        }
    }

    /** Passes every call to the real buffer and copies vertex positions and normals into {@link Quads} */
    private static final class CopyingConsumer implements VertexConsumer {
        private final VertexConsumer target;
        private final Quads quads;
        private boolean copying;

        CopyingConsumer(VertexConsumer target, Quads quads) {
            this.target = target;
            this.quads = quads;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.target.addVertex(x, y, z);
            this.copying = suspended == 0;
            if (this.copying) {
                this.quads.add(x, y, z, 0.0F, 1.0F, 0.0F);
            }
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            this.target.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
            this.copying = suspended == 0;
            if (this.copying) {
                this.quads.add(x, y, z, nx, ny, nz);
            }
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.target.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.target.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.target.setNormal(x, y, z);
            if (this.copying) {
                this.quads.setLastNormal(x, y, z);
            }
            return this;
        }
    }

    /**
     * Render type of the red pass: white texture, emissive entity shader (directional light, no lightmap — like
     * 1.7.10 with the lightmap off), alpha blending, depth test EQUAL, no depth writes, no culling
     */
    private static final class PassType extends RenderType {

        private PassType(String name, boolean depthOffset) {
            this(name, List.of(
                new RenderStateShard.TextureStateShard(WHITE, false, false),
                RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER,
                TRANSLUCENT_TRANSPARENCY,
                EQUAL_DEPTH_TEST,
                NO_CULL,
                NO_LIGHTMAP,
                OVERLAY,
                depthOffset ? VIEW_OFFSET_Z_LAYERING : NO_LAYERING,
                MAIN_TARGET,
                DEFAULT_TEXTURING,
                COLOR_WRITE,
                DEFAULT_LINE,
                NO_COLOR_LOGIC
            ));
        }

        private PassType(String name, List<RenderStateShard> shards) {
            super(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
                () -> shards.forEach(RenderStateShard::setupRenderState),
                () -> shards.forEach(RenderStateShard::clearRenderState));
        }
    }
}
