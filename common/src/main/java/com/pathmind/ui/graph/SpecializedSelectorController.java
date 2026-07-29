package com.pathmind.ui.graph;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.animation.HoverAnimator;
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
 * Owns schematic and run-preset selector state and interaction. Rendering reads
 * the exposed immutable state while all graph mutations flow through the host.
 */
final class SpecializedSelectorController {
    private static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 16;
    private static final int SIDE_PADDING = 6;
    private static final int SCROLLBAR_ALLOWANCE = 8;

    interface Host {
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        int cameraX();
        int cameraY();
        int guiScaledHeight();
        float zoomScale();
        Font clientTextRenderer();
        boolean compactViewportMode();
        boolean shouldRenderNodeText();
        int selectedNodeAccentColor();
        int toGrayscale(int color, float brightnessFactor);
        String translate(String key);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
        List<String> loadSchematicOptions();
        boolean schematicExists(String name);
        boolean isPresetSelectorNode(Node node);
        String stopTargetParameterKey(Node node);
        boolean isEditingStopTargetField();
        Node stopTargetEditingNode();
        InlineTextEditor stopTargetEditor();
        void prepareRunPresetOpen(Node node);
        void applySchematicSelection(Node node, String value);
        void applyRunPresetSelection(Node node, String value);
    }

    private final Host host;
    private Node schematicNode;
    private boolean schematicOpen;
    private List<String> schematicOptions = new ArrayList<>();
    private int schematicScrollOffset;
    private int schematicHoverIndex = -1;
    private Node runPresetNode;
    private boolean runPresetOpen;
    private List<String> runPresetOptions = new ArrayList<>();
    private int runPresetScrollOffset;
    private int runPresetHoverIndex = -1;
    private final AnimatedValue schematicDropdownAnimation = AnimatedValue.forHover();
    private final AnimatedValue runPresetDropdownAnimation = AnimatedValue.forHover();

    SpecializedSelectorController(Host host) {
        this.host = host;
    }

    void renderSchematicField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                              int mouseX, int mouseY) {
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

        boolean open = isSchematicOpenFor(node);

        int labelTop = node.getSchematicFieldLabelTop() - host.cameraY();
        int labelHeight = node.getSchematicFieldLabelHeight();
        int fieldTop = node.getSchematicFieldInputTop() - host.cameraY();
        int fieldHeight = node.getSchematicFieldHeight();
        int fieldLeft = node.getSchematicFieldLeft() - host.cameraX();
        int fieldWidth = node.getSchematicFieldWidth();
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getSchematicFieldLeft()
            && worldMouseX <= node.getSchematicFieldLeft() + fieldWidth
            && worldMouseY >= node.getSchematicFieldInputTop()
            && worldMouseY <= node.getSchematicFieldInputTop() + fieldHeight;
        float hoverProgress = getAnimatedHoverProgress(node.getId() + "#schematicSelector", hovered || open);
        int accentColor = isOverSidebar
            ? host.toGrayscale(UITheme.SCHEMATIC_ACTIVE_BORDER, 0.8f)
            : UITheme.SCHEMATIC_ACTIVE_BORDER;
        UIStyleHelper.FieldPalette palette =
            UIStyleHelper.getDropdownFieldPalette(accentColor, hoverProgress, open, false);

        host.drawNodeText(context, textRenderer, Component.translatable("pathmind.field.schematic"),
            fieldLeft, labelTop + (labelHeight - textRenderer.lineHeight) / 2, labelColor);

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            new UIStyleHelper.FieldPalette(
                isOverSidebar ? UITheme.BACKGROUND_SECONDARY : palette.backgroundColor(),
                isOverSidebar ? UITheme.BORDER_SUBTLE : palette.borderColor(),
                isOverSidebar ? UITheme.PANEL_INNER_BORDER : palette.innerBorderColor(),
                palette.textColor(),
                palette.placeholderColor()
            )
        );

        NodeParameter schematicParam = node.getParameter("Schematic");
        String value = schematicParam != null ? schematicParam.getDisplayValue() : "";
        if (value != null && !value.isEmpty() && !host.schematicExists(value)) {
            value = "";
        }
        if (value == null || value.isEmpty()) {
            value = "schematic";
            textColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
        }

        String display = host.trimTextToWidth(value, textRenderer, fieldWidth - 16);
        int textX = fieldLeft + 3;
        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        int animatedTextColor = isOverSidebar ? textColor : palette.textColor();
        host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY,
            value.equals("schematic") ? textColor : animatedTextColor);

        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;
        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, animatedTextColor);
    }

    void renderPresetField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                           int mouseX, int mouseY) {
        int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int caretColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.CARET_COLOR;

        boolean editing = host.isEditingStopTargetField() && host.stopTargetEditingNode() == node;
        InlineTextEditor editor = host.stopTargetEditor();
        if (editing) {
            editor.updateCaretBlink();
        }
        boolean open = isRunPresetOpenFor(node);

        int fieldTop = node.getStopTargetFieldInputTop() - host.cameraY();
        int fieldHeight = node.getStopTargetFieldHeight();
        int fieldLeft = node.getStopTargetFieldLeft() - host.cameraX();
        int fieldWidth = node.getStopTargetFieldWidth();
        int fieldBottom = fieldTop + fieldHeight;
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        boolean hovered = !isOverSidebar
            && worldMouseX >= node.getStopTargetFieldLeft()
            && worldMouseX <= node.getStopTargetFieldLeft() + fieldWidth
            && worldMouseY >= node.getStopTargetFieldInputTop()
            && worldMouseY <= node.getStopTargetFieldInputTop() + fieldHeight;
        float hoverProgress = getAnimatedHoverProgress(node.getId() + "#presetSelector", hovered || open);
        int accentColor = isOverSidebar
            ? host.toGrayscale(host.selectedNodeAccentColor(), 0.8f)
            : host.selectedNodeAccentColor();
        UIStyleHelper.FieldPalette palette =
            UIStyleHelper.getDropdownFieldPalette(accentColor, hoverProgress, open, false);
        int animatedTextColor = isOverSidebar ? textColor : palette.textColor();

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            new UIStyleHelper.FieldPalette(
                isOverSidebar ? UITheme.BACKGROUND_SECONDARY : palette.backgroundColor(),
                isOverSidebar ? UITheme.BORDER_SUBTLE : palette.borderColor(),
                isOverSidebar ? UITheme.PANEL_INNER_BORDER : palette.innerBorderColor(),
                animatedTextColor,
                palette.placeholderColor()
            )
        );

        String inlineLabel = "Preset:";
        int labelX = fieldLeft + 4;
        int labelY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;
        host.drawNodeText(context, textRenderer, Component.literal(inlineLabel), labelX, labelY, labelColor);
        int valueTextX = labelX + textRenderer.width(inlineLabel) + 6;
        int maxValueWidth = Math.max(0, fieldLeft + fieldWidth - valueTextX - 16);

        String value;
        if (editing) {
            value = editor.getBuffer();
        } else {
            NodeParameter targetParam = node.getParameter(host.stopTargetParameterKey(node));
            value = targetParam != null ? targetParam.getStringValue() : "";
        }
        if (value == null) {
            value = "";
        }

        String display = value;
        int valueDrawColor = animatedTextColor;
        if (!editing && display.isEmpty()) {
            display = "preset";
            valueDrawColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.TEXT_SECONDARY;
        }
        display = editing ? display : host.trimTextToWidth(display, textRenderer, maxValueWidth);

        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2 + 1;
        if (editing && editor.hasSelection()) {
            int start = Mth.clamp(editor.getSelectionStart(), 0, display.length());
            int end = Mth.clamp(editor.getSelectionEnd(), 0, display.length());
            if (start != end) {
                int selectionStartX = valueTextX + textRenderer.width(display.substring(0, start));
                int selectionEndX = valueTextX + textRenderer.width(display.substring(0, end));
                context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        host.drawNodeText(context, textRenderer, Component.literal(display), valueTextX, textY, valueDrawColor);

        if (editing && editor.isCaretVisible()) {
            int caretIndex = Mth.clamp(editor.getCaretPosition(), 0, display.length());
            int caretX = valueTextX + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, fieldLeft + fieldWidth - 2);
            UIStyleHelper.drawTextCaret(context, caretX, fieldTop + 2, fieldBottom - 2, caretColor);
        }
        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;
        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, animatedTextColor);
    }

    void renderSchematicDropdown(GuiGraphics context, Font textRenderer, Node node,
                                 boolean isOverSidebar, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(schematicDropdownAnimation, schematicOpen);
        if (schematicNode != node) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearSchematicState();
            return;
        }

        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int optionCount = schematicOptions.isEmpty() ? 1 : schematicOptions.size();
        int listTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 - host.cameraY();
        DropdownLayoutHelper.Layout layout = schematicLayout(node, optionCount);
        int visibleCount = layout.visibleCount;
        schematicScrollOffset = Mth.clamp(schematicScrollOffset, 0, layout.maxScrollOffset);

        int dropdownWidth = getSchematicDropdownWidth(node);
        int listLeft = node.getSchematicFieldLeft() - host.cameraX();
        int accentColor = isOverSidebar
            ? host.toGrayscale(UITheme.SCHEMATIC_ACTIVE_BORDER, 0.8f)
            : UITheme.SCHEMATIC_ACTIVE_BORDER;
        UIStyleHelper.ScrollContainerPalette containerPalette =
            UIStyleHelper.getScrollContainerPalette(accentColor, animProgress, true, false);
        UIStyleHelper.ScrollContainerPalette adjustedPalette = new UIStyleHelper.ScrollContainerPalette(
            isOverSidebar ? UITheme.BACKGROUND_SECONDARY : containerPalette.backgroundColor(),
            isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor(),
            isOverSidebar ? UITheme.PANEL_INNER_BORDER : containerPalette.innerBorderColor(),
            containerPalette.trackColor(),
            containerPalette.thumbColor()
        );

        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        schematicHoverIndex = PathmindDropdownRenderer.renderTextList(
            context,
            textRenderer,
            PathmindDropdownRenderer.TextListSpec.builder()
                .bounds(listLeft, listTop, dropdownWidth)
                .rows(ROW_HEIGHT, visibleCount, schematicOptions.size())
                .scroll(schematicScrollOffset, layout.maxScrollOffset, SCROLLBAR_ALLOWANCE)
                .animation(animProgress)
                .hoverPoint(worldMouseX - host.cameraX(), worldMouseY - host.cameraY())
                .colors(accentColor, textColor)
                .textLayout(3, 4, false, host.shouldRenderNodeText())
                .labels(host.translate("pathmind.dropdown.noSchematicsFound"), schematicOptions::get)
                .chrome(
                    adjustedPalette,
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.trackColor(),
                    isOverSidebar ? UITheme.BORDER_HIGHLIGHT : containerPalette.thumbColor(),
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor()
                )
                .build()
        );
    }

    void renderRunPresetDropdown(GuiGraphics context, Font textRenderer, Node node,
                                 boolean isOverSidebar, int mouseX, int mouseY) {
        float animProgress = getDropdownAnimationProgress(runPresetDropdownAnimation, runPresetOpen);
        if (runPresetNode != node) {
            return;
        }
        if (animProgress <= 0.001f) {
            clearRunPresetState();
            return;
        }

        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;
        int optionCount = runPresetOptions.isEmpty() ? 1 : runPresetOptions.size();
        int listTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 - host.cameraY();
        DropdownLayoutHelper.Layout layout = runPresetLayout(node, optionCount);
        int visibleCount = layout.visibleCount;
        runPresetScrollOffset = Mth.clamp(runPresetScrollOffset, 0, layout.maxScrollOffset);

        int dropdownWidth = getRunPresetDropdownWidth(node);
        int listLeft = node.getStopTargetFieldLeft() - host.cameraX();
        int accentColor = isOverSidebar
            ? host.toGrayscale(host.selectedNodeAccentColor(), 0.8f)
            : host.selectedNodeAccentColor();
        UIStyleHelper.ScrollContainerPalette containerPalette =
            UIStyleHelper.getScrollContainerPalette(accentColor, animProgress, true, false);
        UIStyleHelper.ScrollContainerPalette adjustedPalette = new UIStyleHelper.ScrollContainerPalette(
            isOverSidebar ? UITheme.BACKGROUND_SECONDARY : containerPalette.backgroundColor(),
            isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor(),
            isOverSidebar ? UITheme.PANEL_INNER_BORDER : containerPalette.innerBorderColor(),
            containerPalette.trackColor(),
            containerPalette.thumbColor()
        );

        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        runPresetHoverIndex = PathmindDropdownRenderer.renderTextList(
            context,
            textRenderer,
            PathmindDropdownRenderer.TextListSpec.builder()
                .bounds(listLeft, listTop, dropdownWidth)
                .rows(ROW_HEIGHT, visibleCount, runPresetOptions.size())
                .scroll(runPresetScrollOffset, layout.maxScrollOffset, SCROLLBAR_ALLOWANCE)
                .animation(animProgress)
                .hoverPoint(worldMouseX - host.cameraX(), worldMouseY - host.cameraY())
                .colors(accentColor, textColor)
                .textLayout(3, 4, false, host.shouldRenderNodeText())
                .labels(host.translate("pathmind.dropdown.noPresetsFound"), runPresetOptions::get)
                .chrome(
                    adjustedPalette,
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.trackColor(),
                    isOverSidebar ? UITheme.BORDER_HIGHLIGHT : containerPalette.thumbColor(),
                    isOverSidebar ? UITheme.BORDER_SUBTLE : containerPalette.borderColor()
                )
                .build()
        );
    }

    boolean isSchematicOpenFor(Node node) { return schematicOpen && schematicNode == node; }

    boolean isRunPresetOpen() { return runPresetOpen; }
    boolean isRunPresetOpenFor(Node node) { return runPresetOpen && runPresetNode == node; }
    Node getRunPresetNode() { return runPresetNode; }

    boolean handleSchematicClick(Node clickedNode, int screenX, int screenY) {
        if (schematicOpen && schematicNode != null) {
            if (isPointInsideSchematicList(schematicNode, screenX, screenY)) {
                int index = getSchematicIndexAt(schematicNode, screenY);
                if (index >= 0 && index < schematicOptions.size()) {
                    host.applySchematicSelection(schematicNode, schematicOptions.get(index));
                }
                closeSchematic();
                return true;
            }
            if (isPointInsideSchematicField(schematicNode, screenX, screenY)) {
                closeSchematic();
                return true;
            }
            closeSchematic();
        }
        if (clickedNode != null && clickedNode.hasSchematicDropdownField()
            && isPointInsideSchematicField(clickedNode, screenX, screenY)) {
            openSchematic(clickedNode);
            return true;
        }
        return false;
    }

    boolean handleRunPresetClick(Node clickedNode, int screenX, int screenY) {
        if (runPresetOpen && runPresetNode != null) {
            if (isPointInsideRunPresetList(runPresetNode, screenX, screenY)) {
                int index = getRunPresetIndexAt(runPresetNode, screenY);
                if (index >= 0 && index < runPresetOptions.size()) {
                    host.applyRunPresetSelection(runPresetNode, runPresetOptions.get(index));
                }
                closeRunPreset();
                return true;
            }
            if (isPointInsideRunPresetField(runPresetNode, screenX, screenY)) {
                closeRunPreset();
                return true;
            }
            closeRunPreset();
        }
        if (host.isPresetSelectorNode(clickedNode)
            && isPointInsideRunPresetField(clickedNode, screenX, screenY)) {
            host.prepareRunPresetOpen(clickedNode);
            openRunPreset(clickedNode);
            return true;
        }
        return false;
    }

    boolean handleSchematicScroll(double screenX, double screenY, double amount) {
        if (!schematicOpen || schematicNode == null || schematicOptions.isEmpty()
            || !isPointInsideSchematicList(schematicNode, (int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = schematicLayout(schematicNode, schematicOptions.size());
        if (layout.maxScrollOffset <= 0) return true;
        schematicScrollOffset = Mth.clamp(
            schematicScrollOffset + (amount > 0 ? -1 : 1), 0, layout.maxScrollOffset);
        return true;
    }

    boolean handleRunPresetScroll(double screenX, double screenY, double amount) {
        if (!runPresetOpen || runPresetNode == null || runPresetOptions.isEmpty()
            || !isPointInsideRunPresetList(runPresetNode, (int) screenX, (int) screenY)) {
            return false;
        }
        DropdownLayoutHelper.Layout layout = runPresetLayout(runPresetNode, runPresetOptions.size());
        if (layout.maxScrollOffset <= 0) return true;
        runPresetScrollOffset = Mth.clamp(
            runPresetScrollOffset + (amount > 0 ? -1 : 1), 0, layout.maxScrollOffset);
        return true;
    }

    void openSchematic(Node node) {
        schematicNode = node;
        schematicOptions = host.loadSchematicOptions();
        String current = "";
        if (node != null) {
            NodeParameter parameter = node.getParameter("Schematic");
            current = parameter != null ? parameter.getStringValue() : "";
        }
        if (current != null && !current.isEmpty()
            && !schematicOptions.contains(current) && host.schematicExists(current)) {
            schematicOptions.add(0, current);
        }
        schematicOpen = true;
        schematicScrollOffset = 0;
        schematicHoverIndex = -1;
    }

    void closeSchematic() {
        schematicOpen = false;
        schematicHoverIndex = -1;
    }

    void openRunPreset(Node node) {
        if (!host.isPresetSelectorNode(node)) return;
        runPresetNode = node;
        runPresetOptions = new ArrayList<>(PresetManager.getAvailablePresets());
        NodeParameter parameter = node.getParameter(host.stopTargetParameterKey(node));
        String current = parameter != null && parameter.getStringValue() != null
            ? parameter.getStringValue().trim() : "";
        if (!current.isEmpty()
            && runPresetOptions.stream().noneMatch(option -> option.equalsIgnoreCase(current))) {
            runPresetOptions.add(0, current);
        }
        runPresetOpen = true;
        runPresetScrollOffset = 0;
        runPresetHoverIndex = -1;
    }

    void closeRunPreset() {
        runPresetOpen = false;
        runPresetHoverIndex = -1;
    }

    void clearSchematicState() {
        if (schematicOpen) return;
        schematicNode = null;
        schematicHoverIndex = -1;
        schematicScrollOffset = 0;
    }

    void clearRunPresetState() {
        if (runPresetOpen) return;
        runPresetNode = null;
        runPresetOptions = new ArrayList<>();
        runPresetHoverIndex = -1;
        runPresetScrollOffset = 0;
    }

    private boolean isPointInsideSchematicField(Node node, int screenX, int screenY) {
        if (node == null || !node.hasSchematicDropdownField()) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        return worldX >= node.getSchematicFieldLeft()
            && worldX <= node.getSchematicFieldLeft() + node.getSchematicFieldWidth()
            && worldY >= node.getSchematicFieldInputTop()
            && worldY <= node.getSchematicFieldInputTop() + node.getSchematicFieldHeight();
    }

    private boolean isPointInsideRunPresetField(Node node, int screenX, int screenY) {
        if (!host.isPresetSelectorNode(node)) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        return worldX >= node.getStopTargetFieldLeft()
            && worldX <= node.getStopTargetFieldLeft() + node.getStopTargetFieldWidth()
            && worldY >= node.getStopTargetFieldInputTop()
            && worldY <= node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight();
    }

    private boolean isPointInsideSchematicList(Node node, int screenX, int screenY) {
        if (node == null || !isSchematicOpenFor(node)) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        int listTop = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2;
        int height = schematicLayout(node, Math.max(1, schematicOptions.size())).height;
        return worldX >= node.getSchematicFieldLeft()
            && worldX <= node.getSchematicFieldLeft() + getSchematicDropdownWidth(node)
            && worldY >= listTop && worldY <= listTop + height;
    }

    private boolean isPointInsideRunPresetList(Node node, int screenX, int screenY) {
        if (!host.isPresetSelectorNode(node) || !isRunPresetOpenFor(node)) return false;
        int worldX = host.screenToWorldX(screenX);
        int worldY = host.screenToWorldY(screenY);
        int listTop = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2;
        int height = runPresetLayout(node, Math.max(1, runPresetOptions.size())).height;
        return worldX >= node.getStopTargetFieldLeft()
            && worldX <= node.getStopTargetFieldLeft() + getRunPresetDropdownWidth(node)
            && worldY >= listTop && worldY <= listTop + height;
    }

    private int getSchematicIndexAt(Node node, int screenY) {
        if (node == null || schematicOptions.isEmpty()) return -1;
        int row = (host.screenToWorldY(screenY)
            - node.getSchematicFieldInputTop() - node.getSchematicFieldHeight() - 2) / ROW_HEIGHT;
        DropdownLayoutHelper.Layout layout = schematicLayout(node, schematicOptions.size());
        return row < 0 || row >= layout.visibleCount ? -1 : schematicScrollOffset + row;
    }

    private int getRunPresetIndexAt(Node node, int screenY) {
        if (node == null || runPresetOptions.isEmpty()) return -1;
        int row = (host.screenToWorldY(screenY)
            - node.getStopTargetFieldInputTop() - node.getStopTargetFieldHeight() - 2) / ROW_HEIGHT;
        DropdownLayoutHelper.Layout layout = runPresetLayout(node, runPresetOptions.size());
        return row < 0 || row >= layout.visibleCount ? -1 : runPresetScrollOffset + row;
    }

    private int getSchematicDropdownWidth(Node node) {
        Font textRenderer = host.clientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getSchematicFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.width(host.translate("pathmind.dropdown.noSchematicsFound"));
        for (String option : schematicOptions) {
            if (option != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.width(option));
            }
        }
        return longestLabelWidth + SIDE_PADDING * 2 + SCROLLBAR_ALLOWANCE;
    }

    private int getRunPresetDropdownWidth(Node node) {
        Font textRenderer = host.clientTextRenderer();
        if (textRenderer == null || node == null) {
            return node != null ? node.getStopTargetFieldWidth() : 0;
        }
        int longestLabelWidth = textRenderer.width(host.translate("pathmind.dropdown.noPresetsFound"));
        for (String option : runPresetOptions) {
            if (option != null) {
                longestLabelWidth = Math.max(longestLabelWidth, textRenderer.width(option));
            }
        }
        return longestLabelWidth + SIDE_PADDING * 2 + SCROLLBAR_ALLOWANCE;
    }

    private DropdownLayoutHelper.Layout schematicLayout(Node node, int count) {
        int top = node.getSchematicFieldInputTop() + node.getSchematicFieldHeight() + 2 - host.cameraY();
        int transformedScreenHeight =
            Math.round(host.guiScaledHeight() / Math.max(0.01f, host.zoomScale()));
        return DropdownLayoutHelper.calculate(
            count, ROW_HEIGHT, MAX_ROWS, top, transformedScreenHeight);
    }

    private DropdownLayoutHelper.Layout runPresetLayout(Node node, int count) {
        int top = node.getStopTargetFieldInputTop() + node.getStopTargetFieldHeight() + 2 - host.cameraY();
        int transformedScreenHeight =
            Math.round(host.guiScaledHeight() / Math.max(0.01f, host.zoomScale()));
        return DropdownLayoutHelper.calculate(
            count, ROW_HEIGHT, MAX_ROWS, top, transformedScreenHeight);
    }

    private float getAnimatedHoverProgress(Object key, boolean highlighted) {
        if (host.compactViewportMode()) {
            return 0f;
        }
        return HoverAnimator.getProgress(key, highlighted, UITheme.HOVER_ANIM_MS);
    }

    private float getDropdownAnimationProgress(AnimatedValue animation, boolean open) {
        animation.animateTo(open ? 1f : 0f, UITheme.TRANSITION_ANIM_MS, AnimationHelper::easeOutQuad);
        animation.tick();
        return AnimationHelper.easeOutQuad(animation.getValue());
    }
}
