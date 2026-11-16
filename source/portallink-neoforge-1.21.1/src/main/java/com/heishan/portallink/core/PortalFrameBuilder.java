package com.heishan.portallink.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Turns a bag of nether-portal block positions into rectangular portal frames.
 *
 * <p>Every nether portal block is a point-of-interest record, so a single portal shows up as
 * 2..21 x 3..21 separate positions. Connected-component grouping collapses them back into one
 * frame, and the frame's extents come from the block set itself (never from a guessed size).</p>
 */
public final class PortalFrameBuilder {

    /** A portal wider or taller than this cannot exist in vanilla. */
    private static final int MAX_EXTENT = PortalRef.MAX_SIZE;

    private PortalFrameBuilder() {
    }

    public static List<ScannedPortal> build(Level level,
                                            ResourceKey<Level> dimension,
                                            Iterable<BlockPos> portalBlocks) {
        Map<Long, BlockPos> byPacked = new HashMap<>();
        for (BlockPos pos : portalBlocks) {
            byPacked.put(pos.asLong(), pos.immutable());
        }

        Set<Long> visited = new HashSet<>();
        List<ScannedPortal> result = new ArrayList<>();

        for (Map.Entry<Long, BlockPos> entry : byPacked.entrySet()) {
            if (visited.contains(entry.getKey())) {
                continue;
            }
            List<BlockPos> component = floodFill(level, byPacked, visited, entry.getValue());
            ScannedPortal portal = toPortal(level, dimension, component);
            if (portal != null) {
                result.add(portal);
            }
        }

        result.sort(Comparator
                .comparingInt((ScannedPortal p) -> p.ref().minCorner().getX())
                .thenComparingInt(p -> p.ref().minCorner().getZ())
                .thenComparingInt(p -> p.ref().minCorner().getY()));
        return result;
    }

    private static List<BlockPos> floodFill(Level level,
                                            Map<Long, BlockPos> byPacked,
                                            Set<Long> visited,
                                            BlockPos start) {
        List<BlockPos> component = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start.asLong());

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            component.add(current);
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = current.relative(direction);
                long packed = neighbour.asLong();
                if (visited.contains(packed) || !byPacked.containsKey(packed)) {
                    continue;
                }
                if (!isPortalBlock(level, neighbour)) {
                    continue;
                }
                visited.add(packed);
                queue.add(neighbour);
            }
        }
        return component;
    }

    private static boolean isPortalBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.NETHER_PORTAL);
    }

    private static ScannedPortal toPortal(Level level,
                                          ResourceKey<Level> dimension,
                                          List<BlockPos> component) {
        if (component.isEmpty()) {
            return null;
        }
        BlockPos first = component.get(0);
        BlockState state = level.getBlockState(first);
        Direction.Axis axis = state.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS)
                .orElse(Direction.Axis.X);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : component) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int width = axis == Direction.Axis.X ? (maxX - minX + 1) : (maxZ - minZ + 1);
        int height = maxY - minY + 1;
        if (width > MAX_EXTENT || height > MAX_EXTENT) {
            // Two touching portals merged into one component: keep the first block as a 1x1 frame
            // so the entry is still visible instead of vanishing silently.
            width = 1;
            height = 1;
        }

        BlockPos minCorner = axis == Direction.Axis.X
                ? new BlockPos(minX, minY, minZ)
                : new BlockPos(minX, minY, minZ);

        PortalRef ref = new PortalRef(dimension, axis, minCorner, width, height);
        return new ScannedPortal(ref, component);
    }
}
