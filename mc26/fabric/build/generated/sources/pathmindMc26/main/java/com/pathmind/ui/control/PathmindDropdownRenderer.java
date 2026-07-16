package com.pathmind.ui.control;

import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.TextRenderUtil;
import java.util.function.IntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared renderer for Pathmind dropdown lists.
 * Callers keep ownership of open state, selection, and option lookup.
 */
public final class PathmindDropdownRenderer {
    private PathmindDropdownRenderer() {
    }

    public static int renderTextList(GuiGraphicsExtractor context, Font textRenderer, TextListSpec spec) {
        if (context == null || textRenderer == null || spec == null || spec.width <= 0 || spec.visibleCount <= 0) {
            return -1;
        }

        int rowHeight = Math.max(1, spec.rowHeight);
        int listHeight = spec.visibleCount * rowHeight;
        int animatedHeight = Math.max(1, Math.round(listHeight * Math.max(0f, Math.min(1f, spec.animationProgress))));
        int listRight = spec.x + spec.width;
        int listBottom = spec.y + listHeight;

        int hoverIndex = -1;
        if (spec.animationProgress >= 1f
            && spec.hoverX >= spec.x
            && spec.hoverX <= listRight
            && spec.hoverY >= spec.y
            && spec.hoverY <= listBottom) {
            int row = (spec.hoverY - spec.y) / rowHeight;
            if (row >= 0 && row < spec.visibleCount) {
                hoverIndex = spec.scrollOffset + row;
            }
        }

        Object matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translateZ(matrices, 400.0f);
        context.enableScissor(spec.x, spec.y, spec.x + Math.max(1, spec.width), spec.y + animatedHeight);
        UIStyleHelper.drawScrollContainer(context, spec.x, spec.y, spec.width, listHeight, spec.containerPalette);

        int renderedOptionCount = Math.max(1, spec.optionCount);
        for (int row = 0; row < spec.visibleCount; row++) {
            int optionIndex = spec.scrollOffset + row;
            if (spec.optionCount > 0 && optionIndex >= spec.optionCount) {
                break;
            }

            String optionLabel = spec.optionCount <= 0 ? spec.emptyLabel : spec.labelProvider.apply(optionIndex);
            int rowTop = spec.y + row * rowHeight;
            boolean hovered = spec.optionCount <= 0 ? row == 0 && hoverIndex >= 0 : optionIndex == hoverIndex;
            UIStyleHelper.DropdownRowPalette rowPalette = UIStyleHelper.getDropdownRowPalette(
                spec.accentColor,
                hovered ? 1f : 0f,
                false,
                false
            );
            if (hovered) {
                UIStyleHelper.drawDropdownRow(context, spec.x + 1, rowTop + 1, spec.width - 2, rowHeight - 1, rowPalette);
            }

            if (spec.renderText) {
                int maxTextWidth = Math.max(0, spec.width - (spec.textPadding * 2) - spec.scrollbarAllowance);
                String rowText = TextRenderUtil.trimWithEllipsis(textRenderer, optionLabel, maxTextWidth);
                int textX = spec.centerText
                    ? spec.x + Math.max(spec.textPadding, (spec.width - textRenderer.width(rowText)) / 2)
                    : spec.x + spec.textPadding;
                int baseTextColor = spec.textColorProvider == null ? spec.textColor : spec.textColorProvider.apply(optionIndex);
                int textColor = hovered ? rowPalette.textColor() : baseTextColor;
                context.text(textRenderer, Component.literal(rowText), textX, rowTop + spec.textOffsetY, textColor, false);
            }
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            spec.x,
            spec.y,
            spec.width,
            listHeight,
            renderedOptionCount,
            spec.visibleCount,
            spec.scrollOffset,
            spec.maxScrollOffset,
            spec.scrollbarTrackColor,
            spec.scrollbarThumbColor
        );
        DropdownLayoutHelper.drawOutline(context, spec.x, spec.y, spec.width, listHeight, spec.outlineColor);
        context.disableScissor();
        MatrixStackBridge.pop(matrices);
        return hoverIndex;
    }

    public static final class TextListSpec {
        private final int x;
        private final int y;
        private final int width;
        private final int rowHeight;
        private final int visibleCount;
        private final int optionCount;
        private final int scrollOffset;
        private final int maxScrollOffset;
        private final float animationProgress;
        private final int hoverX;
        private final int hoverY;
        private final int accentColor;
        private final int textColor;
        private final int textPadding;
        private final int textOffsetY;
        private final int scrollbarAllowance;
        private final boolean centerText;
        private final boolean renderText;
        private final String emptyLabel;
        private final IntFunction<String> labelProvider;
        private final IntFunction<Integer> textColorProvider;
        private final UIStyleHelper.ScrollContainerPalette containerPalette;
        private final int scrollbarTrackColor;
        private final int scrollbarThumbColor;
        private final int outlineColor;

        private TextListSpec(Builder builder) {
            this.x = builder.x;
            this.y = builder.y;
            this.width = builder.width;
            this.rowHeight = builder.rowHeight;
            this.visibleCount = builder.visibleCount;
            this.optionCount = builder.optionCount;
            this.scrollOffset = builder.scrollOffset;
            this.maxScrollOffset = builder.maxScrollOffset;
            this.animationProgress = builder.animationProgress;
            this.hoverX = builder.hoverX;
            this.hoverY = builder.hoverY;
            this.accentColor = builder.accentColor;
            this.textColor = builder.textColor;
            this.textPadding = builder.textPadding;
            this.textOffsetY = builder.textOffsetY;
            this.scrollbarAllowance = builder.scrollbarAllowance;
            this.centerText = builder.centerText;
            this.renderText = builder.renderText;
            this.emptyLabel = builder.emptyLabel == null ? "" : builder.emptyLabel;
            this.labelProvider = builder.labelProvider == null ? index -> "" : builder.labelProvider;
            this.textColorProvider = builder.textColorProvider;
            this.containerPalette = builder.containerPalette;
            this.scrollbarTrackColor = builder.scrollbarTrackColor;
            this.scrollbarThumbColor = builder.scrollbarThumbColor;
            this.outlineColor = builder.outlineColor;
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    public static final class Builder {
        private int x;
        private int y;
        private int width;
        private int rowHeight = 18;
        private int visibleCount = 1;
        private int optionCount;
        private int scrollOffset;
        private int maxScrollOffset;
        private float animationProgress = 1f;
        private int hoverX = Integer.MIN_VALUE;
        private int hoverY = Integer.MIN_VALUE;
        private int accentColor;
        private int textColor;
        private int textPadding = 4;
        private int textOffsetY = 4;
        private int scrollbarAllowance;
        private boolean centerText;
        private boolean renderText = true;
        private String emptyLabel = "";
        private IntFunction<String> labelProvider;
        private IntFunction<Integer> textColorProvider;
        private UIStyleHelper.ScrollContainerPalette containerPalette;
        private int scrollbarTrackColor;
        private int scrollbarThumbColor;
        private int outlineColor;

        public Builder bounds(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
            return this;
        }

        public Builder rows(int rowHeight, int visibleCount, int optionCount) {
            this.rowHeight = rowHeight;
            this.visibleCount = visibleCount;
            this.optionCount = optionCount;
            return this;
        }

        public Builder scroll(int scrollOffset, int maxScrollOffset, int scrollbarAllowance) {
            this.scrollOffset = scrollOffset;
            this.maxScrollOffset = maxScrollOffset;
            this.scrollbarAllowance = scrollbarAllowance;
            return this;
        }

        public Builder animation(float animationProgress) {
            this.animationProgress = animationProgress;
            return this;
        }

        public Builder hoverPoint(int hoverX, int hoverY) {
            this.hoverX = hoverX;
            this.hoverY = hoverY;
            return this;
        }

        public Builder colors(int accentColor, int textColor) {
            this.accentColor = accentColor;
            this.textColor = textColor;
            return this;
        }

        public Builder textLayout(int textPadding, int textOffsetY, boolean centerText, boolean renderText) {
            this.textPadding = textPadding;
            this.textOffsetY = textOffsetY;
            this.centerText = centerText;
            this.renderText = renderText;
            return this;
        }

        public Builder labels(String emptyLabel, IntFunction<String> labelProvider) {
            this.emptyLabel = emptyLabel;
            this.labelProvider = labelProvider;
            return this;
        }

        public Builder textColors(IntFunction<Integer> textColorProvider) {
            this.textColorProvider = textColorProvider;
            return this;
        }

        public Builder chrome(UIStyleHelper.ScrollContainerPalette containerPalette, int scrollbarTrackColor,
                              int scrollbarThumbColor, int outlineColor) {
            this.containerPalette = containerPalette;
            this.scrollbarTrackColor = scrollbarTrackColor;
            this.scrollbarThumbColor = scrollbarThumbColor;
            this.outlineColor = outlineColor;
            return this;
        }

        public TextListSpec build() {
            if (containerPalette == null) {
                containerPalette = new UIStyleHelper.ScrollContainerPalette(0, 0, 0, 0, 0);
            }
            return new TextListSpec(this);
        }
    }
}
