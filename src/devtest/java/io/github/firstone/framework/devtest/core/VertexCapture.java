package io.github.firstone.framework.devtest.core;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffers that keep every vertex drawn into them, so a test can check where something was drawn
 *
 * <p>Hand it to the game's drawing code instead of the real buffers; afterwards {@link #vertices()} has each vertex's
 * position (already moved by the pose stack), texture coordinates, normal and color, and {@link #renderTypes()} the
 * render types asked for. The glint's vertices are left out (they repeat the item's). Nothing reaches the screen.</p>
 */
public final class VertexCapture implements MultiBufferSource {

    private final List<Vertex> vertices = new ArrayList<>();
    private final List<String> renderTypes = new ArrayList<>();
    private final Consumer consumer = new Consumer();

    /** Takes the glint's vertices, which repeat the item's own, and keeps nothing */
    private final Consumer glint = new Consumer();

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        String name = renderType.toString();
        this.renderTypes.add(name);
        // the game joins the glint's buffer with the item's, which must be two different buffers
        return name.contains("glint") ? this.glint : this.consumer;
    }

    /**
     * Returns the render types asked for so far, as text (their names, like {@code glint} or {@code text}, are in it)
     *
     * @return the render types, in the order they were asked for
     */
    public List<String> renderTypes() {
        return List.copyOf(this.renderTypes);
    }

    /**
     * Returns the vertices drawn so far, in drawing order
     *
     * @return the vertices
     */
    public List<Vertex> vertices() {
        this.consumer.finish();
        return List.copyOf(this.vertices);
    }

    /**
     * One drawn vertex
     *
     * @param position where it was drawn
     * @param u        texture u
     * @param v        texture v
     * @param normal   its normal (already turned by the pose stack)
     * @param color    its color, as ARGB (-1 when white or not set)
     */
    public record Vertex(Vector3f position, float u, float v, Vector3f normal, int color) {}

    /** Collects the parts of one vertex as the game sets them */
    private final class Consumer implements VertexConsumer {

        private Vector3f position;
        private float u;
        private float v;
        private Vector3f normal = new Vector3f();
        private int color = -1;

        /** Keeps the vertex being built, if any (the glint's buffer keeps nothing) */
        void finish() {
            if (this.position != null && this != VertexCapture.this.glint) {
                VertexCapture.this.vertices.add(new Vertex(this.position, this.u, this.v, this.normal, this.color));
                this.position = null;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finish();
            this.position = new Vector3f(x, y, z);
            this.u = 0.0F;
            this.v = 0.0F;
            this.normal = new Vector3f();
            this.color = -1;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.color = alpha << 24 | red << 16 | green << 8 | blue;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.normal = new Vector3f(x, y, z);
            return this;
        }
    }
}
