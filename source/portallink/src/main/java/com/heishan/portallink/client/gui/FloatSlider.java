package com.heishan.portallink.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * Slider bound to a float range, shown with one decimal.
 */
public class FloatSlider extends AbstractSliderButton {

    private final float min;
    private final float max;
    private final Component label;
    private final String suffix;
    private final java.util.function.Consumer<Float> onChange;

    public FloatSlider(int x, int y, int width, int height, Component label, String suffix,
                       float min, float max, float value, java.util.function.Consumer<Float> onChange) {
        super(x, y, width, height, Component.empty(), toFraction(value, min, max));
        this.label = label;
        this.suffix = suffix;
        this.min = min;
        this.max = max;
        this.onChange = onChange;
        updateMessage();
    }

    private static double toFraction(float value, float min, float max) {
        if (max <= min) {
            return 0.0D;
        }
        return (value - min) / (max - min);
    }

    public float floatValue() {
        float raw = min + (float) value * (max - min);
        return Math.max(min, Math.min(max, Math.round(raw * 10.0F) / 10.0F));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label.getString() + ": "
                + String.format(java.util.Locale.ROOT, "%.1f", floatValue()) + suffix));
    }

    @Override
    protected void applyValue() {
        onChange.accept(floatValue());
    }
}
