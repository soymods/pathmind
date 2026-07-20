package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.nodes.StartScreenTarget;
import com.pathmind.ui.control.UiHitTest;
import com.pathmind.ui.menu.ContextMenuRenderer;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.MatrixStackBridge;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class StartModeDropdownController {

    interface Host {
        float getZoomScale();
        int worldToScreenX(int worldX);
        int worldToScreenY(int worldY);
        int getScaledScreenWidth();
        int getScaledScreenHeight();
        int getSelectedNodeAccentColor();
        Node findStartModeButtonAt(int mouseX, int mouseY);
        int getStartModeButtonWorldX(Node node);
        int getStartModeButtonWorldY(Node node);
        void markWorkspaceDirty();
        void closeContextMenu();
        void closeNodeContextMenu();
    }

    private static final int START_MODE_DROPDOWN_WIDTH = 124;
    private static final int START_MODE_DROPDOWN_ROW_HEIGHT = 18;
    private static final int START_MODE_DROPDOWN_PADDING = 2;
    private static final int START_MODE_DROPDOWN_SCREEN_MARGIN = 10;

    private final Host host;
    private Node startModeDropdownNode = null;
    private int startModeDropdownWorldX = 0;
    private int startModeDropdownWorldY = 0;

    StartModeDropdownController(Host host) {
        this.host = host;
    }

    boolean isOpen() {
        return startModeDropdownNode != null;
    }

    boolean handleClick(int mouseX, int mouseY) {
        if (startModeDropdownNode != null) {
            StartScreenTarget selectedTarget = getStartScreenTargetDropdownOptionAt(mouseX, mouseY);
            if (selectedTarget != null) {
                startModeDropdownNode.setStartLaunchMode(StartLaunchMode.SCREEN_OPENED);
                startModeDropdownNode.setStartScreenTarget(selectedTarget);
                host.markWorkspaceDirty();
                close();
                return true;
            }
            StartLaunchMode selectedMode = getStartModeDropdownOptionAt(mouseX, mouseY);
            if (selectedMode != null) {
                startModeDropdownNode.setStartLaunchMode(selectedMode);
                host.markWorkspaceDirty();
                if (selectedMode == StartLaunchMode.SCREEN_OPENED) {
                    return true;
                }
                close();
                return true;
            }
            close();
            return true;
        }

        Node node = host.findStartModeButtonAt(mouseX, mouseY);
        if (node == null) {
            return false;
        }
        startModeDropdownNode = node;
        startModeDropdownWorldX = host.getStartModeButtonWorldX(node) + 10;
        startModeDropdownWorldY = host.getStartModeButtonWorldY(node) + 8;
        host.closeContextMenu();
        host.closeNodeContextMenu();
        return true;
    }

    void close() {
        startModeDropdownNode = null;
    }

    void render(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        if (startModeDropdownNode == null) {
            return;
        }
        StartDropdownLayout layout = getStartDropdownLayout();
        int x = layout.x();
        int y = layout.y();
        int width = START_MODE_DROPDOWN_WIDTH;
        int rowHeight = START_MODE_DROPDOWN_ROW_HEIGHT;
        int height = getStartModeDropdownHeight();

        var matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.translate(matrices, x, y);
        MatrixStackBridge.scale(matrices, layout.scale(), layout.scale());
        MatrixStackBridge.translate(matrices, -x, -y);

        int transformedMouseX = toStartDropdownSpaceX(mouseX, x, layout.scale());
        int transformedMouseY = toStartDropdownSpaceY(mouseY, y, layout.scale());

        ContextMenuRenderer.renderMenuBackground(context, x, y, width, height);
        StartLaunchMode currentMode = startModeDropdownNode.getStartLaunchMode();
        StartLaunchMode[] modes = StartLaunchMode.values();
        for (int i = 0; i < modes.length; i++) {
            StartLaunchMode mode = modes[i];
            int rowY = y + 2 + i * rowHeight;
            boolean hovered = transformedMouseX >= x + 2 && transformedMouseX <= x + width - 2
                && transformedMouseY >= rowY && transformedMouseY <= rowY + rowHeight;
            if (hovered) {
                context.fill(x + 2, rowY, x + width - 2, rowY + rowHeight, UITheme.CONTEXT_MENU_ITEM_HOVER);
            }
            int textColor = mode == currentMode ? host.getSelectedNodeAccentColor() : UITheme.TEXT_PRIMARY;
            if (mode == currentMode) {
                renderStartModeSubmenuArrow(context, x + 8, rowY + 6, textColor);
            }
            context.drawString(textRenderer, Component.literal(mode.getDisplayName()), x + 20, rowY + 5, textColor);
            if (mode == StartLaunchMode.SCREEN_OPENED) {
                renderStartModeSubmenuArrow(context, x + width - 12, rowY + 6, UITheme.CONTEXT_MENU_TEXT);
            }
        }
        if (shouldRenderStartScreenTargetSubmenu(mouseX, mouseY)) {
            renderStartScreenTargetSubmenu(context, textRenderer, transformedMouseX, transformedMouseY, layout);
        }

        MatrixStackBridge.pop(matrices);
    }

    private void renderStartScreenTargetSubmenu(GuiGraphics context, Font textRenderer, int mouseX, int mouseY,
                                                StartDropdownLayout layout) {
        int x = layout.submenuX();
        int y = layout.submenuY();
        int width = START_MODE_DROPDOWN_WIDTH;
        int rowHeight = START_MODE_DROPDOWN_ROW_HEIGHT;
        int height = getStartScreenTargetSubmenuHeight();
        ContextMenuRenderer.renderMenuBackground(context, x, y, width, height);
        StartScreenTarget currentTarget = startModeDropdownNode.getStartScreenTarget();
        StartScreenTarget[] targets = StartScreenTarget.values();
        for (int i = 0; i < targets.length; i++) {
            StartScreenTarget target = targets[i];
            int rowY = y + 2 + i * rowHeight;
            boolean hovered = mouseX >= x + 2 && mouseX <= x + width - 2
                && mouseY >= rowY && mouseY <= rowY + rowHeight;
            if (hovered) {
                context.fill(x + 2, rowY, x + width - 2, rowY + rowHeight, UITheme.CONTEXT_MENU_ITEM_HOVER);
            }
            int textColor = target == currentTarget ? host.getSelectedNodeAccentColor() : UITheme.TEXT_PRIMARY;
            if (target == currentTarget) {
                context.hLine(x + 8, x + 11, rowY + 9, textColor);
                context.hLine(x + 11, x + 15, rowY + 8, textColor);
            }
            context.drawString(textRenderer, Component.literal(target.getDisplayName()), x + 20, rowY + 5, textColor);
        }
    }

    private void renderStartModeSubmenuArrow(GuiGraphics context, int x, int y, int color) {
        context.fill(x, y + 1, x + 3, y + 2, color);
        context.fill(x, y + 2, x + 5, y + 3, color);
        context.fill(x, y + 3, x + 7, y + 4, color);
        context.fill(x, y + 4, x + 5, y + 5, color);
        context.fill(x, y + 5, x + 3, y + 6, color);
    }

    private StartLaunchMode getStartModeDropdownOptionAt(int mouseX, int mouseY) {
        StartDropdownLayout layout = getStartDropdownLayout();
        int x = layout.x();
        int y = layout.y();
        int transformedMouseX = toStartDropdownSpaceX(mouseX, x, layout.scale());
        int transformedMouseY = toStartDropdownSpaceY(mouseY, y, layout.scale());
        int localX = transformedMouseX - x;
        int localY = transformedMouseY - y - START_MODE_DROPDOWN_PADDING;
        if (localX < START_MODE_DROPDOWN_PADDING || localX > START_MODE_DROPDOWN_WIDTH - START_MODE_DROPDOWN_PADDING || localY < 0) {
            return null;
        }
        int index = localY / START_MODE_DROPDOWN_ROW_HEIGHT;
        StartLaunchMode[] modes = StartLaunchMode.values();
        if (index < 0 || index >= modes.length) {
            return null;
        }
        return modes[index];
    }

    private StartScreenTarget getStartScreenTargetDropdownOptionAt(int mouseX, int mouseY) {
        if (startModeDropdownNode == null
            || !shouldRenderStartScreenTargetSubmenu(mouseX, mouseY)) {
            return null;
        }
        StartDropdownLayout layout = getStartDropdownLayout();
        int x = layout.submenuX();
        int y = layout.submenuY();
        int transformedMouseX = toStartDropdownSpaceX(mouseX, layout.x(), layout.scale());
        int transformedMouseY = toStartDropdownSpaceY(mouseY, layout.y(), layout.scale());
        int localX = transformedMouseX - x;
        int localY = transformedMouseY - y - START_MODE_DROPDOWN_PADDING;
        if (localX < START_MODE_DROPDOWN_PADDING || localX > START_MODE_DROPDOWN_WIDTH - START_MODE_DROPDOWN_PADDING || localY < 0) {
            return null;
        }
        int index = localY / START_MODE_DROPDOWN_ROW_HEIGHT;
        StartScreenTarget[] targets = StartScreenTarget.values();
        if (index < 0 || index >= targets.length) {
            return null;
        }
        return targets[index];
    }

    private boolean shouldRenderStartScreenTargetSubmenu(int mouseX, int mouseY) {
        return startModeDropdownNode != null
            && (startModeDropdownNode.getStartLaunchMode() == StartLaunchMode.SCREEN_OPENED
                || isMouseOverStartScreenOpenedModeRow(mouseX, mouseY)
                || isMouseOverStartScreenTargetSubmenu(mouseX, mouseY));
    }

    private boolean isMouseOverStartScreenOpenedModeRow(int mouseX, int mouseY) {
        StartDropdownLayout layout = getStartDropdownLayout();
        int x = layout.x();
        int y = layout.y();
        int transformedMouseX = toStartDropdownSpaceX(mouseX, x, layout.scale());
        int transformedMouseY = toStartDropdownSpaceY(mouseY, y, layout.scale());
        int rowIndex = StartLaunchMode.SCREEN_OPENED.ordinal();
        int rowY = y + START_MODE_DROPDOWN_PADDING + rowIndex * START_MODE_DROPDOWN_ROW_HEIGHT;
        return UiHitTest.contains(transformedMouseX, transformedMouseY,
            x, rowY, START_MODE_DROPDOWN_WIDTH, START_MODE_DROPDOWN_ROW_HEIGHT);
    }

    private boolean isMouseOverStartScreenTargetSubmenu(int mouseX, int mouseY) {
        StartDropdownLayout layout = getStartDropdownLayout();
        int transformedMouseX = toStartDropdownSpaceX(mouseX, layout.x(), layout.scale());
        int transformedMouseY = toStartDropdownSpaceY(mouseY, layout.y(), layout.scale());
        int x = layout.submenuX();
        int y = layout.submenuY();
        return UiHitTest.contains(transformedMouseX, transformedMouseY,
            x, y, START_MODE_DROPDOWN_WIDTH, getStartScreenTargetSubmenuHeight());
    }

    private int getStartScreenTargetSubmenuX() {
        return getStartDropdownLayout().submenuX();
    }

    private int getStartScreenTargetSubmenuY() {
        return getStartDropdownLayout().submenuY();
    }

    private StartDropdownLayout getStartDropdownLayout() {
        float scale = Math.max(0.05f, host.getZoomScale());
        int screenWidth = host.getScaledScreenWidth();
        int screenHeight = host.getScaledScreenHeight();

        int x = host.worldToScreenX(startModeDropdownWorldX);
        int y = host.worldToScreenY(startModeDropdownWorldY);
        x = clampScaledDropdownPosition(x, START_MODE_DROPDOWN_WIDTH, scale, screenWidth);
        y = clampScaledDropdownPosition(y, getStartModeDropdownHeight(), scale, screenHeight);

        int submenuX = x + START_MODE_DROPDOWN_WIDTH - 1;
        int submenuY = y + START_MODE_DROPDOWN_PADDING
            + StartLaunchMode.SCREEN_OPENED.ordinal() * START_MODE_DROPDOWN_ROW_HEIGHT;

        int submenuScreenLeft = toStartDropdownScreenX(submenuX, x, scale);
        int submenuScreenRight = submenuScreenLeft + Math.round(START_MODE_DROPDOWN_WIDTH * scale);
        if (screenWidth > 0 && submenuScreenRight > screenWidth - START_MODE_DROPDOWN_SCREEN_MARGIN) {
            submenuX = x - START_MODE_DROPDOWN_WIDTH + 1;
        }

        int submenuScreenTop = toStartDropdownScreenY(submenuY, y, scale);
        int submenuScreenHeight = Math.round(getStartScreenTargetSubmenuHeight() * scale);
        if (screenHeight > 0) {
            int overflow = submenuScreenTop + submenuScreenHeight - (screenHeight - START_MODE_DROPDOWN_SCREEN_MARGIN);
            if (overflow > 0) {
                submenuY -= Math.round(overflow / scale);
                submenuY = Math.max(y, submenuY);
            }
        }

        return new StartDropdownLayout(x, y, submenuX, submenuY, scale);
    }

    private int getStartModeDropdownHeight() {
        return StartLaunchMode.values().length * START_MODE_DROPDOWN_ROW_HEIGHT
            + START_MODE_DROPDOWN_PADDING * 2;
    }

    private int getStartScreenTargetSubmenuHeight() {
        return StartScreenTarget.values().length * START_MODE_DROPDOWN_ROW_HEIGHT
            + START_MODE_DROPDOWN_PADDING * 2;
    }

    private int clampScaledDropdownPosition(int position, int size, float scale, int screenSize) {
        if (screenSize <= 0) {
            return position;
        }
        int scaledSize = Math.round(size * scale);
        int min = START_MODE_DROPDOWN_SCREEN_MARGIN;
        int max = Math.max(min, screenSize - START_MODE_DROPDOWN_SCREEN_MARGIN - scaledSize);
        return Math.max(min, Math.min(position, max));
    }

    private int toStartDropdownSpaceX(int mouseX, int dropdownX, float scale) {
        return Math.round(dropdownX + (mouseX - dropdownX) / scale);
    }

    private int toStartDropdownSpaceY(int mouseY, int dropdownY, float scale) {
        return Math.round(dropdownY + (mouseY - dropdownY) / scale);
    }

    private int toStartDropdownScreenX(int x, int dropdownX, float scale) {
        return Math.round(dropdownX + (x - dropdownX) * scale);
    }

    private int toStartDropdownScreenY(int y, int dropdownY, float scale) {
        return Math.round(dropdownY + (y - dropdownY) * scale);
    }

    private record StartDropdownLayout(int x, int y, int submenuX, int submenuY, float scale) {
    }
}
