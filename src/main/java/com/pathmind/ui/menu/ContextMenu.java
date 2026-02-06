package com.pathmind.ui.menu;

import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.MatrixStackBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Main context menu that displays node categories.
 * Hovering over a category shows a submenu with nodes in that category.
 */
public class ContextMenu {

    private static final int MENU_WIDTH = UITheme.CONTEXT_MENU_WIDTH;
    private static final int ITEM_HEIGHT = UITheme.CONTEXT_MENU_ITEM_HEIGHT;
    private static final int PADDING = UITheme.CONTEXT_MENU_PADDING;

    private final PopupAnimationHandler popupAnimation;
    private final List<NodeCategory> categories;
    private final Sidebar sidebar;

    private int menuX;
    private int menuY;
    private int baseMenuX;
    private int baseMenuY;
    private int screenWidth;
    private int screenHeight;
    private boolean isOpen;
    private float scale;

    private NodeCategory hoveredCategory;
    private ContextMenuSubmenu activeSubmenu;

    public ContextMenu(Sidebar sidebar) {
        this.sidebar = sidebar;
        this.categories = new ArrayList<>();
        this.popupAnimation = new PopupAnimationHandler();
        this.isOpen = false;
        this.hoveredCategory = null;
        this.activeSubmenu = null;
        this.scale = 1.0f;
        this.baseMenuX = 0;
        this.baseMenuY = 0;

        // Initialize with all categories that have nodes
        for (NodeCategory category : NodeCategory.values()) {
            if (sidebar.hasNodesInCategory(category)) {
                categories.add(category);
            }
        }
    }

    /**
     * Opens the context menu at the specified screen position.
     */
    public void showAt(int x, int y, int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        this.baseMenuX = x;
        this.baseMenuY = y;
        menuX = baseMenuX;
        menuY = baseMenuY;

        this.isOpen = true;
        this.popupAnimation.show();
    }

    /**
     * Closes the context menu.
     */
    public void close() {
        this.isOpen = false;
        this.hoveredCategory = null;
        this.activeSubmenu = null;
        this.popupAnimation.hide();
    }

    /**
     * Returns true if the menu is open.
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Updates the scale used to render and interact with the menu.
     */
    public void setScale(float scale) {
        this.scale = Math.max(0.05f, scale);
        menuX = baseMenuX;
        menuY = baseMenuY;
    }

    /**
     * Updates the anchor screen position (typically derived from world position).
     */
    public void setAnchorScreen(int x, int y) {
        this.baseMenuX = x;
        this.baseMenuY = y;
        this.menuX = x;
        this.menuY = y;
    }

    /**
     * Returns true if the mouse is over the menu or its active submenu.
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!isOpen) {
            return false;
        }

        int transformedMouseX = toMenuSpaceX(mouseX);
        int transformedMouseY = toMenuSpaceY(mouseY);
        int menuHeight = PADDING * 2 + (categories.size() * ITEM_HEIGHT);
        if (ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, menuY, MENU_WIDTH, menuHeight)) {
            return true;
        }

        return activeSubmenu != null && activeSubmenu.isHovered(transformedMouseX, transformedMouseY);
    }

    /**
     * Updates hover state based on mouse position.
     */
    public void updateHover(int mouseX, int mouseY) {
        if (!isOpen) {
            hoveredCategory = null;
            activeSubmenu = null;
            return;
        }

        int transformedMouseX = toMenuSpaceX(mouseX);
        int transformedMouseY = toMenuSpaceY(mouseY);
        int menuHeight = PADDING * 2 + (categories.size() * ITEM_HEIGHT);

        if (activeSubmenu != null) {
            positionActiveSubmenu();
        }

        // Check if mouse is in menu bounds
        if (!ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, menuY, MENU_WIDTH, menuHeight)) {
            // Check if mouse is in submenu
            if (activeSubmenu != null && activeSubmenu.isHovered(transformedMouseX, transformedMouseY)) {
                // Keep submenu open and update its hover state
                activeSubmenu.updateHover(transformedMouseX, transformedMouseY);
                return;
            }

            close();
            return;
        }

        // Find hovered category
        int itemY = menuY + PADDING;
        NodeCategory newHovered = null;

        for (NodeCategory category : categories) {
            if (ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, itemY, MENU_WIDTH, ITEM_HEIGHT)) {
                newHovered = category;
                break;
            }
            itemY += ITEM_HEIGHT;
        }

        if (newHovered != hoveredCategory) {
            hoveredCategory = newHovered;

            // Create submenu for hovered category
            if (hoveredCategory != null) {
                activeSubmenu = new ContextMenuSubmenu(sidebar, hoveredCategory);
                positionActiveSubmenu();
            } else {
                activeSubmenu = null;
            }
        }

        if (activeSubmenu != null) {
            activeSubmenu.updateHover(transformedMouseX, transformedMouseY);
        }
    }

    /**
     * Handles a click event. Returns the selected NodeType, or null if nothing was clicked.
     */
    public NodeType handleClick(int mouseX, int mouseY) {
        if (!isOpen) {
            return null;
        }

        int transformedMouseX = toMenuSpaceX(mouseX);
        int transformedMouseY = toMenuSpaceY(mouseY);

        // Check if submenu handled the click
        if (activeSubmenu != null) {
            NodeType clicked = activeSubmenu.handleClick(transformedMouseX, transformedMouseY);
            if (clicked != null) {
                return clicked;
            }
        }

        int menuHeight = PADDING * 2 + (categories.size() * ITEM_HEIGHT);

        // Check if click is in menu bounds
        if (!ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, menuY, MENU_WIDTH, menuHeight)) {
            // Click outside - close menu
            close();
            return null;
        }

        // Categories themselves are not clickable - only nodes in submenus are
        return null;
    }

    /**
     * Handles scroll events for the active submenu.
     */
    public void handleScroll(double amount) {
        if (activeSubmenu != null) {
            activeSubmenu.handleScroll(amount);
        }
    }

    /**
     * Renders the context menu.
     */
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!isOpen) {
            return;
        }

        popupAnimation.tick();
        if (!popupAnimation.isVisible()) {
            return;
        }

        int menuHeight = PADDING * 2 + (categories.size() * ITEM_HEIGHT);

        var matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        // Scale around the menu's top-left anchor.
        MatrixStackBridge.translate(matrices, menuX, menuY);
        MatrixStackBridge.scale(matrices, scale, scale);
        MatrixStackBridge.translate(matrices, -menuX, -menuY);

        // Render menu background
        ContextMenuRenderer.renderMenuBackground(context, menuX, menuY, MENU_WIDTH, menuHeight);

        // Render category items
        int itemY = menuY + PADDING;
        for (NodeCategory category : categories) {
            boolean hovered = (category == hoveredCategory);
            ContextMenuRenderer.renderCategoryItem(
                context, textRenderer,
                menuX, itemY, MENU_WIDTH, ITEM_HEIGHT,
                category, hovered
            );
            itemY += ITEM_HEIGHT;
        }

        // Render active submenu
        if (activeSubmenu != null) {
            positionActiveSubmenu();
            activeSubmenu.setScale(scale, menuX, menuY);
            activeSubmenu.render(context, textRenderer, mouseX, mouseY);
        }

        MatrixStackBridge.pop(matrices);
    }

    private void positionActiveSubmenu() {
        if (activeSubmenu == null) {
            return;
        }

        int submenuWidth = MENU_WIDTH;
        int submenuX = menuX + MENU_WIDTH - 1; // Overlap by 1px to merge borders
        int submenuY = menuY; // Align top with main menu top
        boolean positionedLeft = false;

        int submenuScreenLeft = toScreenX(submenuX);
        int submenuScreenRight = submenuScreenLeft + Math.round(submenuWidth * scale);

        // If submenu would go off the right edge, flip it to the left
        if (submenuScreenRight > screenWidth - 10) {
            submenuX = menuX - submenuWidth + 1; // Overlap by 1px on left side
            positionedLeft = true;
        }

        activeSubmenu.setPosition(submenuX, submenuY, screenWidth, screenHeight, positionedLeft, menuX, menuY, scale);
    }

    private int toMenuSpaceX(int mouseX) {
        if (scale == 0.0f) {
            return mouseX;
        }
        return Math.round(menuX + (mouseX - menuX) / scale);
    }

    private int toMenuSpaceY(int mouseY) {
        if (scale == 0.0f) {
            return mouseY;
        }
        return Math.round(menuY + (mouseY - menuY) / scale);
    }

    private int toScreenX(int x) {
        return Math.round(menuX + (x - menuX) * scale);
    }

}
