package com.pathmind.screen;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.pathmind.data.NodeGraphData;
import com.pathmind.data.NodeGraphPersistence;
import com.pathmind.data.PresetManager;
import com.pathmind.data.WorkspaceFileAccess;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import com.pathmind.util.LoaderMetadata;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.VersionSupport;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Owns the visual editor's general workspace dialogs and import/export workflow. */
final class PathmindWorkspaceDialogController {
    static final int CLEAR_POPUP_WIDTH = 280;
    static final int CLEAR_POPUP_HEIGHT = 150;
    static final int IMPORT_EXPORT_POPUP_WIDTH = 360;
    static final int IMPORT_EXPORT_POPUP_HEIGHT = 210;
    static final int INFO_POPUP_WIDTH = 320;
    static final int INFO_POPUP_HEIGHT = 180;
    static final int MISSING_BARITONE_POPUP_WIDTH = 360;
    static final int MISSING_BARITONE_POPUP_HEIGHT = 175;
    static final int MISSING_UI_UTILS_POPUP_WIDTH = 360;
    static final int MISSING_UI_UTILS_POPUP_HEIGHT = 175;

    private static final String INFO_POPUP_AUTHOR = "soymods";
    private static final String INFO_POPUP_TARGET_VERSION = VersionSupport.SUPPORTED_RANGE;
    private static final Component INFO_POPUP_TITLE_TEXT = Component.literal("Pathmind");
    private static final String UI_UTILS_DOWNLOAD_URL = "https://ui-utils.com";
    private static final boolean IS_MAC_OS = System.getProperty("os.name", "")
        .toLowerCase(java.util.Locale.ROOT)
        .contains("mac");

    interface Host {
        Font font();
        boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height);
        int[] boundedScaledPopupBounds(PopupAnimationHandler animation, int width, int height);
        int boundedPopupContentY(int popupY, PopupAnimationHandler animation, int preferredHeight);
        void setOverlayCutout(int x, int y, int width, int height);
        void drawPopupContainer(GuiGraphics context, int x, int y, int width, int height,
                                PopupAnimationHandler animation);
        boolean enablePopupScissor(GuiGraphics context, int popupX, int popupY, int width, int height);
        void disablePopupScissor(GuiGraphics context, boolean enabled);
        int popupAnimatedColor(PopupAnimationHandler animation, int color);
        void drawPopupTextWithEllipsis(GuiGraphics context, String text, int x, int y, int maxWidth, int color);
        void drawPopupCenteredTextWithEllipsis(GuiGraphics context, String text, int centerX, int y,
                                               int maxWidth, int color);
        void drawPopupButton(GuiGraphics context, int x, int y, int width, int height, boolean hovered,
                             Component label, PathmindPopupRenderer.ButtonStyle style,
                             PopupAnimationHandler animation);
        void resetBoundedPopupScroll(PopupAnimationHandler animation);
        void dismissParameterOverlay();
        void closeCreatePresetPopupIfVisible();
        void closeSettingsPopup();
        void closePresetDropdown();
        void clearWorkspace();
        boolean containsBaritoneNodes();
        boolean containsUiUtilsNodes();
        NodeGraphData snapshotRootPresetWorkspace();
        String activePresetName();
        void applyImportedPreset(String presetName, NodeGraphData importedData);
        void runOnClientThread(Runnable task);
        String currentMinecraftVersion();
        String modVersion();
        String loaderVersion();
        void copyToClipboard(String value);
    }

    private final Host host;
    private final boolean baritoneAvailable;
    private final boolean uiUtilsAvailable;
    private final PopupAnimationHandler clearPopupAnimation;
    private final PopupAnimationHandler importExportPopupAnimation;
    private final PopupAnimationHandler infoPopupAnimation;
    private final PopupAnimationHandler missingBaritonePopupAnimation;
    private final PopupAnimationHandler missingUiUtilsPopupAnimation;
    private Path lastImportExportPath;
    private String importExportStatus = "";
    private int importExportStatusColor = UITheme.TEXT_SECONDARY;
    private boolean importExportBusy = false;

    PathmindWorkspaceDialogController(
        Host host,
        boolean baritoneAvailable,
        boolean uiUtilsAvailable,
        PopupAnimationHandler clearPopupAnimation,
        PopupAnimationHandler importExportPopupAnimation,
        PopupAnimationHandler infoPopupAnimation,
        PopupAnimationHandler missingBaritonePopupAnimation,
        PopupAnimationHandler missingUiUtilsPopupAnimation
    ) {
        this.host = host;
        this.baritoneAvailable = baritoneAvailable;
        this.uiUtilsAvailable = uiUtilsAvailable;
        this.clearPopupAnimation = clearPopupAnimation;
        this.importExportPopupAnimation = importExportPopupAnimation;
        this.infoPopupAnimation = infoPopupAnimation;
        this.missingBaritonePopupAnimation = missingBaritonePopupAnimation;
        this.missingUiUtilsPopupAnimation = missingUiUtilsPopupAnimation;
    }

    boolean isImportExportBusy() {
        return importExportBusy;
    }

    boolean shouldBlockBaritoneNode(NodeType nodeType) {
        if (nodeType == null || !nodeType.requiresBaritone()) {
            return false;
        }
        if (baritoneAvailable) {
            return false;
        }
        host.resetBoundedPopupScroll(missingBaritonePopupAnimation);
        missingBaritonePopupAnimation.show();
        return true;
    }

    void refreshMissingBaritonePopup() {
        if (!baritoneAvailable && host.containsBaritoneNodes()) {
            host.resetBoundedPopupScroll(missingBaritonePopupAnimation);
            missingBaritonePopupAnimation.show();
        } else {
            missingBaritonePopupAnimation.hide();
        }
    }

    boolean shouldBlockUiUtilsNode(NodeType nodeType) {
        if (nodeType == null || !nodeType.requiresUiUtils()) {
            return false;
        }
        if (uiUtilsAvailable) {
            return false;
        }
        host.resetBoundedPopupScroll(missingUiUtilsPopupAnimation);
        missingUiUtilsPopupAnimation.show();
        return true;
    }

    void refreshMissingUiUtilsPopup() {
        if (!uiUtilsAvailable && host.containsUiUtilsNodes()) {
            host.resetBoundedPopupScroll(missingUiUtilsPopupAnimation);
            missingUiUtilsPopupAnimation.show();
        } else {
            missingUiUtilsPopupAnimation.hide();
        }
    }

    void renderClearConfirmationPopup(GuiGraphics context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, clearPopupAnimation.getPopupAlpha());

        int[] bounds = host.boundedScaledPopupBounds(clearPopupAnimation, CLEAR_POPUP_WIDTH, CLEAR_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.boundedPopupContentY(popupY, clearPopupAnimation, CLEAR_POPUP_HEIGHT);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        host.drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, clearPopupAnimation);
        boolean popupScissor = host.enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredString(
            host.font(),
            Component.translatable("pathmind.popup.clearWorkspace.title"),
            popupX + scaledWidth / 2,
            contentY + 14,
            host.popupAnimatedColor(clearPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        host.drawPopupTextWithEllipsis(
            context,
            Component.translatable("pathmind.popup.clearWorkspace.message").getString(),
            popupX + 20,
            contentY + 48,
            scaledWidth - 40,
            host.popupAnimatedColor(clearPopupAnimation, UITheme.TEXT_SECONDARY)
        );

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(
            popupX, scaledWidth, contentY, CLEAR_POPUP_HEIGHT, 90, 20, 16);
        PathmindPopupLayout.Rect cancelButton = buttonRow.left();
        PathmindPopupLayout.Rect confirmButton = buttonRow.right();
        int buttonWidth = cancelButton.width();
        int buttonHeight = cancelButton.height();
        int buttonY = cancelButton.y();
        int cancelX = cancelButton.x();
        int confirmX = confirmButton.x();

        boolean cancelHovered = host.isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);
        boolean confirmHovered = host.isPointInRect(mouseX, mouseY, confirmX, buttonY, buttonWidth, buttonHeight);

        host.drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered,
            Component.translatable("pathmind.button.cancel"), PathmindPopupRenderer.ButtonStyle.DEFAULT, clearPopupAnimation);
        host.drawPopupButton(context, confirmX, buttonY, buttonWidth, buttonHeight, confirmHovered,
            Component.translatable("pathmind.button.clear"), PathmindPopupRenderer.ButtonStyle.PRIMARY, clearPopupAnimation);
        host.disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderImportExportPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, importExportPopupAnimation.getPopupAlpha());

        int[] bounds = host.boundedScaledPopupBounds(
            importExportPopupAnimation, IMPORT_EXPORT_POPUP_WIDTH, IMPORT_EXPORT_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.boundedPopupContentY(popupY, importExportPopupAnimation, IMPORT_EXPORT_POPUP_HEIGHT);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        host.drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, importExportPopupAnimation);
        boolean popupScissor = host.enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredString(
            host.font(),
            Component.translatable("pathmind.popup.importExport.title"),
            popupX + scaledWidth / 2,
            contentY + 14,
            host.popupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int infoY = contentY + 44;
        String importInfo = Component.translatable("pathmind.popup.importExport.importInfo").getString();
        host.drawPopupTextWithEllipsis(context, importInfo, popupX + 20, infoY, scaledWidth - 40,
            host.popupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        String exportInfo = Component.translatable("pathmind.popup.importExport.exportInfo").getString();
        host.drawPopupTextWithEllipsis(context, exportInfo, popupX + 20, infoY + 14, scaledWidth - 40,
            host.popupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_SECONDARY));

        Path defaultPath = NodeGraphPersistence.getDefaultSavePath();
        if (defaultPath != null) {
            String defaultLabel = Component.translatable(
                "pathmind.popup.importExport.defaultSave", defaultPath.toString()).getString();
            host.drawPopupTextWithEllipsis(context, defaultLabel, popupX + 20, infoY + 30, scaledWidth - 40,
                host.popupAnimatedColor(importExportPopupAnimation, UITheme.TEXT_TERTIARY));
        }

        if (!importExportStatus.isEmpty()) {
            int textAreaWidth = scaledWidth - 40;
            host.drawPopupTextWithEllipsis(
                context, importExportStatus, popupX + 20, contentY + IMPORT_EXPORT_POPUP_HEIGHT - 56,
                textAreaWidth, host.popupAnimatedColor(importExportPopupAnimation, importExportStatusColor));
        }

        PathmindPopupLayout.ThreeButtonRow buttonRow = PathmindPopupLayout.leftPairRightButtonRow(
            popupX, scaledWidth, contentY, IMPORT_EXPORT_POPUP_HEIGHT, 100, 20, 8, 16);
        PathmindPopupLayout.Rect importButton = buttonRow.first();
        PathmindPopupLayout.Rect exportButton = buttonRow.second();
        PathmindPopupLayout.Rect cancelButton = buttonRow.third();
        int buttonWidth = importButton.width();
        int buttonHeight = importButton.height();
        int buttonY = importButton.y();
        int importX = importButton.x();
        int exportX = exportButton.x();
        int cancelX = cancelButton.x();

        boolean importHovered = !importExportBusy
            && host.isPointInRect(mouseX, mouseY, importX, buttonY, buttonWidth, buttonHeight);
        boolean exportHovered = !importExportBusy
            && host.isPointInRect(mouseX, mouseY, exportX, buttonY, buttonWidth, buttonHeight);
        boolean cancelHovered = host.isPointInRect(mouseX, mouseY, cancelX, buttonY, buttonWidth, buttonHeight);

        host.drawPopupButton(context, importX, buttonY, buttonWidth, buttonHeight, importHovered,
            Component.translatable("pathmind.button.import"), PathmindPopupRenderer.ButtonStyle.PRIMARY,
            importExportPopupAnimation);
        host.drawPopupButton(context, exportX, buttonY, buttonWidth, buttonHeight, exportHovered,
            Component.translatable("pathmind.button.export"), PathmindPopupRenderer.ButtonStyle.PRIMARY,
            importExportPopupAnimation);
        host.drawPopupButton(context, cancelX, buttonY, buttonWidth, buttonHeight, cancelHovered,
            Component.translatable("pathmind.button.close"), PathmindPopupRenderer.ButtonStyle.DEFAULT,
            importExportPopupAnimation);
        host.disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderInfoPopup(GuiGraphics context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, infoPopupAnimation.getPopupAlpha());

        int[] bounds = host.boundedScaledPopupBounds(infoPopupAnimation, INFO_POPUP_WIDTH, INFO_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.boundedPopupContentY(popupY, infoPopupAnimation, INFO_POPUP_HEIGHT);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        host.drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, infoPopupAnimation);
        boolean popupScissor = host.enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        context.drawCenteredString(
            host.font(),
            INFO_POPUP_TITLE_TEXT,
            popupX + scaledWidth / 2,
            contentY + 14,
            host.popupAnimatedColor(infoPopupAnimation, UITheme.TEXT_PRIMARY)
        );

        int textStartY = contentY + 42;
        int lineSpacing = 12;
        int centerX = popupX + scaledWidth / 2;

        String authorLine = Component.translatable("pathmind.popup.info.createdBy", INFO_POPUP_AUTHOR).getString();
        String targetLine = Component.translatable(
            "pathmind.popup.info.builtForMinecraft", INFO_POPUP_TARGET_VERSION).getString();
        String currentLine = Component.translatable(
            "pathmind.popup.info.runningMinecraft", host.currentMinecraftVersion()).getString();
        String buildLine = Component.translatable("pathmind.popup.info.currentBuild", host.modVersion()).getString();
        String loaderLine = LoaderMetadata.getLoaderName() + ": " + host.loaderVersion();

        int maxCenteredWidth = scaledWidth - 40;
        host.drawPopupCenteredTextWithEllipsis(context, authorLine, centerX, textStartY, maxCenteredWidth,
            host.popupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupCenteredTextWithEllipsis(context, targetLine, centerX, textStartY + lineSpacing,
            maxCenteredWidth, host.popupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupCenteredTextWithEllipsis(context, currentLine, centerX, textStartY + lineSpacing * 2,
            maxCenteredWidth, host.popupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupCenteredTextWithEllipsis(context, buildLine, centerX, textStartY + lineSpacing * 3,
            maxCenteredWidth, host.popupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupCenteredTextWithEllipsis(context, loaderLine, centerX, textStartY + lineSpacing * 4,
            maxCenteredWidth, host.popupAnimatedColor(infoPopupAnimation, UITheme.TEXT_SECONDARY));

        PathmindPopupLayout.Rect closeButton = PathmindPopupLayout.centeredButton(
            popupX, scaledWidth, contentY, INFO_POPUP_HEIGHT, 100, 20, 16);
        int buttonWidth = closeButton.width();
        int buttonHeight = closeButton.height();
        int buttonX = closeButton.x();
        int buttonY = closeButton.y();
        boolean closeHovered = host.isPointInRect(
            mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);

        host.drawPopupButton(
            context, buttonX, buttonY, buttonWidth, buttonHeight, closeHovered,
            Component.translatable("pathmind.button.close"), PathmindPopupRenderer.ButtonStyle.DEFAULT,
            infoPopupAnimation
        );
        host.disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderMissingBaritonePopup(GuiGraphics context, int mouseX, int mouseY) {
        renderMissingDependencyPopup(
            context, mouseX, mouseY, missingBaritonePopupAnimation,
            MISSING_BARITONE_POPUP_WIDTH, MISSING_BARITONE_POPUP_HEIGHT,
            "pathmind.popup.missingBaritone.title", "pathmind.popup.missingBaritone.message",
            BaritoneDependencyChecker.DOWNLOAD_URL
        );
    }

    void renderMissingUiUtilsPopup(GuiGraphics context, int mouseX, int mouseY) {
        renderMissingDependencyPopup(
            context, mouseX, mouseY, missingUiUtilsPopupAnimation,
            MISSING_UI_UTILS_POPUP_WIDTH, MISSING_UI_UTILS_POPUP_HEIGHT,
            "pathmind.popup.missingUiUtils.title", "pathmind.popup.missingUiUtils.message",
            UI_UTILS_DOWNLOAD_URL
        );
    }

    private void renderMissingDependencyPopup(
        GuiGraphics context,
        int mouseX,
        int mouseY,
        PopupAnimationHandler animation,
        int preferredWidth,
        int preferredHeight,
        String titleKey,
        String messageKey,
        String downloadUrl
    ) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, animation.getPopupAlpha());

        int[] bounds = host.boundedScaledPopupBounds(animation, preferredWidth, preferredHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.boundedPopupContentY(popupY, animation, preferredHeight);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        host.drawPopupContainer(context, popupX, popupY, scaledWidth, scaledHeight, animation);
        boolean popupScissor = host.enablePopupScissor(context, popupX, popupY, scaledWidth, scaledHeight);

        int centerX = popupX + scaledWidth / 2;
        int messageY = contentY + 16;
        int maxCenteredWidth = scaledWidth - 40;
        host.drawPopupCenteredTextWithEllipsis(context, Component.translatable(titleKey).getString(),
            centerX, messageY, maxCenteredWidth, host.popupAnimatedColor(animation, UITheme.TEXT_PRIMARY));
        host.drawPopupCenteredTextWithEllipsis(context, Component.translatable(messageKey).getString(),
            centerX, messageY + 16, maxCenteredWidth, host.popupAnimatedColor(animation, UITheme.TEXT_PRIMARY));
        host.drawPopupCenteredTextWithEllipsis(context, downloadUrl, centerX, messageY + 30,
            maxCenteredWidth, host.popupAnimatedColor(animation, UITheme.LINK_COLOR));

        PathmindPopupLayout.ThreeButtonRow buttonRow = PathmindPopupLayout.threeButtonRow(
            popupX, scaledWidth, contentY, preferredHeight, 100, 20, 8, 10);
        PathmindPopupLayout.Rect openButton = buttonRow.first();
        PathmindPopupLayout.Rect copyButton = buttonRow.second();
        PathmindPopupLayout.Rect closeButton = buttonRow.third();
        int buttonWidth = openButton.width();
        int buttonHeight = openButton.height();
        int buttonY = openButton.y();
        int openX = openButton.x();
        int copyX = copyButton.x();
        int closeX = closeButton.x();

        boolean openHovered = host.isPointInRect(mouseX, mouseY, openX, buttonY, buttonWidth, buttonHeight);
        boolean copyHovered = host.isPointInRect(mouseX, mouseY, copyX, buttonY, buttonWidth, buttonHeight);
        boolean closeHovered = host.isPointInRect(mouseX, mouseY, closeX, buttonY, buttonWidth, buttonHeight);

        host.drawPopupButton(context, openX, buttonY, buttonWidth, buttonHeight, openHovered,
            Component.translatable("pathmind.button.openLink"), PathmindPopupRenderer.ButtonStyle.PRIMARY, animation);
        host.drawPopupButton(context, copyX, buttonY, buttonWidth, buttonHeight, copyHovered,
            Component.translatable("pathmind.button.copyLink"), PathmindPopupRenderer.ButtonStyle.PRIMARY, animation);
        host.drawPopupButton(context, closeX, buttonY, buttonWidth, buttonHeight, closeHovered,
            Component.translatable("pathmind.button.close"), PathmindPopupRenderer.ButtonStyle.DEFAULT, animation);
        host.disablePopupScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    boolean handleClearPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int[] bounds = host.boundedScaledPopupBounds(clearPopupAnimation, CLEAR_POPUP_WIDTH, CLEAR_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.boundedPopupContentY(popupY, clearPopupAnimation, CLEAR_POPUP_HEIGHT);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(
            popupX, scaledWidth, contentY, CLEAR_POPUP_HEIGHT, 90, 20, 16);
        PathmindPopupLayout.Rect cancelButton = buttonRow.left();
        PathmindPopupLayout.Rect confirmButton = buttonRow.right();
        int buttonWidth = cancelButton.width();
        int buttonHeight = cancelButton.height();
        int buttonY = cancelButton.y();
        int cancelX = cancelButton.x();
        int confirmX = confirmButton.x();

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (!host.isPointInRect(mouseXi, mouseYi, popupX, popupY, scaledWidth, scaledHeight)) {
            clearPopupAnimation.hide();
            return true;
        }

        if (host.isPointInRect(mouseXi, mouseYi, confirmX, buttonY, buttonWidth, buttonHeight)) {
            confirmClearWorkspace();
            return true;
        }

        if (host.isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            clearPopupAnimation.hide();
            return true;
        }

        return true;
    }

    boolean handleImportExportPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int[] bounds = host.boundedScaledPopupBounds(
            importExportPopupAnimation, IMPORT_EXPORT_POPUP_WIDTH, IMPORT_EXPORT_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.boundedPopupContentY(popupY, importExportPopupAnimation, IMPORT_EXPORT_POPUP_HEIGHT);
        PathmindPopupLayout.ThreeButtonRow buttonRow = PathmindPopupLayout.leftPairRightButtonRow(
            popupX, scaledWidth, contentY, IMPORT_EXPORT_POPUP_HEIGHT, 100, 20, 8, 16);
        PathmindPopupLayout.Rect importButton = buttonRow.first();
        PathmindPopupLayout.Rect exportButton = buttonRow.second();
        PathmindPopupLayout.Rect cancelButton = buttonRow.third();
        int buttonWidth = importButton.width();
        int buttonHeight = importButton.height();
        int buttonY = importButton.y();
        int importX = importButton.x();
        int exportX = exportButton.x();
        int cancelX = cancelButton.x();

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (!host.isPointInRect(mouseXi, mouseYi, popupX, popupY, scaledWidth, scaledHeight)) {
            closeImportExportPopup();
            return true;
        }

        if (!importExportBusy && host.isPointInRect(
            mouseXi, mouseYi, importX, buttonY, buttonWidth, buttonHeight)) {
            attemptImport();
            return true;
        }

        if (!importExportBusy && host.isPointInRect(
            mouseXi, mouseYi, exportX, buttonY, buttonWidth, buttonHeight)) {
            attemptExport();
            return true;
        }

        if (host.isPointInRect(mouseXi, mouseYi, cancelX, buttonY, buttonWidth, buttonHeight)) {
            closeImportExportPopup();
            return true;
        }

        return true;
    }

    boolean handleMissingBaritonePopupClick(double mouseX, double mouseY, int button) {
        return handleMissingDependencyPopupClick(
            mouseX, mouseY, button, missingBaritonePopupAnimation,
            MISSING_BARITONE_POPUP_WIDTH, MISSING_BARITONE_POPUP_HEIGHT,
            BaritoneDependencyChecker.DOWNLOAD_URL
        );
    }

    boolean handleMissingUiUtilsPopupClick(double mouseX, double mouseY, int button) {
        return handleMissingDependencyPopupClick(
            mouseX, mouseY, button, missingUiUtilsPopupAnimation,
            MISSING_UI_UTILS_POPUP_WIDTH, MISSING_UI_UTILS_POPUP_HEIGHT,
            UI_UTILS_DOWNLOAD_URL
        );
    }

    private boolean handleMissingDependencyPopupClick(
        double mouseX,
        double mouseY,
        int button,
        PopupAnimationHandler animation,
        int preferredWidth,
        int preferredHeight,
        String downloadUrl
    ) {
        if (button != 0) {
            return true;
        }

        int[] bounds = host.boundedScaledPopupBounds(animation, preferredWidth, preferredHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int contentY = host.boundedPopupContentY(popupY, animation, preferredHeight);
        PathmindPopupLayout.ThreeButtonRow buttonRow = PathmindPopupLayout.threeButtonRow(
            popupX, popupWidth, contentY, preferredHeight, 100, 20, 8, 10);
        PathmindPopupLayout.Rect openButton = buttonRow.first();
        PathmindPopupLayout.Rect copyButton = buttonRow.second();
        PathmindPopupLayout.Rect closeButton = buttonRow.third();
        int buttonWidth = openButton.width();
        int buttonHeight = openButton.height();
        int buttonY = openButton.y();
        int openX = openButton.x();
        int copyX = copyButton.x();
        int closeX = closeButton.x();

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (host.isPointInRect(mouseXi, mouseYi, openX, buttonY, buttonWidth, buttonHeight)) {
            Util.getPlatform().openUri(downloadUrl);
            return true;
        }

        if (host.isPointInRect(mouseXi, mouseYi, copyX, buttonY, buttonWidth, buttonHeight)) {
            host.copyToClipboard(downloadUrl);
            return true;
        }

        if (host.isPointInRect(mouseXi, mouseYi, closeX, buttonY, buttonWidth, buttonHeight)) {
            animation.hide();
            return true;
        }

        return true;
    }

    boolean handleInfoPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int[] bounds = host.boundedScaledPopupBounds(infoPopupAnimation, INFO_POPUP_WIDTH, INFO_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        int contentY = host.boundedPopupContentY(popupY, infoPopupAnimation, INFO_POPUP_HEIGHT);
        PathmindPopupLayout.Rect closeButton = PathmindPopupLayout.centeredButton(
            popupX, popupWidth, contentY, INFO_POPUP_HEIGHT, 100, 20, 16);
        int buttonWidth = closeButton.width();
        int buttonHeight = closeButton.height();
        int buttonX = closeButton.x();
        int buttonY = closeButton.y();

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;

        if (host.isPointInRect(mouseXi, mouseYi, buttonX, buttonY, buttonWidth, buttonHeight)) {
            closeInfoPopup();
            return true;
        }

        if (!host.isPointInRect(mouseXi, mouseYi, popupX, popupY, popupWidth, popupHeight)) {
            closeInfoPopup();
            return true;
        }

        return true;
    }

    void closeInfoPopup() {
        host.resetBoundedPopupScroll(infoPopupAnimation);
        infoPopupAnimation.hide();
    }

    void openClearPopup() {
        host.dismissParameterOverlay();
        closeImportExportPopup();
        host.closeCreatePresetPopupIfVisible();
        closeInfoPopup();
        host.closeSettingsPopup();
        host.closePresetDropdown();
        host.resetBoundedPopupScroll(clearPopupAnimation);
        clearPopupAnimation.show();
    }

    void confirmClearWorkspace() {
        host.clearWorkspace();
        host.resetBoundedPopupScroll(clearPopupAnimation);
        clearPopupAnimation.hide();
    }

    void openImportExportPopup() {
        host.dismissParameterOverlay();
        clearPopupAnimation.hide();
        host.closeCreatePresetPopupIfVisible();
        closeInfoPopup();
        host.closeSettingsPopup();
        host.closePresetDropdown();
        host.resetBoundedPopupScroll(importExportPopupAnimation);
        importExportPopupAnimation.show();
        clearImportExportStatus();
        importExportBusy = false;
        if (lastImportExportPath == null) {
            lastImportExportPath = NodeGraphPersistence.getDefaultSavePath();
        }
    }

    void closeImportExportPopup() {
        host.resetBoundedPopupScroll(importExportPopupAnimation);
        importExportPopupAnimation.hide();
    }

    void attemptImport() {
        String defaultPath = lastImportExportPath != null
            ? lastImportExportPath.toString()
            : Optional.ofNullable(NodeGraphPersistence.getDefaultSavePath())
                .map(Path::toString)
                .orElse("");
        importExportBusy = true;
        setImportExportStatus(
            Component.translatable("pathmind.status.waitingForImportFile").getString(), UITheme.TEXT_SECONDARY);
        WorkspaceFileAccess.supplyAsync(() -> openWorkspaceImportDialog(defaultPath))
            .whenComplete((selection, throwable) -> host.runOnClientThread(() -> {
                if (throwable != null) {
                    importExportBusy = false;
                    setImportExportStatus(
                        Component.translatable("pathmind.status.failedOpenImportDialog").getString(),
                        UITheme.STATE_ERROR);
                    return;
                }
                if (selection == null) {
                    importExportBusy = false;
                    setImportExportStatus(
                        Component.translatable("pathmind.status.importCancelled").getString(),
                        UITheme.TEXT_SECONDARY);
                    return;
                }
                try {
                    Path path = Paths.get(selection.trim());
                    beginImportFromPath(path);
                } catch (InvalidPathException ex) {
                    importExportBusy = false;
                    setImportExportStatus(
                        Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
                }
            }));
    }

    private void beginImportFromPath(Path path) {
        try {
            lastImportExportPath = path;
            Path fileName = path.getFileName();
            String fileLabel = fileName != null ? fileName.toString() : path.toString();
            String currentPresetName = host.activePresetName();
            NodeGraphData currentPresetSnapshot = host.snapshotRootPresetWorkspace();
            setImportExportStatus(
                Component.translatable("pathmind.status.importingWorkspace").getString(), UITheme.TEXT_SECONDARY);
            WorkspaceFileAccess.supplyAsync(() -> {
                if (currentPresetSnapshot != null && currentPresetName != null && !currentPresetName.isBlank()) {
                    NodeGraphPersistence.saveNodeGraphDataForPreset(currentPresetName, currentPresetSnapshot);
                }
                Optional<String> importedPreset = PresetManager.importPresetFromFile(path);
                if (importedPreset.isEmpty()) {
                    return ImportOperationResult.failed(fileLabel);
                }
                NodeGraphData importedData = NodeGraphPersistence.loadNodeGraphForPreset(importedPreset.get());
                return ImportOperationResult.succeeded(fileLabel, importedPreset.get(), importedData);
            }).whenComplete((result, throwable) -> host.runOnClientThread(() -> {
                importExportBusy = false;
                if (throwable != null || result == null || !result.success) {
                    setImportExportStatus(
                        Component.translatable("pathmind.status.failedImportWorkspaceFrom", fileLabel).getString(),
                        UITheme.STATE_ERROR);
                    return;
                }
                host.applyImportedPreset(result.presetName, result.importedData);
                setImportExportStatus(
                    Component.translatable(
                        "pathmind.status.importedWorkspaceAsPreset", result.fileLabel, result.presetName).getString(),
                    UITheme.STATE_SUCCESS
                );
            }));
        } catch (InvalidPathException ex) {
            setImportExportStatus(
                Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
        }
    }

    void attemptExport() {
        Path defaultSavePath = Optional.ofNullable(lastImportExportPath)
            .orElseGet(NodeGraphPersistence::getDefaultSavePath);
        String defaultPathString = defaultSavePath != null ? defaultSavePath.toString() : "workspace.json";
        importExportBusy = true;
        setImportExportStatus(
            Component.translatable("pathmind.status.waitingForExportPath").getString(), UITheme.TEXT_SECONDARY);
        WorkspaceFileAccess.supplyAsync(() -> openWorkspaceExportDialog(defaultPathString))
            .whenComplete((selection, throwable) -> host.runOnClientThread(() -> {
                if (throwable != null) {
                    importExportBusy = false;
                    setImportExportStatus(
                        Component.translatable("pathmind.status.failedOpenExportDialog").getString(),
                        UITheme.STATE_ERROR);
                    return;
                }
                if (selection == null) {
                    importExportBusy = false;
                    setImportExportStatus(
                        Component.translatable("pathmind.status.exportCancelled").getString(),
                        UITheme.TEXT_SECONDARY);
                    return;
                }
                try {
                    Path path = Paths.get(selection.trim());
                    beginExportToPath(path);
                } catch (InvalidPathException ex) {
                    importExportBusy = false;
                    setImportExportStatus(
                        Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
                }
            }));
    }

    private void beginExportToPath(Path path) {
        try {
            NodeGraphData snapshot = host.snapshotRootPresetWorkspace();
            setImportExportStatus(
                Component.translatable("pathmind.status.exportingWorkspace").getString(), UITheme.TEXT_SECONDARY);
            WorkspaceFileAccess.supplyExportAsync(() -> NodeGraphPersistence.saveNodeGraphDataToPath(snapshot, path))
                .whenComplete((success, throwable) -> host.runOnClientThread(() -> {
                    importExportBusy = false;
                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        setImportExportStatus(
                            Component.translatable("pathmind.status.failedExportWorkspace").getString(),
                            UITheme.STATE_ERROR);
                        return;
                    }
                    lastImportExportPath = path;
                    Path fileName = path.getFileName();
                    setImportExportStatus(
                        Component.translatable(
                            "pathmind.status.exportedWorkspaceTo",
                            fileName != null ? fileName.toString() : path.toString()).getString(),
                        UITheme.STATE_SUCCESS);
                }));
        } catch (InvalidPathException ex) {
            setImportExportStatus(
                Component.translatable("pathmind.status.invalidFilePath").getString(), UITheme.STATE_ERROR);
        }
    }

    private String openWorkspaceImportDialog(String defaultPath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = IS_MAC_OS ? null : createJsonFilterPatterns(stack);
            return TinyFileDialogs.tinyfd_openFileDialog(
                Component.translatable("pathmind.dialog.importWorkspace").getString(),
                defaultPath,
                filters,
                filters != null ? Component.translatable("pathmind.dialog.jsonFiles").getString() : null,
                false
            );
        }
    }

    private String openWorkspaceExportDialog(String defaultPath) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = IS_MAC_OS ? null : createJsonFilterPatterns(stack);
            return TinyFileDialogs.tinyfd_saveFileDialog(
                Component.translatable("pathmind.dialog.exportWorkspace").getString(),
                defaultPath,
                filters,
                filters != null ? Component.translatable("pathmind.dialog.jsonFiles").getString() : null
            );
        }
    }

    private PointerBuffer createJsonFilterPatterns(MemoryStack stack) {
        PointerBuffer filters = stack.mallocPointer(2);
        filters.put(stack.UTF8("*.json"));
        filters.put(stack.UTF8("*.JSON"));
        filters.flip();
        return filters;
    }

    void updateImportExportPathFromPreset() {
        lastImportExportPath = NodeGraphPersistence.getDefaultSavePath();
    }

    void clearImportExportStatus() {
        importExportStatus = "";
        importExportStatusColor = UITheme.TEXT_SECONDARY;
    }

    private void setImportExportStatus(String message, int color) {
        importExportStatus = message != null ? message : "";
        importExportStatusColor = color;
    }

    private static final class ImportOperationResult {
        private final boolean success;
        private final String fileLabel;
        private final String presetName;
        private final NodeGraphData importedData;

        private ImportOperationResult(
            boolean success, String fileLabel, String presetName, NodeGraphData importedData
        ) {
            this.success = success;
            this.fileLabel = fileLabel;
            this.presetName = presetName;
            this.importedData = importedData;
        }

        private static ImportOperationResult succeeded(
            String fileLabel, String presetName, NodeGraphData importedData
        ) {
            return new ImportOperationResult(true, fileLabel, presetName, importedData);
        }

        private static ImportOperationResult failed(String fileLabel) {
            return new ImportOperationResult(false, fileLabel, null, null);
        }
    }
}
