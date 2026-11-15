package com.heishan.portallink.client.render;

import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.RangeMarker;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Selects which claim areas to draw and hands them to {@link RangeMeshRenderer}.
 *
 * <p>All geometry is submitted, never gizmo driven; see that class for the reasons. This class only
 * decides visibility.</p>
 */
final class PortalRangeRenderer {

    private PortalRangeRenderer() {
    }

    /**
     * Height band of a claim area, used to decide whether it applies at the player's position.
     *
     * <p>Purely a selection test. Nothing is <i>drawn</i> at the player's height any more: a floor
     * plane tracking {@code eyeY} used to show up as a permanent layer at eye level, so all drawing is
     * tied to the area's own extent instead.</p>
     */
    static boolean appliesAt(RangeMarker marker, double eyeY) {
        return eyeY >= marker.bottomY() && eyeY <= marker.topY();
    }

    /** Draw every claim area that applies here and is within range. */
    static void render(LevelRenderContext context, List<RangeMarker> markers,
                       LinkColorAssigner colors, PortalLinkConfig config, Vec3 eye,
                       double maxDistSq) {
        for (RangeMarker marker : markers) {
            if (!appliesAt(marker, eye.y)) {
                continue;
            }
            double nearestX = PortalGizmoSupport.clamp(eye.x,
                    marker.minBlockX(), marker.maxBlockX() + 1);
            double nearestZ = PortalGizmoSupport.clamp(eye.z,
                    marker.minBlockZ(), marker.maxBlockZ() + 1);
            double dx = nearestX - eye.x;
            double dz = nearestZ - eye.z;
            if (dx * dx + dz * dz > maxDistSq) {
                continue;
            }
            int rgb = colors.colorOf(marker) & 0x00FFFFFF;
            RangeMeshRenderer.render(context, config, marker, rgb);
            areaLabel(marker, rgb, config);
        }
    }

    /**
     * Number of the gate that owns this area, floating over its middle.
     *
     * <p>The area and the gate must be matchable without walking over: with several gates in view, the
     * number on the area is what tells you which gate the ground you are standing on belongs to.</p>
     */
    private static void areaLabel(RangeMarker marker, int rgb, PortalLinkConfig config) {
        if (!config.renderLabels) {
            return;
        }
        int cx = (marker.minBlockX() + marker.maxBlockX()) / 2;
        int cz = (marker.minBlockZ() + marker.maxBlockZ()) / 2;
        int y = marker.ownerPos().getY() + 2;
        int id = com.heishan.portallink.client.PortalIdStore.get()
                .idOf(marker.ownerPos(), marker.ownerIsNether());
        PortalGizmoSupport.finish(Gizmos.billboardTextOverBlock("#" + id,
                        BlockPos.containing(cx, y, cz),
                        PortalPalette.withAlpha(rgb, 0xFF), 0xB0000000, config.labelScale),
                config);
    }
}
