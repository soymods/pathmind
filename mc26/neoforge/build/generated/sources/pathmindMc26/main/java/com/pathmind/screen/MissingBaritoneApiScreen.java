package com.pathmind.screen;

import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Warning screen displayed when the user tries to use Baritone-only features without the Baritone API.
 */
public class MissingBaritoneApiScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 8;

    private final Screen parent;
    private boolean systemCursorHidden = false;

    public MissingBaritoneApiScreen(Screen parent) {
        super(Component.translatable("pathmind.screen.missingBaritone.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        ensureCustomCursorHidden();
        int centerX = this.width / 2;
        int startY = this.height / 2 + 10;

        addRenderableWidget(Button.builder(
            Component.translatable("pathmind.button.openLink"),
            button -> openDownloadLink()
        ).bounds(centerX - BUTTON_WIDTH - BUTTON_SPACING / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.translatable("pathmind.button.copyLink"),
            button -> copyDownloadLink()
        ).bounds(centerX + BUTTON_SPACING / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
            Component.translatable("pathmind.button.close"),
            button -> onClose()
        ).bounds(centerX - (BUTTON_WIDTH / 2), startY + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void openDownloadLink() {
        Util.getPlatform().openUri(BaritoneDependencyChecker.DOWNLOAD_URL);
    }

    private void copyDownloadLink() {
        Minecraft client = this.minecraft;
        if (client != null && client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(BaritoneDependencyChecker.DOWNLOAD_URL);
        }
    }

    @Override
    public void onClose() {
        restoreSystemCursor();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        restoreSystemCursor();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Solid background to avoid blur issues with shader mods
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;
        int messageY = this.height / 2 - 40;

        context.centeredText(this.font, Component.translatable("pathmind.popup.missingBaritone.title"), centerX, messageY, UITheme.TEXT_HEADER);
        context.centeredText(this.font, Component.translatable("pathmind.popup.missingBaritone.message"), centerX, messageY + 16, UITheme.TEXT_PRIMARY);
        context.centeredText(this.font, Component.literal(BaritoneDependencyChecker.DOWNLOAD_URL), centerX, messageY + 32, UITheme.LINK_COLOR);

        super.extractRenderState(context, mouseX, mouseY, delta);
        PathmindCursor.renderDefault(context, mouseX, mouseY);
    }

    private void ensureCustomCursorHidden() {
        if (systemCursorHidden) {
            return;
        }
        PathmindCursor.hideSystemCursor(this.minecraft != null ? this.minecraft : Minecraft.getInstance());
        systemCursorHidden = true;
    }

    private void restoreSystemCursor() {
        if (!systemCursorHidden) {
            return;
        }
        PathmindCursor.showSystemCursor(this.minecraft != null ? this.minecraft : Minecraft.getInstance());
        systemCursorHidden = false;
    }
}
