package com.heishan.portallink.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Immutable result of one scan, always oriented around the dimension the player is standing in.
 *
 * <p>Both directions are kept. {@code entries} describes the portals <b>here</b>, while
 * {@code otherEntries} describes the portals of the <b>opposite</b> dimension with the links they
 * lead to. The second list is what lets the renderer draw a nether gate's claim square right here
 * in the overworld: that square is {@code otherEntry}'s search radius, scaled and projected back.</p>
 *
 * @param sourceDim     dimension the player is in while this snapshot is used
 * @param source        portals found in {@code sourceDim}
 * @param other         portals known in the opposite dimension (may be empty on multiplayer)
 * @param entries       one entry per portal in {@code sourceDim}
 * @param otherEntries  one entry per portal in the opposite dimension
 * @param otherKnown    whether the opposite dimension could be inspected at all
 * @param authoritative whether the data came from the server-side point-of-interest index
 * @param timestamp     {@link System#currentTimeMillis()} of the scan
 */
public record PortalSnapshot(ResourceKey<Level> sourceDim,
                             DimensionPortals source,
                             DimensionPortals other,
                             List<PortalEntry> entries,
                             List<PortalEntry> otherEntries,
                             boolean otherKnown,
                             boolean authoritative,
                             long timestamp) {

    public PortalSnapshot {
        entries = List.copyOf(entries);
        otherEntries = List.copyOf(otherEntries);
    }

    public static PortalSnapshot empty(ResourceKey<Level> sourceDim) {
        return new PortalSnapshot(sourceDim, DimensionPortals.empty(sourceDim),
                DimensionPortals.empty(LinkageMath.targetDimension(sourceDim)),
                List.of(), List.of(), false, false, 0L);
    }

    public boolean isEmpty() {
        return entries.isEmpty() && otherEntries.isEmpty();
    }

    public ResourceKey<Level> otherDim() {
        return LinkageMath.targetDimension(sourceDim);
    }

    /** Every entry of both directions, used for grouping and conflict checks. */
    public List<PortalEntry> allEntries() {
        if (otherEntries.isEmpty()) {
            return entries;
        }
        List<PortalEntry> all = new ArrayList<>(entries.size() + otherEntries.size());
        all.addAll(entries);
        all.addAll(otherEntries);
        return all;
    }

    /** Entries whose link needs attention (asymmetric or newly created). */
    public boolean hasProblems() {
        for (PortalEntry entry : allEntries()) {
            if (entry.status().isProblem()) {
                return true;
            }
        }
        return false;
    }
}
