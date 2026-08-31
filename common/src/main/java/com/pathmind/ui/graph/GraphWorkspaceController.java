package com.pathmind.ui.graph;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.validation.GraphValidator;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

final class GraphWorkspaceController {
    interface Host {
        List<Node> nodes();
        List<NodeConnection> connections();
        List<NodeGraphData.RoutineDefinitionData> routineRegistry();
        List<NodeGraphData.RoutineDefinitionData> validationRoutines();
        String activeRoutineWorkspaceId();
        void cancelDeferredStickySave();
        void commitPendingStickyEdit();
        boolean applyLoadedData(NodeGraphData data);
        NodeGraphData buildGraphDataSnapshot();
        void invalidateRenderCaches();
        void cacheSessionViewportState();
        void restoreSessionViewportState();
    }

    private final Host host;
    private String activePreset = PresetManager.getActivePreset();
    private BooleanSupplier workspaceSaveHandler;
    private boolean workspaceDirty = false;
    /** Prevent edit finalization performed by a save handler from re-entering save(). */
    private boolean saving = false;
    private boolean validationDirty = true;
    private GraphValidationResult cachedValidationResult = GraphValidationResult.empty();

    GraphWorkspaceController(Host host) {
        this.host = host;
    }

    boolean save() {
        if (saving) {
            return false;
        }
        saving = true;
        try {
            host.cancelDeferredStickySave();
            host.commitPendingStickyEdit();
            boolean saved = workspaceSaveHandler != null
                ? workspaceSaveHandler.getAsBoolean()
                : NodeGraphPersistence.saveNodeGraphForPreset(
                    activePreset, host.nodes(), host.connections(), host.routineRegistry());
            if (saved) {
                workspaceDirty = false;
                invalidateTemplatePreviewCachesForPreset(activePreset);
            }
            return saved;
        } finally {
            saving = false;
        }
    }

    void setWorkspaceSaveHandler(BooleanSupplier workspaceSaveHandler) {
        this.workspaceSaveHandler = workspaceSaveHandler;
    }

    boolean load() {
        NodeGraphData data = NodeGraphPersistence.loadNodeGraphForPreset(activePreset);
        if (data != null) {
            boolean applied = host.applyLoadedData(data);
            if (applied) {
                workspaceDirty = false;
                invalidateAllTemplatePreviewCaches();
            }
            return applied;
        }
        return false;
    }

    boolean importFromPath(Path savePath) {
        NodeGraphData data = NodeGraphPersistence.loadNodeGraphFromPath(savePath);
        if (data != null) {
            boolean applied = host.applyLoadedData(data);
            if (applied) {
                markWorkspaceDirty();
            }
            return applied;
        }
        return false;
    }

    boolean exportToPath(Path savePath) {
        boolean saved = NodeGraphPersistence.saveNodeGraphToPath(
            host.nodes(), host.connections(), savePath);
        if (saved) {
            workspaceDirty = false;
        }
        return saved;
    }

    void markWorkspaceDirty() {
        workspaceDirty = true;
        invalidateValidation();
        host.invalidateRenderCaches();
        save();
    }

    void markWorkspaceClean() {
        workspaceDirty = false;
        invalidateValidation();
    }

    boolean isWorkspaceDirty() {
        return workspaceDirty;
    }

    void setWorkspaceDirty(boolean dirty) {
        workspaceDirty = dirty;
    }

    void notifyNodeParametersChanged(Node node) {
        if (node == null) {
            return;
        }
        if (node.getType() == NodeType.TEMPLATE) {
            NodeParameter presetParam = node.getParameter("Preset");
            String presetName = presetParam != null ? presetParam.getStringValue() : "";
            String normalizedPreset = presetName == null ? "" : presetName.trim();
            if (normalizedPreset.isEmpty()) {
                node.setTemplateGraphData(null);
            } else {
                NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphForPreset(normalizedPreset);
                NodeGraphData.CustomNodeDefinition definition = loaded != null
                    ? NodeGraphPersistence.resolveCustomNodeDefinition(normalizedPreset, loaded)
                    : null;
                node.setTemplateGraphData(loaded);
                node.setTemplateName(definition != null ? definition.getName() : normalizedPreset);
                node.setTemplateVersion(
                    definition != null && definition.getVersion() != null
                        ? definition.getVersion()
                        : 0);
            }
        }
        markWorkspaceDirty();
    }

    GraphValidationResult getValidationResult(
        boolean baritoneAvailable, boolean uiUtilsAvailable
    ) {
        if (validationDirty) {
            cachedValidationResult = GraphValidator.validate(
                host.nodes(), host.connections(), activePreset, baritoneAvailable,
                uiUtilsAvailable, host.validationRoutines(), host.activeRoutineWorkspaceId());
            validationDirty = false;
        }
        return cachedValidationResult;
    }

    void invalidateValidation() {
        validationDirty = true;
    }

    private void invalidateTemplatePreviewCachesForPreset(String presetName) {
        if (host.nodes() == null || host.nodes().isEmpty()) {
            return;
        }
        String normalizedPreset = presetName == null ? "" : presetName.trim();
        for (Node candidate : host.nodes()) {
            if (candidate == null || candidate.getType() != NodeType.TEMPLATE) {
                continue;
            }
            NodeParameter presetParam = candidate.getParameter("Preset");
            String selected = presetParam != null ? presetParam.getStringValue() : null;
            String normalizedSelected = selected == null ? "" : selected.trim();
            boolean usesActivePreset = normalizedSelected.isEmpty();
            boolean matchesPreset =
                !normalizedPreset.isEmpty()
                    && normalizedSelected.equalsIgnoreCase(normalizedPreset);
            if (usesActivePreset || matchesPreset) {
                candidate.setTemplateGraphData(null);
            }
        }
    }

    private void invalidateAllTemplatePreviewCaches() {
        if (host.nodes() == null || host.nodes().isEmpty()) {
            return;
        }
        for (Node candidate : host.nodes()) {
            if (candidate != null && candidate.getType() == NodeType.TEMPLATE) {
                candidate.setTemplateGraphData(null);
            }
        }
    }

    boolean hasSavedGraph() {
        return NodeGraphPersistence.hasSavedNodeGraph(activePreset);
    }

    NodeGraphData exportGraphDataSnapshot() {
        NodeGraphData snapshot = host.buildGraphDataSnapshot();
        snapshot.setRoutines(new ArrayList<>(host.routineRegistry()));
        return snapshot;
    }

    boolean applyGraphDataSnapshot(NodeGraphData data, boolean markDirty) {
        if (data == null) {
            return false;
        }
        boolean applied = host.applyLoadedData(data);
        if (applied) {
            if (markDirty) {
                workspaceDirty = true;
            } else {
                workspaceDirty = false;
            }
            invalidateValidation();
        }
        return applied;
    }

    void setActivePreset(String presetName) {
        String previousPreset = this.activePreset;
        if (Objects.equals(previousPreset, presetName)) {
            host.restoreSessionViewportState();
            return;
        }
        host.cacheSessionViewportState();
        this.activePreset = presetName;
        invalidateTemplatePreviewCachesForPreset(previousPreset);
        invalidateTemplatePreviewCachesForPreset(presetName);
        invalidateValidation();
        host.restoreSessionViewportState();
    }

    String getActivePreset() {
        return activePreset;
    }
}
