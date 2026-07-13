package com.pathmind.neoforge;

import com.pathmind.PathmindCommon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

final class PathmindNeoForgeMainMenuButton extends Button {
    private static final String ICON_PATH = "textures/gui/icons/button_logo.png";
    private static final int ICON_PADDING = 2;
    private static final int BUTTON_FILL = 0xFF4C4C4C;
    private static final int BUTTON_HOVER = 0xFF5A5A5A;
    private static final int BUTTON_DISABLED = 0xFF383838;
    private static final int BORDER_LIGHT = 0xFF8B8B8B;
    private static final int BORDER_DARK = 0xFF1F1F1F;
    private static final int BORDER_HOVER_LIGHT = 0xFFFFFFFF;
    private static final int SHADOW_COLOR = 0x66000000;
    private static final Component OPEN_EDITOR = Component.translatable("gui.pathmind.open_editor");
    private static final Object ICON_TEXTURE = createIdentifier(PathmindCommon.MOD_ID, ICON_PATH);

    PathmindNeoForgeMainMenuButton(int x, int y, int size, OnPress pressAction) {
        super(x, y, size, size, Component.empty(), pressAction, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(OPEN_EDITOR));
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderPathmindButton(context);
    }

    private void renderPathmindButton(GuiGraphics context) {
        setTooltip(Tooltip.create(OPEN_EDITOR));
        drawButtonBackground(context);

        int iconSize = getWidth() - ICON_PADDING * 2;
        int iconX = getX() + ICON_PADDING;
        int iconY = getY() + ICON_PADDING;
        if (!drawIcon(context, iconX, iconY, iconSize)) {
            context.drawCenteredString(
                MinecraftFontHolder.font(),
                "P",
                getX() + getWidth() / 2,
                getY() + (getHeight() - MinecraftFontHolder.font().lineHeight) / 2,
                active ? 0xFFFFFFFF : 0xFFA0A0A0
            );
        }
    }

    private void drawButtonBackground(GuiGraphics context) {
        int fill = !active ? BUTTON_DISABLED : (isHovered() ? BUTTON_HOVER : BUTTON_FILL);
        int topBorder = isHovered() && active ? BORDER_HOVER_LIGHT : BORDER_LIGHT;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        context.fill(x + 1, y + h, x + w + 1, y + h + 1, SHADOW_COLOR);
        context.fill(x + w, y + 1, x + w + 1, y + h, SHADOW_COLOR);
        context.fill(x, y, x + w, y + h, fill);
        context.fill(x, y, x + w, y + 1, topBorder);
        context.fill(x, y, x + 1, y + h, topBorder);
        context.fill(x, y + h - 1, x + w, y + h, BORDER_DARK);
        context.fill(x + w - 1, y, x + w, y + h, BORDER_DARK);
    }

    private static boolean drawIcon(GuiGraphics context, int x, int y, int size) {
        if (ICON_TEXTURE == null) {
            return false;
        }
        if (invokePipelineBlit(context, ICON_TEXTURE, x, y, size)) {
            return true;
        }
        return invokeLegacyBlit(context, ICON_TEXTURE, x, y, size);
    }

    private static boolean invokePipelineBlit(GuiGraphics context, Object texture, int x, int y, int size) {
        try {
            Class<?> pipelineClass = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline");
            Class<?> pipelinesClass = Class.forName("net.minecraft.client.renderer.RenderPipelines");
            Object guiTexturedPipeline = pipelinesClass.getField("GUI_TEXTURED").get(null);
            Method method = context.getClass().getMethod(
                "blit",
                pipelineClass,
                texture.getClass(),
                int.class,
                int.class,
                float.class,
                float.class,
                int.class,
                int.class,
                int.class,
                int.class
            );
            method.invoke(context, guiTexturedPipeline, texture, x, y, 0.0f, 0.0f, size, size, size, size);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean invokeLegacyBlit(GuiGraphics context, Object texture, int x, int y, int size) {
        try {
            Method method = context.getClass().getMethod(
                "blit",
                texture.getClass(),
                int.class,
                int.class,
                float.class,
                float.class,
                int.class,
                int.class,
                int.class,
                int.class
            );
            method.invoke(context, texture, x, y, 0.0f, 0.0f, size, size, size, size);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Object createIdentifier(String namespace, String path) {
        for (String className : new String[]{"net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"}) {
            try {
                Class<?> identifierClass = Class.forName(className);
                for (String factoryName : new String[]{"fromNamespaceAndPath", "tryBuild"}) {
                    try {
                        Method factory = identifierClass.getMethod(factoryName, String.class, String.class);
                        Object identifier = factory.invoke(null, namespace, path);
                        if (identifier != null) {
                            return identifier;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // Try the next resource identifier factory.
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Try the next Minecraft naming generation.
            }
        }
        return null;
    }

    private static final class MinecraftFontHolder {
        private MinecraftFontHolder() {
        }

        private static net.minecraft.client.gui.Font font() {
            return net.minecraft.client.Minecraft.getInstance().font;
        }
    }
}
