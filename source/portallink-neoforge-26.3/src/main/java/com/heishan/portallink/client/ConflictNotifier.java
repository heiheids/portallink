package com.heishan.portallink.client;

import java.util.HashSet;
import java.util.Set;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.LinkStatus;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalSnapshot;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * One-shot chat warning when a portal's link is asymmetric.
 *
 * <p>Each portal is reported once per dimension visit; the set is cleared on a dimension change so
 * the same portal can warn again from the other side, where the asymmetry looks different.</p>
 */
public final class ConflictNotifier {

    private static final Set<String> REPORTED = new HashSet<>();
    private static ResourceKey<Level> lastDimension;

    /** Number of warnings actually shown since the last {@link #reset()}. */
    private static volatile int warningCount;
    private static volatile String lastWarnedKey = "";

    private ConflictNotifier() {
    }

    public static void tick() {
        PortalLinkConfig config = PortalLinkConfig.get();
        if (!config.enabled || !config.warnOnConflict) {
            return;
        }
        PortalSnapshot snapshot = ClientPortalLinkState.snapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        if (!snapshot.sourceDim().equals(lastDimension)) {
            REPORTED.clear();
            lastDimension = snapshot.sourceDim();
        }
        for (PortalEntry entry : snapshot.entries()) {
            if (entry.status() != LinkStatus.CONFLICT || !REPORTED.add(entry.key())) {
                continue;
            }
            announce(entry);
        }
    }

    private static void announce(PortalEntry entry) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        BlockPos self = entry.portal().ref().centerPos();
        BlockPos partner = entry.matched() == null ? null : entry.matched().ref().centerPos();
        int px = partner == null ? 0 : partner.getX();
        int py = partner == null ? 0 : partner.getY();
        int pz = partner == null ? 0 : partner.getZ();

        // The numbers are what make the warning actionable: "which portal is the wrong one" is the
        // question, and coordinates alone do not answer it while playing.
        int selfId = PortalIdStore.get().idOf(entry.portal().ref());
        int partnerId = entry.matched() == null
                ? 0
                : PortalIdStore.get().idOf(entry.matched().ref());

        Component message = Component
                .translatable("portallink.warn.conflict_numbered", selfId,
                        self.getX(), self.getY(), self.getZ(), partnerId, px, py, pz)
                .withStyle(ChatFormatting.RED);
        client.player.sendSystemMessage(message);
        lastWarnedKey = entry.key();
        warningCount++;
        PortalLink.LOGGER.debug("asymmetric link at {} -> {}", self.toShortString(),
                partner == null ? "none" : partner.toShortString());
    }

    /** Reset on world unload so a new world re-reports its own problems. */
    public static void reset() {
        REPORTED.clear();
        lastDimension = null;
        lastWarnedKey = "";
        warningCount = 0;
    }

    /**
     * How many warnings have been shown since the last reset. Exposed because the chat history is
     * not readable from outside the vanilla chat component, which makes this the only observable
     * evidence that the warning fired. Also handy when debugging a report of "I got no warning".
     */
    public static int warningCount() {
        return warningCount;
    }

    /** Key of the portal warned about last, or an empty string when nothing was warned. */
    public static String lastWarnedKey() {
        return lastWarnedKey;
    }
}
