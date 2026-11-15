package com.heishan.portallink.gametest;

import java.util.List;

import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.PortalSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Shared fixture helpers for the client game tests.
 *
 * <p>Every coordinate used by the tests is derived from the runtime world (spawn position, build
 * height limits), never hard coded, because the overworld and the nether do not share a Y range.</p>
 */
public final class PortalFixtures {

    /** Inner width and height of the test portals (vanilla minimum is 2x3). */
    public static final int PORTAL_WIDTH = 2;
    public static final int PORTAL_HEIGHT = 3;

    /** Nether portals are searched within 16 blocks, so overworld partners differ by at most 16. */
    public static final int NETHER_SEARCH_RADIUS = 16;

    private PortalFixtures() {
    }

    /** The block a position maps to when travelling from the overworld to the nether. */
    public static BlockPos toNether(BlockPos overworldPos) {
        return new BlockPos(Math.floorDiv(overworldPos.getX(), 8), overworldPos.getY(),
                Math.floorDiv(overworldPos.getZ(), 8));
    }

    /** Clamp a Y coordinate into a level's build height, keeping a margin for the frame. */
    public static int clampY(ServerLevel level, int desiredY) {
        int min = level.getMinY() + 8;
        int max = level.getMaxY() - 16;
        return Math.max(min, Math.min(max, desiredY));
    }

    /** A nether portal near the player's spawn, at a safe height. */
    public static BlockPos portalNearSpawn(ServerLevel level, BlockPos spawn, int offsetX) {
        return new BlockPos(spawn.getX() + offsetX, clampY(level, spawn.getY()), spawn.getZ());
    }

    /**
     * Places a 2x3 nether portal at {@code base}, with a stone floor and cleared headroom so the
     * player can stand in front of it and the scan cannot be confused by terrain.
     */
    public static void buildPortal(ServerLevel level, BlockPos base) {
        for (int x = -3; x <= PORTAL_WIDTH + 2; x++) {
            for (int y = -2; y <= PORTAL_HEIGHT + 1; y++) {
                for (int z = -3; z <= 8; z++) {
                    level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = -3; x <= PORTAL_WIDTH + 2; x++) {
            for (int z = -3; z <= 8; z++) {
                level.setBlock(base.offset(x, -3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        for (int y = -1; y <= PORTAL_HEIGHT; y++) {
            level.setBlock(base.offset(-1, y, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(base.offset(PORTAL_WIDTH, y, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
        }
        for (int x = -1; x <= PORTAL_WIDTH; x++) {
            level.setBlock(base.offset(x, -1, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(base.offset(x, PORTAL_HEIGHT, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
        }

        BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X);
        for (int x = 0; x < PORTAL_WIDTH; x++) {
            for (int y = 0; y < PORTAL_HEIGHT; y++) {
                level.setBlock(base.offset(x, y, 0), portal, 3);
            }
        }
    }

    /** Restore every rendering option to a known state so tests cannot leak into each other. */
    public static void resetConfig() {
        PortalLinkConfig config = PortalLinkConfig.get();
        config.enabled = true;
        config.renderRanges = true;
        config.renderLabels = true;
        config.renderConnectors = true;
        config.renderGhosts = false;
        config.alwaysOnTop = true;
        config.maxRenderDistance = 256;
        config.maxRenderedPortals = 32;
        config.showStatusPanel = false;
        config.warnOnConflict = true;
        config.labelScale = 1.0F;
        config.save();
    }

    /** Put the player in front of a portal, looking at it. */
    public static void placeCamera(ServerLevel level, BlockPos portal, double distance, float pitch) {
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.teleportTo(level, portal.getX() + 0.5D, portal.getY() + 1.0D,
                portal.getZ() + distance, java.util.Set.of(), 180.0F, pitch, true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Human readable dump of a snapshot, used in assertion messages. */
    public static List<String> describe(PortalSnapshot snapshot) {
        return snapshot.entries().stream()
                .map(entry -> entry.key() + " -> " + entry.status()
                        + (entry.matched() == null ? " [none]" : " [" + entry.matched().key() + "]"))
                .toList();
    }

    /** Whether a dimension key is the nether. */
    public static boolean isNether(net.minecraft.resources.ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension);
    }
}
