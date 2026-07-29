package com.pathmind.screen;

import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.MatrixStackBridge;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Owns the preset tab bar context menu state, rendering, and action routing.
 */
final class PathmindPresetContextMenuController {
    private static final int WIDTH = 132;
    private static final int ITEM_HEIGHT = 18;
    private static final int SEPARATOR_HEIGHT = 5;
    private static final String[] GROUP_COLOR_KEYS = {"sky", "mint", "amber", "rose", "violet"};
    private static final int[] GROUP_COLORS = {0xFF38BDF8, 0xFF34D399, 0xFFF59E0B, 0xFFFB7185, 0xFFA78BFA};

    interface Host {
        int screenWidth();
        int screenHeight();
        Font textRenderer();
        boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height);
        String presetTabAt(int mouseX, int mouseY);
        String presetGroupAt(int mouseX, int mouseY);
        String presetGroupKey(String presetName);
        int presetGroupColor(String presetName);
        String presetGroupColorLabel(String key);
        String nextPresetGroupColorKey();
        boolean isPresetRenameDisabled(String presetName);
        boolean isPresetDeleteDisabled(String presetName);
        void closePresetDropdown();
        void closeGraphContextMenus();
        void openCreatePresetPopup();
        void createPresetGroup();
        void deletePresetGroup(String groupKey);
        void recolorPresetGroup(String oldKey, String newKey);
        void openRenamePresetPopup(String presetName);
        void openPresetDeletePopup(String presetName);
        void setPresetGroupColor(String presetName, String colorKey);
    }

    private final Host host;
    private boolean open = false;
    private int x = 0;
    private int y = 0;
    private String presetName = "";
    private String groupKey = "";

    PathmindPresetContextMenuController(Host host) {
        this.host = host;
    }

    boolean isOpen() {
        return open;
    }

    void close() {
        open = false;
    }

    void open(int mouseX, int mouseY) {
        presetName = host.presetTabAt(mouseX, mouseY);
        groupKey = host.presetGroupAt(mouseX, mouseY);
        x = Mth.clamp(mouseX, 4, Math.max(4, host.screenWidth() - WIDTH - 4));
        y = Mth.clamp(mouseY, 4, Math.max(4, host.screenHeight() - getHeight() - 4));
        open = true;
        host.closePresetDropdown();
        host.closeGraphContextMenus();
    }

    void render(GuiGraphics context, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        Object matrices = context.pose();
        MatrixStackBridge.push(matrices);
        try {
            MatrixStackBridge.translateZ(matrices, 600.0f);
            int height = getHeight();
            context.fill(x, y, x + WIDTH, y + height, UITheme.BACKGROUND_SECONDARY);
            DrawContextBridge.drawBorderInLayer(context, x, y, WIDTH, height, UITheme.BORDER_DEFAULT);
            int itemY = y;
            itemY = drawItem(context, mouseX, mouseY, itemY, Component.translatable("pathmind.context.createPreset").getString(), 0, false);
            itemY = drawItem(context, mouseX, mouseY, itemY, Component.translatable("pathmind.context.createGroup").getString(), 0, host.nextPresetGroupColorKey().isEmpty());
            if (groupKey != null && !groupKey.isEmpty()) {
                itemY = drawItem(context, mouseX, mouseY, itemY, Component.translatable("pathmind.context.deleteGroup").getString(), 0, false);
                itemY = drawSeparator(context, itemY);
                for (int i = 0; i < GROUP_COLOR_KEYS.length; i++) {
                    itemY = drawItem(context, mouseX, mouseY, itemY, host.presetGroupColorLabel(GROUP_COLOR_KEYS[i]), GROUP_COLORS[i], GROUP_COLOR_KEYS[i].equals(groupKey));
                }
                return;
            }
            if (presetName == null) {
                return;
            }
            itemY = drawItem(context, mouseX, mouseY, itemY, Component.translatable("pathmind.context.renamePreset").getString(), 0, host.isPresetRenameDisabled(presetName));
            itemY = drawItem(context, mouseX, mouseY, itemY, Component.translatable("pathmind.context.deletePreset").getString(), 0, host.isPresetDeleteDisabled(presetName));
            if (!host.presetGroupKey(presetName).isEmpty()) {
                drawItem(context, mouseX, mouseY, itemY, Component.translatable("pathmind.context.ungroup").getString(), host.presetGroupColor(presetName), false);
            }
        } finally {
            MatrixStackBridge.pop(matrices);
        }
    }

    boolean handleClick(int mouseX, int mouseY) {
        if (!host.isPointInRect(mouseX, mouseY, x, y, WIDTH, getHeight())) {
            open = false;
            return true;
        }
        int relativeY = mouseY - y;
        if (relativeY < ITEM_HEIGHT * 2) {
            int action = relativeY / ITEM_HEIGHT;
            open = false;
            if (action == 0) {
                host.openCreatePresetPopup();
            } else if (action == 1) {
                host.createPresetGroup();
            }
            return true;
        }
        if (groupKey != null && !groupKey.isEmpty()) {
            if (relativeY < ITEM_HEIGHT * 3) {
                int action = relativeY / ITEM_HEIGHT;
                open = false;
                if (action == 2) {
                    host.deletePresetGroup(groupKey);
                }
                return true;
            }
            relativeY -= ITEM_HEIGHT * 3 + SEPARATOR_HEIGHT;
            if (relativeY >= 0 && relativeY < ITEM_HEIGHT * GROUP_COLOR_KEYS.length) {
                int action = relativeY / ITEM_HEIGHT;
                open = false;
                host.recolorPresetGroup(groupKey, GROUP_COLOR_KEYS[action]);
            }
            return true;
        }
        if (presetName == null) {
            return true;
        }
        relativeY -= ITEM_HEIGHT * 2;
        int presetActionCount = host.presetGroupKey(presetName).isEmpty() ? 2 : 3;
        if (relativeY < ITEM_HEIGHT * presetActionCount) {
            int action = relativeY / ITEM_HEIGHT;
            open = false;
            if (action == 0 && !host.isPresetRenameDisabled(presetName)) {
                host.openRenamePresetPopup(presetName);
            } else if (action == 1 && !host.isPresetDeleteDisabled(presetName)) {
                host.openPresetDeletePopup(presetName);
            } else if (action == 2) {
                host.setPresetGroupColor(presetName, null);
            }
        }
        return true;
    }

    private int getHeight() {
        if (groupKey != null && !groupKey.isEmpty()) {
            return ITEM_HEIGHT * (GROUP_COLOR_KEYS.length + 3) + SEPARATOR_HEIGHT;
        }
        if (presetName == null) {
            return ITEM_HEIGHT * 2;
        }
        return ITEM_HEIGHT * (host.presetGroupKey(presetName).isEmpty() ? 4 : 5);
    }

    private int drawSeparator(GuiGraphics context, int itemY) {
        int lineY = itemY + SEPARATOR_HEIGHT / 2;
        context.hLine(x + 5, x + WIDTH - 6, lineY, UITheme.BORDER_SUBTLE);
        return itemY + SEPARATOR_HEIGHT;
    }

    private int drawItem(GuiGraphics context, int mouseX, int mouseY, int itemY, String label, int swatchColor, boolean disabled) {
        boolean hovered = !disabled && host.isPointInRect(mouseX, mouseY, x, itemY, WIDTH, ITEM_HEIGHT);
        if (hovered) {
            context.fill(x + 1, itemY + 1, x + WIDTH - 1, itemY + ITEM_HEIGHT, UITheme.BUTTON_DEFAULT_HOVER);
        }
        int textColor = disabled ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int textX = x + 7;
        if (swatchColor != 0) {
            context.fill(textX, itemY + 6, textX + 7, itemY + 13, swatchColor);
            textX += 12;
        }
        context.drawString(host.textRenderer(), Component.literal(label), textX, itemY + 5, textColor);
        return itemY + ITEM_HEIGHT;
    }
}
