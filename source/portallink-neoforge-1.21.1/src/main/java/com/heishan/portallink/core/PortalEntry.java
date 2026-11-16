package com.heishan.portallink.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One portal plus the answer to "where does this portal send me?".
 *
 * @param portal        the portal frame as scanned in its own dimension
 * @param targetAnchor  arrival anchor in the destination dimension (scaled and border-clamped)
 * @param radius        vanilla search radius used at that destination (16 towards the nether,
 *                      128 towards the overworld)
 * @param matched       the portal vanilla would pick, or {@code null}
 * @param matchedHit    the exact portal block vanilla would arrive at, or {@code null}
 * @param complete      whether the destination search area was fully inspected
 * @param status        link status derived from the above
 */
public record PortalEntry(ScannedPortal portal,
                          BlockPos targetAnchor,
                          int radius,
                          ScannedPortal matched,
                          BlockPos matchedHit,
                          boolean complete,
                          LinkStatus status) {

    public ResourceKey<Level> dimension() {
        return portal.ref().dimension();
    }

    public ResourceKey<Level> targetDimension() {
        return LinkageMath.targetDimension(dimension());
    }

    public boolean hasMatch() {
        return matched != null;
    }

    public String key() {
        return portal.key();
    }

    /**
     * Horizontal distance from the arrival anchor to the matched portal block, or {@code -1} when
     * unlinked.
     *
     * <p>Horizontal only, because vanilla's portal search ignores Y entirely: a portal 60 blocks
     * above the anchor still counts as "right here". Reporting the 3D distance would make a perfect
     * link look broken.</p>
     */
    public double matchedDistance() {
        if (matchedHit == null) {
            return -1.0D;
        }
        double dx = matchedHit.getX() - targetAnchor.getX();
        double dz = matchedHit.getZ() - targetAnchor.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public PortalEntry withMatch(ScannedPortal newMatch, BlockPos newHit, boolean destinationComplete,
                                 LinkStatus newStatus) {
        return new PortalEntry(portal, targetAnchor, radius, newMatch, newHit, destinationComplete,
                newStatus);
    }

    public PortalEntry withStatus(LinkStatus newStatus) {
        return new PortalEntry(portal, targetAnchor, radius, matched, matchedHit, complete, newStatus);
    }
}
