package com.pathmind.ui.graph;

import com.pathmind.nodes.Node;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DropdownLayoutHelper;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Generic controller for the scrollable option dropdowns that hang off a node field
 * (mode selector, schematic picker, run-preset picker, ...). It owns the open/scroll/hover
 * state plus the shared list geometry, hit-testing and rendering; per-dropdown behaviour
 * (where the anchor field sits, what the options are, what a selection does) is supplied
 * as functions at construction time.
 *
 * <p>The controller stays "downward-only": it never reads NodeGraph state directly, only the
 * rendering/coordinate primitives exposed through {@link Host}, so migrating each dropdown is a
 * self-contained change.
 */
public final class DropdownController<T> {

    private static final int DROPDOWN_SIDE_PADDING = 6;
    private static final int DROPDOWN_SCROLLBAR_ALLOWANCE = 8;

    /** One selectable row: the visible label plus the value applied when it is picked. */
    public record Option<T>(String label, T value) {}

    /** Anchor field rectangle in UI (camera-adjusted, pre-zoom) space. */
    public record Rect(int x, int y, int width, int height) {}

    /** Rendering and coordinate primitives the controller borrows from the host editor. */
    public interface Host {
        float getZoomScale();
        int screenToUiX(int screenX);
        int screenToUiY(int screenY);
        int getDropdownRowHeight();
        int getSelectedNodeAccentColor();
        Font getTextRenderer();
        int getGuiScaledHeight();
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
        void enableDropdownScissor(GuiGraphics context, int x, int y, int width, int height);
        float dropdownAnimationProgress(AnimatedValue animation, boolean open);
    }

    private final Host host;
    private final AnimatedValue animation;
    private final int maxRows;
    private final Supplier<String> emptyPlaceholder;
    private final Function<Node, Rect> anchor;
    private final Function<Node, List<Option<T>>> optionsFor;
    private final BiConsumer<Node, T> applySelection;

    private final List<Option<T>> options = new ArrayList<>();
    private Node node = null;
    private boolean open = false;
    private int hoverIndex = -1;
    private int scrollOffset = 0;
    private int fieldX = 0;
    private int fieldY = 0;
    private int fieldWidth = 0;
    private int fieldHeight = 0;

    public DropdownController(Host host,
                             AnimatedValue animation,
                             int maxRows,
                             Supplier<String> emptyPlaceholder,
                             Function<Node, Rect> anchor,
                             Function<Node, List<Option<T>>> optionsFor,
                             BiConsumer<Node, T> applySelection) {
        this.host = host;
        this.animation = animation;
        this.maxRows = maxRows;
        this.emptyPlaceholder = emptyPlaceholder;
        this.anchor = anchor;
        this.optionsFor = optionsFor;
        this.applySelection = applySelection;
    }

    public boolean isOpen() {
        return open;
    }

    public Node getNode() {
        return node;
    }

    public void open(Node node) {
        if (node == null) {
            return;
        }
        this.node = node;
        this.scrollOffset = 0;
        this.hoverIndex = -1;
        applyAnchor();
        options.clear();
        options.addAll(optionsFor.apply(node));
        open = true;
    }

    public void close() {
        open = false;
        hoverIndex = -1;
    }

    /** Clears the retained node/options once the close animation has finished. */
    public void clearStateIfClosed() {
        if (open) {
            return;
        }
        node = null;
        hoverIndex = -1;
        scrollOffset = 0;
        options.clear();
    }

    public boolean handleClick(double screenX, double screenY) {
        if (!open) {
            return false;
        }
        refreshAnchor();
        int x = (int) screenX;
        int y = (int) screenY;
        if (isPointInsideList(x, y)) {
            int index = indexAt(x, y);
            if (index >= 0) {
                select(index);
            }
            return true;
        }
        int transformedX = host.screenToUiX(x);
        int transformedY = host.screenToUiY(y);
        if (transformedX >= fieldX && transformedX <= fieldX + fieldWidth
            && transformedY >= fieldY && transformedY <= fieldY + fieldHeight) {
            close();
            return true;
        }
        close();
        return false;
    }

    public boolean handleScroll(double screenX, double screenY, double verticalAmount) {
        if (!open) {
            return false;
        }
        refreshAnchor();
        if (!isPointInsideList((int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = layout();
        if (layout.maxScrollOffset <= 0) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        scrollOffset = Mth.clamp(scrollOffset - delta, 0, layout.maxScrollOffset);
        return true;
    }

    public void render(GuiGraphics context, Font textRenderer, int mouseX, int mouseY) {
        float animProgress = host.dropdownAnimationProgress(animation, open);
        if (node == null) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearStateIfClosed();
            return;
        }
        refreshAnchor();
        List<Option<T>> options = this.options;
        int optionCount = Math.max(1, options.size());
        float zoom = host.getZoomScale();
        int transformedMouseX = Math.round(mouseX / zoom);
        int transformedMouseY = Math.round(mouseY / zoom);

        int rowHeight = host.getDropdownRowHeight();
        int dropdownWidth = dropdownWidth();
        DropdownLayoutHelper.Layout layout = layout();
        int listTop = listTop();
        int listLeft = fieldX;
        int listRight = listLeft + dropdownWidth;
        int listHeight = layout.height;
        int listBottom = listTop + listHeight;
        int animatedHeight = Math.max(1, (int) (listHeight * animProgress));
        int accentColor = host.getSelectedNodeAccentColor();
        UIStyleHelper.ScrollContainerPalette containerPalette =
            UIStyleHelper.getScrollContainerPalette(accentColor, animProgress, true, false);

        host.enableDropdownScissor(context, listLeft, listTop, dropdownWidth, animatedHeight);
        UIStyleHelper.drawScrollContainer(context, listLeft, listTop, dropdownWidth, listHeight, containerPalette);

        scrollOffset = Mth.clamp(scrollOffset, 0, layout.maxScrollOffset);
        hoverIndex = -1;
        if (animProgress >= 1f
            && transformedMouseX >= listLeft && transformedMouseX <= listRight
            && transformedMouseY >= listTop && transformedMouseY <= listBottom) {
            int row = (transformedMouseY - listTop) / rowHeight;
            if (row >= 0 && row < layout.visibleCount) {
                hoverIndex = scrollOffset + row;
            }
        }

        int visibleCount = layout.visibleCount;
        for (int row = 0; row < visibleCount; row++) {
            int optionIndex = scrollOffset + row;
            String optionLabel = options.isEmpty() ? emptyPlaceholder.get() : options.get(optionIndex).label();
            int rowTop = listTop + row * rowHeight;
            boolean hovered = options.isEmpty() ? row == 0 && hoverIndex >= 0 : optionIndex == hoverIndex;
            UIStyleHelper.DropdownRowPalette rowPalette =
                UIStyleHelper.getDropdownRowPalette(accentColor, hovered ? 1f : 0f, false, false);
            if (hovered) {
                UIStyleHelper.drawDropdownRow(context, listLeft + 1, rowTop + 1, dropdownWidth - 2, rowHeight - 1, rowPalette);
            }
            int textPadding = 5;
            int maxTextWidth = dropdownWidth - (textPadding * 2) - DROPDOWN_SCROLLBAR_ALLOWANCE;
            String rowText = host.trimTextToWidth(optionLabel, textRenderer, Math.max(0, maxTextWidth));
            int textOffsetY = 4;
            host.drawNodeText(context, textRenderer, Component.literal(rowText), listLeft + textPadding, rowTop + textOffsetY,
                hovered ? rowPalette.textColor() : UITheme.TEXT_PRIMARY);
        }

        DropdownLayoutHelper.drawScrollBar(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            optionCount,
            layout.visibleCount,
            scrollOffset,
            layout.maxScrollOffset,
            containerPalette.trackColor(),
            containerPalette.thumbColor()
        );
        DropdownLayoutHelper.drawOutline(
            context,
            listLeft,
            listTop,
            dropdownWidth,
            listHeight,
            containerPalette.borderColor()
        );
        context.disableScissor();
    }

    private void select(int optionIndex) {
        if (!open || node == null || options.isEmpty()) {
            return;
        }
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        Option<T> option = options.get(optionIndex);
        if (option == null || option.value() == null) {
            return;
        }
        applySelection.accept(node, option.value());
        close();
    }

    private void applyAnchor() {
        Rect rect = anchor.apply(node);
        fieldX = rect.x();
        fieldY = rect.y();
        fieldWidth = rect.width();
        fieldHeight = rect.height();
    }

    private void refreshAnchor() {
        if (!open || node == null) {
            return;
        }
        applyAnchor();
    }

    private int listTop() {
        return fieldY + fieldHeight;
    }

    private int dropdownWidth() {
        Font textRenderer = host.getTextRenderer();
        if (textRenderer == null) {
            return fieldWidth;
        }
        int longestLabelWidth = textRenderer.width(emptyPlaceholder.get());
        for (Option<T> option : options) {
            if (option != null && option.label() != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.width(option.label()));
            }
        }
        return longestLabelWidth + DROPDOWN_SIDE_PADDING * 2 + DROPDOWN_SCROLLBAR_ALLOWANCE;
    }

    private DropdownLayoutHelper.Layout layout() {
        int optionCount = Math.max(1, options.size());
        float zoom = Math.max(0.01f, host.getZoomScale());
        int transformedScreenHeight = Math.round(host.getGuiScaledHeight() / zoom);
        int rowHeight = host.getDropdownRowHeight();
        return DropdownLayoutHelper.calculate(optionCount, rowHeight, maxRows, listTop(), transformedScreenHeight);
    }

    private boolean isPointInsideList(int screenX, int screenY) {
        if (!open) {
            return false;
        }
        refreshAnchor();
        float zoom = host.getZoomScale();
        int transformedX = Math.round(screenX / zoom);
        int transformedY = Math.round(screenY / zoom);
        int dropdownWidth = dropdownWidth();
        DropdownLayoutHelper.Layout layout = layout();
        int listTop = listTop();
        int listLeft = fieldX;
        return transformedX >= listLeft && transformedX <= listLeft + dropdownWidth
            && transformedY >= listTop && transformedY <= listTop + layout.height;
    }

    private int indexAt(int screenX, int screenY) {
        if (!open) {
            return -1;
        }
        refreshAnchor();
        float zoom = host.getZoomScale();
        int transformedY = Math.round(screenY / zoom);
        int rowHeight = host.getDropdownRowHeight();
        DropdownLayoutHelper.Layout layout = layout();
        int listTop = listTop();
        int row = (transformedY - listTop) / rowHeight;
        if (row < 0 || row >= layout.visibleCount) {
            return -1;
        }
        if (options.isEmpty()) {
            return -1;
        }
        return scrollOffset + row;
    }
}
