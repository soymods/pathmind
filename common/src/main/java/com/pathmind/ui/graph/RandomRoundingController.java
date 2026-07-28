package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.control.PathmindDropdownRenderer;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Owns the random-rounding field, toggle, and dropdown lifecycle.
 */
final class RandomRoundingController {
    private static final int DROPDOWN_MAX_ROWS = 4;
    private static final int DROPDOWN_ROW_HEIGHT = 16;
    private static final int DROPDOWN_SIDE_PADDING = 6;
    private static final int DROPDOWN_SCROLLBAR_ALLOWANCE = 8;

    interface Host {
        int cameraX();
        int cameraY();
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        int guiScaledHeight();
        Font clientTextRenderer();
        float zoomScale();
        boolean compactViewportMode();
        boolean shouldRenderNodeText();
        int selectedNodeAccentColor();
        String translate(String key);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        UIStyleHelper.FieldPalette nodeInputPalette(
            boolean isOverSidebar, int accentColor, float progress, boolean active, boolean disabled
        );
        UIStyleHelper.FieldPalette lowDetailAwareFieldPalette(
            int backgroundColor, int borderColor, int innerBorderColor, int textColor,
            int placeholderColor, boolean isOverSidebar
        );
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
        void renderToggle(
            GuiGraphics context, Node node, int left, int top, int width, int height,
            boolean enabled, boolean isOverSidebar
        );
        float dropdownAnimationProgress(AnimatedValue animation, boolean open);
        void animateToggle(Node node, boolean enabled);
        void stopParameterEditing();
        void notifyNodeParametersChanged(Node node);
    }

    private final Host host;
    private final AnimatedValue dropdownAnimation = AnimatedValue.forHover();
    private Node dropdownNode;
    private boolean dropdownOpen;
    private int dropdownHoverIndex = -1;
    private int dropdownScrollOffset;

    RandomRoundingController(Host host) {
        this.host = host;
    }

    void renderField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar) {
        int baseLabelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        boolean enabled = node.isRandomRoundingEnabled();
        boolean open = dropdownOpen && dropdownNode == node;

        int labelTop = node.getRandomRoundingFieldLabelTop() - host.cameraY();
        int labelHeight = node.getRandomRoundingFieldLabelHeight();
        int fieldTop = node.getRandomRoundingFieldInputTop() - host.cameraY();
        int fieldHeight = node.getRandomRoundingFieldHeight();
        int fieldLeft = node.getRandomRoundingFieldLeft() - host.cameraX();
        int fieldWidth = node.getRandomRoundingFieldWidth();
        int fieldRight = fieldLeft + fieldWidth;

        int labelY = labelTop + Math.max(0, (labelHeight - textRenderer.lineHeight) / 2);
        host.drawNodeText(
            context,
            textRenderer,
            Component.translatable("pathmind.field.rounding"),
            fieldLeft + 2,
            labelY,
            baseLabelColor
        );

        int disabledBg = isOverSidebar ? UITheme.BACKGROUND_TERTIARY
            : (host.compactViewportMode() ? UITheme.BACKGROUND_SECONDARY : UITheme.BUTTON_DEFAULT_BG);
        UIStyleHelper.FieldPalette palette = host.nodeInputPalette(
            isOverSidebar, host.selectedNodeAccentColor(), open ? 1f : 0f, open, !enabled
        );
        int valueColor = enabled ? textColor : UITheme.TEXT_SECONDARY;

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            enabled
                ? palette
                : host.lowDetailAwareFieldPalette(
                    disabledBg,
                    isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT,
                    UITheme.PANEL_INNER_BORDER,
                    palette.textColor(),
                    palette.placeholderColor(),
                    isOverSidebar
                )
        );

        String value = node.getRandomRoundingModeDisplay();
        int arrowCenterX = fieldRight - 7;
        int valueStartX = fieldLeft + 4;
        int maxValueWidth = Math.max(0, arrowCenterX - 5 - valueStartX);
        String display = host.trimTextToWidth(value, textRenderer, maxValueWidth);
        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        host.drawNodeText(
            context, textRenderer, Component.literal(display), valueStartX, textY, valueColor
        );
        UIStyleHelper.drawChevron(
            context, arrowCenterX, fieldTop + fieldHeight / 2, open, valueColor
        );

        if (node.hasRandomRoundingToggle()) {
            int toggleLeft = node.getRandomRoundingToggleLeft() - host.cameraX();
            int toggleTop = node.getRandomRoundingToggleTop() - host.cameraY();
            int toggleWidth = node.getRandomRoundingToggleWidth();
            int toggleHeight = node.getRandomRoundingToggleHeight();
            host.renderToggle(
                context, node, toggleLeft, toggleTop, toggleWidth, toggleHeight, enabled, isOverSidebar
            );
        }
    }

    void renderDropdown(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        float animProgress = host.dropdownAnimationProgress(dropdownAnimation, dropdownOpen);
        if (dropdownNode == null) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearState();
            return;
        }
        Node node = dropdownNode;
        List<ParameterDropdownOption> options = getOptions();
        int optionCount = Math.max(1, options.size());
        float zoom = host.zoomScale();
        int transformedMouseX = Math.round(mouseX / zoom);
        int transformedMouseY = Math.round(mouseY / zoom);

        int dropdownWidth = getDropdownWidth(node, textRenderer);
        int listTop = node.getRandomRoundingFieldInputTop()
            + node.getRandomRoundingFieldHeight() + 2 - host.cameraY();
        int listLeft = node.getRandomRoundingFieldLeft() - host.cameraX();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            optionCount,
            DROPDOWN_ROW_HEIGHT,
            DROPDOWN_MAX_ROWS,
            listTop,
            host.guiScaledHeight()
        );
        int accentColor = host.selectedNodeAccentColor();
        UIStyleHelper.ScrollContainerPalette containerPalette =
            UIStyleHelper.getScrollContainerPalette(accentColor, animProgress, true, false);

        dropdownScrollOffset = Mth.clamp(dropdownScrollOffset, 0, layout.maxScrollOffset);
        dropdownHoverIndex = PathmindDropdownRenderer.renderTextList(
            context,
            textRenderer,
            PathmindDropdownRenderer.TextListSpec.builder()
                .bounds(listLeft, listTop, dropdownWidth)
                .rows(DROPDOWN_ROW_HEIGHT, layout.visibleCount, options.size())
                .scroll(
                    dropdownScrollOffset,
                    layout.maxScrollOffset,
                    DROPDOWN_SCROLLBAR_ALLOWANCE
                )
                .animation(animProgress)
                .hoverPoint(transformedMouseX, transformedMouseY)
                .colors(accentColor, UITheme.TEXT_PRIMARY)
                .textLayout(5, 4, false, host.shouldRenderNodeText())
                .labels(host.translate("pathmind.dropdown.noOptions"), index -> options.get(index).label())
                .chrome(
                    containerPalette,
                    containerPalette.trackColor(),
                    containerPalette.thumbColor(),
                    containerPalette.borderColor()
                )
                .build()
        );
    }

    boolean handleScroll(double screenX, double screenY, double verticalAmount) {
        if (!dropdownOpen || dropdownNode == null) {
            return false;
        }
        if (!isPointInsideDropdownList((int) screenX, (int) screenY)) {
            return false;
        }
        int listTop = dropdownNode.getRandomRoundingFieldInputTop()
            + dropdownNode.getRandomRoundingFieldHeight() + 2;
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            getOptions().size(),
            DROPDOWN_ROW_HEIGHT,
            DROPDOWN_MAX_ROWS,
            listTop,
            host.guiScaledHeight()
        );
        if (layout.maxScrollOffset <= 0) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        dropdownScrollOffset = Mth.clamp(
            dropdownScrollOffset - delta,
            0,
            layout.maxScrollOffset
        );
        return true;
    }

    boolean handleToggleClick(Node node, int mouseX, int mouseY) {
        if (!isPointInsideToggle(node, mouseX, mouseY)) {
            return false;
        }
        boolean newState = !node.isRandomRoundingEnabled();
        node.setRandomRoundingEnabled(newState);
        host.animateToggle(node, newState);
        if (!newState && dropdownOpen && dropdownNode == node) {
            close();
        }
        node.recalculateDimensions();
        host.notifyNodeParametersChanged(node);
        return true;
    }

    boolean handleDropdownClick(Node node, int mouseX, int mouseY) {
        if (dropdownOpen) {
            if (node == null || node != dropdownNode) {
                if (isPointInsideDropdownList(mouseX, mouseY)) {
                    applySelection(dropdownNode, mouseX, mouseY);
                    close();
                    return true;
                }
                close();
                return false;
            }
            if (isPointInsideField(node, mouseX, mouseY)) {
                close();
                return true;
            }
            if (isPointInsideDropdownList(mouseX, mouseY)) {
                applySelection(node, mouseX, mouseY);
                close();
                return true;
            }
            close();
            return false;
        }

        if (node == null || !node.hasRandomRoundingField()) {
            return false;
        }
        if (!isPointInsideField(node, mouseX, mouseY)) {
            return false;
        }
        host.stopParameterEditing();
        open(node);
        return true;
    }

    boolean isPointInsideField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasRandomRoundingField()) {
            return false;
        }
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        int fieldLeft = node.getRandomRoundingFieldLeft();
        int fieldTop = node.getRandomRoundingFieldInputTop();
        int fieldWidth = node.getRandomRoundingFieldWidth();
        int fieldHeight = node.getRandomRoundingFieldHeight();
        return worldX >= fieldLeft && worldX <= fieldLeft + fieldWidth
            && worldY >= fieldTop && worldY <= fieldTop + fieldHeight;
    }

    boolean isPointInsideToggle(Node node, int screenX, int screenY) {
        if (node == null || !node.hasRandomRoundingToggle()) {
            return false;
        }
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        int left = node.getRandomRoundingToggleLeft() - 3;
        int top = node.getRandomRoundingToggleTop() - 3;
        int width = node.getRandomRoundingToggleWidth() + 6;
        int height = node.getRandomRoundingToggleHeight() + 6;
        return worldX >= left && worldX <= left + width
            && worldY >= top && worldY <= top + height;
    }

    void close() {
        dropdownOpen = false;
        dropdownHoverIndex = -1;
    }

    private void open(Node node) {
        dropdownNode = node;
        dropdownOpen = true;
        dropdownScrollOffset = 0;
        dropdownHoverIndex = -1;
    }

    private void clearState() {
        if (dropdownOpen) {
            return;
        }
        dropdownNode = null;
        dropdownHoverIndex = -1;
        dropdownScrollOffset = 0;
    }

    private void applySelection(Node node, int mouseX, int mouseY) {
        int index = getDropdownIndexAt(node, mouseY);
        if (index >= 0) {
            List<ParameterDropdownOption> options = getOptions();
            if (index < options.size()) {
                node.setRandomRoundingMode(options.get(index).value());
                node.recalculateDimensions();
                host.notifyNodeParametersChanged(node);
            }
        }
    }

    private boolean isPointInsideDropdownList(int screenX, int screenY) {
        if (!dropdownOpen || dropdownNode == null) {
            return false;
        }
        Node node = dropdownNode;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        int listTopScreen = node.getRandomRoundingFieldInputTop()
            + node.getRandomRoundingFieldHeight() + 2 - host.cameraY();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            getOptions().size(),
            DROPDOWN_ROW_HEIGHT,
            DROPDOWN_MAX_ROWS,
            listTopScreen,
            host.guiScaledHeight()
        );
        int listLeft = node.getRandomRoundingFieldLeft();
        int listWidth = getDropdownWidth(node, null);
        int worldListTop = node.getRandomRoundingFieldInputTop()
            + node.getRandomRoundingFieldHeight() + 2;
        return worldX >= listLeft && worldX <= listLeft + listWidth
            && worldY >= worldListTop && worldY <= worldListTop + layout.height;
    }

    private int getDropdownIndexAt(Node node, int screenY) {
        if (node == null) {
            return -1;
        }
        List<ParameterDropdownOption> options = getOptions();
        if (options.isEmpty()) {
            return -1;
        }
        int worldY = host.screenToWorldY(screenY);
        int worldListTop = node.getRandomRoundingFieldInputTop()
            + node.getRandomRoundingFieldHeight() + 2;
        int listTopScreen = node.getRandomRoundingFieldInputTop()
            + node.getRandomRoundingFieldHeight() + 2 - host.cameraY();
        DropdownLayoutHelper.Layout layout = DropdownLayoutHelper.calculate(
            options.size(),
            DROPDOWN_ROW_HEIGHT,
            DROPDOWN_MAX_ROWS,
            listTopScreen,
            host.guiScaledHeight()
        );
        int row = (worldY - worldListTop) / DROPDOWN_ROW_HEIGHT;
        if (row < 0 || row >= layout.visibleCount) {
            return -1;
        }
        int index = dropdownScrollOffset + row;
        if (index < 0 || index >= options.size()) {
            return -1;
        }
        return index;
    }

    private int getDropdownWidth(Node node, Font suppliedRenderer) {
        Font textRenderer = suppliedRenderer;
        if (textRenderer == null) {
            textRenderer = host.clientTextRenderer();
        }
        if (textRenderer == null || node == null) {
            return node != null ? node.getRandomRoundingFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.width(host.translate("pathmind.dropdown.noOptions"));
        for (ParameterDropdownOption option : getOptions()) {
            if (option != null && option.label() != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.width(option.label()));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private List<ParameterDropdownOption> getOptions() {
        List<ParameterDropdownOption> options = new ArrayList<>(3);
        options.add(new ParameterDropdownOption(host.translate("pathmind.option.round"), "round"));
        options.add(new ParameterDropdownOption(host.translate("pathmind.option.floor"), "floor"));
        options.add(new ParameterDropdownOption(host.translate("pathmind.option.ceil"), "ceil"));
        return options;
    }
}
