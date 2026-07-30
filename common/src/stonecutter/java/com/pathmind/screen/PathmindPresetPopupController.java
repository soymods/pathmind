package com.pathmind.screen;

// Canonical UI shared by the default and 26.x Stonecutter targets.

import static com.pathmind.screen.PathmindVisualEditorScreen.*;

import com.pathmind.data.PresetManager;
import com.pathmind.data.NodeGraphData;
import com.pathmind.marketplace.MarketplaceAuthManager;
import com.pathmind.marketplace.MarketplacePreset;
import com.pathmind.marketplace.MarketplaceService;
import com.pathmind.ui.animation.PopupAnimationHandler;
import com.pathmind.ui.control.PathmindTextField;
import com.pathmind.ui.control.PathmindPopupLayout;
import com.pathmind.ui.control.PathmindPopupRenderer;
import com.pathmind.ui.control.ToggleSwitch;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.RenderStateBridge;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
//? if MC_1_21_8 {
/*// Legacy screen input callbacks use primitive parameters.*/
//?} else {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class PathmindPresetPopupController {
    interface Host {
        PathmindVisualEditorScreen editorScreen();
        Minecraft client();
        net.minecraft.client.gui.Font textRenderer();
        void addWidget(EditBox field);
        String activePresetName();
        void closePresetDropdown();
        void closeInfoPopup();
        void closeSettingsPopup();
        void stopInlinePresetRename(boolean save);
        void saveRootPresetWorkspace();
        int getAccentColor();
        String getCurrentMinecraftVersion();
        String getModVersion();
        String fallback(String value, String fallback);
        boolean isPointInRect(int pointX, int pointY, int x, int y, int width, int height);
        int[] getBoundedScaledPopupBounds(PopupAnimationHandler animation, int width, int height);
        int getBoundedPopupContentY(int popupY, PopupAnimationHandler animation, int height);
        void resetBoundedPopupScroll(PopupAnimationHandler animation);
        boolean handleBoundedPopupScroll(double mouseX, double mouseY, double verticalAmount,
                                         PopupAnimationHandler animation, int width, int height);
        int getPopupAnimatedColor(PopupAnimationHandler animation, int color);
        void setOverlayCutout(int x, int y, int width, int height);
        void drawPopupTextWithEllipsis(GuiGraphics context, String text, int x, int y,
                                       int maxWidth, int color);
        boolean renameOpenLibraryRoutine(String routineId, String routineName);
        boolean isDuplicateRoutineName(String routineName, String ignoredRoutineId);
        void renameRoutine(String routineId, String routineName);
        void createRoutineFromSidebar(String routineName);
        void switchPreset(String presetName);
        boolean renamePreset(String oldName, String newName);
        void deletePreset(String presetName);
        boolean skipPresetDeleteConfirm();
        void setSkipPresetDeleteConfirm(boolean skip);
    }

    private final Host host;
    private final PopupAnimationHandler createPresetPopupAnimation = new PopupAnimationHandler();
    private EditBox createPresetField;
    private String createPresetStatus = "";
    private int createPresetStatusColor = UITheme.TEXT_SECONDARY;
    private boolean createRoutineNaming;
    private String pendingRoutineRenameId = "";
    private String pendingLibraryRoutineRenameId = "";
    private final PopupAnimationHandler publishPresetPopupAnimation = new PopupAnimationHandler();
    private EditBox publishPresetNameField;
    private EditBox publishPresetDescriptionField;
    private EditBox publishPresetTagsField;
    private String publishPresetStatus = "";
    private int publishPresetStatusColor = UITheme.TEXT_SECONDARY;
    private boolean publishPresetBusy;
    private MarketplaceAuthManager.AuthSession publishPresetSession;
    private MarketplacePreset publishPresetEditingPreset;
    private boolean publishPresetPublic = true;
    private final ToggleSwitch publishPresetVisibilityToggle = new ToggleSwitch(true);
    private final PopupAnimationHandler renamePresetPopupAnimation = new PopupAnimationHandler();
    private EditBox renamePresetField;
    private String renamePresetStatus = "";
    private int renamePresetStatusColor = UITheme.TEXT_SECONDARY;
    private String pendingPresetRenameName = "";
    private final PopupAnimationHandler presetDeletePopupAnimation = new PopupAnimationHandler();
    private String pendingPresetDeletionName = "";

    PathmindPresetPopupController(Host host) {
        this.host = host;
    }

    PopupAnimationHandler createAnimation() {
        return createPresetPopupAnimation;
    }

    PopupAnimationHandler publishAnimation() {
        return publishPresetPopupAnimation;
    }

    PopupAnimationHandler renameAnimation() {
        return renamePresetPopupAnimation;
    }

    PopupAnimationHandler deleteAnimation() {
        return presetDeletePopupAnimation;
    }

    void initializeFields() {
        if (createPresetField == null) {
            createPresetField = PathmindTextField.createInactive(host.textRenderer(), 0, 0, 200, 20, Component.translatable("pathmind.field.presetName"), 64);
            createPresetField.setResponder(value -> clearCreatePresetStatus());
            host.addWidget(createPresetField);
        }
        if (publishPresetNameField == null) {
            publishPresetNameField = PathmindTextField.createInactive(host.textRenderer(), 0, 0, 240, 20, Component.translatable("pathmind.field.presetName"), 64);
            publishPresetNameField.setResponder(value -> clearPublishPresetStatus());
            host.addWidget(publishPresetNameField);
        }
        if (publishPresetDescriptionField == null) {
            publishPresetDescriptionField = PathmindTextField.createInactive(host.textRenderer(), 0, 0, 240, 20, Component.translatable("pathmind.field.description"), 180);
            publishPresetDescriptionField.setResponder(value -> clearPublishPresetStatus());
            host.addWidget(publishPresetDescriptionField);
        }
        if (publishPresetTagsField == null) {
            publishPresetTagsField = PathmindTextField.createInactive(host.textRenderer(), 0, 0, 240, 20, Component.translatable("pathmind.field.tags"), 96);
            publishPresetTagsField.setResponder(value -> clearPublishPresetStatus());
            host.addWidget(publishPresetTagsField);
        }
        if (renamePresetField == null) {
            renamePresetField = PathmindTextField.createInactive(host.textRenderer(), 0, 0, 200, 20, Component.translatable("pathmind.field.newPresetName"), 64);
            renamePresetField.setResponder(value -> clearRenamePresetStatus());
            host.addWidget(renamePresetField);
        }
    }

    void tick() {
        createPresetPopupAnimation.tick();
        publishPresetPopupAnimation.tick();
        renamePresetPopupAnimation.tick();
        presetDeletePopupAnimation.tick();
    }

    boolean createVisible() {
        return createPresetPopupAnimation.isVisible();
    }

    boolean publishVisible() {
        return publishPresetPopupAnimation.isVisible();
    }

    boolean renameVisible() {
        return renamePresetPopupAnimation.isVisible();
    }

    boolean deleteVisible() {
        return presetDeletePopupAnimation.isVisible();
    }

    boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (createPresetPopupAnimation.isVisible()) {
            return host.handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, createPresetPopupAnimation,
                CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        }
        if (publishPresetPopupAnimation.isVisible()) {
            return host.handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, publishPresetPopupAnimation,
                PUBLISH_PRESET_POPUP_WIDTH, PUBLISH_PRESET_POPUP_HEIGHT);
        }
        if (renamePresetPopupAnimation.isVisible()) {
            return host.handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, renamePresetPopupAnimation,
                CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        }
        if (presetDeletePopupAnimation.isVisible()) {
            return host.handleBoundedPopupScroll(mouseX, mouseY, verticalAmount, presetDeletePopupAnimation,
                PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        }
        return false;
    }

    void openCreatePresetPopup() {
        createRoutineNaming = false;
        host.closePresetDropdown();
        clearCreatePresetStatus();
        host.closeInfoPopup();
        host.stopInlinePresetRename(false);
        closeRenamePresetPopup();
        closePublishPresetPopup();
        host.resetBoundedPopupScroll(createPresetPopupAnimation);
        createPresetPopupAnimation.show();
        if (createPresetField != null) {
            createPresetField.setValue("");
            createPresetField.setVisible(true);
            createPresetField.setEditable(true);
            createPresetField.setFocused(true);
        }
    }

    void openCreateRoutinePopup() {
        openCreatePresetPopup();
        createRoutineNaming = true;
        pendingRoutineRenameId = "";
        pendingLibraryRoutineRenameId = "";
    }

    int getCreateNamingPopupHeight() {
        return createRoutineNaming ? 148 : CREATE_PRESET_POPUP_HEIGHT;
    }

    void openRenameRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null) return;
        openCreatePresetPopup();
        createRoutineNaming = true;
        pendingRoutineRenameId = routine.getId();
        pendingLibraryRoutineRenameId = "";
        if (createPresetField != null) {
            createPresetField.setValue(routine.getName() == null ? "" : routine.getName());
            createPresetField.setFocused(true);
        }
    }

    void openRenameLibraryRoutinePopup(NodeGraphData.RoutineDefinitionData routine) {
        if (routine == null) return;
        openCreatePresetPopup();
        createRoutineNaming = true;
        pendingRoutineRenameId = "";
        pendingLibraryRoutineRenameId = routine.getId();
        if (createPresetField != null) {
            createPresetField.setValue(routine.getName() == null ? "" : routine.getName());
            createPresetField.setFocused(true);
        }
    }

    void closeCreatePresetPopup() {
        createRoutineNaming = false;
        pendingRoutineRenameId = "";
        pendingLibraryRoutineRenameId = "";
        host.resetBoundedPopupScroll(createPresetPopupAnimation);
        createPresetPopupAnimation.hide();
        clearCreatePresetStatus();
        if (createPresetField != null) {
            PathmindTextField.deactivate(createPresetField);
        }
    }

    void openRenamePresetPopup(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        host.closePresetDropdown();
        clearRenamePresetStatus();
        host.closeInfoPopup();
        host.stopInlinePresetRename(false);
        closeCreatePresetPopup();
        pendingPresetRenameName = presetName;
        host.resetBoundedPopupScroll(renamePresetPopupAnimation);
        renamePresetPopupAnimation.show();
        if (renamePresetField != null) {
            renamePresetField.setValue(presetName);
            renamePresetField.setVisible(true);
            renamePresetField.setEditable(true);
            renamePresetField.setFocused(true);
        }
    }

    void closeRenamePresetPopup() {
        host.resetBoundedPopupScroll(renamePresetPopupAnimation);
        renamePresetPopupAnimation.hide();
        pendingPresetRenameName = "";
        clearRenamePresetStatus();
        if (renamePresetField != null) {
            PathmindTextField.deactivate(renamePresetField);
        }
    }

    void attemptCreatePreset() {
        if (createPresetField == null) {
            return;
        }

        String desiredName = createPresetField.getValue();
        if (createRoutineNaming) {
            String routineName = desiredName == null ? "" : desiredName.trim();
            if (routineName.isEmpty()) {
                setCreatePresetStatus(Component.translatable("pathmind.status.enterRoutineName").getString(), UITheme.STATE_ERROR);
                return;
            }
            if (!pendingLibraryRoutineRenameId.isBlank()) {
                if (!host.renameOpenLibraryRoutine(pendingLibraryRoutineRenameId, routineName)) {
                    setCreatePresetStatus(Component.translatable("pathmind.status.routineNameExists").getString(), UITheme.STATE_ERROR);
                    return;
                }
                closeCreatePresetPopup();
                return;
            }
            boolean duplicate = host.isDuplicateRoutineName(routineName, pendingRoutineRenameId);
            if (duplicate) {
                setCreatePresetStatus(Component.translatable("pathmind.status.routineNameExists").getString(), UITheme.STATE_ERROR);
                return;
            }
            if (!pendingRoutineRenameId.isBlank()) {
                host.renameRoutine(pendingRoutineRenameId, routineName);
            } else {
                host.createRoutineFromSidebar(routineName);
            }
            closeCreatePresetPopup();
            return;
        }
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setCreatePresetStatus(Component.translatable("pathmind.status.enterPresetName").getString(), UITheme.STATE_ERROR);
            return;
        }

        Optional<String> createdPreset = PresetManager.createPreset(desiredName);
        if (createdPreset.isEmpty()) {
            setCreatePresetStatus(Component.translatable("pathmind.status.presetNameExistsOrInvalid").getString(), UITheme.STATE_ERROR);
            return;
        }

        host.switchPreset(createdPreset.get());
        closeCreatePresetPopup();
    }

    void attemptRenamePreset() {
        if (renamePresetField == null) {
            return;
        }
        if (pendingPresetRenameName == null || pendingPresetRenameName.trim().isEmpty()) {
            setRenamePresetStatus(Component.translatable("pathmind.status.selectPresetToRename").getString(), UITheme.STATE_ERROR);
            return;
        }
        String desiredName = renamePresetField.getValue();
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setRenamePresetStatus(Component.translatable("pathmind.status.enterPresetName").getString(), UITheme.STATE_ERROR);
            return;
        }
        if (!host.renamePreset(pendingPresetRenameName, desiredName)) {
            setRenamePresetStatus(Component.translatable("pathmind.status.presetNameExistsOrInvalid").getString(), UITheme.STATE_ERROR);
            return;
        }
        closeRenamePresetPopup();
    }

    void openPresetDeletePopup(String presetName) {
        if (presetName == null || presetName.isEmpty()) {
            return;
        }
        if (host.skipPresetDeleteConfirm()) {
            host.deletePreset(presetName);
            return;
        }
        pendingPresetDeletionName = presetName;
        host.resetBoundedPopupScroll(presetDeletePopupAnimation);
        presetDeletePopupAnimation.show();
        host.closePresetDropdown();
    }

    void closePresetDeletePopup() {
        host.resetBoundedPopupScroll(presetDeletePopupAnimation);
        presetDeletePopupAnimation.hide();
        pendingPresetDeletionName = "";
    }

    void confirmPresetDeletion() {
        String presetName = pendingPresetDeletionName;
        closePresetDeletePopup();
        if (presetName != null && !presetName.isEmpty()) {
            host.deletePreset(presetName);
        }
    }

    void setSkipPresetDeleteConfirm(boolean skip) {
        host.setSkipPresetDeleteConfirm(skip);
    }

    boolean isSkipPresetDeleteConfirm() {
        return host.skipPresetDeleteConfirm();
    }

    private void setCreatePresetStatus(String message, int color) {
        createPresetStatus = message != null ? message : "";
        createPresetStatusColor = color;
    }

    private void clearCreatePresetStatus() {
        createPresetStatus = "";
        createPresetStatusColor = UITheme.TEXT_SECONDARY;
    }

    private void setPublishPresetStatus(String message, int color) {
        publishPresetStatus = message != null ? message : "";
        publishPresetStatusColor = color;
    }

    private void clearPublishPresetStatus() {
        publishPresetStatus = "";
        publishPresetStatusColor = UITheme.TEXT_SECONDARY;
    }

    private void setRenamePresetStatus(String message, int color) {
        renamePresetStatus = message != null ? message : "";
        renamePresetStatusColor = color;
    }

    private void clearRenamePresetStatus() {
        renamePresetStatus = "";
        renamePresetStatusColor = UITheme.TEXT_SECONDARY;
    }

    //? if MC_1_21_8 {
    /*boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        *///?} else {
    boolean handleMouseClicked(MouseButtonEvent click, boolean inBounds) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //?}
        if (createPresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (createPresetField != null && createPresetField.mouseClicked(mouseX, mouseY, button)) {
                *///?} else {
            if (createPresetField != null && createPresetField.mouseClicked(click, inBounds)) {
                //?}
                return true;
            }
            handleCreatePresetPopupClick(mouseX, mouseY, button);
            return true;
        }

        if (publishPresetPopupAnimation.isVisible()) {
            handlePublishPresetPopupClick(mouseX, mouseY, button);
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (renamePresetField != null && renamePresetField.mouseClicked(mouseX, mouseY, button)) {
                *///?} else {
            if (renamePresetField != null && renamePresetField.mouseClicked(click, inBounds)) {
                //?}
                return true;
            }
            handleRenamePresetPopupClick(mouseX, mouseY, button);
            return true;
        }

        return false;
    }

    //? if MC_1_21_8 {
    /*boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        *///?} else {
    boolean handleMouseReleased(MouseButtonEvent click) {
        //?}
        if (createPresetPopupAnimation.isVisible()) {
            if (createPresetField != null) {
                //? if MC_1_21_8 {
                /*createPresetField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                createPresetField.mouseReleased(click);
                //?}
            }
            return true;
        }

        if (publishPresetPopupAnimation.isVisible()) {
            if (publishPresetNameField != null) {
                //? if MC_1_21_8 {
                /*publishPresetNameField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                publishPresetNameField.mouseReleased(click);
                //?}
            }
            if (publishPresetDescriptionField != null) {
                //? if MC_1_21_8 {
                /*publishPresetDescriptionField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                publishPresetDescriptionField.mouseReleased(click);
                //?}
            }
            if (publishPresetTagsField != null) {
                //? if MC_1_21_8 {
                /*publishPresetTagsField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                publishPresetTagsField.mouseReleased(click);
                //?}
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            if (renamePresetField != null) {
                //? if MC_1_21_8 {
                /*renamePresetField.mouseReleased(mouseX, mouseY, button);*/
                //?} else {
                renamePresetField.mouseReleased(click);
                //?}
            }
            return true;
        }
        return false;
    }

    //? if MC_1_21_8 {
    /*boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        *///?} else {
    boolean handleKeyPressed(KeyEvent input) {
        int keyCode = input.key();
        //?}
        if (createPresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (createPresetField != null && createPresetField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (createPresetField != null && createPresetField.keyPressed(input)) {
                //?}
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeCreatePresetPopup();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptCreatePreset();
                return true;
            }
            return true;
        }

        if (publishPresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (publishPresetNameField != null && publishPresetNameField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (publishPresetNameField != null && publishPresetNameField.keyPressed(input)) {
                //?}
                return true;
            }
            //? if MC_1_21_8 {
            /*if (publishPresetDescriptionField != null && publishPresetDescriptionField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (publishPresetDescriptionField != null && publishPresetDescriptionField.keyPressed(input)) {
                //?}
                return true;
            }
            //? if MC_1_21_8 {
            /*if (publishPresetTagsField != null && publishPresetTagsField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (publishPresetTagsField != null && publishPresetTagsField.keyPressed(input)) {
                //?}
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closePublishPresetPopup();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptPublishPreset();
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (renamePresetField != null && renamePresetField.keyPressed(keyCode, scanCode, modifiers)) {
                *///?} else {
            if (renamePresetField != null && renamePresetField.keyPressed(input)) {
                //?}
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeRenamePresetPopup();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                attemptRenamePreset();
                return true;
            }
            return true;
        }

        return false;
    }

    //? if MC_1_21_8 {
    /*boolean handleCharTyped(char chr, int modifiers) {
        *///?} else {
    boolean handleCharTyped(CharacterEvent input) {
        //?}
        if (createPresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (createPresetField != null && createPresetField.charTyped(chr, modifiers)) {
                *///?} else {
            if (createPresetField != null && createPresetField.charTyped(input)) {
                //?}
                return true;
            }
            return true;
        }

        if (publishPresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (publishPresetNameField != null && publishPresetNameField.charTyped(chr, modifiers)) {
                *///?} else {
            if (publishPresetNameField != null && publishPresetNameField.charTyped(input)) {
                //?}
                return true;
            }
            //? if MC_1_21_8 {
            /*if (publishPresetDescriptionField != null && publishPresetDescriptionField.charTyped(chr, modifiers)) {
                *///?} else {
            if (publishPresetDescriptionField != null && publishPresetDescriptionField.charTyped(input)) {
                //?}
                return true;
            }
            //? if MC_1_21_8 {
            /*if (publishPresetTagsField != null && publishPresetTagsField.charTyped(chr, modifiers)) {
                *///?} else {
            if (publishPresetTagsField != null && publishPresetTagsField.charTyped(input)) {
                //?}
                return true;
            }
            return true;
        }

        if (renamePresetPopupAnimation.isVisible()) {
            //? if MC_1_21_8 {
            /*if (renamePresetField != null && renamePresetField.charTyped(chr, modifiers)) {
                *///?} else {
            if (renamePresetField != null && renamePresetField.charTyped(input)) {
                //?}
                return true;
            }
            return true;
        }
        return false;
    }

    void openPublishPresetPopup() {
        host.closePresetDropdown();
        clearPublishPresetStatus();
        host.closeInfoPopup();
        host.stopInlinePresetRename(false);
        closeCreatePresetPopup();
        closeRenamePresetPopup();
        host.closeSettingsPopup();
        host.saveRootPresetWorkspace();
        PresetManager.setActivePreset(host.activePresetName());
        publishPresetSession = MarketplaceAuthManager.getCachedSession().orElse(null);
        Optional<String> linkedPresetId = PresetManager.getMarketplaceLinkedPresetId(host.activePresetName());
        if (publishPresetSession != null && linkedPresetId.isPresent()) {
            publishPresetBusy = true;
            setPublishPresetStatus(Component.translatable("pathmind.status.openingPublishedPreset").getString(), UITheme.TEXT_SECONDARY);
            PathmindMarketplaceFlowController.resolveLinkedPreset(host.client(), publishPresetSession, linkedPresetId, result -> {
                publishPresetBusy = false;
                if (result.status() == PathmindMarketplaceFlowController.LinkedPresetStatus.FOUND) {
                    publishPresetSession = result.session();
                    clearPublishPresetStatus();
                    Minecraft client = host.client();
                    if (client != null) {
                        client.setScreen(new PathmindMarketplaceScreen(host.editorScreen(), false, null, result.preset()));
                    }
                    return;
                }
                if (result.status() == PathmindMarketplaceFlowController.LinkedPresetStatus.SESSION_EXPIRED) {
                    publishPresetSession = null;
                    setPublishPresetStatus(Component.translatable("pathmind.status.sessionExpiredSignInAgain").getString(), UITheme.STATE_WARNING);
                } else {
                    publishPresetSession = result.session();
                    setPublishPresetStatus(Component.translatable("pathmind.marketplace.linkedPresetNotFound").getString(), UITheme.STATE_WARNING);
                }
                openRawPublishPresetPopup();
            });
            return;
        }
        openRawPublishPresetPopup();
    }

    private void openRawPublishPresetPopup() {
        publishPresetEditingPreset = null;
        host.resetBoundedPopupScroll(publishPresetPopupAnimation);
        publishPresetPopupAnimation.show();
        if (publishPresetNameField != null) {
            publishPresetNameField.setValue(host.activePresetName());
            publishPresetNameField.setVisible(true);
            publishPresetNameField.setEditable(true);
            publishPresetNameField.setFocused(true);
        }
        if (publishPresetDescriptionField != null) {
            publishPresetDescriptionField.setValue("");
            publishPresetDescriptionField.setVisible(true);
            publishPresetDescriptionField.setEditable(true);
            publishPresetDescriptionField.setFocused(false);
        }
        if (publishPresetTagsField != null) {
            publishPresetTagsField.setValue("");
            publishPresetTagsField.setVisible(true);
            publishPresetTagsField.setEditable(true);
            publishPresetTagsField.setFocused(false);
        }
        publishPresetPublic = true;
    }

    void reopenPublishPresetPopup(String presetName) {
        host.saveRootPresetWorkspace();
        PresetManager.setActivePreset(host.activePresetName());
        clearPublishPresetStatus();
        publishPresetSession = MarketplaceAuthManager.getCachedSession().orElse(null);
        publishPresetEditingPreset = null;
        host.resetBoundedPopupScroll(publishPresetPopupAnimation);
        publishPresetPopupAnimation.show();
        if (publishPresetNameField != null) {
            publishPresetNameField.setValue(host.fallback(presetName, host.activePresetName()));
            publishPresetNameField.setVisible(true);
            publishPresetNameField.setEditable(true);
            publishPresetNameField.setFocused(true);
        }
        if (publishPresetDescriptionField != null) {
            publishPresetDescriptionField.setValue("");
            publishPresetDescriptionField.setVisible(true);
            publishPresetDescriptionField.setEditable(true);
            publishPresetDescriptionField.setFocused(false);
        }
        if (publishPresetTagsField != null) {
            publishPresetTagsField.setValue("");
            publishPresetTagsField.setVisible(true);
            publishPresetTagsField.setEditable(true);
            publishPresetTagsField.setFocused(false);
        }
        publishPresetPublic = true;
    }

    void closePublishPresetPopup() {
        host.resetBoundedPopupScroll(publishPresetPopupAnimation);
        publishPresetPopupAnimation.hide();
        publishPresetBusy = false;
        publishPresetEditingPreset = null;
        clearPublishPresetStatus();
        if (publishPresetNameField != null) {
            PathmindTextField.deactivate(publishPresetNameField);
        }
        if (publishPresetDescriptionField != null) {
            PathmindTextField.deactivate(publishPresetDescriptionField);
        }
        if (publishPresetTagsField != null) {
            PathmindTextField.deactivate(publishPresetTagsField);
        }
    }

    void attemptPublishPreset() {
        if (publishPresetBusy) {
            return;
        }
        if (publishPresetNameField == null) {
            return;
        }

        String desiredName = publishPresetNameField.getValue();
        if (desiredName == null || desiredName.trim().isEmpty()) {
            setPublishPresetStatus(Component.translatable("pathmind.status.enterPresetName").getString(), UITheme.STATE_ERROR);
            return;
        }

        if (publishPresetSession == null) {
            setPublishPresetStatus(Component.translatable("pathmind.status.signInBeforePublishing").getString(), UITheme.STATE_WARNING);
            return;
        }

        host.saveRootPresetWorkspace();
        PresetManager.setActivePreset(host.activePresetName());
        Path presetPath = PresetManager.getPresetPath(host.activePresetName());
        if (presetPath == null || !Files.exists(presetPath)) {
            setPublishPresetStatus(Component.translatable("pathmind.status.currentPresetFileMissing").getString(), UITheme.STATE_ERROR);
            return;
        }

        publishPresetBusy = true;
        setPublishPresetStatus(Component.translatable("pathmind.status.publishingPreset").getString(), UITheme.TEXT_SECONDARY);
        MarketplaceService.PublishRequest request = PathmindMarketplaceActions.publishRequest(
            presetPath,
            null,
            desiredName.trim(),
            host.fallback(publishPresetSession.getDisplayName(), host.fallback(publishPresetSession.getEmail(), Component.translatable("pathmind.status.discordUser").getString())),
            publishPresetDescriptionField == null ? "" : publishPresetDescriptionField.getValue().trim(),
            publishPresetTagsField == null ? "" : publishPresetTagsField.getValue(),
            host.getCurrentMinecraftVersion(),
            host.getModVersion(),
            publishPresetPublic
        );
        PathmindMarketplaceFlowController.submitPublish(host.client(), null, request, result -> {
            if (result.status() == PathmindMarketplaceFlowController.PublishStatus.SESSION_EXPIRED) {
                publishPresetBusy = false;
                publishPresetSession = null;
                setPublishPresetStatus(Component.translatable("pathmind.status.sessionExpiredSignInAgain").getString(), UITheme.STATE_WARNING);
                return;
            }
            publishPresetSession = result.session();
            if (result.status() == PathmindMarketplaceFlowController.PublishStatus.RATE_LIMITED) {
                publishPresetBusy = false;
                setPublishPresetStatus(result.limitMessage(), UITheme.STATE_WARNING);
                return;
            }
            finishPublishPreset(result.preset(), result.throwable());
        });
    }

    void startPublishPresetSignIn() {
        if (publishPresetBusy) {
            return;
        }
        publishPresetBusy = true;
        setPublishPresetStatus(Component.translatable("pathmind.status.openingDiscordSignIn").getString(), UITheme.TEXT_SECONDARY);
        PathmindMarketplaceAsyncController.startDiscordSignIn(host.client(), (session, throwable) -> {
            publishPresetBusy = false;
            if (throwable != null || session == null) {
                publishPresetSession = null;
                setPublishPresetStatus(host.fallback(throwable == null ? null : throwable.getMessage(), Component.translatable("pathmind.status.discordSignInFailed").getString()), UITheme.STATE_ERROR);
                return;
            }
            publishPresetSession = session;
            setPublishPresetStatus(Component.translatable("pathmind.status.signedInAs", host.fallback(session.getDisplayName(), host.fallback(session.getEmail(), Component.translatable("pathmind.status.discordUser").getString()))).getString(), host.getAccentColor());
        });
    }

    private void finishPublishPreset(MarketplacePreset preset, Throwable throwable) {
        publishPresetBusy = false;
        if (throwable != null) {
            setPublishPresetStatus(buildPublishFailureMessage(throwable), UITheme.STATE_ERROR);
            return;
        }
        closePublishPresetPopup();
        if (preset != null) {
            PresetManager.setMarketplaceLinkedPreset(host.activePresetName(), preset.getId());
        }
        if (host.client() != null && host.client().player != null) {
            host.client().player.displayClientMessage(Component.translatable("pathmind.status.presetPublished"), true);
        }
        Minecraft client = host.client();
        if (client != null && preset != null) {
            client.setScreen(new PathmindMarketplaceScreen(host.editorScreen(), false, null, preset));
        }
    }

    private String buildPublishFailureMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            return Component.translatable("pathmind.status.publishFailed").getString();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 120) {
            normalized = normalized.substring(0, 117) + "...";
        }
        return normalized;
    }

    boolean handleCreatePresetPopupClick(double mouseX, double mouseY, int button) {
        int popupHeight = getCreateNamingPopupHeight();
        if (button != 0) {
            return false;
        }

        int[] bounds = host.getBoundedScaledPopupBounds(createPresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int contentY = host.getBoundedPopupContentY(popupY, createPresetPopupAnimation, popupHeight);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, popupWidth, contentY, popupHeight, 90, 20, 16);

        if (buttonRow.left().contains((int) mouseX, (int) mouseY)) {
            closeCreatePresetPopup();
            return true;
        }

        if (buttonRow.right().contains((int) mouseX, (int) mouseY)) {
            attemptCreatePreset();
            return true;
        }

        return false;
    }

    boolean handlePublishPresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int[] bounds = host.getBoundedScaledPopupBounds(publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_WIDTH, PUBLISH_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int popupHeight = bounds[3];
        int contentY = host.getBoundedPopupContentY(popupY, publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_HEIGHT);
        PathmindPopupLayout.PublishPresetLayout layout = PathmindPopupLayout.publishPreset(
            popupX,
            popupY,
            popupWidth,
            popupHeight,
            contentY,
            PUBLISH_PRESET_POPUP_HEIGHT,
            96,
            20,
            publishPresetVisibilityToggle.getWidth(),
            publishPresetVisibilityToggle.getHeight()
        );

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        if (!host.isPointInRect(mouseXi, mouseYi, popupX, popupY, popupWidth, popupHeight)) {
            closePublishPresetPopup();
            return true;
        }
        if (layout.cancelButton().contains(mouseXi, mouseYi)) {
            closePublishPresetPopup();
            return true;
        }
        if (publishPresetSession == null && layout.signInButton().contains(mouseXi, mouseYi)) {
            startPublishPresetSignIn();
            return true;
        }
        if (layout.publishButton().contains(mouseXi, mouseYi)) {
            attemptPublishPreset();
            return true;
        }
        if (layout.nameField().contains(mouseXi, mouseYi)) {
            focusPublishPresetField(publishPresetNameField);
            return true;
        }
        if (layout.descriptionField().contains(mouseXi, mouseYi)) {
            focusPublishPresetField(publishPresetDescriptionField);
            return true;
        }
        if (layout.tagsField().contains(mouseXi, mouseYi)) {
            focusPublishPresetField(publishPresetTagsField);
            return true;
        }
        if (layout.visibilityToggle().contains(mouseXi, mouseYi)) {
            publishPresetVisibilityToggle.mouseClicked(mouseXi, mouseYi);
            publishPresetPublic = publishPresetVisibilityToggle.getValue();
            return true;
        }
        focusPublishPresetField(null);
        return true;
    }

    boolean handleRenamePresetPopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        int[] bounds = host.getBoundedScaledPopupBounds(renamePresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int contentY = host.getBoundedPopupContentY(popupY, renamePresetPopupAnimation, CREATE_PRESET_POPUP_HEIGHT);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, popupWidth, contentY, CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);

        if (buttonRow.left().contains((int) mouseX, (int) mouseY)) {
            closeRenamePresetPopup();
            return true;
        }

        if (buttonRow.right().contains((int) mouseX, (int) mouseY)) {
            attemptRenamePreset();
            return true;
        }

        return false;
    }

    boolean handlePresetDeletePopupClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return true;
        }

        int[] bounds = host.getBoundedScaledPopupBounds(presetDeletePopupAnimation, PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int popupWidth = bounds[2];
        int contentY = host.getBoundedPopupContentY(popupY, presetDeletePopupAnimation, PRESET_DELETE_POPUP_HEIGHT);
        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, popupWidth, contentY, PRESET_DELETE_POPUP_HEIGHT, 90, 20, 16);

        int mouseXi = (int) mouseX;
        int mouseYi = (int) mouseY;
        int checkboxX = popupX + 20;
        int checkboxY = contentY + 86;
        int checkboxHitboxSize = PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4;

        if (buttonRow.right().contains(mouseXi, mouseYi)) {
            confirmPresetDeletion();
            return true;
        }

        if (buttonRow.left().contains(mouseXi, mouseYi)) {
            closePresetDeletePopup();
            return true;
        }

        if (host.isPointInRect(mouseXi, mouseYi, checkboxX - 2, checkboxY - 2, checkboxHitboxSize, checkboxHitboxSize)) {
            setSkipPresetDeleteConfirm(!isSkipPresetDeleteConfirm());
            return true;
        }

        return true;
    }

    void renderCreatePresetPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int popupHeight = getCreateNamingPopupHeight();
        RenderStateBridge.setShaderColor(1f, 1f, 1f, createPresetPopupAnimation.getPopupAlpha());

        int[] bounds = host.getBoundedScaledPopupBounds(createPresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, popupHeight);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.getBoundedPopupContentY(popupY, createPresetPopupAnimation, popupHeight);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, createPresetPopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            host.textRenderer(),
            Component.translatable(createRoutineNaming ? (pendingRoutineRenameId.isBlank() ? "pathmind.popup.createRoutine.title" : "pathmind.popup.renameRoutine.title") : "pathmind.popup.createPreset.title"),
            popupX,
            contentY,
            scaledWidth,
            createPresetPopupAnimation
        );

        host.drawPopupTextWithEllipsis(
            context,
            Component.translatable(createRoutineNaming ? (pendingRoutineRenameId.isBlank() ? "pathmind.popup.createRoutine.message" : "pathmind.popup.renameRoutine.message") : "pathmind.popup.createPreset.message").getString(),
            popupX + 20,
            contentY + 44,
            scaledWidth - 40,
            host.getPopupAnimatedColor(createPresetPopupAnimation, UITheme.TEXT_SECONDARY)
        );

        int fieldX = popupX + 20;
        int fieldY = contentY + 70;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;
        renderPresetTextField(context, mouseX, mouseY, delta, createPresetField, fieldX, fieldY, fieldWidth, fieldHeight, createPresetPopupAnimation);

        if (!createPresetStatus.isEmpty()) {
            host.drawPopupTextWithEllipsis(
                context,
                createPresetStatus,
                fieldX,
                fieldY + fieldHeight + 8,
                fieldWidth,
                host.getPopupAnimatedColor(createPresetPopupAnimation, createPresetStatusColor)
            );
        }

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, contentY, popupHeight, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Component.translatable("pathmind.button.cancel"),
            Component.translatable(createRoutineNaming && !pendingRoutineRenameId.isBlank() ? "pathmind.button.rename" : "pathmind.button.create"),
            createPresetPopupAnimation);
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderPublishPresetPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, publishPresetPopupAnimation.getPopupAlpha());
        syncPublishPresetVisibilityToggleColors();

        int[] bounds = host.getBoundedScaledPopupBounds(publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_WIDTH, PUBLISH_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.getBoundedPopupContentY(popupY, publishPresetPopupAnimation, PUBLISH_PRESET_POPUP_HEIGHT);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, publishPresetPopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            host.textRenderer(),
            publishPresetEditingPreset == null ? Component.translatable("pathmind.marketplace.publishPreset") : Component.translatable("pathmind.marketplace.updateUploadedPreset"),
            popupX,
            contentY,
            scaledWidth,
            publishPresetPopupAnimation
        );

        PathmindPopupLayout.PublishPresetLayout layout = PathmindPopupLayout.publishPreset(
            popupX,
            popupY,
            scaledWidth,
            scaledHeight,
            contentY,
            PUBLISH_PRESET_POPUP_HEIGHT,
            96,
            20,
            publishPresetVisibilityToggle.getWidth(),
            publishPresetVisibilityToggle.getHeight()
        );
        int fieldX = layout.fieldX();
        int fieldWidth = layout.fieldWidth();
        int fieldHeight = layout.nameField().height();

        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.name").getString(), fieldX, layout.nameField().y() - 10, fieldWidth,
            host.getPopupAnimatedColor(publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.description").getString(), fieldX, layout.descriptionField().y() - 10, fieldWidth,
            host.getPopupAnimatedColor(publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.tags").getString(), fieldX, layout.tagsField().y() - 10, fieldWidth,
            host.getPopupAnimatedColor(publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));

        renderPublishPresetField(context, mouseX, mouseY, delta, publishPresetNameField, layout.nameField());
        renderPublishPresetField(context, mouseX, mouseY, delta, publishPresetDescriptionField, layout.descriptionField());
        renderPublishPresetField(context, mouseX, mouseY, delta, publishPresetTagsField, layout.tagsField());

        int visibilityY = layout.visibilityRow().y();
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.field.visibility").getString(), fieldX, visibilityY - 10, fieldWidth,
            host.getPopupAnimatedColor(publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        renderPublishVisibilityToggle(context, mouseX, mouseY, layout.visibilityRow(), layout.visibilityToggle());

        String accountLabel = publishPresetBusy ? Component.translatable("pathmind.status.working").getString() : publishPresetSession == null
            ? Component.translatable("pathmind.marketplace.signIn").getString()
            : TextRenderUtil.trimWithEllipsis(host.textRenderer(),
                host.fallback(publishPresetSession.getDisplayName(), host.fallback(publishPresetSession.getEmail(), Component.translatable("pathmind.marketplace.signedIn").getString())), 110);
        host.drawPopupTextWithEllipsis(context, publishPresetPublic
                ? Component.translatable("pathmind.marketplace.visiblePublic").getString()
                : Component.translatable("pathmind.marketplace.visiblePrivate").getString(),
            fieldX, visibilityY + fieldHeight + 8, fieldWidth, host.getPopupAnimatedColor(publishPresetPopupAnimation, UITheme.TEXT_TERTIARY));

        if (!publishPresetStatus.isEmpty()) {
            host.drawPopupTextWithEllipsis(context, publishPresetStatus, fieldX, contentY + 214, fieldWidth,
                host.getPopupAnimatedColor(publishPresetPopupAnimation, publishPresetStatusColor));
        }

        PathmindPopupLayout.Rect cancelButton = layout.cancelButton();
        PathmindPopupLayout.Rect publishButton = layout.publishButton();
        PathmindPopupLayout.Rect signInButton = layout.signInButton();
        int accountTextX = popupX + scaledWidth / 2 - host.textRenderer().width(accountLabel) / 2;
        int accountTextY = cancelButton.y() + (cancelButton.height() - host.textRenderer().lineHeight) / 2 + 1;

        PathmindPopupRenderer.drawButton(
            context,
            host.textRenderer(),
            cancelButton,
            mouseX,
            mouseY,
            Component.translatable("pathmind.button.cancel"),
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            host.getAccentColor(),
            publishPresetPopupAnimation
        );
        if (publishPresetSession == null) {
            PathmindPopupRenderer.drawButton(
                context,
                host.textRenderer(),
                signInButton,
                mouseX,
                mouseY,
                Component.literal(accountLabel),
                PathmindPopupRenderer.ButtonStyle.DEFAULT,
                host.getAccentColor(),
                publishPresetPopupAnimation
            );
        } else {
            context.drawString(host.textRenderer(), Component.literal(accountLabel), accountTextX, accountTextY,
                host.getPopupAnimatedColor(publishPresetPopupAnimation, UITheme.TEXT_SECONDARY));
        }
        PathmindPopupRenderer.drawButton(
            context,
            host.textRenderer(),
            publishButton,
            mouseX,
            mouseY,
            Component.literal(publishPresetBusy ? Component.translatable("pathmind.status.working").getString() : (publishPresetEditingPreset == null ? Component.translatable("pathmind.marketplace.publish").getString() : Component.translatable("pathmind.button.update").getString())),
            PathmindPopupRenderer.ButtonStyle.PRIMARY,
            host.getAccentColor(),
            publishPresetPopupAnimation
        );
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderRenamePresetPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, renamePresetPopupAnimation.getPopupAlpha());

        int[] bounds = host.getBoundedScaledPopupBounds(renamePresetPopupAnimation, CREATE_PRESET_POPUP_WIDTH, CREATE_PRESET_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.getBoundedPopupContentY(popupY, renamePresetPopupAnimation, CREATE_PRESET_POPUP_HEIGHT);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, renamePresetPopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            host.textRenderer(),
            Component.translatable("pathmind.popup.renamePreset.title"),
            popupX,
            contentY,
            scaledWidth,
            renamePresetPopupAnimation
        );

        String presetLabel = pendingPresetRenameName == null || pendingPresetRenameName.isEmpty()
            ? Component.translatable("pathmind.popup.preset.fallbackSelected").getString()
            : Component.translatable("pathmind.popup.preset.label", pendingPresetRenameName).getString();
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.popup.renamePreset.message").getString(), popupX + 20, contentY + 44, scaledWidth - 40,
            host.getPopupAnimatedColor(renamePresetPopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupTextWithEllipsis(context, presetLabel, popupX + 20, contentY + 58, scaledWidth - 40,
            host.getPopupAnimatedColor(renamePresetPopupAnimation, UITheme.TEXT_SECONDARY));

        int fieldX = popupX + 20;
        int fieldY = contentY + 80;
        int fieldWidth = scaledWidth - 40;
        int fieldHeight = 16;
        renderPresetTextField(context, mouseX, mouseY, delta, renamePresetField, fieldX, fieldY, fieldWidth, fieldHeight, renamePresetPopupAnimation);

        if (!renamePresetStatus.isEmpty()) {
            host.drawPopupTextWithEllipsis(context, renamePresetStatus, fieldX, fieldY + fieldHeight + 8, fieldWidth,
                host.getPopupAnimatedColor(renamePresetPopupAnimation, renamePresetStatusColor));
        }

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, contentY, CREATE_PRESET_POPUP_HEIGHT, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Component.translatable("pathmind.button.cancel"),
            Component.translatable("pathmind.button.rename"),
            renamePresetPopupAnimation);
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void renderPresetDeletePopup(GuiGraphics context, int mouseX, int mouseY) {
        RenderStateBridge.setShaderColor(1f, 1f, 1f, presetDeletePopupAnimation.getPopupAlpha());

        int[] bounds = host.getBoundedScaledPopupBounds(presetDeletePopupAnimation, PRESET_DELETE_POPUP_WIDTH, PRESET_DELETE_POPUP_HEIGHT);
        int popupX = bounds[0];
        int popupY = bounds[1];
        int scaledWidth = bounds[2];
        int scaledHeight = bounds[3];
        int contentY = host.getBoundedPopupContentY(popupY, presetDeletePopupAnimation, PRESET_DELETE_POPUP_HEIGHT);
        host.setOverlayCutout(popupX, popupY, scaledWidth, scaledHeight);

        boolean popupScissor = PathmindPopupRenderer.beginPopup(context, popupX, popupY, scaledWidth, scaledHeight, presetDeletePopupAnimation);

        PathmindPopupRenderer.drawTitle(
            context,
            host.textRenderer(),
            Component.translatable("pathmind.popup.deletePreset.title"),
            popupX,
            contentY,
            scaledWidth,
            presetDeletePopupAnimation
        );

        String presetLabel = pendingPresetDeletionName != null && !pendingPresetDeletionName.isEmpty()
            ? pendingPresetDeletionName
            : Component.translatable("pathmind.popup.preset.fallbackCurrent").getString();
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.popup.deletePreset.message").getString(), popupX + 20, contentY + 48, scaledWidth - 40,
            host.getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.popup.preset.label", presetLabel).getString(), popupX + 20, contentY + 64, scaledWidth - 40,
            host.getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        int checkboxX = popupX + 20;
        int checkboxY = contentY + 86;
        boolean checkboxHovered = host.isPointInRect(mouseX, mouseY, checkboxX - 2, checkboxY - 2, PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4, PRESET_DELETE_SKIP_CHECKBOX_SIZE + 4);
        context.fill(checkboxX, checkboxY, checkboxX + PRESET_DELETE_SKIP_CHECKBOX_SIZE, checkboxY + PRESET_DELETE_SKIP_CHECKBOX_SIZE,
            host.getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.RENAME_INPUT_BG));
        DrawContextBridge.drawBorder(context, checkboxX, checkboxY, PRESET_DELETE_SKIP_CHECKBOX_SIZE, PRESET_DELETE_SKIP_CHECKBOX_SIZE,
            host.getPopupAnimatedColor(presetDeletePopupAnimation, checkboxHovered ? UITheme.BORDER_HIGHLIGHT : UITheme.BORDER_DEFAULT));
        if (isSkipPresetDeleteConfirm()) {
            int checkColor = host.getPopupAnimatedColor(presetDeletePopupAnimation, host.getAccentColor());
            context.fill(checkboxX + 2, checkboxY + 5, checkboxX + 3, checkboxY + 7, checkColor);
            context.fill(checkboxX + 3, checkboxY + 6, checkboxX + 4, checkboxY + 8, checkColor);
            context.fill(checkboxX + 4, checkboxY + 6, checkboxX + 5, checkboxY + 7, checkColor);
            context.fill(checkboxX + 5, checkboxY + 5, checkboxX + 6, checkboxY + 6, checkColor);
            context.fill(checkboxX + 6, checkboxY + 4, checkboxX + 7, checkboxY + 5, checkColor);
            context.fill(checkboxX + 7, checkboxY + 3, checkboxX + 8, checkboxY + 4, checkColor);
        }
        host.drawPopupTextWithEllipsis(context, Component.translatable("pathmind.presetDelete.dontShowAgain").getString(), checkboxX + PRESET_DELETE_SKIP_CHECKBOX_SIZE + 8, checkboxY + 1, scaledWidth - 68,
            host.getPopupAnimatedColor(presetDeletePopupAnimation, UITheme.TEXT_SECONDARY));

        PathmindPopupLayout.ButtonRow buttonRow = PathmindPopupLayout.twoButtonRow(popupX, scaledWidth, contentY, PRESET_DELETE_POPUP_HEIGHT, 90, 20, 16);
        renderButtonRow(context, mouseX, mouseY, buttonRow,
            Component.translatable("pathmind.button.cancel"),
            Component.translatable("pathmind.button.delete"),
            presetDeletePopupAnimation);
        PathmindPopupRenderer.disableScissor(context, popupScissor);
        RenderStateBridge.setShaderColor(1f, 1f, 1f, 1f);
    }

    void focusPublishPresetField(EditBox target) {
        if (publishPresetNameField != null) {
            publishPresetNameField.setFocused(publishPresetNameField == target);
        }
        if (publishPresetDescriptionField != null) {
            publishPresetDescriptionField.setFocused(publishPresetDescriptionField == target);
        }
        if (publishPresetTagsField != null) {
            publishPresetTagsField.setFocused(publishPresetTagsField == target);
        }
    }

    private void renderPresetTextField(GuiGraphics context, int mouseX, int mouseY, float delta, EditBox field,
                                       int fieldX, int fieldY, int fieldWidth, int fieldHeight, com.pathmind.ui.animation.PopupAnimationHandler animation) {
        boolean fieldHovered = host.isPointInRect(mouseX, mouseY, fieldX, fieldY, fieldWidth, fieldHeight);
        boolean focused = field != null && field.isFocused();
        int borderColor = focused ? host.getAccentColor() : fieldHovered ? UITheme.BORDER_HIGHLIGHT : UITheme.RENAME_INPUT_BORDER;
        PathmindPopupRenderer.drawPopupTextField(
            context,
            field,
            mouseX,
            mouseY,
            delta,
            fieldX,
            fieldY,
            fieldWidth,
            fieldHeight,
            borderColor,
            animation,
            UITheme.TEXT_PRIMARY,
            UITheme.TEXT_TERTIARY,
            TEXT_FIELD_VERTICAL_PADDING
        );
    }

    private void renderPublishPresetField(GuiGraphics context, int mouseX, int mouseY, float delta, EditBox field,
                                          PathmindPopupLayout.Rect bounds) {
        renderPresetTextField(context, mouseX, mouseY, delta, field, bounds.x(), bounds.y(), bounds.width(), bounds.height(), publishPresetPopupAnimation);
    }

    private void renderPublishVisibilityToggle(GuiGraphics context, int mouseX, int mouseY, PathmindPopupLayout.Rect row, PathmindPopupLayout.Rect toggle) {
        publishPresetVisibilityToggle.setValue(publishPresetPublic);
        publishPresetVisibilityToggle.setPosition(toggle.x(), toggle.y());
        publishPresetVisibilityToggle.render(context, mouseX, mouseY, publishPresetPopupAnimation.getPopupAlpha());
        String label = publishPresetPublic ? Component.translatable("pathmind.option.public").getString() : Component.translatable("pathmind.option.private").getString();
        int labelColor = publishPresetPublic ? host.getAccentColor() : UITheme.STATE_WARNING;
        host.drawPopupTextWithEllipsis(context, label, row.x(), row.y() + 4, row.width() - toggle.width() - 8,
            host.getPopupAnimatedColor(publishPresetPopupAnimation, labelColor));
    }

    private void syncPublishPresetVisibilityToggleColors() {
        publishPresetVisibilityToggle.setIndicatorColors(UITheme.MARKETPLACE_PRIVATE_VISIBILITY, host.getAccentColor());
    }

    private void renderButtonRow(GuiGraphics context, int mouseX, int mouseY, PathmindPopupLayout.ButtonRow buttonRow,
                                 Component leftLabel, Component rightLabel, com.pathmind.ui.animation.PopupAnimationHandler animation) {
        PathmindPopupLayout.Rect leftButton = buttonRow.left();
        PathmindPopupLayout.Rect rightButton = buttonRow.right();
        PathmindPopupRenderer.drawButton(
            context,
            host.textRenderer(),
            leftButton,
            mouseX,
            mouseY,
            leftLabel,
            PathmindPopupRenderer.ButtonStyle.DEFAULT,
            host.getAccentColor(),
            animation
        );
        PathmindPopupRenderer.drawButton(
            context,
            host.textRenderer(),
            rightButton,
            mouseX,
            mouseY,
            rightLabel,
            PathmindPopupRenderer.ButtonStyle.PRIMARY,
            host.getAccentColor(),
            animation
        );
    }
}
