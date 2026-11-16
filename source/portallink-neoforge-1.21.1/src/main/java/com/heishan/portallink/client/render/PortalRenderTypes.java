package com.heishan.portallink.client.render;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types used by the world overlay on 1.21.1.
 *
 * <h2>Why custom types are needed at all</h2>
 * This generation has no gizmo system, so overlays are plain vertex buffers. Vanilla's
 * {@code RenderType.lines()} / {@code debugQuads()} always depth test, which means an overlay is
 * hidden behind terrain. The "see through" setting therefore needs two extra types that differ only
 * in {@code NO_DEPTH_TEST}; both are otherwise the vanilla debug types (translucent, no culling) so
 * the look matches.
 *
 * <p>Vanilla's own {@code debugLineStrip} is not usable here: it is {@code POSITION_COLOR} with
 * {@code NO_TRANSPARENCY}, so the alpha of every vertex would be ignored.</p>
 */
final class PortalRenderTypes {

    /** Line width for corners and rims, in pixels. */
    private static final double LINE_WIDTH = 3.0D;

    /** Depth-tested translucent lines (default). */
    static final RenderType LINES = RenderType.create(
            "portallink_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            GameRenderer::getRendertypeLinesShader))
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(LINE_WIDTH)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Lines that ignore depth, i.e. visible through terrain. */
    static final RenderType LINES_SEE_THROUGH = RenderType.create(
            "portallink_lines_see_through",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            GameRenderer::getRendertypeLinesShader))
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(LINE_WIDTH)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Translucent quads, both faces visible (default). */
    static final RenderType QUADS = RenderType.create(
            "portallink_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Translucent quads that ignore depth. */
    static final RenderType QUADS_SEE_THROUGH = RenderType.create(
            "portallink_quads_see_through",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private PortalRenderTypes() {
    }

    static RenderType lines(boolean seeThrough) {
        return seeThrough ? LINES_SEE_THROUGH : LINES;
    }

    static RenderType quads(boolean seeThrough) {
        return seeThrough ? QUADS_SEE_THROUGH : QUADS;
    }
}
