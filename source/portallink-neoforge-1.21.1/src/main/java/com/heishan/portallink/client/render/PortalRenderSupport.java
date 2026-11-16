package com.heishan.portallink.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Vertex-level helpers for the 1.21.1 world overlay.
 *
 * <h2>Two rules that are easy to get wrong</h2>
 * <ul>
 *   <li>Every line vertex needs a normal. {@code RenderType.lines()} uses
 *       {@code POSITION_COLOR_NORMAL}; a vertex without {@code setNormal} silently draws nothing.</li>
 *   <li>Quads use {@code POSITION_COLOR} in {@code QUADS} mode, so they must be emitted as four
 *       vertices in order. Vanilla's {@code debugFilledBox()} is a {@code TRIANGLE_STRIP}, which is
 *       why it cannot be used for arbitrary wall quads.</li>
 * </ul>
 */
final class PortalRenderSupport {

    private PortalRenderSupport() {
    }

    /** One line segment. */
    static void line(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, int argb,
                     float nx, float ny, float nz) {
        vertex(buffer, pose, from.x, from.y, from.z, argb, nx, ny, nz);
        vertex(buffer, pose, to.x, to.y, to.z, argb, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z,
                               int argb, float nx, float ny, float nz) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(argb)
                .setNormal(nx, ny, nz);
    }

    /** One quad, emitted in both winding orders so it is visible from either side. */
    static void quadBothSides(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                              double ax, double ay, double az,
                              double bx, double by, double bz,
                              double cx, double cy, double cz,
                              double dx, double dy, double dz) {
        quad(buffer, pose, argb, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
        quad(buffer, pose, argb, dx, dy, dz, cx, cy, cz, bx, by, bz, ax, ay, az);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz) {
        vertex(buffer, pose, ax, ay, az, argb);
        vertex(buffer, pose, bx, by, bz, argb);
        vertex(buffer, pose, cx, cy, cz, argb);
        vertex(buffer, pose, dx, dy, dz, argb);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double x, double y, double z,
                               int argb) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z).setColor(argb);
    }

    /** Flush whatever was written to a buffer source. */
    static void flush(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch();
    }

    /** Clamp a value into a range. */
    static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
