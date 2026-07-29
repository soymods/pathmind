package com.pathmind.screen;

import com.pathmind.data.OnboardingPresetManager;
import com.pathmind.data.SettingsManager;
import com.pathmind.data.SettingsManager.Settings;
import com.pathmind.nodes.Node;
import com.pathmind.ui.onboarding.FirstRunTutorialOverlay;
import com.pathmind.ui.sidebar.Sidebar;
import com.pathmind.util.DrawContextBridge;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Owns first-run tutorial state, navigation, rendering, and target geometry.
 */
final class PathmindFirstRunTutorialController {
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int PLAY_BUTTON_SIZE = 18;
    private static final int STOP_BUTTON_SIZE = 18;
    private static final int VALIDATION_BUTTON_SIZE = 18;
    private static final int MARKETPLACE_BUTTON_WIDTH = 66;
    private static final int BOTTOM_BUTTON_SIZE = 18;

    interface Host {
        boolean isScreenPopupVisible();
        boolean isScreenCoordinateCaptureActive();
        boolean isParameterOverlayVisible();
        boolean isBookTextEditorVisible();
        void switchPreset(String presetName);
        void closeSettingsPopup();
        void hideSettingsPopupInstantly();
        void focusNodeById(String nodeId);
        int screenWidth();
        int screenHeight();
        int sidebarWidth();
        int titleTextX();
        int presetTabRightLimit();
        int stopButtonX();
        int stopButtonY();
        int playButtonX();
        int validationButtonX();
        int validationButtonY();
        int publishButtonX();
        int marketplaceButtonX();
        int workspaceButtonY();
        int accentColor();
        float zoomScale();
        List<Node> nodes();
        int worldToScreenX(int worldX);
        int worldToScreenY(int worldY);
    }

    private final Host host;
    private final FirstRunTutorialOverlay overlay = new FirstRunTutorialOverlay();
    private boolean pending = false;

    PathmindFirstRunTutorialController(Host host) {
        this.host = host;
    }

    void initialize(boolean completed) {
        pending = !completed;
    }

    boolean isVisible() {
        return overlay.isVisible();
    }

    void maybeShow() {
        if (!pending || overlay.isVisible()) {
            return;
        }
        if (host.isScreenPopupVisible()
            || host.isScreenCoordinateCaptureActive()
            || host.isParameterOverlayVisible()
            || host.isBookTextEditorVisible()) {
            return;
        }
        pending = false;
        showWithExamplePreset();
    }

    void replay() {
        pending = false;
        host.closeSettingsPopup();
        host.hideSettingsPopupInstantly();
        showWithExamplePreset();
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return overlay.mouseClicked(mouseX, mouseY, button, this::complete);
    }

    boolean keyPressed(int keyCode) {
        return overlay.keyPressed(keyCode, this::complete);
    }

    void render(GuiGraphics context, Font font, int mouseX, int mouseY) {
        if (!overlay.isVisible()) {
            return;
        }
        DrawContextBridge.startNewRootLayer(context);
        overlay.render(
            context,
            font,
            mouseX,
            mouseY,
            host.screenWidth(),
            host.screenHeight(),
            host.accentColor(),
            this::getTargetBounds
        );
    }

    private void complete() {
        pending = false;
        Settings settings = SettingsManager.getCurrent();
        settings.firstRunTutorialCompleted = true;
        SettingsManager.save(settings);
    }

    private void showWithExamplePreset() {
        if (OnboardingPresetManager.ensureTutorialPresetInstalled()) {
            host.switchPreset(OnboardingPresetManager.TUTORIAL_PRESET_NAME);
        }
        overlay.show(this::handleStepChanged);
    }

    private void handleStepChanged(FirstRunTutorialOverlay.Target target) {
        String nodeId = switch (target) {
            case WORKSPACE, EXAMPLE_START -> "tutorial-1-start";
            case EXAMPLE_INTRO -> "tutorial-1-intro";
            case EXAMPLE_LOOK -> "tutorial-1-look";
            case EXAMPLE_WALK -> "tutorial-1-walk";
            case EXAMPLE_ACTIONS -> "tutorial-1-jump";
            default -> null;
        };
        if (nodeId != null) {
            host.focusNodeById(nodeId);
        }
    }

    private int[] getTargetBounds(FirstRunTutorialOverlay.Target target) {
        int width = host.screenWidth();
        int height = host.screenHeight();
        return switch (target) {
            case PRESETS -> new int[]{
                host.titleTextX(),
                0,
                Math.max(160, Math.min(width - host.titleTextX() - 8, host.presetTabRightLimit() - host.titleTextX())),
                TITLE_BAR_HEIGHT
            };
            case SIDEBAR -> new int[]{
                0,
                TITLE_BAR_HEIGHT,
                Math.max(Sidebar.getCollapsedWidth(), host.sidebarWidth()),
                Math.max(1, height - TITLE_BAR_HEIGHT)
            };
            case WORKSPACE -> new int[]{
                Math.max(host.sidebarWidth() + 24, width / 2 - 150),
                Math.max(TITLE_BAR_HEIGHT + 36, height / 2 - 95),
                Math.min(300, Math.max(120, width - host.sidebarWidth() - 48)),
                Math.min(190, Math.max(90, height - TITLE_BAR_HEIGHT - 72))
            };
            case EXAMPLE_START -> getNodeBounds("tutorial-1-start");
            case EXAMPLE_INTRO -> getNodeBounds("tutorial-1-intro");
            case EXAMPLE_LOOK -> getNodeBounds("tutorial-1-look");
            case EXAMPLE_WALK -> getNodeBounds("tutorial-1-walk");
            case EXAMPLE_ACTIONS -> getNodeBounds("tutorial-1-jump", "tutorial-1-wait", "tutorial-1-finish");
            case RUN_CONTROLS -> new int[]{
                host.stopButtonX() - 4,
                host.stopButtonY() - 4,
                host.playButtonX() + PLAY_BUTTON_SIZE - host.stopButtonX() + 8,
                Math.max(PLAY_BUTTON_SIZE, STOP_BUTTON_SIZE) + 8
            };
            case VALIDATION -> new int[]{
                host.validationButtonX() - 4,
                host.validationButtonY() - 4,
                VALIDATION_BUTTON_SIZE + 8,
                VALIDATION_BUTTON_SIZE + 8
            };
            case MARKETPLACE -> new int[]{
                host.publishButtonX() - 4,
                host.workspaceButtonY() - 4,
                host.marketplaceButtonX() + MARKETPLACE_BUTTON_WIDTH - host.publishButtonX() + 8,
                BOTTOM_BUTTON_SIZE + 8
            };
            case NONE -> new int[]{width / 2 - 1, height / 2 - 1, 2, 2};
        };
    }

    private int[] getNodeBounds(String... nodeIds) {
        if (nodeIds == null || nodeIds.length == 0) {
            return null;
        }
        float scale = Math.max(0.1f, host.zoomScale());
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (String nodeId : nodeIds) {
            for (Node node : host.nodes()) {
                if (node == null || !nodeId.equals(node.getId())) {
                    continue;
                }
                int nodeLeft = host.worldToScreenX(node.getX());
                int nodeTop = host.worldToScreenY(node.getY());
                left = Math.min(left, nodeLeft);
                top = Math.min(top, nodeTop);
                right = Math.max(right, nodeLeft + Math.max(32, Math.round(node.getWidth() * scale)));
                bottom = Math.max(bottom, nodeTop + Math.max(24, Math.round(node.getHeight() * scale)));
                break;
            }
        }
        if (left == Integer.MAX_VALUE) {
            return null;
        }
        return new int[]{left, top, Math.max(32, right - left), Math.max(24, bottom - top)};
    }
}
