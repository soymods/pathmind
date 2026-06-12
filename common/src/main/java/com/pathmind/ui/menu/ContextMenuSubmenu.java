package com.pathmind.ui.menu;

import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Submenu that displays nodes within a category.
 * Supports grouped categories (SENSORS, PARAMETERS) with collapsible headers.
 */
public class ContextMenuSubmenu {

    private static final int MENU_WIDTH = UITheme.CONTEXT_MENU_WIDTH;
    private static final int ITEM_HEIGHT = UITheme.CONTEXT_MENU_ITEM_HEIGHT;
    private static final int PADDING = UITheme.CONTEXT_MENU_PADDING;
    private static final int MAX_VISIBLE_ITEMS = 15;

    private final NodeCategory category;
    private final List<SubmenuItem> items;

    private int submenuX;
    private int submenuY;
    private int submenuHeight;
    private int scrollOffset;
    private int maxScroll;
    private float scale = 1.0f;
    private int anchorX = 0;
    private int anchorY = 0;

    private NodeType hoveredNode;

    public ContextMenuSubmenu(Sidebar sidebar, NodeCategory category) {
        this.category = category;
        this.items = new ArrayList<>();
        this.scrollOffset = 0;
        this.hoveredNode = null;

        // Build item list from sidebar data
        buildItems(sidebar);

        // Calculate height
        int visibleItems = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        this.submenuHeight = PADDING * 2 + (visibleItems * ITEM_HEIGHT);
        this.maxScroll = Math.max(0, (items.size() - MAX_VISIBLE_ITEMS) * ITEM_HEIGHT);
    }

    /**
     * Sets the position of the submenu, adjusting for screen bounds.
     */
    public void setPosition(int x, int y, int screenWidth, int screenHeight, boolean positionedLeft, int anchorX, int anchorY, float scale) {
        this.submenuX = x;
        this.submenuY = y;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.scale = Math.max(0.05f, scale);
    }

    /**
     * Updates hover state based on mouse position.
     */
    public void updateHover(int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY)) {
            hoveredNode = null;
            return;
        }

        // Find hovered item
        int itemY = submenuY + PADDING - scrollOffset;
        hoveredNode = null;

        for (SubmenuItem item : items) {
            if (item.nodeType != null) {
                if (ContextMenuRenderer.isPointInRect(mouseX, mouseY, submenuX, itemY, MENU_WIDTH, ITEM_HEIGHT)) {
                    hoveredNode = item.nodeType;
                    break;
                }
            }
            itemY += ITEM_HEIGHT;
        }
    }

    /**
     * Returns true if the mouse is over the submenu.
     */
    public boolean isHovered(int mouseX, int mouseY) {
        return ContextMenuRenderer.isPointInRect(mouseX, mouseY, submenuX, submenuY, MENU_WIDTH, submenuHeight);
    }

    /**
     * Handles a click event. Returns the selected NodeType, or null if nothing was clicked.
     */
    public NodeType handleClick(int mouseX, int mouseY) {
        if (!isHovered(mouseX, mouseY)) {
            return null;
        }

        // Return hovered node if any
        return hoveredNode;
    }

    /**
     * Handles scroll events.
     */
    public void handleScroll(double amount) {
        scrollOffset -= (int) (amount * ITEM_HEIGHT);
        scrollOffset = ContextMenuRenderer.clamp(scrollOffset, 0, maxScroll);
    }

    /**
     * Renders the submenu.
     */
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        // Render background with full border (overlaps with main menu border by 1px)
        ContextMenuRenderer.renderMenuBackground(context, submenuX, submenuY, MENU_WIDTH, submenuHeight, true);

        // Enable scissor for scrolling to clip items outside the visible content area
        int clipLeft = submenuX + 1;
        int clipTop = submenuY + PADDING;
        int clipRight = submenuX + MENU_WIDTH - 1;
        int clipBottom = submenuY + submenuHeight - PADDING;

        // Simple scissor implementation (disable when heavily zoomed out to avoid clipping artifacts)
        boolean useScissor = scale >= 0.75f;
        if (useScissor) {
            enableScissor(context, clipLeft, clipTop, clipRight - clipLeft, clipBottom - clipTop);
        }

        // Render items
        int itemY = submenuY + PADDING - scrollOffset;
        int visibleTop = submenuY + PADDING;
        int visibleBottom = submenuY + submenuHeight - PADDING;

        for (SubmenuItem item : items) {
            // Only render items that are fully within the visible bounds
            boolean isVisible = (itemY >= visibleTop) && (itemY + ITEM_HEIGHT <= visibleBottom);

            if (isVisible) {
                if (item.isGroupHeader) {
                    // Render group header
                    ContextMenuRenderer.renderGroupHeader(
                        context, textRenderer,
                        submenuX, itemY, MENU_WIDTH, ITEM_HEIGHT,
                        item.groupName
                    );
                } else if (item.nodeType != null) {
                    // Render node item
                    boolean hovered = (item.nodeType == hoveredNode);
                    int color = item.nodeType.getCategory().getColor();
                    ContextMenuRenderer.renderNodeItem(
                        context, textRenderer,
                        submenuX, itemY, MENU_WIDTH, ITEM_HEIGHT,
                        item.nodeType.getDisplayName(), color,
                        hovered, item.indented
                    );
                }
            }
            itemY += ITEM_HEIGHT;
        }

        // Disable scissor
        if (useScissor) {
            disableScissor(context);
        }

        // Render scrollbar if needed
        if (maxScroll > 0) {
            renderScrollbar(context);
        }
    }

    /**
     * Builds the list of submenu items from sidebar data.
     */
    private void buildItems(Sidebar sidebar) {
        List<Sidebar.NodeGroup> groups = sidebar.getGroupedNodesForCategory(category);

        if (groups != null && !groups.isEmpty()) {
            // Grouped category (SENSORS, PARAMETERS)
            for (Sidebar.NodeGroup group : groups) {
                if (group.isEmpty()) {
                    continue;
                }

                // Add group header
                items.add(new SubmenuItem(group.getTitle()));

                // Add nodes in group
                for (NodeType nodeType : group.getNodes()) {
                    items.add(new SubmenuItem(nodeType, true));
                }
            }
        } else {
            // Non-grouped category
            List<NodeType> nodes = sidebar.getNodesForCategory(category);
            for (NodeType nodeType : nodes) {
                items.add(new SubmenuItem(nodeType, false));
            }
        }
    }

    /**
     * Renders a scrollbar indicator.
     */
    private void renderScrollbar(DrawContext context) {
        int scrollbarHeight = submenuHeight - (PADDING * 2);
        int scrollbarX = submenuX + MENU_WIDTH - 8;
        int scrollbarY = submenuY + PADDING;

        // Calculate thumb position and size
        int totalContentHeight = items.size() * ITEM_HEIGHT;
        int visibleRatio = Math.min(1, submenuHeight * 100 / totalContentHeight);
        int thumbHeight = Math.max(20, scrollbarHeight * visibleRatio / 100);
        int scrollRange = scrollbarHeight - thumbHeight;
        int thumbY = scrollbarY + (scrollRange * scrollOffset / Math.max(1, maxScroll));

        // Render scrollbar track
        context.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarHeight,
            UITheme.CONTEXT_MENU_ICON_BG);

        // Render scrollbar thumb
        context.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight,
            UITheme.CONTEXT_MENU_SEPARATOR);
    }

    /**
     * Simple scissor enable (clips rendering to a rectangle).
     */
    private void enableScissor(DrawContext context, int x, int y, int width, int height) {
        // Use Minecraft's built-in scissor if available
        try {
            int screenX = (int) Math.floor(anchorX + (x - anchorX) * scale);
            int screenY = (int) Math.floor(anchorY + (y - anchorY) * scale);
            // Add small padding to account for rounding and text shadows
            int screenW = Math.max(1, (int) Math.ceil(width * scale) + 2);
            int screenH = Math.max(1, (int) Math.ceil(height * scale) + 2);
            context.enableScissor(screenX, screenY, screenX + screenW, screenY + screenH);
        } catch (Exception e) {
            // Fallback: no scissor
        }
    }

    /**
     * Disables scissor.
     */
    private void disableScissor(DrawContext context) {
        try {
            context.disableScissor();
        } catch (Exception e) {
            // Fallback: no scissor
        }
    }

    public void setScale(float scale, int anchorX, int anchorY) {
        this.scale = Math.max(0.05f, scale);
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    /**
     * Represents an item in the submenu (either a group header or a node).
     */
    private static class SubmenuItem {
        final boolean isGroupHeader;
        final String groupName;
        final NodeType nodeType;
        final boolean indented;

        // Group header constructor
        SubmenuItem(String groupName) {
            this.isGroupHeader = true;
            this.groupName = groupName;
            this.nodeType = null;
            this.indented = false;
        }

        // Node item constructor
        SubmenuItem(NodeType nodeType, boolean indented) {
            this.isGroupHeader = false;
            this.groupName = null;
            this.nodeType = nodeType;
            this.indented = indented;
        }
    }
}
