package com.pathmind.ui.menu;

import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.MatrixStackBridge;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

public class NodeContextMenu {

    private static final int MENU_WIDTH = UITheme.CONTEXT_MENU_WIDTH;
    private static final int ITEM_HEIGHT = UITheme.CONTEXT_MENU_ITEM_HEIGHT;
    private static final int PADDING = UITheme.CONTEXT_MENU_PADDING;
    private static final int TEXT_OFFSET_X = 6;

    private final PopupAnimationHandler popupAnimation;
    private final List<NodeContextMenuAction> actions;

    private int menuX;
    private int menuY;
    private int baseMenuX;
    private int baseMenuY;
    private int screenWidth;
    private int screenHeight;
    private float scale = 1.0f;
    private boolean isOpen = false;
    private int hoveredIndex = -1;

    public NodeContextMenu() {
        this.popupAnimation = new PopupAnimationHandler();
        this.actions = Arrays.asList(
            NodeContextMenuAction.COPY,
            NodeContextMenuAction.DUPLICATE,
            NodeContextMenuAction.PASTE,
            NodeContextMenuAction.DELETE
        );
    }

    public void showAt(int x, int y, int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.baseMenuX = x;
        this.baseMenuY = y;
        this.menuX = x;
        this.menuY = y;
        this.isOpen = true;
        this.popupAnimation.show();
    }

    public void close() {
        isOpen = false;
        hoveredIndex = -1;
        popupAnimation.hide();
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setScale(float scale) {
        this.scale = Math.max(0.05f, scale);
        this.menuX = baseMenuX;
        this.menuY = baseMenuY;
    }

    public void setAnchorScreen(int x, int y) {
        this.baseMenuX = x;
        this.baseMenuY = y;
        this.menuX = x;
        this.menuY = y;
    }

    public void updateHover(int mouseX, int mouseY) {
        if (!isOpen) {
            return;
        }

        int transformedMouseX = toMenuSpaceX(mouseX);
        int transformedMouseY = toMenuSpaceY(mouseY);
        int menuHeight = PADDING * 2 + (actions.size() * ITEM_HEIGHT);

        if (!ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, menuY, MENU_WIDTH, menuHeight)) {
            close();
            return;
        }

        int itemY = menuY + PADDING;
        int newHovered = -1;
        for (int i = 0; i < actions.size(); i++) {
            if (ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, itemY, MENU_WIDTH, ITEM_HEIGHT)) {
                newHovered = i;
                break;
            }
            itemY += ITEM_HEIGHT;
        }
        hoveredIndex = newHovered;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        if (!isOpen) {
            return false;
        }
        int transformedMouseX = toMenuSpaceX(mouseX);
        int transformedMouseY = toMenuSpaceY(mouseY);
        int menuHeight = PADDING * 2 + (actions.size() * ITEM_HEIGHT);
        return ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, menuY, MENU_WIDTH, menuHeight);
    }

    public NodeContextMenuAction handleClick(int mouseX, int mouseY) {
        if (!isOpen) {
            return null;
        }

        int transformedMouseX = toMenuSpaceX(mouseX);
        int transformedMouseY = toMenuSpaceY(mouseY);
        int menuHeight = PADDING * 2 + (actions.size() * ITEM_HEIGHT);

        if (!ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, menuY, MENU_WIDTH, menuHeight)) {
            close();
            return null;
        }

        int itemY = menuY + PADDING;
        for (int i = 0; i < actions.size(); i++) {
            if (ContextMenuRenderer.isPointInRect(transformedMouseX, transformedMouseY, menuX, itemY, MENU_WIDTH, ITEM_HEIGHT)) {
                return actions.get(i);
            }
            itemY += ITEM_HEIGHT;
        }
        return null;
    }

    public void render(DrawContext context, TextRenderer textRenderer) {
        if (!isOpen) {
            return;
        }

        popupAnimation.tick();
        if (!popupAnimation.isVisible()) {
            return;
        }

        int menuHeight = PADDING * 2 + (actions.size() * ITEM_HEIGHT);

        var matrices = context.getMatrices();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translate(matrices, menuX, menuY);
        MatrixStackBridge.scale(matrices, scale, scale);
        MatrixStackBridge.translate(matrices, -menuX, -menuY);

        ContextMenuRenderer.renderMenuBackground(context, menuX, menuY, MENU_WIDTH, menuHeight);

        int itemY = menuY + PADDING;
        for (int i = 0; i < actions.size(); i++) {
            boolean hovered = i == hoveredIndex;
            ContextMenuRenderer.renderMenuItem(context, menuX, itemY, MENU_WIDTH, ITEM_HEIGHT, hovered);
            int textX = menuX + TEXT_OFFSET_X;
            int textY = itemY + (ITEM_HEIGHT - textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(textRenderer, Text.literal(actions.get(i).getLabel()), textX, textY, UITheme.CONTEXT_MENU_TEXT);
            itemY += ITEM_HEIGHT;
        }

        MatrixStackBridge.pop(matrices);
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
}
