package com.heishan.portallink.core;

import java.util.List;

import net.minecraft.core.BlockPos;

/**
 * A portal frame together with the exact block positions it was built from.
 *
 * <p>Vanilla picks the <b>closest portal block</b> (not the portal centre), so keeping the block
 * list is what makes our prediction match the game's decision instead of merely approximating it.</p>
 */
public record ScannedPortal(PortalRef ref, List<BlockPos> blocks) {

    public ScannedPortal {
        blocks = List.copyOf(blocks);
    }

    public String key() {
        return ref.key();
    }

    public boolean contains(BlockPos pos) {
        return blocks.contains(pos);
    }
}
