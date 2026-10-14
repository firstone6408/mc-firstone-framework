package io.github.firstone.framework.devtest.features.animatium.item;

import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.VertexCapture.Vertex;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks shared by the first-person and third-person item tests: where an item was drawn compared with the 1.7.10
 * shape, written again from the 1.7.10 code
 *
 * <p>A flat 1.7.10 item ({@code ItemRenderer.renderItem}) is a sprite from (0, 0, 0) to (1, 1, -1/16) drawn after
 * {@code translate(0, -0.3, 0)}, scale 1.5, 50° around Y, 335° around Z and {@code translate(-0.9375, -0.0625, 0)},
 * mirrored left to right (u max at x = 0, v max at y = 0); a block is a cube around its center. In a left hand the
 * placement is mirrored across x: its sprite runs from x = 0 to x = -1. Every corner of the shape must be a drawn
 * vertex, and nothing may be drawn outside it.</p>
 */
public final class ItemDrawingChecks {

    /** Largest distance between an expected and a drawn position (floating point only) */
    public static final float POSITION_TOLERANCE = 1.0E-4F;

    /** A drawing counts as somewhere else when a corner is at least this far from every drawn vertex */
    public static final float ELSEWHERE = 0.01F;

    private ItemDrawingChecks() {}

    /**
     * Turns a matrix around an axis, in degrees (like {@code GL11.glRotatef})
     *
     * @param m       the matrix, changed
     * @param degrees the angle
     * @param x       axis x
     * @param y       axis y
     * @param z       axis z
     * @return {@code m}
     */
    public static Matrix4f rotate(Matrix4f m, float degrees, float x, float y, float z) {
        return m.rotate((float) Math.toRadians(degrees), x, y, z);
    }

    /**
     * Returns the matrix a flat 1.7.10 item's sprite is drawn with ({@code renderItem}), from where it was called
     *
     * @param held the matrix where {@code renderItem} was called
     * @param side 1 for a right hand, -1 for a left hand (mirrored)
     * @return the matrix of the sprite
     */
    public static Matrix4f legacyFlat(Matrix4f held, int side) {
        Matrix4f m = new Matrix4f(held);
        m.translate(0.0F, -0.3F, 0.0F);
        m.scale(1.5F);
        rotate(m, side * 50.0F, 0, 1, 0);
        rotate(m, side * 335.0F, 0, 0, 1);
        m.translate(side * -0.9375F, -0.0625F, 0.0F);
        return m;
    }

    /**
     * Returns the corners of a flat 1.7.10 item's sprite
     *
     * @param held the matrix where {@code renderItem} was called
     * @param side 1 for a right hand, -1 for a left hand
     * @return the 8 corners
     */
    public static List<Vector3f> flatCorners(Matrix4f held, int side) {
        return corners(legacyFlat(held, side), side > 0 ? 0.0F : -1.0F, side > 0 ? 1.0F : 0.0F, 0.0F, 1.0F,
            -1.0F / 16.0F, 0.0F);
    }

    /**
     * Returns the corners of a 1.7.10 block in the hand (a cube around the point {@code renderItem} was called at)
     *
     * @param held the matrix where {@code renderItem} was called
     * @return the 8 corners
     */
    public static List<Vector3f> blockCorners(Matrix4f held) {
        return corners(held, -0.5F, 0.5F, -0.5F, 0.5F, -0.5F, 0.5F);
    }

    /**
     * Returns the corners of a box moved by a matrix
     *
     * @param m  the matrix
     * @param x0 smallest x
     * @param x1 largest x
     * @param y0 smallest y
     * @param y1 largest y
     * @param z0 smallest z
     * @param z1 largest z
     * @return the 8 corners
     */
    public static List<Vector3f> corners(Matrix4f m, float x0, float x1, float y0, float y1, float z0, float z1) {
        List<Vector3f> corners = new ArrayList<>();
        for (float x : new float[] {x0, x1}) {
            for (float y : new float[] {y0, y1}) {
                for (float z : new float[] {z0, z1}) {
                    corners.add(m.transformPosition(x, y, z, new Vector3f()));
                }
            }
        }
        return corners;
    }

    /**
     * Returns the largest distance from an expected point to the nearest drawn vertex
     *
     * @param drawn    the drawn vertices
     * @param expected the expected points
     * @return the distance; 0 when every point was drawn
     */
    public static double farthestCorner(List<Vertex> drawn, List<Vector3f> expected) {
        double farthest = 0.0;
        for (Vector3f corner : expected) {
            double nearest = Double.MAX_VALUE;
            for (Vertex vertex : drawn) {
                nearest = Math.min(nearest, vertex.position().distance(corner));
            }
            farthest = Math.max(farthest, nearest);
        }
        return farthest;
    }

    /**
     * Returns how far a drawn vertex lies outside the box around the expected points
     *
     * @param drawn    the drawn vertices
     * @param expected the expected points
     * @return the largest distance outside, 0 when everything is inside
     */
    public static double outsideBox(List<Vertex> drawn, List<Vector3f> expected) {
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (Vector3f p : expected) {
            min.min(p);
            max.max(p);
        }
        double outside = 0.0;
        for (Vertex vertex : drawn) {
            Vector3f p = vertex.position();
            outside = Math.max(outside, Math.max(Math.max(min.x - p.x, p.x - max.x),
                Math.max(Math.max(min.y - p.y, p.y - max.y), Math.max(min.z - p.z, p.z - max.z))));
        }
        return outside;
    }

    /**
     * Checks that a drawing has every expected corner and nothing outside them
     *
     * @param t        the test
     * @param name     what was drawn, for the check names
     * @param drawn    the drawn vertices
     * @param expected the expected corners
     */
    public static void checkAt(TestContext t, String name, List<Vertex> drawn, List<Vector3f> expected) {
        t.require(!drawn.isEmpty(), "Nothing was drawn for: " + name);
        t.check(name + ": farthest 1.7.10 corner from the drawing", farthestCorner(drawn, expected))
            .atMost(POSITION_TOLERANCE);
        t.check(name + ": drawn outside the 1.7.10 shape", outsideBox(drawn, expected)).atMost(POSITION_TOLERANCE);
        checkFacingOut(t, name, drawn);
    }

    /**
     * Tells whether a flat item's texture lies like 1.7.10's: u max and v max at the sprite's corner (0, 0, 0)
     *
     * <p>Only the big front and back faces count (the edge strips reach a little further into the texture).</p>
     *
     * @param drawn the drawn vertices
     * @param held  the matrix where {@code renderItem} was called
     * @param side  1 for a right hand, -1 for a left hand
     * @return true if the texture lies like 1.7.10's (mirrored as a whole in a left hand)
     */
    public static boolean spriteLikeLegacy(List<Vertex> drawn, Matrix4f held, int side) {
        Matrix4f sprite = legacyFlat(held, side);
        Vector3f origin = sprite.transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        Vector3f faceNormal = sprite.transformDirection(0.0F, 0.0F, 1.0F, new Vector3f()).normalize();
        List<Vertex> faces = drawn.stream()
            .filter(v -> Math.abs(new Vector3f(v.normal()).normalize().dot(faceNormal)) > 0.99F).toList();
        float maxU = (float) faces.stream().mapToDouble(vertex -> vertex.u()).max().orElse(0.0);
        float maxV = (float) faces.stream().mapToDouble(vertex -> vertex.v()).max().orElse(0.0);
        return faces.stream().anyMatch(v -> v.position().distance(origin) < POSITION_TOLERANCE
            && Math.abs(v.u() - maxU) < 1.0E-6F && Math.abs(v.v() - maxV) < 1.0E-6F);
    }

    /**
     * Returns the share of drawn quads that face the way their normal says (their corners go counter-clockwise when
     * seen from the side the normal points to)
     *
     * <p>The game hides the back of item quads, so a quad drawn the other way round shows its inside: a placement that
     * mirrors the item without turning its quads makes its edges look hollow. A good drawing gives 1.</p>
     *
     * @param drawn the drawn vertices, four per quad, in drawing order
     * @return the share of quads facing their normal, from 0 to 1
     */
    public static double shareFacingOut(List<Vertex> drawn) {
        int quads = drawn.size() / 4;
        int facing = 0;
        for (int q = 0; q < quads; q++) {
            Vector3f a = drawn.get(q * 4).position();
            Vector3f b = drawn.get(q * 4 + 1).position();
            Vector3f c = drawn.get(q * 4 + 2).position();
            Vector3f winding = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
            if (winding.dot(drawn.get(q * 4).normal()) > 0.0F) {
                facing++;
            }
        }
        return quads == 0 ? 0.0 : (double) facing / quads;
    }

    /**
     * Checks that every drawn quad faces the way its normal says, so no side shows its inside
     *
     * @param t     the test
     * @param name  what was drawn, for the check name
     * @param drawn the drawn vertices
     */
    public static void checkFacingOut(TestContext t, String name, List<Vertex> drawn) {
        t.check(name + ": share of quads facing out (not inside out)", shareFacingOut(drawn)).near(1.0, 1.0E-9);
    }

    /**
     * Tells whether two drawings put the same vertices in the same places
     *
     * @param a one drawing
     * @param b the other
     * @return true if they match
     */
    public static boolean samePositions(List<Vertex> a, List<Vertex> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).position().distance(b.get(i).position()) > POSITION_TOLERANCE) {
                return false;
            }
        }
        return true;
    }
}
