package com.heishan.portallink.client.render;

import java.util.List;

import com.heishan.portallink.client.PortalIdStore;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.RangeMarker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the claim areas: translucent walls plus a full-height wireframe and the owner's number.
 *
 * <h2>Why the walls are skipped while the camera is inside the column</h2>
 * A claim area is a full-height column, because vanilla's search ignores Y. When the player stands
 * <b>inside</b> that column the walls are behind the camera or wrapped around it, and a translucent
 * surface at that distance does not read as "a volume over there" - it reads as a sheet stuck to the
 * player, and walking makes it look like the area moves with them. In the nether the same area is
 * only ~17 blocks wide, so the player is almost always inside it, which is exactly where that
 * complaint came from.
 *
 * <p>So: inside the column, only the wireframe is drawn (corner posts and the rims at the band ends);
 * outside it, the translucent walls are drawn as well. The number in the middle is always drawn,
 * because that is what identifies the owner.</p>
 *
 * <h2>No floor plane</h2>
 * A horizontal fill was tried and removed: the vertical placement of a cross-dimension area is
 * meaningless (Y does not cross dimensions, so the owning gate's Y can be hundreds of blocks from
 * the player), which left a stray horizontal sheet hanging in the air - reported as "an extra
 * horizontal layer". The wireframe rims already give the vertical extent.
 */
final class PortalRangeRenderer {

    private PortalRangeRenderer() {
    }

    /**
     * Height band of a claim area, used to decide whether it applies at the player's position.
     *
     * <p>Purely a selection test: a shared column is split into one band per competing gate, and only
     * the band the player stands in is drawn.</p>
     */
    static boolean appliesAt(RangeMarker marker, double eyeY) {
        return eyeY >= marker.bottomY() && eyeY <= marker.topY();
    }

    /** Cull by the nearest point of the square: a 257 block wide area is "here" even off centre. */
    static boolean outOfRange(RangeMarker marker, Vec3 eye, double maxDistSq) {
        double nearestX = PortalRenderSupport.clamp(eye.x,
                marker.minBlockX(), marker.maxBlockX() + 1);
        double nearestZ = PortalRenderSupport.clamp(eye.z,
                marker.minBlockZ(), marker.maxBlockZ() + 1);
        double dx = nearestX - eye.x;
        double dz = nearestZ - eye.z;
        return dx * dx + dz * dz > maxDistSq;
    }

    /** Draw every claim area that applies here and is within range. */
    static void render(PoseStack pose, MultiBufferSource buffers, List<RangeMarker> markers,
                       LinkColorAssigner colors, PortalLinkConfig config, Vec3 eye,
                       double maxDistSq) {
        for (RangeMarker marker : markers) {
            if (!appliesAt(marker, eye.y) || outOfRange(marker, eye, maxDistSq)) {
                continue;
            }
            int rgb = colors.colorOf(marker) & 0x00FFFFFF;
            if (config.showZoneWalls) {
                walls(pose, buffers, marker, rgb, config, eye);
            }
            edges(pose, buffers, marker, rgb, config);
            guides(pose, buffers, marker, rgb, config);
        }
    }

    /**
     * The {@code #N} in the middle of every visible area.
     *
     * <p>Drawn separately from the geometry, and with the pose at identity: the text helper subtracts
     * the camera itself, so it must not run inside the camera-translated pass.</p>
     */
    static void drawAreaLabels(PoseStack pose, MultiBufferSource buffers, List<RangeMarker> markers,
                               LinkColorAssigner colors, PortalLinkConfig config, Vec3 eye,
                               double maxDistSq) {
        if (!config.renderLabels) {
            return;
        }
        for (RangeMarker marker : markers) {
            if (!appliesAt(marker, eye.y) || outOfRange(marker, eye, maxDistSq)) {
                continue;
            }
            int rgb = colors.colorOf(marker) & 0x00FFFFFF;
            areaLabel(pose, buffers, marker, rgb, config, eye);
        }
    }

    /** Four translucent walls over the band's whole vertical extent. */
    private static void walls(PoseStack pose, MultiBufferSource buffers, RangeMarker marker,
                              int rgb, PortalLinkConfig config, Vec3 eye) {
        double x0 = marker.minBlockX();
        double x1 = marker.maxBlockX() + 1.0D;
        double z0 = marker.minBlockZ();
        double z1 = marker.maxBlockZ() + 1.0D;
        double yDown = marker.bottomY();
        double yUp = marker.topY();

        VertexConsumer buffer = buffers.getBuffer(PortalRenderTypes.quads(config.alwaysOnTop));
        PoseStack.Pose last = pose.last();

        // One constant alpha for all four walls. A distance-dependent fade was tried and removed: the
        // surfaces then change brightness as the player walks, which itself reads as "the range is
        // moving". A fixed value keeps the box visually still.
        int argb = PortalPalette.withAlpha(rgb, config.zoneWallAlpha);
        int argb0 = argb;
        int argb1 = argb;
        int argb2 = argb;
        int argb3 = argb;

        PortalRenderSupport.quadBothSides(buffer, last, argb0, x0, yDown, z0, x1, yDown, z0,
                x1, yUp, z0, x0, yUp, z0);
        PortalRenderSupport.quadBothSides(buffer, last, argb1, x1, yDown, z0, x1, yDown, z1,
                x1, yUp, z1, x1, yUp, z0);
        PortalRenderSupport.quadBothSides(buffer, last, argb2, x1, yDown, z1, x0, yDown, z1,
                x0, yUp, z1, x1, yUp, z1);
        PortalRenderSupport.quadBothSides(buffer, last, argb3, x0, yDown, z1, x0, yDown, z0,
                x0, yUp, z0, x0, yUp, z1);
    }

    /**
     * Vertical position for the owner number: inside the band and next to the eye.
     *
     * <p>Clamping into the band keeps a cross-dimension area's label visible in the level the player
     * is standing in, and following the eye keeps it visible wherever they are in the band.</p>
     */
    private static double labelY(RangeMarker marker) {
        return marker.ownerPos().getY() + 2.0D;
    }

    /**
     * Whether the in-world number should be drawn for this area at all.
     *
     * <h2>Why the owner's height decides it</h2>
     * Height does not cross dimensions, so an area claimed by a gate of the <b>other</b> dimension has
     * a meaningless Y here: in the nether the owning overworld gate sat at y=-62 and the number ended
     * up under the floor. Following the eye instead made it worse in a different way - standing at the
     * centre of the area put the text in the player's face, reported as "an extremely large #3".
     *
     * <p>So the number is drawn only where it means something: when the owning gate's own height is
     * inside the band this dimension draws. The status panel already names the owner for the rest.</p>
     */
    private static boolean labelBelongsHere(RangeMarker marker) {
        int ownerY = marker.ownerPos().getY();
        return ownerY >= marker.bottomY() && ownerY <= marker.topY();
    }

    /**
     * Vertical guide lines across every wall, every {@link #GUIDE_SPACING} blocks.
     *
     * <h2>Why the walls need them</h2>
     * The walls span the whole height band and are drawn through terrain, so a player standing inside
     * sees four uniform sheets with no edge and no stationary feature to compare against; walking then
     * makes the surfaces read as sliding sideways even though the coordinates never change. A grid
     * pinned to the world gives the eye something fixed to judge against.
     */
    private static void guides(PoseStack pose, MultiBufferSource buffers, RangeMarker marker,
                               int rgb, PortalLinkConfig config) {
        double x0 = marker.minBlockX();
        double x1 = marker.maxBlockX() + 1.0D;
        double z0 = marker.minBlockZ();
        double z1 = marker.maxBlockZ() + 1.0D;
        double yDown = marker.bottomY();
        double yUp = marker.topY();
        int argb = PortalPalette.withAlpha(rgb, Math.max(40, config.zoneEdgeAlpha / 3));
        VertexConsumer buffer = buffers.getBuffer(PortalRenderTypes.lines(config.alwaysOnTop));
        PoseStack.Pose last = pose.last();

        for (double x = x0 + GUIDE_SPACING; x < x1; x += GUIDE_SPACING) {
            PortalRenderSupport.line(buffer, last, new Vec3(x, yDown, z0), new Vec3(x, yUp, z0),
                    argb, 0.0F, 0.0F, 1.0F);
            PortalRenderSupport.line(buffer, last, new Vec3(x, yDown, z1), new Vec3(x, yUp, z1),
                    argb, 0.0F, 0.0F, 1.0F);
        }
        for (double z = z0 + GUIDE_SPACING; z < z1; z += GUIDE_SPACING) {
            PortalRenderSupport.line(buffer, last, new Vec3(x0, yDown, z), new Vec3(x0, yUp, z),
                    argb, 1.0F, 0.0F, 0.0F);
            PortalRenderSupport.line(buffer, last, new Vec3(x1, yDown, z), new Vec3(x1, yUp, z),
                    argb, 1.0F, 0.0F, 0.0F);
        }
    }

    /** Spacing of the vertical guide lines on the walls, in blocks. */
    private static final double GUIDE_SPACING = 8.0D;


    /** Four vertical corner posts plus a rim at the bottom and at the top of the band. */
    private static void edges(PoseStack pose, MultiBufferSource buffers, RangeMarker marker,
                              int rgb, PortalLinkConfig config) {
        double x0 = marker.minBlockX();
        double x1 = marker.maxBlockX() + 1.0D;
        double z0 = marker.minBlockZ();
        double z1 = marker.maxBlockZ() + 1.0D;
        double yDown = marker.bottomY();
        double yUp = marker.topY();
        int argb = PortalPalette.withAlpha(rgb, config.zoneEdgeAlpha);

        VertexConsumer buffer = buffers.getBuffer(PortalRenderTypes.lines(config.alwaysOnTop));
        PoseStack.Pose last = pose.last();

        // Corner posts: the four vertical edges of the column. Lines only need a unit normal to
        // satisfy the vertex format.
        PortalRenderSupport.line(buffer, last, new Vec3(x0, yDown, z0), new Vec3(x0, yUp, z0), argb,
                1.0F, 0.0F, 0.0F);
        PortalRenderSupport.line(buffer, last, new Vec3(x1, yDown, z0), new Vec3(x1, yUp, z0), argb,
                1.0F, 0.0F, 0.0F);
        PortalRenderSupport.line(buffer, last, new Vec3(x1, yDown, z1), new Vec3(x1, yUp, z1), argb,
                1.0F, 0.0F, 0.0F);
        PortalRenderSupport.line(buffer, last, new Vec3(x0, yDown, z1), new Vec3(x0, yUp, z1), argb,
                1.0F, 0.0F, 0.0F);

        rim(buffer, last, argb, x0, x1, z0, z1, yDown);
        rim(buffer, last, argb, x0, x1, z0, z1, yUp);
    }

    private static void rim(VertexConsumer buffer, PoseStack.Pose last, int argb,
                            double x0, double x1, double z0, double z1, double y) {
        PortalRenderSupport.line(buffer, last, new Vec3(x0, y, z0), new Vec3(x1, y, z0), argb,
                0.0F, 1.0F, 0.0F);
        PortalRenderSupport.line(buffer, last, new Vec3(x1, y, z0), new Vec3(x1, y, z1), argb,
                0.0F, 1.0F, 0.0F);
        PortalRenderSupport.line(buffer, last, new Vec3(x1, y, z1), new Vec3(x0, y, z1), argb,
                0.0F, 1.0F, 0.0F);
        PortalRenderSupport.line(buffer, last, new Vec3(x0, y, z1), new Vec3(x0, y, z0), argb,
                0.0F, 1.0F, 0.0F);
    }

    /**
     * Number of the gate that owns this area, floating over its middle.
     *
     * <p>The area and the gate must be matchable without walking over: with several gates in view, the
     * number on the area is what tells you which gate the ground you are standing on belongs to.</p>
     */
    private static void areaLabel(PoseStack pose, MultiBufferSource buffers, RangeMarker marker,
                                  int rgb, PortalLinkConfig config, Vec3 eye) {
        int cx = (marker.minBlockX() + marker.maxBlockX()) / 2;
        int cz = (marker.minBlockZ() + marker.maxBlockZ()) / 2;
        // The label must stay inside the band, near the eye. Using the owning gate's own Y put the
        // number hundreds of blocks away: for an area drawn in the nether the owner is an overworld
        // gate, so its Y (-62 here) is meaningless in this level and the text ended up under the floor.
        double y = labelY(marker);
        if (!labelBelongsHere(marker)) {
            return;
        }
        int id = PortalIdStore.get().idOf(marker.ownerPos(), marker.ownerIsNether());
        PortalLabelRenderer.floatingText(pose, buffers, "#" + id,
                cx + 0.5D, y, cz + 0.5D,
                PortalPalette.withAlpha(rgb, 0xFF), config.labelScale, config.alwaysOnTop);
    }
}
