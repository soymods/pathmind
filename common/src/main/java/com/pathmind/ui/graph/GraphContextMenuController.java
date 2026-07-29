package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.ui.menu.ContextMenu;
import com.pathmind.ui.menu.ContextMenuSelection;
import com.pathmind.ui.menu.NodeContextMenu;
import com.pathmind.ui.menu.NodeContextMenuAction;
import com.pathmind.ui.sidebar.Sidebar;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Owns canvas and node context-menu state, anchoring, rendering, and actions. */
final class GraphContextMenuController {

    interface Host {
        float zoomScale();
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        int worldToScreenX(int worldX);
        int worldToScreenY(int worldY);
        boolean isNodeSelected(Node node);
        void selectNode(Node node);
        void copySelectedNodeToClipboard();
        void duplicateSelectedNode();
        void pasteClipboardNode();
        void deleteSelectedNode();
    }

    private final Host host;
    private ContextMenu contextMenu;
    private NodeContextMenu nodeContextMenu;
    private int contextMenuWorldX;
    private int contextMenuWorldY;
    private int nodeContextMenuWorldX;
    private int nodeContextMenuWorldY;
    private Node nodeContextMenuTarget;

    GraphContextMenuController(Host host) {
        this.host = host;
    }

    void showContextMenu(int screenX, int screenY, Sidebar sidebar, int screenWidth, int screenHeight) {
        closeNodeContextMenu();
        if (contextMenu == null) {
            contextMenu = new ContextMenu(sidebar);
        }
        contextMenu.setScale(host.zoomScale());
        // Store the world coordinates where nodes should be created
        contextMenuWorldX = host.screenToWorldX(screenX);
        contextMenuWorldY = host.screenToWorldY(screenY);
        contextMenu.setAnchorScreen(screenX, screenY);
        contextMenu.showAt(screenX, screenY, screenWidth, screenHeight);
    }

    void showNodeContextMenu(int screenX, int screenY, Node targetNode, int screenWidth, int screenHeight) {
        closeContextMenu();
        if (nodeContextMenu == null) {
            nodeContextMenu = new NodeContextMenu();
        }
        nodeContextMenuTarget = targetNode;
        nodeContextMenuWorldX = host.screenToWorldX(screenX);
        nodeContextMenuWorldY = host.screenToWorldY(screenY);
        nodeContextMenu.setScale(host.zoomScale());
        nodeContextMenu.setAnchorScreen(screenX, screenY);
        nodeContextMenu.showAt(screenX, screenY, screenWidth, screenHeight);
    }

    void closeContextMenu() {
        if (contextMenu != null) {
            contextMenu.close();
        }
    }

    void closeNodeContextMenu() {
        if (nodeContextMenu != null) {
            nodeContextMenu.close();
        }
        nodeContextMenuTarget = null;
    }

    boolean isContextMenuOpen() {
        return contextMenu != null && contextMenu.isOpen();
    }

    boolean isNodeContextMenuOpen() {
        return nodeContextMenu != null && nodeContextMenu.isOpen();
    }

    void updateContextMenuHover(int mouseX, int mouseY) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = host.worldToScreenX(contextMenuWorldX);
            int anchorScreenY = host.worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(host.zoomScale());
            contextMenu.updateHover(mouseX, mouseY);
        }
    }

    void updateNodeContextMenuHover(int mouseX, int mouseY) {
        if (nodeContextMenu != null && nodeContextMenu.isOpen()) {
            int anchorScreenX = host.worldToScreenX(nodeContextMenuWorldX);
            int anchorScreenY = host.worldToScreenY(nodeContextMenuWorldY);
            nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            nodeContextMenu.setScale(host.zoomScale());
            nodeContextMenu.updateHover(mouseX, mouseY);
        }
    }

    ContextMenuSelection handleContextMenuClick(int mouseX, int mouseY) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = host.worldToScreenX(contextMenuWorldX);
            int anchorScreenY = host.worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(host.zoomScale());
            return contextMenu.handleClick(mouseX, mouseY);
        }
        return null;
    }

    boolean handleNodeContextMenuClick(int mouseX, int mouseY) {
        if (nodeContextMenu == null || !nodeContextMenu.isOpen()) {
            return false;
        }
        int anchorScreenX = host.worldToScreenX(nodeContextMenuWorldX);
        int anchorScreenY = host.worldToScreenY(nodeContextMenuWorldY);
        nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
        nodeContextMenu.setScale(host.zoomScale());
        NodeContextMenuAction action = nodeContextMenu.handleClick(mouseX, mouseY);
        if (action == null) {
            closeNodeContextMenu();
            return true;
        }

        if (nodeContextMenuTarget != null && !host.isNodeSelected(nodeContextMenuTarget)) {
            host.selectNode(nodeContextMenuTarget);
        }

        switch (action) {
            case COPY:
                host.copySelectedNodeToClipboard();
                break;
            case DUPLICATE:
                host.duplicateSelectedNode();
                break;
            case PASTE:
                host.pasteClipboardNode();
                break;
            case DELETE:
                host.deleteSelectedNode();
                break;
        }
        closeNodeContextMenu();
        return true;
    }

    void renderContextMenu(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = host.worldToScreenX(contextMenuWorldX);
            int anchorScreenY = host.worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(host.zoomScale());
            contextMenu.render(context, textRenderer, mouseX, mouseY);
        }
    }

    void renderNodeContextMenu(GuiGraphics context, Font textRenderer) {
        if (nodeContextMenu != null && nodeContextMenu.isOpen()) {
            int anchorScreenX = host.worldToScreenX(nodeContextMenuWorldX);
            int anchorScreenY = host.worldToScreenY(nodeContextMenuWorldY);
            nodeContextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            nodeContextMenu.setScale(host.zoomScale());
            nodeContextMenu.render(context, textRenderer);
        }
    }

    boolean handleContextMenuScroll(int mouseX, int mouseY, double amount) {
        if (contextMenu != null && contextMenu.isOpen()) {
            int anchorScreenX = host.worldToScreenX(contextMenuWorldX);
            int anchorScreenY = host.worldToScreenY(contextMenuWorldY);
            contextMenu.setAnchorScreen(anchorScreenX, anchorScreenY);
            contextMenu.setScale(host.zoomScale());
            // Check if mouse is over the menu and handle scroll
            if (contextMenu.isMouseOver(mouseX, mouseY)) {
                contextMenu.handleScroll(amount);
                return true;
            }
        }
        return false;
    }

    int contextMenuWorldX() {
        return contextMenuWorldX;
    }

    int contextMenuWorldY() {
        return contextMenuWorldY;
    }

}
