package com.heishan.portallink.client.render;

import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.LinkageMath;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.core.ScannedPortal;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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
    static void render(PoseStack pose, MultiBufferSource buffers, PortalEntry entry, int color,
                       PortalLinkConfig config, Vec3 eye, double maxDistSq) {
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
        VertexConsumer buffer = buffers.getBuffer(PortalRenderTypes.lines(config.alwaysOnTop));
        float red = ((ghostColor >> 16) & 0xFF) / 255.0F;
        float green = ((ghostColor >> 8) & 0xFF) / 255.0F;
        float blue = (ghostColor & 0xFF) / 255.0F;
        float alpha = ((ghostColor >> 24) & 0xFF) / 255.0F;
        LevelRenderer.renderLineBox(pose, buffer, ghostBox, red, green, blue, alpha);

        // A marker at the ghost's centre makes it findable when the outline is far away.
        double s = 0.12D;
        LevelRenderer.renderLineBox(pose, buffer,
                new AABB(ghostCenter.x - s, ghostCenter.y - s, ghostCenter.z - s,
                        ghostCenter.x + s, ghostCenter.y + s, ghostCenter.z + s),
                red, green, blue, alpha);
    }

    /** Straight line from the portal's top edge to its ghost. */
    static void renderConnector(PoseStack pose, MultiBufferSource buffers, PortalEntry entry,
                                int color, PortalLinkConfig config, Vec3 eye, double maxDistSq) {
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
        VertexConsumer buffer = buffers.getBuffer(PortalRenderTypes.lines(config.alwaysOnTop));
        PortalRenderSupport.line(buffer, pose.last(), from, to,
                PortalPalette.withAlpha(color, 0xCC), 0.0F, 1.0F, 0.0F);
    }
}
