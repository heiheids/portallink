package com.heishan.portallink.client.render;

import java.util.List;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.core.LinkGroups;
import com.heishan.portallink.core.PortalEntry;
import com.heishan.portallink.core.PortalRef;
import com.heishan.portallink.core.PortalSnapshot;
import com.heishan.portallink.core.RangeMarker;
import com.heishan.portallink.core.RangePlanner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Entry point for in-world drawing on 1.21.1.
 *
 * <h2>Why this generation needs its own draw code</h2>
 * 1.21.1 has no gizmo API: an overlay is raw vertices written into a {@link MultiBufferSource}. The
 * only stage that offers both a valid pose matrix and usable consumers is
 * {@code AFTER_ENTITIES} ({@code silent-failures.md} B5), so that is the hook.
 *
 * <h2>The pose stack does not contain the camera here</h2>
 * At this stage the matrix is the identity, so a vertex submitted with world coordinates would be
 * placed relative to the world origin. The camera offset is therefore applied once here
 * ({@code translate(-cam.x, -cam.y, -cam.z)}) and every helper below works in plain world
 * coordinates. Verified against the engine's own behaviour: {@code LevelRenderer#renderEntity}
 * subtracts the camera position before submitting.
 *
 * <p>The camera must come from {@code GameRenderer#getMainCamera()}. The event carries its own
 * reference, and at this stage that one can lag the position the frame is actually rendered with;
 * subtracting a slightly different value shifts every vertex and reads as the overlay sliding while
 * walking. The other 1.21.1 overlay in this pack (ironfarm) reads the main camera for that reason.</p>
 *
 * <p>The scan data, the planning and the colours are the same code as every other build of this
 * mod; only this translation layer differs.</p>
 */
public final class PortalWorldRenderer {

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }

        PortalLinkConfig config = PortalLinkConfig.get();
        PortalSnapshot snapshot = ClientPortalLinkState.snapshot();
        // A snapshot belongs to one dimension. For the frame right after a teleport the previous
        // level's snapshot may still be in hand; drawing it here would put the other dimension's
        // columns into this level at coordinates that mean nothing here.
        if (snapshot != null && !snapshot.sourceDim().equals(client.level.dimension())) {
            snapshot = null;
        }
        if (!config.enabled || snapshot == null || snapshot.isEmpty()) {
            return;
        }

        Vec3 eye = client.gameRenderer.getMainCamera().getPosition();
        double maxDistSq = (double) config.maxRenderDistance * config.maxRenderDistance;

        LinkGroups groups = LinkGroups.of(snapshot);
        LinkColorAssigner colors = new LinkColorAssigner(snapshot, groups);

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();

        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);
        List<RangeMarker> markers = List.of();
        try {
            if (config.renderRanges) {
                markers = RangePlanner.plan(snapshot, groups,
                        client.level.getMinBuildHeight(), client.level.getMaxBuildHeight());
                PortalRangeRenderer.render(pose, buffers, markers, colors, config, eye, maxDistSq);
            }

            int drawn = 0;
            for (PortalEntry entry : snapshot.entries()) {
                if (drawn >= config.maxRenderedPortals) {
                    break;
                }
                PortalRef portal = entry.portal().ref();
                if (portal.center().distanceToSqr(eye) > maxDistSq) {
                    continue;
                }
                drawn++;
                drawPortalGeometry(pose, buffers, entry, colors.colorOf(entry, groups), config, eye,
                        maxDistSq);
            }
        } catch (RuntimeException exception) {
            // A drawing error must not take the frame down; the next frame retries.
            PortalLink.LOGGER.warn("world overlay could not be drawn", exception);
        } finally {
            pose.popPose();
        }

        // Labels are drawn with the pose back at identity on purpose:
        // DebugRenderer#renderFloatingText applies the camera offset itself (it translates by
        // `x - camera` before handing the matrix to the font). Calling it inside the translated
        // block above subtracted the camera twice, which pushed every label far off screen.
        if (config.renderLabels) {
            try {
                PortalRangeRenderer.drawAreaLabels(pose, buffers, markers, colors, config, eye,
                        maxDistSq);
                int labelled = 0;
                for (PortalEntry entry : snapshot.entries()) {
                    if (labelled >= config.maxRenderedPortals) {
                        break;
                    }
                    if (entry.portal().ref().center().distanceToSqr(eye) > maxDistSq) {
                        continue;
                    }
                    labelled++;
                    PortalLabelRenderer.render(pose, buffers, entry,
                            colors.colorOf(entry, groups), config);
                }
            } catch (RuntimeException exception) {
                PortalLink.LOGGER.warn("portal labels could not be drawn", exception);
            }
        }

        buffers.endBatch();
    }

    /** Frame, ghost and connector: everything that belongs in camera-translated space. */
    private static void drawPortalGeometry(PoseStack pose, MultiBufferSource buffers,
                                           PortalEntry entry, int color, PortalLinkConfig config,
                                           Vec3 eye, double maxDistSq) {
        // Wireframe hugging the portal blocks, so the frame itself is unambiguous.
        VertexConsumer buffer = buffers.getBuffer(PortalRenderTypes.lines(config.alwaysOnTop));
        AABB bounds = entry.portal().ref().bounds().inflate(0.02D);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        LevelRenderer.renderLineBox(pose, buffer, bounds, red, green, blue, 1.0F);

        if (config.renderGhosts) {
            PortalGhostRenderer.render(pose, buffers, entry, color, config, eye, maxDistSq);
        }
        if (config.renderConnectors) {
            PortalGhostRenderer.renderConnector(pose, buffers, entry, color, config, eye, maxDistSq);
        }
    }
}
