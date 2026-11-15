package com.heishan.portallink.client.gui;

import java.util.List;

import com.heishan.portallink.PortalLink;
import com.heishan.portallink.client.ClientPortalLinkState;
import com.heishan.portallink.config.PortalLinkConfig;
import com.heishan.portallink.i18n.PortalI18n;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen: language, rendering toggles, limits and a rescan button.
 *
 * <p>The layout is computed from the row count and the screen height, so the controls stay inside
 * the window at any GUI scale. The previous version anchored the first row at {@code height / 4}
 * and silently overflowed on small GUI heights, hiding the last row behind the footer.</p>
 *
 * <p>Everything is applied live; values are persisted when the screen closes.</p>
 */
public final class PortalLinkConfigScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;
    private static final int WIDGET_WIDTH = 210;
    private static final int TITLE_TOP = 12;
    private static final int FOOTER_HEIGHT = 22;

    /** Rows: master, language, 3 toggle rows, 3 sliders, button row. */
    private static final int ROW_COUNT = 9;

    private final Screen parent;
    private PortalLinkConfig config;

    public PortalLinkConfigScreen(Screen parent) {
        super(Component.translatable("portallink.config.title"));
        this.parent = parent;
        this.config = PortalLinkConfig.get();
    }

    @Override
    protected void init() {
        config = PortalLinkConfig.get();

        int centerX = this.width / 2;
        int left = centerX - WIDGET_WIDTH / 2;
        int column = WIDGET_WIDTH / 2 - 2;

        // Centre the whole block vertically, leaving room for the title and the footer.
        int blockHeight = ROW_COUNT * ROW_HEIGHT + 6;
        int top = Math.max(TITLE_TOP + 16,
                (this.height - blockHeight - FOOTER_HEIGHT) / 2);
        int y = top;

        addRenderableWidget(Button.builder(masterLabel(), button -> {
                    config.enabled = !config.enabled;
                    button.setMessage(masterLabel());
                    ClientPortalLinkState.markDirty();
                })
                .bounds(left, y, WIDGET_WIDTH, ROW_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("portallink.config.master.tip")))
                .build());
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(CycleButton.<String>builder(this::languageLabel, () -> config.language)
                .withValues(PortalLinkConfig.LANGUAGE_AUTO, PortalLinkConfig.LANGUAGE_ZH_CN,
                        PortalLinkConfig.LANGUAGE_EN_US)
                .create(left, y, WIDGET_WIDTH, ROW_HEIGHT,
                        Component.translatable("portallink.config.language"),
                        (button, value) -> {
                            config.language = value;
                            PortalI18n.invalidate();
                        }));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(toggle(left, y, column, "portallink.config.ranges",
                config.renderRanges, value -> config.renderRanges = value));
        addRenderableWidget(toggle(left + column + 4, y, column, "portallink.config.labels",
                config.renderLabels, value -> config.renderLabels = value));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(toggle(left, y, column, "portallink.config.connectors",
                config.renderConnectors, value -> config.renderConnectors = value));
        addRenderableWidget(toggle(left + column + 4, y, column, "portallink.config.always_on_top",
                config.alwaysOnTop, value -> config.alwaysOnTop = value));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(toggle(left, y, column, "portallink.config.panel",
                config.showStatusPanel, value -> config.showStatusPanel = value));
        addRenderableWidget(toggle(left + column + 4, y, column, "portallink.config.warn",
                config.warnOnConflict, value -> config.warnOnConflict = value));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new IntSlider(left, y, WIDGET_WIDTH, ROW_HEIGHT,
                Component.translatable("portallink.config.distance"), " m",
                32, 1024, 16, config.maxRenderDistance,
                value -> config.maxRenderDistance = value));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new IntSlider(left, y, WIDGET_WIDTH, ROW_HEIGHT,
                Component.translatable("portallink.config.max_portals"), "",
                1, 128, 1, config.maxRenderedPortals,
                value -> config.maxRenderedPortals = value));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new FloatSlider(left, y, WIDGET_WIDTH, ROW_HEIGHT,
                Component.translatable("portallink.config.label_scale"), "x",
                0.5F, 2.5F, config.labelScale,
                value -> config.labelScale = value));
        y += ROW_HEIGHT + 6;

        addRenderableWidget(Button.builder(Component.translatable("portallink.config.rescan"),
                        button -> ClientPortalLinkState.requestScan(null))
                .bounds(left, y, column, ROW_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + column + 4, y, column, ROW_HEIGHT)
                .build());
    }

    private Component masterLabel() {
        return Component.translatable(config.enabled
                ? "portallink.config.master.on"
                : "portallink.config.master.off");
    }

    private Component languageLabel(String value) {
        return switch (value) {
            case PortalLinkConfig.LANGUAGE_ZH_CN -> Component.translatable("portallink.lang.zh_cn");
            case PortalLinkConfig.LANGUAGE_EN_US -> Component.translatable("portallink.lang.en_us");
            default -> Component.translatable("portallink.lang.auto");
        };
    }

    private CycleButton<Boolean> toggle(int x, int y, int width, String key, boolean initial,
                                        java.util.function.Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .create(x, y, width, ROW_HEIGHT, Component.translatable(key),
                        (button, value) -> setter.accept(value));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, TITLE_TOP, 0xFFFFFFFF);

        // The footer only fits when the screen is tall enough; draw it above the bottom edge.
        int footerTop = this.height - FOOTER_HEIGHT;
        if (footerTop > lastWidgetBottom() + 4) {
            List<Component> footer = List.of(
                    Component.translatable("portallink.config.footer.author")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("portallink.config.footer.links")
                            .withStyle(ChatFormatting.DARK_GRAY));
            int y = footerTop + 2;
            for (Component line : footer) {
                graphics.centeredText(this.font, line, this.width / 2, y, 0xFFFFFFFF);
                y += 10;
            }
        }
    }

    private int lastWidgetBottom() {
        int bottom = 0;
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                bottom = Math.max(bottom, widget.getBottom());
            }
        }
        return bottom;
    }

    @Override
    public void onClose() {
        config.save();
        PortalI18n.invalidate();
        ClientPortalLinkState.markDirty();
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(Screen parent) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        client.setScreenAndShow(new PortalLinkConfigScreen(parent));
        PortalLink.LOGGER.debug("opened config screen");
    }
}
