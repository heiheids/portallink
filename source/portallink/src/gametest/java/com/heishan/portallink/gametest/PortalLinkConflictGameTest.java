package com.heishan.portallink.gametest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.client.ConflictNotifier;
import com.heishan.portallink.core.LinkGroups;
import com.heishan.portallink.core.LinkStatus;
import com.heishan.portallink.core.PortalEntry;
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
import static com.heishan.portallink.gametest.PortalFixtures.describe;
import static com.heishan.portallink.gametest.PortalFixtures.placeCamera;
import static com.heishan.portallink.gametest.PortalFixtures.require;
import static com.heishan.portallink.gametest.PortalFixtures.resetConfig;
import static com.heishan.portallink.gametest.PortalFixtures.toNether;

/**
 * Reproduces the "串门" case the mod exists to warn about.
 *
 * <p>Two overworld portals stand 40 blocks apart while both map inside the same 16 block nether
 * square. Only one nether gate exists, at the first portal's mapped position, so it is the nearest
 * match for both - yet its own nearest overworld portal is only the first. Vanilla therefore sends
 * the second portal's travellers to the gate, and the gate sends theirs back to the first.</p>
 */
public final class PortalLinkConflictGameTest implements FabricClientGameTest {

    /** Distance between the two overworld portals; 40 / 8 = 5 blocks in the nether. */
    private static final int SECOND_PORTAL_OFFSET = 40;

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
                BlockPos first = new BlockPos(spawn.getX() + 6, y, spawn.getZ());
                BlockPos second = new BlockPos(spawn.getX() + 6 + SECOND_PORTAL_OFFSET, y,
                        spawn.getZ());
                BlockPos gateAnchor = toNether(first);
                BlockPos gate = new BlockPos(gateAnchor.getX(), clampY(nether, gateAnchor.getY()),
                        gateAnchor.getZ());

                buildPortal(overworld, first);
                buildPortal(overworld, second);
                buildPortal(nether, gate);
                return new BlockPos[] { first, second, gate };
            });
            BlockPos first = positions[0];
            BlockPos second = positions[1];

            // Build height is a dimension property, so read it from the running level instead of
            // hard-coding the overworld range in the test.
            int overworldMinY = singleplayer.getServer().computeOnServer(server ->
                    server.getLevel(Level.OVERWORLD).getMinY());
            int overworldMaxY = singleplayer.getServer().computeOnServer(server ->
                    server.getLevel(Level.OVERWORLD).getMaxY());
            context.waitTicks(30);

            PortalSnapshot snapshot = requestSnapshot(context);
            require(snapshot.entries().size() >= 2,
                    "expected two overworld portals, got " + describe(snapshot));

            PortalEntry nearEntry = entryAt(snapshot, first);
            PortalEntry farEntry = entryAt(snapshot, second);

            require(nearEntry.status() == LinkStatus.LINKED,
                    "portal at the gate's mapped position should be LINKED, was "
                            + nearEntry.status() + " " + describe(snapshot));
            require(farEntry.status() == LinkStatus.CONFLICT,
                    "the portal sharing that gate should be CONFLICT, was " + farEntry.status()
                            + " " + describe(snapshot));
            require(farEntry.hasMatch() && nearEntry.hasMatch()
                            && farEntry.matched().key().equals(nearEntry.matched().key()),
                    "both portals should target the same gate: " + describe(snapshot));

            // The two overworld portals compete for one gate, so they must land in two different
            // link groups; otherwise they would be painted the same colour and the player could
            // not tell which square belongs to which gate.
            PortalGroupAssertions.assertGroupsSeparateCompetingLinks(snapshot);
            PortalGroupAssertions.assertNoTruncatedFrames(snapshot);
            PortalGroupAssertions.assertColumnsHaveMappedSize(snapshot, overworldMinY, overworldMaxY);
            PortalGroupAssertions.assertRangesPlanned(snapshot, overworldMinY, overworldMaxY);
            PortalGroupAssertions.assertOverworldColumnsAre128(snapshot, overworldMinY, overworldMaxY);

            // Ranges must be anchored to the gate, not to the player: scan, walk a good distance,
            // scan again, and require byte-identical geometry for every group seen twice.
            List<RangeMarker> rangesBefore = RangePlanner.plan(snapshot, LinkGroups.of(snapshot),
                    overworldMinY, overworldMaxY);
            BlockPos awayFromPortals = new BlockPos(second.getX() + 30, second.getY(),
                    second.getZ() + 30);
            singleplayer.getServer().runOnServer(server -> placeCamera(
                    server.getLevel(Level.OVERWORLD), awayFromPortals, 0.0D, 0.0F));
            context.waitTicks(20);
            PortalSnapshot moved = requestSnapshot(context);
            List<RangeMarker> rangesAfter = RangePlanner.plan(moved, LinkGroups.of(moved),
                    overworldMinY, overworldMaxY);
            PortalGroupAssertions.assertRangesStable(rangesBefore, rangesAfter);

            boolean warned = waitForWarning(context, 200);
            require(warned, "expected ConflictNotifier to warn about the asymmetric link, count="
                    + context.computeOnClient(client -> ConflictNotifier.warningCount()));

            singleplayer.getServer().runOnServer(server -> placeCamera(
                    server.getLevel(Level.OVERWORLD), first, 26.5D, -12.0F));
            context.waitTicks(40);
            context.takeScreenshot("portal-link-conflict");
        }
    }

    private static PortalEntry entryAt(PortalSnapshot snapshot, BlockPos portalPos) {
        return snapshot.entries().stream()
                .filter(entry -> entry.portal().ref().minCorner().equals(portalPos))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry for portal at "
                        + portalPos.toShortString() + ": " + describe(snapshot)));
    }

    private static PortalSnapshot requestSnapshot(ClientGameTestContext context) {
        AtomicReference<PortalSnapshot> holder = new AtomicReference<>();
        context.runOnClient(client -> ClientPortalLinkState.requestScan(holder::set));
        context.waitFor(client -> holder.get() != null, 200);
        PortalSnapshot snapshot = holder.get();
        require(snapshot != null, "snapshot is null");
        return snapshot;
    }

    private static boolean waitForWarning(ClientGameTestContext context, int timeoutTicks) {
        for (int waited = 0; waited < timeoutTicks; waited += 5) {
            boolean fired = context.computeOnClient(client -> ConflictNotifier.warningCount() > 0);
            if (fired) {
                return true;
            }
            context.waitTicks(5);
        }
        return false;
    }
}
