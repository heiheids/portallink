package com.heishan.portallink.client.render;

import java.util.List;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.LinkGroups;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.core.PortalSnapshot;
import com.heishan.portallink.core.RangeMarker;
import com.heishan.portallink.core.RangePlanner;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Entry point for in-world drawing. This class only orchestrates; the actual drawing lives in
 * {@link PortalRangeRenderer}, {@link PortalGhostRenderer} and {@link PortalLabelRenderer}.
 *
 * <h2>What is shown</h2>
 * <ul>
 *   <li><b>Claim squares</b> — where a newly built portal would link to a given gate. Squares are
 *       owned by the <i>opposite</i> dimension's portals and coloured per link group, so a pairing
 *       (nether gate + its overworld gate) shares one colour.</li>
 *   <li><b>Portal outlines</b> — the real frame of every portal here, in its own group colour.</li>
 *   <li><b>Ghosts and connectors</b> — where the partner sits once projected into this dimension.</li>
 *   <li><b>Labels</b> — status plus destination, again per group.</li>
 * </ul>
 *
 * <p>A portal that stands inside another gate's square but links elsewhere is not merged into it:
 * it belongs to a different group, so it gets its own colour, its own square and its own label.</p>
 */
public final class PortalLinkRenderer {

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(PortalLink.MOD_ID, "portal_ranges");

    /** Timestamp of the snapshot whose columns were last logged, so it happens once per scan. */
    private static long lastLoggedTimestamp = -1L;

    /** Last frame timestamp, used to size the gizmo retention window to the real frame time. */
    private static long lastFrameNanos;

    /**
     * Measured frame time in milliseconds.
     *
     * <p>Gizmo retention is expressed in milliseconds but leaves one copy per frame, so the number of
     * copies is frame-rate dependent and translucent fills have to be divided by it. Measuring the
     * frame time keeps the perceived alpha the same at 30, 60 or 240 fps.</p>
     */
    private static double frameMillis() {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous == 0L) {
            return 16.7D;
        }
        return Math.max(1.0D, Math.min(200.0D, (now - previous) / 1_000_000.0D));
    }

    private PortalLinkRenderer() {
    }

    public static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        PortalLinkConfig config = PortalLinkConfig.get();
        if (!config.enabled) {
            return;
        }
        PortalSnapshot snapshot = ClientPortalLinkState.snapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }

        Vec3 eye = client.player.getEyePosition();
        double maxDistSq = (double) config.maxRenderDistance * config.maxRenderDistance;

        LinkGroups groups = LinkGroups.of(snapshot);
        LinkColorAssigner colors = new LinkColorAssigner(snapshot, groups);

        if (config.renderRanges) {
            // The area is pinned to the gate, never to the player: a range must not slide around
            // while walking. It is drawn as a floor plane at the player's own height, which is what
            // makes it read as fixed - a full-height wall fills the screen and looks like it moves.
            List<RangeMarker> markers = RangePlanner.plan(snapshot, groups,
                    client.level.getMinY(), client.level.getMaxY());
            logOncePerScan(snapshot, markers);
            PortalRangeRenderer.render(context, markers, colors, config, eye, maxDistSq);
        }

        int drawn = 0;
        for (PortalEntry entry : snapshot.entries()) {
            if (drawn >= config.maxRenderedPortals) {
                break;
            }
            PortalRef portal = entry.portal().ref();
            if (portal.center().distanceToSqr(eye) > maxDistSq) {
                continue;
            }
            drawn++;
            drawPortal(entry, colors.colorOf(entry, groups), config, eye, maxDistSq);
        }
    }

    /**
     * Log one line per scan describing the claim columns exactly.
     *
     * <p>Geometry was guessed at three times in a row from screenshots and was wrong each time. A
     * single machine-readable line per scan turns "the range looks small" into concrete numbers
     * (half edge, block bounds, band heights) that can be checked against the arithmetic.</p>
     */
    private static void logOncePerScan(PortalSnapshot snapshot, List<RangeMarker> markers) {
        if (snapshot.timestamp() == lastLoggedTimestamp) {
            return;
        }
        lastLoggedTimestamp = snapshot.timestamp();
        if (!PortalLink.LOGGER.isInfoEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder("[ranges] columns=").append(markers.size())
                .append(" entriesHere=").append(snapshot.entries().size())
                .append(" entriesOther=").append(snapshot.otherEntries().size())
                .append(" otherKnown=").append(snapshot.otherKnown())
                .append(" authoritative=").append(snapshot.authoritative());
        for (RangeMarker marker : markers) {
            sb.append(" | group=").append(shortGroup(marker.groupKey()))
                    .append(" owner=").append(marker.ownerPos().getX()).append(',')
                    .append(marker.ownerPos().getY()).append(',')
                    .append(marker.ownerPos().getZ())
                    .append(" half=").append((int) marker.half())
                    .append(" x=").append(marker.minBlockX()).append("..").append(marker.maxBlockX())
                    .append(" z=").append(marker.minBlockZ()).append("..").append(marker.maxBlockZ())
                    .append(" y=").append((int) marker.bottomY()).append("..")
                    .append((int) marker.topY());
        }
        PortalLink.LOGGER.info(sb.toString());
    }

    /** Group ids contain control characters; keep the log readable. */
    private static String shortGroup(String groupKey) {
        return groupKey.length() <= 40 ? groupKey.replace('\u0001', '/')
                : groupKey.substring(0, 40).replace('\u0001', '/') + "..";
    }

    private static void drawPortal(PortalEntry entry, int color, PortalLinkConfig config, Vec3 eye,
                                   double maxDistSq) {        // Wireframe hugging the portal blocks, so the frame itself is unambiguous.
        PortalGizmoSupport.finish(
                Gizmos.cuboid(entry.portal().ref().bounds().inflate(0.02D), GizmoStyle.stroke(color)),
                config);

        if (config.renderGhosts) {
            PortalGhostRenderer.render(entry, color, config, eye, maxDistSq);
        }
        if (config.renderConnectors) {
            PortalGhostRenderer.renderConnector(entry, color, config, eye, maxDistSq);
        }
        if (config.renderLabels) {
            PortalLabelRenderer.render(entry, color, config);
        }
    }
}
