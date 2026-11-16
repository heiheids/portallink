package com.heishan.portallink.client.render;

import com.heishan.portallink.config.PortalLinkConfig;

import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * Shared helpers for the gizmo work.
 *
 * <h2>Why nothing here sets a lifetime</h2>
 * This renderer is driven by the (NeoForge) debug renderer hook, which is called once per frame
 * during level extraction. Every gizmo is therefore emitted again next frame, so a retention
 * lifetime would only stack duplicate translucent copies on top of each other and multiply the
 * alpha — the exact flicker this project fought on the Fabric side, where the hook ran before the
 * retained collector was drained. Vanilla's own debug renderers do not set a lifetime either
 * (verified: {@code PoiDebugRenderer}, {@code VillageSectionsDebugRenderer},
 * {@code ChunkDebugRenderer} contain no {@code persistForMillis} call).
 *
 * <p>{@code setAlwaysOnTop} is still applied, because "see through terrain" is a real user
 * preference and is exactly what vanilla's {@code ChunkDebugRenderer} uses it for.</p>
 */
final class PortalGizmoSupport {

    private PortalGizmoSupport() {
    }

    /** Apply the see-through preference. */
    static void finish(GizmoProperties properties, PortalLinkConfig config) {
        if (config.alwaysOnTop) {
            properties.setAlwaysOnTop();
        }
    }

    /** One line segment, see-through unless the player turned that off. */
    static void line(Vec3 from, Vec3 to, int color, PortalLinkConfig config) {
        finish(Gizmos.line(from, to, color), config);
    }

    /** A line segment with an explicit width. */
    static void line(Vec3 from, Vec3 to, int color, float width, PortalLinkConfig config) {
        finish(Gizmos.line(from, to, color, width), config);
    }

    /** Clamp a value into a range. */
    static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
