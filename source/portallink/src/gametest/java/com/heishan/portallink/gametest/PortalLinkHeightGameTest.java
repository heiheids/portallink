package com.heishan.portallink.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.core.LinkGroups;
import com.heishan.portallink.core.PortalSnapshot;
import com.heishan.portallink.core.RangeMarker;
import com.heishan.portallink.core.RangePlanner;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import static com.heishan.portallink.gametest.PortalFixtures.buildPortal;
import static com.heishan.portallink.gametest.PortalFixtures.clampY;
import static com.heishan.portallink.gametest.PortalFixtures.resetConfig;
import static com.heishan.portallink.gametest.PortalFixtures.require;

/**
 * Two nether gates stacked in the same X/Z column at different heights.
 *
 * <p>Vanilla searches X/Z with no Y bound, but ranks candidates by {@code BlockPos#distSqr}, which is
 * three dimensional. The gates therefore split the column between them at the midpoint of their Y
 * values, and the mod must show that split instead of painting one ambiguous column.</p>
 */
public final class PortalLinkHeightGameTest implements FabricClientGameTest {

    /** Vertical separation between the two nether gates. */
    private static final int HEIGHT_GAP = 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {

            context.waitFor(client -> client.player != null && client.level != null);
            context.waitTicks(20);
            context.runOnClient(client -> resetConfig());

            BlockPos spawn = context.computeOnClient(client -> client.player.blockPosition());

            BlockPos[] positions = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                ServerLevel nether = server.getLevel(Level.NETHER);
                require(overworld != null, "overworld level missing");
                require(nether != null, "nether level missing");

                int y = clampY(overworld, spawn.getY());
                BlockPos here = new BlockPos(spawn.getX() + 6, y, spawn.getZ());
                buildPortal(overworld, here);

                // Both gates share the same X/Z, so their claim columns are identical; only Y
                // separates them.
                BlockPos base = new BlockPos(Math.floorDiv(here.getX(), 8),
                        clampY(nether, y), Math.floorDiv(here.getZ(), 8));
                int low = Math.max(nether.getMinY() + 10, base.getY() - HEIGHT_GAP / 2);
                int high = Math.min(nether.getMaxY() - 20, base.getY() + HEIGHT_GAP / 2);
                require(high - low >= 20, "nether build height too small for the stacked gates: "
                        + low + ".." + high);

                BlockPos lower = new BlockPos(base.getX(), low, base.getZ());
                BlockPos upper = new BlockPos(base.getX(), high, base.getZ());
                buildPortal(nether, lower);
                buildPortal(nether, upper);
                return new BlockPos[] { here, lower, upper };
            });
            BlockPos lowerGate = positions[1];
            BlockPos upperGate = positions[2];
            context.waitTicks(40);

            int minY = singleplayer.getServer().computeOnServer(server ->
                    server.getLevel(Level.OVERWORLD).getMinY());
            int maxY = singleplayer.getServer().computeOnServer(server ->
                    server.getLevel(Level.OVERWORLD).getMaxY());

            PortalSnapshot snapshot = requestSnapshot(context);
            List<RangeMarker> markers = RangePlanner.plan(snapshot, LinkGroups.of(snapshot),
                    minY, maxY);
            require(!markers.isEmpty(), "expected claim columns, got none");

            // The two gates must produce two bands sharing one identical horizontal rectangle.
            RangeMarker lowBand = bandOwning(markers, lowerGate);
            RangeMarker highBand = bandOwning(markers, upperGate);
            require(lowBand != null && highBand != null,
                    "both nether gates must own a band; lower=" + lowBand + " upper=" + highBand
                            + " markers=" + describeBands(markers));

            require(lowBand.minBlockX() == highBand.minBlockX()
                            && lowBand.maxBlockX() == highBand.maxBlockX()
                            && lowBand.minBlockZ() == highBand.minBlockZ()
                            && lowBand.maxBlockZ() == highBand.maxBlockZ(),
                    "stacked gates should share one column rectangle: "
                            + describeBands(markers));

            require(lowBand.topY() <= highBand.bottomY(),
                    "the lower gate's band must end before the upper gate's band starts: "
                            + describeBands(markers));

            // The divide must sit at the midpoint of the two gate heights, because that is where
            // the 3D distance comparison flips.
            double expectedDivide = (lowBand.ownerPos().getY() + highBand.ownerPos().getY()) / 2.0D;
            double actualDivide = (lowBand.topY() + highBand.bottomY()) / 2.0D;
            require(Math.abs(expectedDivide - actualDivide) <= 1.0D,
                    "band boundary should be the midpoint of the gate heights: expected ~"
                            + expectedDivide + " got " + actualDivide);

            // Each band must actually contain the gate it names, otherwise the label floats free.
            require(lowBand.bottomY() <= lowBand.ownerPos().getY()
                            && lowBand.ownerPos().getY() <= lowBand.topY(),
                    "lower band must contain its gate height: " + describeBands(markers));
            require(highBand.bottomY() <= highBand.ownerPos().getY()
                            && highBand.ownerPos().getY() <= highBand.topY(),
                    "upper band must contain its gate height: " + describeBands(markers));

            // Colours must differ, otherwise the split is invisible.
            require(!lowBand.groupKey().equals(highBand.groupKey()),
                    "stacked gates must be different link groups: " + describeBands(markers));
        }
    }

    /** The band whose owner is the given gate (compared on its own dimension's position). */
    private static RangeMarker bandOwning(List<RangeMarker> markers, BlockPos gate) {
        for (RangeMarker marker : markers) {
            BlockPos owner = marker.ownerPos();
            if (Math.abs(owner.getX() - gate.getX()) <= 1
                    && Math.abs(owner.getZ() - gate.getZ()) <= 1
                    && Math.abs(owner.getY() - gate.getY()) <= 2) {
                return marker;
            }
        }
        return null;
    }

    private static String describeBands(List<RangeMarker> markers) {
        StringBuilder sb = new StringBuilder();
        for (RangeMarker marker : markers) {
            sb.append('[').append(marker.groupKey())
                    .append(" owner=").append(marker.ownerPos().toShortString())
                    .append(" y=").append(marker.bottomY()).append("..").append(marker.topY())
                    .append(" x=").append(marker.minBlockX()).append("..").append(marker.maxBlockX())
                    .append(" z=").append(marker.minBlockZ()).append("..").append(marker.maxBlockZ())
                    .append("] ");
        }
        return sb.toString();
    }

    private static PortalSnapshot requestSnapshot(ClientGameTestContext context) {
        AtomicReference<PortalSnapshot> holder = new AtomicReference<>();
        context.runOnClient(client -> ClientPortalLinkState.requestScan(holder::set));
        context.waitFor(client -> holder.get() != null, 200);
        PortalSnapshot snapshot = holder.get();
        require(snapshot != null, "no snapshot was produced");
        return snapshot;
    }
}
