package com.heishan.portallink.client.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.border.WorldBorder;

/**
 * Authoritative portal scan through the server's point-of-interest index.
 *
 * <p>This mirrors exactly what {@code PortalForcer#findClosestPortalPosition} sees, so a prediction
 * made from this data cannot disagree with the teleport.</p>
 *
 * <p><b>Threading:</b> {@link PoiManager} is not thread safe and {@link #scan} may load chunks.
 * Every method here must therefore run on the server thread, never on the render or client thread.
 * See {@code PortalScanService} for the scheduling.</p>
 */
public final class ServerPortalScanner {

    private static final Predicate<Holder<PoiType>> NETHER_PORTAL =
            holder -> holder.is(PoiTypes.NETHER_PORTAL);

    private ServerPortalScanner() {
    }

    /** Portal blocks found by an authoritative scan. */
    public record Result(List<BlockPos> portalBlocks, WorldBorder border, boolean complete) {

        public Result {
            portalBlocks = List.copyOf(portalBlocks);
        }
    }

    /**
     * Gather every nether portal point of interest in a square around {@code center}.
     *
     * <p>Server thread only.</p>
     *
     * @param level  server level to query
     * @param center centre of the square
     * @param radius square radius in blocks
     */
    public static Result scan(ServerLevel level, BlockPos center, int radius) {
        PoiManager poiManager = level.getPoiManager();
        WorldBorder border = level.getWorldBorder();
        List<BlockPos> found = new ArrayList<>();

        boolean complete = true;
        try {
            poiManager.ensureLoadedAndValid(level, center, radius);
        } catch (RuntimeException exception) {
            // Chunk loading is best effort; whatever the index already holds is still usable.
            complete = false;
        }

        try (var stream = poiManager.getInSquare(NETHER_PORTAL, center, radius,
                PoiManager.Occupancy.ANY)) {
            for (PoiRecord record : stream.toList()) {
                found.add(record.getPos().immutable());
            }
        } catch (RuntimeException exception) {
            complete = false;
        }

        return new Result(found, border, complete);
    }
}
