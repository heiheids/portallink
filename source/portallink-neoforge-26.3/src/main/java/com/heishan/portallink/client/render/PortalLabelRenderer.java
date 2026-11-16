package com.heishan.portallink.client.render;

import com.heishan.portallink.client.PortalIdStore;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.i18n.PortalI18n;

import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/**
 * Floating labels above portals and at the centre of claim squares.
 *
 * <p>Each label carries the colour of its link group, so a label always belongs to exactly one
 * pairing. The status word (linked / one-way / new gate / unknown) is kept as the first line
 * because it is the part the player scans for.</p>
 */
final class PortalLabelRenderer {

    /** Vanilla uses 0.32 for its own billboard text; slightly larger stays readable. */
    private static final float LABEL_BASE_SCALE = 0.5F;

    private PortalLabelRenderer() {
    }

    /** Two-line label above the portal: number and status, then where it leads. */
    static void render(PortalEntry entry, int color, PortalLinkConfig config) {
        // Clear the frame and its top obsidian row, otherwise the second line sits on the portal.
        Vec3 anchor = entry.portal().ref().topCenter().add(0.0D, 1.9D, 0.0D);
        float base = config.labelScale * LABEL_BASE_SCALE;
        TextGizmo.Style style = TextGizmo.Style.forColorAndCentered(color).withScale(base);
        TextGizmo.Style detail = style.withScale(base * 0.75F);

        // The number comes first so two portals can be told apart at a glance; reading coordinates
        // and holding them in your head is what the numbering exists to avoid.
        billboard(PortalI18n.tr("portallink.label.numbered",
                        PortalIdStore.get().idOf(entry.portal().ref()),
                        PortalI18n.tr("portallink.status." + entry.status().name().toLowerCase())),
                anchor, style, config);
        billboard(describePartner(entry), anchor.subtract(0.0D, 0.85D, 0.0D), detail, config);
    }

    private static void billboard(String text, Vec3 pos, TextGizmo.Style style,
                                  PortalLinkConfig config) {
        PortalGizmoSupport.finish(Gizmos.billboardText(text, pos, style), config);
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
