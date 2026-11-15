package com.heishan.portallink.client.render;

import com.heishan.portallink.client.PortalIdStore;
import java.util.List;

import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.LinkGroups;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.core.PortalSnapshot;
import com.heishan.portallink.i18n.PortalI18n;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

/**
 * Compact status panel drawn in the top-left corner.
 *
 * <p>Part of the same master switch as the world rendering: one key turns the whole mod's display on
 * and off, so the panel must not be left behind on screen when the ranges are hidden, and must not
 * require a second key to appear. {@code showStatusPanel} stays available in the settings for players
 * who want the ranges without the panel.</p>
 */
public final class PortalLinkHud implements HudElement {

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LINES = 7;
    private static final int BACKGROUND = 0x90000000;

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        PortalLinkConfig config = PortalLinkConfig.get();
        // Both gates are required, but the master switch alone is enough to hide the whole mod - that
        // is what makes "press P to hide everything" true.
        if (!config.enabled || !config.showStatusPanel) {
            return;
        }
        PortalSnapshot snapshot = ClientPortalLinkState.snapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.font == null) {
            return;
        }

        List<PortalEntry> entries = snapshot.entries();
        int shown = Math.min(entries.size(), MAX_LINES);
        String title = PortalI18n.tr("portallink.hud.title", entries.size());

        // Chat/HUD text cannot carry custom colours, so each line is tinted with its group colour.
        // That keeps the panel consistent with the in-world colours.
        LinkGroups groups = LinkGroups.of(snapshot);
        LinkColorAssigner colors = new LinkColorAssigner(snapshot, groups);

        int width = client.font.width(title);
        for (int index = 0; index < shown; index++) {
            String line = line(entries.get(index));
            width = Math.max(width, client.font.width(line));
        }
        int height = LINE_HEIGHT * (shown + 1) + 2;

        graphics.fill(MARGIN - 2, MARGIN - 2, MARGIN + width + 2, MARGIN + height, BACKGROUND);
        graphics.text(client.font, title, MARGIN, MARGIN, 0xFFFFFFFF, true);

        for (int index = 0; index < shown; index++) {
            PortalEntry entry = entries.get(index);
            int y = MARGIN + LINE_HEIGHT * (index + 1);
            graphics.text(client.font, line(entry), MARGIN, y,
                    colors.colorOf(entry, groups), true);
        }
    }

    private static String line(PortalEntry entry) {
        PortalRef portal = entry.portal().ref();
        BlockPos self = portal.centerPos();
        String status = PortalI18n.tr("portallink.status." + entry.status().name().toLowerCase());
        int selfId = PortalIdStore.get().idOf(portal);
        if (entry.hasMatch()) {
            PortalRef partner = entry.matched().ref();
            BlockPos partnerPos = partner.centerPos();
            // Numbers lead each side: with several gates in one area, "#3 -> #7" is far easier to hold
            // in your head than two coordinate triples.
            return PortalI18n.tr("portallink.hud.line_linked_numbered", status, selfId,
                    self.getX(), self.getY(), self.getZ(),
                    PortalIdStore.get().idOf(partner),
                    partnerPos.getX(), partnerPos.getY(), partnerPos.getZ(),
                    Math.round(entry.matchedDistance()));
        }
        BlockPos target = entry.targetAnchor();
        return PortalI18n.tr("portallink.hud.line_open_numbered", status, selfId,
                self.getX(), self.getY(), self.getZ(),
                target.getX(), target.getY(), target.getZ());
    }
}
