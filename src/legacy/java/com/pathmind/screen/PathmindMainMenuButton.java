package com.pathmind.screen;

import com.pathmind.PathmindMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import com.pathmind.util.TextCompatibilityBridge;

/**
 * A small icon button used on the title screen to open the Pathmind visual editor.
 */
public class PathmindMainMenuButton extends ButtonWidget {
    private static final Identifier ICON_TEXTURE = PathmindMod.id("textures/gui/button_logo.png");
    private static final int ICON_PADDING = 2;
    private static final int BUTTON_FILL = 0xFF4C4C4C;
    private static final int BUTTON_HOVER = 0xFF5A5A5A;
    private static final int BUTTON_DISABLED = 0xFF383838;
    private static final int BORDER_LIGHT = 0xFF8B8B8B;
    private static final int BORDER_DARK = 0xFF1F1F1F;
    private static final int BORDER_HOVER_LIGHT = 0xFFFFFFFF;
    private static final int SHADOW_COLOR = 0x66000000;
    private static final String OPEN_EDITOR_KEY = "gui.pathmind.open_editor";
    private static final String OPEN_EDITOR_FALLBACK = "Open Pathmind";
    private static final String CREATOR_SUFFIX = " by soymods";

    public PathmindMainMenuButton(int x, int y, int size, PressAction pressAction) {
        super(x, y, size, size, TextCompatibilityBridge.empty(), pressAction, DEFAULT_NARRATION_SUPPLIER);
        this.setTooltip(Tooltip.of(resolveOpenEditorText()));
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        this.setTooltip(Tooltip.of(resolveOpenEditorText()));
        drawButtonBackground(context);

        int iconSize = this.width - ICON_PADDING * 2;
        int iconX = this.getX() + ICON_PADDING;
        int iconY = this.getY() + ICON_PADDING;

        int rgb;
        if (!this.active) {
            rgb = 0xA0A0A0;
        } else if (this.isHovered()) {
            rgb = 0xFFFFFF;
        } else {
            rgb = 0xFFFFFF;
        }

        int alphaComponent = MathHelper.ceil(this.alpha * 255.0F);
        if (alphaComponent <= 0) {
            return; // let the icon fade in with the button instead of flashing
        }
        int color = (alphaComponent << 24) | rgb;

        GuiTextureRenderer.drawIcon(context, ICON_TEXTURE, iconX, iconY, iconSize, color);
    }

    private void drawButtonBackground(DrawContext context) {
        int baseFill = !this.active ? BUTTON_DISABLED : (this.isHovered() ? BUTTON_HOVER : BUTTON_FILL);
        int fill = applyAlpha(baseFill, this.alpha);
        boolean hovered = this.active && this.isHovered();
        int topBorder = applyAlpha(hovered ? BORDER_HOVER_LIGHT : BORDER_LIGHT, this.alpha);
        int bottomBorder = applyAlpha(BORDER_DARK, this.alpha);
        int shadow = applyAlpha(SHADOW_COLOR, this.alpha);

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        context.fill(x + 1, y + h, x + w + 1, y + h + 1, shadow);
        context.fill(x + w, y + 1, x + w + 1, y + h, shadow);
        context.fill(x, y, x + w, y + h, fill);
        context.fill(x, y, x + w, y + 1, topBorder);
        context.fill(x, y, x + 1, y + h, topBorder);
        context.fill(x, y + h - 1, x + w, y + h, bottomBorder);
        context.fill(x + w - 1, y, x + w, y + h, bottomBorder);
    }

    private static int applyAlpha(int color, float alphaMultiplier) {
        int alpha = (color >>> 24) & 0xFF;
        int scaledAlpha = MathHelper.ceil(alpha * Math.max(0.0f, Math.min(1.0f, alphaMultiplier)));
        return (scaledAlpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public MutableText getNarrationMessage() {
        return TextCompatibilityBridge.copy(resolveOpenEditorText());
    }

    private static MutableText resolveOpenEditorText() {
        MutableText text = TextCompatibilityBridge.translatable(OPEN_EDITOR_KEY);
        if (OPEN_EDITOR_KEY.equals(text.getString())) {
            return TextCompatibilityBridge.literal(OPEN_EDITOR_FALLBACK + CREATOR_SUFFIX);
        }
        return text.append(TextCompatibilityBridge.literal(CREATOR_SUFFIX));
    }
}
