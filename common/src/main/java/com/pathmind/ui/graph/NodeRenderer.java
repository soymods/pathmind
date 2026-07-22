package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.InlineVariableRenderer.buildInlineVariableRender;
import static com.pathmind.ui.graph.InlineVariableRenderer.isSingleKnownInlineVariableReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.ui.graph.InlineVariableRenderer.InlineVariableRender;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Renders node hierarchies and the common chrome shared by every node type. */
final class NodeRenderer {

    interface Host {
        int cameraX();
        int cameraY();
        boolean compactViewportMode();
        boolean intersectsViewport(Node node);
        boolean isNodeOverSidebarForRender(Node node, int x, int width);
        void renderStickyNote(GuiGraphics context, Font textRenderer, Node node, int x, int y,
                              int width, int height, boolean isOverSidebar);
        int selectedNodeAccentColor();
        int toGrayscale(int color, float brightnessFactor);
        int adjustColorBrightness(int color, float factor);
        String trimTextToWidth(String text, Font renderer, int maxWidth);
        boolean isComparisonOperator(Node node);
        void drawNodeText(GuiGraphics context, Font renderer, Component text, int x, int y, int color);
        void drawNodeText(GuiGraphics context, Font renderer, String text, int x, int y, int color);
        boolean shouldRenderNodeSockets(Node node);
        Node hoveredSocketNode();
        int hoveredSocketIndex();
        boolean hoveredSocketInput();
        boolean isSocketActive(Node node, int socketIndex, boolean isInput);
        boolean isEditingEventNameField();
        InlineTextEditor eventNameEditor();
        Node eventNameEditingNode();
        void renderEventNamePreview(GuiGraphics context, Font textRenderer, String value, int x, int y,
                                    int color, int maxWidth);
        void renderPopupEditButton(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                   int mouseX, int mouseY);
        Set<String> collectRuntimeVariableNames(Node node);
        boolean shouldRenderNodeText();
        boolean shouldBuildInlineExpressionRender(String rawText, Set<String> variableNames);
        boolean hoveringStartButton();
        void renderStartLaunchIcon(GuiGraphics context, StartLaunchMode mode, int centerX, int centerY,
                                   int color, int nodeTop, int nodeHeight);
        void renderStartNodeNumber(GuiGraphics context, Font textRenderer, Node node, int x, int y,
                                   boolean isOverSidebar);
        void renderStartModeButton(GuiGraphics context, Node node, int x, int y, boolean isOverSidebar,
                                   int mouseX, int mouseY);
        boolean isEditingParameterField();
        Node parameterEditingNode();
        int parameterEditingIndex();
        void updateParameterCaretBlink();
        String parameterEditBuffer();
        boolean hasParameterSelection();
        int parameterSelectionStart();
        int parameterSelectionEnd();
        boolean parameterCaretVisible();
        int parameterCaretPosition();
        void renderParameterSlot(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                 int slotIndex);
        String getOperatorSymbol(Node node, boolean negated);
        void renderNodeContent(GuiGraphics context, Font textRenderer, Node node, int mouseX, int mouseY,
                               int x, int y, int width, int height, boolean isOverSidebar,
                               boolean simpleStyle, boolean lowDetail);
    }

    private static final int MINIMAL_NODE_TAB_WIDTH = 6;
    private static final int NODE_HEADER_BUTTON_SIZE = 12;

    private final Host host;

    NodeRenderer(Host host) {
        this.host = host;
    }

    void renderHierarchy(Node node, GuiGraphics context, Font textRenderer, int mouseX, int mouseY,
                         float delta, boolean onlyDragged, boolean ancestorActive, Set<Node> renderedNodes) {
        if (node == null || renderedNodes.contains(node)) {
            return;
        }

        boolean ownActive = isHierarchyDragging(node);
        boolean hierarchyActive = ancestorActive || ownActive;
        if ((onlyDragged && !hierarchyActive) || (!onlyDragged && hierarchyActive)) {
            markHierarchyRendered(node, renderedNodes);
            return;
        }

        if (host.intersectsViewport(node) || hierarchyActive) {
            renderNode(context, textRenderer, node, mouseX, mouseY, delta);
        }
        renderedNodes.add(node);

        Node actionChild = node.getAttachedActionNode();
        renderHierarchy(actionChild, context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);

        Node sensorChild = node.getAttachedSensor();
        renderHierarchy(sensorChild, context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);

        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            List<Integer> keys = new ArrayList<>(parameterMap.keySet());
            Collections.sort(keys);
            for (Integer key : keys) {
                renderHierarchy(parameterMap.get(key), context, textRenderer, mouseX, mouseY, delta, onlyDragged, hierarchyActive, renderedNodes);
            }
        }
    }

    private void markHierarchyRendered(Node node, Set<Node> renderedNodes) {
        if (node == null || renderedNodes.contains(node)) {
            return;
        }
        renderedNodes.add(node);
        markHierarchyRendered(node.getAttachedActionNode(), renderedNodes);
        markHierarchyRendered(node.getAttachedSensor(), renderedNodes);
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                markHierarchyRendered(parameter, renderedNodes);
            }
        }
    }

    private boolean isHierarchyDragging(Node node) {
        return isHierarchyDragging(node, new HashSet<>());
    }

    private boolean isHierarchyDragging(Node node, Set<Node> visited) {
        if (node == null || visited.contains(node)) {
            return false;
        }
        visited.add(node);
        if (node.isDragging()) {
            return true;
        }
        if (isHierarchyDragging(node.getAttachedActionNode(), visited)) {
            return true;
        }
        if (isHierarchyDragging(node.getAttachedSensor(), visited)) {
            return true;
        }
        Map<Integer, Node> parameterMap = node.getAttachedParameters();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            for (Node parameter : parameterMap.values()) {
                if (isHierarchyDragging(parameter, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderNode(GuiGraphics context, Font textRenderer, Node node, int mouseX, int mouseY, float delta) {
        int x = node.getX() - host.cameraX();
        int y = node.getY() - host.cameraY();
        int width = node.getWidth();
        int height = node.getHeight();

        // Check if node is being dragged over sidebar (grey-out effect)
        // Use screen coordinates (with camera offset) for this check
        boolean isOverSidebar = host.isNodeOverSidebarForRender(node, x, width);

        if (node.isStickyNote()) {
            host.renderStickyNote(context, textRenderer, node, x, y, width, height, isOverSidebar);
            return;
        }

        boolean simpleStyle = node.usesMinimalNodePresentation();
        boolean isStopControl = node.isStopControlNode();
        boolean lowDetail = host.compactViewportMode();

        // Node background
        int bgColor = node.isSelected() ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SECONDARY;
        if (isOverSidebar) {
            bgColor = UITheme.NODE_DIMMED_BG; // Grey when over sidebar for deletion
        }
        context.fill(x, y, x + width, y + height, bgColor);
        if (simpleStyle) {
            int tabColor;
            if (lowDetail) {
                tabColor = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.BACKGROUND_SECTION;
            } else if (isStopControl) {
                tabColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_STOP_BG, 0.7f) : UITheme.NODE_STOP_BG;
            } else {
                int baseColor = node.getColor();
                tabColor = isOverSidebar ? host.toGrayscale(baseColor, 0.7f) : baseColor;
            }
            int tabRight = Math.min(x + MINIMAL_NODE_TAB_WIDTH, x + width - 1);
            if (tabRight > x + 1) {
                context.fill(x + 1, y + 1, tabRight, y + height - 1, tabColor);
            }
        }

        // Node border - use light blue for selection, grey for dragging, darker node type color for START/events, node type color otherwise
        int borderColor;
        if (node.isDragging()) {
            borderColor = UITheme.BORDER_DRAGGING; // Medium grey outline when dragging
        } else if (node.isSelected()) {
            borderColor = host.selectedNodeAccentColor();
        } else if (lowDetail) {
            borderColor = UITheme.BORDER_SUBTLE;
        } else if (node.getType() == NodeType.START) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_START_BORDER, 0.75f) : UITheme.NODE_START_BORDER; // Darker green for START
        } else if (!simpleStyle
            && (node.getType() == NodeType.EVENT_FUNCTION || node.getType() == NodeType.ROUTINE_ENTRY)) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_EVENT_BORDER, 0.75f) : UITheme.NODE_EVENT_BORDER; // Darker pink for event functions
        } else if (node.getType() == NodeType.EVENT_CALL) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_EVENT_CALL_BG, 0.75f) : UITheme.NODE_EVENT_CALL_BG;
        } else if (node.getType() == NodeType.VARIABLE) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_VARIABLE_BORDER, 0.75f) : UITheme.NODE_VARIABLE_BORDER; // Darker orange for variables
        } else if (isStopControl) {
            borderColor = isOverSidebar ? host.toGrayscale(UITheme.NODE_STOP_BORDER, 0.75f) : UITheme.NODE_STOP_BORDER;
        } else if (simpleStyle) {
            int baseColor = node.getColor();
            borderColor = isOverSidebar ? host.toGrayscale(baseColor, 0.6f) : host.adjustColorBrightness(baseColor, 1.1f);
        } else {
            borderColor = node.getColor();
        }
        if (isOverSidebar && node.getType() != NodeType.START && !node.isDragging()) {
            borderColor = UITheme.BORDER_SUBTLE; // Darker grey border when over sidebar (for regular nodes)
        }
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, borderColor);

        // Node header (only for non-START/event function nodes)
        if (simpleStyle) {
            boolean isOperator = node.getType() == NodeType.OPERATOR_EQUALS
                || node.getType() == NodeType.OPERATOR_NOT
                || node.getType() == NodeType.OPERATOR_BOOLEAN_OR
                || node.getType() == NodeType.OPERATOR_BOOLEAN_AND
                || node.getType() == NodeType.OPERATOR_BOOLEAN_XOR;
            boolean isRoutineNode = node.getType() == NodeType.ROUTINE_ENTRY
                || node.getType() == NodeType.ROUTINE_CALL
                || node.getType() == NodeType.ROUTINE_INPUT;
            String label = isRoutineNode
                ? node.getDisplayName().getString()
                : node.getType().getDisplayName().toUpperCase(Locale.ROOT);
            boolean isActivateNode = node.getType() == NodeType.START_CHAIN;
            int titleColor = (isStopControl || isActivateNode)
                ? UITheme.TEXT_PRIMARY
                : (isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY);
            int textX;
            int textY;
            if (node.hasStopTargetInputField()) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                textX = contentLeft + 4;
                textY = y + 4;
            } else if (!isOperator) {
                int contentLeft = x + MINIMAL_NODE_TAB_WIDTH;
                int contentWidth = Math.max(0, width - MINIMAL_NODE_TAB_WIDTH);
                int reservedWidth = node.supportsRuntimeValueScope() ? NODE_HEADER_BUTTON_SIZE + 4 : 0;
                String displayLabel = host.trimTextToWidth(label, textRenderer,
                    Math.max(0, contentWidth - 8 - reservedWidth));
                int textWidth = textRenderer.width(displayLabel);
                textX = contentLeft + Math.max(4, (contentWidth - textWidth) / 2);
                textY = y + (height - textRenderer.lineHeight) / 2;
                label = displayLabel;
            } else {
                textX = 0;
                textY = 0;
            }
            if (!isOperator && !host.isComparisonOperator(node)) {
                host.drawNodeText(context, textRenderer, label, textX, textY, titleColor);
            }
        } else if (node.getType() != NodeType.START
            && node.getType() != NodeType.EVENT_FUNCTION
            && node.getType() != NodeType.ROUTINE_ENTRY
            && node.getType() != NodeType.VARIABLE
            && node.getType() != NodeType.ROUTINE_INPUT
            && node.getType() != NodeType.TEMPLATE
            && node.getType() != NodeType.OPERATOR_EQUALS
            && node.getType() != NodeType.OPERATOR_NOT
            && node.getType() != NodeType.OPERATOR_BOOLEAN_XOR) {
            if (!lowDetail) {
                int headerColor = node.getColor() & UITheme.NODE_HEADER_ALPHA_MASK;
                if (isOverSidebar) {
                    headerColor = UITheme.NODE_HEADER_DIMMED; // Grey header when over sidebar
                }
                context.fill(x + 1, y + 1, x + width - 1, y + 14, headerColor);
            }

            // Node title
            int titleColor = isOverSidebar ? UITheme.TEXT_TERTIARY : (lowDetail ? UITheme.TEXT_SECONDARY : UITheme.TEXT_PRIMARY);
            Component displayName = node.getDisplayName();
            if (node.supportsRuntimeValueScope()) {
                displayName = Component.literal(host.trimTextToWidth(displayName.getString(), textRenderer,
                    Math.max(0, width - NODE_HEADER_BUTTON_SIZE - 10)));
            }
            host.drawNodeText(
                context,
                textRenderer,
                displayName,
                x + 4,
                y + 4,
                titleColor
            );
        }

        renderNodeSockets(context, node, isOverSidebar, lowDetail);
        host.renderNodeContent(context, textRenderer, node, mouseX, mouseY, x, y, width, height,
            isOverSidebar, simpleStyle, lowDetail);
    }

    private void renderNodeSockets(GuiGraphics context, Node node, boolean isOverSidebar, boolean lowDetail) {
        if (!host.shouldRenderNodeSockets(node)) {
            return;
        }

        // Render input sockets
        for (int i = 0; i < node.getInputSocketCount(); i++) {
            boolean isHovered = (host.hoveredSocketNode() == node
                && host.hoveredSocketIndex() == i
                && host.hoveredSocketInput());
            boolean isActive = host.isSocketActive(node, i, true);
            int socketColor;
            if (lowDetail && !isOverSidebar) {
                socketColor = isHovered
                    ? host.selectedNodeAccentColor()
                    : (isActive ? UITheme.BORDER_DEFAULT : UITheme.BORDER_SUBTLE);
            } else {
                socketColor = isHovered ? host.selectedNodeAccentColor() : node.getColor();
                if (!isActive && !isHovered) {
                    socketColor = darkenColor(socketColor, 0.7f); // Darker when unused
                }
            }
            if (isOverSidebar) {
                socketColor = UITheme.BORDER_HIGHLIGHT; // Grey sockets when over sidebar
            }
            renderSocket(context, node.getSocketX(true) - host.cameraX(), node.getSocketY(i, true) - host.cameraY(), true, socketColor);
        }

        // Render output sockets
        for (int i = 0; i < node.getOutputSocketCount(); i++) {
            boolean isHovered = (host.hoveredSocketNode() == node
                && host.hoveredSocketIndex() == i
                && !host.hoveredSocketInput());
            boolean isActive = host.isSocketActive(node, i, false);
            int socketColor;
            if (lowDetail && !isOverSidebar) {
                socketColor = isHovered
                    ? host.selectedNodeAccentColor()
                    : (isActive ? UITheme.BORDER_DEFAULT : UITheme.BORDER_SUBTLE);
            } else {
                socketColor = isHovered ? host.selectedNodeAccentColor() : node.getOutputSocketColor(i);
                if (!isActive && !isHovered) {
                    socketColor = darkenColor(socketColor, 0.7f); // Darker when unused
                }
            }
            if (isOverSidebar) {
                socketColor = UITheme.BORDER_HIGHLIGHT; // Grey sockets when over sidebar
            }
            renderSocket(context, node.getSocketX(false) - host.cameraX(), node.getSocketY(i, false) - host.cameraY(), false, socketColor);
        }
    }

    void renderStartContent(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                            int mouseX, int mouseY, int x, int y, int width, int height, boolean lowDetail) {
        // START node - green square with play button
        int greenColor = lowDetail
            ? (isOverSidebar ? UITheme.NODE_DIMMED_BG : UITheme.BACKGROUND_SECTION)
            : (isOverSidebar ? host.toGrayscale(UITheme.NODE_START_BG, 0.7f) : UITheme.NODE_START_BG);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, greenColor);

        // Draw launch-mode icon - with hover effect
        int playColor;
        if (host.hoveringStartButton()) {
            playColor = isOverSidebar ? UITheme.TEXT_LABEL : UITheme.TEXT_PRIMARY; // Darker when hovered
        } else {
            playColor = isOverSidebar ? UITheme.TEXT_PRIMARY : UITheme.TEXT_PRIMARY; // Normal white
        }
        int centerX = x + width / 2;
        int centerY = y + height / 2;

        host.renderStartLaunchIcon(context, node.getStartLaunchMode(), centerX, centerY, playColor, y, height);
        host.renderStartNodeNumber(context, textRenderer, node, x, y, isOverSidebar);
        host.renderStartModeButton(context, node, x, y, isOverSidebar, mouseX, mouseY);
    }

    void renderVariableContent(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                               int x, int y, int width, int height, boolean lowDetail) {
        boolean routineInput = node.getType() == NodeType.ROUTINE_INPUT;
        int baseColor = lowDetail
            ? (isOverSidebar ? UITheme.NODE_DIMMED_BG : UITheme.BACKGROUND_SECTION)
            : (isOverSidebar ? host.toGrayscale(routineInput ? node.getColor() : UITheme.NODE_VARIABLE_BG, 0.7f)
                : (routineInput ? node.getColor() : UITheme.NODE_VARIABLE_BG));
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

        int titleColor = isOverSidebar
            ? host.toGrayscale(routineInput ? UITheme.NODE_EVENT_TITLE : UITheme.NODE_VARIABLE_TITLE, 0.9f)
            : (lowDetail ? UITheme.TEXT_SECONDARY : (routineInput ? UITheme.NODE_EVENT_TITLE : UITheme.NODE_VARIABLE_TITLE));
        host.drawNodeText(context, textRenderer,
            routineInput ? Component.literal("Input") : Component.translatable("pathmind.node.type.variable"),
            x + 6, y + 4, titleColor);

        int boxLeft = x + 6;
        int boxRight = x + width - 6;
        int boxHeight = 16;
        int boxTop = y + height / 2 - boxHeight / 2 + 4;
        int boxBottom = boxTop + boxHeight;
        boolean editingThis = host.isEditingParameterField()
            && host.parameterEditingNode() == node
            && host.parameterEditingIndex() == 0;
        if (editingThis) {
            host.updateParameterCaretBlink();
        }
        int inputBackground = isOverSidebar
            ? UITheme.NODE_INPUT_BG_DIMMED
            : (lowDetail ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_INPUT);
        int inputBorder = isOverSidebar
            ? host.toGrayscale(routineInput ? UITheme.NODE_EVENT_INPUT_BORDER : UITheme.NODE_VARIABLE_INPUT_BORDER, 0.8f)
            : (lowDetail ? UITheme.BORDER_DEFAULT : UITheme.BORDER_SUBTLE);
        if (editingThis) {
            inputBorder = host.selectedNodeAccentColor();
        }
        context.fill(boxLeft, boxTop, boxRight, boxBottom, inputBackground);
        DrawContextBridge.drawBorderInLayer(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

        NodeParameter nameParam = node.getParameter(routineInput ? "Label" : "Variable");
        String value = editingThis
            ? host.parameterEditBuffer()
            : (nameParam != null ? nameParam.getStringValue() : "");
        if (value == null) {
            value = "";
        }
        String display;
        if (!editingThis && value.isEmpty()) {
            display = routineInput ? "input" : "enter variable";
        } else {
            display = value;
        }
        display = editingThis
            ? display
            : host.trimTextToWidth(display, textRenderer, boxRight - boxLeft - 8);
        int textY = boxTop + (boxHeight - textRenderer.lineHeight) / 2 + 1;
        int textColor = editingThis ? UITheme.TEXT_EDITING
            : (isOverSidebar ? host.toGrayscale(routineInput ? UITheme.NODE_EVENT_TEXT : UITheme.NODE_VARIABLE_TEXT, 0.85f)
                : (lowDetail ? UITheme.TEXT_PRIMARY : (routineInput ? UITheme.NODE_EVENT_TEXT : UITheme.NODE_VARIABLE_TEXT)));
        if (editingThis && host.hasParameterSelection()) {
            int start = Mth.clamp(host.parameterSelectionStart(), 0, display.length());
            int end = Mth.clamp(host.parameterSelectionEnd(), 0, display.length());
            if (start != end) {
                int selectionStartX = boxLeft + 4 + textRenderer.width(display.substring(0, start));
                int selectionEndX = boxLeft + 4 + textRenderer.width(display.substring(0, end));
                context.fill(selectionStartX, boxTop + 2, selectionEndX, boxBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        host.drawNodeText(context, textRenderer, Component.literal(display), boxLeft + 4, textY, textColor);
        if (editingThis && host.parameterCaretVisible()) {
            int caretIndex = Mth.clamp(host.parameterCaretPosition(), 0, display.length());
            int caretX = boxLeft + 4 + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, boxRight - 2);
            int caretBaseline = Math.min(textY + textRenderer.lineHeight - 1, boxBottom - 2);
            UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, boxRight - 2, UITheme.CARET_COLOR);
        }
    }

    void renderComparisonContent(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                 int x, int y, int width, int height, boolean lowDetail) {
        int accentColor = node.getColor();
        int baseColor = lowDetail ? (isOverSidebar ? UITheme.NODE_DIMMED_BG : UITheme.BACKGROUND_SECTION)
            : (isOverSidebar ? host.toGrayscale(accentColor, 0.7f) : host.adjustColorBrightness(accentColor, 0.55f));
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

        int titleColor = isOverSidebar ? host.toGrayscale(UITheme.TEXT_LABEL, 0.9f) : UITheme.TEXT_PRIMARY;
        if (titleColor != 0) {
            // Intentionally skip title text for operator nodes to keep the symbol clean.
        }

        host.renderParameterSlot(context, textRenderer, node, isOverSidebar, 0);
        host.renderParameterSlot(context, textRenderer, node, isOverSidebar, 1);

        int leftSlotX = node.getParameterSlotLeft(0) - host.cameraX();
        int rightSlotX = node.getParameterSlotLeft(1) - host.cameraX();
        int leftSlotWidth = node.getParameterSlotWidth(0);
        int leftSlotHeight = node.getParameterSlotHeight(0);
        int rightSlotHeight = node.getParameterSlotHeight(1);
        int gapCenterX = leftSlotX + leftSlotWidth + (rightSlotX - (leftSlotX + leftSlotWidth)) / 2;
        String operatorText = host.getOperatorSymbol(node, false);
        int operatorWidth = textRenderer.width(operatorText);
        int operatorX = gapCenterX - operatorWidth / 2;
        int leftSlotTop = node.getParameterSlotTop(0) - host.cameraY();
        int rightSlotTop = node.getParameterSlotTop(1) - host.cameraY();
        int leftCenterY = leftSlotTop + leftSlotHeight / 2;
        int rightCenterY = rightSlotTop + rightSlotHeight / 2;
        int operatorCenterY = (leftCenterY + rightCenterY) / 2;
        int operatorY = operatorCenterY - textRenderer.lineHeight / 2;
        int operatorColor = isOverSidebar
            ? host.toGrayscale(UITheme.NODE_OPERATOR_SYMBOL, 0.85f)
            : (lowDetail ? UITheme.TEXT_SECONDARY : UITheme.NODE_OPERATOR_SYMBOL);
        if (node.getType() == NodeType.OPERATOR_GREATER || node.getType() == NodeType.OPERATOR_LESS) {
            int buttonPaddingX = 3;
            int buttonPaddingY = 4;
            int maxSymbolWidth = textRenderer.width(">=");
            int buttonWidth = maxSymbolWidth + buttonPaddingX * 2;
            int buttonHeight = textRenderer.lineHeight + buttonPaddingY * 2;
            int buttonLeft = gapCenterX - buttonWidth / 2;
            int buttonTop = operatorY - buttonPaddingY;
            int buttonFill = isOverSidebar ? UITheme.BACKGROUND_SECONDARY
                : (lowDetail ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_TERTIARY);
            int buttonBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
            context.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, buttonFill);
            DrawContextBridge.drawBorderInLayer(context, buttonLeft, buttonTop, buttonWidth, buttonHeight, buttonBorder);
            operatorX = buttonLeft + (buttonWidth - operatorWidth) / 2;
        }
        host.drawNodeText(context, textRenderer, Component.literal(operatorText), operatorX, operatorY, operatorColor);
    }

    void renderEventDefinitionContent(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                      int mouseX, int mouseY, int x, int y, int width, int height,
                                      boolean lowDetail) {
        int baseColor = lowDetail
            ? (isOverSidebar ? UITheme.NODE_DIMMED_BG : UITheme.BACKGROUND_SECTION)
            : (isOverSidebar ? host.toGrayscale(UITheme.NODE_EVENT_BG, 0.7f) : UITheme.NODE_EVENT_BG);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

        int titleColor = isOverSidebar
            ? host.toGrayscale(UITheme.NODE_EVENT_TITLE, 0.9f)
            : (lowDetail ? UITheme.TEXT_SECONDARY : UITheme.NODE_EVENT_TITLE);
        host.drawNodeText(
            context,
            textRenderer,
            Component.translatable("pathmind.node.type.eventFunction"),
            x + 6,
            y + 4,
            titleColor
        );

        int boxLeft = node.getEventNameFieldLeft() - host.cameraX();
        int boxTop = node.getEventNameFieldTop() - host.cameraY();
        int boxWidth = node.getEventNameFieldWidth();
        int boxHeight = node.getEventNameFieldHeight();
        int boxRight = boxLeft + boxWidth;
        int boxBottom = boxTop + boxHeight;
        int inputBackground = isOverSidebar
            ? UITheme.NODE_INPUT_BG_DIMMED
            : (lowDetail ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_INPUT);
        context.fill(boxLeft, boxTop, boxRight, boxBottom, inputBackground);
        int inputBorder = isOverSidebar
            ? host.toGrayscale(UITheme.NODE_EVENT_INPUT_BORDER, 0.8f)
            : (lowDetail ? UITheme.BORDER_DEFAULT : UITheme.BORDER_SUBTLE);
        DrawContextBridge.drawBorderInLayer(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

        InlineTextEditor eventNameEditor = host.eventNameEditor();
        boolean editingEventName = host.isEditingEventNameField() && host.eventNameEditingNode() == node;
        if (editingEventName) {
            eventNameEditor.updateCaretBlink();
        }

        NodeParameter nameParam = node.getParameter("Name");
        String value = editingEventName
            ? eventNameEditor.getBuffer()
            : (nameParam != null ? nameParam.getStringValue() : "");
        if (value == null) {
            value = "";
        }
        String display;
        boolean showPlaceholder = !editingEventName && value.isEmpty();
        if (showPlaceholder) {
            display = "enter name";
        } else {
            display = value;
        }
        int eventNameVariableHighlightColor = isOverSidebar ? host.toGrayscale(host.selectedNodeAccentColor(), 0.85f) : host.selectedNodeAccentColor();
        Set<String> eventNameVariableNames = host.collectRuntimeVariableNames(node);
        InlineVariableRender eventNameRenderData = null;
        boolean highlightPlainEventName = false;
        if (host.shouldBuildInlineExpressionRender(value, eventNameVariableNames)) {
            InlineVariableRender candidate = buildInlineVariableRender(value, eventNameVariableNames, isOverSidebar ? host.toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f) : UITheme.NODE_EVENT_TEXT, eventNameVariableHighlightColor);
            if (editingEventName) {
                eventNameRenderData = candidate;
                display = eventNameRenderData.displayText;
            } else if (textRenderer.width(candidate.displayText) <= boxRight - boxLeft - 8) {
                eventNameRenderData = candidate;
                display = eventNameRenderData.displayText;
            } else if (isSingleKnownInlineVariableReference(value, eventNameVariableNames)) {
                display = host.trimTextToWidth(candidate.displayText, textRenderer, boxRight - boxLeft - 8);
                highlightPlainEventName = true;
            }
        }
        int textY = boxTop + (boxHeight - textRenderer.lineHeight) / 2 + 1;
        int textColor = isOverSidebar
            ? host.toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f)
            : (lowDetail ? UITheme.TEXT_PRIMARY : UITheme.NODE_EVENT_TEXT);
        if (showPlaceholder) {
            textColor = UITheme.TEXT_TERTIARY;
        }
        if (highlightPlainEventName) {
            textColor = eventNameVariableHighlightColor;
        }
        int textX = boxLeft + 4;
        if (editingEventName && eventNameEditor.hasSelection()) {
            int start = eventNameEditor.getSelectionStart();
            int end = eventNameEditor.getSelectionEnd();
            if (eventNameRenderData != null) {
                start = eventNameRenderData.toDisplayIndex(start);
                end = eventNameRenderData.toDisplayIndex(end);
            }
            start = Mth.clamp(start, 0, display.length());
            end = Mth.clamp(end, 0, display.length());
            if (start != end) {
                int selectionStartX = textX + textRenderer.width(display.substring(0, start));
                int selectionEndX = textX + textRenderer.width(display.substring(0, end));
                context.fill(selectionStartX, boxTop + 2, selectionEndX, boxBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        if (!editingEventName) {
            if (eventNameRenderData != null && host.shouldRenderNodeText()) {
                eventNameRenderData.draw(context, textRenderer, textX, textY);
            } else {
                host.renderEventNamePreview(context, textRenderer, display, textX, textY, textColor, boxRight - boxLeft - 8);
            }
        } else {
            if (eventNameRenderData != null && host.shouldRenderNodeText()) {
                eventNameRenderData.draw(context, textRenderer, textX, textY);
            } else {
                host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, textColor);
            }
        }

        if (editingEventName && eventNameEditor.isCaretVisible()) {
            int caretIndex = eventNameEditor.getCaretPosition();
            if (eventNameRenderData != null) {
                caretIndex = eventNameRenderData.toDisplayIndex(caretIndex);
            }
            caretIndex = Mth.clamp(caretIndex, 0, display.length());
            int caretX = textX + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, boxRight - 2);
            int caretBaseline = Math.min(textY + textRenderer.lineHeight - 1, boxBottom - 2);
            UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, boxRight - 2, UITheme.CARET_COLOR);
        }
        host.renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    void renderEventCallContent(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                int mouseX, int mouseY, int x, int y, int width, int height,
                                boolean lowDetail) {
        int baseColor = lowDetail
            ? (isOverSidebar ? UITheme.NODE_DIMMED_BG : UITheme.BACKGROUND_SECTION)
            : (isOverSidebar ? host.toGrayscale(node.getColor(), 0.7f) : node.getColor());
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, baseColor);

        int titleColor = isOverSidebar
            ? host.toGrayscale(UITheme.NODE_EVENT_TITLE, 0.9f)
            : (lowDetail ? UITheme.TEXT_SECONDARY : UITheme.NODE_EVENT_TITLE);
        host.drawNodeText(
            context,
            textRenderer,
            Component.translatable("pathmind.node.type.eventCall"),
            x + 6,
            y + 4,
            titleColor
        );

        int boxLeft = node.getEventNameFieldLeft() - host.cameraX();
        int boxTop = node.getEventNameFieldTop() - host.cameraY();
        int boxWidth = node.getEventNameFieldWidth();
        int boxHeight = node.getEventNameFieldHeight();
        int boxRight = boxLeft + boxWidth;
        int boxBottom = boxTop + boxHeight;
        int inputBackground = isOverSidebar
            ? UITheme.NODE_INPUT_BG_DIMMED
            : (lowDetail ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_INPUT);
        context.fill(boxLeft, boxTop, boxRight, boxBottom, inputBackground);
        int inputBorder = isOverSidebar
            ? host.toGrayscale(UITheme.NODE_EVENT_CALL_INPUT_BORDER, 0.8f)
            : (lowDetail ? UITheme.BORDER_DEFAULT : UITheme.BORDER_SUBTLE);
        DrawContextBridge.drawBorderInLayer(context, boxLeft, boxTop, boxRight - boxLeft, boxHeight, inputBorder);

        InlineTextEditor eventNameEditor = host.eventNameEditor();
        boolean editingEventName = host.isEditingEventNameField() && host.eventNameEditingNode() == node;
        if (editingEventName) {
            eventNameEditor.updateCaretBlink();
        }

        NodeParameter nameParam = node.getParameter("Name");
        String value = editingEventName
            ? eventNameEditor.getBuffer()
            : (nameParam != null ? nameParam.getStringValue() : "");
        if (value == null) {
            value = "";
        }
        String display;
        boolean showPlaceholder = !editingEventName && value.isEmpty();
        if (showPlaceholder) {
            display = "enter name";
        } else {
            display = value;
        }
        int textY = boxTop + (boxHeight - textRenderer.lineHeight) / 2 + 1;
        int textColor = isOverSidebar
            ? host.toGrayscale(UITheme.NODE_EVENT_TEXT, 0.85f)
            : (lowDetail ? UITheme.TEXT_PRIMARY : UITheme.NODE_EVENT_TEXT);
        if (showPlaceholder) {
            textColor = UITheme.TEXT_TERTIARY;
        }
        int textX = boxLeft + 4;
        if (editingEventName && eventNameEditor.hasSelection()) {
            int start = Mth.clamp(eventNameEditor.getSelectionStart(), 0, display.length());
            int end = Mth.clamp(eventNameEditor.getSelectionEnd(), 0, display.length());
            if (start != end) {
                int selectionStartX = textX + textRenderer.width(display.substring(0, start));
                int selectionEndX = textX + textRenderer.width(display.substring(0, end));
                context.fill(selectionStartX, boxTop + 2, selectionEndX, boxBottom - 2, UITheme.TEXT_SELECTION_BG);
            }
        }
        if (!editingEventName) {
            host.renderEventNamePreview(context, textRenderer, display, textX, textY, textColor, boxRight - boxLeft - 8);
        } else {
            host.drawNodeText(context, textRenderer, Component.literal(display), textX, textY, textColor);
        }

        if (editingEventName && eventNameEditor.isCaretVisible()) {
            int caretIndex = Mth.clamp(eventNameEditor.getCaretPosition(), 0, display.length());
            int caretX = textX + textRenderer.width(display.substring(0, caretIndex));
            caretX = Math.min(caretX, boxRight - 2);
            int caretBaseline = Math.min(textY + textRenderer.lineHeight - 1, boxBottom - 2);
            UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, boxRight - 2, UITheme.CARET_COLOR);
        }
        host.renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
    }

    static void renderSocket(GuiGraphics context, int x, int y, boolean isInput, int color) {
        // Socket circle
        context.fill(x - 3, y - 3, x + 3, y + 3, color);
        DrawContextBridge.drawBorderInLayer(context, x - 3, y - 3, 6, 6, UITheme.BORDER_SOCKET);

        // Socket highlight
        context.fill(x - 1, y - 1, x + 1, y + 1, UITheme.TEXT_PRIMARY);
    }

    private static int darkenColor(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        red = Math.min(255, Math.max(0, Math.round(red * factor)));
        green = Math.min(255, Math.max(0, Math.round(green * factor)));
        blue = Math.min(255, Math.max(0, Math.round(blue * factor)));

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
