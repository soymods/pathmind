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
    private static final net.minecraft.text.Text OPEN_EDITOR_TEXT =
        TextCompatibilityBridge.translatableWithFallback("gui.pathmind.open_editor", "Open Pathmind Editor");

    public PathmindMainMenuButton(int x, int y, int size, PressAction pressAction) {
        super(x, y, size, size, TextCompatibilityBridge.empty(), pressAction, DEFAULT_NARRATION_SUPPLIER);
        this.setTooltip(Tooltip.of(OPEN_EDITOR_TEXT));
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        drawButton(context);

        int iconSize = this.width - ICON_PADDING * 2;
        int iconX = this.getX() + ICON_PADDING;
        int iconY = this.getY() + ICON_PADDING;

        int rgb;
        if (!this.active) {
            rgb = 0xA0A0A0;
        } else if (this.isHovered()) {
            rgb = 0xFFFFA0;
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

    @Override
    public MutableText getNarrationMessage() {
        return TextCompatibilityBridge.copy(OPEN_EDITOR_TEXT);
    }
}
