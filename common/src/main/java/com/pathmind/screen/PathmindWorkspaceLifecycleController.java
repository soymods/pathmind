package com.pathmind.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeType;
import com.pathmind.routines.RoutineBuilderModel;
import com.pathmind.routines.RoutineLibraryManager;
import com.pathmind.routines.RoutineLifecycle;
import com.pathmind.routines.RoutineValueKind;
import com.pathmind.routines.RoutineWorkspaceSupport;
import com.pathmind.ui.control.PathmindRoutineUi;
import com.pathmind.ui.control.PathmindWorkspaceChrome;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.sidebar.Sidebar;

import net.minecraft.client.gui.GuiGraphics;

/** Owns nested template/routine workspaces and persistence of the root preset graph. */
final class PathmindWorkspaceLifecycleController {
    private static final String ROUTINE_WORKSPACE_PREFIX = "__pathmind_routine__:";
    private static final String LIBRARY_ROUTINE_WORKSPACE_PREFIX = "__pathmind_library_routine__:";
    private static final int ROUTINE_EXIT_BUTTON_SIZE = 18;

    interface Host {
        NodeGraph nodeGraph();
        Sidebar sidebar();
        String activePresetName();
        void openRenameRoutinePopup(NodeGraphData.RoutineDefinitionData routine);
        void openRenameLibraryRoutinePopup(NodeGraphData.RoutineDefinitionData routine);
        int routineExitButtonX();
        int routineExitButtonY();
        float hoverProgress(String key, boolean hovered);
    }

    private static final class WorkspaceTab {
        private String label;
        private NodeGraphData graphData;
        private final Integer parentTabIndex;
        private final String hostTemplateNodeId;
        private final NodeGraphData.RoutineDefinitionData libraryRoutineDefinition;

        private WorkspaceTab(String label, NodeGraphData graphData, Integer parentTabIndex, String hostTemplateNodeId) {
            this(label, graphData, parentTabIndex, hostTemplateNodeId, null);
        }

        private WorkspaceTab(String label, NodeGraphData graphData, Integer parentTabIndex, String hostTemplateNodeId,
                             NodeGraphData.RoutineDefinitionData libraryRoutineDefinition) {
            this.label = label;
            this.graphData = graphData;
            this.parentTabIndex = parentTabIndex;
            this.hostTemplateNodeId = hostTemplateNodeId;
            this.libraryRoutineDefinition = libraryRoutineDefinition;
        }
    }

    private final Host host;
    private final List<WorkspaceTab> workspaceTabs = new ArrayList<>();
    private int activeWorkspaceTabIndex = 0;
    private boolean hasSavedOnClose = false;

    PathmindWorkspaceLifecycleController(Host host) {
        this.host = host;
    }

    void resetFromCurrentGraph() {
        workspaceTabs.clear();
        workspaceTabs.add(new WorkspaceTab("Main", host.nodeGraph().exportGraphDataSnapshot(), null, null));
        activeWorkspaceTabIndex = 0;
    }

    void openTemplateWorkspaceTab(Node templateNode) {
        if (templateNode == null || templateNode.getType() != NodeType.TEMPLATE) {
            return;
        }
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();

        int currentTab = activeWorkspaceTabIndex;
        String nodeId = templateNode.getId();
        for (int i = 0; i < workspaceTabs.size(); i++) {
            WorkspaceTab existing = workspaceTabs.get(i);
            if (existing.parentTabIndex != null && existing.parentTabIndex == currentTab
                && nodeId.equals(existing.hostTemplateNodeId)) {
                switchToWorkspaceTab(i);
                return;
            }
        }

        NodeGraphData source = templateNode.getTemplateGraphData();
        if (source == null || source.getNodes() == null || source.getNodes().isEmpty()) {
            source = createDefaultTemplateGraphData();
            templateNode.setTemplateGraphData(source);
            host.nodeGraph().markWorkspaceDirty();
        }
        String label = templateNode.getTemplateName();
        WorkspaceTab newTab = new WorkspaceTab(label, source, currentTab, nodeId);
        workspaceTabs.add(newTab);
        switchToWorkspaceTab(workspaceTabs.size() - 1);
    }

    void refreshRoutineSidebarContext() {
        if (workspaceTabs.isEmpty() || workspaceTabs.get(0).graphData == null) {
            host.sidebar().setRoutineContext(List.of(), "");
            return;
        }
        String activeId = "";
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        activeId = getRoutineWorkspaceId(active);
        NodeGraphData.RoutineDefinitionData activeRoutine = getActiveRoutineWorkspace();
        if (!activeId.isBlank() && (host.nodeGraph().isEditingParameterField() || host.nodeGraph().isEditingEventNameField())) {
            if (activeRoutine != null) host.nodeGraph().syncRoutineDefinitionMetadata(activeRoutine);
        }
        NodeGraphData rootData = workspaceTabs.get(0).graphData;
        host.nodeGraph().setRoutineValidationContext(isLibraryRoutineWorkspace(active)
            ? getActiveRoutineRegistry() : rootData.getRoutines());
        host.sidebar().setRoutineContext(rootData.getRoutines(), activeId, activeRoutine);
    }

    NodeGraphData.RoutineDefinitionData getActiveRoutineWorkspace() {
        if (workspaceTabs.isEmpty() || activeWorkspaceTabIndex < 0 || activeWorkspaceTabIndex >= workspaceTabs.size()) return null;
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (isLibraryRoutineWorkspace(active)) return active.libraryRoutineDefinition;
        if (active.hostTemplateNodeId == null || !active.hostTemplateNodeId.startsWith(ROUTINE_WORKSPACE_PREFIX)) return null;
        String routineId = active.hostTemplateNodeId.substring(ROUTINE_WORKSPACE_PREFIX.length());
        return workspaceTabs.get(0).graphData.getRoutines().stream()
            .filter(routine -> routineId.equals(routine.getId())).findFirst().orElse(null);
    }

    List<NodeGraphData.RoutineDefinitionData> getActiveRoutineRegistry() {
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (!isLibraryRoutineWorkspace(active)) return workspaceTabs.get(0).graphData.getRoutines();
        NodeGraphData.RoutineDefinitionData edited = active.libraryRoutineDefinition;
        List<NodeGraphData.RoutineDefinitionData> registry = new ArrayList<>(RoutineLibraryManager.list());
        registry.removeIf(routine -> routine != null && edited != null && edited.getId().equals(routine.getId()));
        if (edited != null) registry.add(edited);
        return registry;
    }

    List<NodeGraphData.RoutineDefinitionData> getRootRoutines() {
        if (workspaceTabs.isEmpty() || workspaceTabs.get(0).graphData == null) return List.of();
        return workspaceTabs.get(0).graphData.getRoutines();
    }

    void renderRoutineWorkspaceExitButton(GuiGraphics context, int mouseX, int mouseY) {
        if (getActiveRoutineWorkspace() == null) return;
        int x = host.routineExitButtonX();
        int y = host.routineExitButtonY();
        boolean hovered = PathmindWorkspaceChrome.contains(mouseX, mouseY, x, y,
            ROUTINE_EXIT_BUTTON_SIZE, ROUTINE_EXIT_BUTTON_SIZE);
        PathmindRoutineUi.renderReturnButton(context, x, y, ROUTINE_EXIT_BUTTON_SIZE, mouseX, mouseY,
            host.hoverProgress("routine-return-button", hovered),
            PathmindRoutineUi.subtleRoutineAccent(NodeCategory.ROUTINES.getColor()));
    }

    void createRoutineFromSidebar(String name) {
        persistActiveWorkspaceToTabs();
        WorkspaceTab root = workspaceTabs.get(0);
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine(name);
        root.graphData.getRoutines().add(routine);
        if (activeWorkspaceTabIndex == 0) {
            // Keep NodeGraph's in-memory registry aligned before openRoutineWorkspaceTab
            // persists the main tab again.
            host.nodeGraph().applyGraphDataSnapshot(root.graphData, false);
        }
        openRoutineWorkspaceTab(routine.getId());
    }

    void addInputToActiveRoutine() {
        if (workspaceTabs.isEmpty()) return;
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (getRoutineWorkspaceId(active).isBlank()) return;
        persistActiveWorkspaceToTabs();
        NodeGraphData.RoutineDefinitionData routine = getActiveRoutineWorkspace();
        if (routine == null) return;
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        int number = routine.getInputs().size() + 1;
        NodeGraphData.RoutineInputData input = builder.addInput(number == 1 ? "input" : "input " + number, RoutineValueKind.TEXT);
        Node reporter = builder.createInputReporter(input.getId(), 420, 140 + (number - 1) * 80);
        if (reporter != null) host.nodeGraph().addNode(reporter);
        active.graphData = routine.getGraph();
        host.nodeGraph().markWorkspaceDirty();
    }

    void editActiveRoutineInput(String inputId, int action) {
        if (workspaceTabs.isEmpty()) return;
        WorkspaceTab active = workspaceTabs.get(activeWorkspaceTabIndex);
        if (getRoutineWorkspaceId(active).isBlank()) return;
        persistActiveWorkspaceToTabs();
        NodeGraphData.RoutineDefinitionData routine = getActiveRoutineWorkspace();
        if (routine == null) return;
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        if (action == 2) {
            builder.removeInput(inputId);
            for (Node node : new ArrayList<>(host.nodeGraph().getNodes())) {
                if (node.getType() == NodeType.ROUTINE_INPUT && inputId.equals(node.getRoutineInputId())) host.nodeGraph().removeNode(node);
            }
        } else {
            builder.moveInput(inputId, action);
        }
        host.nodeGraph().markWorkspaceDirty();
    }

    void handleRoutineAction(String routineId, int action) {
        if (workspaceTabs.isEmpty()) return;
        persistActiveWorkspaceToTabs();
        NodeGraphData root = workspaceTabs.get(0).graphData;
        NodeGraphData.RoutineDefinitionData routine = root.getRoutines().stream()
            .filter(candidate -> candidate != null && routineId.equals(candidate.getId())).findFirst().orElse(null);
        if (routine == null) return;
        if (action == 7) {
            host.openRenameRoutinePopup(routine);
            return;
        }
        if (action != 4 || !RoutineLifecycle.delete(root, routineId)) return;
        String hostId = ROUTINE_WORKSPACE_PREFIX + routineId;
        workspaceTabs.removeIf(tab -> hostId.equals(tab.hostTemplateNodeId));
        switchToRootAfterRoutineChange(root);
    }

    void handleRoutineLibraryAction(String libraryRoutineId, int action) {
        if (action == 7) {
            RoutineLibraryManager.list().stream()
                .filter(routine -> routine != null && libraryRoutineId.equals(routine.getId()))
                .findFirst().ifPresent(host::openRenameLibraryRoutinePopup);
            return;
        }
        if (action != 3) return;
        String hostId = LIBRARY_ROUTINE_WORKSPACE_PREFIX + libraryRoutineId;
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (!hostId.equals(workspaceTabs.get(i).hostTemplateNodeId)) continue;
            if (i == activeWorkspaceTabIndex) switchToWorkspaceTab(0);
            else if (i < activeWorkspaceTabIndex) activeWorkspaceTabIndex--;
            workspaceTabs.remove(i);
            break;
        }
        RoutineLibraryManager.delete(libraryRoutineId);
        refreshRoutineSidebarContext();
    }

    boolean saveDraggedRoutineToLibrary(double mouseX, double mouseY, boolean draggingFromRoutineLibrary,
                                        Node draggingSidebarNode) {
        if (draggingFromRoutineLibrary || draggingSidebarNode == null
            || draggingSidebarNode.getType() != NodeType.ROUTINE_CALL
            || !host.sidebar().isRoutineLibraryDropTarget(mouseX, mouseY)) return false;
        persistActiveWorkspaceToTabs();
        NodeGraphData root = workspaceTabs.get(0).graphData;
        root.getRoutines().stream()
            .filter(routine -> routine != null && draggingSidebarNode.getRoutineId().equals(routine.getId()))
            .findFirst().ifPresent(routine -> RoutineLibraryManager.share(routine, root.getRoutines()));
        refreshRoutineSidebarContext();
        return true;
    }

    Node dropDraggedSidebarNodeIntoWorkspace(int mouseX, int mouseY, boolean draggingFromRoutineLibrary,
                                             Node draggingSidebarNode, NodeType draggingNodeType) {
        int worldMouseX = host.nodeGraph().screenToWorldX(mouseX);
        int worldMouseY = host.nodeGraph().screenToWorldY(mouseY);
        if (!draggingFromRoutineLibrary) {
            return draggingSidebarNode != null
                ? host.nodeGraph().handleSidebarDrop(draggingSidebarNode, worldMouseX, worldMouseY)
                : host.nodeGraph().handleSidebarDrop(draggingNodeType, worldMouseX, worldMouseY);
        }
        NodeGraphData.RoutineDefinitionData imported = ensureDraggedLibraryRoutineImported(draggingSidebarNode);
        if (imported == null) return null;
        return host.nodeGraph().handleSidebarDrop(Node.createRoutineCall(imported, 0, 0), worldMouseX, worldMouseY);
    }

    boolean importDraggedLibraryRoutineToList(double mouseX, double mouseY, boolean draggingFromRoutineLibrary,
                                              Node draggingSidebarNode) {
        if (!draggingFromRoutineLibrary || !host.sidebar().isRoutineListDropTarget(mouseX, mouseY)) return false;
        ensureDraggedLibraryRoutineImported(draggingSidebarNode);
        return true;
    }

    private NodeGraphData.RoutineDefinitionData ensureDraggedLibraryRoutineImported(Node draggingSidebarNode) {
        if (draggingSidebarNode == null || draggingSidebarNode.getRoutineId().isBlank() || workspaceTabs.isEmpty()) return null;
        persistActiveWorkspaceToTabs();
        NodeGraphData root = workspaceTabs.get(0).graphData;
        String libraryRoutineId = draggingSidebarNode.getRoutineId();
        NodeGraphData.RoutineDefinitionData imported = root.getRoutines().stream()
            .filter(routine -> routine != null && (libraryRoutineId.equals(routine.getId())
                || libraryRoutineId.equals(routine.getLibraryRoutineId())))
            .findFirst().orElse(null);
        if (imported == null) {
            RoutineLibraryManager.ImportResult result = RoutineLibraryManager.importInto(root, libraryRoutineId);
            if (!result.added() || result.routine() == null) return null;
            imported = result.routine();
            if (activeWorkspaceTabIndex == 0) host.nodeGraph().applyGraphDataSnapshot(root, false);
            else host.nodeGraph().setRoutineValidationContext(root.getRoutines());
        }
        refreshRoutineSidebarContext();
        return imported;
    }

    void switchToRootAfterRoutineChange(NodeGraphData root) {
        host.nodeGraph().setActiveRoutineWorkspaceId("");
        host.nodeGraph().applyGraphDataSnapshot(root, false);
        activeWorkspaceTabIndex = 0;
        host.nodeGraph().markWorkspaceDirty();
    }

    void openRoutineWorkspaceTab(String routineId) {
        if (routineId == null || workspaceTabs.isEmpty()) return;
        persistActiveWorkspaceToTabs();
        WorkspaceTab root = workspaceTabs.get(0);
        NodeGraphData.RoutineDefinitionData routine = root.graphData.getRoutines().stream()
            .filter(candidate -> routineId.equals(candidate.getId())).findFirst().orElse(null);
        if (routine == null) return;
        new RoutineBuilderModel(routine).ensureDefinitionGraph();
        String hostId = ROUTINE_WORKSPACE_PREFIX + routineId;
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (hostId.equals(workspaceTabs.get(i).hostTemplateNodeId)) {
                switchToWorkspaceTab(i);
                return;
            }
        }
        workspaceTabs.add(new WorkspaceTab(routine.getName(), routine.getGraph(), 0, hostId));
        switchToWorkspaceTab(workspaceTabs.size() - 1);
    }

    void openLibraryRoutineWorkspaceTab(String routineId) {
        if (routineId == null || workspaceTabs.isEmpty()) return;
        persistActiveWorkspaceToTabs();
        String hostId = LIBRARY_ROUTINE_WORKSPACE_PREFIX + routineId;
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (hostId.equals(workspaceTabs.get(i).hostTemplateNodeId)) {
                switchToWorkspaceTab(i);
                return;
            }
        }
        NodeGraphData.RoutineDefinitionData routine = RoutineLibraryManager.list().stream()
            .filter(candidate -> candidate != null && routineId.equals(candidate.getId())).findFirst().orElse(null);
        if (routine == null) return;
        new RoutineBuilderModel(routine).ensureDefinitionGraph();
        workspaceTabs.add(new WorkspaceTab(routine.getName(), routine.getGraph(), null, hostId, routine));
        switchToWorkspaceTab(workspaceTabs.size() - 1);
    }

    void switchToWorkspaceTab(int targetIndex) {
        if (targetIndex < 0 || targetIndex >= workspaceTabs.size() || targetIndex == activeWorkspaceTabIndex) {
            return;
        }
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();

        WorkspaceTab target = workspaceTabs.get(targetIndex);
        if (target == null) {
            return;
        }
        String routineWorkspaceId = getRoutineWorkspaceId(target);
        activeWorkspaceTabIndex = targetIndex;
        host.nodeGraph().setActiveRoutineWorkspaceId(routineWorkspaceId);
        host.nodeGraph().setRoutineValidationContext(isLibraryRoutineWorkspace(target)
            ? getActiveRoutineRegistry() : workspaceTabs.get(0).graphData.getRoutines());
        NodeGraphData data = target.graphData != null ? target.graphData : createDefaultTemplateGraphData();
        host.nodeGraph().applyGraphDataSnapshot(data, false);
    }

    private boolean isLibraryRoutineWorkspace(WorkspaceTab tab) {
        return tab != null && tab.hostTemplateNodeId != null
            && tab.hostTemplateNodeId.startsWith(LIBRARY_ROUTINE_WORKSPACE_PREFIX);
    }

    private String getRoutineWorkspaceId(WorkspaceTab tab) {
        if (tab == null || tab.hostTemplateNodeId == null) return "";
        if (tab.hostTemplateNodeId.startsWith(ROUTINE_WORKSPACE_PREFIX)) {
            return tab.hostTemplateNodeId.substring(ROUTINE_WORKSPACE_PREFIX.length());
        }
        if (tab.hostTemplateNodeId.startsWith(LIBRARY_ROUTINE_WORKSPACE_PREFIX)) {
            return tab.hostTemplateNodeId.substring(LIBRARY_ROUTINE_WORKSPACE_PREFIX.length());
        }
        return "";
    }

    void persistActiveWorkspaceToTabs() {
        if (workspaceTabs.isEmpty() || activeWorkspaceTabIndex < 0 || activeWorkspaceTabIndex >= workspaceTabs.size()) {
            return;
        }
        WorkspaceTab tab = workspaceTabs.get(activeWorkspaceTabIndex);
        tab.graphData = host.nodeGraph().exportGraphDataSnapshot();
        if (isLibraryRoutineWorkspace(tab) && tab.libraryRoutineDefinition != null) {
            RoutineWorkspaceSupport.syncMetadata(tab.libraryRoutineDefinition, tab.graphData);
            tab.libraryRoutineDefinition.setGraph(tab.graphData);
            tab.label = tab.libraryRoutineDefinition.getName();
            RoutineLibraryManager.save(tab.libraryRoutineDefinition);
            return;
        }
        if (tab.parentTabIndex != null && tab.parentTabIndex >= 0 && tab.parentTabIndex < workspaceTabs.size()) {
            WorkspaceTab parent = workspaceTabs.get(tab.parentTabIndex);
            if (tab.hostTemplateNodeId != null && tab.hostTemplateNodeId.startsWith(ROUTINE_WORKSPACE_PREFIX)) {
                String routineId = tab.hostTemplateNodeId.substring(ROUTINE_WORKSPACE_PREFIX.length());
                for (NodeGraphData.RoutineDefinitionData routine : parent.graphData.getRoutines()) {
                    if (routineId.equals(routine.getId())) {
                        RoutineWorkspaceSupport.syncMetadata(routine, tab.graphData);
                        routine.setGraph(tab.graphData);
                        tab.label = routine.getName();
                        return;
                    }
                }
            }
            if (parent != null && parent.graphData != null && parent.graphData.getNodes() != null) {
                for (NodeGraphData.NodeData nodeData : parent.graphData.getNodes()) {
                    if (nodeData != null && tab.hostTemplateNodeId != null && tab.hostTemplateNodeId.equals(nodeData.getId())) {
                        nodeData.setTemplateGraph(tab.graphData);
                        nodeData.setTemplateName(tab.label);
                        break;
                    }
                }
            }
        } else {
            tab.label = "Main";
        }
    }

    NodeGraphData snapshotRootPresetWorkspace() {
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();
        if (!workspaceTabs.isEmpty() && workspaceTabs.get(0).graphData != null) {
            return workspaceTabs.get(0).graphData;
        }
        return host.nodeGraph().exportGraphDataSnapshot();
    }

    boolean saveRootPresetWorkspace() {
        return NodeGraphPersistence.saveNodeGraphDataForPreset(host.activePresetName(), snapshotRootPresetWorkspace());
    }

    void syncAllTemplateTabsIntoParents() {
        if (workspaceTabs.isEmpty()) {
            return;
        }
        for (int i = 0; i < workspaceTabs.size(); i++) {
            if (i == activeWorkspaceTabIndex) {
                continue;
            }
            WorkspaceTab tab = workspaceTabs.get(i);
            if (tab == null || tab.parentTabIndex == null || tab.graphData == null) {
                continue;
            }
            if (tab.parentTabIndex < 0 || tab.parentTabIndex >= workspaceTabs.size()) {
                continue;
            }
            WorkspaceTab parent = workspaceTabs.get(tab.parentTabIndex);
            if (parent == null || parent.graphData == null || parent.graphData.getNodes() == null) {
                continue;
            }
            for (NodeGraphData.NodeData nodeData : parent.graphData.getNodes()) {
                if (nodeData != null && tab.hostTemplateNodeId != null && tab.hostTemplateNodeId.equals(nodeData.getId())) {
                    nodeData.setTemplateGraph(tab.graphData);
                    nodeData.setTemplateName(tab.label);
                    break;
                }
            }
        }
    }

    void restoreRootWorkspaceIfNeeded() {
        if (workspaceTabs.isEmpty()) {
            return;
        }
        WorkspaceTab root = workspaceTabs.get(0);
        if (root == null || root.graphData == null) {
            return;
        }
        host.nodeGraph().setActiveRoutineWorkspaceId("");
        host.nodeGraph().applyGraphDataSnapshot(root.graphData, false);
        activeWorkspaceTabIndex = 0;
    }

    private NodeGraphData createDefaultTemplateGraphData() {
        NodeGraphData data = new NodeGraphData();
        NodeGraphData.NodeData start = new NodeGraphData.NodeData();
        start.setId(UUID.randomUUID().toString());
        start.setType(NodeType.START);
        start.setX(220);
        start.setY(160);
        start.setStartNodeNumber(1);
        data.getNodes().add(start);
        return data;
    }

    void autoSaveWorkspace() {
        if (hasSavedOnClose) {
            return;
        }

        hasSavedOnClose = true;

        host.nodeGraph().stopCoordinateEditing(true);
        host.nodeGraph().stopAmountEditing(true);
        host.nodeGraph().stopStopTargetEditing(true);
        host.nodeGraph().stopVariableEditing(true);
        host.nodeGraph().stopMessageEditing(true);
        host.nodeGraph().stopParameterEditing(true);
        host.nodeGraph().stopParameterEditing(true);
        host.nodeGraph().stopStickyNoteEditing(true);
        persistActiveWorkspaceToTabs();
        syncAllTemplateTabsIntoParents();
        restoreRootWorkspaceIfNeeded();

        saveRootPresetWorkspace();

        PresetManager.setActivePreset(host.activePresetName());
    }

    boolean renameOpenLibraryRoutine(String routineId, String routineName) {
        if (!RoutineLibraryManager.rename(routineId, routineName)) return false;
        String libraryHostId = LIBRARY_ROUTINE_WORKSPACE_PREFIX + routineId;
        for (WorkspaceTab tab : workspaceTabs) {
            if (!libraryHostId.equals(tab.hostTemplateNodeId) || tab.libraryRoutineDefinition == null) continue;
            new RoutineBuilderModel(tab.libraryRoutineDefinition).renameRoutine(routineName);
            tab.graphData = tab.libraryRoutineDefinition.getGraph();
            tab.label = routineName;
        }
        refreshRoutineSidebarContext();
        return true;
    }

    boolean isDuplicateRoutineName(String routineName, String ignoredRoutineId) {
        return !workspaceTabs.isEmpty() && workspaceTabs.get(0).graphData.getRoutines().stream()
            .anyMatch(routine -> routine != null && routine.getName() != null
                && !routine.getId().equals(ignoredRoutineId)
                && routineName.equalsIgnoreCase(routine.getName().trim()));
    }

    void renameRoutine(String routineId, String routineName) {
        NodeGraphData root = workspaceTabs.get(0).graphData;
        NodeGraphData.RoutineDefinitionData routine = root.getRoutines().stream()
            .filter(candidate -> candidate != null && routineId.equals(candidate.getId()))
            .findFirst().orElse(null);
        if (routine != null) {
            new RoutineBuilderModel(routine).renameRoutine(routineName);
            switchToRootAfterRoutineChange(root);
        }
    }
}
