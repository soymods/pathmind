package com.pathmind.ui.menu;

import com.pathmind.nodes.NodeCategory;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Utility class for rendering context menu elements with consistent styling.
 * Provides methods for backgrounds, borders, text, and hover states.
 */
public final class ContextMenuRenderer {

    private static final int TEXT_OFFSET_X = 6;
    private static final int ARROW_SIZE = 3;
    private static final int ARROW_MARGIN = 6;

    private ContextMenuRenderer() {
        // Utility class - prevent instantiation
    }

    /**
     * Renders a menu background with border.
     */
    public static void renderMenuBackground(DrawContext context, int x, int y, int width, int height) {
        renderMenuBackground(context, x, y, width, height, true);
    }

    /**
     * Renders a menu background with optional left border (for submenus).
     */
    public static void renderMenuBackground(DrawContext context, int x, int y, int width, int height, boolean includeLeftBorder) {
        // Fill background
        context.fill(x, y, x + width, y + height, UITheme.CONTEXT_MENU_BG);

        if (includeLeftBorder) {
            // Draw full border
            DrawContextBridge.drawBorder(context, x, y, width, height, UITheme.CONTEXT_MENU_BORDER);
        } else {
            // Draw border without left side (for submenu on right)
            int right = x + width - 1;
            int bottom = y + height - 1;
            // Top border
            context.drawHorizontalLine(x, right, y, UITheme.CONTEXT_MENU_BORDER);
            // Bottom border
            context.drawHorizontalLine(x, right, bottom, UITheme.CONTEXT_MENU_BORDER);
            // Right border
            context.drawVerticalLine(right, y, bottom, UITheme.CONTEXT_MENU_BORDER);
            // No left border - this creates 1px separator from main menu
        }
    }

    /**
     * Renders a menu background without right border (for submenu on left).
     */
    public static void renderMenuBackgroundNoRightBorder(DrawContext context, int x, int y, int width, int height) {
        // Fill background
        context.fill(x, y, x + width, y + height, UITheme.CONTEXT_MENU_BG);

        // Draw border without right side (for submenu on left)
        int right = x + width - 1;
        int bottom = y + height - 1;
        // Top border
        context.drawHorizontalLine(x, right, y, UITheme.CONTEXT_MENU_BORDER);
        // Bottom border
        context.drawHorizontalLine(x, right, bottom, UITheme.CONTEXT_MENU_BORDER);
        // Left border
        context.drawVerticalLine(x, y, bottom, UITheme.CONTEXT_MENU_BORDER);
        // No right border - this creates 1px separator from main menu
    }

    /**
     * Renders a menu item background (with optional hover state).
     * Inset by 1px to avoid covering menu borders.
     */
    public static void renderMenuItem(DrawContext context, int x, int y, int width, int height, boolean hovered) {
        if (hovered) {
            // Inset by 1px on left and right to keep borders visible
            context.fill(x + 1, y, x + width - 1, y + height, UITheme.CONTEXT_MENU_ITEM_HOVER);
        }
    }

    /**
     * Renders a category item with text and arrow.
     */
    public static void renderCategoryItem(DrawContext context, TextRenderer textRenderer,
                                          int x, int y, int width, int height,
                                          NodeCategory category, boolean hovered) {
        // Render hover background
        renderMenuItem(context, x, y, width, height, hovered);

        // Render category text
        int textX = x + TEXT_OFFSET_X;
        int textY = y + (height - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(category.getDisplayName()),
            textX,
            textY,
            UITheme.CONTEXT_MENU_TEXT
        );

        // Render submenu arrow indicator
        int arrowX = x + width - ARROW_MARGIN - ARROW_SIZE;
        int arrowY = y + (height - (ARROW_SIZE * 2)) / 2;
        renderArrow(context, arrowX, arrowY);
    }

    /**
     * Renders a node item with text.
     */
    public static void renderNodeItem(DrawContext context, TextRenderer textRenderer,
                                      int x, int y, int width, int height,
                                      String nodeName, int nodeColor, boolean hovered, boolean indented) {
        // Render hover background
        renderMenuItem(context, x, y, width, height, hovered);

        // Adjust x for indentation if needed
        int itemX = x + (indented ? 10 : 0);

        // Render node text
        int textX = itemX + TEXT_OFFSET_X;
        int textY = y + (height - textRenderer.fontHeight) / 2;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(nodeName),
            textX,
            textY,
            UITheme.CONTEXT_MENU_TEXT
        );
    }

    /**
     * Renders a group header separator (non-clickable).
     */
    public static void renderGroupHeader(DrawContext context, TextRenderer textRenderer,
                                         int x, int y, int width, int height, String groupName) {
        // Render group text at top
        int textX = x + 8;
        int textY = y + 2;
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(groupName),
            textX,
            textY,
            UITheme.CONTEXT_MENU_GROUP_HEADER
        );

        // Render separator line below text
        int lineY = y + textRenderer.fontHeight + 3;
        context.fill(x + 4, lineY, x + width - 4, lineY + 1, UITheme.CONTEXT_MENU_SEPARATOR);
    }

    /**
     * Renders a horizontal separator line.
     */
    public static void renderSeparator(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, UITheme.CONTEXT_MENU_SEPARATOR);
    }

    /**
     * Renders a submenu arrow indicator (►).
     */
    private static void renderArrow(DrawContext context, int x, int y) {
        // Draw a simple right-facing triangle
        int color = UITheme.CONTEXT_MENU_TEXT;

        // Small right-facing arrow
        for (int dy = 0; dy <= ARROW_SIZE * 2; dy++) {
            int width = (dy <= ARROW_SIZE) ? (dy + 1) / 2 : ((ARROW_SIZE * 2) - dy + 1) / 2;
            if (width > 0) {
                context.fill(x, y + dy, x + width, y + dy + 1, color);
            }
        }
    }

    /**
     * Checks if a point is inside a rectangle.
     */
    public static boolean isPointInRect(int px, int py, int rx, int ry, int width, int height) {
        return px >= rx && px < rx + width && py >= ry && py < ry + height;
    }

    /**
     * Clamps a value between min and max.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
