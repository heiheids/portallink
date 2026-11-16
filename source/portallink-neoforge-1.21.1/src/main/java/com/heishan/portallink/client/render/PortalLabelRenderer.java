package com.heishan.portallink.client.render;

import com.heishan.portallink.client.PortalIdStore;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.i18n.PortalI18n;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Floating labels above portals and at the centre of claim squares.
 *
 * <p>Each label carries the colour of its link group, so a label always belongs to exactly one
 * pairing. The status word (linked / one-way / new gate / unknown) is kept as the first line
 * because it is the part the player scans for.</p>
 *
 * <p>Billboarding is delegated to {@code DebugRenderer#renderFloatingText}: it translates by the
 * camera position, rotates by the camera orientation and scales the text, so the label always faces
 * the player. Its {@code seeThrough} flag is what turns the "see through terrain" preference into
 * the text being drawn after the depth test is disabled.</p>
 */
final class PortalLabelRenderer {

    /** Vanilla uses 0.02 for its debug text; the label needs to be readable at range. */
    private static final float BASE_SCALE = 0.025F;

    private PortalLabelRenderer() {
    }

    /** Two-line label above the portal: number and status, then where it leads. */
    static void render(PoseStack pose, MultiBufferSource buffers, PortalEntry entry, int color,
                       PortalLinkConfig config) {
        // Clear the frame and its top obsidian row, otherwise the second line sits on the portal.
        Vec3 anchor = entry.portal().ref().topCenter().add(0.0D, 1.9D, 0.0D);
        float base = BASE_SCALE * config.labelScale;

        // The number comes first so two portals can be told apart at a glance; reading coordinates
        // and holding them in your head is what the numbering exists to avoid.
        floatingText(pose, buffers,
                PortalI18n.tr("portallink.label.numbered",
                        PortalIdStore.get().idOf(entry.portal().ref()),
                        PortalI18n.tr("portallink.status." + entry.status().name().toLowerCase())),
                anchor.x, anchor.y, anchor.z, color, base, config.alwaysOnTop);
        floatingText(pose, buffers, describePartner(entry),
                anchor.x, anchor.y - 0.85D, anchor.z,
                PortalPalette.withAlpha(color, 0xCC), base * 0.8F, config.alwaysOnTop);
    }

    /** One billboarded line of text at a world position. */
    static void floatingText(PoseStack pose, MultiBufferSource buffers, String text,
                             double x, double y, double z, int color, float scale,
                             boolean seeThrough) {
        try {
            DebugRenderer.renderFloatingText(pose, buffers, text, x, y, z, color, scale,
                    true, 0.0F, seeThrough);
        } catch (RuntimeException exception) {
            // A label that cannot be drawn must never take the frame down.
            com.heishan.portallink.PortalLink.LOGGER.debug("could not draw label {}", text,
                    exception);
        }
    }

    private static String describePartner(PortalEntry entry) {
        if (!entry.hasMatch()) {
            BlockPos anchor = entry.targetAnchor();
            return PortalI18n.tr("portallink.label.will_create",
                    anchor.getX(), anchor.getY(), anchor.getZ());
        }
        PortalRef partner = entry.matched().ref();
        BlockPos pos = partner.centerPos();
        // The partner's number is what makes a "串门" (link stealing) pair identifiable: two portals
        // pointing at the same number are the ones competing.
        return PortalI18n.tr("portallink.label.partner_numbered",
                PortalIdStore.get().idOf(partner),
                pos.getX(), pos.getY(), pos.getZ(), Math.round(entry.matchedDistance()));
    }
}
