package com.heishan.portallink.client.render;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.RangeMarker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a claim area as a submitted mesh: full height walls plus edges, with no gizmos involved.
 *
 * <h2>Nothing is drawn at the player's height</h2>
 * An earlier version put a translucent floor plane at a height derived from the player's own Y, so the
 * player had something anchored to the ground. That floor tracked the player up and down, which showed
 * up as "a layer of colour permanently at eye level". Every surface here is tied to the <b>area's own
 * vertical extent</b> ({@code bottomY..topY}) instead, so nothing moves with the player's height.
 *
 * <h2>Why a submitted mesh rather than gizmos</h2>
 * Gizmos are retained: an element needs a lifetime to be drawn at all, which leaves
 * {@code lifetime / frameTime} copies alive. Translucent copies stack to {@code 1-(1-a)^N} with
 * {@code N} wobbling, and tall retained geometry fills the screen and sweeps past while walking. A
 * submitted mesh is drawn once per frame with exactly the alpha given, so it neither flickers nor
 * appears to follow the player. Edges are therefore thin quads, not gizmo lines.
 *
 * <h2>Vertex recipe (do not trim it)</h2>
 * <ul>
 *   <li>Vertices are {@code world - camera}: the level render pose is in camera space on this path.
 *       Verified by experiment - world coordinates render nothing at all.</li>
 *   <li>Every vertex needs {@code setColor} + {@code setUv} + {@code setOverlay} + {@code setLight} +
 *       {@code setNormal}; omitting any of them makes the quad invisible, silently.</li>
 * </ul>
 */
final class RangeMeshRenderer {

    /** Our own indicator, so it must not be dimmed by world lighting. */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Edge thickness in blocks; thin enough to read as a line at distance, visible up close. */
    private static final double EDGE = 0.08D;

    /** Inset used for the rim strips so they do not z-fight with the wall quads. */
    private static final double RIM_INSET = 0.02D;

    private RangeMeshRenderer() {
    }

    /** Camera position of the frame being rendered. */
    private static Vec3 camera(LevelRenderContext context) {
        try {
            return context.levelState().cameraRenderState.pos;
        } catch (RuntimeException exception) {
            return net.minecraft.client.Minecraft.getInstance()
                    .gameRenderer.mainCamera().position();
        }
    }

    /** Draw one area: walls and edges over the area's own vertical extent. */
    static void render(LevelRenderContext context, PortalLinkConfig config, RangeMarker marker,
                       int rgb) {
        Vec3 cam = camera(context);
        double x0 = marker.minBlockX() - cam.x;
        double x1 = marker.maxBlockX() + 1.0D - cam.x;
        double z0 = marker.minBlockZ() - cam.z;
        double z1 = marker.maxBlockZ() + 1.0D - cam.z;
        double yDown = marker.bottomY() - cam.y;
        double yUp = marker.topY() - cam.y;

        int wallArgb = PortalPalette.withAlpha(rgb, config.zoneWallAlpha);
        int edgeArgb = PortalPalette.withAlpha(rgb, config.zoneEdgeAlpha);

        try {
            SubmitNodeCollector collector = context.submitNodeCollector();
            PoseStack pose = context.poseStack();

            if (config.showZoneWalls) {
                collector.submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buffer) -> {
                    wallQuad(buffer, p, wallArgb, x0, z0, x1, z0, yDown, yUp);
                    wallQuad(buffer, p, wallArgb, x1, z0, x1, z1, yDown, yUp);
                    wallQuad(buffer, p, wallArgb, x1, z1, x0, z1, yDown, yUp);
                    wallQuad(buffer, p, wallArgb, x0, z1, x0, z0, yDown, yUp);
                });
            }

            // Edges: four vertical corner posts, plus top and bottom rims.
            collector.submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buffer) -> {
                postXZ(buffer, p, edgeArgb, x0, z0, yDown, yUp);
                postXZ(buffer, p, edgeArgb, x1, z0, yDown, yUp);
                postXZ(buffer, p, edgeArgb, x1, z1, yDown, yUp);
                postXZ(buffer, p, edgeArgb, x0, z1, yDown, yUp);

                rimX(buffer, p, edgeArgb, x0, x1, z0, yDown);
                rimX(buffer, p, edgeArgb, x0, x1, z1, yDown);
                rimZ(buffer, p, edgeArgb, z0, z1, x0, yDown);
                rimZ(buffer, p, edgeArgb, z0, z1, x1, yDown);

                rimX(buffer, p, edgeArgb, x0, x1, z0, yUp);
                rimX(buffer, p, edgeArgb, x0, x1, z1, yUp);
                rimZ(buffer, p, edgeArgb, z0, z1, x0, yUp);
                rimZ(buffer, p, edgeArgb, z0, z1, x1, yUp);
            });
        } catch (RuntimeException exception) {
            PortalLink.LOGGER.warn("could not submit claim area", exception);
        }
    }

    /** One wall: a vertical quad between two floor-plan points. */
    private static void wallQuad(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                                 double ax, double az, double bx, double bz,
                                 double yDown, double yUp) {
        quadBothSides(buffer, pose, argb,
                ax, yDown, az, bx, yDown, bz, bx, yUp, bz, ax, yUp, az);
    }

    /**
     * A vertical corner post, drawn as two perpendicular quads.
     *
     * <p>A single quad has one normal, so a corner seen from the other side would disappear. Two
     * perpendicular quads make the post visible from any direction without the cost of a full box.</p>
     */
    private static void postXZ(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                               double x, double z, double yDown, double yUp) {
        quadBothSides(buffer, pose, argb,
                x - EDGE, yDown, z, x + EDGE, yDown, z, x + EDGE, yUp, z, x - EDGE, yUp, z);
        quadBothSides(buffer, pose, argb,
                x, yDown, z - EDGE, x, yDown, z + EDGE, x, yUp, z + EDGE, x, yUp, z - EDGE);
    }

    /** A horizontal rim strip running along X at a fixed Z. */
    private static void rimX(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                             double x0, double x1, double z, double y) {
        double yOffset = y > 0 ? -RIM_INSET : RIM_INSET;
        double yy = y + yOffset;
        quadBothSides(buffer, pose, argb,
                x0, yy, z - EDGE, x1, yy, z - EDGE, x1, yy, z + EDGE, x0, yy, z + EDGE);
    }

    /** A horizontal rim strip running along Z at a fixed X. */
    private static void rimZ(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                             double z0, double z1, double x, double y) {
        double yOffset = y > 0 ? -RIM_INSET : RIM_INSET;
        double yy = y + yOffset;
        quadBothSides(buffer, pose, argb,
                x - EDGE, yy, z0, x + EDGE, yy, z0, x + EDGE, yy, z1, x - EDGE, yy, z1);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz) {
        vertex(buffer, pose, argb, ax, ay, az);
        vertex(buffer, pose, argb, bx, by, bz);
        vertex(buffer, pose, argb, cx, cy, cz);
        vertex(buffer, pose, argb, dx, dy, dz);
    }

    /**
     * A quad emitted in both winding orders, so it is visible from either side.
     *
     * <h2>Why this is required, not cosmetic</h2>
     * {@code RenderTypes.debugFilledBox()} culls back faces. A single wall quad is therefore visible
     * only from outside the column: standing <b>inside</b>, all four walls face away and are culled,
     * so the range rendered as nothing at all exactly where the player needs it most. Emitting the
     * reversed winding makes the same surface front-facing from the other side.
     *
     * <p>Emitted twice, not a separate layer: the two copies share one plane, so blended alpha is that
     * of a single surface seen twice only when both faces are visible, which cannot happen for a
     * flat quad.</p>
     */
    private static void quadBothSides(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                                      double ax, double ay, double az,
                                      double bx, double by, double bz,
                                      double cx, double cy, double cz,
                                      double dx, double dy, double dz) {
        quad(buffer, pose, argb, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
        quad(buffer, pose, argb, dx, dy, dz, cx, cy, cz, bx, by, bz, ax, ay, az);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                               double x, double y, double z) {
        buffer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(argb)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
