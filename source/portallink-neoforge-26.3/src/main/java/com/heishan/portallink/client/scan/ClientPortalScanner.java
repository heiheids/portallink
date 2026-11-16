package com.heishan.portallink.client.scan;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Read-only scan of the client's loaded chunks for nether portal blocks.
 *
 * <p>The multiplayer-safe fallback: it never asks the client to load a chunk ({@code load = false})
 * and never talks to the server. The {@code complete} flag lets the UI distinguish "no portal
 * there" from "that chunk is not loaded".</p>
 *
 * <p>Client thread only.</p>
 */
public final class ClientPortalScanner {

    private ClientPortalScanner() {
    }

    /** Blocks found by a client-side scan. */
    public record Result(List<BlockPos> portalBlocks, boolean complete) {

        public Result {
            portalBlocks = List.copyOf(portalBlocks);
        }
    }

    public static Result scan(ClientLevel level, BlockPos center, int radius) {
        List<BlockPos> found = new ArrayList<>();
        boolean complete = true;

        int chunkRadius = Math.max(1, (radius + 15) >> 4);
        ChunkPos origin = ChunkPos.containing(center);

        for (int chunkX = origin.x() - chunkRadius; chunkX <= origin.x() + chunkRadius; chunkX++) {
            for (int chunkZ = origin.z() - chunkRadius; chunkZ <= origin.z() + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL,
                        false);
                if (chunk == null) {
                    complete = false;
                    continue;
                }
                collectFromChunk(level, chunk, found);
            }
        }
        return new Result(found, complete);
    }

    private static void collectFromChunk(ClientLevel level, LevelChunk chunk, List<BlockPos> out) {
        LevelChunkSection[] sections = chunk.getSections();
        int chunkMinX = chunk.getPos().x() << 4;
        int chunkMinZ = chunk.getPos().z() << 4;

        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            // Cheap rejection: one palette scan instead of 4096 block lookups.
            if (!section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL))) {
                continue;
            }
            int baseY = (level.getSectionYFromSectionIndex(index)) << 4;
            for (int localY = 0; localY < 16; localY++) {
                for (int localX = 0; localX < 16; localX++) {
                    for (int localZ = 0; localZ < 16; localZ++) {
                        if (section.getBlockState(localX, localY, localZ)
                                .is(Blocks.NETHER_PORTAL)) {
                            out.add(new BlockPos(chunkMinX + localX, baseY + localY,
                                    chunkMinZ + localZ));
                        }
                    }
                }
            }
        }
    }
}
