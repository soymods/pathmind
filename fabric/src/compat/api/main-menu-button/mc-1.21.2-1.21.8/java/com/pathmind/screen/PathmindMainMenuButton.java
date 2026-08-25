package com.pathmind.screen;

import com.pathmind.PathmindMod;
import com.pathmind.util.TextCompatibilityBridge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class PathmindMainMenuButton extends Button {
    private static final Identifier ICON_TEXTURE = PathmindMod.id("textures/gui/icons/button_logo.png");
    private static final int ICON_PADDING = 2;
    private static final String OPEN_EDITOR_KEY = "gui.pathmind.open_editor";

    public PathmindMainMenuButton(int x, int y, int size, OnPress pressAction) {
        super(x, y, size, size, TextCompatibilityBridge.empty(), pressAction, DEFAULT_NARRATION);
        this.setTooltip(Tooltip.create(resolveOpenEditorText()));
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.setTooltip(Tooltip.create(resolveOpenEditorText()));
        drawButtonBackground(context);
        int iconSize = this.width - ICON_PADDING * 2;
        if (Mth.ceil(this.alpha * 255.0F) > 0) {
            // 1.21.2–1.21.8 changed the GUI render type more than once.  The
            // shared renderer resolves the available API at runtime so this
            // one source set remains valid for the whole compatibility range.
            GuiTextureRenderer.drawIcon(context, ICON_TEXTURE, this.getX() + ICON_PADDING, this.getY() + ICON_PADDING, iconSize, 0xFFFFFFFF);
        }
    }

    private void drawButtonBackground(GuiGraphics context) {
        int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
        int fill = applyAlpha(!this.active ? 0xFF383838 : (this.isHovered() ? 0xFF5A5A5A : 0xFF4C4C4C), this.alpha);
        context.fill(x, y, x + w, y + h, fill);
        context.fill(x, y, x + w, y + 1, applyAlpha(this.isHovered() && this.active ? 0xFFFFFFFF : 0xFF8B8B8B, this.alpha));
        context.fill(x, y, x + 1, y + h, applyAlpha(this.isHovered() && this.active ? 0xFFFFFFFF : 0xFF8B8B8B, this.alpha));
        context.fill(x, y + h - 1, x + w, y + h, applyAlpha(0xFF1F1F1F, this.alpha));
        context.fill(x + w - 1, y, x + w, y + h, applyAlpha(0xFF1F1F1F, this.alpha));
    }

    private static int applyAlpha(int color, float alphaMultiplier) {
        return (Mth.ceil(((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alphaMultiplier)) ) << 24) | (color & 0x00FFFFFF);
    }

    @Override public MutableComponent createNarrationMessage() { return TextCompatibilityBridge.copy(resolveOpenEditorText()); }
    private static MutableComponent resolveOpenEditorText() { return TextCompatibilityBridge.translatable(OPEN_EDITOR_KEY); }
}
