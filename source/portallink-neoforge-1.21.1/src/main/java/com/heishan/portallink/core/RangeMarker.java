package com.heishan.portallink.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * One vertical slice of a claim range: the horizontal square (block aligned) plus the Y band that
 * this particular gate owns inside it.
 *
 * <h2>Why a column and not a plane</h2>
 * Vanilla searches for a partner with {@code PoiManager#getInSquare}, a square in X/Z with
 * <b>no Y bound at all</b>. The volume a gate competes for is therefore a full-height column, not a
 * flat sheet at one height. Drawing a plane was actively misleading: it implied only one height
 * mattered.
 *
 * <h2>Why the column is split into bands</h2>
 * The ranking comparator is {@code BlockPos#distSqr}, which is <b>three dimensional</b>, with Y as
 * the tie breaker. So Y does decide the winner. If two gates sit at the same X/Z but different
 * heights, the boundary between them is exactly the midpoint of their Y values: a new portal above
 * that line links to the upper gate, below it to the lower one.
 *
 * <p>Hence a shared column is cut into bands, one per gate, each in that gate's colour. That is the
 * only rendering that answers "which gate will this spot link to" correctly when heights compete.</p>
 *
 * @param center        X/Z anchor of the column; {@code y} is the middle of the band, used for labels
 * @param half          half edge length of the square, in blocks of the current dimension
 * @param bottomY       inclusive bottom of this band
 * @param topY          inclusive top of this band
 * @param ownerPos      centre of the gate that owns this band, in its own dimension
 * @param ownerIsNether whether the owning gate lives in the nether
 * @param existing      {@code true} when the gate really exists
 * @param groupKey      link group this band belongs to; decides its colour
 */
public record RangeMarker(Vec3 center,
                          double half,
                          double bottomY,
                          double topY,
                          BlockPos ownerPos,
                          boolean ownerIsNether,
                          boolean existing,
                          String groupKey) {

    /** Squares larger than this are clamped; 2048 blocks is already the whole world. */
    public static final double MAX_HALF = 2048.0D;

    public RangeMarker {
        if (half < 0.5D) {
            half = 0.5D;
        }
        if (half > MAX_HALF) {
            half = MAX_HALF;
        }
        if (topY < bottomY) {
            double swap = bottomY;
            bottomY = topY;
            topY = swap;
        }
    }

    // ---- Block aligned horizontal bounds ---------------------------------------------------

    public int minBlockX() {
        return (int) Math.floor(center.x - half);
    }

    public int maxBlockX() {
        return (int) Math.floor(center.x + half);
    }

    public int minBlockZ() {
        return (int) Math.floor(center.z - half);
    }

    public int maxBlockZ() {
        return (int) Math.floor(center.z + half);
    }

    /** Width in blocks, always at least 1. */
    public int blockWidth() {
        return maxBlockX() - minBlockX() + 1;
    }

    /** Depth in blocks, always at least 1. */
    public int blockDepth() {
        return maxBlockZ() - minBlockZ() + 1;
    }

    public int blockCount() {
        return blockWidth() * blockDepth();
    }

    /** Height in blocks of this band. */
    public double bandHeight() {
        return topY - bottomY;
    }

    /** Whether a block position falls inside this band (horizontal and vertical). */
    public boolean contains(int blockX, int blockY, int blockZ) {
        return blockX >= minBlockX() && blockX <= maxBlockX()
                && blockZ >= minBlockZ() && blockZ <= maxBlockZ()
                && blockY >= bottomY && blockY <= topY;
    }

    /** New marker with the same column but a different Y band. */
    public RangeMarker withBand(double newBottomY, double newTopY) {
        double mid = (newBottomY + newTopY) * 0.5D;
        return new RangeMarker(new Vec3(center.x, mid, center.z), half, newBottomY, newTopY,
                ownerPos, ownerIsNether, existing, groupKey);
    }
}
