package com.heishan.portallink.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.client.scan.PortalScanService;
import com.heishan.portallink.core.PortalSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * Holds the most recent scan result and schedules new scans.
 *
 * <p>A snapshot is rebuilt when the dimension changes, the player moved far enough, or a manual
 * refresh is requested - never every frame.</p>
 *
 * <p>With an integrated server the scan runs on the <b>server thread</b>, because the
 * point-of-interest index is not thread safe and may load chunks. Everything the server task needs
 * is captured on the client thread first, so the task never touches client state. The finished
 * snapshot is published into a volatile field and delivered to waiting callbacks on a later client
 * tick. On a remote server the scan stays on the client thread and only reads loaded chunks.</p>
 */
public final class ClientPortalLinkState {

    /** Re-scan after the player moved this many blocks. */
    /** Movement needed before rescanning. Larger than the vanilla portal search step (16) so the */
    /** set of gates inside the window does not churn while walking, which made the shown area jump. */
    private static final double RESCAN_DISTANCE = 48.0D;

    /** Hard upper bound between two scans, so idle players still refresh. */
    private static final long RESCAN_INTERVAL_MILLIS = 3000L;

    private static volatile PortalSnapshot snapshot = PortalSnapshot.empty(Level.OVERWORLD);

    /** Incremented every time a new snapshot is published. */
    private static volatile long published;

    private static final AtomicBoolean SCAN_IN_FLIGHT = new AtomicBoolean(false);

    /** Callbacks waiting for a snapshot newer than the generation they were registered at. */
    private static final List<Pending> WAITING = new ArrayList<>();

    private record Pending(long generation, java.util.function.Consumer<PortalSnapshot> callback) {
    }

    private static ResourceKey<Level> lastDimension;
    private static double lastX = Double.NaN;
    private static double lastZ = Double.NaN;
    private static long lastScanMillis;
    private static boolean dirty = true;

    private ClientPortalLinkState() {
    }

    /** The latest snapshot, never {@code null}. */
    public static PortalSnapshot snapshot() {
        return snapshot;
    }

    /** Request a rescan on the next tick. */
    public static void markDirty() {
        dirty = true;
    }

    /**
     * Request a rescan and run {@code callback} on the client thread once a <b>newer</b> snapshot
     * exists. Passing {@code null} only requests the scan.
     */
    public static void requestScan(java.util.function.Consumer<PortalSnapshot> callback) {
        if (callback != null) {
            WAITING.add(new Pending(published, callback));
        }
        dirty = true;
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            reset();
            return;
        }
        // A snapshot belongs to one dimension. Keeping the old one across a teleport paints the
        // previous level's columns here for a few seconds, at coordinates that are meaningless in
        // this level, which reads as the range wandering with you.
        if (!level.dimension().equals(lastDimension)) {
            reset();
        }
        if (isScanDue(client, level)) {
            startScan(client, level);
        }
        deliver();
    }

    private static boolean isScanDue(Minecraft client, ClientLevel level) {
        if (dirty || !level.dimension().equals(lastDimension)) {
            return true;
        }
        double dx = client.player.getX() - lastX;
        double dz = client.player.getZ() - lastZ;
        if (Double.isNaN(dx) || Double.isNaN(dz)
                || dx * dx + dz * dz >= RESCAN_DISTANCE * RESCAN_DISTANCE) {
            return true;
        }
        return System.currentTimeMillis() - lastScanMillis >= RESCAN_INTERVAL_MILLIS;
    }

    private static void startScan(Minecraft client, ClientLevel level) {
        // Capture everything the server task needs while we are still on the client thread.
        ResourceKey<Level> dimension = level.dimension();
        BlockPos anchor = client.player.blockPosition();
        markScanStarted(client, level);

        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            publish(PortalScanService.buildOnClientThread(client, level));
            return;
        }
        if (!SCAN_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        server.execute(() -> {
            try {
                publish(PortalScanService.build(server, dimension, anchor));
            } catch (RuntimeException exception) {
                PortalLink.LOGGER.warn("server-side portal scan failed", exception);
            } finally {
                SCAN_IN_FLIGHT.set(false);
            }
        });
    }

    /** Run and clear callbacks whose snapshot has arrived. Client thread only. */
    private static void deliver() {
        if (WAITING.isEmpty()) {
            return;
        }
        long current = published;
        PortalSnapshot latest = snapshot;
        List<Pending> ready = new ArrayList<>();
        WAITING.removeIf(pending -> {
            if (pending.generation() < current) {
                ready.add(pending);
                return true;
            }
            return false;
        });
        for (Pending pending : ready) {
            try {
                pending.callback().accept(latest);
            } catch (RuntimeException exception) {
                PortalLink.LOGGER.warn("portal scan callback failed", exception);
            }
        }
    }

    private static void markScanStarted(Minecraft client, ClientLevel level) {
        lastDimension = level.dimension();
        lastScanMillis = System.currentTimeMillis();
        dirty = false;
        if (client.player != null) {
            lastX = client.player.getX();
            lastZ = client.player.getZ();
        }
    }

    private static void publish(PortalSnapshot built) {
        snapshot = built;
        published++;
        dirty = false;
        lastScanMillis = System.currentTimeMillis();
    }

    private static void reset() {
        snapshot = PortalSnapshot.empty(Level.OVERWORLD);
        WAITING.clear();
        lastDimension = null;
        lastX = Double.NaN;
        lastZ = Double.NaN;
        lastScanMillis = 0L;
        dirty = true;
        SCAN_IN_FLIGHT.set(false);
    }
}
