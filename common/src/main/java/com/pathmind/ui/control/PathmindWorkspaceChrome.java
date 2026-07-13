package com.pathmind.ui.control;

import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Shared rendering and layout primitives for workspace toolbar chrome.
 */
public final class PathmindWorkspaceChrome {
    private PathmindWorkspaceChrome() {
    }

    public static int topButtonY(int titleBarHeight, int margin) {
        return titleBarHeight + margin;
    }

    public static int bottomButtonY(int screenHeight, int margin, int buttonSize) {
        return screenHeight - margin - buttonSize;
    }

    public static int publishButtonX(int sidebarWidth, int margin) {
        return sidebarWidth + margin;
    }

    public static int settingsButtonX(int sidebarWidth, int margin) {
        return sidebarWidth + margin;
    }

    public static int marketplaceButtonX(int sidebarWidth, int margin, int buttonSize, int spacing) {
        return sidebarWidth + margin + buttonSize + spacing;
    }

    public static int importExportButtonX(int settingsButtonX, int buttonSize, int spacing) {
        return settingsButtonX + buttonSize + spacing;
    }

    public static int clearButtonX(int settingsButtonX, int buttonSize, int spacing) {
        return settingsButtonX + (buttonSize + spacing) * 2;
    }

    public static int homeButtonX(int settingsButtonX, int buttonSize, int spacing) {
        return settingsButtonX + (buttonSize + spacing) * 3;
    }

    public static boolean contains(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static boolean renderButtonFrame(DrawContext context, int x, int y, int width, int height,
                                            int mouseX, int mouseY, boolean active, boolean disabled,
                                            float hoverProgress, int accentColor) {
        boolean hovered = !disabled && contains(mouseX, mouseY, x, y, width, height);
        drawToolbarButtonFrame(context, x, y, width, height, hovered, active, disabled, hoverProgress, accentColor);
        return hovered;
    }

    public static void drawToolbarButtonFrame(DrawContext context, int x, int y, int width, int height,
                                              boolean hovered, boolean active, boolean disabled,
                                              float hoverProgress, int accentColor) {
        UIStyleHelper.ToolbarButtonPalette palette = UIStyleHelper.getToolbarButtonPalette(accentColor, hoverProgress, active || hovered, disabled);
        UIStyleHelper.drawToolbarButtonFrame(context, x, y, width, height, palette);
    }

    public static boolean renderMarketplaceButton(DrawContext context, TextRenderer textRenderer,
                                                  int x, int y, int width, int height,
                                                  int mouseX, int mouseY, float hoverProgress,
                                                  int accentColor, String label) {
        boolean hovered = contains(mouseX, mouseY, x, y, width, height);
        drawToolbarButtonFrame(context, x, y, width, height, hovered, false, false, hoverProgress, accentColor);
        int textColor = hovered ? accentColor : UITheme.TEXT_PRIMARY;
        int textX = x + (width - textRenderer.getWidth(label)) / 2;
        int textY = y + (height - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(textRenderer, Text.literal(label), textX, textY, textColor);
        return hovered;
    }

    public static boolean renderPublishButton(DrawContext context, int x, int y, int size,
                                              int mouseX, int mouseY, float hoverProgress,
                                              int accentColor, boolean synced) {
        boolean hovered = contains(mouseX, mouseY, x, y, size, size);
        if (synced) {
            UIStyleHelper.ToolbarButtonPalette palette = UIStyleHelper.getToolbarButtonPalette(accentColor, hoverProgress, false, false);
            int iconColor = AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, accentColor, hoverProgress);
            UIStyleHelper.drawToolbarButtonFrame(context, x, y, size, size, palette);
            PathmindIconRenderer.drawPublishArrow(context, x, y, size, iconColor);
            return hovered;
        }

        int idleBackground = mixColor(UITheme.BACKGROUND_SECTION, accentColor, 0.46f);
        int hoverBackground = mixColor(UITheme.BACKGROUND_SECTION, accentColor, 0.62f);
        int background = AnimationHelper.lerpColor(idleBackground, hoverBackground, hoverProgress);
        int border = AnimationHelper.lerpColor(accentColor, UITheme.TEXT_HEADER, hoverProgress * 0.22f);
        int iconColor = AnimationHelper.lerpColor(
            AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, accentColor, 0.42f),
            UITheme.TEXT_HEADER,
            hoverProgress * 0.35f
        );
        UIStyleHelper.drawToolbarButtonFrame(context, x, y, size, size, background, border, UITheme.PANEL_INNER_BORDER);
        PathmindIconRenderer.drawPublishArrow(context, x, y, size, iconColor);
        return hovered;
    }

    public static void drawHomeIcon(DrawContext context, int buttonX, int buttonY, int buttonSize, int color) {
        int centerX = buttonX + buttonSize / 2;
        int centerY = buttonY + buttonSize / 2;
        context.drawHorizontalLine(centerX - 4, centerX + 2, centerY, color);
        context.drawVerticalLine(centerX - 4, centerY - 4, centerY + 2, color);
        context.drawHorizontalLine(centerX - 2, centerX, centerY - 2, color);
        context.drawHorizontalLine(centerX - 3, centerX - 1, centerY - 1, color);
        context.drawVerticalLine(centerX - 2, centerY - 2, centerY, color);
        context.drawVerticalLine(centerX - 3, centerY - 3, centerY - 1, color);
    }

    public static void drawClearIcon(DrawContext context, int buttonX, int buttonY, int buttonSize, int color) {
        int centerX = buttonX + buttonSize / 2;
        int top = buttonY + 4;
        int bottom = buttonY + buttonSize - 4;
        context.drawHorizontalLine(centerX - 5, centerX + 4, top, color);
        context.drawVerticalLine(centerX - 5, top, top + 2, color);
        context.drawVerticalLine(centerX + 4, top, top + 2, color);
        context.drawHorizontalLine(centerX - 4, centerX + 3, top + 2, color);
        context.drawVerticalLine(centerX - 3, top + 2, bottom, color);
        context.drawVerticalLine(centerX + 2, top + 2, bottom, color);
        context.drawHorizontalLine(centerX - 3, centerX + 2, bottom, color);
    }

    public static void drawImportExportIcon(DrawContext context, int buttonX, int buttonY, int buttonSize, int color) {
        int centerX = buttonX + buttonSize / 2;
        int centerY = buttonY + buttonSize / 2;
        context.drawVerticalLine(centerX - 4, centerY - 5, centerY, color);
        context.drawHorizontalLine(centerX - 6, centerX - 2, centerY - 5, color);
        context.drawHorizontalLine(centerX - 5, centerX - 3, centerY - 4, color);
        context.drawVerticalLine(centerX + 3, centerY, centerY + 5, color);
        context.drawHorizontalLine(centerX + 1, centerX + 5, centerY + 5, color);
        context.drawHorizontalLine(centerX + 2, centerX + 4, centerY + 4, color);
        context.drawHorizontalLine(centerX - 4, centerX + 3, centerY, color);
    }

    public static void drawSettingsIcon(DrawContext context, int buttonX, int buttonY, int buttonSize, int color) {
        PathmindIconRenderer.drawSettings(context, buttonX, buttonY, buttonSize, color, UITheme.GRID_ORIGIN);
    }

    public static int mixColor(int color, int target, float ratio) {
        int a = (int) (((color >>> 24) & 0xFF) * (1.0f - ratio) + ((target >>> 24) & 0xFF) * ratio);
        int r = (int) (((color >>> 16) & 0xFF) * (1.0f - ratio) + ((target >>> 16) & 0xFF) * ratio);
        int g = (int) (((color >>> 8) & 0xFF) * (1.0f - ratio) + ((target >>> 8) & 0xFF) * ratio);
        int b = (int) ((color & 0xFF) * (1.0f - ratio) + (target & 0xFF) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
