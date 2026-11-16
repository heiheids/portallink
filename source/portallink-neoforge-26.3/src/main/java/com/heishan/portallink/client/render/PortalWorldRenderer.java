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
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

/**
 * Entry point for in-world drawing, driven by NeoForge's debug renderer hook.
 *
 * <h2>Why a debug renderer is the right hook here</h2>
 * On this generation the client extracts render state first and submits geometry later, so there is
 * no "draw now" callback: gizmos are emitted during extraction. NeoForge exposes
 * {@code RegisterDebugRenderersEvent} for exactly that, and vanilla's own gizmo-only renderers
 * ({@code PoiDebugRenderer}, {@code ChunkDebugRenderer}, ...) are built the same way. The hook is
 * called once per frame by {@code LevelExtractor}, not only while the F3 debug screen is open
 * (verified in the 26.3 bytecode: {@code LevelExtractor} calls {@code DebugRenderer#emitGizmos}
 * unconditionally and {@code DebugRenderer} only gates its own <i>vanilla</i> entries on debug
 * screen entries).
 *
 * <h2>What is shown</h2>
 * <ul>
 *   <li><b>Claim areas</b> — where a newly built portal would link to a given gate. Areas are owned
 *       by the <i>opposite</i> dimension's portals and coloured per link group, so a pairing
 *       (nether gate + its overworld gate) shares one colour.</li>
 *   <li><b>Portal outlines</b> — the real frame of every portal here, in its own group colour.</li>
 *   <li><b>Ghosts and connectors</b> — where the partner sits once projected into this dimension.</li>
 *   <li><b>Labels</b> — status plus destination, again per group.</li>
 * </ul>
 */
public final class PortalWorldRenderer implements DebugRenderer.SimpleDebugRenderer {

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }

        PortalLinkConfig config = PortalLinkConfig.get();
        PortalSnapshot snapshot = ClientPortalLinkState.snapshot();
        // A snapshot belongs to one dimension. For the frame right after a teleport the previous
        // level's snapshot may still be in hand; drawing it here would put the other dimension's
        // columns into this level at coordinates that mean nothing here.
        if (snapshot != null && !snapshot.sourceDim().equals(client.level.dimension())) {
            snapshot = null;
        }
        if (!config.enabled || snapshot == null || snapshot.isEmpty()) {
            return;
        }

        // The camera position is what the frustum and the engine use, and it is what "how far is this
        // from me" means for culling. Sampling it here also keeps the areas' own coordinates free of
        // any camera term, which is what stops them from sliding while walking.
        Vec3 eye = new Vec3(camX, camY, camZ);
        double maxDistSq = (double) config.maxRenderDistance * config.maxRenderDistance;

        LinkGroups groups = LinkGroups.of(snapshot);
        LinkColorAssigner colors = new LinkColorAssigner(snapshot, groups);

        if (config.renderRanges) {
            List<RangeMarker> markers = RangePlanner.plan(snapshot, groups,
                    client.level.getMinY(), client.level.getMaxY());
            PortalRangeRenderer.render(markers, colors, config, eye, maxDistSq);
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

    /** Outline, ghost, connector and label of one portal. */
    private static void drawPortal(PortalEntry entry, int color, PortalLinkConfig config, Vec3 eye,
                                   double maxDistSq) {
        // Wireframe hugging the portal blocks, so the frame itself is unambiguous.
        PortalGizmoSupport.finish(Gizmos.cuboid(entry.portal().ref().bounds().inflate(0.02D),
                GizmoStyle.stroke(PortalPalette.withAlpha(color, 0xFF))), config);
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
