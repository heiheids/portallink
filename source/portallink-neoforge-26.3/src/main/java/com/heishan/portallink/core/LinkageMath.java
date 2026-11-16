package com.heishan.portallink.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pure math mirroring the vanilla nether portal search.
 *
 * <p>Vanilla behaviour (verified against the 26.3 bytecode of
 * {@code PortalForcer#findClosestPortalPosition} and
 * {@code ServerLevel#getPortalForcer}):</p>
 *
 * <ol>
 *   <li>The source position is multiplied by the ratio of the two dimensions'
 *       {@code coordinateScale} values (overworld 1.0, nether 8.0), so overworld -&gt; nether is
 *       {@code /8} and nether -&gt; overworld is {@code *8}.</li>
 *   <li>That point is clamped to the destination world border.</li>
 *   <li>The search radius is <b>16</b> when the destination is the nether and <b>128</b> when the
 *       destination is the overworld. It is a square in X/Z (no Y limit), then candidates are
 *       sorted by squared distance and finally by Y.</li>
 * </ol>
 */
public final class LinkageMath {

    /** Search radius used by vanilla when the destination dimension is the nether. */
    public static final int RADIUS_TO_NETHER = 16;

    /** Search radius used by vanilla when the destination dimension is the overworld. */
    public static final int RADIUS_TO_OVERWORLD = 128;

    /**
     * Half width of a standing player's bounding box.
     *
     * <p>Teleporting is triggered by {@code Block#entityInside}, i.e. by the player's bounding box
     * intersecting a portal block, not by the player's centre being inside it. A player can therefore
     * stand up to this far outside the portal and still be teleported, which widens the set of reachable
     * entry points by this much on every horizontal axis.</p>
     */
    public static final double PLAYER_HALF_WIDTH = 0.3D;

    private LinkageMath() {
    }

    /**
     * Extra reach, in <b>this</b> dimension's blocks, from the contact rule and the portal's own width.
     *
     * <h2>Derivation</h2>
     * A new portal at {@code p} links to gate {@code G} when some reachable entry point {@code e}
     * satisfies {@code |map(e) - G| <= R} (a square in X/Z). The entry point may sit anywhere on the new
     * portal's plane, so it can differ from the portal centre by up to {@code width/2}, plus
     * {@link #PLAYER_HALF_WIDTH} because contact does not require standing inside the block:
     *
     * <pre>{@code |p - G/scale| <= R/|scale| + (width/2 + PLAYER_HALF_WIDTH)}</pre>
     *
     * <p>The {@code R/|scale|} term is the half edge already in use. The remaining term is in this
     * dimension's blocks and is <b>not</b> scaled - the player's box does not shrink when it crosses
     * dimensions.</p>
     *
     * <h2>Why the shape stays square</h2>
     * The search region is {@code PoiManager#getInSquare}, i.e. a Chebyshev square, and both the portal
     * plane and the player's box are axis aligned rectangles. The Minkowski sum of a rectangle and a
     * square is still an axis aligned rectangle, so no cylinder ever appears; only a spherical player
     * would round the corners, and a 0.3 block rounding is invisible at these sizes.
     *
     * @param portalWidth width of the portal being considered, in blocks
     */
    public static double contactMargin(double portalWidth) {
        double halfWidth = Math.max(0.0D, portalWidth) * 0.5D;
        return halfWidth + PLAYER_HALF_WIDTH;
    }

    public static double coordinateScale(double sourceScale, double destinationScale) {
        return sourceScale / destinationScale;
    }

    /**
     * Fallback coordinate scale for a dimension when its {@code DimensionType} is not at hand:
     * the nether is 8:1, everything else is 1:1.
     */
    public static double defaultCoordinateScale(ResourceKey<Level> dimension) {
        return isNetherDimension(dimension) ? 8.0D : 1.0D;
    }

    /** Ratio taking {@code source} coordinates into {@code destination} coordinates. */
    public static double scaleBetween(ResourceKey<Level> source, ResourceKey<Level> destination) {
        return coordinateScale(defaultCoordinateScale(source), defaultCoordinateScale(destination));
    }

    public static boolean isNetherDimension(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension);
    }

    /** The dimension a nether portal in {@code dimension} leads to. */
    public static ResourceKey<Level> targetDimension(ResourceKey<Level> dimension) {
        return isNetherDimension(dimension) ? Level.OVERWORLD : Level.NETHER;
    }

    /**
     * Search radius vanilla will use for a teleport that arrives in {@code destination}.
     */
    public static int radiusForDestination(ResourceKey<Level> destination) {
        return isNetherDimension(destination) ? RADIUS_TO_NETHER : RADIUS_TO_OVERWORLD;
    }

    /** Scale a source position into destination coordinates (no clamping). */
    public static Vec3 mapToDestination(Vec3 source, double scale) {
        return new Vec3(source.x * scale, source.y, source.z * scale);
    }

    /** Same as {@link #mapToDestination(Vec3, double)} but keeps the block-space rounding of vanilla. */
    public static BlockPos mapToDestination(BlockPos source, double scale) {
        return BlockPos.containing(source.getX() * scale, source.getY(), source.getZ() * scale);
    }

    /**
     * Inverse of {@link #mapToDestination(BlockPos, double)}: take a destination-space position and
     * express it in source coordinates. Used to draw a marker for a portal that lives in the other
     * dimension at the place it would correspond to here.
     */
    public static Vec3 mapBackToSource(Vec3 destination, double scale) {
        double safe = Math.abs(scale) < 1.0E-6D ? 1.0D : scale;
        return new Vec3(destination.x / safe, destination.y, destination.z / safe);
    }

    /** Block-space variant of {@link #mapBackToSource(Vec3, double)}. */
    public static BlockPos mapBackToSource(BlockPos destination, double scale) {
        double safe = Math.abs(scale) < 1.0E-6D ? 1.0D : scale;
        return BlockPos.containing(destination.getX() / safe, destination.getY(),
                destination.getZ() / safe);
    }

    /** Clamp to the destination world border the same way {@code getPortalDestination} does. */
    public static BlockPos clampToBorder(BlockPos pos, WorldBorder border) {
        if (border == null) {
            return pos;
        }
        return border.clampToBounds(pos.getX(), pos.getY(), pos.getZ());
    }

    /** A square column in X/Z spanning the whole build height: the real search volume. */
    public static AABB searchColumn(BlockPos center, int radius, int minY, int maxY) {
        return new AABB(center.getX() - radius, minY, center.getZ() - radius,
                center.getX() + radius + 1.0D, maxY, center.getZ() + radius + 1.0D);
    }

    public static boolean isInsideSearchSquare(BlockPos candidate, BlockPos origin, int radius) {
        return Math.abs(candidate.getX() - origin.getX()) <= radius
                && Math.abs(candidate.getZ() - origin.getZ()) <= radius;
    }

    /**
     * Vanilla ordering: squared 3D distance first, Y as tie breaker.
     */
    public static int compare(BlockPos a, BlockPos b, BlockPos origin) {
        int byDistance = Double.compare(a.distSqr(origin), b.distSqr(origin));
        return byDistance != 0 ? byDistance : Integer.compare(a.getY(), b.getY());
    }

    /** Direction the portal plane faces (X spanning portals face along Z). */
    public static Direction.Axis normalAxis(Direction.Axis horizontalAxis) {
        return horizontalAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }
}
