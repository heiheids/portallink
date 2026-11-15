package com.heishan.portallink.gametest;

import java.util.concurrent.atomic.AtomicReference;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.core.LinkStatus;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalSnapshot;

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
 * Basic end-to-end test: one overworld portal, one nether gate at its mapped position.
 *
 * <p>Checks that the scan finds the pair, that the predicted link is {@code LINKED}, and that the
 * renderer survives several frames while producing the markers.</p>
 */
public final class PortalLinkClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {

            context.waitFor(client -> client.player != null && client.level != null);
            context.waitTicks(20);
            context.runOnClient(client -> resetConfig());

            BlockPos spawn = context.computeOnClient(client -> client.player.blockPosition());

            // Build height differs per dimension, so both positions are resolved on the server.
            BlockPos[] positions = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                ServerLevel nether = server.getLevel(Level.NETHER);
                require(overworld != null, "overworld level missing");
                require(nether != null, "nether level missing");
                BlockPos overworldPortal = new BlockPos(spawn.getX() + 6,
                        clampY(overworld, spawn.getY()), spawn.getZ());
                BlockPos netherAnchor = toNether(overworldPortal);
                BlockPos netherPortal = new BlockPos(netherAnchor.getX(),
                        clampY(nether, netherAnchor.getY()), netherAnchor.getZ());
                buildPortal(overworld, overworldPortal);
                buildPortal(nether, netherPortal);
                return new BlockPos[] { overworldPortal, netherPortal };
            });
            BlockPos overworldPortal = positions[0];
            context.waitTicks(30);

            PortalSnapshot snapshot = requestSnapshot(context);
            require(!snapshot.isEmpty(), "snapshot has no entries: " + describe(snapshot));
            require(snapshot.authoritative(), "singleplayer scan should be authoritative");

            PortalEntry entry = snapshot.entries().stream()
                    .filter(candidate -> Level.OVERWORLD.equals(candidate.portal().ref().dimension()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no overworld portal: " + describe(snapshot)));

            require(entry.hasMatch(),
                    "overworld portal should link, status " + entry.status() + " "
                            + describe(snapshot));
            require(Level.NETHER.equals(entry.matched().ref().dimension()),
                    "partner should be in the nether: " + entry.matched().ref().dimension());
            require(entry.status() == LinkStatus.LINKED,
                    "expected LINKED, got " + entry.status() + " " + describe(snapshot));
            require(entry.matchedDistance() >= 0.0D && entry.matchedDistance() <= 16.0D,
                    "partner horizontally too far: " + entry.matchedDistance());

            // The renderer must survive a few frames with all markers enabled.
            singleplayer.getServer().runOnServer(server -> placeCamera(
                    server.getLevel(Level.OVERWORLD), overworldPortal, 8.5D, -8.0F));
            context.waitTicks(40);
            context.takeScreenshot("portal-link-in-world");

            // Second viewpoint, far outside and above the column, looking down at it.
            //
            // Two screenshots of the same world are the only way to tell "the range moves" apart from
            // "it looks like it moves from inside". The marker bounds are dumped next to each shot so
            // the numbers and the pixels can be compared directly.
            BlockPos overlook = new BlockPos(overworldPortal.getX() + 150,
                    clampY(singleplayer.getServer().computeOnServer(server ->
                            server.getLevel(Level.OVERWORLD)), overworldPortal.getY() + 60),
                    overworldPortal.getZ() + 150);
            singleplayer.getServer().runOnServer(server -> placeCamera(
                    server.getLevel(Level.OVERWORLD), overlook, 0.0D, 55.0F));
            context.waitTicks(40);
            context.takeScreenshot("portal-link-outside");
            require(context.computeOnClient(client -> client.level != null),
                    "client level lost after moving the camera outside the column");

            // Third viewpoint: outside the column but still INSIDE maxRenderDistance, looking away
            // from it. This is the decisive test for "the volume follows the player": anchored
            // geometry must be behind the camera and therefore invisible, while camera-relative
            // geometry would fill the whole screen here.
            //
            // ⚠ The distance matters. An earlier version of this shot stood 400 blocks away, where
            // the column was culled by maxRenderDistance anyway — a clean screen proved nothing.
            singleplayer.getServer().runOnServer(server -> {
                var level = server.getLevel(Level.OVERWORLD);
                var player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(level, overworldPortal.getX() + 0.5D,
                        clampY(level, overworldPortal.getY() + 40),
                        overworldPortal.getZ() + 200.5D,
                        java.util.Set.of(), 0.0F, 0.0F, true);
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                player.fallDistance = 0.0F;
            });
            context.waitTicks(40);
            context.takeScreenshot("portal-link-away");
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
