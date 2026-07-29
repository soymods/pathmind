package com.pathmind.screen;

import com.pathmind.data.PresetManager;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.control.PathmindIconRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;
import com.pathmind.util.TextRenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Owns the preset overflow dropdown's animation, scrolling, rendering, and hit testing.
 * The visual editor supplies only the screen primitives and preset actions it needs.
 */
final class PathmindPresetDropdownController {
    private static final int TAB_BAR_TOP = 4;
    private static final int TAB_HEIGHT = 16;
    private static final int PRESET_MENU_BUTTON_SIZE = 18;
    private static final int PRESET_DROPDOWN_WIDTH = 220;
    private static final int PRESET_DROPDOWN_MARGIN = 6;
    private static final int PRESET_OPTION_HEIGHT = 18;
    private static final int PRESET_TEXT_LEFT_PADDING = 6;
    private static final int PRESET_DELETE_ICON_SIZE = 8;
    private static final int PRESET_DELETE_ICON_MARGIN = 6;
    private static final int PRESET_DELETE_ICON_HITBOX_PADDING = 2;
    private static final int PRESET_RENAME_ICON_SIZE = 8;
    private static final int PRESET_RENAME_ICON_HITBOX_PADDING = 2;
    private static final int PRESET_TEXT_ICON_GAP = 4;

    interface Host {
        int screenWidth();
        int screenHeight();
        int titleTextX();
        int accentColor();
        Font textRenderer();
        List<String> availablePresets();
        String activePresetName();
        boolean isPointInRect(int mouseX, int mouseY, int x, int y, int width, int height);
        void openRenamePresetPopup(String presetName);
        void openPresetDeletePopup(String presetName);
        void openCreatePresetPopup();
        void beginPresetDropdownDrag(String presetName, int mouseX, int mouseY);
    }

    private final Host host;
    private final AnimatedValue animation = AnimatedValue.forHover();
    private final DropdownLayoutHelper.SmoothScrollState smoothScroll = new DropdownLayoutHelper.SmoothScrollState();
    private boolean open = false;
    private int scrollOffset = 0;

    PathmindPresetDropdownController(Host host) {
        this.host = host;
    }

    boolean isOpen() {
        return open;
    }

    void close() {
        open = false;
    }

    void toggle() {
        open = !open;
    }

    int getX() {
        int preferredX = host.titleTextX() + PRESET_MENU_BUTTON_SIZE - PRESET_DROPDOWN_WIDTH;
        return Mth.clamp(preferredX, PRESET_DROPDOWN_MARGIN, host.screenWidth() - PRESET_DROPDOWN_WIDTH - PRESET_DROPDOWN_MARGIN);
    }

    int getY() {
        return TAB_BAR_TOP + TAB_HEIGHT + 2;
    }

    void render(GuiGraphics context, int mouseX, int mouseY, boolean disabled) {
        int dropdownX = getX();
        int dropdownY = getY();

        if (disabled && open) {
            open = false;
        }

        float animProgress = DropdownLayoutHelper.updateOpenAnimation(animation, open);
        if (animProgress <= 0.001f) {
            return;
        }

        List<String> availablePresets = host.availablePresets();
        String activePresetName = host.activePresetName();
        int optionStartY = dropdownY;
        int optionCount = availablePresets.size() + 1;
        DropdownLayoutHelper.Layout layout = getLayout(optionStartY);
        scrollOffset = Mth.clamp(scrollOffset, 0, layout.maxScrollOffset);
        int fullOptionsHeight = layout.height;
        int animatedHeight = DropdownLayoutHelper.getRevealHeight(fullOptionsHeight, animProgress);

        context.enableScissor(dropdownX, optionStartY, dropdownX + PRESET_DROPDOWN_WIDTH, optionStartY + animatedHeight + 1);

        UIStyleHelper.drawScrollContainer(context, dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, fullOptionsHeight,
            UIStyleHelper.getScrollContainerPalette(host.accentColor(), animProgress, true, false));

        float smoothScrollOffset = DropdownLayoutHelper.updateSmoothScroll(smoothScroll, scrollOffset, layout.maxScrollOffset);
        DropdownLayoutHelper.ScrollWindow scrollWindow = DropdownLayoutHelper.getSmoothScrollWindow(
            smoothScrollOffset,
            layout.visibleCount,
            optionCount,
            PRESET_OPTION_HEIGHT
        );
        for (int index = scrollWindow.firstIndex; index < scrollWindow.endIndex; index++) {
            int optionY = optionStartY + (index - scrollWindow.firstIndex) * PRESET_OPTION_HEIGHT + scrollWindow.pixelOffset;
            if (index < availablePresets.size()) {
                String preset = availablePresets.get(index);
                boolean optionHovered = animProgress >= 1f && host.isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                UIStyleHelper.DropdownRowPalette rowPalette = UIStyleHelper.getDropdownRowPalette(host.accentColor(), optionHovered ? 1f : 0f, preset.equals(activePresetName), false);
                UIStyleHelper.drawDropdownRow(context, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1, rowPalette);
                int textColor = preset.equals(activePresetName) ? host.accentColor() : UITheme.TEXT_PRIMARY;
                int textX = dropdownX + PRESET_TEXT_LEFT_PADDING;
                int iconSpace = PRESET_DELETE_ICON_SIZE
                    + PRESET_DELETE_ICON_MARGIN
                    + PRESET_TEXT_ICON_GAP
                    + PRESET_RENAME_ICON_SIZE
                    + PRESET_TEXT_ICON_GAP;
                int textMaxWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING - iconSpace;
                String presetLabel = TextRenderUtil.trimWithEllipsis(host.textRenderer(), preset, textMaxWidth);
                context.drawString(host.textRenderer(), Component.literal(presetLabel), textX, optionY + 5, textColor);

                boolean renameDisabled = isPresetRenameDisabled(preset);
                int renameLeft = getRenameIconLeft(dropdownX);
                int renameTop = getRenameIconTop(optionY);
                boolean renameHovered = animProgress >= 1f && !renameDisabled && isPointInRenameIcon(mouseX, mouseY, optionY, dropdownX);
                if (renameHovered) {
                    context.fill(renameLeft - PRESET_RENAME_ICON_HITBOX_PADDING,
                        renameTop - PRESET_RENAME_ICON_HITBOX_PADDING,
                        renameLeft + PRESET_RENAME_ICON_SIZE + PRESET_RENAME_ICON_HITBOX_PADDING,
                        renameTop + PRESET_RENAME_ICON_SIZE + PRESET_RENAME_ICON_HITBOX_PADDING,
                        UITheme.ICON_HITBOX_HOVER_BG);
                }
                int renameColor = renameDisabled
                    ? UITheme.DROPDOWN_ACTION_DISABLED
                    : renameHovered ? host.accentColor() : UITheme.TEXT_SECONDARY;
                PathmindIconRenderer.drawPencil(context, renameLeft, renameTop, PRESET_RENAME_ICON_SIZE, renameColor);

                boolean deleteDisabled = isPresetDeleteDisabled(preset);
                int deleteLeft = getDeleteIconLeft(dropdownX);
                int deleteTop = getDeleteIconTop(optionY);
                boolean deleteHovered = animProgress >= 1f && !deleteDisabled && isPointInDeleteIcon(mouseX, mouseY, optionY, dropdownX);
                if (deleteHovered) {
                    context.fill(deleteLeft - PRESET_DELETE_ICON_HITBOX_PADDING,
                        deleteTop - PRESET_DELETE_ICON_HITBOX_PADDING,
                        deleteLeft + PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING,
                        deleteTop + PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING,
                        UITheme.ICON_HITBOX_HOVER_BG);
                }
                int deleteColor = deleteDisabled
                    ? UITheme.DROPDOWN_ACTION_DISABLED
                    : deleteHovered ? host.accentColor() : UITheme.TEXT_SECONDARY;
                PathmindIconRenderer.drawTrash(context, deleteLeft, deleteTop, PRESET_DELETE_ICON_SIZE, deleteColor);
            } else {
                context.hLine(dropdownX + 1, dropdownX + PRESET_DROPDOWN_WIDTH - 2, optionY, UITheme.BORDER_SUBTLE);
                boolean createHovered = animProgress >= 1f && host.isPointInRect(mouseX, mouseY, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1);
                UIStyleHelper.DropdownRowPalette createPalette = UIStyleHelper.getDropdownRowPalette(host.accentColor(), createHovered ? 1f : 0f, false, false);
                UIStyleHelper.drawDropdownRow(context, dropdownX + 1, optionY + 1, PRESET_DROPDOWN_WIDTH - 2, PRESET_OPTION_HEIGHT - 1, createPalette);
                int createTextWidth = PRESET_DROPDOWN_WIDTH - PRESET_TEXT_LEFT_PADDING * 2;
                String createLabel = TextRenderUtil.trimWithEllipsis(host.textRenderer(), Component.translatable("pathmind.preset.createNew").getString(), createTextWidth);
                context.drawString(host.textRenderer(), Component.literal(createLabel), dropdownX + PRESET_TEXT_LEFT_PADDING, optionY + 5, host.accentColor());
            }
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            dropdownX,
            optionStartY,
            PRESET_DROPDOWN_WIDTH,
            fullOptionsHeight,
            optionCount,
            layout.visibleCount,
            Math.round(smoothScrollOffset),
            layout.maxScrollOffset,
            UITheme.BORDER_DEFAULT,
            UITheme.BORDER_HIGHLIGHT
        );
        DropdownLayoutHelper.drawOutline(
            context,
            dropdownX,
            optionStartY,
            PRESET_DROPDOWN_WIDTH,
            fullOptionsHeight,
            UITheme.BORDER_DEFAULT
        );

        context.disableScissor();
    }

    boolean handleMouseDown(double mouseX, double mouseY) {
        int dropdownX = getX();
        int optionStartY = getY();
        DropdownLayoutHelper.Layout layout = getLayout(optionStartY);
        scrollOffset = Mth.clamp(scrollOffset, 0, layout.maxScrollOffset);
        int optionsHeight = layout.height;
        if (!host.isPointInRect((int) mouseX, (int) mouseY, dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, optionsHeight)) {
            return false;
        }

        List<String> availablePresets = host.availablePresets();
        int relativeY = (int) mouseY - optionStartY;
        int index = scrollOffset + (relativeY / PRESET_OPTION_HEIGHT);
        if (index < availablePresets.size()) {
            if (index >= 0) {
                String selectedPreset = availablePresets.get(index);
                int optionTop = optionStartY + (index - scrollOffset) * PRESET_OPTION_HEIGHT;
                if (isPointInRenameIcon((int) mouseX, (int) mouseY, optionTop, dropdownX)) {
                    if (!isPresetRenameDisabled(selectedPreset)) {
                        host.openRenamePresetPopup(selectedPreset);
                    }
                    return true;
                }
                if (isPointInDeleteIcon((int) mouseX, (int) mouseY, optionTop, dropdownX)) {
                    if (!isPresetDeleteDisabled(selectedPreset)) {
                        host.openPresetDeletePopup(selectedPreset);
                    }
                    return true;
                }
                host.beginPresetDropdownDrag(selectedPreset, (int) mouseX, (int) mouseY);
                return true;
            }
        } else if (index == availablePresets.size()) {
            open = false;
            host.openCreatePresetPopup();
            return true;
        }

        open = false;
        return true;
    }

    boolean handleScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!open) {
            return false;
        }
        int dropdownX = getX();
        int optionStartY = getY();
        DropdownLayoutHelper.Layout layout = getLayout(optionStartY);
        if (!host.isPointInRect((int) mouseX, (int) mouseY, dropdownX, optionStartY, PRESET_DROPDOWN_WIDTH, layout.height)) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta != 0) {
            scrollOffset = Mth.clamp(scrollOffset - delta, 0, layout.maxScrollOffset);
        }
        return true;
    }

    private DropdownLayoutHelper.Layout getLayout(int optionStartY) {
        int optionCount = host.availablePresets().size() + 1;
        int visibleCount = Math.min(optionCount, 10);
        return DropdownLayoutHelper.calculate(optionCount, PRESET_OPTION_HEIGHT, visibleCount, optionStartY, host.screenHeight());
    }

    private int getDeleteIconLeft(int dropdownX) {
        return dropdownX + PRESET_DROPDOWN_WIDTH - PRESET_DELETE_ICON_MARGIN - PRESET_DELETE_ICON_SIZE;
    }

    private int getDeleteIconTop(int optionTop) {
        return optionTop + (PRESET_OPTION_HEIGHT - PRESET_DELETE_ICON_SIZE) / 2;
    }

    private int getRenameIconLeft(int dropdownX) {
        return getDeleteIconLeft(dropdownX) - PRESET_TEXT_ICON_GAP - PRESET_RENAME_ICON_SIZE;
    }

    private int getRenameIconTop(int optionTop) {
        return optionTop + (PRESET_OPTION_HEIGHT - PRESET_RENAME_ICON_SIZE) / 2;
    }

    private boolean isPointInDeleteIcon(int mouseX, int mouseY, int optionTop, int dropdownX) {
        int iconLeft = getDeleteIconLeft(dropdownX);
        int iconTop = getDeleteIconTop(optionTop);
        int hitboxSize = PRESET_DELETE_ICON_SIZE + PRESET_DELETE_ICON_HITBOX_PADDING * 2;
        return host.isPointInRect(mouseX, mouseY, iconLeft - PRESET_DELETE_ICON_HITBOX_PADDING, iconTop - PRESET_DELETE_ICON_HITBOX_PADDING, hitboxSize, hitboxSize);
    }

    private boolean isPointInRenameIcon(int mouseX, int mouseY, int optionTop, int dropdownX) {
        int iconLeft = getRenameIconLeft(dropdownX);
        int iconTop = getRenameIconTop(optionTop);
        int hitboxSize = PRESET_RENAME_ICON_SIZE + PRESET_RENAME_ICON_HITBOX_PADDING * 2;
        return host.isPointInRect(mouseX, mouseY, iconLeft - PRESET_RENAME_ICON_HITBOX_PADDING, iconTop - PRESET_RENAME_ICON_HITBOX_PADDING, hitboxSize, hitboxSize);
    }

    private boolean isPresetDeleteDisabled(String presetName) {
        if (presetName == null) {
            return true;
        }
        return presetName.equalsIgnoreCase(PresetManager.getDefaultPresetName());
    }

    private boolean isPresetRenameDisabled(String presetName) {
        return isPresetDeleteDisabled(presetName);
    }
}
