package com.pathmind.ui.graph;

import static com.pathmind.ui.graph.InlineVariableRenderer.buildInlineVariableRender;
import static com.pathmind.ui.graph.InlineVariableRenderer.isSingleKnownInlineVariableReference;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAmountParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isAttributeDetectionDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockFaceParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockItemParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBlockStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isBooleanLiteralParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isDirectionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isEntityStateParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isFabricEventSensorParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isGuiParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isHandParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isInlineDropdownParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMessageParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isMouseButtonParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isPlayerParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isSeedParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerProfessionParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeParameter;
import static com.pathmind.ui.graph.ParameterTypeClassifier.isVillagerTradeVariantParameter;

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
import com.pathmind.nodes.RelativeInputSupport;
import com.pathmind.nodes.StartLaunchMode;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.graph.InlineVariableRenderer.InlineVariableRender;
import com.pathmind.ui.theme.UIStyleHelper;
import com.pathmind.ui.theme.UITheme;
import com.pathmind.util.DrawContextBridge;
import com.pathmind.util.PathmindI18n;

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
        void renderPopupEditButton(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                   int mouseX, int mouseY);
        Set<String> collectRuntimeVariableNames(Node node);
        boolean shouldRenderNodeText();
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
        boolean shouldShowParameters(Node node);
        int parameterInputHeight();
        int parameterInputGap();
        int directionModeTabHeight();
        int getParameterFieldLeft(Node node);
        int getParameterFieldWidth(Node node);
        int getParameterFieldHeight();
        int screenToWorldX(int screenX);
        int screenToWorldY(int screenY);
        float getTextFieldHighlightProgress(Object key, boolean hovered, boolean active);
        UIStyleHelper.FieldPalette getLowDetailAwareFieldPalette(
            int backgroundColor, int borderColor, int innerBorderColor,
            int textColor, int placeholderColor, boolean isOverSidebar);
        boolean modeDropdownOpenFor(Node node);
        boolean isCombinedDirectionNode(Node node);
        void renderDirectionModeTabs(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                     int fieldTop, int mouseX, int mouseY);
        boolean isCombinedBooleanNode(Node node);
        void renderBooleanModeTabs(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                   int fieldTop, int mouseX, int mouseY);
        String getParameterLabelText(Node node, NodeParameter parameter, Font textRenderer, int maxWidth);
        int getParameterValueStartX(Node node, NodeParameter parameter, Font textRenderer);
        boolean isDefaultMouseButtonValue(String value);
        boolean isDefaultHandValue(String value);
        boolean isTradeInlinePlaceholder(Node node, NodeParameter parameter, boolean editing);
        boolean isAnyBlockItemValue(String value);
        String formatVillagerTradeValue(String rawValue);
        String formatMouseButtonValue(String value);
        String formatHandValue(String value);
        String formatAttributeDetectionInlineValue(Node node, NodeParameter parameter, String value);
        boolean parameterDropdownOpen();
        Node parameterDropdownNode();
        int parameterDropdownIndex();
        void updateParameterDropdown(Node node, int index, Font textRenderer, int fieldX, int fieldY,
                                     int fieldWidth, int fieldHeight);
        void renderRandomRoundingField(GuiGraphics context, Font textRenderer, Node node,
                                       boolean isOverSidebar);
        void renderAmountInputField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                    int mouseX, int mouseY);
        void renderParameterSlot(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                 int slotIndex);
        String getOperatorSymbol(Node node, boolean negated);
        boolean rendersInlineParameters(Node node);
        void renderTemplateNode(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                int mouseX, int mouseY);
        void renderStopTargetInputField(GuiGraphics context, Font textRenderer, Node node,
                                        boolean isOverSidebar, int mouseX, int mouseY);
        void renderSchematicDropdownField(GuiGraphics context, Font textRenderer, Node node,
                                          boolean isOverSidebar, int mouseX, int mouseY);
        void renderVariableInputField(GuiGraphics context, Font textRenderer, Node node,
                                      boolean isOverSidebar, int mouseX, int mouseY);
        void renderCoordinateInputFields(GuiGraphics context, Font textRenderer, Node node,
                                         boolean isOverSidebar, int mouseX, int mouseY);
        void renderMessageInputFields(GuiGraphics context, Font textRenderer, Node node,
                                      boolean isOverSidebar, int mouseX, int mouseY);
        void renderMessageScopeToggle(GuiGraphics context, Font textRenderer, Node node,
                                      boolean isOverSidebar, int mouseX, int mouseY);
        void renderMessageButtons(GuiGraphics context, Font textRenderer, Node node,
                                  boolean isOverSidebar, int mouseX, int mouseY);
        void renderBooleanOperatorButtons(GuiGraphics context, Font textRenderer, Node node,
                                          boolean isOverSidebar, int mouseX, int mouseY);
        void renderBookTextInput(GuiGraphics context, Font textRenderer, Node node,
                                 boolean isOverSidebar, int mouseX, int mouseY);
        void renderSchematicDropdownList(GuiGraphics context, Font textRenderer, Node node,
                                         boolean isOverSidebar, int mouseX, int mouseY);
        boolean isPresetSelectorNode(Node node);
        void renderRunPresetDropdownList(GuiGraphics context, Font textRenderer, Node node,
                                         boolean isOverSidebar, int mouseX, int mouseY);
        void renderBooleanToggleButton(GuiGraphics context, Font textRenderer, Node node,
                                       boolean isOverSidebar, int mouseX, int mouseY);
        void renderSensorSlot(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar);
        void renderActionSlot(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar);
        void renderRuntimeScopeButton(GuiGraphics context, Node node, boolean isOverSidebar,
                                      int mouseX, int mouseY);
        boolean hasRunPresetSelection(Node node);
        void renderRunPresetOpenButton(GuiGraphics context, Font textRenderer, Node node,
                                       boolean isOverSidebar, int mouseX, int mouseY);
        boolean hasBuildSchematicPreview(Node node);
        void renderBuildSchematicPreviewButton(GuiGraphics context, Font textRenderer, Node node,
                                               boolean isOverSidebar, int mouseX, int mouseY);
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
        } else if (node.hasRuntimeDiagnostic()) {
            borderColor = UITheme.STATE_ERROR;
        }
        DrawContextBridge.drawBorderInLayer(context, x, y, width, height, borderColor);
        if (node.hasRuntimeDiagnostic() && !lowDetail) {
            int markerSize = 7;
            int markerX = x + width - markerSize - 3;
            int markerY = y + 3;
            context.fill(markerX, markerY, markerX + markerSize, markerY + markerSize, UITheme.STATE_ERROR);
            context.drawString(textRenderer, "!", markerX + 2, markerY, UITheme.TEXT_PRIMARY);
        }

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
        renderNodeContent(context, textRenderer, node, mouseX, mouseY, x, y, width, height,
            isOverSidebar, simpleStyle, lowDetail);
    }

    private void renderNodeContent(GuiGraphics context, Font textRenderer, Node node, int mouseX, int mouseY,
                                   int x, int y, int width, int height, boolean isOverSidebar,
                                   boolean simpleStyle, boolean lowDetail) {
        // Render node content based on type
        if (node.getType() == NodeType.START) {
            renderStartContent(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                x, y, width, height, lowDetail);
        } else if (!simpleStyle
            && (node.getType() == NodeType.EVENT_FUNCTION || node.getType() == NodeType.ROUTINE_ENTRY)) {
            renderEventDefinitionContent(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                x, y, width, height, lowDetail);
        } else if (node.getType() == NodeType.VARIABLE || node.getType() == NodeType.ROUTINE_INPUT) {
            renderVariableContent(context, textRenderer, node, isOverSidebar,
                x, y, width, height, lowDetail);
        } else if (!simpleStyle && host.isComparisonOperator(node) && !node.isExpandableBooleanOperator()) {
            renderComparisonContent(context, textRenderer, node, isOverSidebar,
                x, y, width, height, lowDetail);
        } else if (node.getType() == NodeType.EVENT_CALL) {
            renderEventCallContent(context, textRenderer, node, isOverSidebar, mouseX, mouseY,
                x, y, width, height, lowDetail);
        } else if (node.getType() == NodeType.TEMPLATE) {
            host.renderTemplateNode(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        } else {
            if (host.rendersInlineParameters(node)) {
                renderInlineParameterContent(context, textRenderer, node, isOverSidebar,
                    mouseX, mouseY, x, y, width, height);
            } else {
                if (node.hasStopTargetInputField()) {
                    host.renderStopTargetInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasSchematicDropdownField()) {
                    host.renderSchematicDropdownField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasVariableInputField()) {
                    host.renderVariableInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.showsModeFieldAboveParameterSlot()) {
                    renderModeField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasParameterSlot()) {
                    int slotCount = node.getParameterSlotCount();
                    for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                        host.renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
                    }
                    if (node.hasCoordinateInputFields()) {
                        host.renderCoordinateInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                    if (node.hasAmountInputField()) {
                        host.renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                } else if (node.hasCoordinateInputFields()) {
                    host.renderCoordinateInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                } else if (node.hasAmountInputField()) {
                    host.renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasMessageInputFields()) {
                    host.renderMessageInputFields(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    if (node.hasMessageScopeToggle()) {
                        host.renderMessageScopeToggle(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                    }
                    host.renderMessageButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.isExpandableBooleanOperator()) {
                    host.renderBooleanOperatorButtons(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasBookTextInput()) {
                    host.renderBookTextInput(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (node.hasSchematicDropdownField()) {
                    host.renderSchematicDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
                if (host.isPresetSelectorNode(node)) {
                    host.renderRunPresetDropdownList(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
                }
            }

            if (node.hasBooleanToggle()) {
                host.renderBooleanToggleButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
            }
            if (node.hasSensorSlot()) {
                host.renderSensorSlot(context, textRenderer, node, isOverSidebar);
            }
            if (node.hasActionSlot()) {
                host.renderActionSlot(context, textRenderer, node, isOverSidebar);
            }
        }
        if (node.supportsRuntimeValueScope()) {
            host.renderRuntimeScopeButton(context, node, isOverSidebar, mouseX, mouseY);
        }
        if (host.hasRunPresetSelection(node)) {
            host.renderRunPresetOpenButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
        if (host.hasBuildSchematicPreview(node)) {
            host.renderBuildSchematicPreviewButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
        }
    }

    private void renderModeField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                 int mouseX, int mouseY) {
        if (node == null || !node.showsModeFieldAboveParameterSlot()) {
            return;
        }
        int fieldLeft = node.getModeFieldLeft() - host.cameraX();
        int fieldTop = node.getModeFieldTop() - host.cameraY();
        int fieldWidth = node.getModeFieldWidth();
        int fieldHeight = node.getModeFieldHeight();
        String labelText = node.getModeFieldLabelText();
        String modeValue = node.getMode() != null ? node.getMode().getDisplayName() : PathmindI18n.tr("pathmind.node.mode.select");
        renderDropdownSelectorField(
            context, textRenderer, node, isOverSidebar, mouseX, mouseY,
            fieldLeft, fieldTop, fieldWidth, fieldHeight,
            labelText, true, modeValue
        );
    }

    void renderDropdownSelectorField(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                     int mouseX, int mouseY, int fieldLeft, int fieldTop, int fieldWidth,
                                     int fieldHeight, String label, boolean includeValue, String value) {
        int worldMouseX = host.screenToWorldX(mouseX);
        int worldMouseY = host.screenToWorldY(mouseY);
        int worldFieldLeft = fieldLeft + host.cameraX();
        int worldFieldTop = fieldTop + host.cameraY();
        boolean hovered = !isOverSidebar
            && worldMouseX >= worldFieldLeft
            && worldMouseX <= worldFieldLeft + fieldWidth
            && worldMouseY >= worldFieldTop
            && worldMouseY <= worldFieldTop + fieldHeight;
        boolean open = host.modeDropdownOpenFor(node);

        float hoverProgress = host.getTextFieldHighlightProgress(
            node.getId() + "#selector:" + fieldLeft + ":" + fieldTop, hovered || open, false);
        int accentColor = isOverSidebar
            ? host.toGrayscale(host.selectedNodeAccentColor(), 0.8f)
            : host.selectedNodeAccentColor();
        UIStyleHelper.FieldPalette palette;
        if (host.compactViewportMode() && !isOverSidebar) {
            palette = new UIStyleHelper.FieldPalette(
                open ? UITheme.BACKGROUND_INPUT : UITheme.BACKGROUND_SECONDARY,
                open ? accentColor : UITheme.BORDER_DEFAULT,
                UITheme.PANEL_INNER_BORDER,
                UITheme.TEXT_PRIMARY,
                UITheme.TEXT_TERTIARY
            );
        } else {
            palette = UIStyleHelper.getDropdownFieldPalette(accentColor, hoverProgress, open, false);
        }
        int textColor = isOverSidebar ? UITheme.TEXT_TERTIARY : palette.textColor();
        int labelColor = includeValue && !isOverSidebar && !(hovered || open)
            ? UITheme.NODE_LABEL_COLOR
            : textColor;

        UIStyleHelper.drawFieldFrame(
            context,
            fieldLeft,
            fieldTop,
            fieldWidth,
            fieldHeight,
            host.getLowDetailAwareFieldPalette(
                isOverSidebar ? UITheme.BACKGROUND_SECONDARY : palette.backgroundColor(),
                isOverSidebar ? UITheme.BORDER_SUBTLE : palette.borderColor(),
                isOverSidebar ? UITheme.PANEL_INNER_BORDER : palette.innerBorderColor(),
                textColor,
                palette.placeholderColor(),
                isOverSidebar
            )
        );

        int textY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;
        int textX = fieldLeft + 4;
        int chevronCenterX = fieldLeft + fieldWidth - 8;
        int chevronCenterY = fieldTop + fieldHeight / 2;

        if (includeValue) {
            host.drawNodeText(context, textRenderer, Component.literal(label), textX, textY, labelColor);
            int valueStartX = textX + textRenderer.width(label) + 6;
            int maxValueWidth = Math.max(0, chevronCenterX - 5 - valueStartX);
            String displayValue = host.trimTextToWidth(value != null ? value : "", textRenderer, maxValueWidth);
            host.drawNodeText(context, textRenderer, Component.literal(displayValue), valueStartX, textY, textColor);
        } else {
            int maxLabelWidth = Math.max(0, fieldWidth - 20);
            String displayLabel = host.trimTextToWidth(label != null ? label : "", textRenderer, maxLabelWidth);
            host.drawNodeText(context, textRenderer, Component.literal(displayLabel), textX, textY, textColor);
        }

        UIStyleHelper.drawChevron(context, chevronCenterX, chevronCenterY, open, textColor);
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
            routineInput ? Component.translatable("pathmind.routine.input") : Component.translatable("pathmind.node.type.variable"),
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

    void renderInlineParameterContent(GuiGraphics context, Font textRenderer, Node node, boolean isOverSidebar,
                                      int mouseX, int mouseY, int x, int y, int width, int height) {
        if (host.shouldShowParameters(node)) {
            int paramBgColor = isOverSidebar ? UITheme.BACKGROUND_SECONDARY : UITheme.BACKGROUND_SIDEBAR; // Grey when over sidebar
            context.fill(x + 3, y + 16, x + width - 3, y + height - 3, paramBgColor);

            // Render parameters
            int paramY = y + 18;
            List<NodeParameter> parameters = node.getParameters();
            if (host.isEditingParameterField() && host.parameterEditingNode() == node) {
                host.updateParameterCaretBlink();
            }

            if (node.supportsModeSelection()) {
                int fieldLeft = host.getParameterFieldLeft(node) - host.cameraX();
                int fieldTop = paramY;
                int fieldWidth = host.getParameterFieldWidth(node);
                int fieldHeight = host.getParameterFieldHeight();
                int fieldRight = fieldLeft + fieldWidth;
                int worldMouseX = host.screenToWorldX(mouseX);
                int worldMouseY = host.screenToWorldY(mouseY);

                int fieldBackground = isOverSidebar
                    ? UITheme.BACKGROUND_SECONDARY
                    : UITheme.BACKGROUND_SIDEBAR;
                int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
                int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
                int activeFieldBorder = host.selectedNodeAccentColor();
                boolean hovered = !isOverSidebar
                    && worldMouseX >= host.getParameterFieldLeft(node)
                    && worldMouseX <= host.getParameterFieldLeft(node) + fieldWidth
                    && worldMouseY >= fieldTop + host.cameraY()
                    && worldMouseY <= fieldTop + host.cameraY() + fieldHeight;
                float progress = host.getTextFieldHighlightProgress(node.getId() + "#modeInline", hovered, false);
                int backgroundColor = isOverSidebar
                    ? fieldBackground
                    : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
                int modeFieldBorderColor = isOverSidebar
                    ? fieldBorder
                    : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);

                context.fill(fieldLeft, fieldTop, fieldRight, fieldTop + fieldHeight, backgroundColor);
                DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, modeFieldBorderColor);

                int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
                int valueColor = isOverSidebar ? UITheme.TEXT_TERTIARY
                    : AnimationHelper.lerpColor(UITheme.TEXT_PRIMARY, UITheme.TEXT_HEADER, progress);
                String labelText = PathmindI18n.tr("pathmind.field.mode");
                int labelX = fieldLeft + 4;
                int labelY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;
                host.drawNodeText(context, textRenderer, Component.literal(labelText), labelX, labelY, labelColor);

                String modeValue = node.getMode() != null ? node.getMode().getDisplayName() : PathmindI18n.tr("pathmind.node.mode.select");
                int valueStartX = labelX + textRenderer.width(labelText) + 6;
                int maxValueWidth = Math.max(0, fieldRight - valueStartX - 4);
                String displayValue = host.trimTextToWidth(modeValue, textRenderer, maxValueWidth);
                int valueY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;
                host.drawNodeText(context, textRenderer, Component.literal(displayValue), valueStartX, valueY, valueColor);

                paramY += host.parameterInputHeight() + host.parameterInputGap();
            }

            if (host.isCombinedDirectionNode(node)) {
                host.renderDirectionModeTabs(context, textRenderer, node, isOverSidebar, paramY, mouseX, mouseY);
                paramY += host.directionModeTabHeight() + host.parameterInputGap();
            }

            if (host.isCombinedBooleanNode(node)) {
                host.renderBooleanModeTabs(context, textRenderer, node, isOverSidebar, paramY, mouseX, mouseY);
                paramY += host.directionModeTabHeight() + host.parameterInputGap();
            }

            for (int i = 0; i < parameters.size(); i++) {
                NodeParameter param = parameters.get(i);
                String displayLabel = node.getParameterLabel(param);
                if (displayLabel == null || displayLabel.isEmpty()) {
                    continue;
                }

                boolean editingThis = host.isEditingParameterField()
                    && host.parameterEditingNode() == node
                    && host.parameterEditingIndex() == i;

                int fieldLeft = host.getParameterFieldLeft(node) - host.cameraX();
                int fieldTop = paramY;
                int fieldWidth = host.getParameterFieldWidth(node);
                int fieldHeight = host.getParameterFieldHeight();
                int fieldRight = fieldLeft + fieldWidth;
                int worldMouseX = host.screenToWorldX(mouseX);
                int worldMouseY = host.screenToWorldY(mouseY);

                int fieldBackground = isOverSidebar
                    ? UITheme.BACKGROUND_SECONDARY
                    : UITheme.BACKGROUND_SIDEBAR;
                int activeFieldBackground = isOverSidebar ? UITheme.BACKGROUND_TERTIARY : UITheme.NODE_INPUT_BG_ACTIVE;
                int fieldBorder = isOverSidebar ? UITheme.BORDER_SUBTLE : UITheme.BORDER_DEFAULT;
                int activeFieldBorder = host.selectedNodeAccentColor();

                int labelColor = isOverSidebar ? UITheme.NODE_LABEL_DIMMED : UITheme.NODE_LABEL_COLOR;
                int valueColor = isOverSidebar ? UITheme.TEXT_TERTIARY : UITheme.TEXT_PRIMARY;

                int maxLabelWidth = Math.max(0, fieldWidth - 40);
                String labelText = host.getParameterLabelText(node, param, textRenderer, maxLabelWidth);
                int labelX = fieldLeft + 4;
                int labelY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;

                int valueStartX = host.getParameterValueStartX(node, param, textRenderer) - host.cameraX();
                int maxValueWidth = Math.max(0, fieldRight - valueStartX - 4);
                String value = editingThis ? host.parameterEditBuffer() : param.getStringValue();
                if (value == null) {
                    value = "";
                }
                boolean isPlayerParam = isPlayerParameter(node, param);
                boolean isMessageParam = isMessageParameter(node, param);
                boolean isSeedParam = isSeedParameter(node, param);
                boolean isGuiParam = isGuiParameter(node, param);
                boolean isMouseButtonParam = isMouseButtonParameter(node, param);
                boolean isHandParam = isHandParameter(node, param);
                boolean isAmountParam = isAmountParameter(node, param);
                boolean isAttributeDetectionDropdownParam = isAttributeDetectionDropdownParameter(node, i);
                boolean showPlayerPlaceholder = false;
                boolean showMessagePlaceholder = false;
                boolean showSeedPlaceholder = false;
                boolean showBlockItemPlaceholder = false;
                boolean showFabricEventPlaceholder = false;
                boolean showDirectionPlaceholder = false;
                boolean showBlockFacePlaceholder = false;
                boolean showGuiPlaceholder = false;
                boolean showMouseButtonPlaceholder = false;
                boolean showHandPlaceholder = false;
                boolean showAmountPlaceholder = false;
                boolean showTradePlaceholder = false;
                if (isPlayerParam) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && "Self".equalsIgnoreCase(value)));
                    showPlayerPlaceholder = showPlaceholder;
                }
                if (isMessageParam) {
                    showMessagePlaceholder = false;
                }
                if (isSeedParam) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && "Any".equalsIgnoreCase(value)));
                    showSeedPlaceholder = showPlaceholder;
                }
                if (isGuiParam) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && "Any".equalsIgnoreCase(value)));
                    showGuiPlaceholder = showPlaceholder;
                }
                if (isMouseButtonParam) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && host.isDefaultMouseButtonValue(value)));
                    showMouseButtonPlaceholder = showPlaceholder;
                }
                if (isHandParam) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && host.isDefaultHandValue(value)));
                    showHandPlaceholder = showPlaceholder;
                }
                if (isAmountParam) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && "0".equalsIgnoreCase(value)));
                    showAmountPlaceholder = showPlaceholder;
                }
                if (!editingThis && host.isTradeInlinePlaceholder(node, param, false)) {
                    showTradePlaceholder = true;
                }
                if (isBlockItemParameter(node, i)) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && host.isAnyBlockItemValue(value)));
                    showBlockItemPlaceholder = showPlaceholder;
                }
                if (isFabricEventSensorParameter(node, i)) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && "Any".equalsIgnoreCase(value)));
                    showFabricEventPlaceholder = showPlaceholder;
                }
                if (isDirectionParameter(node, i)) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && "north".equalsIgnoreCase(value)));
                    showDirectionPlaceholder = showPlaceholder;
                }
                if (isBlockFaceParameter(node, i)) {
                    boolean showPlaceholder = !editingThis
                        && (value.isEmpty() || (!param.isUserEdited() && "north".equalsIgnoreCase(value)));
                    showBlockFacePlaceholder = showPlaceholder;
                }
                if (!editingThis
                    && node.getType() == NodeType.PARAM_VILLAGER_TRADE
                    && ("Item".equalsIgnoreCase(param.getName()) || "Trade".equalsIgnoreCase(param.getName()))) {
                    value = host.formatVillagerTradeValue(value);
                }
                if (!editingThis && (value.isEmpty() || host.isAnyBlockItemValue(value)) && isBlockItemParameter(node, i)) {
                    value = (isBlockStateParameter(node, i) || isEntityStateParameter(node, i))
                        ? PathmindI18n.tr("pathmind.option.anyState")
                        : PathmindI18n.tr("pathmind.option.any");
                }
                if (!editingThis && isFabricEventSensorParameter(node, i)
                    && (value.isEmpty() || "Any".equalsIgnoreCase(value))) {
                    value = PathmindI18n.tr("pathmind.option.any");
                }
                if (!editingThis && isMouseButtonParam) {
                    value = host.formatMouseButtonValue(value);
                }
                if (!editingThis && isHandParam) {
                    value = host.formatHandValue(value);
                }
                if (!editingThis && isAttributeDetectionDropdownParam) {
                    value = host.formatAttributeDetectionInlineValue(node, param, value);
                }
                if (!editingThis && isBooleanLiteralParameter(node, i) && value.isEmpty()) {
                    value = PathmindI18n.tr("pathmind.option.true");
                }
                if (!editingThis && isBooleanLiteralParameter(node, i) && !value.isEmpty()) {
                    value = Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
                }
                if (!editingThis && isBlockFaceParameter(node, i) && !value.isEmpty()) {
                    value = Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
                }
                if (showPlayerPlaceholder || showMessagePlaceholder || showSeedPlaceholder
                    || showBlockItemPlaceholder || showFabricEventPlaceholder
                    || showGuiPlaceholder || showMouseButtonPlaceholder || showHandPlaceholder || showAmountPlaceholder
                    || showDirectionPlaceholder || showBlockFacePlaceholder || showTradePlaceholder) {
                    if (isBlockStateParameter(node, i) || isEntityStateParameter(node, i)) {
                        value = PathmindI18n.tr("pathmind.option.anyState");
                    } else if (showPlayerPlaceholder) {
                        value = PathmindI18n.tr("pathmind.option.self");
                    } else if (showMouseButtonPlaceholder) {
                        value = PathmindI18n.tr("pathmind.option.mouse.left");
                    } else if (showHandPlaceholder) {
                        value = PathmindI18n.tr("pathmind.option.hand.main");
                    } else if (showTradePlaceholder) {
                        value = "1";
                    } else if (showAmountPlaceholder) {
                        value = "0";
                    } else if (showBlockFacePlaceholder) {
                        value = PathmindI18n.tr("pathmind.option.direction.north");
                    } else if (showDirectionPlaceholder) {
                        value = PathmindI18n.tr("pathmind.option.direction.north");
                    } else {
                        value = PathmindI18n.tr("pathmind.option.any");
                    }
                    valueColor = UITheme.TEXT_TERTIARY;
                }
                boolean inlineDropdown = isInlineDropdownParameter(node, i);
                boolean inlineDropdownOpen = inlineDropdown
                    && host.parameterDropdownOpen()
                    && host.parameterDropdownNode() == node
                    && host.parameterDropdownIndex() == i;
                boolean hovered = !isOverSidebar
                    && worldMouseX >= fieldLeft + host.cameraX()
                    && worldMouseX <= fieldLeft + host.cameraX() + fieldWidth
                    && worldMouseY >= fieldTop + host.cameraY()
                    && worldMouseY <= fieldTop + host.cameraY() + fieldHeight;
                float progress = host.getTextFieldHighlightProgress(
                    node.getId() + "#param:" + i,
                    hovered,
                    editingThis || inlineDropdownOpen
                );
                int backgroundColor;
                int parameterFieldBorderColor;
                if (host.compactViewportMode() && !isOverSidebar) {
                    backgroundColor = editingThis || inlineDropdownOpen
                        ? UITheme.BACKGROUND_INPUT
                        : UITheme.BACKGROUND_SECONDARY;
                    parameterFieldBorderColor = editingThis || inlineDropdownOpen
                        ? host.selectedNodeAccentColor()
                        : UITheme.BORDER_DEFAULT;
                } else {
                    backgroundColor = isOverSidebar
                        ? fieldBackground
                        : AnimationHelper.lerpColor(fieldBackground, activeFieldBackground, progress);
                    parameterFieldBorderColor = isOverSidebar
                        ? fieldBorder
                        : AnimationHelper.lerpColor(fieldBorder, activeFieldBorder, progress);
                }
                if (inlineDropdownOpen && !isOverSidebar) {
                    parameterFieldBorderColor = host.selectedNodeAccentColor();
                }

                context.fill(fieldLeft, fieldTop, fieldRight, fieldTop + fieldHeight, backgroundColor);
                DrawContextBridge.drawBorderInLayer(context, fieldLeft, fieldTop, fieldWidth, fieldHeight, parameterFieldBorderColor);

                if (!host.compactViewportMode() || isOverSidebar) {
                    labelColor = isOverSidebar ? labelColor
                        : AnimationHelper.lerpColor(labelColor, UITheme.TEXT_HEADER, progress * 0.6f);
                    valueColor = isOverSidebar ? valueColor
                        : AnimationHelper.lerpColor(valueColor, UITheme.TEXT_HEADER, progress);
                }
                if (!labelText.isEmpty()) {
                    host.drawNodeText(context, textRenderer, Component.literal(labelText), labelX, labelY, labelColor);
                }

                String arrow = inlineDropdown ? (inlineDropdownOpen ? "v" : "^") : "";
                int arrowWidth = inlineDropdown ? textRenderer.width(arrow) : 0;
                if (inlineDropdown) {
                    maxValueWidth = Math.max(0, maxValueWidth - arrowWidth - 8);
                }
                String displayValue = editingThis
                    ? value
                    : host.trimTextToWidth(value, textRenderer, maxValueWidth);
                int paramVariableHighlightColor = isOverSidebar ? host.toGrayscale(host.selectedNodeAccentColor(), 0.85f) : host.selectedNodeAccentColor();
                Set<String> paramVariableNames = host.collectRuntimeVariableNames(node);
                InlineVariableRender paramRenderData = null;
                boolean allowRelativeMarker = RelativeInputSupport.supportsRelativeCoordinate(node, param.getName())
                    || RelativeInputSupport.supportsRelativeLook(node, param.getName());
                if (InlineVariableRenderer.shouldBuildInlineExpressionRender(
                    host.compactViewportMode(), value, paramVariableNames, allowRelativeMarker
                )) {
                    InlineVariableRender candidate = buildInlineVariableRender(
                        value,
                        paramVariableNames,
                        valueColor,
                        paramVariableHighlightColor,
                        allowRelativeMarker
                    );
                    if (editingThis) {
                        paramRenderData = candidate;
                        displayValue = paramRenderData.displayText;
                    } else if (textRenderer.width(candidate.displayText) <= maxValueWidth) {
                        paramRenderData = candidate;
                        displayValue = paramRenderData.displayText;
                    } else if (isSingleKnownInlineVariableReference(value, paramVariableNames)) {
                        displayValue = host.trimTextToWidth(candidate.displayText, textRenderer, maxValueWidth);
                        valueColor = paramVariableHighlightColor;
                    }
                }
                int valueY = fieldTop + (fieldHeight - textRenderer.lineHeight) / 2;

                if (editingThis && host.hasParameterSelection()) {
                    int start = host.parameterSelectionStart();
                    int end = host.parameterSelectionEnd();
                    if (paramRenderData != null) {
                        start = paramRenderData.toDisplayIndex(start);
                        end = paramRenderData.toDisplayIndex(end);
                    }
                    start = Mth.clamp(start, 0, displayValue.length());
                    end = Mth.clamp(end, 0, displayValue.length());
                    if (start != end) {
                        int selectionStartX = valueStartX + textRenderer.width(displayValue.substring(0, start));
                        int selectionEndX = valueStartX + textRenderer.width(displayValue.substring(0, end));
                        context.fill(selectionStartX, fieldTop + 2, selectionEndX, fieldTop + fieldHeight - 2, UITheme.TEXT_SELECTION_BG);
                    }
                }

                if (paramRenderData != null && host.shouldRenderNodeText()) {
                    paramRenderData.draw(context, textRenderer, valueStartX, valueY);
                } else {
                    host.drawNodeText(context, textRenderer, Component.literal(displayValue), valueStartX, valueY, valueColor);
                }

                if (inlineDropdown) {
                    int arrowX = fieldRight - arrowWidth - 4;
                    host.drawNodeText(context, textRenderer, Component.literal(arrow), arrowX, valueY, valueColor);
                }

                if (editingThis && host.parameterCaretVisible()) {
                    int caretIndex = host.parameterCaretPosition();
                    if (paramRenderData != null) {
                        caretIndex = paramRenderData.toDisplayIndex(caretIndex);
                    }
                    caretIndex = Mth.clamp(caretIndex, 0, displayValue.length());
                    int caretX = valueStartX + textRenderer.width(displayValue.substring(0, caretIndex));
                    caretX = Math.min(caretX, fieldRight - 2);
                    int caretBaseline = Math.min(valueY + textRenderer.lineHeight - 1, fieldTop + fieldHeight - 2);
                    UIStyleHelper.drawTextCaretAtBaseline(context, textRenderer, caretX, caretBaseline, fieldRight - 2, UITheme.CARET_COLOR);
                }

                if (editingThis && (isBlockItemParameter(node, i)
                    || isMouseButtonParameter(node, param)
                    || isHandParameter(node, param)
                    || isGuiParameter(node, param)
                    || isDirectionParameter(node, i)
                    || isAttributeDetectionDropdownParameter(node, i)
                    || isBlockFaceParameter(node, i)
                    || isFabricEventSensorParameter(node, i)
                    || isVillagerProfessionParameter(node, i)
                    || isVillagerTradeParameter(node, i)
                    || isVillagerTradeVariantParameter(node, i))) {
                    host.updateParameterDropdown(node, i, textRenderer, fieldLeft, fieldTop, fieldWidth, fieldHeight);
                }

                paramY += host.parameterInputHeight() + host.parameterInputGap();
            }
            if (node.hasRandomRoundingField()) {
                host.renderRandomRoundingField(context, textRenderer, node, isOverSidebar);
            }
            if (node.hasParameterSlot()) {
                int slotCount = node.getParameterSlotCount();
                for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                    host.renderParameterSlot(context, textRenderer, node, isOverSidebar, slotIndex);
                }
            }
            if (node.hasAmountInputField()) {
                host.renderAmountInputField(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
            }
            if (node.hasPopupEditButton()) {
                host.renderPopupEditButton(context, textRenderer, node, isOverSidebar, mouseX, mouseY);
            }
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
            display = PathmindI18n.tr("pathmind.field.enterName");
        } else {
            display = value;
        }
        int eventNameVariableHighlightColor = isOverSidebar ? host.toGrayscale(host.selectedNodeAccentColor(), 0.85f) : host.selectedNodeAccentColor();
        Set<String> eventNameVariableNames = host.collectRuntimeVariableNames(node);
        InlineVariableRender eventNameRenderData = null;
        boolean highlightPlainEventName = false;
        if (InlineVariableRenderer.shouldBuildInlineExpressionRender(
            host.compactViewportMode(), value, eventNameVariableNames, false
        )) {
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
                renderEventNamePreview(context, textRenderer, display, textX, textY, textColor, boxRight - boxLeft - 8);
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
            display = PathmindI18n.tr("pathmind.field.enterName");
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
            renderEventNamePreview(context, textRenderer, display, textX, textY, textColor, boxRight - boxLeft - 8);
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

    private void renderEventNamePreview(GuiGraphics context, Font textRenderer, String value, int x, int y,
                                        int baseColor, int maxWidth) {
        if (value == null || value.isEmpty()) {
            host.drawNodeText(context, textRenderer, Component.translatable("pathmind.field.enterName"), x, y, baseColor);
            return;
        }
        if (textRenderer.width(value) <= maxWidth) {
            host.drawNodeText(context, textRenderer, Component.literal(value), x, y, baseColor);
            return;
        }

        String trimmed = host.trimTextToWidth(value, textRenderer, maxWidth);
        host.drawNodeText(context, textRenderer, Component.literal(trimmed), x, y, baseColor);
        int trimmedWidth = textRenderer.width(trimmed);

        String tail = "..";
        int tailWidth = textRenderer.width(tail);
        if (trimmedWidth + tailWidth + 4 >= maxWidth) {
            return;
        }

        String suffix = value.substring(Math.max(0, value.length() - 4));
        String tailText = tail + suffix;
        int tailTextWidth = textRenderer.width(tailText);
        if (trimmedWidth + tailTextWidth + 4 > maxWidth) {
            return;
        }
        int tailX = x + maxWidth - tailTextWidth;
        int hintColor = host.toGrayscale(baseColor, 0.85f);
        host.drawNodeText(context, textRenderer, Component.literal(tailText), tailX, y, hintColor);
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
