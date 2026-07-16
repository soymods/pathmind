package com.pathmind.ui.sidebar;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shared animated rendering for every entry in the node sidebar. */
final class PathmindSidebarEntryUi {
    private PathmindSidebarEntryUi() {
    }

    static float renderRowBackground(GuiGraphicsExtractor context, int left, int top, int right, int height,
                                     Object animationKey, boolean hovered) {
        float progress = hoverProgress(animationKey, hovered);
        int background = AnimationHelper.lerpColor(
            UITheme.BACKGROUND_SECONDARY, UITheme.BACKGROUND_TERTIARY, progress);
        context.fill(left, top, right, top + height, background);
        return progress;
    }

    static int animatedTextColor(Object animationKey, boolean hovered, int idleColor, int hoverColor) {
        return AnimationHelper.lerpColor(idleColor, hoverColor, hoverProgress(animationKey, hovered));
    }

    static void renderNodeEntry(GuiGraphicsExtractor context, Font textRenderer,
                                int rowLeft, int rowTop, int rowRight, int rowHeight,
                                int indicatorX, int indicatorY, int indicatorSize, int indicatorColor,
                                int textX, int textY, List<String> lines, int lineHeight,
                                int idleTextColor, int accentColor, Object animationKey, boolean hovered) {
        float progress = renderRowBackground(
            context, rowLeft, rowTop, rowRight, rowHeight, animationKey, hovered);
        int animatedIndicator = AnimationHelper.lerpColor(
            indicatorColor, AnimationHelper.brighten(indicatorColor, 1.16f), progress);
        int animatedBorder = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, accentColor, progress);
        UIStyleHelper.drawBeveledPanel(
            context, indicatorX, indicatorY, indicatorSize, indicatorSize,
            animatedIndicator, animatedBorder, UITheme.PANEL_INNER_BORDER);

        int hoverTextColor = AnimationHelper.lerpColor(idleTextColor, UITheme.TEXT_HEADER, 0.28f);
        int textColor = AnimationHelper.lerpColor(idleTextColor, hoverTextColor, progress);
        int lineY = textY;
        for (String line : lines) {
            context.text(textRenderer, Component.literal(line), textX, lineY, textColor);
            lineY += lineHeight;
        }
    }

    private static float hoverProgress(Object animationKey, boolean hovered) {
        return HoverAnimator.getProgress(animationKey, hovered, UITheme.HOVER_ANIM_MS);
    }
}
