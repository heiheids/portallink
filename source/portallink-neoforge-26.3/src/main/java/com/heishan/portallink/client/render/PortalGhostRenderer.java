package com.heishan.portallink.client.render;

import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.LinkageMath;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.core.ScannedPortal;

import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the "ghost" of a partner portal: where the gate in the other dimension sits when its
 * coordinates are projected into this dimension, plus the connector line that ties the pair
 * together.
 *
 * <p>The ghost is pinned to the local portal's height on purpose. Vanilla ignores Y when matching,
 * so the only thing worth showing is <i>which horizontal spot</i> the partner occupies.</p>
 */
final class PortalGhostRenderer {

    private PortalGhostRenderer() {
    }

    /** Outline + marker for the partner, or nothing when the portal is unlinked. */
    static void render(PortalEntry entry, int color, PortalLinkConfig config, Vec3 eye,
                       double maxDistSq) {
        ScannedPortal matched = entry.matched();
        if (matched == null) {
            return;
        }
        PortalRef partner = matched.ref();
        double scale = LinkageMath.scaleBetween(entry.dimension(), entry.targetDimension());
        Vec3 ghostCenter = LinkageMath.mapBackToSource(partner.center(), scale);
        ghostCenter = new Vec3(ghostCenter.x, entry.portal().ref().center().y, ghostCenter.z);
        if (ghostCenter.distanceToSqr(eye) > maxDistSq * 4.0D) {
            return;
        }

        // The partner's size is unchanged by the coordinate scale, so its box can be translated.
        AABB partnerBox = partner.bounds();
        Vec3 offset = ghostCenter.subtract(partner.bounds().getCenter());
        AABB ghostBox = partnerBox.move(offset);

        int ghostColor = PortalPalette.withAlpha(color, 0xAA);
        PortalGizmoSupport.finish(Gizmos.cuboid(ghostBox, GizmoStyle.stroke(ghostColor)), config);
        PortalGizmoSupport.finish(Gizmos.point(ghostCenter, ghostColor, 8.0F), config);
        PortalGizmoSupport.finish(
                Gizmos.circle(ghostCenter.subtract(0.0D, partnerBox.getYsize() * 0.5D, 0.0D), 1.2F,
                        GizmoStyle.stroke(ghostColor)), config);
    }

    /** Straight line from the portal's top edge to its ghost. */
    static void renderConnector(PortalEntry entry, int color, PortalLinkConfig config, Vec3 eye,
                                double maxDistSq) {
        ScannedPortal matched = entry.matched();
        if (matched == null) {
            return;
        }
        double scale = LinkageMath.scaleBetween(entry.dimension(), entry.targetDimension());
        Vec3 from = entry.portal().ref().topCenter();
        Vec3 to = LinkageMath.mapBackToSource(matched.ref().center(), scale);
        to = new Vec3(to.x, from.y, to.z);
        if (from.distanceToSqr(eye) > maxDistSq && to.distanceToSqr(eye) > maxDistSq) {
            return;
        }
        PortalGizmoSupport.line(from, to, PortalPalette.withAlpha(color, 0xCC), config);
    }
}
