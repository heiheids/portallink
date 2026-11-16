package com.heishan.portallink.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Pure-Java implementation of the vanilla portal lookup, used when the authoritative
 * {@code PortalForcer} is not available (multiplayer clients).
 *
 * <p>Vanilla semantics reproduced here:</p>
 * <ul>
 *   <li>the search origin is the source portal's centre scaled by the coordinate ratio and
 *       clamped to the destination world border;</li>
 *   <li>candidates are individual portal <b>blocks</b> inside a square of the destination radius
 *       (16 towards the nether, 128 towards the overworld), ignoring Y entirely;</li>
 *   <li>the winner is the smallest squared 3D distance, Y ascending as the tie breaker.</li>
 * </ul>
 */
public final class LinkageResolver {

    private LinkageResolver() {
    }

    /**
     * Resolve a single portal against a candidate list.
     *
     * @param portal      source portal
     * @param candidates  every portal known in the destination dimension
     * @param scale       {@code sourceScale / destinationScale}
     * @param radius      vanilla search radius at the destination
     * @param destBorder  destination world border, may be {@code null}
     * @param complete    whether {@code candidates} covers the whole search square
     */
    public static PortalEntry resolve(ScannedPortal portal,
                                      List<ScannedPortal> candidates,
                                      double scale,
                                      int radius,
                                      WorldBorder destBorder,
                                      boolean complete) {
        BlockPos anchor = LinkageMath.clampToBorder(
                LinkageMath.mapToDestination(portal.ref().centerPos(), scale), destBorder);

        BlockPos bestHit = null;
        ScannedPortal bestPortal = null;
        for (ScannedPortal candidate : candidates) {
            for (BlockPos block : candidate.blocks()) {
                if (!LinkageMath.isInsideSearchSquare(block, anchor, radius)) {
                    continue;
                }
                if (bestHit == null || LinkageMath.compare(block, bestHit, anchor) < 0) {
                    bestHit = block;
                    bestPortal = candidate;
                }
            }
        }

        LinkStatus status = bestPortal != null ? LinkStatus.LINKED : LinkStatus.WILL_CREATE;
        return new PortalEntry(portal, anchor, radius, bestPortal, bestHit, complete, status);
    }

    /** Resolve every portal of one dimension against the other dimension's portal list. */
    public static List<PortalEntry> resolveAll(List<ScannedPortal> sourcePortals,
                                               List<ScannedPortal> candidates,
                                               double scale,
                                               int radius,
                                               WorldBorder destBorder,
                                               boolean complete) {
        List<PortalEntry> entries = new ArrayList<>(sourcePortals.size());
        for (ScannedPortal portal : sourcePortals) {
            entries.add(resolve(portal, candidates, scale, radius, destBorder, complete));
        }
        return entries;
    }

    /**
     * Mark asymmetric links: a portal is in {@link LinkStatus#CONFLICT} when the partner it leads
     * to would send the player somewhere else instead of back to it (the classic "串门").
     *
     * @param entries        entries of the source dimension
     * @param backLinks      entries of the destination dimension, resolved against the source list
     */
    public static List<PortalEntry> applyConflicts(List<PortalEntry> entries,
                                                   List<PortalEntry> backLinks) {
        List<PortalEntry> result = new ArrayList<>(entries.size());
        for (PortalEntry entry : entries) {
            if (!entry.hasMatch()) {
                result.add(entry);
                continue;
            }
            boolean symmetric = false;
            for (PortalEntry back : backLinks) {
                if (back.key().equals(entry.matched().key())) {
                    symmetric = back.hasMatch() && back.matched().key().equals(entry.key());
                    break;
                }
            }
            result.add(symmetric ? entry : entry.withStatus(LinkStatus.CONFLICT));
        }
        return result;
    }
}
