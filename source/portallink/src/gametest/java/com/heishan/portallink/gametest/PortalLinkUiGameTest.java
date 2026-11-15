package com.heishan.portallink.gametest;

import java.util.ArrayList;
import java.util.List;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.client.gui.PortalLinkConfigScreen;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.PortalSnapshot;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import static com.heishan.portallink.gametest.PortalFixtures.buildPortal;
import static com.heishan.portallink.gametest.PortalFixtures.clampY;
import static com.heishan.portallink.gametest.PortalFixtures.placeCamera;
import static com.heishan.portallink.gametest.PortalFixtures.require;
import static com.heishan.portallink.gametest.PortalFixtures.resetConfig;
import static com.heishan.portallink.gametest.PortalFixtures.toNether;

/**
 * Exercises the user facing surfaces: the settings screen, the status panel, and the
 * {@code /portallink} command.
 *
 * <p>Deliberately asserts on <b>observable effects</b> (config flags, snapshot timestamps, screens)
 * rather than on rendered chat text: 26.3 does not expose the received-message list, and the
 * delivery of system messages is vanilla's responsibility, not this mod's.</p>
 */
public final class PortalLinkUiGameTest implements FabricClientGameTest {

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
                BlockPos portal = new BlockPos(spawn.getX() + 6, clampY(overworld, spawn.getY()),
                        spawn.getZ());
                BlockPos anchor = toNether(portal);
                BlockPos gate = new BlockPos(anchor.getX(), clampY(nether, anchor.getY()),
                        anchor.getZ());
                buildPortal(overworld, portal);
                buildPortal(nether, gate);
                return new BlockPos[] { portal, gate };
            });
            BlockPos portal = positions[0];
            context.waitTicks(30);

            checkSettingsScreen(context);
            checkStatusPanel(context, singleplayer, portal);
            checkCommand(context);

            singleplayer.getServer().runOnServer(server -> placeCamera(
                    server.getLevel(Level.OVERWORLD), portal, 8.5D, -8.0F));
            context.waitTicks(20);
            context.takeScreenshot("portal-link-ui-final");
        }
    }

    /** The settings screen must open, render inside its bounds, and close cleanly. */
    private static void checkSettingsScreen(ClientGameTestContext context) {
        context.setScreen(() -> new PortalLinkConfigScreen(null));
        context.waitForScreen(PortalLinkConfigScreen.class);
        context.waitTicks(10);
        context.takeScreenshot("portal-link-config-screen");

        // Every widget must lie inside the screen, otherwise the last row is cut off.
        List<String> outside = context.computeOnClient(client -> {
            List<String> offenders = new ArrayList<>();
            for (var child : client.gui.screen().children()) {
                if (child instanceof AbstractWidget widget) {
                    if (widget.getBottom() > client.gui.screen().height
                            || widget.getRight() > client.gui.screen().width
                            || widget.getX() < 0 || widget.getY() < 0) {
                        offenders.add(widget.getMessage().getString() + " @ "
                                + widget.getX() + "," + widget.getY() + " -> "
                                + widget.getRight() + "," + widget.getBottom());
                    }
                }
            }
            return offenders;
        });
        require(outside.isEmpty(), "config screen widgets outside the window: " + outside);

        context.runOnClient(client -> client.setScreenAndShow(null));
        context.waitFor(client -> client.gui.screen() == null, 100);
    }

    /** With the panel enabled the HUD element runs every frame; assert it stays alive. */
    private static void checkStatusPanel(ClientGameTestContext context,
                                         TestSingleplayerContext singleplayer, BlockPos portal) {
        context.runOnClient(client -> {
            PortalLinkConfig config = PortalLinkConfig.get();
            config.showStatusPanel = true;
            config.save();
        });
        singleplayer.getServer().runOnServer(server ->
                placeCamera(server.getLevel(Level.OVERWORLD), portal, 8.5D, -8.0F));
        context.waitTicks(40);
        context.takeScreenshot("portal-link-hud");

        boolean stillAlive = context.computeOnClient(client -> client.level != null
                && client.player != null);
        require(stillAlive, "client state was lost while the status panel was enabled");

        context.runOnClient(client -> {
            PortalLinkConfig config = PortalLinkConfig.get();
            config.showStatusPanel = false;
            config.save();
        });
    }

    /**
     * {@code /portallink} must request a fresh scan (observable as a newer snapshot timestamp) and
     * {@code /portallink toggle} must flip the master switch.
     */
    private static void checkCommand(ClientGameTestContext context) {
        long before = context.computeOnClient(client -> ClientPortalLinkState.snapshot().timestamp());
        context.waitTicks(5);

        context.runOnClient(client -> client.player.connection.sendCommand("portallink"));

        boolean rescanned = waitForSnapshotNewerThan(context, before, 200);
        require(rescanned, "the /portallink command did not trigger a new scan");

        boolean enabledBefore = context.computeOnClient(client -> PortalLinkConfig.get().enabled);
        context.runOnClient(client -> client.player.connection.sendCommand("portallink toggle"));
        context.waitTicks(10);
        boolean enabledAfter = context.computeOnClient(client -> PortalLinkConfig.get().enabled);
        require(enabledBefore != enabledAfter, "/portallink toggle did not flip the master switch");

        // Restore, so nothing leaks into a later test sharing the same process.
        context.runOnClient(client -> client.player.connection.sendCommand("portallink toggle"));
        context.waitTicks(10);
        require(context.computeOnClient(client -> PortalLinkConfig.get().enabled) == enabledBefore,
                "the master switch did not return to its original value");
    }

    private static boolean waitForSnapshotNewerThan(ClientGameTestContext context, long reference,
                                                    int timeoutTicks) {
        for (int waited = 0; waited < timeoutTicks; waited += 5) {
            PortalSnapshot snapshot = context.computeOnClient(
                    client -> ClientPortalLinkState.snapshot());
            if (snapshot.timestamp() > reference) {
                return true;
            }
            context.waitTicks(5);
        }
        return false;
    }
}
