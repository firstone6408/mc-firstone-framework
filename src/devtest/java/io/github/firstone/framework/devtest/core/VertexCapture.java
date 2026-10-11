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
 * position (already moved by the pose stack), texture coordinates and normal. Nothing reaches the screen.</p>
 */
public final class VertexCapture implements MultiBufferSource {

    private final List<Vertex> vertices = new ArrayList<>();
    private final Consumer consumer = new Consumer();

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return this.consumer;
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
     */
    public record Vertex(Vector3f position, float u, float v, Vector3f normal) {}

    /** Collects the parts of one vertex as the game sets them */
    private final class Consumer implements VertexConsumer {

        private Vector3f position;
        private float u;
        private float v;
        private Vector3f normal = new Vector3f();

        /** Keeps the vertex being built, if any */
        void finish() {
            if (this.position != null) {
                VertexCapture.this.vertices.add(new Vertex(this.position, this.u, this.v, this.normal));
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
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
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
