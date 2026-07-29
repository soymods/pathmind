package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.ParameterTypeClassifier.isAmountParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionAttributeParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionBooleanValueParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockFaceParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockItemParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBooleanLiteralParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isDirectionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isEntityParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isEntityStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isFabricEventSensorParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isGuiParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isHandParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isInlineDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isItemParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isListIndexParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMessageParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMouseButtonParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerProfessionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeVariantParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isPlayerParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isSeedParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isTradeInlineParameter;
import static com.pathmind.ui.graph.ParameterDropdownOptions.getAttributeDetectionTargetKind;
import static com.pathmind.ui.graph.ParameterDropdownOptions.getParameterDropdownOptions;
import static com.pathmind.ui.graph.ParameterDropdownOptions.resolveParameterDropdownIcon;
import static com.pathmind.ui.graph.SchematicRepository.loadSchematicOptions;
import static com.pathmind.ui.graph.SchematicRepository.schematicExistsInRoots;
import static com.pathmind.ui.graph.InlineVariableRenderer.buildInlineVariableRender;
import static com.pathmind.ui.graph.InlineVariableRenderer.isSingleKnownInlineVariableReference;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.SettingsManager;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.AttributeDetectionConfig;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCatalog;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.ParameterType;
import com.pathmind.nodes.RuntimeValueScope;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.nodes.StartScreenTarget;
import com.pathmind.ui.menu.ContextMenuSelection;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.UiUtilsProxy;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.FabricEventTracker;
import com.pathmind.util.InputCompatibilityBridge;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.ui.graph.InlineVariableRenderer.InlineVariableRender;

/**
 * Manages the node graph for the Pathmind visual editor.
 * Handles node rendering, connections, and interactions.
 */
public class NodeGraph {
    private static final int DUPLICATE_OFFSET_X = 32;
    private static final int DUPLICATE_OFFSET_Y = 24;
    private static final int MINIMAL_NODE_TAB_WIDTH = 6;
    private static final int TEMPLATE_PREVIEW_MARGIN = 6;
    private static final int NODE_HEADER_BUTTON_SIZE = 12;
    private static final int GRID_SNAP_SIZE = 20;

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
    private static final int TEMPLATE_PREVIEW_TOP = 42;
    private static final int TEMPLATE_PREVIEW_BOTTOM_MARGIN = 6;
    private static final int STICKY_NOTE_MAX_CHARS = 4096;
    private static final int PROFILER_OVERLAY_MARGIN = 10;
    private static final int PROFILER_OVERLAY_PADDING = 6;

    private final List<Node> nodes;
    private final List<NodeConnection> connections;
    private final List<Node> cachedRootNodes;
    private final Map<Node, SelectionBounds> cachedHierarchyBounds;
    private final Map<Node, Integer> cachedHierarchyNodeCounts;
    private final ViewportController viewport = new ViewportController(new ViewportController.Host() {
        @Override public void rebuildHierarchyCacheIfNeeded() {
            NodeGraph.this.rebuildHierarchyCacheIfNeeded();
        }
        @Override public List<Node> cachedRootNodes() { return cachedRootNodes; }
        @Override public Map<Node, SelectionBounds> cachedHierarchyBounds() { return cachedHierarchyBounds; }
        @Override public Map<Node, Integer> cachedHierarchyNodeCounts() { return cachedHierarchyNodeCounts; }
        @Override public String activePreset() { return workspace.getActivePreset(); }
    });
    private final NodeFocusController nodeFocus = new NodeFocusController(new NodeFocusController.Host() {
        @Override public List<Node> nodes() { return nodes; }
        @Override public void stopEditorsForFocus() {
            stopCoordinateEditing(true);
            stopAmountEditing(true);
            stopMessageEditing(true);
            stopParameterEditing(true);
            stopStopTargetEditing(true);
            stopVariableEditing(true);
            stopEventNameEditing(true);
        }
        @Override public void clearSelection() { NodeGraph.this.clearSelection(); }
        @Override public void selectNode(Node node) { NodeGraph.this.selectNode(node); }
        @Override public void focusViewport(
            Node node, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight
        ) {
            viewport.focusNode(node, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
        }
    });
    private final NodeGraphLoadController graphLoader =
        new NodeGraphLoadController(new NodeGraphLoadController.Host() {
            @Override public List<Node> nodes() { return nodes; }
            @Override public List<NodeConnection> connections() { return connections; }
            @Override public void setRoutineRegistry(
                List<NodeGraphData.RoutineDefinitionData> routines
            ) {
                routineWorkspace.setRoutineRegistry(routines);
            }
            @Override public void invalidateRenderCaches() {
                NodeGraph.this.invalidateRenderCaches();
            }
            @Override public void clearSelectionTransientState() {
                selectionController.clearTransientState();
            }
            @Override public void normalizeStartNodeNumbers() {
                nodeLifecycle.normalizeStartNodeNumbers();
            }
            @Override public void syncRoutineInvocations() {
                NodeGraph.this.syncRoutineInvocations();
            }
            @Override public void addConnectionReplacingConflicts(
                Node outputNode, Node inputNode, int outputSocket, int inputSocket
            ) {
                NodeGraph.this.addConnectionReplacingConflicts(
                    outputNode, inputNode, outputSocket, inputSocket);
            }
            @Override public void resetDropTargets() { selectionController.resetDropTargets(); }
            @Override public void clearConnectionGraphState() {
                connectionController.clearGraphState();
            }
            @Override public void clearStartHoverState() {
                hoveringStartButton = false;
                hoveredStartNode = null;
            }
            @Override public void invalidateValidation() {
                NodeGraph.this.invalidateValidation();
            }
            @Override public void restoreSessionViewportState() {
                NodeGraph.this.restoreSessionViewportState();
            }
            @Override public void setNextStartNodeNumber(int value) {
                nextStartNodeNumber = value;
            }
        });
    private final NodeLifecycleController nodeLifecycle =
        new NodeLifecycleController(new NodeLifecycleController.Host() {
            @Override public List<Node> nodes() { return nodes; }
            @Override public List<NodeConnection> connections() { return connections; }
            @Override public String activeRoutineWorkspaceId() {
                return routineWorkspace.getActiveRoutineWorkspaceId();
            }
            @Override public int nextStartNodeNumber() { return nextStartNodeNumber; }
            @Override public void setNextStartNodeNumber(int value) {
                nextStartNodeNumber = value;
            }
            @Override public void pushUndoState() { NodeGraph.this.pushUndoState(); }
            @Override public void markWorkspaceDirty() { NodeGraph.this.markWorkspaceDirty(); }
            @Override public void stopEditorsForRemovedNode(Node node) {
                if (inlineFields.getCoordinateEditingNode() == node) {
                    stopCoordinateEditing(false);
                }
                if (inlineFields.getAmountEditingNode() == node) {
                    stopAmountEditing(false);
                }
                if (inlineFields.getStopTargetEditingNode() == node) {
                    stopStopTargetEditing(false);
                }
                if (inlineFields.getVariableEditingNode() == node) {
                    stopVariableEditing(false);
                }
                if (inlineFields.getMessageEditingNode() == node) {
                    stopMessageEditing(false);
                }
            }
            @Override public void closeRunPresetDropdownForRemovedNode(Node node) {
                if (specializedSelectors.getRunPresetNode() == node) {
                    closeRunPresetDropdown();
                }
            }
            @Override public void clearDropTargetsForRemovedNode(Node node) {
                selectionController.clearDropTargetsForRemovedNode(node);
            }
            @Override public void addConnectionReplacingConflicts(
                Node outputNode, Node inputNode, int outputSocket, int inputSocket
            ) {
                NodeGraph.this.addConnectionReplacingConflicts(
                    outputNode, inputNode, outputSocket, inputSocket);
            }
            @Override public void onNodeRemoved(Node node) {
                selectionController.onNodeRemoved(node);
            }
            @Override public void invalidateHierarchyCache() {
                NodeGraph.this.invalidateHierarchyCache();
            }
            @Override public void invalidateRenderCaches() {
                NodeGraph.this.invalidateRenderCaches();
            }
            @Override public int cameraX() { return viewport.getCameraX(); }
            @Override public boolean isNodeOverSidebar(
                Node node, int sidebarWidth, int screenX, int screenWidth
            ) {
                return NodeGraph.this.isNodeOverSidebar(
                    node, sidebarWidth, screenX, screenWidth);
            }
            @Override public void pruneSelectionToCurrentNodes() {
                NodeGraph.this.pruneSelectionToCurrentNodes();
            }
            @Override public Set<Node> selectedNodes() {
                return selectionController.getSelectedNodes();
            }
            @Override public void clearSelection() { NodeGraph.this.clearSelection(); }
        });
    private final RoutineWorkspaceController routineWorkspace =
        new RoutineWorkspaceController(new RoutineWorkspaceController.Host() {
            @Override public List<Node> nodes() { return nodes; }
            @Override public void invalidateValidation() {
                NodeGraph.this.invalidateValidation();
            }
            @Override public String liveRoutineParameterValue(
                Node node, String parameterName
            ) {
                return NodeGraph.this.liveRoutineParameterValue(node, parameterName);
            }
        });
    private final GraphWorkspaceController workspace =
        new GraphWorkspaceController(new GraphWorkspaceController.Host() {
            @Override public List<Node> nodes() { return nodes; }
            @Override public List<NodeConnection> connections() { return connections; }
            @Override public List<NodeGraphData.RoutineDefinitionData> routineRegistry() {
                return routineWorkspace.routineRegistry();
            }
            @Override public List<NodeGraphData.RoutineDefinitionData> validationRoutines() {
                return routineWorkspace.validationRoutines();
            }
            @Override public String activeRoutineWorkspaceId() {
                return routineWorkspace.getActiveRoutineWorkspaceId();
            }
            @Override public void cancelDeferredStickySave() {
                stickyNoteController.cancelDeferredSave();
            }
            @Override public void commitPendingStickyEdit() {
                stickyNoteController.commitPendingEdit();
            }
            @Override public boolean applyLoadedData(NodeGraphData data) {
                return graphLoader.applyLoadedData(data);
            }
            @Override public NodeGraphData buildGraphDataSnapshot() {
                return buildGraphData(
                    new ArrayList<>(nodes), new ArrayList<>(connections), null);
            }
            @Override public void invalidateRenderCaches() {
                NodeGraph.this.invalidateRenderCaches();
            }
            @Override public void cacheSessionViewportState() {
                NodeGraph.this.cacheSessionViewportState();
            }
            @Override public void restoreSessionViewportState() {
                NodeGraph.this.restoreSessionViewportState();
            }
        });
    private final ConnectionController connectionController = new ConnectionController(new ConnectionController.Host() {
        @Override public List<Node> getNodes() { return nodes; }
        @Override public List<NodeConnection> getConnections() { return connections; }
        @Override public Node getNodeAtWorld(int worldX, int worldY) { return NodeGraph.this.getNodeAtWorld(worldX, worldY); }
        @Override public Node getNodeAtWorldExcluding(int worldX, int worldY, Node excludedNode) {
            return NodeGraph.this.getNodeAtWorldExcluding(worldX, worldY, excludedNode);
        }
        @Override public Node getParentForNode(Node node) { return NodeGraph.this.getParentForNode(node); }
        @Override public void stopConnectionEditors() { NodeGraph.this.stopConnectionEditors(); }
        @Override public void pushUndoState() { NodeGraph.this.pushUndoState(); }
        @Override public boolean isUndoCaptureSuppressed() { return suppressUndoCapture; }
        @Override public void markWorkspaceDirty() { NodeGraph.this.markWorkspaceDirty(); }
        @Override public void invalidateRenderCaches() { NodeGraph.this.invalidateRenderCaches(); }
        @Override public void markDragOperationChanged() { selectionController.markDragOperationChanged(); }
    });
    private final ConnectionRenderer connectionRenderer = new ConnectionRenderer(new ConnectionRenderer.Host() {
        @Override public List<NodeConnection> getConnections() { return connections; }
        @Override public List<Node> getVisibleRootsForViewport() { return NodeGraph.this.getVisibleRootsForViewport(); }
        @Override public int getViewportWorldWidth() { return NodeGraph.this.getViewportWorldWidth(); }
        @Override public int getViewportWorldHeight() { return NodeGraph.this.getViewportWorldHeight(); }
        @Override public int getCameraX() { return viewport.getCameraX(); }
        @Override public int getCameraY() { return viewport.getCameraY(); }
        @Override public boolean isDenseViewportMode() { return viewport.isDenseViewportMode(); }
        @Override public boolean shouldRenderConnectionsOnTop() { return NodeGraph.this.shouldRenderConnectionsOnTop(); }
        @Override public Node getParentForNode(Node node) { return NodeGraph.this.getParentForNode(node); }
        @Override public boolean shouldConsiderConnectionForViewport(NodeConnection connection, Set<Node> visibleRoots,
                                                                     int viewportWidth, int viewportHeight) {
            return NodeGraph.this.shouldConsiderConnectionForViewport(connection, visibleRoots, viewportWidth, viewportHeight);
        }
        @Override public boolean isNodeOverSidebarForRender(Node node, int screenX, int screenWidth) {
            return NodeGraph.this.isNodeOverSidebarForRender(node, screenX, screenWidth);
        }
        @Override public int toGrayscale(int color, float brightnessFactor) {
            return nodeControls.toGrayscale(color, brightnessFactor);
        }
        @Override public int getSelectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public void renderSocket(GuiGraphics context, int x, int y, boolean isInput, int color) {
            NodeGraph.this.renderSocket(context, x, y, isInput, color);
        }
        @Override public void setProfilerConnectionMs(double profilerConnectionMs) {
            NodeGraph.this.profilerConnectionMs = profilerConnectionMs;
        }
    }, connectionController);
    
    private double profilerRenderMs = 0.0;
    private double profilerNodeMs = 0.0;
    private double profilerConnectionMs = 0.0;
    private double profilerDropdownMs = 0.0;
    private double profilerHoverMs = 0.0;
    private double profilerHitTestAvgMs = 0.0;
    private double profilerHitTestAvgRoots = 0.0;
    private int profilerVisibleNodes = 0;
    private int profilerDrawnNodes = 0;
    private int profilerVisibleRoots = 0;
    private int profilerDrawnConnections = 0;
    private long profilerHitTestTotalNanos = 0L;
    private long profilerHitTestCallCount = 0L;
    private long profilerHitTestTotalRoots = 0L;
    
    // Start button hover state
    private boolean hoveringStartButton = false;
    private Node hoveredStartNode = null;
    private boolean lastStartButtonTriggeredExecution = false;
    private final StartModeDropdownController startModeDropdown = new StartModeDropdownController(new StartModeDropdownController.Host() {
        @Override public float getZoomScale() { return NodeGraph.this.getZoomScale(); }
        @Override public int worldToScreenX(int worldX) { return NodeGraph.this.worldToScreenX(worldX); }
        @Override public int worldToScreenY(int worldY) { return NodeGraph.this.worldToScreenY(worldY); }
        @Override public int getScaledScreenWidth() {
            Minecraft client = Minecraft.getInstance();
            return client != null && client.getWindow() != null ? client.getWindow().getGuiScaledWidth() : 0;
        }
        @Override public int getScaledScreenHeight() {
            Minecraft client = Minecraft.getInstance();
            return client != null && client.getWindow() != null ? client.getWindow().getGuiScaledHeight() : 0;
        }
        @Override public int getSelectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public Node findStartModeButtonAt(int mouseX, int mouseY) { return NodeGraph.this.findStartModeButtonAt(mouseX, mouseY); }
        @Override public int getStartModeButtonWorldX(Node node) { return NodeGraph.this.getStartModeButtonWorldX(node); }
        @Override public int getStartModeButtonWorldY(Node node) { return NodeGraph.this.getStartModeButtonWorldY(node); }
        @Override public void markWorkspaceDirty() { NodeGraph.this.markWorkspaceDirty(); }
        @Override public void closeContextMenu() { NodeGraph.this.closeContextMenu(); }
        @Override public void closeNodeContextMenu() { NodeGraph.this.closeNodeContextMenu(); }
    });
    private final GraphContextMenuController contextMenus =
        new GraphContextMenuController(new GraphContextMenuController.Host() {
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public int screenToWorldX(int screenX) {
                return NodeGraph.this.screenToWorldX(screenX);
            }
            @Override public int screenToWorldY(int screenY) {
                return NodeGraph.this.screenToWorldY(screenY);
            }
            @Override public int worldToScreenX(int worldX) {
                return NodeGraph.this.worldToScreenX(worldX);
            }
            @Override public int worldToScreenY(int worldY) {
                return NodeGraph.this.worldToScreenY(worldY);
            }
            @Override public boolean isNodeSelected(Node node) {
                return NodeGraph.this.isNodeSelected(node);
            }
            @Override public void selectNode(Node node) { NodeGraph.this.selectNode(node); }
            @Override public void copySelectedNodeToClipboard() {
                NodeGraph.this.copySelectedNodeToClipboard();
            }
            @Override public void duplicateSelectedNode() { NodeGraph.this.duplicateSelectedNode(); }
            @Override public void pasteClipboardNode() { NodeGraph.this.pasteClipboardNode(); }
            @Override public void deleteSelectedNode() { NodeGraph.this.deleteSelectedNode(); }
        });

    private final Map<Node, AnimatedValue> amountToggleAnimations = new WeakHashMap<>();
    private final Map<Node, AnimatedValue> randomRoundingToggleAnimations = new WeakHashMap<>();

    // Double-click detection
    private int sidebarWidthForRendering = 180;
    private boolean executionEnabled = true;
    private boolean hierarchyGeometryDirty = true;
    private int visibleNodeCountForFrame = 0;
    private final Map<TrimKey, String> trimmedTextCache = new HashMap<>();
    private final Map<String, Set<String>> runtimeVariableNamesFrameCache = new HashMap<>();
    private Set<String> cachedBaseRuntimeVariableNames = null;

    private static final int PARAMETER_INPUT_HEIGHT = 16;
    private static final int PARAMETER_INPUT_GAP = 4;
    private static final int DIRECTION_MODE_TAB_HEIGHT = 18;

    private final InlineFieldController inlineFields = new InlineFieldController(new InlineFieldController.Host() {
        @Override public Font getClientTextRenderer() { return NodeGraph.this.getClientTextRenderer(); }
        @Override public void closeSchematicDropdown() { NodeGraph.this.closeSchematicDropdown(); }
        @Override public void closeRunPresetDropdown() { NodeGraph.this.closeRunPresetDropdown(); }
        @Override public void closeRandomRoundingDropdown() { NodeGraph.this.closeRandomRoundingDropdown(); }
        @Override public void stopParameterEditing(boolean commit) { NodeGraph.this.stopParameterEditing(commit); }
        @Override public void stopStickyNoteEditing(boolean commit) { NodeGraph.this.stopStickyNoteEditing(commit); }
        @Override public void cancelScreenCoordinateCapture() { NodeGraph.this.cancelScreenCoordinateCapture(); }
        @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
        @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
        @Override public boolean isScreenCoordinateCaptureActiveFor(Node node) {
            return NodeGraph.this.isScreenCoordinateCaptureActiveFor(node);
        }
        @Override public void notifyNodeParametersChanged(Node node) { NodeGraph.this.notifyNodeParametersChanged(node); }
        @Override public boolean isPresetSelectorNode(Node node) { return NodeGraph.this.isPresetSelectorNode(node); }
        @Override public boolean isNumericOrVariableReference(String value, Node node, boolean allowDecimal, boolean allowNegative) {
            return NodeGraph.this.isNumericOrVariableReference(value, node, allowDecimal, allowNegative);
        }
        @Override public String getStopTargetParameterKey(Node node) { return NodeGraph.this.getStopTargetParameterKey(node); }
        @Override public String getNumberExpressionErrorMessage() {
            return tr("pathmind.error.enterNumberExpressionOrVariable");
        }
        @Override public boolean isTextShortcutDown(int modifiers) { return NodeGraph.this.isTextShortcutDown(modifiers); }
        @Override public String getClipboardText() { return NodeGraph.this.getClipboardText(); }
        @Override public void setClipboardText(String text) { NodeGraph.this.setClipboardText(text); }
        @Override public int findPreviousWordBoundary(String text, int fromPosition) {
            return ParameterTextEditorController.findPreviousWordBoundary(text, fromPosition);
        }
    });
    private final InlineFieldRenderer inlineFieldRenderer = new InlineFieldRenderer(new InlineFieldRenderer.Host() {
        @Override public int cameraX() { return viewport.getCameraX(); }
        @Override public int cameraY() { return viewport.getCameraY(); }
        @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
        @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
        @Override public int selectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public float textFieldHighlightProgress(Object key, boolean hovered, boolean active) {
            return getTextFieldHighlightProgress(key, hovered, active);
        }
        @Override public UIStyleHelper.FieldPalette nodeInputPalette(boolean isOverSidebar, int accentColor,
                                                                     float progress, boolean active, boolean disabled) {
            return getNodeInputPalette(isOverSidebar, accentColor, progress, active, disabled);
        }
        @Override public UIStyleHelper.FieldPalette lowDetailAwareFieldPalette(int backgroundColor, int borderColor,
                                                                               int innerBorderColor, int textColor,
                                                                               int placeholderColor, boolean isOverSidebar) {
            return getLowDetailAwareFieldPalette(backgroundColor, borderColor, innerBorderColor, textColor,
                placeholderColor, isOverSidebar);
        }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, Component text,
                                           int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
            return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
        }
        @Override public Set<String> collectRuntimeVariableNames(Node node) {
            return NodeGraph.this.collectRuntimeVariableNames(node);
        }
        @Override public boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames) {
            return NodeGraph.this.shouldBuildInlineExpressionRender(rawText, variableNames);
        }
        @Override public boolean shouldRenderNodeText() { return NodeGraph.this.shouldRenderNodeText(); }
        @Override public boolean isCompactViewportMode() { return viewport.isCompactViewportMode(); }
        @Override public boolean isScreenCoordinateCaptureActiveFor(Node node) {
            return NodeGraph.this.isScreenCoordinateCaptureActiveFor(node);
        }
        @Override public int screenCoordinatePreviewX() { return screenCoordinateCapture.getPreviewX(); }
        @Override public int screenCoordinatePreviewY() { return screenCoordinateCapture.getPreviewY(); }
        @Override public void renderScreenCoordinatePickerButton(GuiGraphics context, Font textRenderer, Node node,
                                                                  boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderScreenCoordinatePickerButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderDropdownSelectorField(GuiGraphics context, Font textRenderer, Node node,
                                                           boolean isOverSidebar, int mouseX, int mouseY,
                                                           int fieldLeft, int fieldTop, int fieldWidth, int fieldHeight,
                                                           String label, boolean includeValue, String value) {
            nodeRenderer.renderDropdownSelectorField(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                fieldLeft, fieldTop, fieldWidth, fieldHeight, label, includeValue, value);
        }
        @Override public boolean isMoveItemAllAmountValue(String value) {
            return ParameterTextEditorController.isMoveItemAllAmountValue(value);
        }
        @Override public void renderAmountToggle(GuiGraphics context, Node node, boolean amountEnabled,
                                                  boolean isOverSidebar) {
            int toggleLeft = node.getAmountToggleLeft() - viewport.getCameraX();
            int toggleTop = node.getAmountToggleTop() - viewport.getCameraY();
            int toggleWidth = node.getAmountToggleWidth();
            int toggleHeight = node.getAmountToggleHeight();
            nodeControls.renderNodeSliderToggle(context, toggleLeft, toggleTop, toggleWidth, toggleHeight,
                nodeControls.getNodeToggleProgress(amountToggleAnimations, node, amountEnabled), false, isOverSidebar);
        }
        @Override public boolean isPresetSelectorNode(Node node) { return NodeGraph.this.isPresetSelectorNode(node); }
        @Override public void renderPresetSelectorField(GuiGraphics context, Font textRenderer, Node node,
                                                         boolean isOverSidebar, int mouseX, int mouseY) {
            specializedSelectors.renderPresetField(
                context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public boolean isRunPresetDropdownOpenFor(Node node) {
            return specializedSelectors.isRunPresetOpen() && specializedSelectors.getRunPresetNode() == node;
        }
        @Override public String getStopTargetParameterKey(Node node) {
            return NodeGraph.this.getStopTargetParameterKey(node);
        }
        @Override public String getStopTargetPlaceholder(Node node) {
            return NodeGraph.this.getStopTargetPlaceholder(node);
        }
    }, inlineFields);
    private final ScreenCoordinateCaptureController screenCoordinateCapture = new ScreenCoordinateCaptureController(new ScreenCoordinateCaptureController.Host() {
        @Override
        public void stopOtherEditors() {
            stopCoordinateEditing(true);
            stopAmountEditing(true);
            stopStopTargetEditing(true);
            stopVariableEditing(true);
            stopMessageEditing(true);
            stopStickyNoteEditing(true);
            stopParameterEditing(true);
            stopEventNameEditing(true);
        }

        @Override
        public void notifyNodeParametersChanged(Node node) {
            NodeGraph.this.notifyNodeParametersChanged(node);
        }

        @Override
        public void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
    });
    private final StickyNoteController stickyNoteController = new StickyNoteController(new StickyNoteHost() {
        @Override
        public int cameraX() {
            return viewport.getCameraX();
        }

        @Override
        public int cameraY() {
            return viewport.getCameraY();
        }

        @Override
        public int screenToWorldX(int screenX) {
            return NodeGraph.this.screenToWorldX(screenX);
        }

        @Override
        public int screenToWorldY(int screenY) {
            return NodeGraph.this.screenToWorldY(screenY);
        }

        @Override
        public void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }

        @Override
        public int selectedNodeAccentColor() {
            return nodeControls.getSelectedNodeAccentColor();
        }

        @Override
        public void stopOtherEditors() {
            stopCoordinateEditing(true);
            stopAmountEditing(true);
            stopStopTargetEditing(true);
            stopVariableEditing(true);
            stopEventNameEditing(true);
            stopParameterEditing(true);
            stopMessageEditing(true);
        }

        @Override
        public void markWorkspaceDirty() {
            NodeGraph.this.markWorkspaceDirty();
        }

        @Override
        public boolean isWorkspaceDirty() {
            return workspace.isWorkspaceDirty();
        }

        @Override
        public void setWorkspaceDirty(boolean dirty) {
            workspace.setWorkspaceDirty(dirty);
        }

        @Override
        public void invalidateValidation() {
            NodeGraph.this.invalidateValidation();
        }

        @Override
        public void invalidateRenderCaches() {
            NodeGraph.this.invalidateRenderCaches();
        }

        @Override
        public void invalidateHierarchyCache() {
            NodeGraph.this.invalidateHierarchyCache();
        }

        @Override
        public void beginDragOperation() {
            selectionController.beginDragOperation();
        }

        @Override
        public void markDragOperationChanged() {
            selectionController.markDragOperationChanged();
        }

        @Override
        public boolean save() {
            return NodeGraph.this.save();
        }

        @Override
        public boolean isTextShortcutDown(int modifiers) {
            return NodeGraph.this.isTextShortcutDown(modifiers);
        }

        @Override
        public String getClipboardText() {
            return NodeGraph.this.getClipboardText();
        }

        @Override
        public void setClipboardText(String text) {
            NodeGraph.this.setClipboardText(text);
        }
    });
    private final NodeSelectionController selectionController = new NodeSelectionController(
        new NodeSelectionController.Host() {
            @Override public List<Node> nodes() { return nodes; }
            @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
            @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
            @Override public int worldToScreenX(int worldX) { return NodeGraph.this.worldToScreenX(worldX); }
            @Override public int worldToScreenY(int worldY) { return NodeGraph.this.worldToScreenY(worldY); }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public void stopEditorsForNodeDrag() {
                stopCoordinateEditing(true);
                stopAmountEditing(true);
                stopMessageEditing(true);
                stopStickyNoteEditing(true);
                stopParameterEditing(true);
                stopStopTargetEditing(true);
                stopVariableEditing(true);
                stopMessageEditing(true);
            }
            @Override public boolean isUndoCaptureSuppressed() { return suppressUndoCapture; }
            @Override public NodeGraphData captureDragUndoSnapshot() {
                return buildGraphData(new ArrayList<>(nodes), new ArrayList<>(connections), null);
            }
            @Override public void pushUndoSnapshot(NodeGraphData snapshot) { NodeGraph.this.pushUndoSnapshot(snapshot); }
            @Override public void markWorkspaceDirty() { NodeGraph.this.markWorkspaceDirty(); }
            @Override public void invalidateHierarchyCache() { NodeGraph.this.invalidateHierarchyCache(); }
            @Override public boolean isConnectionCutActive() { return connectionController.isConnectionCutActive(); }
            @Override public void updateConnectionCut(int worldX, int worldY) {
                connectionController.updateConnectionCut(worldX, worldY);
            }
            @Override public boolean isStickyNoteResizing() { return stickyNoteController.isResizing(); }
            @Override public void updateStickyNoteResize(int worldX, int worldY) {
                stickyNoteController.updateResize(worldX, worldY);
            }
            @Override public Node finishStickyNoteResize() { return stickyNoteController.finishResize(); }
            @Override public void cancelStickyNoteResize() { stickyNoteController.cancelResize(); }
            @Override public void setInsertionPreviewConnection(NodeConnection connection) {
                connectionController.setInsertionPreviewConnection(connection);
            }
            @Override public NodeConnection findInsertionPreviewConnection(Node node) {
                return connectionController.findInsertionPreviewConnection(node);
            }
            @Override public boolean tryInsertDraggedNodeIntoPreviewConnection(Node node) {
                return connectionController.tryInsertDraggedNodeIntoPreviewConnection(node);
            }
            @Override public boolean insertNodeIntoConnection(Node node, NodeConnection connection) {
                return connectionController.insertNodeIntoConnection(node, connection);
            }
            @Override public void updateConnectionDrag(int worldX, int worldY) {
                connectionController.updateDrag(worldX, worldY, viewport.isDenseViewportMode());
            }
            @Override public void forceClearConnectionDragState() {
                connectionController.forceClearTransientDragState();
            }
            @Override public boolean isDraggingConnection() { return connectionController.isDraggingConnection(); }
            @Override public boolean isConnectionCutActiveForStatus() {
                return connectionController.isConnectionCutActive();
            }
            @Override public Node getNodeAtWorldExcluding(int worldX, int worldY, Node excluded) {
                return NodeGraph.this.getNodeAtWorldExcluding(worldX, worldY, excluded);
            }
            @Override public Node getParentForNode(Node node) { return NodeGraph.this.getParentForNode(node); }
            @Override public boolean intersectsViewport(Node node) { return NodeGraph.this.intersectsViewport(node); }
            @Override public void positionNewNode(Node node, int worldMouseX, int worldMouseY) {
                NodeGraph.this.positionNewNode(node, worldMouseX, worldMouseY);
            }
            @Override public String activeRoutineWorkspaceId() {
                return routineWorkspace.getActiveRoutineWorkspaceId();
            }
            @Override public void assignNewStartNodeNumber(Node node) {
                NodeGraph.this.assignNewStartNodeNumber(node);
            }
            @Override public void bringNodeToFront(Node node) { NodeGraph.this.bringNodeToFront(node); }
            @Override public Node getRootNode(Node node) { return NodeGraph.this.getRootNode(node); }
            @Override public SelectionBounds calculateBounds(Collection<Node> nodesToMeasure) {
                return NodeGraph.this.calculateBounds(nodesToMeasure);
            }
            @Override public void removeNodeCascade(Node node, boolean captureUndo) {
                NodeGraph.this.removeNodeCascade(node, captureUndo);
            }
            @Override public boolean shouldCascadeDelete(Node node) {
                return nodeLifecycle.shouldCascadeDelete(node);
            }
            @Override public void collectNodesForCascade(Node node, List<Node> order, Set<Node> visited) {
                nodeLifecycle.collectNodesForCascade(node, order, visited);
            }
            @Override public int cameraX() { return viewport.getCameraX(); }
            @Override public int sidebarWidthForRendering() { return sidebarWidthForRendering; }
        }
    );
    private final NodeControlController nodeControls = new NodeControlController(
        new NodeControlController.Host() {
            @Override public int cameraX() { return viewport.getCameraX(); }
            @Override public int cameraY() { return viewport.getCameraY(); }
            @Override public int screenToWorldX(int screenX) {
                return NodeGraph.this.screenToWorldX(screenX);
            }
            @Override public int screenToWorldY(int screenY) {
                return NodeGraph.this.screenToWorldY(screenY);
            }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public boolean compactViewportMode() { return viewport.isCompactViewportMode(); }
            @Override public Node sensorDropTarget() { return selectionController.getSensorDropTarget(); }
            @Override public Node actionDropTarget() { return selectionController.getActionDropTarget(); }
            @Override public Node parameterDropTarget() { return selectionController.getParameterDropTarget(); }
            @Override public Integer parameterDropSlotIndex() { return selectionController.getParameterDropSlotIndex(); }
            @Override public Node nodeAt(int screenX, int screenY) {
                return NodeGraph.this.getNodeAt(screenX, screenY);
            }
            @Override public int getStartModeButtonWorldX(Node node) {
                return NodeGraph.this.getStartModeButtonWorldX(node);
            }
            @Override public int getStartModeButtonWorldY(Node node) {
                return NodeGraph.this.getStartModeButtonWorldY(node);
            }
            @Override public boolean isPointInsideStartModeButton(
                Node node, int screenX, int screenY
            ) {
                return NodeGraph.this.isPointInsideStartModeButton(node, screenX, screenY);
            }
            @Override public void pushUndoState() { NodeGraph.this.pushUndoState(); }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
            @Override public void stopMessageEditing(boolean commit) {
                NodeGraph.this.stopMessageEditing(commit);
            }
            @Override public void startMessageEditing(Node node, int index) {
                NodeGraph.this.startMessageEditing(node, index);
            }
            @Override public Node messageEditingNode() {
                return inlineFields.getMessageEditingNode();
            }
            @Override public int messageEditingIndex() {
                return inlineFields.getMessageEditingIndex();
            }
            @Override public String translate(String key) { return tr(key); }
            @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
                return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, Component text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, String text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
        }
    );
    private final NodeRenderer nodeRenderer = new NodeRenderer(new NodeRenderer.Host() {
        @Override public int cameraX() { return viewport.getCameraX(); }
        @Override public int cameraY() { return viewport.getCameraY(); }
        @Override public boolean compactViewportMode() { return viewport.isCompactViewportMode(); }
        @Override public boolean intersectsViewport(Node node) { return NodeGraph.this.intersectsViewport(node); }
        @Override public boolean isNodeOverSidebarForRender(Node node, int x, int width) {
            return NodeGraph.this.isNodeOverSidebarForRender(node, x, width);
        }
        @Override public void renderStickyNote(GuiGraphics context, Font textRenderer, Node node, int x, int y,
                                               int width, int height, boolean isOverSidebar) {
            stickyNoteController.render(context, textRenderer, node, x, y, width, height, isOverSidebar);
        }
        @Override public int selectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public int toGrayscale(int color, float brightnessFactor) {
            return nodeControls.toGrayscale(color, brightnessFactor);
        }
        @Override public int adjustColorBrightness(int color, float factor) {
            return nodeControls.adjustColorBrightness(color, factor);
        }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
            return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
        }
        @Override public boolean isComparisonOperator(Node node) { return nodeControls.isComparisonOperator(node); }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public boolean shouldRenderNodeSockets(Node node) { return NodeGraph.this.shouldRenderNodeSockets(node); }
        @Override public Node hoveredSocketNode() { return connectionController.getHoveredSocketNode(); }
        @Override public int hoveredSocketIndex() { return connectionController.getHoveredSocketIndex(); }
        @Override public boolean hoveredSocketInput() { return connectionController.isHoveredSocketInput(); }
        @Override public boolean isSocketActive(Node node, int socketIndex, boolean isInput) {
            return connectionController.isSocketActive(node, socketIndex, isInput);
        }
        @Override public boolean isEditingEventNameField() { return NodeGraph.this.isEditingEventNameField(); }
        @Override public InlineTextEditor eventNameEditor() { return inlineFields.getEventNameEditor(); }
        @Override public Node eventNameEditingNode() { return inlineFields.getEventNameEditingNode(); }
        @Override public void renderPopupEditButton(GuiGraphics context, Font textRenderer, Node node,
                                                    boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public Set<String> collectRuntimeVariableNames(Node node) {
            return NodeGraph.this.collectRuntimeVariableNames(node);
        }
        @Override public boolean shouldRenderNodeText() { return NodeGraph.this.shouldRenderNodeText(); }
        @Override public boolean hoveringStartButton() { return hoveringStartButton; }
        @Override public void renderStartLaunchIcon(GuiGraphics context, StartLaunchMode mode, int centerX,
                                                    int centerY, int color, int nodeTop, int nodeHeight) {
            nodeControls.renderStartLaunchIcon(context, mode, centerX, centerY, color, nodeTop, nodeHeight);
        }
        @Override public void renderStartNodeNumber(GuiGraphics context, Font textRenderer, Node node,
                                                    int x, int y, boolean isOverSidebar) {
            nodeControls.renderStartNodeNumber(context, textRenderer, node, x, y, isOverSidebar);
        }
        @Override public void renderStartModeButton(GuiGraphics context, Node node, int x, int y,
                                                    boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderStartModeButton(context, node, x, y, isOverSidebar, mouseX, mouseY);
        }
        @Override public boolean isEditingParameterField() { return NodeGraph.this.isEditingParameterField(); }
        @Override public Node parameterEditingNode() { return parameterEditor.getNode(); }
        @Override public int parameterEditingIndex() { return parameterEditor.getIndex(); }
        @Override public void updateParameterCaretBlink() { parameterEditor.updateCaretBlink(); }
        @Override public String parameterEditBuffer() { return parameterEditor.getBuffer(); }
        @Override public boolean hasParameterSelection() {
            return parameterEditor.getSelectionStart() >= 0
                && parameterEditor.getSelectionEnd() >= 0
                && parameterEditor.getSelectionStart() != parameterEditor.getSelectionEnd();
        }
        @Override public int parameterSelectionStart() { return parameterEditor.getSelectionStart(); }
        @Override public int parameterSelectionEnd() { return parameterEditor.getSelectionEnd(); }
        @Override public boolean parameterCaretVisible() { return parameterEditor.isCaretVisible(); }
        @Override public int parameterCaretPosition() { return parameterEditor.getCaretPosition(); }
        @Override public boolean shouldShowParameters(Node node) { return NodeGraph.this.shouldShowParameters(node); }
        @Override public int parameterInputHeight() { return PARAMETER_INPUT_HEIGHT; }
        @Override public int parameterInputGap() { return PARAMETER_INPUT_GAP; }
        @Override public int directionModeTabHeight() { return DIRECTION_MODE_TAB_HEIGHT; }
        @Override public int getParameterFieldLeft(Node node) { return nodeControls.getParameterFieldLeft(node); }
        @Override public int getParameterFieldWidth(Node node) { return nodeControls.getParameterFieldWidth(node); }
        @Override public int getParameterFieldHeight() { return nodeControls.getParameterFieldHeight(); }
        @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
        @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
        @Override public float getTextFieldHighlightProgress(Object key, boolean hovered, boolean active) {
            return NodeGraph.this.getTextFieldHighlightProgress(key, hovered, active);
        }
        @Override public UIStyleHelper.FieldPalette getLowDetailAwareFieldPalette(
            int backgroundColor, int borderColor, int innerBorderColor,
            int textColor, int placeholderColor, boolean isOverSidebar
        ) {
            return NodeGraph.this.getLowDetailAwareFieldPalette(
                backgroundColor, borderColor, innerBorderColor,
                textColor, placeholderColor, isOverSidebar);
        }
        @Override public boolean modeDropdownOpenFor(Node node) {
            return modeDropdown.isOpen() && modeDropdown.getNode() == node;
        }
        @Override public boolean isCombinedDirectionNode(Node node) {
            return nodeControls.isCombinedDirectionNode(node);
        }
        @Override public void renderDirectionModeTabs(GuiGraphics context, Font textRenderer, Node node,
                                                      boolean isOverSidebar, int fieldTop, int mouseX, int mouseY) {
            nodeControls.renderDirectionModeTabs(context, textRenderer, node, isOverSidebar, fieldTop, mouseX, mouseY);
        }
        @Override public boolean isCombinedBooleanNode(Node node) {
            return nodeControls.isCombinedBooleanNode(node);
        }
        @Override public void renderBooleanModeTabs(GuiGraphics context, Font textRenderer, Node node,
                                                    boolean isOverSidebar, int fieldTop, int mouseX, int mouseY) {
            nodeControls.renderBooleanModeTabs(context, textRenderer, node, isOverSidebar, fieldTop, mouseX, mouseY);
        }
        @Override public String getParameterLabelText(Node node, NodeParameter parameter, Font textRenderer,
                                                      int maxWidth) {
            return nodeControls.getParameterLabelText(node, parameter, textRenderer, maxWidth);
        }
        @Override public int getParameterValueStartX(Node node, NodeParameter parameter, Font textRenderer) {
            return nodeControls.getParameterValueStartX(node, parameter, textRenderer);
        }
        @Override public boolean isDefaultMouseButtonValue(String value) {
            return NodeGraph.this.isDefaultMouseButtonValue(value);
        }
        @Override public boolean isDefaultHandValue(String value) {
            return NodeGraph.this.isDefaultHandValue(value);
        }
        @Override public boolean isTradeInlinePlaceholder(Node node, NodeParameter parameter, boolean editing) {
            return NodeGraph.this.isTradeInlinePlaceholder(node, parameter, editing);
        }
        @Override public boolean isAnyBlockItemValue(String value) {
            return NodeGraph.this.isAnyBlockItemValue(value);
        }
        @Override public String formatVillagerTradeValue(String rawValue) {
            return nodeControls.formatVillagerTradeValue(rawValue);
        }
        @Override public String formatMouseButtonValue(String value) {
            return NodeGraph.this.formatMouseButtonValue(value);
        }
        @Override public String formatHandValue(String value) { return NodeGraph.this.formatHandValue(value); }
        @Override public String formatAttributeDetectionInlineValue(Node node, NodeParameter parameter, String value) {
            return NodeGraph.this.formatAttributeDetectionInlineValue(node, parameter, value);
        }
        @Override public boolean parameterDropdownOpen() { return parameterDropdown.isOpen(); }
        @Override public Node parameterDropdownNode() { return parameterDropdown.getNode(); }
        @Override public int parameterDropdownIndex() { return parameterDropdown.getIndex(); }
        @Override public void updateParameterDropdown(Node node, int index, Font textRenderer, int fieldX,
                                                      int fieldY, int fieldWidth, int fieldHeight) {
            NodeGraph.this.updateParameterDropdown(node, index, textRenderer, fieldX, fieldY, fieldWidth, fieldHeight);
        }
        @Override public void renderRandomRoundingField(GuiGraphics context, Font textRenderer, Node node,
                                                        boolean isOverSidebar) {
            NodeGraph.this.renderRandomRoundingField(context, textRenderer, node, isOverSidebar);
        }
        @Override public void renderAmountInputField(GuiGraphics context, Font textRenderer, Node node,
                                                     boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderParameterSlot(GuiGraphics context, Font textRenderer, Node node,
                                                  boolean isOverSidebar, int slotIndex) {
            nodeControls.renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
        }
        @Override public String getOperatorSymbol(Node node, boolean negated) {
            return nodeControls.getOperatorSymbol(node, negated);
        }
        @Override public boolean rendersInlineParameters(Node node) {
            return NodeGraph.this.rendersInlineParameters(node);
        }
        @Override public void renderTemplateNode(GuiGraphics context, Font textRenderer, Node node,
                                                 boolean isOverSidebar, int mouseX, int mouseY) {
            templateNodeRenderer.render(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node,
                                                         boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderSchematicDropdownField(GuiGraphics context, Font textRenderer, Node node,
                                                           boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderSchematicDropdownField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderVariableInputField(GuiGraphics context, Font textRenderer, Node node,
                                                       boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderVariableInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderCoordinateInputFields(GuiGraphics context, Font textRenderer, Node node,
                                                          boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderCoordinateInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderMessageInputFields(GuiGraphics context, Font textRenderer, Node node,
                                                       boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderMessageInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderMessageScopeToggle(GuiGraphics context, Font textRenderer, Node node,
                                                       boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderMessageScopeToggle(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderMessageButtons(GuiGraphics context, Font textRenderer, Node node,
                                                   boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderMessageButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderBooleanOperatorButtons(GuiGraphics context, Font textRenderer, Node node,
                                                           boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderBooleanOperatorButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderBookTextInput(GuiGraphics context, Font textRenderer, Node node,
                                                  boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderBookTextInput(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderSchematicDropdownList(GuiGraphics context, Font textRenderer, Node node,
                                                          boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderSchematicDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public boolean isPresetSelectorNode(Node node) {
            return NodeGraph.this.isPresetSelectorNode(node);
        }
        @Override public void renderRunPresetDropdownList(GuiGraphics context, Font textRenderer, Node node,
                                                          boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderBooleanToggleButton(GuiGraphics context, Font textRenderer, Node node,
                                                        boolean isOverSidebar, int mouseX, int mouseY) {
            nodeControls.renderBooleanToggleButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderSensorSlot(GuiGraphics context, Font textRenderer, Node node,
                                               boolean isOverSidebar) {
            nodeControls.renderSensorSlot(context, textRenderer, node, isOverSidebar);
        }
        @Override public void renderActionSlot(GuiGraphics context, Font textRenderer, Node node,
                                               boolean isOverSidebar) {
            nodeControls.renderActionSlot(context, textRenderer, node, isOverSidebar);
        }
        @Override public void renderRuntimeScopeButton(GuiGraphics context, Node node, boolean isOverSidebar,
                                                       int mouseX, int mouseY) {
            nodeControls.renderRuntimeScopeButton(context, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public boolean hasRunPresetSelection(Node node) {
            return NodeGraph.this.hasRunPresetSelection(node);
        }
        @Override public void renderRunPresetOpenButton(GuiGraphics context, Font textRenderer, Node node,
                                                        boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderRunPresetOpenButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    });
    private final TemplateNodeRenderer templateNodeRenderer = new TemplateNodeRenderer(new TemplateNodeRenderer.Host() {
        @Override public int cameraX() { return viewport.getCameraX(); }
        @Override public int cameraY() { return viewport.getCameraY(); }
        @Override public int adjustColorBrightness(int color, float factor) {
            return nodeControls.adjustColorBrightness(color, factor);
        }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
            NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
        }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
            return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
        }
        @Override public NodeGraphData.CustomNodeDefinition getTemplateDefinition(Node node) {
            return NodeGraph.this.getTemplateDefinition(node);
        }
        @Override public String getSelectedPresetName(Node node) { return NodeGraph.this.getSelectedPresetName(node); }
        @Override public NodeGraphData getPresetPreviewGraphData(Node node) {
            return NodeGraph.this.getPresetPreviewGraphData(node);
        }
        @Override public void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node,
                                                         boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        @Override public void renderRunPresetDropdownList(GuiGraphics context, Font textRenderer, Node node,
                                                          boolean isOverSidebar, int mouseX, int mouseY) {
            NodeGraph.this.renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    });
    private final ParameterTextEditorController parameterEditor = new ParameterTextEditorController(
        new ParameterTextEditorController.Host() {
            @Override public boolean canEditInlineParameterFields(Node node) {
                return NodeGraph.this.canEditInlineParameterFields(node);
            }
            @Override public void closeModeDropdown() { NodeGraph.this.closeModeDropdown(); }
            @Override public void closeSchematicDropdown() { NodeGraph.this.closeSchematicDropdown(); }
            @Override public void closeRunPresetDropdown() { NodeGraph.this.closeRunPresetDropdown(); }
            @Override public void closeRandomRoundingDropdown() { NodeGraph.this.closeRandomRoundingDropdown(); }
            @Override public void closeParameterDropdown() { parameterDropdown.close(); }
            @Override public void clearParameterDropdownSuppression() { parameterDropdown.clearSuppression(); }
            @Override public void stopCoordinateEditing(boolean commit) { NodeGraph.this.stopCoordinateEditing(commit); }
            @Override public void stopAmountEditing(boolean commit) { NodeGraph.this.stopAmountEditing(commit); }
            @Override public void stopStopTargetEditing(boolean commit) { NodeGraph.this.stopStopTargetEditing(commit); }
            @Override public void stopMessageEditing(boolean commit) { NodeGraph.this.stopMessageEditing(commit); }
            @Override public void stopVariableEditing(boolean commit) { NodeGraph.this.stopVariableEditing(commit); }
            @Override public void stopEventNameEditing(boolean commit) { NodeGraph.this.stopEventNameEditing(commit); }
            @Override public void stopStickyNoteEditing(boolean commit) { NodeGraph.this.stopStickyNoteEditing(commit); }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
            @Override public void updateParameterFieldContentWidth(
                Node node, Font renderer, int index, String value
            ) {
                NodeGraph.this.updateParameterFieldContentWidth(node, renderer, index, value);
            }
            @Override public Font getClientTextRenderer() { return NodeGraph.this.getClientTextRenderer(); }
            @Override public boolean isTextShortcutDown(int modifiers) {
                return NodeGraph.this.isTextShortcutDown(modifiers);
            }
            @Override public String getClipboardText() { return NodeGraph.this.getClipboardText(); }
            @Override public void setClipboardText(String text) { NodeGraph.this.setClipboardText(text); }
        }
    );
    private final ParameterDropdownController parameterDropdown = new ParameterDropdownController(
        new ParameterDropdownController.Host() {
            @Override public boolean isEditingParameterField() {
                return NodeGraph.this.isEditingParameterField();
            }
            @Override public Node parameterEditingNode() { return parameterEditor.getNode(); }
            @Override public int parameterEditingIndex() { return parameterEditor.getIndex(); }
            @Override public String parameterEditBuffer() { return parameterEditor.getBuffer(); }
            @Override public int parameterCaretPosition() { return parameterEditor.getCaretPosition(); }
            @Override public void replaceParameterEditBuffer(String value, int caretPosition) {
                parameterEditor.replaceBuffer(value, caretPosition);
            }
            @Override public void updateParameterFieldContentWidth(
                Node node, Font textRenderer, int index, String value
            ) {
                NodeGraph.this.updateParameterFieldContentWidth(node, textRenderer, index, value);
            }
            @Override public void refreshStateParameterPreview() {
                parameterEditor.refreshStatePreview();
            }
            @Override public boolean applyParameterEdit() {
                return parameterEditor.apply();
            }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
            @Override public void closeModeDropdown() { NodeGraph.this.closeModeDropdown(); }
            @Override public void closeSchematicDropdown() { NodeGraph.this.closeSchematicDropdown(); }
            @Override public void closeRunPresetDropdown() { NodeGraph.this.closeRunPresetDropdown(); }
            @Override public void closeRandomRoundingDropdown() {
                NodeGraph.this.closeRandomRoundingDropdown();
            }
            @Override public void stopParameterEditing(boolean commit) {
                NodeGraph.this.stopParameterEditing(commit);
            }
            @Override public int parameterFieldLeft(Node node) {
                return nodeControls.getParameterFieldLeft(node);
            }
            @Override public int inlineParameterFieldTop(Node node, int index) {
                return getInlineParameterFieldTop(node, index);
            }
            @Override public int parameterFieldWidth(Node node) {
                return nodeControls.getParameterFieldWidth(node);
            }
            @Override public int parameterFieldHeight() { return nodeControls.getParameterFieldHeight(); }
            @Override public int cameraX() { return viewport.getCameraX(); }
            @Override public int cameraY() { return viewport.getCameraY(); }
            @Override public int screenToUiX(int screenX) {
                return NodeGraph.this.screenToUiX(screenX);
            }
            @Override public int screenToUiY(int screenY) {
                return NodeGraph.this.screenToUiY(screenY);
            }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public Font getClientTextRenderer() {
                return NodeGraph.this.getClientTextRenderer();
            }
            @Override public float dropdownAnimationProgress(AnimatedValue animation, boolean open) {
                return getDropdownAnimationProgress(animation, open);
            }
            @Override public int selectedNodeAccentColor() {
                return nodeControls.getSelectedNodeAccentColor();
            }
            @Override public void enableDropdownScissor(
                GuiGraphics context, int x, int y, int width, int height
            ) {
                NodeGraph.this.enableDropdownScissor(context, x, y, width, height);
            }
            @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
                return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, Component text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
        }
    );
    private final SpecializedSelectorController specializedSelectors = new SpecializedSelectorController(
        new SpecializedSelectorController.Host() {
            @Override public int screenToWorldX(int screenX) { return NodeGraph.this.screenToWorldX(screenX); }
            @Override public int screenToWorldY(int screenY) { return NodeGraph.this.screenToWorldY(screenY); }
            @Override public int cameraX() { return viewport.getCameraX(); }
            @Override public int cameraY() { return viewport.getCameraY(); }
            @Override public int guiScaledHeight() {
                return Minecraft.getInstance().getWindow().getGuiScaledHeight();
            }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public Font clientTextRenderer() { return getClientTextRenderer(); }
            @Override public boolean compactViewportMode() { return viewport.isCompactViewportMode(); }
            @Override public boolean shouldRenderNodeText() {
                return NodeGraph.this.shouldRenderNodeText();
            }
            @Override public int selectedNodeAccentColor() {
                return nodeControls.getSelectedNodeAccentColor();
            }
            @Override public int toGrayscale(int color, float brightnessFactor) {
                return nodeControls.toGrayscale(color, brightnessFactor);
            }
            @Override public String translate(String key) { return tr(key); }
            @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
                return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, Component text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
            @Override public List<String> loadSchematicOptions() {
                return SchematicRepository.loadSchematicOptions();
            }
            @Override public boolean schematicExists(String name) {
                return schematicExistsInRoots(name);
            }
            @Override public boolean isPresetSelectorNode(Node node) {
                return NodeGraph.this.isPresetSelectorNode(node);
            }
            @Override public String stopTargetParameterKey(Node node) {
                return getStopTargetParameterKey(node);
            }
            @Override public boolean isEditingStopTargetField() {
                return NodeGraph.this.isEditingStopTargetField();
            }
            @Override public Node stopTargetEditingNode() {
                return inlineFields.getStopTargetEditingNode();
            }
            @Override public InlineTextEditor stopTargetEditor() {
                return inlineFields.getStopTargetEditor();
            }
            @Override public void prepareRunPresetOpen(Node node) {
                focusSelectedNode(node);
                stopStopTargetEditing(true);
            }
            @Override public void applySchematicSelection(Node node, String value) {
                NodeGraph.this.applySchematicSelection(node, value);
            }
            @Override public void applyRunPresetSelection(Node node, String value) {
                NodeGraph.this.applyRunPresetSelection(node, value);
            }
        }
    );
    private final RandomRoundingController randomRounding = new RandomRoundingController(
        new RandomRoundingController.Host() {
            @Override public int cameraX() { return viewport.getCameraX(); }
            @Override public int cameraY() { return viewport.getCameraY(); }
            @Override public int screenToWorldX(int screenX) {
                return NodeGraph.this.screenToWorldX(screenX);
            }
            @Override public int screenToWorldY(int screenY) {
                return NodeGraph.this.screenToWorldY(screenY);
            }
            @Override public int guiScaledHeight() {
                return Minecraft.getInstance().getWindow().getGuiScaledHeight();
            }
            @Override public Font clientTextRenderer() { return getClientTextRenderer(); }
            @Override public float zoomScale() { return getZoomScale(); }
            @Override public boolean compactViewportMode() { return viewport.isCompactViewportMode(); }
            @Override public boolean shouldRenderNodeText() {
                return NodeGraph.this.shouldRenderNodeText();
            }
            @Override public int selectedNodeAccentColor() {
                return nodeControls.getSelectedNodeAccentColor();
            }
            @Override public String translate(String key) { return tr(key); }
            @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) {
                return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth);
            }
            @Override public UIStyleHelper.FieldPalette nodeInputPalette(
                boolean isOverSidebar, int accentColor, float progress, boolean active, boolean disabled
            ) {
                return getNodeInputPalette(
                    isOverSidebar, accentColor, progress, active, disabled
                );
            }
            @Override public UIStyleHelper.FieldPalette lowDetailAwareFieldPalette(
                int backgroundColor, int borderColor, int innerBorderColor, int textColor,
                int placeholderColor, boolean isOverSidebar
            ) {
                return getLowDetailAwareFieldPalette(
                    backgroundColor, borderColor, innerBorderColor, textColor,
                    placeholderColor, isOverSidebar
                );
            }
            @Override public void drawNodeText(
                GuiGraphics context, Font renderer, Component text, int x, int y, int color
            ) {
                NodeGraph.this.drawNodeText(context, renderer, text, x, y, color);
            }
            @Override public void renderToggle(
                GuiGraphics context, Node node, int left, int top, int width, int height,
                boolean enabled, boolean isOverSidebar
            ) {
                nodeControls.renderNodeSliderToggle(
                    context, left, top, width, height,
                    nodeControls.getNodeToggleProgress(randomRoundingToggleAnimations, node, enabled),
                    false, isOverSidebar
                );
            }
            @Override public float dropdownAnimationProgress(
                AnimatedValue animation, boolean open
            ) {
                return getDropdownAnimationProgress(animation, open);
            }
            @Override public void animateToggle(Node node, boolean enabled) {
                nodeControls.getNodeToggleAnimation(randomRoundingToggleAnimations, node, enabled)
                    .animateTo(
                        enabled ? 1f : 0f,
                        UITheme.TRANSITION_ANIM_MS,
                        AnimationHelper::easeInOutCubic
                    );
            }
            @Override public void stopParameterEditing() {
                NodeGraph.this.stopParameterEditing(true);
            }
            @Override public void notifyNodeParametersChanged(Node node) {
                NodeGraph.this.notifyNodeParametersChanged(node);
            }
        }
    );
    private final AnimatedValue modeDropdownAnimation = AnimatedValue.forHover();
    private final DropdownController.Host dropdownHost = new DropdownController.Host() {
        @Override public float getZoomScale() { return NodeGraph.this.getZoomScale(); }
        @Override public int screenToUiX(int screenX) { return NodeGraph.this.screenToUiX(screenX); }
        @Override public int screenToUiY(int screenY) { return NodeGraph.this.screenToUiY(screenY); }
        @Override public int getDropdownRowHeight() { return NodeGraph.this.getDropdownRowHeight(); }
        @Override public int getSelectedNodeAccentColor() { return nodeControls.getSelectedNodeAccentColor(); }
        @Override public Font getTextRenderer() { return getClientTextRenderer(); }
        @Override public int getGuiScaledHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
        @Override public String trimTextToWidth(String text, Font renderer, int maxWidth) { return NodeGraph.this.trimTextToWidth(text, renderer, maxWidth); }
        @Override public void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) { NodeGraph.this.drawNodeText(context, renderer, text, x, y, color); }
        @Override public void enableDropdownScissor(GuiGraphics context, int x, int y, int width, int height) { NodeGraph.this.enableDropdownScissor(context, x, y, width, height); }
        @Override public float dropdownAnimationProgress(AnimatedValue animation, boolean open) { return getDropdownAnimationProgress(animation, open); }
    };
    private final DropdownController<com.pathmind.nodes.NodeMode> modeDropdown = new DropdownController<>(
        dropdownHost,
        modeDropdownAnimation,
        PARAMETER_DROPDOWN_MAX_ROWS,
        () -> tr("pathmind.dropdown.noModes"),
        this::computeModeDropdownAnchor,
        this::getModeDropdownOptions,
        (node, mode) -> {
            node.setMode(mode);
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
        }
    );
    private static final int PARAMETER_DROPDOWN_MAX_ROWS = 8;
    private static final int SCHEMATIC_DROPDOWN_ROW_HEIGHT = 16;
    private int nextStartNodeNumber = 1;
    private ClipboardSnapshot clipboardNodeSnapshot = null;
    private final Deque<NodeGraphData> undoStack = new ArrayDeque<>();
    private final Deque<NodeGraphData> redoStack = new ArrayDeque<>();
    private boolean suppressUndoCapture = false;
    private static final int MAX_HISTORY = 50;


    public enum ZoomLevel {
        FOCUSED(1.0f, true),
        OVERVIEW(0.35f, true),
        DISTANT(0.18f, true);

        private final float scale;
        private final boolean showText;

        ZoomLevel(float scale, boolean showText) {
            this.scale = scale;
            this.showText = showText;
        }

        public float getScale() {
            return scale;
        }

        public boolean shouldShowText() {
            return showText;
        }
    }

    static final class ClipboardSnapshot {
        final NodeGraphData data;
        final List<String> selectionIds;
        final int anchorX;
        final int anchorY;

        ClipboardSnapshot(NodeGraphData data, List<String> selectionIds, int anchorX, int anchorY) {
            this.data = data;
            this.selectionIds = selectionIds;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }
    }

    static final class SelectionBounds {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;

        SelectionBounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    public ZoomLevel getZoomLevel() {
        return viewport.getZoomLevel();
    }

    public boolean isZoomedOut() {
        return viewport.isZoomedOut();
    }

    public void setZoomLevel(ZoomLevel newLevel, int anchorScreenX, int anchorScreenY) {
        viewport.setZoomLevel(newLevel, anchorScreenX, anchorScreenY);
    }

    public float getZoomScale() {
        return viewport.getZoomScale();
    }

    private boolean shouldRenderNodeText() {
        return viewport.shouldRenderNodeText();
    }

    public boolean canZoomIn() {
        return viewport.canZoomIn();
    }

    public boolean canZoomOut() {
        return viewport.canZoomOut();
    }

    public void zoomIn(int anchorScreenX, int anchorScreenY) {
        viewport.zoomIn(anchorScreenX, anchorScreenY);
    }

    public void zoomOut(int anchorScreenX, int anchorScreenY) {
        viewport.zoomOut(anchorScreenX, anchorScreenY);
    }

    public boolean isDefaultZoom() {
        return viewport.isDefaultZoom();
    }

    public void zoomByScroll(double scrollAmount, int anchorScreenX, int anchorScreenY) {
        viewport.zoomByScroll(scrollAmount, anchorScreenX, anchorScreenY);
    }

    public NodeGraph() {
        this.nodes = new ArrayList<>();
        this.connections = new ArrayList<>();
        this.cachedRootNodes = new ArrayList<>();
        this.cachedHierarchyBounds = new HashMap<>();
        this.cachedHierarchyNodeCounts = new HashMap<>();
        // Add preset nodes similar to Blender's shader editor
        // Will be initialized with proper centering when screen dimensions are available
    }
    
    public void initializeWithScreenDimensions(int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        // Clear any existing nodes
        nodes.clear();
        connections.clear();
        invalidateRenderCaches();
        clearTransientGraphState();
        nextStartNodeNumber = 1;
        
        // Calculate workspace area
        int workspaceStartX = sidebarWidth;
        int workspaceStartY = titleBarHeight;
        int workspaceWidth = screenWidth - sidebarWidth;
        int workspaceHeight = screenHeight - titleBarHeight;
        
        // Center nodes in the workspace
        int centerX = workspaceStartX + workspaceWidth / 2;
        int centerY = workspaceStartY + workspaceHeight / 2;
        
        // Position the initial start node centered in the workspace.
        Node startNode = new Node(NodeType.START, centerX, centerY - 50);
        assignNewStartNodeNumber(startNode);
        nodes.add(startNode);
        restoreSessionViewportState();
        invalidateValidation();
    }

    void assignNewStartNodeNumber(Node node) {
        nodeLifecycle.assignNewStartNodeNumber(node);
    }

    public void addNode(Node node) {
        nodeLifecycle.addNode(node);
    }

    public void removeNode(Node node) {
        nodeLifecycle.removeNode(node);
    }

    public Node getNodeAt(int x, int y) {
        int worldX = screenToWorldX(x);
        int worldY = screenToWorldY(y);
        return getNodeAtWorld(worldX, worldY);
    }

    private Node getNodeAtWorld(int worldX, int worldY) {
        long startNanos = System.nanoTime();
        List<Node> visibleRoots = getVisibleRootsForViewport();
        int rootCount = visibleRoots.size();
        Node hit = null;
        for (int i = visibleRoots.size() - 1; i >= 0; i--) {
            Node root = visibleRoots.get(i);
            hit = findNodeInHierarchyAt(root, worldX, worldY);
            if (hit != null) {
                break;
            }
        }
        long duration = System.nanoTime() - startNanos;
        profilerHitTestTotalNanos += duration;
        profilerHitTestCallCount++;
        profilerHitTestTotalRoots += rootCount;
        profilerHitTestAvgMs = (profilerHitTestTotalNanos / (double) profilerHitTestCallCount) / 1_000_000.0;
        profilerHitTestAvgRoots = profilerHitTestTotalRoots / (double) profilerHitTestCallCount;
        return hit;
    }

    private Node findNodeInHierarchyAt(Node node, int worldX, int worldY) {
        if (node == null) {
            return null;
        }

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            keys.sort(Collections.reverseOrder());
            for (Integer key : keys) {
                Node hit = findNodeInHierarchyAt(parameterMap.get(key), worldX, worldY);
                if (hit != null) {
                    return hit;
                }
            }
        }

        Node sensorChild = node.getAttachedSensor();
        Node hit = findNodeInHierarchyAt(sensorChild, worldX, worldY);
        if (hit != null) {
            return hit;
        }

        Node actionChild = node.getAttachedActionNode();
        hit = findNodeInHierarchyAt(actionChild, worldX, worldY);
        if (hit != null) {
            return hit;
        }

        if (isNodeHitAt(node, worldX, worldY)) {
            return node;
        }

        return null;
    }

    private boolean isNodeHitAt(Node node, int worldX, int worldY) {
        if (node == null) {
            return false;
        }
        if (node.containsPoint(worldX, worldY)) {
            return true;
        }
        return node.isSelected() && stickyNoteController.getResizeCornerAtWorld(node, worldX, worldY) != null;
    }

    public void selectNode(Node node) {
        selectionController.selectNode(node);
    }

    public void selectNodes(Collection<Node> nodesToSelect) {
        selectionController.selectNodes(nodesToSelect);
    }

    public Set<Node> getSelectedNodes() {
        return selectionController.getSelectedNodes();
    }

    public void setSelectionDeletionPreviewActive(boolean active) {
        selectionController.setSelectionDeletionPreviewActive(active);
    }

    public boolean isNodeSelected(Node node) {
        return selectionController.isNodeSelected(node);
    }

    public void focusSelectedNode(Node node) {
        selectionController.focusSelectedNode(node);
    }

    public void toggleNodeInSelection(Node node) {
        selectionController.toggleNodeInSelection(node);
    }

    SelectionBounds calculateBounds(Collection<Node> nodesToMeasure) {
        return NodeGraphHierarchySupport.calculateBounds(nodesToMeasure);
    }

    private SelectionBounds calculateHierarchyBounds(Node root) {
        if (root == null) {
            return null;
        }
        List<Node> hierarchyNodes = new ArrayList<>();
        collectHierarchyNodes(root, hierarchyNodes, new HashSet<>());
        return calculateBounds(hierarchyNodes);
    }

    private void invalidateHierarchyCache() {
        hierarchyGeometryDirty = true;
        viewport.invalidateVisibleRoots();
    }

    private void invalidateConnectionIndex() {
        connectionController.invalidateConnectionIndex();
    }

    private void invalidateRenderCaches() {
        invalidateHierarchyCache();
        invalidateConnectionIndex();
        trimmedTextCache.clear();
        nodeControls.clearParameterLayoutCache();
        runtimeVariableNamesFrameCache.clear();
        cachedBaseRuntimeVariableNames = null;
    }

    private void rebuildHierarchyCacheIfNeeded() {
        NodeGraphHierarchySupport.rebuildHierarchyCacheIfNeeded(
            this,
            hierarchyGeometryDirty,
            nodes,
            cachedRootNodes,
            cachedHierarchyBounds,
            cachedHierarchyNodeCounts
        );
        hierarchyGeometryDirty = false;
    }

    private List<Node> getVisibleRootsForViewport() {
        return viewport.getVisibleRootsForViewport();
    }

    void collectHierarchyNodes(Node node, List<Node> collected, Set<Node> visited) {
        if (node == null || !visited.add(node)) {
            return;
        }
        collected.add(node);
        collectHierarchyNodes(node.getAttachedActionNode(), collected, visited);
        collectHierarchyNodes(node.getAttachedSensor(), collected, visited);
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                collectHierarchyNodes(parameter, collected, visited);
            }
        }
    }

    private int getViewportWorldWidth() {
        return viewport.getViewportWorldWidth();
    }

    private int getViewportWorldHeight() {
        return viewport.getViewportWorldHeight();
    }

    private boolean intersectsViewport(SelectionBounds bounds) {
        return viewport.intersectsViewport(bounds);
    }

    private boolean intersectsViewport(SelectionBounds bounds, int viewportWidth, int viewportHeight) {
        return viewport.intersectsViewport(bounds, viewportWidth, viewportHeight);
    }

    private boolean intersectsViewport(Node node) {
        return viewport.intersectsViewport(node);
    }

    private void clearSelection() {
        selectionController.clearSelection();
    }

    private void pruneSelectionToCurrentNodes() {
        selectionController.pruneSelectionToCurrentNodes();
    }

    private void clearTransientGraphState() {
        selectionController.clearTransientState();
        connectionController.clearGraphState();
        hoveringStartButton = false;
        hoveredStartNode = null;
        startModeDropdown.close();
    }

    public void beginSelectionBox(int screenX, int screenY) {
        selectionController.beginSelectionBox(screenX, screenY);
    }

    public void updateSelectionBox(int screenX, int screenY) {
        selectionController.updateSelectionBox(screenX, screenY);
    }

    public void completeSelectionBox() {
        selectionController.completeSelectionBox();
    }

    public boolean isSelectionBoxActive() {
        return selectionController.isSelectionBoxActive();
    }

    public void resetDropTargets() {
        selectionController.resetDropTargets();
    }

    void bringNodeToFront(Node node) {
        NodeGraphHierarchySupport.bringNodeToFront(this, node, nodes);
    }

    Node getRootNode(Node node) {
        Node current = node;
        Node parent;
        while ((parent = getParentForNode(current)) != null) {
            current = parent;
        }
        return current;
    }

    void collectHierarchy(Node node, List<Node> result, Set<Node> visited) {
        NodeGraphHierarchySupport.collectHierarchy(node, result, visited);
    }

    public Node getSelectedNode() {
        return selectionController.getSelectedNode();
    }

    public boolean copySelectedNodeToClipboard() {
        pruneSelectionToCurrentNodes();
        Set<Node> selectedNodes = selectionController.getSelectedNodes();
        if (selectedNodes.isEmpty()) {
            return false;
        }
        ClipboardSnapshot snapshot = createClipboardSnapshot(selectedNodes);
        if (snapshot == null) {
            return false;
        }
        clipboardNodeSnapshot = snapshot;
        return true;
    }

    public boolean cutSelectedNodeToClipboard() {
        pruneSelectionToCurrentNodes();
        Set<Node> selectedNodes = selectionController.getSelectedNodes();
        if (selectedNodes.isEmpty()) {
            return false;
        }
        ClipboardSnapshot snapshot = createClipboardSnapshot(selectedNodes);
        if (snapshot == null) {
            return false;
        }
        clipboardNodeSnapshot = snapshot;
        return deleteSelectedNode();
    }

    public Node duplicateSelectedNode() {
        pruneSelectionToCurrentNodes();
        Set<Node> selectedNodes = selectionController.getSelectedNodes();
        if (selectedNodes.isEmpty()) {
            return null;
        }
        ClipboardSnapshot snapshot = createClipboardSnapshot(selectedNodes);
        if (snapshot == null) {
            return null;
        }
        clipboardNodeSnapshot = snapshot;
        pushUndoState();
        SelectionBounds bounds = calculateBounds(selectedNodes);
        int anchorX = bounds != null ? bounds.minX : snapshot.anchorX;
        int anchorY = bounds != null ? bounds.minY : snapshot.anchorY;
        return instantiateClipboardSnapshot(snapshot, anchorX + DUPLICATE_OFFSET_X, anchorY + DUPLICATE_OFFSET_Y);
    }

    public Node pasteClipboardNode() {
        pruneSelectionToCurrentNodes();
        Set<Node> selectedNodes = selectionController.getSelectedNodes();
        if (clipboardNodeSnapshot == null) {
            return null;
        }
        SelectionBounds bounds = calculateBounds(selectedNodes);
        int baseX = bounds != null ? bounds.minX : clipboardNodeSnapshot.anchorX;
        int baseY = bounds != null ? bounds.minY : clipboardNodeSnapshot.anchorY;
        pushUndoState();
        return instantiateClipboardSnapshot(clipboardNodeSnapshot, baseX + DUPLICATE_OFFSET_X, baseY + DUPLICATE_OFFSET_Y);
    }

    public boolean deleteSelectedNode() {
        return nodeLifecycle.deleteSelectedNode();
    }

    private boolean isNodeEligibleForConnectionInsertion(Node node) {
        return connectionController.isNodeEligibleForConnectionInsertion(node);
    }

    private NodeConnection findInsertionPreviewConnection(Node node) {
        return connectionController.findInsertionPreviewConnection(node);
    }

    private boolean tryInsertDraggedNodeIntoPreviewConnection(Node node) {
        return connectionController.tryInsertDraggedNodeIntoPreviewConnection(node);
    }

    private boolean insertNodeIntoConnection(Node node, NodeConnection connection) {
        return connectionController.insertNodeIntoConnection(node, connection);
    }

    private ClipboardSnapshot createClipboardSnapshot(Collection<Node> selection) {
        return NodeGraphClipboardSupport.createClipboardSnapshot(this, selection, connections);
    }

    private Node instantiateClipboardSnapshot(ClipboardSnapshot snapshot, int targetAnchorX, int targetAnchorY) {
        return NodeGraphClipboardSupport.instantiateClipboardSnapshot(this, snapshot, targetAnchorX, targetAnchorY, nodes, connections);
    }

    NodeGraphData buildGraphData(Collection<Node> nodeCollection, Collection<NodeConnection> connectionCollection, Set<Node> allowedNodes) {
        return NodeGraphClipboardSupport.buildGraphData(nodeCollection, connectionCollection, allowedNodes);
    }

    private void pushUndoState() {
        NodeGraphHistorySupport.pushUndoState(this, nodes, connections, undoStack, redoStack, suppressUndoCapture, MAX_HISTORY);
    }

    private void pushUndoSnapshot(NodeGraphData snapshot) {
        NodeGraphHistorySupport.pushUndoSnapshot(snapshot, undoStack, redoStack, suppressUndoCapture, MAX_HISTORY);
    }

    void restoreFromSnapshot(NodeGraphData data) {
        if (data == null) {
            return;
        }
        suppressUndoCapture = true;
        applyLoadedData(data);
        suppressUndoCapture = false;
        markWorkspaceDirty();
    }

    public boolean undo() {
        return NodeGraphHistorySupport.undo(this, nodes, connections, undoStack, redoStack);
    }

    public boolean redo() {
        return NodeGraphHistorySupport.redo(this, nodes, connections, undoStack, redoStack);
    }

    public void startDragging(Node node, int mouseX, int mouseY) {
        selectionController.startDragging(node, mouseX, mouseY);
    }
    
    public void startDraggingConnection(Node node, int socketIndex, boolean isOutput, int mouseX, int mouseY) {
        connectionController.startDraggingConnection(node, socketIndex, isOutput, screenToWorldX(mouseX), screenToWorldY(mouseY));
    }

    private void stopConnectionEditors() {
        stopCoordinateEditing(true);
        stopAmountEditing(true);
        stopStopTargetEditing(true);
        stopVariableEditing(true);
        stopStickyNoteEditing(true);
        stopVariableEditing(true);
        stopEventNameEditing(true);
        stopParameterEditing(true);
        stopMessageEditing(true);
    }

    public void updateDrag(int mouseX, int mouseY) {
        selectionController.updateDrag(mouseX, mouseY);
    }

    public void previewSidebarDrag(NodeType nodeType, int worldMouseX, int worldMouseY) {
        selectionController.previewSidebarDrag(nodeType, worldMouseX, worldMouseY);
    }

    public void previewSidebarDrag(Node candidate, int worldMouseX, int worldMouseY) {
        selectionController.previewSidebarDrag(candidate, worldMouseX, worldMouseY);
    }

    public int[] getSidebarDragPreviewPosition(Node candidate, int worldMouseX, int worldMouseY) {
        return selectionController.getSidebarDragPreviewPosition(candidate, worldMouseX, worldMouseY);
    }

    private Node getNodeAtWorldExcluding(int worldX, int worldY, Node excluded) {
        rebuildHierarchyCacheIfNeeded();
        Set<Node> processedRoots = new HashSet<>();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            Node root = getRootNode(node);
            if (root == null || processedRoots.contains(root)) {
                continue;
            }
            processedRoots.add(root);
            if (!intersectsViewport(cachedHierarchyBounds.get(root))) {
                continue;
            }
            Node hit = findNodeInHierarchyAtExcluding(root, worldX, worldY, excluded);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private Node findNodeInHierarchyAtExcluding(Node node, int worldX, int worldY, Node excluded) {
        if (node == null) {
            return null;
        }

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            keys.sort(Collections.reverseOrder());
            for (Integer key : keys) {
                Node hit = findNodeInHierarchyAtExcluding(parameterMap.get(key), worldX, worldY, excluded);
                if (hit != null) {
                    return hit;
                }
            }
        }

        Node sensorChild = node.getAttachedSensor();
        Node hit = findNodeInHierarchyAtExcluding(sensorChild, worldX, worldY, excluded);
        if (hit != null) {
            return hit;
        }

        Node actionChild = node.getAttachedActionNode();
        hit = findNodeInHierarchyAtExcluding(actionChild, worldX, worldY, excluded);
        if (hit != null) {
            return hit;
        }

        if (node != excluded && isNodeHitAt(node, worldX, worldY)) {
            return node;
        }

        return null;
    }

    public Node handleSidebarDrop(NodeType nodeType, int worldMouseX, int worldMouseY) {
        return selectionController.handleSidebarDrop(nodeType, worldMouseX, worldMouseY);
    }

    public Node handleSidebarDrop(Node newNode, int worldMouseX, int worldMouseY) {
        return selectionController.handleSidebarDrop(newNode, worldMouseX, worldMouseY);
    }
    
    public void updateMouseHover(int mouseX, int mouseY) {
        long startNanos = System.nanoTime();
        List<Node> visibleRoots = getVisibleRootsForViewport();
        // Reset hover state
        connectionController.clearSocketHover();
        hoveringStartButton = false;
        hoveredStartNode = null;

        // Check for start button hover
        for (Node root : visibleRoots) {
            if (root.getType() == NodeType.START && isMouseOverStartButton(root, mouseX, mouseY)) {
                hoveringStartButton = true;
                hoveredStartNode = root;
                break;
            }
        }
        
        // Don't check for socket hover if we're currently dragging a connection
        if (connectionController.isDraggingConnection()) {
            profilerHoverMs = (System.nanoTime() - startNanos) / 1_000_000.0;
            return;
        }

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);

        boolean socketHovered = connectionController.updateMouseHover(
            worldMouseX, worldMouseY, viewport.isDenseViewportMode(), visibleRoots
        );
        if (socketHovered && viewport.isDenseViewportMode()) {
            return;
        }
        profilerHoverMs = (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    public void stopDragging() {
        selectionController.stopDragging();
    }

    public void forceClearTransientDragState() {
        selectionController.forceClearTransientDragState();
    }

    public void stopDraggingConnection() {
        connectionController.stopDraggingConnection();
    }

    private void addConnectionReplacingConflicts(Node outputNode, Node inputNode, int outputSocket, int inputSocket) {
        connectionController.addConnectionReplacingConflicts(outputNode, inputNode, outputSocket, inputSocket);
    }
    
    public boolean isInSidebar(int mouseX, int sidebarWidth) {
        return mouseX < sidebarWidth;
    }
    
    public boolean isAnyNodeBeingDragged() {
        return selectionController.isAnyNodeBeingDragged();
    }

    private boolean isLowDetailModeEnabled() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        return settings != null && Boolean.TRUE.equals(settings.lowDetailMode);
    }

    public void startPanning(int mouseX, int mouseY) {
        viewport.startPanning(mouseX, mouseY);
    }

    /**
     * Shows the context menu at the specified screen position.
     */
    public void showContextMenu(int screenX, int screenY, com.pathmind.ui.sidebar.Sidebar sidebar, int screenWidth, int screenHeight) {
        contextMenus.showContextMenu(screenX, screenY, sidebar, screenWidth, screenHeight);
    }

    public void showNodeContextMenu(int screenX, int screenY, Node targetNode, int screenWidth, int screenHeight) {
        contextMenus.showNodeContextMenu(screenX, screenY, targetNode, screenWidth, screenHeight);
    }

    /**
     * Closes the context menu if it's open.
     */
    public void closeContextMenu() {
        contextMenus.closeContextMenu();
    }

    public void closeNodeContextMenu() {
        contextMenus.closeNodeContextMenu();
    }

    /**
     * Returns true if the context menu is open.
     */
    public boolean isContextMenuOpen() {
        return contextMenus.isContextMenuOpen();
    }

    public boolean isNodeContextMenuOpen() {
        return contextMenus.isNodeContextMenuOpen();
    }

    public boolean isStartModeDropdownOpen() {
        return startModeDropdown.isOpen();
    }

    /**
     * Updates the context menu hover state.
     */
    public void updateContextMenuHover(int mouseX, int mouseY) {
        contextMenus.updateContextMenuHover(mouseX, mouseY);
    }

    public void updateNodeContextMenuHover(int mouseX, int mouseY) {
        contextMenus.updateNodeContextMenuHover(mouseX, mouseY);
    }

    public boolean handleStartModeDropdownClick(int mouseX, int mouseY) {
        return startModeDropdown.handleClick(mouseX, mouseY);
    }

    public void closeStartModeDropdown() {
        startModeDropdown.close();
    }

    /**
     * Handles a click on the context menu. Returns the selected NodeType, or null.
     */
    public ContextMenuSelection handleContextMenuClick(int mouseX, int mouseY) {
        return contextMenus.handleContextMenuClick(mouseX, mouseY);
    }

    public boolean handleNodeContextMenuClick(int mouseX, int mouseY) {
        return contextMenus.handleNodeContextMenuClick(mouseX, mouseY);
    }

    /**
     * Renders the context menu.
     */
    public void renderContextMenu(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        contextMenus.renderContextMenu(context, textRenderer, mouseX, mouseY);
    }

    public void renderNodeContextMenu(GuiGraphics context, Font textRenderer) {
        contextMenus.renderNodeContextMenu(context, textRenderer);
    }

    public void renderStartModeDropdown(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        startModeDropdown.render(context, textRenderer, mouseX, mouseY);
    }

    /**
     * Adds a node of the specified type at the given world coordinates.
     */
    public Node addNodeAtPosition(NodeType type, int worldX, int worldY) {
        Node node = Node.createForEditor(type, 0, 0);
        positionNewNode(node, worldX, worldY);
        addNode(node);
        selectNode(node);
        return node;
    }

    /**
     * Adds a node from the context menu at the stored right-click position.
     */
    public Node addNodeFromContextMenu(NodeType type) {
        return addNodeAtPosition(type, contextMenus.contextMenuWorldX(), contextMenus.contextMenuWorldY());
    }

    public Node addRoutineFromContextMenu(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null) return null;
        Node node = Node.createRoutineCall(
            routine, contextMenus.contextMenuWorldX(), contextMenus.contextMenuWorldY());
        addNode(node);
        selectNode(node);
        return node;
    }

    private void positionNewNode(Node node, int worldMouseX, int worldMouseY) {
        if (node == null) {
            return;
        }
        int nodeX = worldMouseX - node.getWidth() / 2;
        int nodeY = worldMouseY - node.getHeight() / 2;
        if (InputCompatibilityBridge.hasShiftDown()) {
            nodeX = snapToGrid(nodeX);
            nodeY = snapToGrid(nodeY);
        }
        node.setPosition(nodeX, nodeY);
        invalidateHierarchyCache();
    }

    /**
     * Handles scroll events for the context menu.
     * Returns true if the context menu handled the scroll.
     */
    public boolean handleContextMenuScroll(int mouseX, int mouseY, double amount) {
        return contextMenus.handleContextMenuScroll(mouseX, mouseY, amount);
    }

    public void updatePanning(int mouseX, int mouseY) {
        viewport.updatePanning(mouseX, mouseY);
    }
    
    public void stopPanning() {
        viewport.stopPanning();
    }
    
    public boolean isPanning() {
        return viewport.isPanning();
    }
    
    public void resetCamera() {
        viewport.resetCamera();
    }

    public void restoreSessionViewportState() {
        viewport.restoreSessionViewportState();
    }

    public void persistSessionViewportState() {
        viewport.persistSessionViewportState();
    }

    private void cacheSessionViewportState() {
        viewport.persistSessionViewportState();
    }

    public boolean focusNodeById(String nodeId, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        return nodeFocus.focusNodeById(nodeId, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
    }

    public void focusNode(Node node, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        nodeFocus.focusNode(node, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
    }

    public boolean focusBestMatchingNode(String query, int screenWidth, int screenHeight, int sidebarWidth, int titleBarHeight) {
        return nodeFocus.focusBestMatchingNode(
            query, screenWidth, screenHeight, sidebarWidth, titleBarHeight);
    }

    public String getBestMatchingNodeLabel(String query) {
        return nodeFocus.getBestMatchingNodeLabel(query);
    }
    
    // Convert screen coordinates to world coordinates
    public int screenToWorldX(int screenX) {
        return viewport.screenToWorldX(screenX);
    }
    
    public int screenToWorldY(int screenY) {
        return viewport.screenToWorldY(screenY);
    }

    private int screenToUiX(int screenX) {
        return viewport.screenToUiX(screenX);
    }

    private int screenToUiY(int screenY) {
        return viewport.screenToUiY(screenY);
    }
    
    // Convert world coordinates to screen coordinates
    public int worldToScreenX(int worldX) {
        return viewport.worldToScreenX(worldX);
    }
    
    public int worldToScreenY(int worldY) {
        return viewport.worldToScreenY(worldY);
    }

    /**
     * Snaps a world coordinate to the nearest grid point.
     * @param worldCoord The world coordinate to snap
     * @return The snapped coordinate
     */
    private int snapToGrid(int worldCoord) {
        return Math.round((float) worldCoord / GRID_SNAP_SIZE) * GRID_SNAP_SIZE;
    }

    public void deleteNodeIfInSidebar(Node node, int mouseX, int sidebarWidth) {
        nodeLifecycle.deleteNodeIfInSidebar(node, mouseX, sidebarWidth);
    }

    private void removeNodeCascade(Node node, boolean captureUndo) {
        nodeLifecycle.removeNodeCascade(node, captureUndo);
    }
    
    public boolean isNodeOverSidebar(Node node, int sidebarWidth) {
        if (node == null) {
            return false;
        }
        int screenX = worldToScreenX(node.getX());
        double scaledCenter = screenX + (node.getWidth() * getZoomScale()) / 2.0;
        return scaledCenter < sidebarWidth;
    }
    
    public boolean isNodeOverSidebar(Node node, int sidebarWidth, int screenX, int screenWidth) {
        double scaledCenter = (screenX + screenWidth / 2.0) * getZoomScale();
        return scaledCenter < sidebarWidth;
    }

    public boolean isSelectionOverSidebar(int sidebarWidth) {
        return selectionController.isSelectionOverSidebar(sidebarWidth);
    }
    
    public boolean tryConnectToSocket(Node targetNode, int targetSocket, boolean isInput) {
        return connectionController.tryConnectToSocket(targetNode, targetSocket, isInput);
    }
    
    public NodeConnection getConnectionAt(int mouseX, int mouseY) {
        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);
        return connectionController.getConnectionAt(worldX, worldY);
    }

    public void startConnectionCut(int mouseX, int mouseY) {
        connectionController.startConnectionCut(screenToWorldX(mouseX), screenToWorldY(mouseY));
    }

    public void updateConnectionCut(int worldX, int worldY) {
        connectionController.updateConnectionCut(worldX, worldY);
    }

    public boolean stopConnectionCut() {
        return connectionController.stopConnectionCut();
    }

    public void cancelConnectionCut() {
        connectionController.cancelConnectionCut();
    }

    public boolean removeConnection(NodeConnection connection) {
        return connectionController.removeConnection(connection);
    }

    public boolean isConnectionCutActive() {
        return connectionController.isConnectionCutActive();
    }

    public boolean hasConnectionCutMoved() {
        return connectionController.hasConnectionCutMoved();
    }

    public void render(GuiGraphics context, Font textRenderer, int mouseX, int mouseY, float delta, boolean onlyDragged) {
        long totalStartNanos = !onlyDragged ? System.nanoTime() : 0L;
        flushDeferredStickyNoteSaveIfDue();
        var matrices = context.pose();
        MatrixStackBridge.push(matrices);
        MatrixStackBridge.scale(matrices, getZoomScale(), getZoomScale());
        if (onlyDragged) {
            // Keep active drags above border/line layers while they are in motion.
            MatrixStackBridge.translateZ(matrices, 250.0f);
        }

        if (!onlyDragged) {
            updateCascadeDeletionPreview();
        }
        List<Node> visibleRoots = getVisibleRootsForViewport();
        visibleNodeCountForFrame = viewport.getCachedVisibleNodeCount();
        if (!onlyDragged) {
            profilerVisibleRoots = visibleRoots.size();
            profilerVisibleNodes = viewport.getCachedVisibleNodeCount();
        }
        if (!onlyDragged) {
            runtimeVariableNamesFrameCache.clear();
        }
        viewport.beginRenderFrame(isLowDetailModeEnabled());
        boolean renderConnectionsOnTop = shouldRenderConnectionsOnTop();
        int drawnConnections = 0;
        if (!renderConnectionsOnTop) {
            drawnConnections += connectionRenderer.renderConnections(context, onlyDragged, !onlyDragged);
        }

        Set<Node> renderedNodes = new HashSet<>();
        long nodesStartNanos = !onlyDragged ? System.nanoTime() : 0L;

        for (Node root : visibleRoots) {
            nodeRenderer.renderHierarchy(root, context, textRenderer, mouseX, mouseY, delta, onlyDragged, false, renderedNodes);
        }
        if (!onlyDragged) {
            profilerNodeMs = (System.nanoTime() - nodesStartNanos) / 1_000_000.0;
            profilerDrawnNodes = renderedNodes.size();
        }

        long dropdownStartNanos = !onlyDragged ? System.nanoTime() : 0L;
        if (!onlyDragged) {
            renderParameterDropdownList(context, textRenderer, mouseX, mouseY);
            renderRandomRoundingDropdownList(context, textRenderer, mouseX, mouseY);
            renderModeDropdownList(context, textRenderer, mouseX, mouseY);
            profilerDropdownMs = (System.nanoTime() - dropdownStartNanos) / 1_000_000.0;
        }

        if (renderConnectionsOnTop) {
            drawnConnections += connectionRenderer.renderConnections(context, onlyDragged, !onlyDragged);
        }
        if (!onlyDragged) {
            profilerDrawnConnections = drawnConnections;
            profilerRenderMs = (System.nanoTime() - totalStartNanos) / 1_000_000.0;
        }

        if (!onlyDragged) {
            DrawContextBridge.startNewRootLayer(context);
            nodeControls.renderRuntimeScopeTooltip(context, textRenderer, mouseX, mouseY);
        }
        MatrixStackBridge.pop(matrices);
        viewport.endRenderFrame();
        visibleNodeCountForFrame = 0;
    }

    private boolean shouldRenderConnectionsOnTop() {
        SettingsManager.Settings settings = SettingsManager.getCurrent();
        return settings != null && Boolean.TRUE.equals(settings.renderConnectionsOnTop);
    }

    public PerformanceSnapshot getPerformanceSnapshot() {
        return new PerformanceSnapshot(
            profilerRenderMs,
            profilerNodeMs,
            profilerConnectionMs,
            profilerDropdownMs,
            profilerHoverMs,
            profilerHitTestAvgMs,
            profilerHitTestAvgRoots,
            profilerVisibleNodes,
            profilerDrawnNodes,
            profilerVisibleRoots,
            profilerDrawnConnections
        );
    }

    public void renderProfilerOverlay(GuiGraphics context, Font textRenderer) {
        PerformanceSnapshot snapshot = getPerformanceSnapshot();
        List<String> lines = List.of(
            String.format(Locale.ROOT, "render %.2f ms", snapshot.renderMs()),
            String.format(Locale.ROOT, "nodes %.2f ms (%d visible, %d drawn, %d roots)", snapshot.nodeMs(), snapshot.visibleNodes(), snapshot.drawnNodes(), snapshot.visibleRoots()),
            String.format(Locale.ROOT, "connections %.2f ms (%d drawn)", snapshot.connectionMs(), snapshot.drawnConnections()),
            String.format(Locale.ROOT, "dropdowns %.2f ms", snapshot.dropdownMs()),
            String.format(Locale.ROOT, "hover %.2f ms", snapshot.hoverMs()),
            String.format(Locale.ROOT, "hit-test %.2f ms (%.1f roots/call)", snapshot.hitTestAvgMs(), snapshot.hitTestAvgRoots())
        );
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, textRenderer.width(line));
        }
        int lineHeight = textRenderer.lineHeight + 2;
        int overlayWidth = maxWidth + PROFILER_OVERLAY_PADDING * 2;
        int overlayHeight = lines.size() * lineHeight + PROFILER_OVERLAY_PADDING * 2;
        int overlayX = PROFILER_OVERLAY_MARGIN;
        int overlayY = PROFILER_OVERLAY_MARGIN;
        context.fill(overlayX, overlayY, overlayX + overlayWidth, overlayY + overlayHeight, 0xD0101010);
        DrawContextBridge.drawBorder(context, overlayX, overlayY, overlayWidth, overlayHeight, 0xFF505050);
        int textY = overlayY + PROFILER_OVERLAY_PADDING;
        for (String line : lines) {
            context.drawString(textRenderer, Component.literal(line), overlayX + PROFILER_OVERLAY_PADDING, textY, 0xFFFFFFFF);
            textY += lineHeight;
        }
    }

    public record PerformanceSnapshot(
        double renderMs,
        double nodeMs,
        double connectionMs,
        double dropdownMs,
        double hoverMs,
        double hitTestAvgMs,
        double hitTestAvgRoots,
        int visibleNodes,
        int drawnNodes,
        int visibleRoots,
        int drawnConnections
    ) {
    }

    public void renderScreenCoordinateCaptureOverlay(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        screenCoordinateCapture.renderOverlay(context, textRenderer, mouseX, mouseY);
    }

    public void renderSelectionBox(GuiGraphics context) {
        selectionController.renderSelectionBox(context);
    }

    private Node getParentForNode(Node node) {
        if (node == null) {
            return null;
        }
        if (node.isParameterNode()) {
            return node.getParentParameterHost();
        }
        if (node.isSensorNode()) {
            return node.getParentControl();
        }
        if (node.isAttachedToActionControl()) {
            return node.getParentActionControl();
        }
        return null;
    }

    boolean shouldRenderConnectionInDraggedPass(NodeConnection connection) {
        return connectionRenderer.shouldRenderConnectionInDraggedPass(connection);
    }

    private boolean rendersInlineParameters(Node node) {
        if (node == null) {
            return false;
        }
        if (viewport.isDenseViewportMode()) {
            return false;
        }
        if (node.shouldRenderInlineParameters()) {
            return true;
        }
        return node.isParameterNode()
            && node.getType() != NodeType.ROUTINE_INPUT
            && node.getType() != NodeType.CREATE_LIST
            && node.getType() != NodeType.LIST_LENGTH
            && node.getType() != NodeType.OPERATOR_MOD
            && node.getType() != NodeType.PARAM_DURATION
            && node.getType() != NodeType.SENSOR_POSITION_OF
            && node.getType() != NodeType.SENSOR_LOOK_DIRECTION
            && node.getType() != NodeType.SENSOR_DISTANCE_BETWEEN
            && node.getType() != NodeType.SENSOR_CURRENT_GUI
            && node.getType() != NodeType.SENSOR_SLOT_ITEM_COUNT;
    }

    private boolean shouldRenderNodeSockets(Node node) {
        if (node == null || !node.shouldRenderSockets()) {
            return false;
        }
        if (!viewport.isDenseViewportMode() && !viewport.isCompactViewportMode()) {
            return true;
        }
        return node.isSelected()
            || node.isDragging()
            || node == connectionController.getConnectionSourceNode()
            || node == connectionController.getHoveredSocketNode()
            || node == connectionController.getHoveredNode();
    }

    private boolean hasRunPresetSelection(Node node) {
        return node != null && node.getType() == NodeType.RUN_PRESET
            && !getSelectedPresetName(node).isBlank();
    }

    private int getRunPresetOpenButtonWorldX(Node node) {
        return node.getX() + node.getWidth() - NODE_HEADER_BUTTON_SIZE - 2;
    }

    private int getRunPresetOpenButtonWorldY(Node node) {
        return node.getY() + 2;
    }

    private void renderRunPresetOpenButton(GuiGraphics context, Font textRenderer, Node node,
                                           boolean dimmed, int mouseX, int mouseY) {
        nodeControls.renderNodeHeaderTextButton(context, textRenderer, getRunPresetOpenButtonWorldX(node),
            getRunPresetOpenButtonWorldY(node), NODE_HEADER_BUTTON_SIZE, "↗", dimmed, true,
            nodeControls.getSelectedNodeAccentColor(), mouseX, mouseY);
    }







































    private float getAnimatedHoverProgress(Object key, boolean highlighted) {
        if (viewport.isCompactViewportMode()) {
            return 0f;
        }
        return HoverAnimator.getProgress(key, highlighted, UITheme.HOVER_ANIM_MS);
    }

    private float getTextFieldHighlightProgress(Object key, boolean hovered, boolean active) {
        return active ? 1f : getAnimatedHoverProgress(key, hovered);
    }

    private UIStyleHelper.FieldPalette getNodeInputPalette(boolean isOverSidebar, int accentColor, float progress, boolean active, boolean disabled) {
        if (viewport.isCompactViewportMode() && !isOverSidebar) {
            return new UIStyleHelper.FieldPalette(
                active ? UITheme.BACKGROUND_INPUT : UITheme.BACKGROUND_SECONDARY,
                active ? accentColor : UITheme.BORDER_DEFAULT,
                active ? accentColor : UITheme.BORDER_DEFAULT,
                active ? UITheme.TEXT_EDITING : UITheme.TEXT_PRIMARY,
                UITheme.TEXT_TERTIARY
            );
        }
        UIStyleHelper.FieldPalette palette = UIStyleHelper.getInputFieldPalette(accentColor, progress, active, disabled);
        if (!isOverSidebar) {
            return palette;
        }
        return new UIStyleHelper.FieldPalette(
            active ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SECONDARY,
            active ? accentColor : UITheme.BORDER_SUBTLE,
            UITheme.PANEL_INNER_BORDER,
            active ? UITheme.TEXT_EDITING : UITheme.TEXT_TERTIARY,
            disabled ? UITheme.TEXT_TERTIARY : palette.placeholderColor()
        );
    }

    private UIStyleHelper.FieldPalette getLowDetailAwareFieldPalette(int backgroundColor, int borderColor, int innerBorderColor,
                                                                     int textColor, int placeholderColor, boolean isOverSidebar) {
        if (viewport.isCompactViewportMode() && !isOverSidebar) {
            innerBorderColor = borderColor;
        }
        return new UIStyleHelper.FieldPalette(backgroundColor, borderColor, innerBorderColor, textColor, placeholderColor);
    }













































    public boolean handleBooleanModeTabClick(Node ignoredNode, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        Node node = findBooleanModeNodeAt(worldX, worldY);
        if (!nodeControls.isCombinedBooleanNode(node)) {
            return false;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldTop = nodeControls.getBooleanModeTabTop(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        if (worldX < fieldLeft || worldX > fieldLeft + fieldWidth
            || worldY < fieldTop || worldY > fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
            return false;
        }

        boolean literalMode = worldX < fieldLeft + fieldWidth / 2;
        if (isEditingParameterField()) {
            stopParameterEditing(true);
        }
        if (node.isBooleanModeLiteral() != literalMode) {
            node.setBooleanModeLiteral(literalMode);
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
        }
        return true;
    }



































    private String[] getCoordinateAxes(Node node) {
        if (node == null) {
            return new String[0];
        }
        return node.getCoordinateFieldAxes();
    }

    private void renderScreenCoordinatePickerButton(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                                    int mouseX, int mouseY) {
        if (node == null || !node.hasScreenCoordinatePickerButton()) {
            return;
        }
        int buttonLeft = node.getScreenCoordinatePickerButtonLeft() - viewport.getCameraX();
        int buttonTop = node.getScreenCoordinatePickerButtonTop() - viewport.getCameraY();
        int buttonWidth = node.getScreenCoordinatePickerButtonWidth();
        int buttonHeight = node.getScreenCoordinatePickerButtonHeight();

        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getScreenCoordinatePickerButtonLeft()
            && worldMouseX <= node.getScreenCoordinatePickerButtonLeft() + buttonWidth
            && worldMouseY >= node.getScreenCoordinatePickerButtonTop()
            && worldMouseY <= node.getScreenCoordinatePickerButtonTop() + buttonHeight;

        int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG;
        int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BUTTON_DEFAULT_BORDER;
        if (isScreenCoordinateCaptureActiveFor(node)) {
            buttonFill = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = nodeControls.getSelectedNodeAccentColor();
        } else if (hovered) {
            buttonFill = UITheme.BUTTON_DEFAULT_HOVER;
            buttonBorder = nodeControls.getSelectedNodeAccentColor();
        }

        context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
        DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);

        String buttonLabel = isScreenCoordinateCaptureActiveFor(node) ? "Click To Set" : "Pick";
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = buttonLeft + Math.max(0, (buttonWidth - textRenderer.width(buttonLabel)) / 2);
        int textY = buttonTop + (buttonHeight - textRenderer.lineHeight) / 2;
        drawNodeText(context, textRenderer, Component.literal(buttonLabel), textX, textY, textColor);
    }

    private void renderCoordinateInputFields(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                             int mouseX, int mouseY) {
        inlineFieldRenderer.renderCoordinateInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderAmountInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                        int mouseX, int mouseY) {
        inlineFieldRenderer.renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderRandomRoundingField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar) {
        randomRounding.renderField(context, textRenderer, node, isOverSidebar);
    }

    private void renderRandomRoundingDropdownList(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        randomRounding.renderDropdown(context, textRenderer, mouseX, mouseY);
    }

    private void renderMessageInputFields(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                          int mouseX, int mouseY) {
        inlineFieldRenderer.renderMessageInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames) {
        return InlineVariableRenderer.shouldBuildInlineExpressionRender(
            viewport.isCompactViewportMode(), rawText, variableNames, false);
    }

    static boolean isInlineArithmeticOperatorAt(String text, int index) {
        return InlineVariableRenderer.isInlineArithmeticOperatorAt(text, index);
    }

    /** Returns true if value is empty or a valid arithmetic expression using numbers and/or known $variable references. */
    private boolean isNumericOrVariableReference(String value, Node node, boolean allowDecimal, boolean requireCoordinateValid) {
        if (value == null) {
            value = "";
        }
        value = value.trim();
        if (value.isEmpty()) {
            return true;
        }
        if (requireCoordinateValid && "-".equals(value)) {
            return true;
        }
        return isValidNumericExpression(value, collectRuntimeVariableNames(node), allowDecimal, requireCoordinateValid);
    }

    private boolean isValidNumericExpression(String value, Set<String> variableNames, boolean allowDecimal, boolean requireCoordinateValid) {
        return NumericExpressionValidator.isValid(value, variableNames, allowDecimal, requireCoordinateValid);
    }

    private Set<String> collectRuntimeVariableNames(Node node) {
        Node startNode = node != null ? node.getOwningStartNode() : null;
        String startId = startNode != null ? startNode.getId() : "";
        Set<String> cached = runtimeVariableNamesFrameCache.get(startId);
        if (cached != null) {
            return cached;
        }
        Set<String> names = new HashSet<>(getBaseRuntimeVariableNames());
        ExecutionManager manager = ExecutionManager.getInstance();
        List<ExecutionManager.RuntimeVariableEntry> entries = manager.getRuntimeVariableEntries();
        if (!entries.isEmpty()) {
            String effectiveStartId = startNode != null ? startNode.getId() : null;
            for (ExecutionManager.RuntimeVariableEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                if (entry.getScope() != RuntimeValueScope.GLOBAL
                    && effectiveStartId != null && !effectiveStartId.equals(entry.getStartNodeId())) {
                    continue;
                }
                String name = entry.getName();
                if (name != null) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        names.add(trimmed);
                    }
                }
            }
        }
        runtimeVariableNamesFrameCache.put(startId, names);
        return names;
    }

    private Set<String> getBaseRuntimeVariableNames() {
        if (cachedBaseRuntimeVariableNames != null) {
            return cachedBaseRuntimeVariableNames;
        }
        Set<String> names = new HashSet<>();
        for (Node graphNode : nodes) {
            if (graphNode == null) {
                continue;
            }
            if (graphNode.getType() == NodeType.VARIABLE) {
                NodeParameter parameter = graphNode.getParameter("Variable");
                if (parameter == null) {
                    continue;
                }
                String value = parameter.getStringValue();
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
        }
        cachedBaseRuntimeVariableNames = names;
        return cachedBaseRuntimeVariableNames;
    }

    private void updateMessageFieldContentWidth(Font textRenderer) {
        inlineFields.updateMessageFieldContentWidth(textRenderer);
    }

    private void updateCoordinateFieldContentWidth(Font textRenderer) {
        inlineFields.updateCoordinateFieldContentWidth(textRenderer);
    }

    private void updateAmountFieldContentWidth(Font textRenderer) {
        inlineFields.updateAmountFieldContentWidth(textRenderer);
    }

    private void updateStopTargetFieldContentWidth(Font textRenderer) {
        inlineFields.updateStopTargetFieldContentWidth(textRenderer);
    }

    private void updateVariableFieldContentWidth(Font textRenderer) {
        inlineFields.updateVariableFieldContentWidth(textRenderer);
    }

    private void updateParameterFieldContentWidth(Node node, Font textRenderer, int editingIndex, String editingValue) {
        if (node == null || !rendersInlineParameters(node) || textRenderer == null) {
            return;
        }
        int requiredFieldWidth = 0;
        if (node.supportsModeSelection()) {
            String modeLabel = node.getModeDisplayLabel();
            if (modeLabel != null && !modeLabel.isEmpty()) {
                requiredFieldWidth = Math.max(requiredFieldWidth, textRenderer.width(modeLabel));
            }
        }
        List<NodeParameter> parameters = node.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            NodeParameter param = parameters.get(i);
            if (param == null) {
                continue;
            }
            String label = node.getParameterDisplayName(param);
            if (label == null) {
                label = "";
            }
            label = label + ":";
            String value = i == editingIndex ? editingValue : param.getStringValue();
            if (value == null) {
                value = "";
            }
            if (node.getType() == NodeType.PARAM_VILLAGER_TRADE
                && ("Item".equalsIgnoreCase(param.getName()) || "Trade".equalsIgnoreCase(param.getName()))) {
                value = nodeControls.formatVillagerTradeValue(value);
            }
            int labelWidth = textRenderer.width(label);
            int valueWidth = textRenderer.width(value);
            int fieldWidth = labelWidth + valueWidth + 12;
            requiredFieldWidth = Math.max(requiredFieldWidth, fieldWidth);
        }
        node.setParameterFieldWidthOverride(requiredFieldWidth);
        node.recalculateDimensions();
    }











    private NodeGraphData.CustomNodeDefinition getTemplateDefinition(Node node) {
        NodeGraphData templateData = getPresetPreviewGraphData(node);
        return templateData == null ? null : NodeGraphPersistence.resolveCustomNodeDefinition(getSelectedPresetName(node), templateData);
    }

    private String getSelectedPresetName(Node node) {
        if (node == null) {
            return "";
        }
        NodeParameter presetParam = node.getParameter("Preset");
        String presetName = presetParam != null ? presetParam.getStringValue() : "";
        if (presetName == null || presetName.isBlank()) {
            String activePreset = workspace.getActivePreset();
            return activePreset == null ? "" : activePreset.trim();
        }
        return presetName.trim();
    }

    private NodeGraphData getPresetPreviewGraphData(Node node) {
        if (node == null || node.getType() != NodeType.TEMPLATE) {
            return null;
        }
        String normalized = getSelectedPresetName(node);
        if (normalized.isEmpty()) {
            return node.getTemplateGraphData();
        }
        NodeGraphData cached = node.getTemplateGraphData();
        if (cached != null) {
            return cached;
        }
        NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphForPreset(normalized);
        if (loaded != null) {
            NodeGraphData.CustomNodeDefinition definition = NodeGraphPersistence.resolveCustomNodeDefinition(normalized, loaded);
            if (definition != null) {
                node.setTemplateName(definition.getName());
                node.setTemplateVersion(definition.getVersion() != null ? definition.getVersion() : 0);
            }
            node.setTemplateGraphData(loaded);
            return loaded;
        }
        return cached;
    }





    public boolean isPointInsideTemplateEditButton(Node node, int mouseX, int mouseY) {
        return false;
    }

    private void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                            int mouseX, int mouseY) {
        inlineFieldRenderer.renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderVariableInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                          int mouseX, int mouseY) {
        inlineFieldRenderer.renderVariableInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderSchematicDropdownField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                              int mouseX, int mouseY) {
        specializedSelectors.renderSchematicField(
            context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderSchematicDropdownList(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        specializedSelectors.renderSchematicDropdown(
            context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    private void renderRunPresetDropdownList(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar, int mouseX, int mouseY) {
        specializedSelectors.renderRunPresetDropdown(
            context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    public boolean isEditingCoordinateField() {
        return inlineFields.isEditingCoordinateField();
    }

    public boolean isScreenCoordinateCaptureActive() {
        return screenCoordinateCapture.isActive();
    }

    public boolean isScreenCoordinateCaptureActiveFor(Node node) {
        return screenCoordinateCapture.isActiveFor(node);
    }

    public void startScreenCoordinateCapture(Node node) {
        screenCoordinateCapture.start(node);
    }

    public void cancelScreenCoordinateCapture() {
        screenCoordinateCapture.cancel();
    }

    public void updateScreenCoordinateCapturePreview(int screenX, int screenY) {
        screenCoordinateCapture.updatePreview(screenX, screenY);
    }

    public boolean commitScreenCoordinateCapture(int screenX, int screenY) {
        return screenCoordinateCapture.commit(screenX, screenY);
    }

    public int getCoordinateFieldAxisAt(Node node, int screenX, int screenY) {
        return inlineFields.getCoordinateFieldAxisAt(node, screenX, screenY);
    }

    public boolean isPointInsideScreenCoordinatePickerButton(Node node, int mouseX, int mouseY) {
        return inlineFields.isPointInsideScreenCoordinatePickerButton(node, mouseX, mouseY);
    }

    public boolean handleScreenCoordinatePickerClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideScreenCoordinatePickerButton(node, mouseX, mouseY)) {
            return false;
        }
        focusSelectedNode(node);
        startScreenCoordinateCapture(node);
        return true;
    }

    public void startCoordinateEditing(Node node, int axisIndex) {
        inlineFields.startCoordinateEditing(node, axisIndex);
    }

    public void stopCoordinateEditing(boolean commit) {
        inlineFields.stopCoordinateEditing(commit);
    }

    public boolean handleCoordinateKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleCoordinateKeyPressed(keyCode, modifiers);
    }

    private boolean isTextShortcutDown(int modifiers) {
        return InputCompatibilityBridge.hasControlDown()
            || (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    public boolean handleCoordinateCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleCoordinateCharTyped(chr);
    }

    public boolean isEditingAmountField() {
        return inlineFields.isEditingAmountField();
    }

    public void startAmountEditing(Node node) {
        inlineFields.startAmountEditing(node);
    }

    public void stopAmountEditing(boolean commit) {
        inlineFields.stopAmountEditing(commit);
    }

    public boolean handleAmountKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleAmountKeyPressed(keyCode, modifiers);
    }

    public boolean handleAmountCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleAmountCharTyped(chr);
    }

    public boolean isEditingStopTargetField() {
        return inlineFields.isEditingStopTargetField();
    }

    public void startStopTargetEditing(Node node) {
        inlineFields.startStopTargetEditing(node);
    }

    public void stopStopTargetEditing(boolean commit) {
        inlineFields.stopStopTargetEditing(commit);
    }

    public boolean isEditingVariableField() {
        return inlineFields.isEditingVariableField();
    }

    public void startVariableEditing(Node node) {
        inlineFields.startVariableEditing(node);
    }

    public void stopVariableEditing(boolean commit) {
        inlineFields.stopVariableEditing(commit);
    }

    public boolean handleVariableKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleVariableKeyPressed(keyCode, modifiers);
    }

    public boolean handleVariableCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleVariableCharTyped(chr);
    }

    public boolean handleStopTargetKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleStopTargetKeyPressed(keyCode, modifiers);
    }

    public boolean handleStopTargetCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleStopTargetCharTyped(chr);
    }

    public boolean isEditingStickyNote() {
        return stickyNoteController.isEditing();
    }

    public void startStickyNoteEditing(Node node) {
        stickyNoteController.startEditing(node);
    }

    public void stopStickyNoteEditing(boolean commit) {
        stickyNoteController.stopEditing(commit);
    }

    private void flushDeferredStickyNoteSaveIfDue() {
        stickyNoteController.flushDeferredSaveIfDue();
    }

    public boolean isPointInsideStickyNoteTextArea(Node node, int screenX, int screenY) {
        if (node == null || !node.isStickyNote()) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        return worldX >= node.getStickyNoteBodyLeft()
            && worldX <= node.getStickyNoteBodyLeft() + node.getStickyNoteBodyWidth()
            && worldY >= node.getStickyNoteBodyTop()
            && worldY <= node.getStickyNoteBodyTop() + node.getStickyNoteBodyHeight();
    }

    public boolean handleStickyNoteResizeHandleClick(Node node, int screenX, int screenY) {
        return stickyNoteController.handleResizeHandleClick(node, screenX, screenY);
    }

    public boolean isPointInsideStickyNoteResizeHandle(Node node, int screenX, int screenY) {
        return getStickyNoteResizeCornerAt(node, screenX, screenY) != null;
    }

    public StickyNoteResizeCorner getStickyNoteResizeCornerAt(Node node, int screenX, int screenY) {
        return stickyNoteController.getResizeCornerAt(node, screenX, screenY);
    }

    public boolean handleStickyNoteKeyPressed(int keyCode, int modifiers) {
        return stickyNoteController.handleKeyPressed(keyCode, modifiers);
    }

    public boolean handleStickyNoteCharTyped(char chr, int modifiers) {
        return stickyNoteController.handleCharTyped(chr, modifiers);
    }

    public boolean isEditingMessageField() {
        return inlineFields.isEditingMessageField();
    }

    public void startMessageEditing(Node node, int index) {
        inlineFields.startMessageEditing(node, index);
    }

    public void stopMessageEditing(boolean commit) {
        inlineFields.stopMessageEditing(commit);
    }

    public boolean isEditingEventNameField() {
        return inlineFields.isEditingEventNameField();
    }

    public void startEventNameEditing(Node node) {
        inlineFields.startEventNameEditing(node);
    }

    public void stopEventNameEditing(boolean commit) {
        inlineFields.stopEventNameEditing(commit);
    }

    public boolean handleEventNameKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleEventNameKeyPressed(keyCode, modifiers);
    }

    public boolean handleEventNameCharTyped(char chr, int modifiers) {
        return inlineFields.handleEventNameCharTyped(chr);
    }

    public boolean isEditingParameterField() {
        return parameterEditor.isEditing();
    }

    private void updateParameterCaretBlink() {
        parameterEditor.updateCaretBlink();
    }

    public void startParameterEditing(Node node, int index) {
        parameterEditor.start(node, index);
    }

    public void stopParameterEditing(boolean commit) {
        parameterEditor.stop(commit);
    }

    private boolean applyParameterEdit() {
        return parameterEditor.apply();
    }

    private void refreshStateParameterPreview() {
        parameterEditor.refreshStatePreview();
    }

    private boolean isTradeInlinePlaceholder(Node node, NodeParameter parameter, boolean editing) {
        return parameterEditor.isTradeInlinePlaceholder(node, parameter, editing);
    }

    private boolean isDefaultMouseButtonValue(String value) {
        return ParameterTextEditorController.isDefaultMouseButtonValue(value);
    }

    private boolean isDefaultHandValue(String value) {
        return ParameterTextEditorController.isDefaultHandValue(value);
    }

    private String formatMouseButtonValue(String value) {
        return ParameterTextEditorController.formatMouseButtonValue(value);
    }

    private String formatHandValue(String value) {
        return ParameterTextEditorController.formatHandValue(value);
    }

    public boolean handleParameterKeyPressed(int keyCode, int modifiers) {
        return parameterEditor.handleKeyPressed(keyCode, modifiers);
    }

    public boolean handleParameterCharTyped(char chr, int modifiers, Font textRenderer) {
        return parameterEditor.handleCharTyped(chr, textRenderer);
    }

    public boolean handleMessageKeyPressed(int keyCode, int modifiers) {
        return inlineFields.handleMessageKeyPressed(keyCode, modifiers);
    }

    public boolean handleMessageCharTyped(char chr, int modifiers, Font textRenderer) {
        return inlineFields.handleMessageCharTyped(chr);
    }

    private boolean isAnyBlockItemValue(String value) {
        return ParameterTextEditorController.isAnyBlockItemValue(value);
    }

    private String formatAttributeDetectionInlineValue(Node node, NodeParameter parameter, String value) {
        if (node == null || parameter == null || !node.isAttributeDetectionSensor()) {
            return value;
        }
        if ("Attribute".equalsIgnoreCase(parameter.getName())) {
            AttributeDetectionConfig.AttributeOption attribute = AttributeDetectionConfig.getAttribute(value);
            return attribute != null ? attribute.label() : value;
        }
        if ("Value".equalsIgnoreCase(parameter.getName()) && isAttributeDetectionBooleanValueParameter(node, node.getParameters().indexOf(parameter))) {
            return "true".equalsIgnoreCase(value) ? tr("pathmind.option.true") : tr("pathmind.option.false");
        }
        return value;
    }

    private void updateParameterDropdown(Node node, int index, Font textRenderer, int fieldX, int fieldY, int fieldWidth, int fieldHeight) {
        parameterDropdown.update(node, index, textRenderer, fieldX, fieldY, fieldWidth, fieldHeight);
    }

    private void closeParameterDropdown() {
        parameterDropdown.close();
    }

    private void clearParameterDropdownSuppression() {
        parameterDropdown.clearSuppression();
    }

    private void openInlineParameterDropdown(Node node, int index) {
        parameterDropdown.openInline(node, index);
    }

    private int getDropdownRowHeight() {
        return SCHEMATIC_DROPDOWN_ROW_HEIGHT;
    }

    public boolean handleParameterDropdownClick(double screenX, double screenY) {
        return parameterDropdown.handleClick(screenX, screenY);
    }

    public boolean handleParameterDropdownScroll(double screenX, double screenY, double verticalAmount) {
        return parameterDropdown.handleScroll(screenX, screenY, verticalAmount);
    }

    private void renderParameterDropdownList(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        parameterDropdown.render(context, textRenderer, mouseX, mouseY);
    }

    public boolean handleModeDropdownClick(double screenX, double screenY) {
        return modeDropdown.handleClick(screenX, screenY);
    }

    public boolean handleModeDropdownScroll(double screenX, double screenY, double verticalAmount) {
        return modeDropdown.handleScroll(screenX, screenY, verticalAmount);
    }

    public boolean handleRandomRoundingDropdownScroll(double screenX, double screenY, double verticalAmount) {
        return randomRounding.handleScroll(screenX, screenY, verticalAmount);
    }

    public boolean handleModeFieldClick(Node node, int screenX, int screenY) {
        if (node == null || !node.supportsModeSelection()) {
            return false;
        }
        if (!isPointInsideModeField(node, screenX, screenY)) {
            return false;
        }
        if (modeDropdown.isOpen() && modeDropdown.getNode() == node) {
            closeModeDropdown();
            return true;
        }
        stopParameterEditing(true);
        openModeDropdown(node);
        return true;
    }

    public boolean isModeDropdownOpen() {
        return modeDropdown.isOpen();
    }

    public void closeModeDropdown() {
        modeDropdown.close();
    }

    private void renderModeDropdownList(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        modeDropdown.render(context, textRenderer, mouseX, mouseY);
    }

    private void openModeDropdown(Node node) {
        if (node == null || !node.supportsModeSelection()) {
            return;
        }
        closeParameterDropdown();
        closeSchematicDropdown();
        closeRunPresetDropdown();
        closeRandomRoundingDropdown();
        modeDropdown.open(node);
    }

    private DropdownController.Rect computeModeDropdownAnchor(Node node) {
        if (node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION) {
            return new DropdownController.Rect(
                node.getAmountFieldLeft() - viewport.getCameraX(),
                node.getAmountFieldLabelTop() - viewport.getCameraY(),
                node.getAmountFieldWidth(),
                node.getAmountFieldLabelHeight());
        } else if (node.showsModeFieldAboveParameterSlot()) {
            return new DropdownController.Rect(
                node.getModeFieldLeft() - viewport.getCameraX(),
                node.getModeFieldTop() - viewport.getCameraY(),
                node.getModeFieldWidth(),
                node.getModeFieldHeight());
        }
        return new DropdownController.Rect(
            nodeControls.getParameterFieldLeft(node) - viewport.getCameraX(),
            node.getY() - viewport.getCameraY() + 18,
            nodeControls.getParameterFieldWidth(node),
            nodeControls.getParameterFieldHeight());
    }

    private List<DropdownController.Option<com.pathmind.nodes.NodeMode>> getModeDropdownOptions(Node node) {
        if (node == null || !node.supportsModeSelection()) {
            return Collections.emptyList();
        }
        com.pathmind.nodes.NodeMode[] modes = com.pathmind.nodes.NodeMode.getModesForNodeType(node.getType());
        if (modes == null || modes.length == 0) {
            return Collections.emptyList();
        }
        List<DropdownController.Option<com.pathmind.nodes.NodeMode>> options = new ArrayList<>(modes.length);
        for (com.pathmind.nodes.NodeMode mode : modes) {
            if (mode != null) {
                options.add(new DropdownController.Option<>(mode.getDisplayName(), mode));
            }
        }
        return options;
    }

    private boolean isPointInsideModeField(Node node, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        if (node != null
            && (node.getType() == NodeType.WAIT || node.getType() == NodeType.PARAM_DURATION)
            && node.supportsModeSelection()) {
            int fieldLeft = node.getAmountFieldLeft();
            int fieldWidth = node.getAmountFieldWidth();
            int fieldHeight = node.getAmountFieldLabelHeight();
            int fieldTop = node.getAmountFieldLabelTop();
            return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
        }
        if (node != null && node.showsModeFieldAboveParameterSlot()) {
            int fieldLeft = node.getModeFieldLeft();
            int fieldWidth = node.getModeFieldWidth();
            int fieldHeight = node.getModeFieldHeight();
            int fieldTop = node.getModeFieldTop();
            return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
        }
        if (node == null || !node.shouldRenderInlineParameters()) {
            return false;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        int fieldHeight = nodeControls.getParameterFieldHeight();
        int fieldTop = node.getY() + 18;
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private Font getClientTextRenderer() {
        Minecraft client = Minecraft.getInstance();
        return client != null ? client.font : null;
    }

    private String getClipboardText() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            return client.keyboardHandler.getClipboard();
        }
        return "";
    }

    private void setClipboardText(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(text == null ? "" : text);
        }
    }

    public boolean isPointInsideAmountField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideAmountField(node, screenX, screenY);
    }

    private boolean isPointInsideRandomRoundingField(Node node, int screenX, int screenY) {
        return randomRounding.isPointInsideField(node, screenX, screenY);
    }

    private boolean isPointInsideRandomRoundingToggle(Node node, int screenX, int screenY) {
        return randomRounding.isPointInsideToggle(node, screenX, screenY);
    }

    private boolean isPointInsideAmountToggle(Node node, int screenX, int screenY) {
        if (node == null || !node.hasAmountToggle()) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int left = node.getAmountToggleLeft() - 3;
        int top = node.getAmountToggleTop() - 3;
        int width = node.getAmountToggleWidth() + 6;
        int height = node.getAmountToggleHeight() + 6;
        return worldX >= left && worldX <= left + width
            && worldY >= top && worldY <= top + height;
    }

    public boolean handleAmountToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideAmountToggle(node, mouseX, mouseY)) {
            return false;
        }
        boolean newState = !node.isAmountInputEnabled();
        node.setAmountInputEnabled(newState);
        nodeControls.getNodeToggleAnimation(amountToggleAnimations, node, newState)
            .animateTo(newState ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeInOutCubic);
        if (!newState && isEditingAmountField() && inlineFields.getAmountEditingNode() == node) {
            stopAmountEditing(false);
        }
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
        return true;
    }

    public boolean handleRandomRoundingToggleClick(Node node, int mouseX, int mouseY) {
        return randomRounding.handleToggleClick(node, mouseX, mouseY);
    }

    public boolean handleRandomRoundingDropdownClick(Node node, int mouseX, int mouseY) {
        return randomRounding.handleDropdownClick(node, mouseX, mouseY);
    }

    public boolean isPointInsideStopTargetField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideStopTargetField(node, screenX, screenY);
    }

    private String getStopTargetParameterKey(Node node) {
        if (node == null) {
            return "StartNumber";
        }
        return node.getStopTargetFieldParameterKey();
    }

    private String getStopTargetPlaceholder(Node node) {
        if (isPresetSelectorNode(node)) {
            return "preset";
        }
        return "start #";
    }

    public boolean handleStopTargetFieldClick(int screenX, int screenY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node != null
                && node.hasStopTargetInputField()
                && !isPresetSelectorNode(node)
                && isPointInsideStopTargetField(node, screenX, screenY)) {
                focusSelectedNode(node);
                startStopTargetEditing(node);
                return true;
            }
        }
        return false;
    }

    public boolean handleRunPresetDropdownClick(Node clickedNode, int screenX, int screenY) {
        return specializedSelectors.handleRunPresetClick(clickedNode, screenX, screenY);
    }

    public boolean isPointInsideVariableField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideVariableField(node, screenX, screenY);
    }

    public boolean handleVariableFieldClick(int screenX, int screenY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node != null && node.hasVariableInputField() && isPointInsideVariableField(node, screenX, screenY)) {
                focusSelectedNode(node);
                startVariableEditing(node);
                return true;
            }
        }
        return false;
    }

    public boolean isPointInsideEventNameField(Node node, int screenX, int screenY) {
        return inlineFields.isPointInsideEventNameField(node, screenX, screenY);
    }

    public boolean handleEventNameFieldClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideEventNameField(node, mouseX, mouseY)) {
            return false;
        }
        startEventNameEditing(node);
        return true;
    }

    public int getMessageFieldIndexAt(Node node, int screenX, int screenY) {
        return inlineFields.getMessageFieldIndexAt(node, screenX, screenY);
    }

    public int getParameterFieldIndexAt(Node node, int screenX, int screenY) {
        if (node == null || !canEditInlineParameterFields(node)) {
            return -1;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        if (node.getType() == NodeType.VARIABLE || node.getType() == NodeType.ROUTINE_INPUT) {
            int boxHeight = 16;
            int boxLeft = node.getX() + 6;
            int boxRight = node.getX() + node.getWidth() - 6;
            int boxTop = node.getY() + node.getHeight() / 2 - boxHeight / 2 + 4;
            int boxBottom = boxTop + boxHeight;
            if (worldX >= boxLeft && worldX <= boxRight
                && worldY >= boxTop && worldY <= boxBottom) {
                return 0;
            }
            return -1;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        int fieldHeight = nodeControls.getParameterFieldHeight();
        int fieldTop = nodeControls.getInlineParameterFieldsTop(node);

        List<NodeParameter> parameters = node.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            NodeParameter parameter = parameters.get(i);
            if (node.getParameterLabel(parameter).isEmpty()) {
                continue;
            }
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + fieldHeight) {
                return i;
            }
            fieldTop += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return -1;
    }

    private boolean canEditInlineParameterFields(Node node) {
        if (node == null) {
            return false;
        }
        if (node.getType() == NodeType.ROUTINE_INPUT) {
            return true;
        }
        if (!rendersInlineParameters(node)) {
            return false;
        }
        return !node.hasPopupEditButton() || node.getType() == NodeType.PARAM_INVENTORY_SLOT;
    }

    private int getInlineParameterFieldTop(Node node, int index) {
        if (node == null || index < 0) {
            return 0;
        }
        int fieldTop = nodeControls.getInlineParameterFieldsTop(node);
        List<NodeParameter> parameters = node.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            NodeParameter parameter = parameters.get(i);
            if (parameter == null || node.getParameterLabel(parameter).isEmpty()) {
                continue;
            }
            if (i == index) {
                return fieldTop;
            }
            fieldTop += PARAMETER_INPUT_HEIGHT + PARAMETER_INPUT_GAP;
        }
        return fieldTop;
    }

    public boolean handleBooleanLiteralDropdownClick(Node node, int mouseX, int mouseY) {
        if (parameterDropdown.isOpen() && !isEditingParameterField()
            && parameterDropdown.getNode() != null
            && isInlineDropdownParameter(parameterDropdown.getNode(), parameterDropdown.getIndex())) {
            if (isPointInsideInlineDropdownField(
                parameterDropdown.getNode(), parameterDropdown.getIndex(), mouseX, mouseY
            )) {
                closeParameterDropdown();
                return true;
            }
        }
        if (node == null) {
            int worldX = screenToWorldX(mouseX);
            int worldY = screenToWorldY(mouseY);
            for (int i = nodes.size() - 1; i >= 0; i--) {
                Node candidate = nodes.get(i);
                if (candidate == null) {
                    continue;
                }
                int index = getParameterFieldIndexAt(candidate, mouseX, mouseY);
                if (isInlineDropdownParameter(candidate, index)) {
                    node = candidate;
                    break;
                }
                if (candidate.getX() > worldX || candidate.getY() > worldY) {
                    continue;
                }
            }
            if (node == null) {
                return false;
            }
        }
        int index = getParameterFieldIndexAt(node, mouseX, mouseY);
        if (!isInlineDropdownParameter(node, index)) {
            return false;
        }
        openInlineParameterDropdown(node, index);
        return true;
    }

    private boolean isPointInsideInlineDropdownField(Node node, int index, int screenX, int screenY) {
        if (!isInlineDropdownParameter(node, index)) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldTop = getInlineParameterFieldTop(node, index);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        int fieldHeight = nodeControls.getParameterFieldHeight();
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    public boolean handleDirectionModeTabClick(Node ignoredNode, int screenX, int screenY) {
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        Node node = findDirectionModeNodeAt(worldX, worldY);
        if (!nodeControls.isCombinedDirectionNode(node)) {
            return false;
        }
        int fieldLeft = nodeControls.getParameterFieldLeft(node);
        int fieldTop = nodeControls.getDirectionModeTabTop(node);
        int fieldWidth = nodeControls.getParameterFieldWidth(node);
        if (worldX < fieldLeft || worldX > fieldLeft + fieldWidth
            || worldY < fieldTop || worldY > fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
            return false;
        }

        boolean exactMode = worldX < fieldLeft + fieldWidth / 2;
        if (isEditingParameterField()) {
            stopParameterEditing(true);
        }
        if (node.isDirectionModeExact() != exactMode) {
            node.setDirectionModeExact(exactMode);
            node.recalculateDimensions();
            notifyNodeParametersChanged(node);
        }
        return true;
    }

    private Node findDirectionModeNodeAt(int worldX, int worldY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node candidate = nodes.get(i);
            if (!nodeControls.isCombinedDirectionNode(candidate)) {
                continue;
            }
            int fieldLeft = nodeControls.getParameterFieldLeft(candidate);
            int fieldTop = nodeControls.getDirectionModeTabTop(candidate);
            int fieldWidth = nodeControls.getParameterFieldWidth(candidate);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return candidate;
            }
        }
        return null;
    }

    private Node findBooleanModeNodeAt(int worldX, int worldY) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node candidate = nodes.get(i);
            if (!nodeControls.isCombinedBooleanNode(candidate)) {
                continue;
            }
            int fieldLeft = nodeControls.getParameterFieldLeft(candidate);
            int fieldTop = nodeControls.getBooleanModeTabTop(candidate);
            int fieldWidth = nodeControls.getParameterFieldWidth(candidate);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return candidate;
            }
        }
        return null;
    }

    public boolean handleSchematicDropdownClick(Node clickedNode, int screenX, int screenY) {
        return specializedSelectors.handleSchematicClick(clickedNode, screenX, screenY);
    }

    public boolean handleSchematicDropdownScroll(double screenX, double screenY, double amount) {
        return specializedSelectors.handleSchematicScroll(screenX, screenY, amount);
    }

    public boolean handleRunPresetDropdownScroll(double screenX, double screenY, double amount) {
        return specializedSelectors.handleRunPresetScroll(screenX, screenY, amount);
    }

    private boolean isPointInsideSchematicField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasSchematicDropdownField()) {
            return false;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getSchematicFieldLeft();
        int fieldTop = node.getSchematicFieldInputTop();
        int fieldWidth = node.getSchematicFieldWidth();
        int fieldHeight = node.getSchematicFieldHeight();

        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private boolean isPointInsideRunPresetField(Node node, int screenX, int screenY) {
        if (!isPresetSelectorNode(node)) {
            return false;
        }

        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getStopTargetFieldLeft();
        int fieldTop = node.getStopTargetFieldInputTop();
        int fieldWidth = node.getStopTargetFieldWidth();
        int fieldHeight = node.getStopTargetFieldHeight();

        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    private boolean isPointInsideRunPresetToggle(Node node, int screenX, int screenY) {
        if (!isPresetSelectorNode(node)) {
            return false;
        }
        int worldX = screenToWorldX(screenX);
        int worldY = screenToWorldY(screenY);
        int fieldLeft = node.getStopTargetFieldLeft();
        int fieldTop = node.getStopTargetFieldInputTop();
        int fieldWidth = node.getStopTargetFieldWidth();
        int fieldHeight = node.getStopTargetFieldHeight();
        int toggleLeft = fieldLeft + Math.max(0, fieldWidth - 14);
        return worldX >= toggleLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    public boolean isPointInsideRunPresetOpenButton(Node node, int screenX, int screenY) {
        if (!hasRunPresetSelection(node)) return false;
        int left = getRunPresetOpenButtonWorldX(node);
        int top = getRunPresetOpenButtonWorldY(node);
        return nodeControls.isPointInsideNodeHeaderButton(left, top, NODE_HEADER_BUTTON_SIZE, screenX, screenY);
    }

    public String getSelectedPresetNameForNode(Node node) {
        return getSelectedPresetName(node);
    }

    private void closeSchematicDropdown() {
        specializedSelectors.closeSchematic();
    }

    private void closeRunPresetDropdown() {
        specializedSelectors.closeRunPreset();
    }

    private void closeRandomRoundingDropdown() {
        randomRounding.close();
    }

    private float getDropdownAnimationProgress(AnimatedValue animation, boolean open) {
        animation.animateTo(open ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        animation.tick();
        return AnimationHelper.easeOutQuad(animation.getValue());
    }

    private void enableDropdownScissor(GuiGraphics context, int x, int y, int width, int height) {
        context.enableScissor(x, y, x + Math.max(1, width), y + Math.max(1, height));
    }

    private void clearParameterDropdownState() {
        parameterDropdown.clearState();
    }

    private void applySchematicSelection(Node node, String value) {
        if (node == null || value == null || value.isEmpty()) {
            return;
        }
        node.setParameterValueAndPropagate("Schematic", value);
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
    }

    private void applyRunPresetSelection(Node node, String value) {
        if (node == null || value == null || value.isEmpty()) {
            return;
        }
        String keyName = getStopTargetParameterKey(node);
        node.setParameterValueAndPropagate(keyName, value);
        if (isEditingStopTargetField() && inlineFields.getStopTargetEditingNode() == node) {
            inlineFields.replaceStopTargetEditValue(value);
            updateStopTargetFieldContentWidth(getClientTextRenderer());
        }
        if (node.getType() == NodeType.TEMPLATE) {
            NodeGraphData loaded = NodeGraphPersistence.loadNodeGraphForPreset(value);
            NodeGraphData.CustomNodeDefinition definition = NodeGraphPersistence.resolveCustomNodeDefinition(value, loaded);
            node.setTemplateName(definition != null ? definition.getName() : value);
            node.setTemplateVersion(definition != null && definition.getVersion() != null ? definition.getVersion() : 0);
            node.setTemplateGraphData(loaded);
        }
        node.recalculateDimensions();
        notifyNodeParametersChanged(node);
    }

    private boolean isPresetSelectorNode(Node node) {
        return node != null && (node.getType() == NodeType.RUN_PRESET
            || node.getType() == NodeType.TEMPLATE);
    }

    private void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color) {
        if (!shouldRenderNodeText()) {
            return;
        }
        context.drawString(renderer, text, x, y, color, false);
    }

    private void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color) {
        drawNodeText(context, renderer, Component.literal(text), x, y, color);
    }

    private String trimTextToWidth(String text, Font renderer, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (renderer == null) {
            return text;
        }
        TrimKey cacheKey = new TrimKey(text, maxWidth);
        String cached = trimmedTextCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (renderer.width(text) <= maxWidth) {
            trimmedTextCache.put(cacheKey, text);
            return text;
        }
        int safeMaxWidth = Math.max(0, maxWidth);
        String trimmed = TextRenderUtil.trimWithEllipsis(renderer, text, safeMaxWidth);
        trimmedTextCache.put(cacheKey, trimmed);
        return trimmed;
    }

    private void renderSocket(GuiGraphics context, int x, int y, boolean isInput, int color) {
        NodeRenderer.renderSocket(context, x, y, isInput, color);
    }

    private boolean shouldConsiderConnectionForViewport(NodeConnection connection, Set<Node> visibleRoots, int viewportWidth, int viewportHeight) {
        if (connection == null) {
            return false;
        }

        Node outputNode = connection.getOutputNode();
        Node inputNode = connection.getInputNode();
        if (outputNode == null || inputNode == null) {
            return false;
        }

        Node outputRoot = getRootNode(outputNode);
        Node inputRoot = getRootNode(inputNode);
        if ((outputRoot != null && visibleRoots.contains(outputRoot))
            || (inputRoot != null && visibleRoots.contains(inputRoot))) {
            return true;
        }

        SelectionBounds outputBounds = outputRoot != null ? cachedHierarchyBounds.get(outputRoot) : null;
        SelectionBounds inputBounds = inputRoot != null ? cachedHierarchyBounds.get(inputRoot) : null;
        if (outputBounds == null || inputBounds == null) {
            return true;
        }

        SelectionBounds combinedBounds = new SelectionBounds(
            Math.min(outputBounds.minX, inputBounds.minX),
            Math.min(outputBounds.minY, inputBounds.minY),
            Math.max(outputBounds.maxX, inputBounds.maxX),
            Math.max(outputBounds.maxY, inputBounds.maxY)
        );
        return intersectsViewport(combinedBounds, viewportWidth, viewportHeight);
    }

    private boolean isNodeOverSidebarForRender(Node node, int screenX, int screenWidth) {
        if (node == null) {
            return false;
        }
        boolean isOverSidebar = false;
        if (node.isDragging()) {
            if (selectionController.isMultiDragActive() && node.isSelected()) {
                isOverSidebar = isSelectionOverSidebar(sidebarWidthForRendering);
            } else {
                isOverSidebar = isNodeOverSidebar(node, sidebarWidthForRendering, screenX, screenWidth);
            }
        }
        if (!isOverSidebar && selectionController.isSelectionDeletionPreviewActive() && node.isSelected()) {
            isOverSidebar = true;
        }
        if (!isOverSidebar && selectionController.isCascadeDeletionPreviewNode(node)) {
            isOverSidebar = true;
        }
        return isOverSidebar;
    }

    private record TrimKey(String text, int maxWidth) {
    }



    public boolean isPointInsideRuntimeScopeButton(Node node, int screenX, int screenY) {
        return nodeControls.isPointInsideRuntimeScopeButton(node, screenX, screenY);
    }

    public boolean handleRuntimeScopeButtonClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleRuntimeScopeButtonClick(node, mouseX, mouseY);
    }

    public boolean handleBooleanToggleClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleBooleanToggleClick(node, mouseX, mouseY);
    }

    public boolean handleMessageButtonClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleMessageButtonClick(node, mouseX, mouseY);
    }

    public boolean handleBooleanOperatorButtonClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleBooleanOperatorButtonClick(node, mouseX, mouseY);
    }

    public boolean handleMessageScopeToggleClick(Node node, int mouseX, int mouseY) {
        return nodeControls.handleMessageScopeToggleClick(node, mouseX, mouseY);
    }

    public boolean handleOperatorToggleClick(Font textRenderer, int mouseX, int mouseY) {
        return nodeControls.handleOperatorToggleClick(textRenderer, mouseX, mouseY);
    }

    public boolean isPointInsideBookTextButton(Node node, int mouseX, int mouseY) {
        return nodeControls.isPointInsideBookTextButton(node, mouseX, mouseY);
    }

    public boolean isPointInsidePopupEditButton(Node node, int mouseX, int mouseY) {
        return nodeControls.isPointInsidePopupEditButton(node, mouseX, mouseY);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<NodeConnection> getConnections() {
        return connections;
    }
    
    /**
     * Collects the names of all EVENT_FUNCTION nodes currently in the workspace.
     * Returns them in insertion order with duplicates removed.
     */
    public List<String> getFunctionNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Node node : nodes) {
            if (node.getType() != NodeType.EVENT_FUNCTION) {
                continue;
            }
            NodeParameter nameParam = node.getParameter("Name");
            if (nameParam == null) {
                continue;
            }
            String value = nameParam.getStringValue();
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return new ArrayList<>(names);
    }
    
    public int getCameraX() {
        return viewport.getCameraX();
    }
    
    public int getCameraY() {
        return viewport.getCameraY();
    }
    
    public void setSidebarWidth(int sidebarWidth) {
        this.sidebarWidthForRendering = sidebarWidth;
    }
    
    /**
     * Handle node click and detect double-clicks for parameter editing
     * Returns true if a double-click was detected and the popup should open
     */
    public boolean handleNodeClick(Node clickedNode, int mouseX, int mouseY) {
        return selectionController.handleNodeClick(clickedNode);
    }
    
    private boolean isMouseOverStartButton(Node startNode, int mouseX, int mouseY) {
        if (isPointInsideStartModeButton(startNode, mouseX, mouseY)) {
            return false;
        }
        int centerX = startNode.getX() + startNode.getWidth() / 2;
        int centerY = startNode.getY() + startNode.getHeight() / 2;
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        
        return worldMouseX >= centerX - 11 && worldMouseX <= centerX + 11
            && worldMouseY >= centerY - 11 && worldMouseY <= centerY + 11;
    }

    private int getStartModeButtonWorldX(Node startNode) {
        return startNode.getX() + startNode.getWidth() - 12;
    }

    private int getStartModeButtonWorldY(Node startNode) {
        return startNode.getY() + 4;
    }

    private boolean isPointInsideStartModeButton(Node startNode, int mouseX, int mouseY) {
        if (startNode == null || startNode.getType() != NodeType.START) {
            return false;
        }
        int worldMouseX = screenToWorldX(mouseX);
        int worldMouseY = screenToWorldY(mouseY);
        int buttonX = getStartModeButtonWorldX(startNode) - 1;
        int buttonY = getStartModeButtonWorldY(startNode) - 1;
        return worldMouseX >= buttonX && worldMouseX <= buttonX + 12
            && worldMouseY >= buttonY && worldMouseY <= buttonY + 9;
    }

    private Node findStartModeButtonAt(int mouseX, int mouseY) {
        rebuildHierarchyCacheIfNeeded();
        for (Node node : nodes) {
            if (!intersectsViewport(node)) {
                continue;
            }
            if (node.getType() == NodeType.START && isPointInsideStartModeButton(node, mouseX, mouseY)) {
                return node;
            }
        }
        return null;
    }

    public boolean isHoveringStartButton() {
        return hoveringStartButton;
    }

    public boolean isPointInsideInteractiveNodeControl(Node node, int mouseX, int mouseY) {
        if (node == null) {
            return false;
        }

        int worldX = screenToWorldX(mouseX);
        int worldY = screenToWorldY(mouseY);

        if (node.getType() == NodeType.START
            && (isMouseOverStartButton(node, mouseX, mouseY) || isPointInsideStartModeButton(node, mouseX, mouseY))) {
            return true;
        }
        if (node.getType() == NodeType.TEMPLATE && isPointInsideTemplateEditButton(node, mouseX, mouseY)) {
            return true;
        }
        if (nodeControls.isPointInsideBooleanToggle(node, mouseX, mouseY)
            || isPointInsideSchematicField(node, mouseX, mouseY)
            || isPointInsideRunPresetField(node, mouseX, mouseY)
            || isPointInsideScreenCoordinatePickerButton(node, mouseX, mouseY)
            || isPointInsideStickyNoteTextArea(node, mouseX, mouseY)
            || getStickyNoteResizeCornerAt(node, mouseX, mouseY) != null
            || getCoordinateFieldAxisAt(node, mouseX, mouseY) >= 0
            || isPointInsideStopTargetField(node, mouseX, mouseY)
            || isPointInsideVariableField(node, mouseX, mouseY)
            || isPointInsideRandomRoundingToggle(node, mouseX, mouseY)
            || isPointInsideRandomRoundingField(node, mouseX, mouseY)
            || isPointInsideAmountToggle(node, mouseX, mouseY)
            || isPointInsideAmountField(node, mouseX, mouseY)
            || getMessageFieldIndexAt(node, mouseX, mouseY) >= 0
            || getParameterFieldIndexAt(node, mouseX, mouseY) >= 0
            || isPointInsideEventNameField(node, mouseX, mouseY)
            || nodeControls.isPointInsideBookTextButton(node, mouseX, mouseY)
            || nodeControls.isPointInsidePopupEditButton(node, mouseX, mouseY)
            || nodeControls.isPointInsideMessageScopeToggle(node, mouseX, mouseY)) {
            return true;
        }

        if (node.isExpandableBooleanOperator()) {
            int buttonTop = node.getBooleanOperatorButtonTop();
            int addLeft = node.getBooleanOperatorAddButtonLeft();
            int removeLeft = node.getBooleanOperatorRemoveButtonLeft();
            int buttonSize = node.getBooleanOperatorButtonSize();
            if (nodeControls.isPointInsideNodeHeaderButtonWorld(addLeft, buttonTop, buttonSize, worldX, worldY)
                || nodeControls.isPointInsideNodeHeaderButtonWorld(removeLeft, buttonTop, buttonSize, worldX, worldY)) {
                return true;
            }
        }

        if (node.hasMessageInputFields()) {
            int buttonTop = node.getMessageButtonTop();
            int addLeft = node.getMessageAddButtonLeft();
            int removeLeft = node.getMessageRemoveButtonLeft();
            int buttonSize = node.getMessageButtonSize();
            if (nodeControls.isPointInsideNodeHeaderButtonWorld(addLeft, buttonTop, buttonSize, worldX, worldY)
                || nodeControls.isPointInsideNodeHeaderButtonWorld(removeLeft, buttonTop, buttonSize, worldX, worldY)) {
                return true;
            }
        }

        if (nodeControls.isCombinedDirectionNode(node)) {
            int fieldLeft = nodeControls.getParameterFieldLeft(node);
            int fieldTop = nodeControls.getDirectionModeTabTop(node);
            int fieldWidth = nodeControls.getParameterFieldWidth(node);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return true;
            }
        }

        if (nodeControls.isCombinedBooleanNode(node)) {
            int fieldLeft = nodeControls.getParameterFieldLeft(node);
            int fieldTop = nodeControls.getBooleanModeTabTop(node);
            int fieldWidth = nodeControls.getParameterFieldWidth(node);
            if (worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
                && worldY >= fieldTop && worldY <= fieldTop + DIRECTION_MODE_TAB_HEIGHT) {
                return true;
            }
        }

        return isInlineDropdownParameter(node, getParameterFieldIndexAt(node, mouseX, mouseY))
            || isPointInsideModeField(node, mouseX, mouseY);
    }

    public boolean handleStartButtonClick(int mouseX, int mouseY) {
        lastStartButtonTriggeredExecution = false;
        Node startNode = findStartNodeAt(mouseX, mouseY);
        if (startNode == null) {
            return false;
        }

        stopCoordinateEditing(true);
        stopAmountEditing(true);

        hoveredStartNode = startNode;

        ExecutionManager manager = ExecutionManager.getInstance();
        manager.setWorkspaceGraph(nodes, connections, routineWorkspace.routineRegistry());
        if (manager.isChainActive(startNode)) {
            return manager.requestStopForStart(startNode);
        }

        boolean started = manager.executeBranch(
            startNode, nodes, connections, workspace.getActivePreset());
        if (started) {
            lastStartButtonTriggeredExecution = true;
        }
        return started;
    }

    private Node findStartNodeAt(int mouseX, int mouseY) {
        rebuildHierarchyCacheIfNeeded();
        for (Node node : nodes) {
            if (!intersectsViewport(node)) {
                continue;
            }
            if (node.getType() == NodeType.START && isMouseOverStartButton(node, mouseX, mouseY)) {
                return node;
            }
        }
        return null;
    }
    
    
    /**
     * Check if a node should show parameters (Start and End nodes don't)
     */
    public boolean shouldShowParameters(Node node) {
        if (node == null) {
            return false;
        }
        if (rendersInlineParameters(node)) {
            return node.hasParameters() || node.supportsModeSelection();
        }
        return false;
    }

    public boolean didLastStartButtonTriggerExecution() {
        return lastStartButtonTriggeredExecution;
    }

    public void setExecutionEnabled(boolean enabled) {
        this.executionEnabled = enabled;
    }

    public boolean containsBaritoneNodes() {
        for (Node node : nodes) {
            if (node != null && node.getType() != null && node.getType().requiresBaritone()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsUiUtilsNodes() {
        for (Node node : nodes) {
            if (node != null && node.getType() != null && node.getType().requiresUiUtils()) {
                return true;
            }
        }
        return false;
    }

    private void updateCascadeDeletionPreview() {
        selectionController.updateCascadeDeletionPreview();
    }
    
    /**
     * Save the current node graph to disk
     */
    public boolean save() {
        return workspace.save();
    }

    public void setWorkspaceSaveHandler(java.util.function.BooleanSupplier workspaceSaveHandler) {
        workspace.setWorkspaceSaveHandler(workspaceSaveHandler);
    }

    /**
     * Load a node graph from disk, replacing the current one
     */
    public boolean load() {
        return workspace.load();
    }

    public boolean importFromPath(Path savePath) {
        return workspace.importFromPath(savePath);
    }

    public boolean exportToPath(Path savePath) {
        return workspace.exportToPath(savePath);
    }

    public void markWorkspaceDirty() {
        workspace.markWorkspaceDirty();
    }

    public void markWorkspaceClean() {
        workspace.markWorkspaceClean();
    }

    public boolean isWorkspaceDirty() {
        return workspace.isWorkspaceDirty();
    }

    public void notifyNodeParametersChanged(Node node) {
        workspace.notifyNodeParametersChanged(node);
    }

    public GraphValidationResult getValidationResult(boolean baritoneAvailable, boolean uiUtilsAvailable) {
        return workspace.getValidationResult(baritoneAvailable, uiUtilsAvailable);
    }

    private void invalidateValidation() {
        workspace.invalidateValidation();
    }

    public void clearWorkspace() {
        graphLoader.clearWorkspace();
    }

    private boolean applyLoadedData(NodeGraphData data) {
        return graphLoader.applyLoadedData(data);
    }

    /**
     * Check if there's a saved node graph available
     */
    public boolean hasSavedGraph() {
        return workspace.hasSavedGraph();
    }

    public NodeGraphData exportGraphDataSnapshot() {
        return workspace.exportGraphDataSnapshot();
    }

    public boolean applyGraphDataSnapshot(NodeGraphData data, boolean markDirty) {
        return workspace.applyGraphDataSnapshot(data, markDirty);
    }

    public void setActivePreset(String presetName) {
        workspace.setActivePreset(presetName);
    }

    public String getActivePreset() {
        return workspace.getActivePreset();
    }

    public void setActiveRoutineWorkspaceId(String routineId) {
        routineWorkspace.setActiveRoutineWorkspaceId(routineId);
    }

    public String getActiveRoutineWorkspaceId() {
        return routineWorkspace.getActiveRoutineWorkspaceId();
    }

    public List<NodeGraphData.RoutineDefinitionData> getRoutineDefinitions() {
        return routineWorkspace.getRoutineDefinitions();
    }

    public void setRoutineValidationContext(List<NodeGraphData.RoutineDefinitionData> routines) {
        routineWorkspace.setRoutineValidationContext(routines);
    }

    /** Mirrors live routine card edits into sidebar metadata, including the current uncommitted text buffer. */
    public void syncRoutineDefinitionMetadata(NodeGraphData.RoutineDefinitionData routine) {
        routineWorkspace.syncRoutineDefinitionMetadata(routine);
    }

    private void syncRoutineInvocations() {
        routineWorkspace.syncRoutineInvocations();
    }

    private String liveRoutineParameterValue(Node node, String parameterName) {
        NodeParameter parameter = node.getParameter(parameterName);
        String value = parameter == null ? "" : parameter.getStringValue();
        if (parameterEditor.getNode() == node && parameterEditor.getIndex() >= 0
            && parameterEditor.getIndex() < node.getParameters().size()
            && node.getParameters().get(parameterEditor.getIndex()) == parameter) {
            value = parameterEditor.getBuffer();
        }
        if (inlineFields.getEventNameEditingNode() == node && "Name".equals(parameterName)) {
            value = inlineFields.getEventNameEditor().getBuffer();
        }
        return value == null ? "" : value;
    }
}
