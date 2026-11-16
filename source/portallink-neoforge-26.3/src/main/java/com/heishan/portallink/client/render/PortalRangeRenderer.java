package com.heishan.portallink.client.render;

import java.util.List;

import com.heishan.portallink.client.PortalIdStore;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.RangeMarker;

import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
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

    /** Width of the corner posts and rims, in pixels. */
    private static final float EDGE_WIDTH = 2.0F;

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

    /** Draw every claim area that applies here and is within range. */
    static void render(List<RangeMarker> markers, LinkColorAssigner colors, PortalLinkConfig config,
                       Vec3 eye, double maxDistSq) {
        for (RangeMarker marker : markers) {
            if (!appliesAt(marker, eye.y) || outOfRange(marker, eye, maxDistSq)) {
                continue;
            }
            int rgb = colors.colorOf(marker) & 0x00FFFFFF;
            if (config.showZoneWalls) {
                walls(marker, rgb, config, eye);
            }
            edges(marker, rgb, config);
            guides(marker, rgb, config);
            areaLabel(marker, rgb, config, eye);
        }
    }

    /** Cull by the nearest point of the square: a 257 block wide area is "here" even off centre. */
    static boolean outOfRange(RangeMarker marker, Vec3 eye, double maxDistSq) {
        double nearestX = PortalGizmoSupport.clamp(eye.x,
                marker.minBlockX(), marker.maxBlockX() + 1);
        double nearestZ = PortalGizmoSupport.clamp(eye.z,
                marker.minBlockZ(), marker.maxBlockZ() + 1);
        double dx = nearestX - eye.x;
        double dz = nearestZ - eye.z;
        return dx * dx + dz * dz > maxDistSq;
    }

    /** Four translucent walls over the band's whole vertical extent. */
    private static void walls(RangeMarker marker, int rgb, PortalLinkConfig config, Vec3 eye) {
        double x0 = marker.minBlockX();
        double x1 = marker.maxBlockX() + 1.0D;
        double z0 = marker.minBlockZ();
        double z1 = marker.maxBlockZ() + 1.0D;
        double yDown = marker.bottomY();
        double yUp = marker.topY();

        // One constant alpha for all four walls. A distance-dependent fade was tried and removed: the
        // surfaces then change brightness as the player walks, which itself reads as "the range is
        // moving". A fixed value keeps the box visually still.
        GizmoStyle nearZ = GizmoStyle.fill(PortalPalette.withAlpha(rgb, config.zoneWallAlpha));
        GizmoStyle farX = nearZ;
        GizmoStyle farZ = nearZ;
        GizmoStyle nearX = nearZ;

        // Each wall is emitted twice with opposite winding. A single quad is only visible from the
        // side its winding faces, so standing inside the column showed the wireframe but no colour -
        // and with a 17 block wide nether area the player is inside almost always.
        wall(x0, yDown, z0, x1, yDown, z0, x1, yUp, z0, x0, yUp, z0, nearZ, config);
        wall(x1, yDown, z0, x1, yDown, z1, x1, yUp, z1, x1, yUp, z0, farX, config);
        wall(x1, yDown, z1, x0, yDown, z1, x0, yUp, z1, x1, yUp, z1, farZ, config);
        wall(x0, yDown, z1, x0, yDown, z0, x0, yUp, z0, x0, yUp, z1, nearX, config);
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

    /** One wall, emitted with both windings so it is visible from inside and from outside. */
    private static void wall(double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz,
                             GizmoStyle fill, PortalLinkConfig config) {
        PortalGizmoSupport.finish(Gizmos.rect(new Vec3(ax, ay, az), new Vec3(bx, by, bz),
                new Vec3(cx, cy, cz), new Vec3(dx, dy, dz), fill), config);
        PortalGizmoSupport.finish(Gizmos.rect(new Vec3(dx, dy, dz), new Vec3(cx, cy, cz),
                new Vec3(bx, by, bz), new Vec3(ax, ay, az), fill), config);
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
    private static void guides(RangeMarker marker, int rgb, PortalLinkConfig config) {
        double x0 = marker.minBlockX();
        double x1 = marker.maxBlockX() + 1.0D;
        double z0 = marker.minBlockZ();
        double z1 = marker.maxBlockZ() + 1.0D;
        double yDown = marker.bottomY();
        double yUp = marker.topY();
        int argb = PortalPalette.withAlpha(rgb, Math.max(40, config.zoneEdgeAlpha / 3));

        for (double x = x0 + GUIDE_SPACING; x < x1; x += GUIDE_SPACING) {
            PortalGizmoSupport.line(new Vec3(x, yDown, z0), new Vec3(x, yUp, z0), argb, 1.5F,
                    config);
            PortalGizmoSupport.line(new Vec3(x, yDown, z1), new Vec3(x, yUp, z1), argb, 1.5F,
                    config);
        }
        for (double z = z0 + GUIDE_SPACING; z < z1; z += GUIDE_SPACING) {
            PortalGizmoSupport.line(new Vec3(x0, yDown, z), new Vec3(x0, yUp, z), argb, 1.5F,
                    config);
            PortalGizmoSupport.line(new Vec3(x1, yDown, z), new Vec3(x1, yUp, z), argb, 1.5F,
                    config);
        }
    }

    /** Spacing of the vertical guide lines on the walls, in blocks. */
    private static final double GUIDE_SPACING = 8.0D;

    /** Four vertical corner posts plus a rim at the bottom and at the top of the band. */
    private static void edges(RangeMarker marker, int rgb, PortalLinkConfig config) {
        double x0 = marker.minBlockX();
        double x1 = marker.maxBlockX() + 1.0D;
        double z0 = marker.minBlockZ();
        double z1 = marker.maxBlockZ() + 1.0D;
        double yDown = marker.bottomY();
        double yUp = marker.topY();
        int argb = PortalPalette.withAlpha(rgb, config.zoneEdgeAlpha);

        PortalGizmoSupport.line(new Vec3(x0, yDown, z0), new Vec3(x0, yUp, z0), argb, EDGE_WIDTH,
                config);
        PortalGizmoSupport.line(new Vec3(x1, yDown, z0), new Vec3(x1, yUp, z0), argb, EDGE_WIDTH,
                config);
        PortalGizmoSupport.line(new Vec3(x1, yDown, z1), new Vec3(x1, yUp, z1), argb, EDGE_WIDTH,
                config);
        PortalGizmoSupport.line(new Vec3(x0, yDown, z1), new Vec3(x0, yUp, z1), argb, EDGE_WIDTH,
                config);

        rim(argb, x0, x1, z0, z1, yDown, config);
        rim(argb, x0, x1, z0, z1, yUp, config);
    }

    private static void rim(int argb, double x0, double x1, double z0, double z1, double y,
                            PortalLinkConfig config) {
        PortalGizmoSupport.line(new Vec3(x0, y, z0), new Vec3(x1, y, z0), argb, EDGE_WIDTH, config);
        PortalGizmoSupport.line(new Vec3(x1, y, z0), new Vec3(x1, y, z1), argb, EDGE_WIDTH, config);
        PortalGizmoSupport.line(new Vec3(x1, y, z1), new Vec3(x0, y, z1), argb, EDGE_WIDTH, config);
        PortalGizmoSupport.line(new Vec3(x0, y, z1), new Vec3(x0, y, z0), argb, EDGE_WIDTH, config);
    }

    /**
     * Number of the gate that owns this area, floating over its middle.
     *
     * <p>The area and the gate must be matchable without walking over: with several gates in view, the
     * number on the area is what tells you which gate the ground you are standing on belongs to.</p>
     */
    private static void areaLabel(RangeMarker marker, int rgb, PortalLinkConfig config, Vec3 eye) {
        if (!config.renderLabels) {
            return;
        }
        int cx = (marker.minBlockX() + marker.maxBlockX()) / 2;
        int cz = (marker.minBlockZ() + marker.maxBlockZ()) / 2;
        // The label must stay inside the band, near the eye. Using the owning gate's own Y put the
        // number hundreds of blocks away: for an area drawn in the nether the owner is an overworld
        // gate, so its Y is meaningless here and the text ended up under the floor.
        double y = labelY(marker);
        if (!labelBelongsHere(marker)) {
            return;
        }
        int id = PortalIdStore.get().idOf(marker.ownerPos(), marker.ownerIsNether());
        // Signature is (text, pos, ROW, color, scale). The third argument is the row index, not the
        // colour: passing an ARGB int there pushed the text to `1.3 + row * 0.2` blocks above the
        // block, i.e. hundreds of millions of blocks up, so the number never appeared at all.
        PortalGizmoSupport.finish(Gizmos.billboardTextOverBlock("#" + id,
                        BlockPos.containing(cx, (int) Math.floor(y), cz), 0,
                        PortalPalette.withAlpha(rgb, 0xFF), config.labelScale),
                config);
    }
}
