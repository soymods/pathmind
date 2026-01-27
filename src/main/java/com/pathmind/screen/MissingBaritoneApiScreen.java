package com.pathmind.screen;

import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.BaritoneDependencyChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Warning screen displayed when the user tries to use Baritone-only features without the Baritone API.
 */
public class MissingBaritoneApiScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 8;

    private final Screen parent;

    public MissingBaritoneApiScreen(Screen parent) {
        super(Text.literal("Pathmind - Missing Baritone API"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = this.height / 2 + 10;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Open download link"),
            button -> openDownloadLink()
        ).dimensions(centerX - BUTTON_WIDTH - BUTTON_SPACING / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Copy link"),
            button -> copyDownloadLink()
        ).dimensions(centerX + BUTTON_SPACING / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            button -> close()
        ).dimensions(centerX - (BUTTON_WIDTH / 2), startY + BUTTON_HEIGHT + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private void openDownloadLink() {
        Util.getOperatingSystem().open(BaritoneDependencyChecker.DOWNLOAD_URL);
    }

    private void copyDownloadLink() {
        MinecraftClient client = this.client;
        if (client != null && client.keyboard != null) {
            client.keyboard.setClipboard(BaritoneDependencyChecker.DOWNLOAD_URL);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Solid background to avoid blur issues with shader mods
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;
        int messageY = this.height / 2 - 40;

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Optional Baritone API not detected"), centerX, messageY, UITheme.TEXT_HEADER);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Install baritone-api to unlock Baritone nodes"), centerX, messageY + 16, UITheme.TEXT_PRIMARY);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(BaritoneDependencyChecker.DOWNLOAD_URL), centerX, messageY + 32, UITheme.LINK_COLOR);

        super.render(context, mouseX, mouseY, delta);
    }
}
