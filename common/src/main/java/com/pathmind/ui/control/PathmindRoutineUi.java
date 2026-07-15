package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Shared animated controls and surfaces used by the routine editor and sidebar. */
public final class PathmindRoutineUi {
    private PathmindRoutineUi() {
    }

    public static int workspaceBackground(int baseColor, int routineColor, float openProgress) {
        return AnimationHelper.lerpColor(baseColor, routineColor,
            Math.max(0f, Math.min(1f, openProgress)) * 0.04f);
    }

    public static boolean renderReturnButton(DrawContext context, int x, int y, int size,
                                             int mouseX, int mouseY, float hoverProgress,
                                             int routineColor) {
        boolean hovered = PathmindWorkspaceChrome.contains(mouseX, mouseY, x, y, size, size);
        PathmindWorkspaceChrome.drawToolbarButtonFrame(
            context, x, y, size, size, hovered, false, false, hoverProgress, routineColor);
        int iconSize = Math.max(6, size / 3);
        int iconX = x + (size - iconSize) / 2;
        int iconY = y + (size - iconSize) / 2;
        int iconColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, hoverProgress);
        PathmindIconRenderer.drawCloseX(context, iconX, iconY, iconSize, iconColor);
        return hovered;
    }

    public static void renderRoutineMarker(DrawContext context, int x, int y, int size,
                                           Object animationKey, boolean active, int routineColor) {
        float progress = HoverAnimator.getProgress(animationKey, active, UITheme.TRANSITION_ANIM_MS);
        int fill = AnimationHelper.lerpColor(
            AnimationHelper.darken(routineColor, 0.72f), routineColor, progress);
        int border = AnimationHelper.lerpColor(UITheme.BORDER_SUBTLE, routineColor, progress);
        UIStyleHelper.drawBeveledPanel(context, x, y, size, size, fill, border, UITheme.PANEL_INNER_BORDER);
    }

    public static void renderSidebarActionButton(DrawContext context, int x, int y, int size,
                                                 Object animationKey, boolean hovered, boolean danger,
                                                 int routineColor, PathmindWorkspaceChrome.IconPainter iconPainter) {
        float progress = HoverAnimator.getProgress(animationKey, hovered, UITheme.HOVER_ANIM_MS);
        int accent = danger ? UITheme.BORDER_DANGER : routineColor;
        UIStyleHelper.ToolbarButtonPalette palette = UIStyleHelper.getToolbarButtonPalette(accent, progress, false, false);
        UIStyleHelper.drawToolbarButtonFrame(context, x, y, size, size, palette);
        int idleIcon = danger ? UITheme.TEXT_DANGER_MUTED : UITheme.TEXT_TERTIARY;
        int iconColor = AnimationHelper.lerpColor(idleIcon, danger ? UITheme.STATE_ERROR : UITheme.TEXT_HEADER, progress);
        int padding = Math.max(2, size / 4);
        iconPainter.draw(context, x + padding, y + padding, Math.max(4, size - padding * 2), iconColor);
    }

    public static int animatedTextColor(Object animationKey, boolean hovered, int idleColor, int hoverColor) {
        float progress = HoverAnimator.getProgress(animationKey, hovered, UITheme.HOVER_ANIM_MS);
        return AnimationHelper.lerpColor(idleColor, hoverColor, progress);
    }

    public static void renderInputAction(DrawContext context, TextRenderer textRenderer, String symbol,
                                         int x, int y, Object animationKey, boolean hovered, int routineColor) {
        int color = animatedTextColor(animationKey, hovered, UITheme.TEXT_TERTIARY, routineColor);
        context.drawTextWithShadow(textRenderer, Text.literal(symbol), x, y, color);
    }

    public static void renderDropTarget(DrawContext context, TextRenderer textRenderer, int x, int y,
                                        int width, int height, int mouseX, int mouseY,
                                        Object animationKey, String label, int routineColor) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        float progress = HoverAnimator.getProgress(animationKey, hovered, UITheme.HOVER_ANIM_MS);
        int background = AnimationHelper.lerpColor(UITheme.BACKGROUND_SIDEBAR, UITheme.BACKGROUND_TERTIARY, progress * 0.65f);
        int border = AnimationHelper.lerpColor(UITheme.BORDER_DEFAULT, routineColor, progress);
        int textColor = AnimationHelper.lerpColor(UITheme.TEXT_TERTIARY, UITheme.TEXT_PRIMARY, progress);
        context.fill(x, y, x + width, y + height, background);
        DrawContextBridge.drawBorder(context, x, y, width, height, border);
        int labelX = x + Math.max(2, (width - textRenderer.getWidth(label)) / 2);
        context.drawTextWithShadow(textRenderer, Text.literal(label), labelX,
            y + (height - textRenderer.fontHeight) / 2 + 1, textColor);
    }
}
