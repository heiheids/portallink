package com.heishan.portallink.client.render;

import java.util.HashMap;
import java.util.Map;

import com.heishan.portallink.core.LinkGroups;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalSnapshot;
import com.heishan.portallink.core.RangeMarker;

/**
 * Assigns one colour per <b>link group</b>, so an entire pairing reads as one unit.
 *
 * <p>This replaces the old "colour by status" scheme, which could not express the case the mod
 * exists for: a nether gate A, the overworld gate B it really links to, and another overworld gate
 * X that sits inside A's square but links to a different nether gate C. Colouring by status painted
 * B and X the same green and the player could not tell which square belonged to whom.</p>
 *
 * <p>Hues are spread with a golden-ratio step, which keeps neighbouring assignments visually
 * distinct without needing a hand-maintained palette. Assignment is keyed by the group id, so it is
 * stable across frames and only changes when the set of links changes.</p>
 */
public final class LinkColorAssigner {

    /** Golden ratio conjugate: successive turns land far apart on the colour wheel. */
    private static final double HUE_STEP = 0.618033988749895D;

    /** Start away from pure red so the conflict colour stays recognisable. */
    private static final double HUE_SEED = 0.08D;

    /** Saturation and value fixed; only the hue varies, keeping brightness readable. */
    private static final float SATURATION = 0.78F;
    private static final float VALUE = 1.0F;

    private final Map<String, Integer> colorByGroup = new HashMap<>();

    public LinkColorAssigner(PortalSnapshot snapshot, LinkGroups groups) {
        if (snapshot == null || groups == null) {
            return;
        }
        // Collect the distinct links first, then sort them, and only then hand out hues.
        //
        // Iterating the entries directly made the colour depend on scan order: the POI query can
        // return the same portals in a different order after a rescan, which reshuffled every hue.
        // With a rescan every few seconds that reads as constant flashing. Sorting by the stable
        // group id makes the assignment a pure function of the set of links.
        java.util.TreeSet<String> ordered = new java.util.TreeSet<>();
        for (PortalEntry entry : snapshot.allEntries()) {
            ordered.add(groups.groupOf(entry.key()));
        }
        int index = 0;
        for (String group : ordered) {
            colorByGroup.put(group, nextColor(index++));
        }
    }

    /** Colour of the link a portal belongs to. */
    public int colorOf(String groupKey) {
        Integer color = colorByGroup.get(groupKey);
        return color == null ? PortalPalette.UNKNOWN : color;
    }

    /** Colour of a range, taken from the link that range belongs to. */
    public int colorOf(RangeMarker marker) {
        return colorOf(marker.groupKey());
    }

    /**
     * Colour of an entry, taken from its link group.
     *
     * <p>A genuine one-way link is deliberately overridden with {@link PortalPalette#CONFLICT}:
     * that is a warning, not an identity, and it must stay instantly recognisable. Everything else
     * uses its group colour so pairings stay distinguishable.</p>
     */
    public int colorOf(PortalEntry entry, LinkGroups groups) {
        if (entry.status() == com.heishan.portallink.core.LinkStatus.CONFLICT) {
            return PortalPalette.CONFLICT;
        }
        return colorOf(groups.groupOf(entry.key()));
    }

    /** Distinct, evenly spread colour for the {@code index}-th group. */
    static int nextColor(int index) {
        double hue = (HUE_SEED + index * HUE_STEP) % 1.0D;
        return hsvToArgb((float) hue, SATURATION, VALUE);
    }

    /** Convert HSV (each 0..1) to a packed opaque ARGB int. */
    static int hsvToArgb(float hue, float saturation, float value) {
        int sector = (int) Math.floor(hue * 6.0F) % 6;
        float fraction = hue * 6.0F - (float) Math.floor(hue * 6.0F);
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * fraction);
        float t = value * (1.0F - saturation * (1.0F - fraction));

        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            default -> {
                r = value;
                g = p;
                b = q;
            }
        }
        int ri = Math.round(r * 255.0F);
        int gi = Math.round(g * 255.0F);
        int bi = Math.round(b * 255.0F);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
