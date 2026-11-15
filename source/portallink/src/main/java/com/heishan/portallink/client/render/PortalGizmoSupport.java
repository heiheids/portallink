package com.heishan.portallink.client.render;

import com.heishan.portallink.config.PortalLinkConfig;

import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * Shared helpers for the <b>line</b> gizmos: borders, corner posts, ribs and labels.
 *
 * <h2>Persistence is mandatory, and that is why surfaces are not gizmos</h2>
 * On this path a gizmo submitted with no lifetime is not drawn at all - removing the lifetime made
 * every outline, rib and label disappear in one go. So every gizmo carries a lifetime, which also
 * leaves {@code lifetime / frameTime} copies alive. For a <b>one pixel line</b> stacked copies are
 * visually identical, so lines are fine. For a translucent <b>surface</b> the copies multiply the alpha
 * to {@code 1-(1-a)^N} with {@code N} wobbling frame to frame - that is the flicker. Surfaces are
 * therefore drawn by {@link ZoneSurfaceSubmitter} as a submitted mesh instead, with exact alpha.
 */
final class PortalGizmoSupport {

    /** Lifetime for gizmo work; above one frame so a dropped frame cannot make anything blink. */
    private static final int LINE_LIFETIME_MILLIS = 50;

    /** Most copies to compensate for; a stalled frame should not fade everything to nothing. */
    private static final int MAX_COPIES = 8;

    /** Smoothed frame time; raw per-frame values are far too noisy to base an alpha on. */
    private static double smoothedFrameMillis = 16.7D;

    /** Copy count currently in use, held steady by hysteresis. */
    private static int stableCopies = 3;

    /** When the copy count last changed, used for the hysteresis window. */
    private static long lastCopiesChangeMillis;

    private PortalGizmoSupport() {
    }

    /**
     * How many copies of each element are alive, with hysteresis.
     *
     * <h2>Why hysteresis and not the raw frame time</h2>
     * {@code N = lifetime / frameTime}, and the raw frame time jumps around by milliseconds every
     * frame. Deriving {@code N} straight from it made {@code N} alternate between three and four, and
     * because the per-copy alpha is computed from {@code N}, the area's brightness oscillated - the
     * flicker survived even though the formula was right. The frame time is therefore smoothed, and the
     * count may only change after it has disagreed for half a second.
     */
    static int copiesFor(double frameMillis) {
        smoothedFrameMillis = smoothedFrameMillis * 0.9D + frameMillis * 0.1D;
        int candidate = (int) Math.ceil(LINE_LIFETIME_MILLIS / Math.max(1.0D, smoothedFrameMillis));
        candidate = Math.max(1, Math.min(MAX_COPIES, candidate));

        long now = System.currentTimeMillis();
        if (candidate != stableCopies && now - lastCopiesChangeMillis > 500L) {
            stableCopies = candidate;
            lastCopiesChangeMillis = now;
        }
        return stableCopies;
    }

    /**
     * Split a target alpha across the live copies.
     *
     * <p>{@code N} copies of a translucent surface blend to {@code 1-(1-a)^N}, so one layer has to be
     * {@code a = 1-(1-target)^(1/N)} for the blended result to match the target. Combined with the
     * stable {@code N} above, the visible brightness stays constant.</p>
     */
    static int perCopyAlpha(int targetAlpha, int copies) {
        int clamped = Math.max(0, Math.min(255, targetAlpha));
        if (clamped == 0) {
            return 0;
        }
        if (copies <= 1) {
            return clamped;
        }
        double target = clamped / 255.0D;
        double perCopy = 1.0D - Math.pow(1.0D - target, 1.0D / copies);
        return Math.max(1, Math.min(255, (int) Math.round(perCopy * 255.0D)));
    }

    /** Apply the see-through preference and the lifetime. */
    static void finish(GizmoProperties properties, PortalLinkConfig config) {
        if (config.alwaysOnTop) {
            properties.setAlwaysOnTop();
        }
        properties.persistForMillis(LINE_LIFETIME_MILLIS);
    }

    /** One line segment, see-through unless the player turned that off. */
    static void line(Vec3 from, Vec3 to, int color, PortalLinkConfig config) {
        finish(Gizmos.line(from, to, color), config);
    }

    /** Clamp a value into a range. */
    static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
