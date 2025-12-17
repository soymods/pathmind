package com.pathmind.screen;

import com.pathmind.PathmindMod;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * A small icon button used on the title screen to open the Pathmind visual editor.
 */
public class PathmindMainMenuButton extends ButtonWidget {
    private static final Identifier ICON_TEXTURE = PathmindMod.id("textures/gui/button_logo.png");
    private static final int ICON_PADDING = 2;
    private static final Text OPEN_EDITOR_TEXT = Text.translatable("gui.pathmind.open_editor");

    public PathmindMainMenuButton(int x, int y, int size, PressAction pressAction) {
        super(x, y, size, size, Text.empty(), pressAction, DEFAULT_NARRATION_SUPPLIER);
        this.setTooltip(Tooltip.of(OPEN_EDITOR_TEXT));
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);

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

        int color = MathHelper.ceil(this.alpha * 255.0F) << 24 | rgb;

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            ICON_TEXTURE,
            iconX,
            iconY,
            0.0F,
            0.0F,
            iconSize,
            iconSize,
            iconSize,
            iconSize,
            color
        );
    }

    @Override
    public MutableText getNarrationMessage() {
        return OPEN_EDITOR_TEXT.copy();
    }
}
