package com.heishan.portallink.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * Slider bound to an integer range, with an optional suffix.
 */
public class IntSlider extends AbstractSliderButton {

    private final int min;
    private final int max;
    private final Component label;
    private final String suffix;
    private final int step;
    private final java.util.function.IntConsumer onChange;

    public IntSlider(int x, int y, int width, int height, Component label, String suffix,
                     int min, int max, int step, int value, java.util.function.IntConsumer onChange) {
        super(x, y, width, height, Component.empty(), toFraction(value, min, max));
        this.label = label;
        this.suffix = suffix;
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.onChange = onChange;
        updateMessage();
    }

    private static double toFraction(int value, int min, int max) {
        if (max <= min) {
            return 0.0D;
        }
        return (double) (value - min) / (double) (max - min);
    }

    public int intValue() {
        int raw = min + (int) Math.round(value * (max - min));
        int snapped = Math.round((float) (raw - min) / step) * step + min;
        return Math.max(min, Math.min(max, snapped));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label.getString() + ": " + intValue() + suffix));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }
}
