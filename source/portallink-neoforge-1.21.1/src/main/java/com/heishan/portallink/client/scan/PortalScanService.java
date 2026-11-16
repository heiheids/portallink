package com.heishan.portallink.client.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.heishan.portallink.core.DimensionPortals;
import com.heishan.portallink.core.LinkageMath;
import com.heishan.portallink.core.LinkageResolver;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalFrameBuilder;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.core.PortalSnapshot;
import com.heishan.portallink.core.ScannedPortal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Builds a {@link PortalSnapshot}.
 *
 * <p>Two entry points, with different threading rules:</p>
 * <ul>
 *   <li>{@link #build} - <b>server thread only</b>, uses the authoritative point-of-interest index.
 *       It never touches client state, so it is safe to call from a scheduled server task.</li>
 *   <li>{@link #buildOnClientThread} - client thread only, reads already loaded chunks. The
 *       multiplayer fallback.</li>
 * </ul>
 *
 * <p>The opposite dimension is always scanned around the <b>mapped</b> position, because that is
 * where vanilla looks for a partner portal.</p>
 */
public final class PortalScanService {

    /** Client-side scans are capped to keep a single frame cheap. */
    private static final int MAX_CLIENT_RADIUS = 128;

    private PortalScanService() {
    }

    /** Extra radius, in the opposite dimension's blocks, used when scanning it. */
    private static final int OTHER_DIM_MARGIN = 32;

    /** Most source portals to use as scan anchors; keeps a crowded area from spamming the POI query. */
    private static final int MAX_SCAN_ANCHORS = 8;

    /** Radius used for the opposite dimension: what vanilla searches plus a stability margin. */
    private static int otherRadius(int radius) {
        return radius + OTHER_DIM_MARGIN;
    }

    /**
     * Centres used to scan the opposite dimension: one per portal found <b>here</b>.
     *
     * <p>Never the player. Scanning around the mapped player position meant the query window slid
     * with the player, so the gates found - and therefore the columns drawn - were always the ones
     * around wherever the player currently stood. That is what made the range appear to follow the
     * player, at a constant distance, even hundreds of blocks away from the portal.</p>
     *
     * <p>Returning an <b>empty</b> list when this dimension has no portals is deliberate: with
     * nothing to link, there is nothing to show, and the alternative (falling back to the player)
     * resurrects the bug.</p>
     */
    private static List<BlockPos> otherCenters(List<ScannedPortal> sourcePortals, double scale) {
        List<BlockPos> centers = new ArrayList<>();
        for (ScannedPortal portal : sourcePortals) {
            if (centers.size() >= MAX_SCAN_ANCHORS) {
                break;
            }
            centers.add(LinkageMath.mapToDestination(portal.ref().centerPos(), scale));
        }
        return centers;
    }

    /**
     * Authoritative scan. Server thread only.
     *
     * @param server     the integrated server
     * @param sourceDim  dimension the player is standing in
     * @param playerPos  player position, captured on the client thread beforehand
     */
    public static PortalSnapshot build(MinecraftServer server, ResourceKey<Level> sourceDim,
                                       BlockPos playerPos) {
        ResourceKey<Level> otherDim = LinkageMath.targetDimension(sourceDim);
        int radius = LinkageMath.radiusForDestination(otherDim);
        double scale = LinkageMath.scaleBetween(sourceDim, otherDim);
        BlockPos mappedCenter = LinkageMath.mapToDestination(playerPos, scale);

        Optional<ServerLevel> sourceLevel = levelFor(server, sourceDim);
        Optional<ServerLevel> otherLevel = levelFor(server, otherDim);

        DimensionPortals source = sourceLevel
                .map(level -> scanServer(level, sourceDim, playerPos, hostRadius(radius)))
                .orElseGet(() -> DimensionPortals.empty(sourceDim));
        List<BlockPos> anchors = otherCenters(source.portals(), scale);
        DimensionPortals other = (otherLevel.isPresent() && !anchors.isEmpty())
                ? scanServerAround(otherLevel.get(), otherDim, anchors, otherRadius(radius))
                : DimensionPortals.empty(otherDim);

        return assemble(sourceDim, source, other, scale, radius,
                sourceLevel.map(ServerLevel::getWorldBorder).orElse(null),
                otherLevel.map(ServerLevel::getWorldBorder).orElse(null), true);
    }

    /** Best-effort client scan. Client thread only; safe on any server. */
    public static PortalSnapshot buildOnClientThread(Minecraft client, ClientLevel clientLevel) {
        ResourceKey<Level> sourceDim = clientLevel.dimension();
        ResourceKey<Level> otherDim = LinkageMath.targetDimension(sourceDim);
        BlockPos playerPos = playerAnchor(client, clientLevel);

        int radius = LinkageMath.radiusForDestination(otherDim);
        double scale = LinkageMath.scaleBetween(sourceDim, otherDim);
        BlockPos mappedCenter = LinkageMath.mapToDestination(playerPos, scale);

        DimensionPortals source = scanClient(clientLevel, sourceDim, playerPos,
                Math.min(hostRadius(radius), MAX_CLIENT_RADIUS));
        List<BlockPos> anchors = otherCenters(source.portals(), scale);
        DimensionPortals other = (clientLevel.dimension().equals(otherDim) && !anchors.isEmpty())
                ? scanClientAround(clientLevel, otherDim, anchors,
                        Math.min(otherRadius(radius), MAX_CLIENT_RADIUS))
                : DimensionPortals.empty(otherDim);

        return assemble(sourceDim, source, other, scale, radius, clientLevel.getWorldBorder(), null,
                false);
    }

    private static PortalSnapshot assemble(ResourceKey<Level> sourceDim, DimensionPortals source,
                                           DimensionPortals other, double scale, int radius,
                                           WorldBorder sourceBorder, WorldBorder destBorder,
                                           boolean authoritative) {
        long startedAt = System.currentTimeMillis();
        List<PortalEntry> forward = LinkageResolver.resolveAll(source.portals(), other.portals(),
                scale, radius, destBorder, other.complete());

        double backScale = 1.0D / scale;
        int backRadius = LinkageMath.radiusForDestination(sourceDim);
        List<PortalEntry> backward = LinkageResolver.resolveAll(other.portals(), source.portals(),
                backScale, backRadius, sourceBorder, source.complete());

        // Conflicts are judged in both directions so a one-way link is flagged from either side.
        List<PortalEntry> entries = new ArrayList<>(
                LinkageResolver.applyConflicts(forward, backward));
        List<PortalEntry> otherEntries = new ArrayList<>(
                LinkageResolver.applyConflicts(backward, forward));

        // One line that says which dimension each list really belongs to. Without it a snapshot that
        // carries the other dimension's gate looks identical to a correct one in the overlay.
        com.heishan.portallink.PortalLink.LOGGER.info("[scan] {} ms | sourceDim={} source={}({} found) "
                        + "other={}({} found) entries={} otherEntries={}",
                System.currentTimeMillis() - startedAt,
                sourceDim.location(), source.dimension().location(), source.count(),
                other.dimension().location(), other.count(), entries.size(),
                otherEntries.size());

        return new PortalSnapshot(sourceDim, source, other, entries, otherEntries,
                other.complete() || other.count() > 0, authoritative,
                System.currentTimeMillis());
    }

    /**
     * Scan one dimension around several centres and merge the results.
     *
     * <p>Merging is by portal key, so a gate found from two anchors is only counted once and its
     * frame is never rebuilt from a partial block set.</p>
     */
    private static DimensionPortals scanServerAround(ServerLevel level,
                                                     ResourceKey<Level> dimension,
                                                     List<BlockPos> centers, int radius) {
        DimensionPortals merged = DimensionPortals.empty(dimension);
        for (BlockPos center : centers) {
            merged = merged.plus(scanServer(level, dimension, center, radius));
        }
        return merged;
    }

    /** Client-side equivalent of {@link #scanServerAround}. */
    private static DimensionPortals scanClientAround(ClientLevel level,
                                                     ResourceKey<Level> dimension,
                                                     List<BlockPos> centers, int radius) {
        DimensionPortals merged = DimensionPortals.empty(dimension);
        for (BlockPos center : centers) {
            merged = merged.plus(scanClient(level, dimension, center, radius));
        }
        return merged;
    }

    private static DimensionPortals scanServer(ServerLevel level, ResourceKey<Level> dimension,
                                               BlockPos center, int radius) {
        // Query wider than the requested radius, then keep only portals that are fully inside it.
        //
        // Reason: the point-of-interest query returns individual portal *blocks*, and a portal
        // straddling the window edge comes back truncated. PortalFrameBuilder derives the frame
        // extents from the block set, so a truncated portal gets a shorter width and a shifted
        // centre. Because the window is centred on the player, that made the frame - and therefore
        // the claim column derived from it - slide as the player walked. Widening the query means
        // every portal that is kept is complete, and the kept set is a pure function of the radius.
        ServerPortalScanner.Result result = ServerPortalScanner.scan(level, center,
                radius + PORTAL_MARGIN);
        List<ScannedPortal> portals = PortalFrameBuilder.build(level, dimension,
                result.portalBlocks());
        return new DimensionPortals(dimension, fullyInside(portals, center, radius),
                result.complete());
    }

    private static DimensionPortals scanClient(ClientLevel level, ResourceKey<Level> dimension,
                                               BlockPos center, int radius) {
        ClientPortalScanner.Result result = ClientPortalScanner.scan(level, center,
                radius + PORTAL_MARGIN);
        List<ScannedPortal> portals = PortalFrameBuilder.build(level, dimension,
                result.portalBlocks());
        return new DimensionPortals(dimension, fullyInside(portals, center, radius),
                result.complete());
    }

    /** Extra query radius so a portal straddling the boundary is captured whole. */
    private static final int PORTAL_MARGIN = PortalRef.MAX_SIZE;

    /**
     * Drop portals that are not entirely within the requested square.
     *
     * <p>Such a portal may have been truncated by the query, so its frame cannot be trusted. Dropping
     * it keeps the result deterministic: the same radius always yields the same set, instead of a set
     * that shifts with the player.</p>
     */
    private static List<ScannedPortal> fullyInside(List<ScannedPortal> portals, BlockPos center,
                                                   int radius) {
        List<ScannedPortal> kept = new ArrayList<>(portals.size());
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;
        for (ScannedPortal portal : portals) {
            PortalRef ref = portal.ref();
            BlockPos max = ref.maxCorner();
            if (ref.minCorner().getX() >= minX && max.getX() <= maxX
                    && ref.minCorner().getZ() >= minZ && max.getZ() <= maxZ) {
                kept.add(portal);
            }
        }
        return kept;
    }

    /**
     * Radius used to find portals <b>here</b>. It has to be wide enough for every portal that
     * could map next to the player, so it uses the largest radius vanilla applies on arrival, plus a
     * margin.
     *
     * <p>The margin is what stops ranges from appearing and disappearing as the player walks: without
     * it a portal drops out of the scan the moment the player is more than one radius away, so the
     * only ranges ever visible are the ones around the player's current position.</p>
     */
    private static int hostRadius(int destinationRadius) {
        return Math.max(destinationRadius, LinkageMath.RADIUS_TO_OVERWORLD) + HOST_MARGIN;
    }

    /** Extra radius for scanning the player's own dimension, to keep ranges visible while walking. */
    private static final int HOST_MARGIN = 128;

    public static Optional<ServerLevel> levelFor(MinecraftServer server,
                                                 ResourceKey<Level> dimension) {
        if (server == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(server.getLevel(dimension));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static BlockPos playerAnchor(Minecraft client, ClientLevel level) {
        if (client.player != null) {
            return client.player.blockPosition();
        }
        return new BlockPos(0, level.getMinBuildHeight() + 64, 0);
    }
}
