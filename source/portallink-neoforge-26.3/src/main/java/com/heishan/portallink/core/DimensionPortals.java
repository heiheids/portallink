package com.heishan.portallink.core;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * All portals found in one dimension.
 */
public final class DimensionPortals {

    private final ResourceKey<Level> dimension;
    private final List<ScannedPortal> portals;
    private final boolean complete;

    public DimensionPortals(ResourceKey<Level> dimension, List<ScannedPortal> portals,
                            boolean complete) {
        this.dimension = dimension;
        this.portals = List.copyOf(portals);
        this.complete = complete;
    }

    public static DimensionPortals empty(ResourceKey<Level> dimension) {
        return new DimensionPortals(dimension, List.of(), false);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public List<ScannedPortal> portals() {
        return portals;
    }

    /** {@code true} when every loaded section in the observed area was inspected. */
    public boolean complete() {
        return complete;
    }

    public int count() {
        return portals.size();
    }

    public DimensionPortals withComplete(boolean newComplete) {
        return new DimensionPortals(dimension, portals, newComplete);
    }

    public DimensionPortals plus(DimensionPortals other) {
        if (other == null || other.count() == 0) {
            return this;
        }
        List<ScannedPortal> merged = new ArrayList<>(portals);
        for (ScannedPortal candidate : other.portals) {
            boolean duplicate = false;
            for (ScannedPortal existing : merged) {
                if (existing.key().equals(candidate.key())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                merged.add(candidate);
            }
        }
        return new DimensionPortals(dimension, merged, complete && other.complete);
    }
}
