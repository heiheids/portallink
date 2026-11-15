package com.heishan.portallink.client.render;

import com.heishan.portallink.core.LinkStatus;


/**
 * Fixed colours used for things that are <b>not</b> link identities.
 *
 * <p>Link colours are generated per pairing by {@link LinkColorAssigner}, because a palette cannot
 * express "this nether gate and its overworld gate share one colour, and the gate next to them does
 * not". The entries here are reserved meanings: a one-way link must always look like a warning, and
 * unknown dimensions must stay neutral.</p>
 *
 * <p>Colours are packed ARGB integers as used by the gizmo API.</p>
 */
public final class PortalPalette {

    public static final int OVERWORLD = 0xFF4FC3F7;
    public static final int NETHER = 0xFFFF7043;

    /** Reserved for a genuinely asymmetric link (串门). Never used as a group colour. */
    public static final int CONFLICT = 0xFFEF5350;
    /** Reserved for "the other dimension could not be inspected". */
    public static final int UNKNOWN = 0xFF9E9E9E;

    private PortalPalette() {
    }

    public static int forDimension(boolean nether) {
        return nether ? NETHER : OVERWORLD;
    }

    /** Same colour with a different alpha, for outlines and translucent fills. */
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

}
