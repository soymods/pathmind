package com.pathmind.screen;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.ui.graph.NodeGraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Owns preset identity and the lifecycle for switching, renaming, deleting, and importing presets. */
final class PathmindPresetWorkspaceController {
    interface Host {
        NodeGraph nodeGraph();
        Settings settings();
        int screenWidth();
        int screenHeight();
        int sidebarWidth();
        int titleBarHeight();
        void stopInlinePresetRename(boolean commit);
        void refreshPresetTabs();
        void movePresetTabToEnd(String presetName);
        void queueAnimatedPresetDeletion(String presetName);
        void persistActiveWorkspaceToTabs();
        void syncAllTemplateTabsIntoParents();
        void restoreRootWorkspaceIfNeeded();
        boolean saveRootPresetWorkspace();
        void dismissParameterOverlay();
        void clearWorkspaceDrag();
        boolean isImportExportPopupVisible();
        void closeImportExportPopup();
        boolean isCreatePresetPopupVisible();
        void closeCreatePresetPopup();
        boolean isRenamePresetPopupVisible();
        void closeRenamePresetPopup();
        void hideClearPopup();
        void closeSettingsPopup();
        void closePresetDropdown();
        void clearImportExportStatus();
        void resetWorkspaceTabsFromCurrentGraph();
        void refreshMissingBaritonePopup();
        void refreshMissingUiUtilsPopup();
        void updateImportExportPathFromPreset();
    }

    private final Host host;
    private List<String> availablePresets = new ArrayList<>();
    private String activePresetName = "";

    PathmindPresetWorkspaceController(Host host) {
        this.host = host;
    }

    List<String> availablePresets() {
        return availablePresets;
    }

    String activePresetName() {
        return activePresetName;
    }

    void refreshAvailablePresets() {
        host.stopInlinePresetRename(false);
        availablePresets = new ArrayList<>(PresetManager.getAvailablePresets());
        activePresetName = PresetManager.getActivePreset();
        host.refreshPresetTabs();
    }

    boolean isPresetDeleteDisabled(String presetName) {
        if (presetName == null) {
            return true;
        }
        return presetName.equalsIgnoreCase(PresetManager.getDefaultPresetName());
    }

    boolean isPresetRenameDisabled(String presetName) {
        return isPresetDeleteDisabled(presetName);
    }

    void switchPreset(String presetName) {
        host.stopInlinePresetRename(false);
        NodeGraph nodeGraph = host.nodeGraph();
        nodeGraph.stopEventNameEditing(true);
        nodeGraph.stopParameterEditing(true);
        host.persistActiveWorkspaceToTabs();
        host.syncAllTemplateTabsIntoParents();
        host.restoreRootWorkspaceIfNeeded();
        host.saveRootPresetWorkspace();
        PresetManager.setActivePreset(presetName);
        refreshAvailablePresets();
        nodeGraph.setActivePreset(activePresetName);
        host.dismissParameterOverlay();
        host.clearWorkspaceDrag();
        if (host.isImportExportPopupVisible()) {
            host.closeImportExportPopup();
        }
        if (host.isCreatePresetPopupVisible()) {
            host.closeCreatePresetPopup();
        }
        if (host.isRenamePresetPopupVisible()) {
            host.closeRenamePresetPopup();
        }
        host.hideClearPopup();
        host.closeSettingsPopup();
        host.closePresetDropdown();
        host.clearImportExportStatus();

        loadActivePresetGraph();
        host.resetWorkspaceTabsFromCurrentGraph();
        host.refreshMissingBaritonePopup();
        host.refreshMissingUiUtilsPopup();
        nodeGraph.restoreSessionViewportState();
        host.updateImportExportPathFromPreset();
    }

    boolean renamePreset(String currentName, String desiredName) {
        if (currentName == null || currentName.trim().isEmpty()) {
            return false;
        }
        if (desiredName == null || desiredName.trim().isEmpty()) {
            return false;
        }

        boolean renamingActive = currentName.equalsIgnoreCase(activePresetName);
        if (renamingActive) {
            host.saveRootPresetWorkspace();
        }

        Optional<String> renamedPreset = PresetManager.renamePreset(currentName, desiredName);
        if (renamedPreset.isEmpty()) {
            return false;
        }
        String renamedKey = renamedPreset.get();
        Settings settings = host.settings();
        if (settings != null && settings.presetGroupColors != null && settings.presetGroupColors.containsKey(currentName)) {
            String groupKey = settings.presetGroupColors.remove(currentName);
            settings.presetGroupColors.put(renamedKey, groupKey);
            SettingsManager.save(settings);
        }
        refreshAvailablePresets();
        host.nodeGraph().setActivePreset(activePresetName);
        host.closePresetDropdown();
        if (renamingActive) {
            host.updateImportExportPathFromPreset();
        }
        return true;
    }

    void queuePresetDeletion(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (isPresetDeleteDisabled(presetName)) {
            return;
        }
        host.queueAnimatedPresetDeletion(presetName);
    }

    void deletePresetImmediately(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (isPresetDeleteDisabled(presetName)) {
            return;
        }
        boolean deletingActive = presetName.equals(activePresetName);
        String defaultPreset = PresetManager.getDefaultPresetName();
        String fallbackPreset = availablePresets.stream()
            .filter(name -> !name.equalsIgnoreCase(presetName))
            .findFirst()
            .orElse(defaultPreset);

        if (!PresetManager.deletePreset(presetName)) {
            return;
        }
        Settings settings = host.settings();
        if (settings != null && settings.presetGroupColors != null) {
            settings.presetGroupColors.remove(presetName);
            SettingsManager.save(settings);
        }

        host.closePresetDropdown();
        host.closeCreatePresetPopup();
        host.closeRenamePresetPopup();

        if (deletingActive) {
            PresetManager.setActivePreset(fallbackPreset);
        }

        refreshAvailablePresets();
        host.nodeGraph().setActivePreset(activePresetName);

        if (deletingActive) {
            host.dismissParameterOverlay();
            host.clearWorkspaceDrag();
            host.hideClearPopup();
            host.clearImportExportStatus();
            loadActivePresetGraph();
            host.refreshMissingBaritonePopup();
            host.refreshMissingUiUtilsPopup();
            host.nodeGraph().restoreSessionViewportState();
            host.updateImportExportPathFromPreset();
        }
    }

    void applyImportedPreset(String presetName, NodeGraphData importedData) {
        if (presetName == null || presetName.isBlank()) {
            return;
        }
        PresetManager.setActivePreset(presetName);
        refreshAvailablePresets();
        host.movePresetTabToEnd(presetName);
        NodeGraph nodeGraph = host.nodeGraph();
        nodeGraph.setActivePreset(activePresetName);
        host.dismissParameterOverlay();
        host.clearWorkspaceDrag();
        host.hideClearPopup();
        host.closeSettingsPopup();
        host.closePresetDropdown();

        if (!nodeGraph.applyGraphDataSnapshot(importedData, false)) {
            initializeGraph();
        }
        host.resetWorkspaceTabsFromCurrentGraph();
        host.refreshMissingBaritonePopup();
        host.refreshMissingUiUtilsPopup();
        nodeGraph.restoreSessionViewportState();
        host.updateImportExportPathFromPreset();
    }

    private void loadActivePresetGraph() {
        if (!host.nodeGraph().load()) {
            initializeGraph();
        }
    }

    private void initializeGraph() {
        host.nodeGraph().initializeWithScreenDimensions(
            host.screenWidth(),
            host.screenHeight(),
            host.sidebarWidth(),
            host.titleBarHeight()
        );
    }
}
