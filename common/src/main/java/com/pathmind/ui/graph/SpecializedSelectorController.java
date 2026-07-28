package com.pathmind.ui.graph;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.util.DropdownLayoutHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Owns schematic and run-preset selector state and interaction. Rendering reads
 * the exposed immutable state while all graph mutations flow through the host.
 */
final class SpecializedSelectorController {
    private static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 16;

    interface Host {
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        int cameraY();
        int guiScaledHeight();
        float zoomScale();
        int schematicDropdownWidth(Node node);
        int runPresetDropdownWidth(Node node);
        List<String> loadSchematicOptions();
        boolean schematicExists(String name);
        boolean isPresetSelectorNode(Node node);
        String stopTargetParameterKey(Node node);
        void prepareRunPresetOpen(Node node);
        void applySchematicSelection(Node node, String value);
        void applyRunPresetSelection(Node node, String value);
    }

    private final Host host;
    private Node schematicNode;
    private boolean schematicOpen;
    private List<String> schematicOptions = new ArrayList<>();
    private int schematicScrollOffset;
    private int schematicHoverIndex = -1;
    private Node runPresetNode;
    private boolean runPresetOpen;
    private List<String> runPresetOptions = new ArrayList<>();
    private int runPresetScrollOffset;
    private int runPresetHoverIndex = -1;

    SpecializedSelectorController(Host host) {
        this.host = host;
    }

    boolean isSchematicOpen() { return schematicOpen; }
    boolean isSchematicOpenFor(Node node) { return schematicOpen && schematicNode == node; }
    Node getSchematicNode() { return schematicNode; }
    List<String> getSchematicOptions() { return schematicOptions; }
    int getSchematicScrollOffset() { return schematicScrollOffset; }
    void setSchematicScrollOffset(int value) { schematicScrollOffset = value; }
    void setSchematicHoverIndex(int value) { schematicHoverIndex = value; }

    boolean isRunPresetOpen() { return runPresetOpen; }
    boolean isRunPresetOpenFor(Node node) { return runPresetOpen && runPresetNode == node; }
    Node getRunPresetNode() { return runPresetNode; }
    List<String> getRunPresetOptions() { return runPresetOptions; }
    int getRunPresetScrollOffset() { return runPresetScrollOffset; }
    void setRunPresetScrollOffset(int value) { runPresetScrollOffset = value; }
    void setRunPresetHoverIndex(int value) { runPresetHoverIndex = value; }

    boolean handleSchematicClick(Node clickedNode, int screenX, int screenY) {
        if (schematicOpen && schematicNode != null) {
            if (isPointInsideSchematicList(schematicNode, screenX, screenY)) {
                int index = getSchematicIndexAt(schematicNode, screenY);
                if (index >= 0 && index < schematicOptions.size()) {
                    host.applySchematicSelection(schematicNode, schematicOptions.get(index));
                }
                closeSchematic();
                return true;
            }
            if (isPointInsideSchematicField(schematicNode, screenX, screenY)) {
                closeSchematic();
                return true;
            }
            closeSchematic();
        }
        if (clickedNode != null && clickedNode.hasSchematicDropdownField()
            && isPointInsideSchematicField(clickedNode, screenX, screenY)) {
            openSchematic(clickedNode);
            return true;
        }
        return false;
    }

    boolean handleRunPresetClick(Node clickedNode, int screenX, int screenY) {
        if (runPresetOpen && runPresetNode != null) {
            if (isPointInsideRunPresetList(runPresetNode, screenX, screenY)) {
                int index = getRunPresetIndexAt(runPresetNode, screenY);
                if (index >= 0 && index < runPresetOptions.size()) {
                    host.applyRunPresetSelection(runPresetNode, runPresetOptions.get(index));
                }
                closeRunPreset();
                return true;
            }
            if (isPointInsideRunPresetField(runPresetNode, screenX, screenY)) {
                closeRunPreset();
                return true;
            }
            closeRunPreset();
        }
        if (host.isPresetSelectorNode(clickedNode)
            && isPointInsideRunPresetField(clickedNode, screenX, screenY)) {
            host.prepareRunPresetOpen(clickedNode);
            openRunPreset(clickedNode);
            return true;
        }
        return false;
    }

    boolean handleSchematicScroll(double screenX, double screenY, double amount) {
        if (!schematicOpen || schematicNode == null || schematicOptions.isEmpty()
            || !isPointInsideSchematicList(schematicNode, (int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = schematicLayout(schematicNode, schematicOptions.size());
        if (layout.maxScrollOffset <= 0) return true;
        schematicScrollOffset = Mth.clamp(
            schematicScrollOffset + (amount > 0 ? -1 : 1), 0, layout.maxScrollOffset);
        return true;
    }

    boolean handleRunPresetScroll(double screenX, double screenY, double amount) {
        if (!runPresetOpen || runPresetNode == null || runPresetOptions.isEmpty()
            || !isPointInsideRunPresetList(runPresetNode, (int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = runPresetLayout(runPresetNode, runPresetOptions.size());
        if (layout.maxScrollOffset <= 0) return true;
        runPresetScrollOffset = Mth.clamp(
            runPresetScrollOffset + (amount > 0 ? -1 : 1), 0, layout.maxScrollOffset);
        return true;
    }

    void openSchematic(Node node) {
        schematicNode = node;
        schematicOptions = host.loadSchematicOptions();
        String current = "";
        if (node != null) {
            NodeParameter parameter = node.getParameter("Schematic");
            current = parameter != null ? parameter.getStringValue() : "";
        }
        if (current != null && !current.isEmpty()
            && !schematicOptions.contains(current) && host.schematicExists(current)) {
            schematicOptions.add(0, current);
        }
        schematicOpen = true;
        schematicScrollOffset = 0;
        schematicHoverIndex = -1;
    }

    void closeSchematic() {
        schematicOpen = false;
        schematicHoverIndex = -1;
    }

    void openRunPreset(Node node) {
        if (!host.isPresetSelectorNode(node)) return;
        runPresetNode = node;
        runPresetOptions = new ArrayList<>(PresetManager.getAvailablePresets());
        NodeParameter parameter = node.getParameter(host.stopTargetParameterKey(node));
        String current = parameter != null && parameter.getStringValue() != null
            ? parameter.getStringValue().trim() : "";
        if (!current.isEmpty()
            && runPresetOptions.stream().noneMatch(option -> option.equalsIgnoreCase(current))) {
            runPresetOptions.add(0, current);
        }
        runPresetOpen = true;
        runPresetScrollOffset = 0;
        runPresetHoverIndex = -1;
    }

    void closeRunPreset() {
        runPresetOpen = false;
        runPresetHoverIndex = -1;
    }

    void clearSchematicState() {
        if (schematicOpen) return;
        schematicNode = null;
        schematicHoverIndex = -1;
        schematicScrollOffset = 0;
    }

    void clearRunPresetState() {
        if (runPresetOpen) return;
        runPresetNode = null;
        runPresetOptions = new ArrayList<>();
        runPresetHoverIndex = -1;
        runPresetScrollOffset = 0;
    }

    private boolean isPointInsideSchematicField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasSchematicDropdownField()) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        return worldX >= node.getSchematicFieldLeft()
            && worldX <= node.getSchematicFieldLeft() + node.getSchematicFieldWidth()
            && worldY >= node.getSchematicFieldInputTop()
            && worldY <= node.getSchematicFieldInputTop() + node.getSchematicFieldHeight();
    }

    private boolean isPointInsideRunPresetField(Node node, int screenX, int screenY) {
        if (!host.isPresetSelectorNode(node)) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        return worldX >= node.getStopTargetFieldLeft()
            && worldX <= node.getStopTargetFieldLeft() + node.getStopTargetFieldWidth()
            && worldY >= node.getStopTargetFieldInputTop()
            && worldY <= node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight();
    }

    private boolean isPointInsideSchematicList(Node node, int screenX, int screenY) {
        if (node == null || !isSchematicOpenFor(node)) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        int listTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2;
        int height = schematicLayout(node, Math.max(1, schematicOptions.size())).height;
        return worldX >= node.getSchematicFieldLeft()
            && worldX <= node.getSchematicFieldLeft() + host.schematicDropdownWidth(node)
            && worldY >= listTop && worldY <= listTop + height;
    }

    private boolean isPointInsideRunPresetList(Node node, int screenX, int screenY) {
        if (!host.isPresetSelectorNode(node) || !isRunPresetOpenFor(node)) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        int listTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2;
        int height = runPresetLayout(node, Math.max(1, runPresetOptions.size())).height;
        return worldX >= node.getStopTargetFieldLeft()
            && worldX <= node.getStopTargetFieldLeft() + host.runPresetDropdownWidth(node)
            && worldY >= listTop && worldY <= listTop + height;
    }

    private int getSchematicIndexAt(Node node, int screenY) {
        if (node == null || schematicOptions.isEmpty()) return -1;
        int row = (host.screenToWorldY(screenY)
            - node.getSchematicFieldInputTop() - node.getSchematicFieldHeight() - 2) / ROW_HEIGHT;
        DropdownLayoutHelper.Layout layout = schematicLayout(node, schematicOptions.size());
        return row < 0 || row >= layout.visibleCount ? -1 : schematicScrollOffset + row;
    }

    private int getRunPresetIndexAt(Node node, int screenY) {
        if (node == null || runPresetOptions.isEmpty()) return -1;
        int row = (host.screenToWorldY(screenY)
            - node.getStopTargetFieldInputTop() - node.getStopTargetFieldHeight() - 2) / ROW_HEIGHT;
        DropdownLayoutHelper.Layout layout = runPresetLayout(node, runPresetOptions.size());
        return row < 0 || row >= layout.visibleCount ? -1 : runPresetScrollOffset + row;
    }

    private DropdownLayoutHelper.Layout schematicLayout(Node node, int count) {
        int top = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 - host.cameraY();
        int transformedScreenHeight =
            Math.round(host.guiScaledHeight() / Math.max(0.01f, host.zoomScale()));
        return DropdownLayoutHelper.calculate(
            count, ROW_HEIGHT, MAX_ROWS, top, transformedScreenHeight);
    }

    private DropdownLayoutHelper.Layout runPresetLayout(Node node, int count) {
        int top = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 - host.cameraY();
        int transformedScreenHeight =
            Math.round(host.guiScaledHeight() / Math.max(0.01f, host.zoomScale()));
        return DropdownLayoutHelper.calculate(
            count, ROW_HEIGHT, MAX_ROWS, top, transformedScreenHeight);
    }
}
