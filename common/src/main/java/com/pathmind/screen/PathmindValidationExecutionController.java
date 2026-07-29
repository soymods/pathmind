package com.pathmind.screen;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.control.PathmindValidationPanelRenderer;
import com.pathmind.ui.control.PathmindWorkspaceChrome;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.ui.tooltip.TooltipRenderer;
import com.pathmind.validation.GraphValidationIssue;
import com.pathmind.validation.GraphValidationResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Owns the workspace execution buttons and graph-validation panel.
 */
final class PathmindValidationExecutionController {
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int PLAY_BUTTON_SIZE = 18;
    private static final int PLAY_BUTTON_MARGIN = 6;
    private static final int STOP_BUTTON_SIZE = 18;
    private static final int CONTROL_BUTTON_GAP = 6;
    private static final int VALIDATION_BUTTON_SIZE = 18;
    private static final int VALIDATION_PANEL_WIDTH = 292;
    private static final int VALIDATION_PANEL_MAX_VISIBLE_ROWS = 8;
    private static final int VALIDATION_PANEL_ROW_HEIGHT = 22;
    private static final int VALIDATION_PANEL_PADDING = 8;
    private static final int VALIDATION_PANEL_BOTTOM_PADDING = 2;
    private static final int VALIDATION_PANEL_HEADER_HEIGHT = 34;
    private static final int VALIDATION_PANEL_FOOTER_HEIGHT = 18;

    interface Host {
        Font font();
        int screenWidth();
        int screenHeight();
        int sidebarWidth();
        int accentColor();
        boolean hasActiveRoutineWorkspace();
        boolean isPopupObscuringWorkspace();
        boolean showWorkspaceTooltips();
        GraphValidationResult validationResult();
        float hoverProgress(Object key, boolean hovered);
        void openRoutineWorkspace(String routineId);
        String activeRoutineWorkspaceId();
        void focusNode(String nodeId);
        void closePresetDropdown();
        void switchToRootWorkspace();
        void startExecutingAllGraphs();
        void stopExecutingAllGraphs();
    }

    private final Host host;
    private final AnimatedValue panelAnimation = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    private boolean panelOpen = false;

    PathmindValidationExecutionController(Host host) {
        this.host = host;
    }

    boolean shouldShowExecutionControls() {
        return true;
    }

    boolean isPanelOpen() {
        return panelOpen;
    }

    void closePanel() {
        panelOpen = false;
    }

    void tick() {
        panelAnimation.animateTo(panelOpen ? 1f : 0f, UITheme.TRANSITION_ANIM_MS);
        panelAnimation.tick();
    }

    void render(GuiGraphics context, int mouseX, int mouseY, boolean controlsDisabled,
                GraphValidationResult validationResult) {
        int chromeMouseX = controlsDisabled ? Integer.MIN_VALUE : mouseX;
        int chromeMouseY = controlsDisabled ? Integer.MIN_VALUE : mouseY;
        if (controlsDisabled && panelOpen) {
            panelOpen = false;
        }

        if (shouldShowExecutionControls()) {
            renderStopButton(context, chromeMouseX, chromeMouseY, false);
            renderPlayButton(context, chromeMouseX, chromeMouseY, false);
        }
        renderValidationPanel(context, mouseX, mouseY, validationResult);
        renderValidationButton(
            context, chromeMouseX, chromeMouseY, false, validationResult);
    }

    void renderTooltip(GuiGraphics context, int mouseX, int mouseY, boolean controlsDisabled,
                       GraphValidationResult validationResult) {
        int chromeMouseX = controlsDisabled ? Integer.MIN_VALUE : mouseX;
        int chromeMouseY = controlsDisabled ? Integer.MIN_VALUE : mouseY;
        int buttonX = validationButtonX();
        int buttonY = validationButtonY();
        boolean hovered = !controlsDisabled
            && PathmindWorkspaceChrome.contains(chromeMouseX, chromeMouseY, buttonX, buttonY,
                VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE);
        if (host.showWorkspaceTooltips() && !host.isPopupObscuringWorkspace() && !panelOpen && hovered) {
            TooltipRenderer.render(context, host.font(),
                Component.translatable("pathmind.validation.checks").getString(),
                chromeMouseX, chromeMouseY, host.screenWidth(), host.screenHeight());
        }
    }

    boolean handlePanelClick(int mouseX, int mouseY) {
        if (isValidationButtonClicked(mouseX, mouseY, 0)) {
            return false;
        }
        GraphValidationResult validationResult = host.validationResult();
        if (!panelOpen) {
            return false;
        }
        int[] bounds = getValidationPanelBounds(validationResult, 1f);
        if (!isPointInRect(mouseX, mouseY, bounds[0], bounds[1], bounds[2], bounds[3])) {
            panelOpen = false;
            return false;
        }

        PathmindValidationPanelRenderer.ClickedIssue clickedIssue = PathmindValidationPanelRenderer.findClickedIssue(
            validationResult,
            host.font(),
            mouseX,
            mouseY,
            bounds[0],
            bounds[1],
            bounds[2],
            VALIDATION_PANEL_HEADER_HEIGHT,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS,
            VALIDATION_PANEL_ROW_HEIGHT
        );
        if (clickedIssue.clicked()) {
            GraphValidationIssue issue = clickedIssue.issue();
            if (issue != null && issue.hasRoutineTarget()
                && !issue.getRoutineId().equals(host.activeRoutineWorkspaceId())) {
                host.openRoutineWorkspace(issue.getRoutineId());
            }
            if (issue != null && issue.hasNodeTarget()) {
                host.focusNode(issue.getNodeId());
            }
            return true;
        }
        return true;
    }

    boolean handleExecutionClick(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (shouldShowExecutionControls()) {
            if (isPointInRoutineExitButton(mouseX, mouseY)) {
                host.switchToRootWorkspace();
                return true;
            }
            if (isPointInPlayButton(mouseX, mouseY)) {
                host.closePresetDropdown();
                panelOpen = false;
                host.startExecutingAllGraphs();
                return true;
            }
            if (isPointInStopButton(mouseX, mouseY)) {
                host.closePresetDropdown();
                host.stopExecutingAllGraphs();
                return true;
            }
        }
        return false;
    }

    boolean handleValidationButtonClick(int mouseX, int mouseY, int button) {
        if (isValidationButtonClicked(mouseX, mouseY, button)) {
            panelOpen = !panelOpen;
            return true;
        }
        return false;
    }

    int playButtonX() {
        return PathmindWorkspaceChrome.playButtonX(host.screenWidth(), PLAY_BUTTON_SIZE, PLAY_BUTTON_MARGIN);
    }

    int playButtonY() {
        return PathmindWorkspaceChrome.playButtonY(TITLE_BAR_HEIGHT, PLAY_BUTTON_MARGIN);
    }

    int stopButtonX() {
        return PathmindWorkspaceChrome.stopButtonX(playButtonX(), CONTROL_BUTTON_GAP, STOP_BUTTON_SIZE);
    }

    int stopButtonY() {
        return playButtonY();
    }

    int validationButtonX() {
        if (shouldShowExecutionControls()) {
            return playButtonX();
        }
        return host.screenWidth() - VALIDATION_BUTTON_SIZE - PLAY_BUTTON_MARGIN;
    }

    int validationButtonY() {
        if (host.hasActiveRoutineWorkspace()) {
            return playButtonY() + PLAY_BUTTON_SIZE + CONTROL_BUTTON_GAP;
        }
        if (shouldShowExecutionControls()) {
            return playButtonY() + PLAY_BUTTON_SIZE + CONTROL_BUTTON_GAP;
        }
        return PathmindWorkspaceChrome.playButtonY(TITLE_BAR_HEIGHT, PLAY_BUTTON_MARGIN);
    }

    int routineExitButtonX() {
        return stopButtonX() - CONTROL_BUTTON_GAP - PLAY_BUTTON_SIZE;
    }

    int routineExitButtonY() {
        return playButtonY();
    }

    private void renderPlayButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = playButtonX();
        int buttonY = playButtonY();
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
        float hoverProgress = host.hoverProgress("play-button", hovered || executing);
        PathmindWorkspaceChrome.renderPlayButton(
            context,
            buttonX,
            buttonY,
            PLAY_BUTTON_SIZE,
            mouseX,
            mouseY,
            disabled,
            executing,
            hoverProgress,
            host.accentColor()
        );
    }

    private void renderStopButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int buttonX = stopButtonX();
        int buttonY = stopButtonY();
        boolean executing = ExecutionManager.getInstance().isGlobalExecutionActive();
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
        float hoverProgress = host.hoverProgress("stop-button", hovered || executing);
        PathmindWorkspaceChrome.renderStopButton(
            context,
            buttonX,
            buttonY,
            STOP_BUTTON_SIZE,
            mouseX,
            mouseY,
            disabled,
            executing,
            hoverProgress,
            host.accentColor()
        );
    }

    private boolean renderValidationButton(GuiGraphics context, int mouseX, int mouseY, boolean disabled,
                                           GraphValidationResult validationResult) {
        int buttonX = validationButtonX();
        int buttonY = validationButtonY();
        boolean active = panelOpen;
        boolean hovered = !disabled && PathmindWorkspaceChrome.contains(mouseX, mouseY, buttonX, buttonY, VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE);
        float hoverProgress = host.hoverProgress("validation-button", hovered || active);
        return PathmindValidationPanelRenderer.renderValidationButton(
            context,
            host.font(),
            buttonX,
            buttonY,
            VALIDATION_BUTTON_SIZE,
            mouseX,
            mouseY,
            active,
            disabled,
            hoverProgress,
            panelAnimation.getValue(),
            host.accentColor(),
            validationResult
        );
    }

    private void renderValidationPanel(GuiGraphics context, int mouseX, int mouseY,
                                       GraphValidationResult validationResult) {
        float progress = panelAnimation.getValue();
        if (progress <= 0.001f || validationResult == null) {
            return;
        }

        int[] bounds = getValidationPanelBounds(validationResult, progress);
        int panelX = bounds[0];
        int panelY = bounds[1];
        int panelWidth = bounds[2];
        int panelHeight = bounds[3];
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        context.enableScissor(panelX, panelY, panelX + panelWidth, panelY + panelHeight);
        PathmindValidationPanelRenderer.renderPanelAndIssues(
            context,
            host.font(),
            mouseX,
            mouseY,
            validationResult,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            VALIDATION_PANEL_PADDING,
            VALIDATION_PANEL_HEADER_HEIGHT,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS,
            VALIDATION_PANEL_ROW_HEIGHT,
            this::getValidationIssueHoverProgress
        );
        PathmindValidationPanelRenderer.renderFooter(
            context,
            host.font(),
            validationResult,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            VALIDATION_PANEL_PADDING,
            VALIDATION_PANEL_FOOTER_HEIGHT,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS
        );
        context.disableScissor();
    }

    private int[] getValidationPanelBounds(GraphValidationResult validationResult, float progress) {
        return PathmindValidationPanelRenderer.getPanelBounds(
            validationResult,
            host.font(),
            validationButtonX() + VALIDATION_BUTTON_SIZE,
            validationButtonY(),
            progress,
            VALIDATION_PANEL_WIDTH,
            VALIDATION_PANEL_MAX_VISIBLE_ROWS,
            VALIDATION_PANEL_HEADER_HEIGHT,
            0,
            VALIDATION_PANEL_FOOTER_HEIGHT,
            VALIDATION_PANEL_BOTTOM_PADDING,
            VALIDATION_PANEL_ROW_HEIGHT
        );
    }

    private float getValidationIssueHoverProgress(GraphValidationIssue issue, int index, boolean hovered) {
        String issueKey = issue == null ? "unknown-" + index : issue.getCode() + ":" + issue.getNodeId() + ":" + index;
        return host.hoverProgress("validation-issue-row:" + issueKey, hovered);
    }

    private boolean isValidationButtonClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        return isPointInRect(mouseX, mouseY, validationButtonX(), validationButtonY(),
            VALIDATION_BUTTON_SIZE, VALIDATION_BUTTON_SIZE);
    }

    private boolean isPointInPlayButton(int mouseX, int mouseY) {
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, playButtonX(), playButtonY(),
            PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
    }

    private boolean isPointInStopButton(int mouseX, int mouseY) {
        return PathmindWorkspaceChrome.contains(mouseX, mouseY, stopButtonX(), stopButtonY(),
            STOP_BUTTON_SIZE, STOP_BUTTON_SIZE);
    }

    private boolean isPointInRoutineExitButton(int mouseX, int mouseY) {
        return host.hasActiveRoutineWorkspace() && PathmindWorkspaceChrome.contains(
            mouseX, mouseY, routineExitButtonX(), routineExitButtonY(), PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE);
    }

    private static boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
