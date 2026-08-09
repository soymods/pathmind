package com.pathmind.screen;

import com.pathmind.PathmindCommon;
//? if PRE_1_21_11 {
/*// CharacterEvent exposes modifiers directly before 1.21.11.*/
//?} else {
import com.pathmind.compat.CharacterEventModifiers;
//?}
import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeCategory;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.PathmindTextField;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindSettingsRowRenderer;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.control.PathmindRoutineUi;
import com.pathmind.ui.control.PathmindWorkspaceChrome;
import com.pathmind.ui.control.UiHitTest;
import com.pathmind.ui.graph.NodeGraph;
import com.pathmind.ui.menu.ContextMenuSelection;
import com.pathmind.ui.overlay.BookTextEditorOverlay;
import com.pathmind.ui.overlay.NodeErrorNotificationOverlay;
import com.pathmind.ui.overlay.NodeParameterOverlay;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.PathmindI18n;
import com.pathmind.validation.GraphValidationResult;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.util.ScrollbarHelper;
import com.pathmind.util.TextRenderUtil;
import com.pathmind.util.LoaderMetadata;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.OverlayProtection;
import com.pathmind.util.UiUtilsProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
//? if MC_1_21_8 {
/*// Legacy screen input callbacks use primitive parameters.*/
//?} else {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The main visual editor screen for Pathmind.
 * This screen provides the interface for creating and editing node-based workflows.
 */
public class PathmindVisualEditorScreen extends Screen {
    static String tr(String key, Object... args) {
        return PathmindI18n.tr(key, args);
    }

    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int PRESET_MENU_BUTTON_SIZE = 18;
    
    // Colors now come from UITheme for consistency
    private static final int BOTTOM_BUTTON_SIZE = 18;
    private static final int BOTTOM_BUTTON_MARGIN = 6;
    private static final int BOTTOM_BUTTON_SPACING = 6;
    private static final int MARKETPLACE_BUTTON_WIDTH = BOTTOM_BUTTON_SIZE * 3 + BOTTOM_BUTTON_SPACING * 2;
    static final int CREATE_PRESET_POPUP_WIDTH = 320;
    static final int CREATE_PRESET_POPUP_HEIGHT = 170;
    static final int PUBLISH_PRESET_POPUP_WIDTH = 380;
    static final int PUBLISH_PRESET_POPUP_HEIGHT = 272;
    private static final int INFO_POPUP_WIDTH = 320;
    private static final int INFO_POPUP_HEIGHT = 180;
    static final int PRESET_DELETE_POPUP_WIDTH = 320;
    static final int PRESET_DELETE_POPUP_HEIGHT = 160;
    static final int PRESET_DELETE_SKIP_CHECKBOX_SIZE = 10;
    private static final int MISSING_BARITONE_POPUP_WIDTH = 360;
    private static final int MISSING_BARITONE_POPUP_HEIGHT = 175;
    private static final int MISSING_UI_UTILS_POPUP_WIDTH = 360;
    private static final int MISSING_UI_UTILS_POPUP_HEIGHT = 175;
    static final int TEXT_FIELD_VERTICAL_PADDING = 3;
    private static final int NODE_SEARCH_FIELD_WIDTH = 180;
    private static final Component TITLE_TEXT = Component.literal("Pathmind");

    NodeGraph nodeGraph;
    private Sidebar sidebar;
    private NodeParameterOverlay parameterOverlay;
    private BookTextEditorOverlay bookTextEditorOverlay;
    private final boolean baritoneAvailable;
    private final boolean uiUtilsAvailable;

    private final PathmindWorkspaceDragController workspaceDragController =
        new PathmindWorkspaceDragController(new WorkspaceDragHost());
    private final PathmindNodeInteractionController nodeInteractionController =
        new PathmindNodeInteractionController(new NodeInteractionHost());
    private final PathmindNodeSearchController nodeSearchController =
        new PathmindNodeSearchController(new NodeSearchHost());

    // Workspace dialogs
    private final PopupAnimationHandler clearPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler importExportPopupAnimation = new PopupAnimationHandler();
    private final PathmindWorkspaceDialogController workspaceDialogController;

    private final PathmindPresetDropdownController presetDropdownController =
        new PathmindPresetDropdownController(new PresetDropdownHost());
    private final AnimatedValue titleUnderlineAnimation = AnimatedValue.forHover();
    private final AnimatedValue routineWorkspaceAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    private final PathmindPresetWorkspaceController presetWorkspaceController =
        new PathmindPresetWorkspaceController(new PresetWorkspaceHost());
    private final PathmindPresetTabController presetTabController =
        new PathmindPresetTabController(new PresetTabHost());
    private final PathmindPresetContextMenuController presetContextMenuController =
        new PathmindPresetContextMenuController(new PresetContextMenuHost());
    private final Map<PopupAnimationHandler, Integer> popupScrollOffsets = new IdentityHashMap<>();
    private EditBox inlinePresetRenameField;
    private final PopupAnimationHandler infoPopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler missingBaritonePopupAnimation = new PopupAnimationHandler();
    private final PopupAnimationHandler missingUiUtilsPopupAnimation = new PopupAnimationHandler();
    private final PathmindSettingsPopupController settingsPopupController =
        new PathmindSettingsPopupController(new SettingsPopupHost());
    private final PathmindPresetPopupController presetPopupController =
        new PathmindPresetPopupController(new PresetPopupHost());
    private final PathmindModalOverlayController modalOverlayController = new PathmindModalOverlayController(
        new ModalOverlayHost(),
        clearPopupAnimation,
        importExportPopupAnimation,
        presetPopupController.createAnimation(),
        presetPopupController.publishAnimation(),
        presetPopupController.renameAnimation(),
        presetPopupController.deleteAnimation(),
        infoPopupAnimation,
        missingBaritonePopupAnimation,
        missingUiUtilsPopupAnimation,
        settingsPopupController.animation()
    );
    private final PathmindFirstRunTutorialController firstRunTutorialController =
        new PathmindFirstRunTutorialController(new FirstRunTutorialHost());
    private final PathmindWorkspaceLifecycleController workspaceLifecycleController =
        new PathmindWorkspaceLifecycleController(new WorkspaceLifecycleHost());
    private final PathmindValidationExecutionController validationExecutionController =
        new PathmindValidationExecutionController(new ValidationExecutionHost());
    private Boolean uiUtilsOverlayPrevEnabled = null;
    private final PathmindWorkspaceViewportController workspaceViewportController =
        new PathmindWorkspaceViewportController(new WorkspaceViewportHost());

    private final class SettingsPopupHost implements PathmindSettingsPopupController.Host {
        @Override
        public Minecraft client() {
            return minecraft;
        }

        @Override
        public Font font() {
            return font;
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int boundedPopupWidth(int requestedWidth) {
            return getBoundedPopupWidth(requestedWidth);
        }

        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public void addWidget(EditBox field) {
            PathmindVisualEditorScreen.this.addWidget(field);
        }

        @Override
        public void setOverlayCutout(int x, int y, int width, int height) {
            PathmindVisualEditorScreen.this.setOverlayCutout(x, y, width, height);
        }

        @Override
        public void drawPopupTextWithEllipsis(GuiGraphics context, String text, int x, int y, int maxWidth, int color) {
            PathmindVisualEditorScreen.this.drawPopupTextWithEllipsis(context, text, x, y, maxWidth, color);
        }

        @Override
        public float hoverProgress(Object key, boolean hovered) {
            return getHoverProgress(key, hovered);
        }

        @Override
        public boolean isPointInRect(int pointX, int pointY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(pointX, pointY, x, y, width, height);
        }

        @Override
        public void refreshAvailablePresets() {
            PathmindVisualEditorScreen.this.refreshAvailablePresets();
        }

        @Override
        public void replayFirstRunTutorial() {
            PathmindVisualEditorScreen.this.replayFirstRunTutorial();
        }

        @Override
        public void reopenForLanguageChange() {
            PathmindVisualEditorScreen.this.reopenForLanguageChange();
        }
    }

    private final class PresetPopupHost implements PathmindPresetPopupController.Host {
        @Override
        public PathmindVisualEditorScreen editorScreen() {
            return PathmindVisualEditorScreen.this;
        }

        @Override
        public Minecraft client() {
            return minecraft;
        }

        @Override
        public Font textRenderer() {
            return font;
        }

        @Override
        public void addWidget(EditBox field) {
            PathmindVisualEditorScreen.this.addWidget(field);
        }

        @Override
        public String activePresetName() {
            return presetWorkspaceController.activePresetName();
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void closeInfoPopup() {
            PathmindVisualEditorScreen.this.closeInfoPopup();
        }

        @Override
        public void closeSettingsPopup() {
            settingsPopupController.close();
        }

        @Override
        public void stopInlinePresetRename(boolean save) {
            presetTabController.stopInlinePresetRename(save);
        }

        @Override
        public void saveRootPresetWorkspace() {
            workspaceLifecycleController.saveRootPresetWorkspace();
        }

        @Override
        public int getAccentColor() {
            return PathmindVisualEditorScreen.this.getAccentColor();
        }

        @Override
        public String getCurrentMinecraftVersion() {
            return PathmindVisualEditorScreen.this.getCurrentMinecraftVersion();
        }

        @Override
        public String getModVersion() {
            return PathmindVisualEditorScreen.this.getModVersion();
        }

        @Override
        public String fallback(String value, String fallback) {
            return PathmindVisualEditorScreen.this.fallback(value, fallback);
        }

        @Override
        public boolean isPointInRect(int pointX, int pointY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(pointX, pointY, x, y, width, height);
        }

        @Override
        public int[] getBoundedScaledPopupBounds(PopupAnimationHandler animation, int width, int height) {
            return PathmindVisualEditorScreen.this.getBoundedScaledPopupBounds(animation, width, height);
        }

        @Override
        public int getBoundedPopupContentY(int popupY, PopupAnimationHandler animation, int height) {
            return PathmindVisualEditorScreen.this.getBoundedPopupContentY(popupY, animation, height);
        }

        @Override
        public void resetBoundedPopupScroll(PopupAnimationHandler animation) {
            PathmindVisualEditorScreen.this.resetBoundedPopupScroll(animation);
        }

        @Override
        public boolean handleBoundedPopupScroll(double mouseX, double mouseY, double verticalAmount,
                                                PopupAnimationHandler animation, int width, int height) {
            return PathmindVisualEditorScreen.this.handleBoundedPopupScroll(
                mouseX, mouseY, verticalAmount, animation, width, height);
        }

        @Override
        public int getPopupAnimatedColor(PopupAnimationHandler animation, int color) {
            return PathmindVisualEditorScreen.this.getPopupAnimatedColor(animation, color);
        }

        @Override
        public void setOverlayCutout(int x, int y, int width, int height) {
            PathmindVisualEditorScreen.this.setOverlayCutout(x, y, width, height);
        }

        @Override
        public void drawPopupTextWithEllipsis(GuiGraphics context, String text, int x, int y,
                                              int maxWidth, int color) {
            PathmindVisualEditorScreen.this.drawPopupTextWithEllipsis(context, text, x, y, maxWidth, color);
        }

        @Override
        public boolean renameOpenLibraryRoutine(String routineId, String routineName) {
            return workspaceLifecycleController.renameOpenLibraryRoutine(routineId, routineName);
        }

        @Override
        public boolean isDuplicateRoutineName(String routineName, String ignoredRoutineId) {
            return workspaceLifecycleController.isDuplicateRoutineName(routineName, ignoredRoutineId);
        }

        @Override
        public void renameRoutine(String routineId, String routineName) {
            workspaceLifecycleController.renameRoutine(routineId, routineName);
        }

        @Override
        public void createRoutineFromSidebar(String routineName) {
            PathmindVisualEditorScreen.this.createRoutineFromSidebar(routineName);
        }

        @Override
        public void switchPreset(String presetName) {
            presetWorkspaceController.switchPreset(presetName);
        }

        @Override
        public boolean renamePreset(String oldName, String newName) {
            return presetWorkspaceController.renamePreset(oldName, newName);
        }

        @Override
        public void deletePreset(String presetName) {
            presetWorkspaceController.queuePresetDeletion(presetName);
        }

        @Override
        public boolean skipPresetDeleteConfirm() {
            return settingsPopupController.skipPresetDeleteConfirm();
        }

        @Override
        public void setSkipPresetDeleteConfirm(boolean skip) {
            settingsPopupController.setSkipPresetDeleteConfirm(skip);
        }
    }

    private final class NodeSearchHost implements PathmindNodeSearchController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int sidebarWidth() {
            return sidebar.getWidth();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public float zoomScale() {
            return nodeGraph.getZoomScale();
        }

        @Override
        public int screenToWorldX(int screenX) {
            return nodeGraph.screenToWorldX(screenX);
        }

        @Override
        public int screenToWorldY(int screenY) {
            return nodeGraph.screenToWorldY(screenY);
        }

        @Override
        public int worldToScreenX(int worldX) {
            return nodeGraph.worldToScreenX(worldX);
        }

        @Override
        public int worldToScreenY(int worldY) {
            return nodeGraph.worldToScreenY(worldY);
        }

        @Override
        public boolean isNodeAvailable(NodeType nodeType) {
            return sidebar.isNodeAvailable(nodeType);
        }

        @Override
        public boolean baritoneAvailable() {
            return baritoneAvailable;
        }

        @Override
        public boolean uiUtilsAvailable() {
            return uiUtilsAvailable;
        }

        @Override
        public List<NodeGraphData.RoutineDefinitionData> rootRoutines() {
            return workspaceLifecycleController.getRootRoutines();
        }

        @Override
        public boolean shouldBlockBaritoneNode(NodeType nodeType) {
            return PathmindVisualEditorScreen.this.shouldBlockBaritoneNode(nodeType);
        }

        @Override
        public boolean shouldBlockUiUtilsNode(NodeType nodeType) {
            return PathmindVisualEditorScreen.this.shouldBlockUiUtilsNode(nodeType);
        }

        @Override
        public void addNode(NodeType nodeType) {
            nodeGraph.addNodeFromContextMenu(nodeType);
        }

        @Override
        public void addRoutine(NodeGraphData.RoutineDefinitionData routine) {
            nodeGraph.addRoutineFromContextMenu(routine);
        }

        @Override
        public EditBox createSearchField(Runnable responder) {
            EditBox field = PathmindTextField.createInactive(font, 0, 0, 180, 22,
                Component.translatable("pathmind.search.nodes"), 64);
            field.setHeight(Math.max(10, 22 - TEXT_FIELD_VERTICAL_PADDING * 2));
            field.setResponder(value -> responder.run());
            addWidget(field);
            return field;
        }
    }

    private final class ValidationExecutionHost implements PathmindValidationExecutionController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int sidebarWidth() {
            return sidebar.getWidth();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public boolean hasActiveRoutineWorkspace() {
            return getActiveRoutineWorkspace() != null;
        }

        @Override
        public boolean isPopupObscuringWorkspace() {
            return PathmindVisualEditorScreen.this.isPopupObscuringWorkspace();
        }

        @Override
        public boolean showWorkspaceTooltips() {
            return settingsPopupController.showWorkspaceTooltips();
        }

        @Override
        public GraphValidationResult validationResult() {
            return nodeGraph.getValidationResult(baritoneAvailable, uiUtilsAvailable);
        }

        @Override
        public float hoverProgress(Object key, boolean hovered) {
            return getHoverProgress(key, hovered);
        }

        @Override
        public void openRoutineWorkspace(String routineId) {
            openRoutineWorkspaceTab(routineId);
        }

        @Override
        public String activeRoutineWorkspaceId() {
            return nodeGraph.getActiveRoutineWorkspaceId();
        }

        @Override
        public void focusNode(String nodeId) {
            nodeGraph.focusNodeById(nodeId, width, height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void switchToRootWorkspace() {
            switchToWorkspaceTab(0);
        }

        @Override
        public void startExecutingAllGraphs() {
            PathmindVisualEditorScreen.this.startExecutingAllGraphs();
        }

        @Override
        public void stopExecutingAllGraphs() {
            ExecutionManager.getInstance().requestStopAll();
        }
    }

    private final class PresetWorkspaceHost implements PathmindPresetWorkspaceController.Host {
        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public Settings settings() {
            return settingsPopupController.settings();
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int sidebarWidth() {
            return sidebar.getWidth();
        }

        @Override
        public int titleBarHeight() {
            return TITLE_BAR_HEIGHT;
        }

        @Override
        public void stopInlinePresetRename(boolean commit) {
            presetTabController.stopInlinePresetRename(commit);
        }

        @Override
        public void refreshPresetTabs() {
            presetTabController.refreshAvailablePresets();
        }

        @Override
        public void movePresetTabToEnd(String presetName) {
            presetTabController.movePresetTabToEnd(presetName);
        }

        @Override
        public void queueAnimatedPresetDeletion(String presetName) {
            presetTabController.queueAnimatedPresetDeletion(presetName);
        }

        @Override
        public void persistActiveWorkspaceToTabs() {
            workspaceLifecycleController.persistActiveWorkspaceToTabs();
        }

        @Override
        public void syncAllTemplateTabsIntoParents() {
            workspaceLifecycleController.syncAllTemplateTabsIntoParents();
        }

        @Override
        public void restoreRootWorkspaceIfNeeded() {
            workspaceLifecycleController.restoreRootWorkspaceIfNeeded();
        }

        @Override
        public boolean saveRootPresetWorkspace() {
            return workspaceLifecycleController.saveRootPresetWorkspace();
        }

        @Override
        public void dismissParameterOverlay() {
            PathmindVisualEditorScreen.this.dismissParameterOverlay();
        }

        @Override
        public void clearWorkspaceDrag() {
            workspaceDragController.clearSidebarDrag();
        }

        @Override
        public boolean isImportExportPopupVisible() {
            return importExportPopupAnimation.isVisible();
        }

        @Override
        public void closeImportExportPopup() {
            workspaceDialogController.closeImportExportPopup();
        }

        @Override
        public boolean isCreatePresetPopupVisible() {
            return presetPopupController.createVisible();
        }

        @Override
        public void closeCreatePresetPopup() {
            PathmindVisualEditorScreen.this.closeCreatePresetPopup();
        }

        @Override
        public boolean isRenamePresetPopupVisible() {
            return presetPopupController.renameVisible();
        }

        @Override
        public void closeRenamePresetPopup() {
            PathmindVisualEditorScreen.this.closeRenamePresetPopup();
        }

        @Override
        public void hideClearPopup() {
            clearPopupAnimation.hide();
        }

        @Override
        public void closeSettingsPopup() {
            PathmindVisualEditorScreen.this.closeSettingsPopup();
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void clearImportExportStatus() {
            workspaceDialogController.clearImportExportStatus();
        }

        @Override
        public void resetWorkspaceTabsFromCurrentGraph() {
            workspaceLifecycleController.resetFromCurrentGraph();
        }

        @Override
        public void refreshMissingBaritonePopup() {
            workspaceDialogController.refreshMissingBaritonePopup();
        }

        @Override
        public void refreshMissingUiUtilsPopup() {
            workspaceDialogController.refreshMissingUiUtilsPopup();
        }

        @Override
        public void updateImportExportPathFromPreset() {
            workspaceDialogController.updateImportExportPathFromPreset();
        }
    }

    private final class PresetTabHost implements PathmindPresetTabController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public int titleTextX() {
            return getTitleTextX();
        }

        @Override
        public int presetOverflowTabRight() {
            return getPresetOverflowTabRight();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public boolean isPopupObscuringWorkspace() {
            return PathmindVisualEditorScreen.this.isPopupObscuringWorkspace();
        }

        @Override
        public List<String> availablePresets() {
            return presetWorkspaceController.availablePresets();
        }

        @Override
        public String activePresetName() {
            return presetWorkspaceController.activePresetName();
        }

        @Override
        public Settings settings() {
            return settingsPopupController.settings();
        }

        @Override
        public EditBox inlinePresetRenameField() {
            return inlinePresetRenameField;
        }

        @Override
        public boolean isPresetDeleteDisabled(String presetName) {
            return PathmindVisualEditorScreen.this.isPresetDeleteDisabled(presetName);
        }

        @Override
        public void openPresetDeletePopup(String presetName) {
            PathmindVisualEditorScreen.this.openPresetDeletePopup(presetName);
        }

        @Override
        public void openCreatePresetPopup() {
            PathmindVisualEditorScreen.this.openCreatePresetPopup();
        }

        @Override
        public void closeCreatePresetPopup() {
            PathmindVisualEditorScreen.this.closeCreatePresetPopup();
        }

        @Override
        public void closeRenamePresetPopup() {
            PathmindVisualEditorScreen.this.closeRenamePresetPopup();
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void switchPreset(String presetName) {
            PathmindVisualEditorScreen.this.switchPreset(presetName);
        }

        @Override
        public boolean renamePresetInternal(String currentName, String desiredName) {
            return PathmindVisualEditorScreen.this.renamePresetInternal(currentName, desiredName);
        }

        @Override
        public void attemptDeletePresetImmediate(String presetName) {
            PathmindVisualEditorScreen.this.attemptDeletePresetImmediate(presetName);
        }
    }

    private final class PresetDropdownHost implements PathmindPresetDropdownController.Host {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int titleTextX() {
            return getTitleTextX();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public Font textRenderer() {
            return font;
        }

        @Override
        public List<String> availablePresets() {
            return presetWorkspaceController.availablePresets();
        }

        @Override
        public String activePresetName() {
            return presetWorkspaceController.activePresetName();
        }

        @Override
        public boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(mouseX, mouseY, x, y, width, height);
        }

        @Override
        public void openRenamePresetPopup(String presetName) {
            PathmindVisualEditorScreen.this.openRenamePresetPopup(presetName);
        }

        @Override
        public void openPresetDeletePopup(String presetName) {
            PathmindVisualEditorScreen.this.openPresetDeletePopup(presetName);
        }

        @Override
        public void openCreatePresetPopup() {
            PathmindVisualEditorScreen.this.openCreatePresetPopup();
        }

        @Override
        public void beginPresetDropdownDrag(String presetName, int mouseX, int mouseY) {
            presetTabController.beginPresetDropdownDrag(presetName, mouseX, mouseY);
        }
    }

    private final class PresetContextMenuHost implements PathmindPresetContextMenuController.Host {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public Font textRenderer() {
            return font;
        }

        @Override
        public boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(mouseX, mouseY, x, y, width, height);
        }

        @Override
        public String presetTabAt(int mouseX, int mouseY) {
            return presetTabController.getPresetTabAt(mouseX, mouseY);
        }

        @Override
        public String presetGroupAt(int mouseX, int mouseY) {
            return presetTabController.getPresetGroupAt(mouseX, mouseY);
        }

        @Override
        public String presetGroupKey(String presetName) {
            return presetTabController.getPresetGroupKey(presetName);
        }

        @Override
        public int presetGroupColor(String presetName) {
            return presetTabController.getPresetGroupColor(presetName);
        }

        @Override
        public String presetGroupColorLabel(String key) {
            return presetTabController.getPresetGroupColorLabel(key);
        }

        @Override
        public String nextPresetGroupColorKey() {
            return presetTabController.getNextPresetGroupColorKey();
        }

        @Override
        public boolean isPresetRenameDisabled(String presetName) {
            return PathmindVisualEditorScreen.this.isPresetRenameDisabled(presetName);
        }

        @Override
        public boolean isPresetDeleteDisabled(String presetName) {
            return PathmindVisualEditorScreen.this.isPresetDeleteDisabled(presetName);
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void closeGraphContextMenus() {
            nodeGraph.closeContextMenu();
            nodeGraph.closeNodeContextMenu();
        }

        @Override
        public void openCreatePresetPopup() {
            PathmindVisualEditorScreen.this.openCreatePresetPopup();
        }

        @Override
        public void createPresetGroup() {
            presetTabController.createPresetGroup();
        }

        @Override
        public void deletePresetGroup(String groupKey) {
            presetTabController.deletePresetGroup(groupKey);
        }

        @Override
        public void recolorPresetGroup(String oldKey, String newKey) {
            presetTabController.recolorPresetGroup(oldKey, newKey);
        }

        @Override
        public void openRenamePresetPopup(String presetName) {
            PathmindVisualEditorScreen.this.openRenamePresetPopup(presetName);
        }

        @Override
        public void openPresetDeletePopup(String presetName) {
            PathmindVisualEditorScreen.this.openPresetDeletePopup(presetName);
        }

        @Override
        public void setPresetGroupColor(String presetName, String colorKey) {
            presetTabController.setPresetGroupColor(presetName, colorKey);
        }
    }

    private final class ModalOverlayHost implements PathmindModalOverlayController.Host {
        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public boolean isParameterOverlayVisible() {
            return parameterOverlay != null && parameterOverlay.isVisible();
        }

        @Override
        public boolean isBookTextEditorVisible() {
            return bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible();
        }

        @Override
        public int parameterOverlayScrimColor() {
            return parameterOverlay != null ? parameterOverlay.getScrimColor() : UITheme.OVERLAY_BACKGROUND;
        }

        @Override
        public int bookTextEditorScrimColor() {
            return bookTextEditorOverlay != null ? bookTextEditorOverlay.getScrimColor() : UITheme.OVERLAY_BACKGROUND;
        }

        @Override
        public int[] parameterOverlayBounds() {
            return parameterOverlay != null ? parameterOverlay.getScaledPopupBounds() : null;
        }

        @Override
        public int[] bookTextEditorBounds() {
            return bookTextEditorOverlay != null ? bookTextEditorOverlay.getScaledPopupBounds() : null;
        }
    }

    private final class FirstRunTutorialHost implements PathmindFirstRunTutorialController.Host {
        @Override
        public boolean isScreenPopupVisible() {
            return PathmindVisualEditorScreen.this.isScreenPopupVisible();
        }

        @Override
        public boolean isScreenCoordinateCaptureActive() {
            return nodeGraph.isScreenCoordinateCaptureActive();
        }

        @Override
        public boolean isParameterOverlayVisible() {
            return parameterOverlay != null && parameterOverlay.isVisible();
        }

        @Override
        public boolean isBookTextEditorVisible() {
            return bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible();
        }

        @Override
        public void switchPreset(String presetName) {
            PathmindVisualEditorScreen.this.switchPreset(presetName);
        }

        @Override
        public void closeSettingsPopup() {
            PathmindVisualEditorScreen.this.closeSettingsPopup();
        }

        @Override
        public void hideSettingsPopupInstantly() {
            settingsPopupController.animation().hideInstant();
        }

        @Override
        public void focusNodeById(String nodeId) {
            nodeGraph.focusNodeById(nodeId, width, height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        }

        @Override
        public int screenWidth() {
            return width;
        }

        @Override
        public int screenHeight() {
            return height;
        }

        @Override
        public int sidebarWidth() {
            return sidebar.getWidth();
        }

        @Override
        public int titleTextX() {
            return getTitleTextX();
        }

        @Override
        public int presetTabRightLimit() {
            return presetTabController.getPresetTabRightLimit();
        }

        @Override
        public int stopButtonX() {
            return getStopButtonX();
        }

        @Override
        public int stopButtonY() {
            return getStopButtonY();
        }

        @Override
        public int playButtonX() {
            return getPlayButtonX();
        }

        @Override
        public int validationButtonX() {
            return getValidationButtonX();
        }

        @Override
        public int validationButtonY() {
            return getValidationButtonY();
        }

        @Override
        public int publishButtonX() {
            return getPublishButtonX();
        }

        @Override
        public int marketplaceButtonX() {
            return getMarketplaceButtonX();
        }

        @Override
        public int workspaceButtonY() {
            return getWorkspaceButtonY();
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public float zoomScale() {
            return nodeGraph.getZoomScale();
        }

        @Override
        public List<Node> nodes() {
            return nodeGraph.getNodes();
        }

        @Override
        public int worldToScreenX(int worldX) {
            return nodeGraph.worldToScreenX(worldX);
        }

        @Override
        public int worldToScreenY(int worldY) {
            return nodeGraph.worldToScreenY(worldY);
        }
    }

    private final class WorkspaceLifecycleHost implements PathmindWorkspaceLifecycleController.Host {
        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public Sidebar sidebar() {
            return sidebar;
        }

        @Override
        public String activePresetName() {
            return presetWorkspaceController.activePresetName();
        }

        @Override
        public void openRenameRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
            PathmindVisualEditorScreen.this.openRenameRoutinePopup(routine);
        }

        @Override
        public void openRenameLibraryRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
            PathmindVisualEditorScreen.this.openRenameLibraryRoutinePopup(routine);
        }

        @Override
        public int routineExitButtonX() {
            return getRoutineExitButtonX();
        }

        @Override
        public int routineExitButtonY() {
            return getRoutineExitButtonY();
        }

        @Override
        public float hoverProgress(String key, boolean hovered) {
            return getHoverProgress(key, hovered);
        }
    }

    private final class WorkspaceDialogHost implements PathmindWorkspaceDialogController.Host {
        @Override
        public Font font() {
            return font;
        }

        @Override
        public boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
            return PathmindVisualEditorScreen.this.isPointInRect(mouseX, mouseY, x, y, width, height);
        }

        @Override
        public int[] boundedScaledPopupBounds(PopupAnimationHandler animation, int width, int height) {
            return getBoundedScaledPopupBounds(animation, width, height);
        }

        @Override
        public int boundedPopupContentY(int popupY, PopupAnimationHandler animation, int preferredHeight) {
            return getBoundedPopupContentY(popupY, animation, preferredHeight);
        }

        @Override
        public void setOverlayCutout(int x, int y, int width, int height) {
            PathmindVisualEditorScreen.this.setOverlayCutout(x, y, width, height);
        }

        @Override
        public void drawPopupContainer(GuiGraphics context, int x, int y, int width, int height,
                                       PopupAnimationHandler animation) {
            PathmindVisualEditorScreen.this.drawPopupContainer(context, x, y, width, height, animation);
        }

        @Override
        public boolean enablePopupScissor(GuiGraphics context, int popupX, int popupY, int width, int height) {
            return PathmindVisualEditorScreen.this.enablePopupScissor(context, popupX, popupY, width, height);
        }

        @Override
        public void disablePopupScissor(GuiGraphics context, boolean enabled) {
            PathmindVisualEditorScreen.this.disablePopupScissor(context, enabled);
        }

        @Override
        public int popupAnimatedColor(PopupAnimationHandler animation, int color) {
            return getPopupAnimatedColor(animation, color);
        }

        @Override
        public void drawPopupTextWithEllipsis(
            GuiGraphics context, String text, int x, int y, int maxWidth, int color
        ) {
            PathmindVisualEditorScreen.this.drawPopupTextWithEllipsis(
                context, text, x, y, maxWidth, color);
        }

        @Override
        public void drawPopupCenteredTextWithEllipsis(
            GuiGraphics context, String text, int centerX, int y, int maxWidth, int color
        ) {
            PathmindVisualEditorScreen.this.drawPopupCenteredTextWithEllipsis(
                context, text, centerX, y, maxWidth, color);
        }

        @Override
        public void drawPopupButton(
            GuiGraphics context, int x, int y, int width, int height, boolean hovered,
            Component label, PathmindPopupRenderer.ButtonStyle style, PopupAnimationHandler animation
        ) {
            PathmindVisualEditorScreen.this.drawPopupButton(
                context, x, y, width, height, hovered, label, style, animation);
        }

        @Override
        public void resetBoundedPopupScroll(PopupAnimationHandler animation) {
            PathmindVisualEditorScreen.this.resetBoundedPopupScroll(animation);
        }

        @Override
        public void dismissParameterOverlay() {
            PathmindVisualEditorScreen.this.dismissParameterOverlay();
        }

        @Override
        public void closeCreatePresetPopupIfVisible() {
            if (presetPopupController.createVisible()) {
                closeCreatePresetPopup();
            }
        }

        @Override
        public void closeSettingsPopup() {
            PathmindVisualEditorScreen.this.closeSettingsPopup();
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }

        @Override
        public void clearWorkspace() {
            nodeGraph.clearWorkspace();
        }

        @Override
        public boolean containsBaritoneNodes() {
            return nodeGraph.containsBaritoneNodes();
        }

        @Override
        public boolean containsUiUtilsNodes() {
            return nodeGraph.containsUiUtilsNodes();
        }

        @Override
        public NodeGraphData snapshotRootPresetWorkspace() {
            return PathmindVisualEditorScreen.this.snapshotRootPresetWorkspace();
        }

        @Override
        public String activePresetName() {
            return presetWorkspaceController.activePresetName();
        }

        @Override
        public void applyImportedPreset(String presetName, NodeGraphData importedData) {
            PathmindVisualEditorScreen.this.applyImportedPreset(presetName, importedData);
        }

        @Override
        public void runOnClientThread(Runnable task) {
            PathmindVisualEditorScreen.this.runOnClientThread(task);
        }

        @Override
        public String currentMinecraftVersion() {
            return getCurrentMinecraftVersion();
        }

        @Override
        public String modVersion() {
            return getModVersion();
        }

        @Override
        public String loaderVersion() {
            return getLoaderVersion();
        }

        @Override
        public void copyToClipboard(String value) {
            if (minecraft != null && minecraft.keyboardHandler != null) {
                minecraft.keyboardHandler.setClipboard(value);
            }
        }
    }

    private final class WorkspaceViewportHost implements PathmindWorkspaceViewportController.Host {
        @Override
        public Font font() {
            return PathmindVisualEditorScreen.this.font;
        }

        @Override
        public int screenWidth() {
            return PathmindVisualEditorScreen.this.width;
        }

        @Override
        public int screenHeight() {
            return PathmindVisualEditorScreen.this.height;
        }

        @Override
        public int accentColor() {
            return getAccentColor();
        }

        @Override
        public Minecraft client() {
            return PathmindVisualEditorScreen.this.minecraft;
        }

        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public Sidebar sidebar() {
            return sidebar;
        }

        @Override
        public boolean showGrid() {
            return settingsPopupController.showGrid();
        }

        @Override
        public boolean isSidebarDragActive() {
            return workspaceDragController.isSidebarDragActive();
        }

        @Override
        public NodeType draggingNodeType() {
            return workspaceDragController.draggingNodeType();
        }

        @Override
        public Node draggingSidebarNode() {
            return workspaceDragController.draggingSidebarNode();
        }

        @Override
        public boolean isNodeDragBlocked(NodeType nodeType) {
            return shouldBlockBaritoneNode(nodeType) || shouldBlockUiUtilsNode(nodeType);
        }

        @Override
        public void closePresetDropdown() {
            presetDropdownController.close();
        }
    }

    private final class WorkspaceDragHost implements PathmindWorkspaceDragController.Host {
        @Override
        public int screenWidth() {
            return PathmindVisualEditorScreen.this.width;
        }

        @Override
        public int screenHeight() {
            return PathmindVisualEditorScreen.this.height;
        }

        @Override
        public Minecraft client() {
            return PathmindVisualEditorScreen.this.minecraft;
        }

        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public Sidebar sidebar() {
            return sidebar;
        }

        @Override
        public boolean isNodeDragBlocked(NodeType nodeType) {
            return shouldBlockBaritoneNode(nodeType) || shouldBlockUiUtilsNode(nodeType);
        }

        @Override
        public boolean saveDraggedRoutineToLibrary(double mouseX, double mouseY, boolean fromLibrary, Node draggedNode) {
            return workspaceLifecycleController.saveDraggedRoutineToLibrary(
                mouseX, mouseY, fromLibrary, draggedNode);
        }

        @Override
        public boolean importDraggedLibraryRoutineToList(double mouseX, double mouseY, boolean fromLibrary, Node draggedNode) {
            return workspaceLifecycleController.importDraggedLibraryRoutineToList(
                mouseX, mouseY, fromLibrary, draggedNode);
        }

        @Override
        public Node dropDraggedSidebarNodeIntoWorkspace(int mouseX, int mouseY, boolean fromLibrary,
                                                        Node draggedNode, NodeType draggedType) {
            return workspaceLifecycleController.dropDraggedSidebarNodeIntoWorkspace(
                mouseX, mouseY, fromLibrary, draggedNode, draggedType);
        }

        @Override
        public void openRoutineWorkspaceTab(String routineId) {
            PathmindVisualEditorScreen.this.openRoutineWorkspaceTab(routineId);
        }

        @Override
        public void openLibraryRoutineWorkspaceTab(String routineId) {
            PathmindVisualEditorScreen.this.openLibraryRoutineWorkspaceTab(routineId);
        }
    }

    private final class NodeInteractionHost implements PathmindNodeInteractionController.Host {
        @Override
        public NodeGraph nodeGraph() {
            return nodeGraph;
        }

        @Override
        public void openTemplateWorkspaceTab(Node node) {
            PathmindVisualEditorScreen.this.openTemplateWorkspaceTab(node);
        }

        @Override
        public void openRoutineWorkspaceTab(String routineId) {
            PathmindVisualEditorScreen.this.openRoutineWorkspaceTab(routineId);
        }

        @Override
        public void switchPreset(String presetName) {
            PathmindVisualEditorScreen.this.switchPreset(presetName);
        }

        @Override
        public void openBookTextEditor(Node node) {
            PathmindVisualEditorScreen.this.openBookTextEditor(node);
        }

        @Override
        public void openParameterOverlay(Node node) {
            PathmindVisualEditorScreen.this.openParameterOverlay(node);
        }

        @Override
        public boolean executeFromNodeOnDoubleClick(Node node) {
            return handleNodeDoubleClickExecution(node);
        }
    }

    public PathmindVisualEditorScreen() {
        super(Component.translatable("screen.pathmind.visual_editor.title"));
        this.baritoneAvailable = BaritoneDependencyChecker.isBaritoneApiPresent();
        this.uiUtilsAvailable = UiUtilsProxy.isAvailable();
        this.workspaceDialogController = new PathmindWorkspaceDialogController(
            new WorkspaceDialogHost(),
            baritoneAvailable,
            uiUtilsAvailable,
            clearPopupAnimation,
            importExportPopupAnimation,
            infoPopupAnimation,
            missingBaritonePopupAnimation,
            missingUiUtilsPopupAnimation
        );
        this.nodeGraph = new NodeGraph();
        this.nodeGraph.setWorkspaceSaveHandler(this::saveRootPresetWorkspace);
        this.sidebar = new Sidebar(baritoneAvailable, uiUtilsAvailable);
        refreshAvailablePresets();
        this.nodeGraph.setActivePreset(presetWorkspaceController.activePresetName());
        updateImportExportPathFromPreset();

        this.firstRunTutorialController.initialize(Boolean.TRUE.equals(settingsPopupController.settings().firstRunTutorialCompleted));
    }

    @Override
    protected void init() {
        super.init();
        workspaceViewportController.ensureSystemCursorHidden();
        if (uiUtilsOverlayPrevEnabled == null) {
            uiUtilsOverlayPrevEnabled = UiUtilsProxy.setOverlayEnabled(false);
        }

        refreshAvailablePresets();
        nodeGraph.setActivePreset(presetWorkspaceController.activePresetName());

        presetPopupController.initializeFields();
        if (inlinePresetRenameField == null) {
            inlinePresetRenameField = PathmindTextField.createInactive(this.font, 0, 0, 200, 20, Component.translatable("pathmind.field.newPresetName"), 64);
            this.addWidget(inlinePresetRenameField);
        }
        settingsPopupController.initializeFields();
        nodeSearchController.initialize();

        updateImportExportPathFromPreset();

        // Try to load saved node graph first
        if (nodeGraph.hasSavedGraph()) {
            if (nodeGraph.load()) {
                nodeGraph.restoreSessionViewportState();
                resetWorkspaceTabsFromCurrentGraph();
                refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
                return; // Don't initialize default nodes if we loaded a saved graph
            }
        }
        
        // Initialize node graph with proper centering based on screen dimensions
        nodeGraph.initializeWithScreenDimensions(this.width, this.height, sidebar.getWidth(), TITLE_BAR_HEIGHT);
        nodeGraph.restoreSessionViewportState();
        resetWorkspaceTabsFromCurrentGraph();
        refreshMissingBaritonePopup();
        refreshMissingUiUtilsPopup();
    }

    @Override
    public void tick() {
        super.tick();
        ExecutionManager.getInstance().setWorkspaceGraph(
            nodeGraph.getNodes(), nodeGraph.getConnections(), nodeGraph.getRoutineDefinitions());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        OverlayProtection.setPathmindRendering(true);
        try {
        workspaceDragController.recoverStaleLeftMouseDrag(mouseX, mouseY);
        resetOverlayCutout();
        context.fill(0, 0, this.width, this.height, UITheme.BACKGROUND_PRIMARY);

        boolean titleHovered = isTitleHovered(mouseX, mouseY);
        boolean titleActive = titleHovered || presetDropdownController.isOpen();
        titleUnderlineAnimation.animateTo(titleActive ? 1f : 0f, UITheme.HOVER_ANIM_MS);
        titleUnderlineAnimation.tick();
        GraphValidationResult validationResult = nodeGraph.getValidationResult(baritoneAvailable, uiUtilsAvailable);

        nodeGraph.setSidebarWidth(sidebar.getWidth());
        nodeGraph.setExecutionEnabled(validationExecutionController.shouldShowExecutionControls());
        nodeGraph.updateMouseHover(mouseX, mouseY);
        renderNodeGraph(context, mouseX, mouseY, delta, false);

        // Start a new GUI layer so UI chrome always sits above workspace nodes.
        DrawContextBridge.startNewRootLayer(context);

        renderWorkspaceButtons(context, mouseX, mouseY);
        boolean sidebarInteractionsEnabled = !isPopupObscuringWorkspace();
        boolean allowSidebarTooltips = settingsPopupController.showWorkspaceTooltips() && !nodeGraph.isAnyNodeBeingDragged();
        refreshRoutineSidebarContext();
        workspaceDragController.updateSidebarRoutineDragState();
        sidebar.render(
            context,
            this.font,
            mouseX,
            mouseY,
            TITLE_BAR_HEIGHT,
            this.height - TITLE_BAR_HEIGHT,
            sidebarInteractionsEnabled,
            allowSidebarTooltips
        );

        // Render title bar above the workspace so nodes never overlap it.
        UIStyleHelper.drawBeveledPanel(
            context,
            0,
            0,
            this.width,
            TITLE_BAR_HEIGHT,
            UITheme.BACKGROUND_SECTION,
            UITheme.BORDER_DEFAULT,
            UITheme.PANEL_INNER_BORDER
        );
        drawTitle(context, mouseX, mouseY, titleUnderlineAnimation.getValue());
        presetTabController.render(context, mouseX, mouseY);

        // Tick all popup animations early so the scrim uses current values
        clearPopupAnimation.tick();
        importExportPopupAnimation.tick();
        presetPopupController.tick();
        infoPopupAnimation.tick();
        missingBaritonePopupAnimation.tick();
        missingUiUtilsPopupAnimation.tick();
        settingsPopupController.animation().tick();
        validationExecutionController.tick();

        boolean controlsDisabled = isPopupObscuringWorkspace();
        int chromeMouseX = controlsDisabled ? Integer.MIN_VALUE : mouseX;
        int chromeMouseY = controlsDisabled ? Integer.MIN_VALUE : mouseY;
        workspaceViewportController.renderZoomControls(context, chromeMouseX, chromeMouseY, false);

        if (validationExecutionController.shouldShowExecutionControls()) {
            renderRoutineWorkspaceExitButton(context, chromeMouseX, chromeMouseY);
        }
        validationExecutionController.render(context, mouseX, mouseY, controlsDisabled, validationResult);
        renderBottomLeftWorkspaceButtons(context, mouseX, mouseY);
        renderSettingsButton(context, chromeMouseX, chromeMouseY, false);
        validationExecutionController.renderTooltip(context, mouseX, mouseY, controlsDisabled, validationResult);
        renderPresetDropdown(context, mouseX, mouseY, controlsDisabled);

        if (controlsDisabled) {
            DrawContextBridge.startNewRootLayer(context);
        }

        //? if MC_1_21_8 {
        /*Object popupMatrices = context.pose();
        boolean popupDepthPushed = isPopupObscuringWorkspace();
        if (popupDepthPushed) {
            MatrixStackBridge.push(popupMatrices);
            MatrixStackBridge.translateZ(popupMatrices, 450.0f);
        }
        try {
            if (!isScreenPopupVisible()) {
                setOverlayCutoutForNodeOverlay();
            }
            renderPopupScrimOverlay(context);
            if (isPopupObscuringWorkspace()) {
                DrawContextBridge.startNewRootLayer(context);
            }
        *///?}

        // Render parameter overlay if visible
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.render(context, this.font, mouseX, mouseY, delta);
        }

        // Render book text editor overlay if visible
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.render(context, this.font, mouseX, mouseY, delta);
        }

        if (clearPopupAnimation.isVisible()) {
            renderClearConfirmationPopup(context, mouseX, mouseY);
        }

        if (importExportPopupAnimation.isVisible()) {
            renderImportExportPopup(context, mouseX, mouseY, delta);
        }

        if (presetPopupController.createVisible()) {
            presetPopupController.renderCreatePresetPopup(context, mouseX, mouseY, delta);
        }

        if (presetPopupController.publishVisible()) {
            presetPopupController.renderPublishPresetPopup(context, mouseX, mouseY, delta);
        }

        if (presetPopupController.renameVisible()) {
            presetPopupController.renderRenamePresetPopup(context, mouseX, mouseY, delta);
        }

        if (presetPopupController.deleteVisible()) {
            presetPopupController.renderPresetDeletePopup(context, mouseX, mouseY);
        }

        if (infoPopupAnimation.isVisible()) {
            renderInfoPopup(context, mouseX, mouseY);
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            renderMissingBaritonePopup(context, mouseX, mouseY);
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            renderMissingUiUtilsPopup(context, mouseX, mouseY);
        }
        if (settingsPopupController.animation().isVisible()) {
            settingsPopupController.renderSettingsPopup(context, mouseX, mouseY);
        }

        //? if MC_1_21_8 {
        /*// Legacy rendering draws the scrim before popup contents inside the translated pose.*/
        //?} else {
        if (!isScreenPopupVisible()) {
            setOverlayCutoutForNodeOverlay();
        }

        renderPopupScrimOverlay(context);
        //?}

        // Render language dropdown options on top of scrim overlay
        if (settingsPopupController.animation().isVisible()) {
            RenderStateBridge.setShaderColor(1f, 1f, 1f, settingsPopupController.animation().getPopupAlpha());
            settingsPopupController.renderLanguageDropdownOptions(context, mouseX, mouseY);
            //? if MC_1_21_8 {
            /*RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);*/
            //?}
        }

        //? if MC_1_21_8 {
        /*} finally {
            if (popupDepthPushed) {
                DrawContextBridge.flush(context);
                MatrixStackBridge.pop(popupMatrices);
            }
        }*/
        //?}

        // Render context menu on top of everything
        if (presetContextMenuController.isOpen()) {
            nodeGraph.closeNodeContextMenu();
            nodeGraph.closeContextMenu();
        } else {
            nodeGraph.updateNodeContextMenuHover(mouseX, mouseY);
            nodeGraph.renderNodeContextMenu(context, this.font);
            nodeGraph.renderStartModeDropdown(context, this.font, mouseX, mouseY);
            nodeGraph.updateContextMenuHover(mouseX, mouseY);
            nodeGraph.renderContextMenu(context, this.font, mouseX, mouseY);
        }
        presetContextMenuController.render(context, mouseX, mouseY);
        nodeSearchController.render(context, mouseX, mouseY, delta);
        DrawContextBridge.startNewRootLayer(context);
        renderDraggedWorkspaceLayer(context, mouseX, mouseY, delta);
        workspaceViewportController.renderDragPreview(context, mouseX, mouseY);
        presetTabController.renderDraggedPresetDropdownTab(context, mouseX, mouseY);
        DrawContextBridge.startNewRootLayer(context);
        NodeErrorNotificationOverlay.getInstance().render(context, this.font, this.width, this.height);
        if (settingsPopupController.settings() != null && Boolean.TRUE.equals(settingsPopupController.settings().showProfilerOverlay)) {
            DrawContextBridge.startNewRootLayer(context);
            nodeGraph.renderProfilerOverlay(context, this.font);
        }
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            DrawContextBridge.startNewRootLayer(context);
            nodeGraph.renderScreenCoordinateCaptureOverlay(context, this.font, mouseX, mouseY);
        }
        firstRunTutorialController.maybeShow();
        firstRunTutorialController.render(context, this.font, mouseX, mouseY);
        DrawContextBridge.startNewRootLayer(context);
        workspaceViewportController.renderCursor(context, mouseX, mouseY);
        } finally {
            OverlayProtection.setPathmindRendering(false);
        }
    }

    void replayFirstRunTutorial() {
        firstRunTutorialController.replay();
    }

    private boolean isPopupObscuringWorkspace() {
        return modalOverlayController.isObscuringWorkspace();
    }
    
    private boolean handleNodeDoubleClickExecution(Node clickedNode) {
        if (clickedNode == null || this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            return false;
        }

        nodeGraph.commitPendingEdits();
        boolean started = ExecutionManager.getInstance().executeFromNode(
            clickedNode,
            nodeGraph.getNodes(),
            nodeGraph.getConnections(),
            presetWorkspaceController.activePresetName()
        );
        if (!started) {
            return false;
        }

        presetDropdownController.close();
        dismissParameterOverlay();
        workspaceDragController.clearSidebarDrag();
        this.minecraft.setScreen(null);
        return true;
    }

    private void resetOverlayCutout() {
        modalOverlayController.resetCutout();
    }

    void setOverlayCutout(int x, int y, int width, int height) {
        modalOverlayController.setCutout(x, y, width, height);
    }

    private boolean isScreenPopupVisible() {
        return modalOverlayController.isScreenPopupVisible();
    }

    private void renderPopupScrimOverlay(GuiGraphics context) {
        modalOverlayController.renderScrim(context);
    }

    private void setOverlayCutoutForNodeOverlay() {
        modalOverlayController.setCutoutForNodeOverlay();
    }

    private boolean shouldBlockBaritoneNode(NodeType nodeType) {
        return workspaceDialogController.shouldBlockBaritoneNode(nodeType);
    }

    private void refreshMissingBaritonePopup() {
        workspaceDialogController.refreshMissingBaritonePopup();
    }

    private boolean shouldBlockUiUtilsNode(NodeType nodeType) {
        return workspaceDialogController.shouldBlockUiUtilsNode(nodeType);
    }

    private void refreshMissingUiUtilsPopup() {
        workspaceDialogController.refreshMissingUiUtilsPopup();
    }
    
    private void renderNodeGraph(GuiGraphics context, int mouseX, int mouseY, float delta, boolean onlyDragged) {
        if (!onlyDragged) {
            // Node graph background
            routineWorkspaceAnimation.animateTo(getActiveRoutineWorkspace() == null ? 0f : 1f, UITheme.TRANSITION_ANIM_MS);
            routineWorkspaceAnimation.tick();
            int workspaceBackground = PathmindRoutineUi.workspaceBackground(
                UITheme.BACKGROUND_PRIMARY, NodeCategory.ROUTINES.getColor(), routineWorkspaceAnimation.getValue());
            context.fill(Sidebar.getCollapsedWidth(), TITLE_BAR_HEIGHT, this.width, this.height, workspaceBackground);
            
            // Render grid pattern for better visual organization
            workspaceViewportController.renderGrid(context);
        }

        nodeGraph.updateScreenCoordinateCapturePreview(mouseX, mouseY);
        
        // Render nodes
        nodeGraph.render(context, this.font, mouseX, mouseY, delta, onlyDragged);
    }

    private void renderDraggedWorkspaceLayer(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (nodeGraph.isAnyNodeBeingDragged()) {
            renderNodeGraph(context, mouseX, mouseY, delta, true);
        }
        nodeGraph.renderSelectionBox(context);
    }
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        *///?} else {
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            if (button == 0) {
                return nodeGraph.commitScreenCoordinateCapture((int) mouseX, (int) mouseY);
            }
            return true;
        }
        if (firstRunTutorialController.isVisible()) {
            return firstRunTutorialController.mouseClicked(mouseX, mouseY, button);
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return handleMissingBaritonePopupClick(mouseX, mouseY, button);
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return handleMissingUiUtilsPopupClick(mouseX, mouseY, button);
        }
        if (settingsPopupController.animation().isVisible()) {
            //? if MC_1_21_8 {
            /*if (settingsPopupController.mouseClicked(mouseX, mouseY, button)) {
                *///?} else {
            if (settingsPopupController.mouseClicked(click, inBounds)) {
                //?}
                return true;
            }
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            if (handleInfoPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleMouseClicked(mouseX, mouseY, button)) {
            *///?} else {
        if (presetPopupController.handleMouseClicked(click, inBounds)) {
            //?}
            return true;
        }

        if (presetTabController.isInlinePresetRenameActive()) {
            //? if MC_1_21_8 {
            /*if (inlinePresetRenameField != null && inlinePresetRenameField.mouseClicked(mouseX, mouseY, button)) {
                *///?} else {
            if (inlinePresetRenameField != null && inlinePresetRenameField.mouseClicked(click, inBounds)) {
                //?}
                return true;
            }
            presetTabController.stopInlinePresetRename(true);
        }

        if (presetPopupController.deleteVisible()) {
            presetPopupController.handlePresetDeletePopupClick(mouseX, mouseY, button);
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            if (handleClearPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            if (handleImportExportPopupClick(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleMouseClick(mouseX, mouseY, button);
            return true;
        }

        if (presetContextMenuController.isOpen()) {
            if (button == 0 && presetContextMenuController.handleClick((int) mouseX, (int) mouseY)) {
                return true;
            }
            presetContextMenuController.close();
            return true;
        }

        if (button == 1 && presetTabController.isPointInPresetTabBarContextZone((int) mouseX, (int) mouseY)) {
            presetContextMenuController.open((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && presetDropdownController.isOpen()) {
            if (presetDropdownController.handleMouseDown(mouseX, mouseY)) {
                return true;
            }
            presetDropdownController.close();
            return true;
        }

        if (button == 0) {
            if (isTitleClicked((int) mouseX, (int) mouseY)) {
                presetDropdownController.toggle();
                return true;
            }
            if (presetTabController.handleClick((int) mouseX, (int) mouseY)) {
                return true;
            }
        }

        if (!isPopupObscuringWorkspace() && button == 0) {
            if (validationExecutionController.handlePanelClick((int) mouseX, (int) mouseY)) {
                return true;
            }
        } else if (button == 0) {
            validationExecutionController.closePanel();
        }

        if (!isPopupObscuringWorkspace() && button == 0
            && mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT
            && handleStartNodeClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        if (!isPopupObscuringWorkspace()
            && validationExecutionController.handleExecutionClick((int) mouseX, (int) mouseY, button)) {
            return true;
        }

        if (!isPopupObscuringWorkspace() && button == 0) {
            if (workspaceViewportController.handleZoomButtonClick((int) mouseX, (int) mouseY)) {
                return true;
            }
            if (validationExecutionController.handleValidationButtonClick((int) mouseX, (int) mouseY, button)) {
                return true;
            }
            if (isSettingsButtonClicked((int) mouseX, (int) mouseY, button)) {
                openSettingsPopup();
                return true;
            }
            if (isMarketplaceButtonClicked((int) mouseX, (int) mouseY, button)) {
                saveRootPresetWorkspace();
                PresetManager.setActivePreset(presetWorkspaceController.activePresetName());
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new PathmindMarketplaceScreen(this));
                }
                return true;
            }
            if (isPublishButtonClicked((int) mouseX, (int) mouseY, button)) {
                saveRootPresetWorkspace();
                PresetManager.setActivePreset(presetWorkspaceController.activePresetName());
                openPublishPresetPopup();
                return true;
            }
        }

        if (nodeSearchController.isOpen()) {
            return nodeSearchController.handleClick((int) mouseX, (int) mouseY, button);
        }

        if (nodeGraph.isNodeContextMenuOpen()) {
            return nodeGraph.handleNodeContextMenuClick((int) mouseX, (int) mouseY);
        }

        if (button == 0 && nodeGraph.handleStartModeDropdownClick((int) mouseX, (int) mouseY)) {
            return true;
        }

        // Handle parameter overlay clicks first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        if (button == 0 && nodeGraph.handleBooleanLiteralDropdownClick(null, (int) mouseX, (int) mouseY)) {
            return true;
        }
        if (nodeGraph.handleParameterDropdownClick(mouseX, mouseY)) {
            return true;
        }
        if (nodeGraph.handleRandomRoundingDropdownClick(null, (int) mouseX, (int) mouseY)) {
            return true;
        }
        if (nodeGraph.handleModeDropdownClick(mouseX, mouseY)) {
            return true;
        }        if (button == 0 && nodeGraph.handleOperatorToggleClick(font, (int)mouseX, (int)mouseY)) {
            return true;
        }

        // Check if clicking home button
        if (isHomeButtonClicked((int)mouseX, (int)mouseY, button)) {
            nodeGraph.resetCamera();
            return true;
        }

        if (isClearButtonClicked((int)mouseX, (int)mouseY, button)) {
            openClearPopup();
            return true;
        }

        if (isImportExportButtonClicked((int)mouseX, (int)mouseY, button)) {
            openImportExportPopup();
            return true;
        }

        // Check if clicking in sidebar to add nodes
        if (mouseX < sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
            if (sidebar.mouseClicked(mouseX, mouseY, button)) {
                int routineInputAction = sidebar.consumeRoutineInputAction();
                String routineInputActionId = sidebar.consumeRoutineInputActionId();
                if (routineInputAction != 0 && routineInputActionId != null) {
                    editActiveRoutineInput(routineInputActionId, routineInputAction);
                    return true;
                }
                int routineAction = sidebar.consumeRoutineAction();
                String routineActionId = sidebar.consumeRoutineActionId();
                if (routineAction != 0 && routineActionId != null) {
                    handleRoutineAction(routineActionId, routineAction);
                    return true;
                }
                int libraryAction = sidebar.consumeLibraryAction();
                String libraryActionId = sidebar.consumeLibraryActionId();
                if (libraryAction != 0 && libraryActionId != null) {
                    handleRoutineLibraryAction(libraryActionId, libraryAction);
                    return true;
                }
                if (sidebar.consumeCreateRoutineRequested()) {
                    openCreateRoutinePopup();
                    return true;
                }
                if (sidebar.consumeAddRoutineInputRequested()) {
                    addInputToActiveRoutine();
                    return true;
                }
                // Check if we should start dragging a node from sidebar
                workspaceDragController.beginSidebarDragIfHovering((int) mouseX, (int) mouseY);
                return true;
            }
        }
        
        // Check if clicking on nodes in the graph area
        if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT) {
            // Check if context menu is open and handle click
            if (nodeGraph.isContextMenuOpen()) {
                ContextMenuSelection selection = nodeGraph.handleContextMenuClick((int)mouseX, (int)mouseY);
                if (selection != null && selection.shouldOpenSearch()) {
                    nodeGraph.closeContextMenu();
                    nodeSearchController.open((int) mouseX, (int) mouseY);
                } else if (selection != null && selection.shouldCreateStickyNote()) {
                    nodeGraph.addNodeFromContextMenu(NodeType.STICKY_NOTE);
                    nodeGraph.closeContextMenu();
                } else if (selection != null && selection.getNodeType() != null) {
                    // Create node at the stored right-click position
                    nodeGraph.addNodeFromContextMenu(selection.getNodeType());
                    nodeGraph.closeContextMenu();
                }
                // Menu handled the click (either selected node or closed)
                return true;
            }

            // Handle right-click for cutting or context menus, middle-click for panning
            if (workspaceDragController.beginWorkspacePointerGesture((int) mouseX, (int) mouseY, button)) {
                return true;
            }

            if (button == 0 && nodeGraph.handleStartButtonClick((int) mouseX, (int) mouseY)) {
                handleStartNodeLaunchAfterClick();
                return true;
            }
            
            return nodeInteractionController.handleClick(mouseX, mouseY, button);
        }
        
        //? if MC_1_21_8 {
        /*return super.mouseClicked(mouseX, mouseY, button);*/
        //?} else {
        return super.mouseClicked(click, inBounds);
        //?}
    }
    
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        *///?} else {
    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            return true;
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return true;
        }
        if (settingsPopupController.animation().isVisible()) {
            settingsPopupController.mouseDragged(mouseX, mouseY);
            return true;
        }
        if (presetPopupController.createVisible()) {
            return true;
        }

        if (presetPopupController.publishVisible()) {
            return true;
        }

        if (presetTabController.isInlinePresetRenameActive()) {
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        if (workspaceDragController.handleSidebarAndSelectionBoxDrag(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && presetTabController.hasPendingPresetTabInteraction()) {
            presetTabController.updatePendingPresetTabInteraction((int) mouseX, (int) mouseY);
            if (presetTabController.isDraggingPresetTab()) {
                return true;
            }
        }
        if (button == 0 && presetTabController.hasPendingPresetDropdownDrag()) {
            presetTabController.updatePendingPresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0 && presetTabController.isDraggingPresetDropdown()) {
            presetTabController.updatePresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }
        if (presetTabController.isDraggingPresetTab()) {
            presetTabController.updatePresetTabDrag((int) mouseX);
            return true;
        }

        if (workspaceDragController.handleWorkspaceDrag(mouseX, mouseY, button)) {
            return true;
        }

        //? if MC_1_21_8 {
        /*return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);*/
        //?} else {
        return super.mouseDragged(click, deltaX, deltaY);
        //?}
    }
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        *///?} else {
    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (firstRunTutorialController.isVisible()) {
            return true;
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            return true;
        }
        if (settingsPopupController.animation().isVisible()) {
            //? if MC_1_21_8 {
            /*settingsPopupController.mouseReleased(mouseX, mouseY, button);*/
            //?} else {
            settingsPopupController.mouseReleased(click);
            //?}
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleMouseReleased(mouseX, mouseY, button)) {
            *///?} else {
        if (presetPopupController.handleMouseReleased(click)) {
            //?}
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        if (workspaceDragController.handleSidebarAndSelectionBoxRelease(button)) {
            return true;
        }

        if (presetTabController.isDraggingPresetTab()) {
            presetTabController.endPresetTabDrag();
            return true;
        }

        if (button == 0 && presetTabController.isDraggingPresetDropdown()) {
            presetTabController.finishPresetDropdownDrag((int) mouseX, (int) mouseY);
            return true;
        }

        if (button == 0 && presetTabController.releasePendingPresetDropdownDrag()) {
            return true;
        }

        if (presetTabController.isInlinePresetRenameActive()) {
            if (inlinePresetRenameField != null) {
                //? if MC_1_21_8 {
                /*inlinePresetRenameField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                inlinePresetRenameField.mouseReleased(click);
                //?}
            }
            return true;
        }

        if (button == 0 && presetTabController.releasePendingPresetTabInteraction()) {
            return true;
        }

        if (workspaceDragController.handleWorkspaceRelease(mouseX, mouseY, button)) {
            return true;
        }
        //? if MC_1_21_8 {
        /*return super.mouseReleased(mouseX, mouseY, button);*/
        //?} else {
        return super.mouseReleased(click);
        //?}
    }

    //? if MC_1_21_8 {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        *///?} else {
    @Override
    public boolean keyPressed(KeyEvent input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();
        //?}
        if (firstRunTutorialController.isVisible()) {
            return firstRunTutorialController.keyPressed(keyCode);
        }
        if (nodeSearchController.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nodeSearchController.close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                nodeSearchController.selectCurrentOrClose();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                nodeSearchController.moveSelection(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                nodeSearchController.moveSelection(1);
                return true;
            }
            //? if MC_1_21_8 {
            /*if (nodeSearchController.field() != null && nodeSearchController.field().keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (nodeSearchController.field() != null && nodeSearchController.field().keyPressed(input)) {
                //?}
                return true;
            }
            return true;
        }
        if (missingBaritonePopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                missingBaritonePopupAnimation.hide();
            }
            return true;
        }
        if (missingUiUtilsPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                missingUiUtilsPopupAnimation.hide();
            }
            return true;
        }
        if (nodeGraph.isModeDropdownOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nodeGraph.closeModeDropdown();
            }
            return true;
        }
        if (nodeGraph.isContextMenuOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                nodeGraph.closeContextMenu();
            }
            return true;
        }
        if (settingsPopupController.animation().isVisible()) {
            //? if MC_1_21_8 {
            /*return settingsPopupController.keyPressed(keyCode, scanCode, modifiers);*/
            //?} else {
            return settingsPopupController.keyPressed(input);
            //?}
        }
        if (infoPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                closeInfoPopup();
                return true;
            }
            return true;
        }

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleKeyPressed(keyCode, scanCode, modifiers)) {
            *///?} else {
        if (presetPopupController.handleKeyPressed(input)) {
            //?}
            return true;
        }

        if (presetTabController.isInlinePresetRenameActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                presetTabController.stopInlinePresetRename(false);
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                presetTabController.stopInlinePresetRename(true);
                return true;
            }

            //? if MC_1_21_8 {
            /*if (inlinePresetRenameField != null && inlinePresetRenameField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (inlinePresetRenameField != null && inlinePresetRenameField.keyPressed(input)) {
                //?}
                return true;
            }

            return true;
        }

        if (presetPopupController.deleteVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePresetDeletePopup();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmPresetDeletion();
                return true;
            }
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                clearPopupAnimation.hide();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmClearWorkspace();
                return true;
            }
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeImportExportPopup();
                return true;
            }

            if (!workspaceDialogController.isImportExportBusy()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
                attemptImport();
                return true;
            }

            return true;
        }

        if (presetDropdownController.isOpen() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            presetDropdownController.close();
            return true;
        }

        // Handle parameter overlay key presses first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // Handle book text editor overlay key presses
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleKeyInput(keyCode, scanCode, modifiers);
            return true;
        }

        if (nodeGraph.isScreenCoordinateCaptureActive() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            nodeGraph.cancelScreenCoordinateCapture();
            return true;
        }

        if (nodeGraph.handleStopTargetKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleVariableKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleEventNameKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleStickyNoteKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleParameterKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleMessageKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleAmountKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if (nodeGraph.handleCoordinateKeyPressed(keyCode, modifiers)) {
            return true;
        }

        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
            && presetTabController.canStartInlinePresetRename(presetWorkspaceController.activePresetName())) {
            presetTabController.startInlinePresetRename(presetWorkspaceController.activePresetName());
            return true;
        }

        if (handleNodeGraphShortcuts(keyCode, modifiers)) {
            return true;
        }

        // Close screen with Escape key
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        
        // Delete selected node with Delete/Backspace key
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (nodeGraph.deleteSelectedNode()) {
                return true;
            }
        }
        
        // Don't handle the opening keybind - let it be ignored
        // This prevents the screen from closing when the same key is pressed
        
        //? if MC_1_21_8 {
        /*return super.keyPressed(keyCode, scanCode, modifiers);*/
        //?} else {
        return super.keyPressed(input);
        //?}
    }
    
    //? if MC_1_21_8 {
    /*@Override
    public boolean charTyped(char chr, int modifiers) {
        *///?} else {
    @Override
    public boolean charTyped(CharacterEvent input) {
        //? if PRE_1_21_11 {
        /*int modifiers = input.modifiers();*/
        //?} else {
        int modifiers = CharacterEventModifiers.get(input, this.minecraft);
        //?}
        char chr = (char) input.codepoint();
        //?}
        if (firstRunTutorialController.isVisible()) {
            return true;
        }
        if (nodeSearchController.isOpen()) {
            //? if MC_1_21_8 {
            /*if (nodeSearchController.field() != null && nodeSearchController.field().charTyped(chr, modifiers)) {
                *///?} else {
            if (nodeSearchController.field() != null && nodeSearchController.field().charTyped(input)) {
                //?}
                return true;
            }
            return true;
        }
        if (settingsPopupController.animation().isVisible()) {
            //? if MC_1_21_8 {
            /*return settingsPopupController.charTyped(chr, modifiers);*/
            //?} else {
            return settingsPopupController.charTyped(input);
            //?}
        }
        if (validationExecutionController.isPanelOpen()) {
            return true;
        }
        if (infoPopupAnimation.isVisible()) {
            return true;
        }

        //? if MC_1_21_8 {
        /*if (presetPopupController.handleCharTyped(chr, modifiers)) {
            *///?} else {
        if (presetPopupController.handleCharTyped(input)) {
            //?}
            return true;
        }

        if (presetTabController.isInlinePresetRenameActive()) {
            //? if MC_1_21_8 {
            /*if (inlinePresetRenameField != null && inlinePresetRenameField.charTyped(chr, modifiers)) {
                *///?} else {
            if (inlinePresetRenameField != null && inlinePresetRenameField.charTyped(input)) {
                //?}
                return true;
            }
            return true;
        }

        if (clearPopupAnimation.isVisible()) {
            return true;
        }

        if (importExportPopupAnimation.isVisible()) {
            return true;
        }

        // Handle parameter overlay character typing first
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            if (parameterOverlay.charTyped(chr, modifiers)) {
                return true;
            }
        }

        // Handle book text editor overlay character typing
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleCharInput(chr);
            return true;
        }

        if (nodeGraph.handleStopTargetCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleVariableCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleEventNameCharTyped(chr, modifiers)) {
            return true;
        }

        if (nodeGraph.handleStickyNoteCharTyped(chr, modifiers)) {
            return true;
        }

        if (nodeGraph.handleParameterCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleMessageCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleAmountCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        if (nodeGraph.handleCoordinateCharTyped(chr, modifiers, this.font)) {
            return true;
        }

        //? if MC_1_21_8 {
        /*return super.charTyped(chr, modifiers);*/
        //?} else {
        return super.charTyped(input);
        //?}
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (firstRunTutorialController.isVisible()) {
            return true;
        }
        if (nodeGraph.isScreenCoordinateCaptureActive()) {
            return true;
        }
        if (settingsPopupController.animation().isVisible()) {
            return settingsPopupController.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        if (infoPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, infoPopupAnimation, INFO_POPUP_WIDTH, INFO_POPUP_HEIGHT);
        }

        if (presetPopupController.createVisible()
            || presetPopupController.publishVisible()
            || presetPopupController.renameVisible()) {
            return presetPopupController.mouseScrolled(mouseX, mouseY, verticalAmount);
        }

        if (clearPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, clearPopupAnimation, 280, 150);
        }

        if (importExportPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, importExportPopupAnimation, 360, 210);
        }

        if (presetPopupController.deleteVisible()) {
            return presetPopupController.mouseScrolled(mouseX, mouseY, verticalAmount);
        }

        if (missingBaritonePopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, missingBaritonePopupAnimation, MISSING_BARITONE_POPUP_WIDTH, MISSING_BARITONE_POPUP_HEIGHT);
        }

        if (missingUiUtilsPopupAnimation.isVisible()) {
            return handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, missingUiUtilsPopupAnimation, MISSING_UI_UTILS_POPUP_WIDTH, MISSING_UI_UTILS_POPUP_HEIGHT);
        }

        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.handleMouseScroll(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (presetDropdownController.isOpen()) {
            presetDropdownController.handleScroll(mouseX, mouseY, verticalAmount);
            return true;
        }

        if (nodeGraph.handleSchematicDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (nodeGraph.handleRunPresetDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }

        if (nodeGraph.handleParameterDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (nodeGraph.handleRandomRoundingDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        if (nodeGraph.handleModeDropdownScroll(mouseX, mouseY, verticalAmount)) {
            return true;
        }

        if (nodeGraph.handleContextMenuScroll((int) mouseX, (int) mouseY, verticalAmount)) {
            return true;
        }

        if (mouseX >= sidebar.getWidth() && mouseY > TITLE_BAR_HEIGHT && verticalAmount != 0.0) {
            workspaceViewportController.zoomByScroll(verticalAmount);
            return true;
        }

        // Handle sidebar scrolling
        if (mouseX >= 0 && mouseX <= sidebar.getWidth()) {
            if (sidebar.mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private void resetWorkspaceTabsFromCurrentGraph() {
        workspaceLifecycleController.resetFromCurrentGraph();
    }

    private boolean renamePresetInternal(String currentName, String desiredName) {
        return presetWorkspaceController.renamePreset(currentName, desiredName);
    }

    void stopInlinePresetRename(boolean commit) {
        presetTabController.stopInlinePresetRename(commit);
    }

    private void openTemplateWorkspaceTab(Node templateNode) {
        workspaceLifecycleController.openTemplateWorkspaceTab(templateNode);
    }

    private void refreshRoutineSidebarContext() {
        workspaceLifecycleController.refreshRoutineSidebarContext();
    }

    private NodeGraphData.RoutineDefinitionData getActiveRoutineWorkspace() {
        return workspaceLifecycleController.getActiveRoutineWorkspace();
    }

    private List<NodeGraphData.RoutineDefinitionData> getActiveRoutineRegistry() {
        return workspaceLifecycleController.getActiveRoutineRegistry();
    }

    private void renderRoutineWorkspaceExitButton(GuiGraphics context, int mouseX, int mouseY) {
        workspaceLifecycleController.renderRoutineWorkspaceExitButton(context, mouseX, mouseY);
    }

    private void createRoutineFromSidebar(String name) {
        workspaceLifecycleController.createRoutineFromSidebar(name);
    }

    private void addInputToActiveRoutine() {
        workspaceLifecycleController.addInputToActiveRoutine();
    }

    private void editActiveRoutineInput(String inputId, int action) {
        workspaceLifecycleController.editActiveRoutineInput(inputId, action);
    }

    private void handleRoutineAction(String routineId, int action) {
        workspaceLifecycleController.handleRoutineAction(routineId, action);
    }

    private void handleRoutineLibraryAction(String libraryRoutineId, int action) {
        workspaceLifecycleController.handleRoutineLibraryAction(libraryRoutineId, action);
    }

    private void switchToRootAfterRoutineChange(NodeGraphData root) {
        workspaceLifecycleController.switchToRootAfterRoutineChange(root);
    }

    private void openRoutineWorkspaceTab(String routineId) {
        workspaceLifecycleController.openRoutineWorkspaceTab(routineId);
    }

    private void openLibraryRoutineWorkspaceTab(String routineId) {
        workspaceLifecycleController.openLibraryRoutineWorkspaceTab(routineId);
    }

    private void switchToWorkspaceTab(int targetIndex) {
        workspaceLifecycleController.switchToWorkspaceTab(targetIndex);
    }

    boolean saveRootPresetWorkspace() {
        nodeGraph.commitPendingEdits();
        return workspaceLifecycleController.saveRootPresetWorkspace();
    }

    private NodeGraphData snapshotRootPresetWorkspace() {
        nodeGraph.commitPendingEdits();
        return workspaceLifecycleController.snapshotRootPresetWorkspace();
    }

    private void persistActiveWorkspaceToTabs() {
        workspaceLifecycleController.persistActiveWorkspaceToTabs();
    }

    private void syncAllTemplateTabsIntoParents() {
        workspaceLifecycleController.syncAllTemplateTabsIntoParents();
    }

    private void restoreRootWorkspaceIfNeeded() {
        workspaceLifecycleController.restoreRootWorkspaceIfNeeded();
    }

    private void autoSaveWorkspace() {
        workspaceLifecycleController.autoSaveWorkspace();
    }

    @Override
    public void onClose() {
        nodeGraph.persistSessionViewportState();
        autoSaveWorkspace();
        workspaceViewportController.restoreSystemCursor();
        super.onClose();
    }

    @Override
    public void removed() {
        nodeGraph.persistSessionViewportState();
        autoSaveWorkspace();
        if (uiUtilsOverlayPrevEnabled != null) {
            UiUtilsProxy.setOverlayEnabled(uiUtilsOverlayPrevEnabled);
            uiUtilsOverlayPrevEnabled = null;
        }
        workspaceViewportController.restoreSystemCursor();
        super.removed();
    }

    private void renderClearConfirmationPopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderClearConfirmationPopup(context, mouseX, mouseY);
    }

    private void renderImportExportPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        workspaceDialogController.renderImportExportPopup(context, mouseX, mouseY, delta);
    }

    private void renderInfoPopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderInfoPopup(context, mouseX, mouseY);
    }

    private void renderMissingBaritonePopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderMissingBaritonePopup(context, mouseX, mouseY);
    }

    private void renderMissingUiUtilsPopup(GuiGraphics context, int mouseX, int mouseY) {
        workspaceDialogController.renderMissingUiUtilsPopup(context, mouseX, mouseY);
    }

    private void drawTitle(GuiGraphics context, int mouseX, int mouseY, float underlineProgress) {
        drawTitleMenuButton(context, mouseX, mouseY, underlineProgress);
    }

    private void drawTitleMenuButton(GuiGraphics context, int mouseX, int mouseY, float hoverProgress) {
        int x = getTitleTextX();
        int y = getTitleTextY();
        boolean hovered = isTitleHovered(mouseX, mouseY);
        int iconColor = (hovered || presetDropdownController.isOpen()) ? getAccentColor() : UITheme.ICON_MUTED_BRIGHT;
        int centerX = x + PRESET_MENU_BUTTON_SIZE / 2;
        int centerY = y + PRESET_MENU_BUTTON_SIZE / 2;
        int lineHalfWidth = 4;
        int alphaColor = AnimationHelper.multiplyAlpha(iconColor, Mth.clamp(0.75f + hoverProgress * 0.25f, 0f, 1f));
        for (int i = -1; i <= 1; i++) {
            int lineY = centerY + i * 3;
            context.hLine(centerX - lineHalfWidth, centerX + lineHalfWidth, lineY, alphaColor);
        }
    }

    void drawPopupTextWithEllipsis(GuiGraphics context, String text, int x, int y, int maxWidth, int color) {
        PathmindPopupRenderer.drawTextWithEllipsis(context, this.font, text, x, y, maxWidth, color);
    }

    private void drawPopupCenteredTextWithEllipsis(GuiGraphics context, String text, int centerX, int y, int maxWidth, int color) {
        PathmindPopupRenderer.drawCenteredTextWithEllipsis(context, this.font, text, centerX, y, maxWidth, color);
    }

    boolean enablePopupScissor(GuiGraphics context, int popupX, int popupY, int scaledWidth, int scaledHeight) {
        return PathmindPopupRenderer.enableScissor(context, popupX, popupY, scaledWidth, scaledHeight);
    }

    void disablePopupScissor(GuiGraphics context, boolean enabled) {
        PathmindPopupRenderer.disableScissor(context, enabled);
    }

    private int getBoundedPopupWidth(int preferredWidth) {
        return Math.min(preferredWidth, Math.max(1, this.width - 40));
    }

    private int getBoundedPopupHeight(int preferredHeight) {
        return Math.min(preferredHeight, Math.max(1, this.height - 40));
    }

    int[] getBoundedScaledPopupBounds(PopupAnimationHandler animation, int preferredWidth, int preferredHeight) {
        return animation.getScaledPopupBounds(
            this.width,
            this.height,
            getBoundedPopupWidth(preferredWidth),
            getBoundedPopupHeight(preferredHeight)
        );
    }

    private int getBoundedPopupMaxScroll(int preferredHeight) {
        return Math.max(0, preferredHeight - getBoundedPopupHeight(preferredHeight));
    }

    private int getBoundedPopupScrollOffset(PopupAnimationHandler animation, int preferredHeight) {
        int maxScroll = getBoundedPopupMaxScroll(preferredHeight);
        int offset = ScrollbarHelper.clampScroll(popupScrollOffsets.getOrDefault(animation, 0), maxScroll);
        if (offset == 0) {
            popupScrollOffsets.remove(animation);
        } else {
            popupScrollOffsets.put(animation, offset);
        }
        return offset;
    }

    int getBoundedPopupContentY(int popupY, PopupAnimationHandler animation, int preferredHeight) {
        return popupY - getBoundedPopupScrollOffset(animation, preferredHeight);
    }

    void resetBoundedPopupScroll(PopupAnimationHandler animation) {
        popupScrollOffsets.remove(animation);
    }

    private boolean handleBoundedPopupScroll(double mouseX, double mouseY, double verticalAmount,
                                             PopupAnimationHandler animation, int preferredWidth, int preferredHeight) {
        int[] bounds = getBoundedScaledPopupBounds(animation, preferredWidth, preferredHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        if (verticalAmount != 0.0 && isPointInRect((int) mouseX, (int) mouseY, popupX, popupY, popupWidth, popupHeight)) {
            int maxScroll = getBoundedPopupMaxScroll(preferredHeight);
            int nextOffset = ScrollbarHelper.applyWheel(
                getBoundedPopupScrollOffset(animation, preferredHeight),
                verticalAmount,
                16,
                maxScroll
            );
            if (nextOffset == 0) {
                popupScrollOffsets.remove(animation);
            } else {
                popupScrollOffsets.put(animation, nextOffset);
            }
        }
        return true;
    }

    private boolean handleClearPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleClearPopupClick(mouseX, mouseY, button);
    }

    private boolean handleImportExportPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleImportExportPopupClick(mouseX, mouseY, button);
    }

    private boolean handleMissingBaritonePopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleMissingBaritonePopupClick(mouseX, mouseY, button);
    }

    private boolean handleMissingUiUtilsPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleMissingUiUtilsPopupClick(mouseX, mouseY, button);
    }

    private boolean handleInfoPopupClick(double mouseX, double mouseY, int button) {
        return workspaceDialogController.handleInfoPopupClick(mouseX, mouseY, button);
    }

    void drawPopupButton(GuiGraphics context, int x, int y, int width, int height, boolean hovered, Component label, boolean primary) {
        PathmindPopupRenderer.ButtonStyle style = primary ? PathmindPopupRenderer.ButtonStyle.PRIMARY : PathmindPopupRenderer.ButtonStyle.DEFAULT;
        drawPopupButton(context, x, y, width, height, hovered, label, style);
    }

    void drawPopupButton(GuiGraphics context, int x, int y, int width, int height, boolean hovered, Component label, PathmindPopupRenderer.ButtonStyle style) {
        drawPopupButton(context, x, y, width, height, hovered, label, style, null);
    }

    void drawPopupButton(GuiGraphics context, int x, int y, int width, int height, boolean hovered,
                                 Component label, PathmindPopupRenderer.ButtonStyle style, PopupAnimationHandler animation) {
        float hoverProgress = getHoverProgress(PathmindPopupRenderer.buttonHoverKey(style, label, x, y, width, height), hovered);
        PathmindPopupRenderer.drawButton(
            context,
            this.font,
            x,
            y,
            width,
            height,
            label,
            style,
            hoverProgress,
            getAccentColor(),
            animation
        );
    }

    void drawPopupContainer(GuiGraphics context, int x, int y, int width, int height, PopupAnimationHandler animation) {
        PathmindPopupRenderer.drawContainer(context, x, y, width, height, animation);
    }

    int getPopupAnimatedColor(PopupAnimationHandler animation, int baseColor) {
        return PathmindPopupRenderer.animatedColor(animation, baseColor);
    }

    void closeInfoPopup() {
        workspaceDialogController.closeInfoPopup();
    }

    private void openClearPopup() {
        workspaceDialogController.openClearPopup();
    }

    private void confirmClearWorkspace() {
        workspaceDialogController.confirmClearWorkspace();
    }

    private void openImportExportPopup() {
        workspaceDialogController.openImportExportPopup();
    }

    private void closeImportExportPopup() {
        workspaceDialogController.closeImportExportPopup();
    }

    private void attemptImport() {
        workspaceDialogController.attemptImport();
    }

    private void attemptExport() {
        workspaceDialogController.attemptExport();
    }

    private void applyImportedPreset(String presetName, NodeGraphData importedData) {
        presetWorkspaceController.applyImportedPreset(presetName, importedData);
    }

    private void runOnClientThread(Runnable task) {
        Minecraft minecraftClient = this.minecraft;
        if (minecraftClient == null || task == null) {
            return;
        }
        minecraftClient.execute(() -> {
            if (this.minecraft == null) {
                return;
            }
            task.run();
        });
    }

    private void clearImportExportStatus() {
        workspaceDialogController.clearImportExportStatus();
    }

    private boolean handleNodeGraphShortcuts(int keyCode, int modifiers) {
        if (!isShortcutModifierDown(modifiers)) {
            return false;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_C:
                nodeGraph.copySelectedNodeToClipboard();
                return true;
            case GLFW.GLFW_KEY_X:
                return nodeGraph.cutSelectedNodeToClipboard();
            case GLFW.GLFW_KEY_V:
                nodeGraph.pasteClipboardNode();
                return true;
            case GLFW.GLFW_KEY_D:
                nodeGraph.duplicateSelectedNode();
                return true;
            case GLFW.GLFW_KEY_Z:
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    nodeGraph.redo();
                } else {
                    nodeGraph.undo();
                }
                return true;
            default:
                return false;
        }
    }

    private boolean isShortcutModifierDown(int modifiers) {
        return (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
    }

    private void dismissParameterOverlay() {
        if (parameterOverlay != null && parameterOverlay.isVisible()) {
            parameterOverlay.close();
        }
    }

    private void openParameterOverlay(Node node) {
        if (node == null) {
            return;
        }
        if (node.getType() != NodeType.PARAM_INVENTORY_SLOT
            && node.getType() != NodeType.PARAM_KEY
            && node.getType() != NodeType.PARAM_VILLAGER_TRADE) {
            return;
        }
        nodeGraph.stopCoordinateEditing(true);
        nodeGraph.stopAmountEditing(true);
        nodeGraph.stopStopTargetEditing(true);
        nodeGraph.stopVariableEditing(true);
        nodeGraph.stopMessageEditing(true);
        final NodeParameterOverlay[] overlayRef = new NodeParameterOverlay[1];
        overlayRef[0] = new NodeParameterOverlay(
            node,
            this.width,
            this.height,
            TITLE_BAR_HEIGHT,
            () -> {
                if (parameterOverlay == overlayRef[0]) {
                    parameterOverlay = null;
                }
            }, // Clear reference on close
            nodeGraph::notifyNodeParametersChanged
        );
        parameterOverlay = overlayRef[0];
        parameterOverlay.init();
        parameterOverlay.show();
    }

    private void openBookTextEditor(Node node) {
        dismissParameterOverlay();
        if (bookTextEditorOverlay != null && bookTextEditorOverlay.isVisible()) {
            bookTextEditorOverlay.hide();
        }
        final BookTextEditorOverlay[] overlayRef = new BookTextEditorOverlay[1];
        overlayRef[0] = new BookTextEditorOverlay(
            node,
            this.width,
            this.height,
            () -> {
                if (bookTextEditorOverlay == overlayRef[0]) {
                    bookTextEditorOverlay = null;
                }
            },
            nodeGraph::notifyNodeParametersChanged
        );
        bookTextEditorOverlay = overlayRef[0];
        bookTextEditorOverlay.init();
        bookTextEditorOverlay.show();
    }

    private void renderPresetDropdown(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        presetDropdownController.render(context, mouseX, mouseY, disabled);
    }

    private int getPresetOverflowTabRight() {
        return getTitleTextX() + PRESET_MENU_BUTTON_SIZE;
    }

    private int getPlayButtonX() {
        return validationExecutionController.playButtonX();
    }

    private int getPlayButtonY() {
        return validationExecutionController.playButtonY();
    }

    private int getValidationButtonX() {
        return validationExecutionController.validationButtonX();
    }

    private int getValidationButtonY() {
        return validationExecutionController.validationButtonY();
    }

    private int getStopButtonX() {
        return validationExecutionController.stopButtonX();
    }

    private int getStopButtonY() {
        return validationExecutionController.stopButtonY();
    }

    private int getRoutineExitButtonX() {
        return validationExecutionController.routineExitButtonX();
    }

    private int getRoutineExitButtonY() {
        return validationExecutionController.routineExitButtonY();
    }

    private boolean isPresetDeleteDisabled(String presetName) {
        return presetWorkspaceController.isPresetDeleteDisabled(presetName);
    }

    private boolean isPresetRenameDisabled(String presetName) {
        return presetWorkspaceController.isPresetRenameDisabled(presetName);
    }

    private void openCreatePresetPopup() {
        presetPopupController.openCreatePresetPopup();
    }

    private void openCreateRoutinePopup() {
        presetPopupController.openCreateRoutinePopup();
    }

    private void openRenameRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
        presetPopupController.openRenameRoutinePopup(routine);
    }

    private void openRenameLibraryRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
        presetPopupController.openRenameLibraryRoutinePopup(routine);
    }

    void closeCreatePresetPopup() {
        presetPopupController.closeCreatePresetPopup();
    }

    private void openPublishPresetPopup() {
        presetPopupController.openPublishPresetPopup();
    }

    void reopenPublishPresetPopup(String presetName) {
        presetPopupController.reopenPublishPresetPopup(presetName);
    }

    void closePublishPresetPopup() {
        presetPopupController.closePublishPresetPopup();
    }

    private void openRenamePresetPopup(String presetName) {
        presetPopupController.openRenamePresetPopup(presetName);
    }

    void closeRenamePresetPopup() {
        presetPopupController.closeRenamePresetPopup();
    }

    void attemptCreatePreset() {
        presetPopupController.attemptCreatePreset();
    }

    void attemptPublishPreset() {
        presetPopupController.attemptPublishPreset();
    }

    void startPublishPresetSignIn() {
        presetPopupController.startPublishPresetSignIn();
    }

    void attemptRenamePreset() {
        presetPopupController.attemptRenamePreset();
    }

    private void openPresetDeletePopup(String presetName) {
        presetPopupController.openPresetDeletePopup(presetName);
    }

    void closePresetDeletePopup() {
        presetPopupController.closePresetDeletePopup();
    }

    void confirmPresetDeletion() {
        presetPopupController.confirmPresetDeletion();
    }

    void setSkipPresetDeleteConfirm(boolean skip) {
        presetPopupController.setSkipPresetDeleteConfirm(skip);
    }

    boolean isSkipPresetDeleteConfirm() {
        return presetPopupController.isSkipPresetDeleteConfirm();
    }

    private void attemptDeletePreset(String presetName) {
        presetWorkspaceController.queuePresetDeletion(presetName);
    }

    private void attemptDeletePresetImmediate(String presetName) {
        presetWorkspaceController.deletePresetImmediately(presetName);
    }

    void refreshAvailablePresets() {
        presetWorkspaceController.refreshAvailablePresets();
    }

    private void updateImportExportPathFromPreset() {
        workspaceDialogController.updateImportExportPathFromPreset();
    }

    private void switchPreset(String presetName) {
        presetWorkspaceController.switchPreset(presetName);
    }

    private void renderWorkspaceButtons(GuiGraphics context, int mouseX, int mouseY) {
        if (isPopupObscuringWorkspace()) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        int buttonY = getWorkspaceButtonY();
        boolean marketplaceHovered = renderMarketplaceButton(context, mouseX, mouseY, buttonY);
        boolean publishHovered = renderPublishButton(context, mouseX, mouseY, buttonY);

        if (settingsPopupController.showWorkspaceTooltips() && !isPopupObscuringWorkspace()) {
            if (publishHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.marketplace.publishPreset").getString(), mouseX, mouseY, this.width, this.height);
            } else if (marketplaceHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.marketplace.title").getString(), mouseX, mouseY, this.width, this.height);
            }
        }
    }

    private void renderBottomLeftWorkspaceButtons(GuiGraphics context, int mouseX, int mouseY) {
        if (isPopupObscuringWorkspace()) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        int buttonY = getSettingsButtonY();
        boolean importHovered = renderImportExportButton(context, mouseX, mouseY, buttonY);
        boolean clearHovered = renderClearButton(context, mouseX, mouseY, buttonY);
        boolean homeHovered = renderHomeButton(context, mouseX, mouseY, buttonY);

        if (settingsPopupController.showWorkspaceTooltips() && !isPopupObscuringWorkspace()) {
            if (homeHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.tooltip.resetView").getString(), mouseX, mouseY, this.width, this.height);
            } else if (clearHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.tooltip.clearWorkspace").getString(), mouseX, mouseY, this.width, this.height);
            } else if (importHovered) {
                TooltipRenderer.render(context, this.font, Component.translatable("pathmind.popup.importExport.title").getString(), mouseX, mouseY, this.width, this.height);
            }
        }
    }

    private boolean renderMarketplaceButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getMarketplaceButtonX();
        boolean hovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, MARKETPLACE_BUTTON_WIDTH, BOTTOM_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("workspace-marketplace", hovered);
        return PathmindWorkspaceChrome.renderMarketplaceButton(
            context,
            this.font,
            buttonX,
            buttonY,
            MARKETPLACE_BUTTON_WIDTH,
            BOTTOM_BUTTON_SIZE,
            mouseX,
            mouseY,
            hoverProgress,
            getAccentColor(),
            Component.translatable("pathmind.marketplace.title").getString()
        );
    }

    private boolean renderPublishButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        int buttonX = getPublishButtonX();
        boolean hovered = isPointInRect(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
        float hoverProgress = getHoverProgress("workspace-publish", hovered);
        return PathmindWorkspaceChrome.renderPublishButton(
            context,
            buttonX,
            buttonY,
            BOTTOM_BUTTON_SIZE,
            mouseX,
            mouseY,
            hoverProgress,
            getAccentColor(),
            isCurrentPresetPublishedAndSynced()
        );
    }

    private boolean isCurrentPresetPublishedAndSynced() {
        if (presetWorkspaceController.activePresetName() == null || presetWorkspaceController.activePresetName().isBlank()) {
            return false;
        }
        return PresetManager.getMarketplaceLinkedPresetId(presetWorkspaceController.activePresetName()).isPresent()
            && !PresetManager.hasMarketplaceLinkedPresetChanges(presetWorkspaceController.activePresetName());
    }

    private boolean renderHomeButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        return renderWorkspaceIconButton(context, getHomeButtonX(), buttonY, mouseX, mouseY,
            false, false, "workspace-home", PathmindWorkspaceChrome::drawHomeIcon);
    }

    private boolean renderClearButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        return renderWorkspaceIconButton(context, getClearButtonX(), buttonY, mouseX, mouseY,
            clearPopupAnimation.isVisible(), false, "workspace-clear", PathmindWorkspaceChrome::drawClearIcon);
    }

    private boolean renderImportExportButton(GuiGraphics context, int mouseX, int mouseY, int buttonY) {
        return renderWorkspaceIconButton(context, getImportExportButtonX(), buttonY, mouseX, mouseY,
            importExportPopupAnimation.isVisible(), false, "workspace-import-export", PathmindWorkspaceChrome::drawImportExportIcon);
    }

    float getHoverProgress(Object key, boolean hovered) {
        return HoverAnimator.getProgress(key, hovered);
    }


    private void renderSettingsButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        boolean hovered = renderWorkspaceIconButton(context, getSettingsButtonX(), getSettingsButtonY(), mouseX, mouseY,
            settingsPopupController.animation().isVisible(), disabled, "settings-button", PathmindWorkspaceChrome::drawSettingsIcon);

        if (hovered && settingsPopupController.showWorkspaceTooltips() && !isPopupObscuringWorkspace()) {
            TooltipRenderer.render(context, this.font, Component.translatable("pathmind.settings.title").getString(), mouseX, mouseY, this.width, this.height);
        }
    }

    private boolean renderWorkspaceIconButton(GuiGraphics context, int buttonX, int buttonY, int mouseX, int mouseY,
                                              boolean active, boolean disabled, Object hoverKey,
                                              PathmindWorkspaceChrome.IconPainter iconPainter) {
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
        float hoverProgress = getHoverProgress(hoverKey, hovered || active);
        return PathmindWorkspaceChrome.renderIconButton(
            context,
            buttonX,
            buttonY,
            BOTTOM_BUTTON_SIZE,
            mouseX,
            mouseY,
            active,
            disabled,
            hoverProgress,
            getAccentColor(),
            iconPainter
        );
    }

    private int getWorkspaceButtonY() {
        return PathmindWorkspaceChrome.topButtonY(TITLE_BAR_HEIGHT, BOTTOM_BUTTON_MARGIN);
    }

    private int getSidebarVisibleWidth() {
        return sidebar != null ? sidebar.getWidth() : Sidebar.getCollapsedWidth();
    }

    private int getMarketplaceButtonX() {
        return PathmindWorkspaceChrome.marketplaceButtonX(getSidebarVisibleWidth(), BOTTOM_BUTTON_MARGIN, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }

    private int getHomeButtonX() {
        return PathmindWorkspaceChrome.homeButtonX(getSettingsButtonX(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }

    private int getPublishButtonX() {
        return PathmindWorkspaceChrome.publishButtonX(getSidebarVisibleWidth(), BOTTOM_BUTTON_MARGIN);
    }

    private int getClearButtonX() {
        return PathmindWorkspaceChrome.clearButtonX(getSettingsButtonX(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }

    private int getImportExportButtonX() {
        return PathmindWorkspaceChrome.importExportButtonX(getSettingsButtonX(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SPACING);
    }

    private int getSettingsButtonX() {
        return PathmindWorkspaceChrome.settingsButtonX(getSidebarVisibleWidth(), BOTTOM_BUTTON_MARGIN);
    }

    private int getSettingsButtonY() {
        return PathmindWorkspaceChrome.bottomButtonY(this.height, BOTTOM_BUTTON_MARGIN, BOTTOM_BUTTON_SIZE);
    }

    private boolean isHomeButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getHomeButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isClearButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getClearButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isImportExportButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getImportExportButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isMarketplaceButtonClicked(int mouseX, int mouseY, int button) {
        return PathmindWorkspaceChrome.primaryClickInBounds(mouseX, mouseY, button, getMarketplaceButtonX(), getWorkspaceButtonY(), MARKETPLACE_BUTTON_WIDTH, BOTTOM_BUTTON_SIZE);
    }

    private boolean isPublishButtonClicked(int mouseX, int mouseY, int button) {
        return PathmindWorkspaceChrome.primaryClickInBounds(mouseX, mouseY, button, getPublishButtonX(), getWorkspaceButtonY(), BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    private boolean isSettingsButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int buttonX = getSettingsButtonX();
        int buttonY = getSettingsButtonY();
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, BOTTOM_BUTTON_SIZE, BOTTOM_BUTTON_SIZE);
    }

    int screenWidth() {
        return this.width;
    }

    int screenHeight() {
        return this.height;
    }

    Minecraft client() {
        return this.minecraft;
    }

    String activePresetName() {
        return presetWorkspaceController.activePresetName();
    }

    void closePresetDropdown() {
        presetDropdownController.close();
    }

    Font textRenderer() {
        return this.font;
    }

    int getAccentColor() {
        return settingsPopupController.accentColor();
    }

    private void openSettingsPopup() {
        dismissParameterOverlay();
        closeInfoPopup();
        clearPopupAnimation.hide();
        importExportPopupAnimation.hide();
        presetDropdownController.close();
        settingsPopupController.open();
    }

    void closeSettingsPopup() {
        settingsPopupController.close();
    }

    private void startExecutingAllGraphs() {
        validationExecutionController.closePanel();
        dismissParameterOverlay();
        workspaceDragController.clearSidebarDrag();
        nodeGraph.commitPendingEdits();
        NodeGraphData.RoutineDefinitionData activeRoutine = getActiveRoutineWorkspace();
        saveRootPresetWorkspace();
        if (activeRoutine != null) {
            ExecutionManager.getInstance().executeRoutine(
                activeRoutine, getActiveRoutineRegistry(), presetWorkspaceController.activePresetName());
        } else {
            ExecutionManager.getInstance().executeGraph(nodeGraph.getNodes(), nodeGraph.getConnections());
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.setScreen(null);
        }
    }

    private boolean handleStartNodeClick(int mouseX, int mouseY) {
        if (!nodeGraph.handleStartButtonClick(mouseX, mouseY)) {
            return false;
        }
        handleStartNodeLaunchAfterClick();
        return true;
    }

    private void handleStartNodeLaunchAfterClick() {
        presetDropdownController.close();
        if (nodeGraph.didLastStartButtonTriggerExecution()) {
            dismissParameterOverlay();
            workspaceDragController.clearSidebarDrag();
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.setScreen(null);
            }
        }
    }

    /** Rebuilds the editor so every string picks up a newly selected language. */
    void reopenForLanguageChange() {
        this.minecraft.setScreen(null);
        this.minecraft.setScreen(new PathmindVisualEditorScreen());
    }

    private void drawCloseXIcon(GuiGraphics context, int x, int y, int size, int color) {
        PathmindIconRenderer.drawCloseX(context, x, y, size, color);
    }

    private boolean isTitleClicked(int mouseX, int mouseY) {
        return isTitleHovered(mouseX, mouseY);
    }

    private boolean isTitleHovered(int mouseX, int mouseY) {
        return isPointInRect(mouseX, mouseY, getTitleTextX(), getTitleTextY(), PRESET_MENU_BUTTON_SIZE, PRESET_MENU_BUTTON_SIZE);
    }

    private int getTitleTextX() {
        return this.width - 8 - PRESET_MENU_BUTTON_SIZE;
    }

    private int getTitleTextY() {
        return (TITLE_BAR_HEIGHT - PRESET_MENU_BUTTON_SIZE) / 2;
    }

    String getModVersion() {
        return LoaderMetadata.getModVersion(PathmindCommon.MOD_ID);
    }

    private String getLoaderVersion() {
        return LoaderMetadata.getLoaderVersion();
    }

    String getCurrentMinecraftVersion() {
        return this.minecraft != null ? this.minecraft.getLaunchedVersion() : "Unknown";
    }

    String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return UiHitTest.contains(mouseX, mouseY, x, y, width, height);
    }

}
