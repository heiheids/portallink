package com.heishan.portallink.gametest;

import java.util.concurrent.atomic.AtomicReference;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalSnapshot;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import static com.heishan.portallink.gametest.PortalFixtures.buildPortal;
import static com.heishan.portallink.gametest.PortalFixtures.clampY;
import static com.heishan.portallink.gametest.PortalFixtures.describe;
import static com.heishan.portallink.gametest.PortalFixtures.placeCamera;
import static com.heishan.portallink.gametest.PortalFixtures.require;
import static com.heishan.portallink.gametest.PortalFixtures.resetConfig;

/**
 * Verifies the multiplayer code path against a real dedicated server.
 *
 * <p>On a remote server the client has no access to the point-of-interest index and usually has not
 * loaded the other dimension, so the mod must fall back to scanning its own loaded chunks and must
 * say so honestly instead of inventing links. This test locks that behaviour in: no crash, no
 * {@code authoritative} claim, and no fabricated partner when the other side is unknown.</p>
 */
public final class PortalLinkMultiplayerGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
             TestDedicatedServerConnection connection = server.connect()) {

            context.waitFor(client -> client.player != null && client.level != null);
            context.waitTicks(20);
            context.runOnClient(client -> resetConfig());

            // Build a portal next to wherever the client actually spawned, then wait for the
            // chunk to reach the client. A dedicated server has no shared POI index to read.
            BlockPos spawn = context.computeOnClient(client -> client.player.blockPosition());
            BlockPos portal = server.computeOnServer(serverInstance -> {
                ServerLevel overworld = serverInstance.getLevel(Level.OVERWORLD);
                require(overworld != null, "overworld level missing");
                BlockPos target = new BlockPos(spawn.getX() + 8, clampY(overworld, spawn.getY()),
                        spawn.getZ());
                buildPortal(overworld, target);
                // Stand the player in front of the portal so the screenshot is meaningful.
                placeCamera(overworld, target, 7.5D, -8.0F);
                return target;
            });

            context.waitTicks(60);

            PortalSnapshot snapshot = requestSnapshot(context);
            require(snapshot != null, "snapshot is null");
            require(!snapshot.authoritative(),
                    "a remote server must not be reported as an authoritative scan");

            boolean foundPortal = snapshot.entries().stream()
                    .map(PortalEntry::key)
                    .anyMatch(key -> key.startsWith("overworld@"));
            require(foundPortal, "client side chunk scanning found no portal at "
                    + portal.toShortString() + ": " + describe(snapshot));

            // The nether was never loaded by this client, so nothing may claim a real partner.
            require(!snapshot.otherKnown(),
                    "the client cannot know the nether from a remote server, otherKnown was true");
            require(!snapshot.hasProblems(),
                    "without the other dimension nothing may be reported as a conflict: "
                            + describe(snapshot));

            context.takeScreenshot("portal-link-multiplayer");
        }
    }

    private static PortalSnapshot requestSnapshot(ClientGameTestContext context) {
        AtomicReference<PortalSnapshot> holder = new AtomicReference<>();
        context.runOnClient(client -> ClientPortalLinkState.requestScan(holder::set));
        context.waitFor(client -> holder.get() != null, 200);
        PortalSnapshot snapshot = holder.get();
        require(snapshot != null, "snapshot is null");
        return snapshot;
    }
}
