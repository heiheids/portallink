package com.heishan.portallink.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A single nether portal frame, described by the rectangle of portal blocks it is made of.
 *
 * <p>The rectangle always lies in a vertical plane: {@code axis} is the horizontal axis the
 * portal's width runs along (the block state's {@code HORIZONTAL_AXIS} property). For
 * {@link Direction.Axis#X} the portal spans X (width) and Y (height) and its plane normal is Z;
 * for {@link Direction.Axis#Z} it spans Z (width) and Y (height) and its normal is X.</p>
 */
public record PortalRef(ResourceKey<Level> dimension,
                        Direction.Axis axis,
                        BlockPos minCorner,
                        int width,
                        int height) {

    public static final int MAX_SIZE = 21;

    public PortalRef {
        if (axis == Direction.Axis.Y) {
            throw new IllegalArgumentException("a nether portal cannot span the Y axis");
        }
        width = Math.max(1, Math.min(width, MAX_SIZE));
        height = Math.max(1, Math.min(height, MAX_SIZE));
    }

    /** Inclusive maximum block position (still inside the portal). */
    public BlockPos maxCorner() {
        int dx = axis == Direction.Axis.X ? width - 1 : 0;
        int dz = axis == Direction.Axis.Z ? width - 1 : 0;
        return minCorner.offset(dx, height - 1, dz);
    }

    /** Bounding box of the portal blocks, exactly covering them (no inflation). */
    public AABB bounds() {
        BlockPos max = maxCorner();
        return new AABB(minCorner.getX(), minCorner.getY(), minCorner.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    /** Geometric centre of the portal plane, in world coordinates. */
    public Vec3 center() {
        return bounds().getCenter();
    }

    /** Block position closest to the centre of the portal. */
    public BlockPos centerPos() {
        BlockPos max = maxCorner();
        return new BlockPos((minCorner.getX() + max.getX()) / 2,
                (minCorner.getY() + max.getY()) / 2,
                (minCorner.getZ() + max.getZ()) / 2);
    }

    /** Point just above the top edge of the portal, used for labels and the marker pillar. */
    public Vec3 topCenter() {
        BlockPos max = maxCorner();
        return new Vec3((minCorner.getX() + max.getX() + 1.0D) * 0.5D,
                max.getY() + 1.0D,
                (minCorner.getZ() + max.getZ() + 1.0D) * 0.5D);
    }

    public boolean isNether() {
        return Level.NETHER.equals(dimension);
    }

    public int blockCount() {
        return width * height;
    }

    /**
     * Stable, human readable key. Uses the dimension path plus the minimum corner so that two
     * different portals never collapse into one label.
     */
    public String key() {
        return dimension.identifier().getPath() + "@" + minCorner.getX() + ","
                + minCorner.getY() + "," + minCorner.getZ();
    }

    /**
     * The position vanilla uses as the source of the teleport: the portal's centre block.
     */
    public BlockPos anchorPos() {
        return centerPos();
    }
}
