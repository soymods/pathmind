package com.pathmind.ui.graph;

import static com.pathmind.util.PathmindI18n.tr;

import static com.pathmind.ui.graph.ParameterDropdownOptions.getAttributeDetectionTargetKind;
import static com.pathmind.ui.graph.ParameterTypeClassifier.*;

import com.pathmind.nodes.AttributeDetectionConfig;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeParameter;
import com.pathmind.nodes.NodeType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Owns inline parameter text-editing state and behavior. All coordination with
 * sibling editors and graph persistence flows downward through {@link Host}.
 */
final class ParameterTextEditorController {
    private static final long CARET_BLINK_INTERVAL_MS = 500;

    interface Host {
        boolean canEditInlineParameterFields(Node node);
        void closeModeDropdown();
        void closeSchematicDropdown();
        void closeRunPresetDropdown();
        void closeRandomRoundingDropdown();
        void closeParameterDropdown();
        void clearParameterDropdownSuppression();
        void stopCoordinateEditing(boolean commit);
        void stopAmountEditing(boolean commit);
        void stopStopTargetEditing(boolean commit);
        void stopMessageEditing(boolean commit);
        void stopVariableEditing(boolean commit);
        void stopEventNameEditing(boolean commit);
        void stopStickyNoteEditing(boolean commit);
        void notifyNodeParametersChanged(Node node);
        void updateParameterFieldContentWidth(Node node, Font renderer, int index, String value);
        Font getClientTextRenderer();
        boolean isTextShortcutDown(int modifiers);
        String getClipboardText();
        void setClipboardText(String text);
    }

    private final Host host;
    private Node node;
    private int index = -1;
    private String buffer = "";
    private String originalValue = "";
    private long caretLastToggleTime;
    private boolean caretVisible = true;
    private int caretPosition;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private int selectionAnchor = -1;

    ParameterTextEditorController(Host host) {
        this.host = host;
    }

    boolean isEditing() { return node != null && index >= 0; }
    Node getNode() { return node; }
    int getIndex() { return index; }
    String getBuffer() { return buffer; }
    boolean isCaretVisible() { return caretVisible; }
    int getCaretPosition() { return caretPosition; }
    int getSelectionStart() { return selectionStart; }
    int getSelectionEnd() { return selectionEnd; }

    void replaceBuffer(String value, int newCaretPosition) {
        buffer = value;
        setCaretPosition(newCaretPosition);
    }

    void updateCaretBlink() {
        long now = System.currentTimeMillis();
        if (now - caretLastToggleTime >= CARET_BLINK_INTERVAL_MS) {
            caretVisible = !caretVisible;
            caretLastToggleTime = now;
        }
    }

    void start(Node candidate, int candidateIndex) {
        if (candidate == null || !host.canEditInlineParameterFields(candidate)
            || candidateIndex < 0 || candidateIndex >= candidate.getParameters().size()) {
            stop(false);
            return;
        }
        host.closeModeDropdown();
        host.closeSchematicDropdown();
        host.closeRunPresetDropdown();
        host.closeRandomRoundingDropdown();
        if (isEditing()) {
            if (node == candidate && index == candidateIndex) {
                host.clearParameterDropdownSuppression();
                return;
            }
            Node previousNode = node;
            boolean changed = apply();
            if (changed) {
                host.notifyNodeParametersChanged(previousNode);
            }
        }
        host.stopCoordinateEditing(true);
        host.stopAmountEditing(true);
        host.stopStopTargetEditing(true);
        host.stopMessageEditing(true);
        host.stopVariableEditing(true);
        host.stopEventNameEditing(true);
        host.stopStickyNoteEditing(true);

        node = candidate;
        index = candidateIndex;
        NodeParameter parameter = candidate.getParameters().get(candidateIndex);
        String initialValue = parameter != null ? parameter.getStringValue() : "";
        buffer = initialValue;
        if (parameter != null && (isPlayerParameter(candidate, parameter)
            || isMessageParameter(candidate, parameter)
            || isSeedParameter(candidate, parameter)
            || isAmountParameter(candidate, parameter)
            || isTradeInlineParameter(candidate, parameter)
            || isMouseButtonParameter(candidate, parameter)
            || isHandParameter(candidate, parameter)
            || isGuiParameter(candidate, parameter)
            || isDirectionParameter(candidate, candidateIndex)
            || isAttributeDetectionBooleanValueParameter(candidate, candidateIndex)
            || isBlockFaceParameter(candidate, candidateIndex)
            || isBlockItemParameter(candidate, candidateIndex)
            || isFabricEventSensorParameter(candidate, candidateIndex))) {
            if (buffer == null || buffer.isEmpty()
                || "Any".equalsIgnoreCase(buffer)
                || "Self".equalsIgnoreCase(buffer)
                || "Any State".equalsIgnoreCase(buffer)
                || "North".equalsIgnoreCase(buffer)
                || "True".equalsIgnoreCase(buffer)
                || "Main".equalsIgnoreCase(buffer)
                || isDefaultMouseButtonValue(buffer)
                || "0".equals(buffer)
                || (isTradeInlineParameter(candidate, parameter) && "1".equals(buffer))) {
                buffer = "";
            }
        }
        originalValue = initialValue != null ? initialValue : "";
        resetCaretBlink();
        caretPosition = buffer.length();
        selectionAnchor = -1;
        selectionStart = -1;
        selectionEnd = -1;
        host.clearParameterDropdownSuppression();
        host.updateParameterFieldContentWidth(node, host.getClientTextRenderer(), index, buffer);
    }

    void stop(boolean commit) {
        if (!isEditing()) {
            return;
        }
        Node editedNode = node;
        boolean changed = commit ? apply() : revert();
        if (commit && changed) {
            host.notifyNodeParametersChanged(editedNode);
        }
        host.updateParameterFieldContentWidth(editedNode, host.getClientTextRenderer(), -1, null);
        node = null;
        index = -1;
        buffer = "";
        originalValue = "";
        caretVisible = true;
        caretPosition = 0;
        selectionAnchor = -1;
        selectionStart = -1;
        selectionEnd = -1;
        host.closeParameterDropdown();
        host.clearParameterDropdownSuppression();
    }

    boolean apply() {
        if (!isEditing() || index < 0 || index >= node.getParameters().size()) {
            return false;
        }
        NodeParameter parameter = node.getParameters().get(index);
        String value = buffer == null ? "" : buffer;
        String previous = parameter != null ? parameter.getStringValue() : "";
        String appliedValue = value;
        if (parameter != null) {
            boolean player = isPlayerParameter(node, parameter);
            boolean direction = isDirectionParameter(node, index);
            boolean anyLike = isSeedParameter(node, parameter)
                || isGuiParameter(node, parameter)
                || isFabricEventSensorParameter(node, index);
            boolean blockFace = isBlockFaceParameter(node, index);
            boolean booleanLiteral = isBooleanLiteralParameter(node, index);
            boolean attribute = isAttributeDetectionAttributeParameter(node, index);
            boolean attributeBoolean = isAttributeDetectionBooleanValueParameter(node, index);
            boolean mouseButton = isMouseButtonParameter(node, parameter);
            boolean hand = isHandParameter(node, parameter);
            boolean amount = isAmountParameter(node, parameter);
            boolean tradeInline = isTradeInlineParameter(node, parameter);
            boolean blockItem = isBlockItemParameter(node, index);
            String trimmed = value.trim();
            if (amount) {
                if (trimmed.isEmpty()) {
                    appliedValue = setDefault(parameter, "0");
                } else {
                    setUser(parameter, value);
                }
            } else if (tradeInline) {
                if (trimmed.isEmpty() || "1".equals(trimmed)) {
                    appliedValue = setDefault(parameter, "1");
                } else {
                    setUser(parameter, value);
                }
            } else if (player) {
                if (trimmed.isEmpty() || "Self".equalsIgnoreCase(trimmed)) {
                    appliedValue = setDefault(parameter, "Self");
                } else {
                    setUser(parameter, value);
                }
            } else if (mouseButton) {
                if (trimmed.isEmpty() || isDefaultMouseButtonValue(trimmed)) {
                    appliedValue = setDefault(parameter, "Left");
                } else {
                    setUser(parameter, value);
                }
            } else if (hand) {
                if (trimmed.isEmpty() || isDefaultHandValue(trimmed)) {
                    appliedValue = setDefault(parameter, "main");
                } else {
                    String normalized = normalizeHand(trimmed);
                    setUser(parameter, normalized);
                }
            } else if (blockFace) {
                if (trimmed.isEmpty() || "North".equalsIgnoreCase(trimmed)) {
                    appliedValue = setDefault(parameter, "north");
                } else {
                    setUser(parameter, trimmed.toLowerCase(Locale.ROOT));
                }
            } else if (direction) {
                if (trimmed.isEmpty()) {
                    appliedValue = setDefault(parameter, "north");
                } else {
                    setUser(parameter, trimmed.toLowerCase(Locale.ROOT));
                }
            } else if (booleanLiteral) {
                if (trimmed.isEmpty() || "true".equalsIgnoreCase(trimmed)) {
                    appliedValue = setDefault(parameter, "true");
                } else {
                    if ("1".equals(trimmed)) {
                        appliedValue = "true";
                        setUser(parameter, appliedValue);
                    } else if ("0".equals(trimmed)) {
                        appliedValue = "false";
                        setUser(parameter, appliedValue);
                    } else {
                        setUser(parameter, trimmed.toLowerCase(Locale.ROOT));
                    }
                }
            } else if (attribute) {
                AttributeDetectionConfig.TargetKind targetKind = getAttributeDetectionTargetKind(node);
                AttributeDetectionConfig.AttributeOption option = AttributeDetectionConfig.getAttribute(trimmed);
                if (option == null || (targetKind != null && !option.supports(targetKind))) {
                    option = AttributeDetectionConfig.getDefaultAttribute(targetKind);
                    appliedValue = setDefault(parameter, option.id());
                } else {
                    appliedValue = option.id();
                    setUser(parameter, appliedValue);
                }
            } else if (attributeBoolean) {
                String normalized = trimmed.isEmpty() || "true".equalsIgnoreCase(trimmed) || "1".equals(trimmed)
                    ? "true"
                    : "false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)
                        ? "false" : trimmed.toLowerCase(Locale.ROOT);
                if (!"true".equals(normalized) && !"false".equals(normalized)) {
                    normalized = "true";
                }
                appliedValue = normalized;
                if ("true".equals(normalized) && trimmed.isEmpty()) {
                    setDefault(parameter, appliedValue);
                } else {
                    setUser(parameter, appliedValue);
                }
            } else if (anyLike || blockItem) {
                boolean emptyOrAny = trimmed.isEmpty()
                    || "Any".equalsIgnoreCase(trimmed)
                    || "Any State".equalsIgnoreCase(trimmed);
                if (emptyOrAny) {
                    appliedValue = blockItem && (isBlockStateParameter(node, index)
                        || isEntityStateParameter(node, index)) ? "Any State" : "Any";
                    setDefault(parameter, appliedValue);
                } else {
                    setUser(parameter, value);
                }
            } else {
                setUser(parameter, value);
            }
        }
        node.recalculateDimensions();
        return !Objects.equals(previous, appliedValue);
    }

    void refreshStatePreview() {
        if (!isEditing() || index < 0 || index >= node.getParameters().size()
            || (!isBlockParameter(node, index) && !isEntityParameter(node, index))) {
            return;
        }
        NodeParameter parameter = node.getParameters().get(index);
        if (parameter != null) {
            parameter.setStringValueFromUser(buffer == null ? "" : buffer);
            node.recalculateDimensions();
        }
    }

    boolean handleKeyPressed(int keyCode, int modifiers) {
        if (!isEditing()) {
            return false;
        }
        boolean shiftHeld = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean controlHeld = host.isTextShortcutDown(modifiers);
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!deleteSelection()) {
                    if (controlHeld && caretPosition > 0) {
                        int deleteTo = findPreviousWordBoundary(buffer, caretPosition);
                        buffer = buffer.substring(0, deleteTo) + buffer.substring(caretPosition);
                        setCaretPosition(deleteTo);
                        changed();
                    } else if (caretPosition > 0 && !buffer.isEmpty()) {
                        buffer = buffer.substring(0, caretPosition - 1) + buffer.substring(caretPosition);
                        setCaretPosition(caretPosition - 1);
                        changed();
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!deleteSelection() && caretPosition < buffer.length()) {
                    buffer = buffer.substring(0, caretPosition) + buffer.substring(caretPosition + 1);
                    setCaretPosition(caretPosition);
                    changed();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> { moveCaretTo(caretPosition - 1, shiftHeld); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { moveCaretTo(caretPosition + 1, shiftHeld); return true; }
            case GLFW.GLFW_KEY_HOME -> { moveCaretTo(0, shiftHeld); return true; }
            case GLFW.GLFW_KEY_END -> { moveCaretTo(buffer.length(), shiftHeld); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                stop(true);
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (controlHeld) { selectAll(); return true; }
            }
            case GLFW.GLFW_KEY_C -> {
                if (controlHeld) {
                    if (isCoordinateNode(node) && !hasSelection()) copyCoordinateValues();
                    else copySelection();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (controlHeld) { cutSelection(); return true; }
            }
            case GLFW.GLFW_KEY_V -> {
                if (controlHeld) {
                    Font renderer = host.getClientTextRenderer();
                    if (renderer != null) {
                        String clipboard = host.getClipboardText();
                        if (!smartPasteCoordinates(clipboard)) insert(clipboard, renderer);
                    }
                    return true;
                }
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (cycleCoordinateAxis(shiftHeld)) return true;
            }
            default -> { return false; }
        }
        return false;
    }

    boolean handleCharTyped(char chr, Font renderer) {
        return isEditing() && chr != '\n' && chr != '\r' && insert(String.valueOf(chr), renderer);
    }

    boolean isTradeInlinePlaceholder(Node candidate, NodeParameter parameter, boolean editing) {
        if (!isTradeInlineParameter(candidate, parameter)) return false;
        String value = parameter.getStringValue();
        if (editing && isEditing() && node == candidate && index >= 0
            && index < candidate.getParameters().size()
            && candidate.getParameters().get(index) == parameter) {
            value = buffer;
        }
        return value == null || value.isEmpty() || (!parameter.isUserEdited() && "1".equals(value));
    }

    static boolean isDefaultMouseButtonValue(String value) {
        return value == null || value.isEmpty()
            || "GLFW_MOUSE_BUTTON_LEFT".equalsIgnoreCase(value)
            || "LEFT".equalsIgnoreCase(value);
    }

    static boolean isDefaultHandValue(String value) {
        return value == null || value.isEmpty()
            || "main".equalsIgnoreCase(value)
            || "main_hand".equalsIgnoreCase(value)
            || "main-hand".equalsIgnoreCase(value)
            || "main hand".equalsIgnoreCase(value);
    }

    static String formatMouseButtonValue(String value) {
        if (value == null || value.isEmpty()) return tr("pathmind.option.mouse.left");
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "GLFW_MOUSE_BUTTON_LEFT", "LEFT" -> tr("pathmind.option.mouse.left");
            case "GLFW_MOUSE_BUTTON_RIGHT", "RIGHT" -> tr("pathmind.option.mouse.right");
            case "GLFW_MOUSE_BUTTON_MIDDLE", "MIDDLE" -> tr("pathmind.option.mouse.middle");
            case "GLFW_MOUSE_BUTTON_4", "BUTTON_4" -> tr("pathmind.option.mouse.button4");
            case "GLFW_MOUSE_BUTTON_5", "BUTTON_5" -> tr("pathmind.option.mouse.button5");
            case "GLFW_MOUSE_BUTTON_6", "BUTTON_6" -> tr("pathmind.option.mouse.button6");
            case "GLFW_MOUSE_BUTTON_7", "BUTTON_7" -> tr("pathmind.option.mouse.button7");
            case "GLFW_MOUSE_BUTTON_8", "BUTTON_8" -> tr("pathmind.option.mouse.button8");
            default -> value;
        };
    }

    static String formatHandValue(String value) {
        if (isDefaultHandValue(value) || value == null || value.isEmpty()) return tr("pathmind.option.hand.main");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "off".equals(normalized) || "offhand".equals(normalized)
            || "off_hand".equals(normalized) || "off-hand".equals(normalized)
            || "off hand".equals(normalized) ? tr("pathmind.option.hand.offhand") : value;
    }

    static boolean isAnyBlockItemValue(String value) {
        if (value == null) return true;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "Any".equalsIgnoreCase(trimmed) || "Any State".equalsIgnoreCase(trimmed);
    }

    static boolean isMoveItemAllAmountValue(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "0".equals(trimmed)
            || "all".equalsIgnoreCase(trimmed)
            || "any".equalsIgnoreCase(trimmed);
    }

    private String setDefault(NodeParameter parameter, String value) {
        parameter.setStringValue(value);
        parameter.setUserEdited(false);
        node.setParameterValueAndPropagate(parameter.getName(), value);
        return value;
    }

    private void setUser(NodeParameter parameter, String value) {
        parameter.setStringValueFromUser(value);
        node.setParameterValueAndPropagate(parameter.getName(), value);
    }

    private boolean revert() {
        if (!isEditing() || index < 0 || index >= node.getParameters().size()) return false;
        NodeParameter parameter = node.getParameters().get(index);
        String previous = parameter != null ? parameter.getStringValue() : "";
        if (parameter != null) {
            parameter.setStringValue(originalValue);
            node.setParameterValueAndPropagate(parameter.getName(), originalValue);
        }
        node.recalculateDimensions();
        return !Objects.equals(previous, originalValue);
    }

    private String normalizeHand(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("offhand".equals(normalized) || "off_hand".equals(normalized)
            || "off-hand".equals(normalized) || "off hand".equals(normalized) || "off".equals(normalized)) {
            return "offhand";
        }
        if ("main_hand".equals(normalized) || "main-hand".equals(normalized)
            || "main hand".equals(normalized) || "mainhand".equals(normalized)) {
            return "main";
        }
        return normalized;
    }

    private boolean insert(String text, Font renderer) {
        if (!isEditing() || renderer == null || text == null || text.isEmpty()) return false;
        String filtered = text.replace("\r", "").replace("\n", "");
        if (filtered.isEmpty()) return false;
        String working = buffer;
        int caret = caretPosition;
        if (hasSelection()) {
            working = working.substring(0, selectionStart) + working.substring(selectionEnd);
            caret = selectionStart;
        }
        for (int i = 0; i < filtered.length(); i++) {
            working = working.substring(0, caret) + filtered.charAt(i) + working.substring(caret);
            caret++;
        }
        buffer = working;
        setCaretPosition(caret);
        changed(renderer);
        return true;
    }

    private void changed() { changed(host.getClientTextRenderer()); }
    private void changed(Font renderer) {
        host.updateParameterFieldContentWidth(node, renderer, index, buffer);
        refreshStatePreview();
        host.clearParameterDropdownSuppression();
    }

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
    }

    private boolean deleteSelection() {
        if (!hasSelection()) return false;
        int start = selectionStart;
        buffer = buffer.substring(0, selectionStart) + buffer.substring(selectionEnd);
        setCaretPosition(start);
        changed();
        return true;
    }

    private void selectAll() {
        selectionAnchor = 0;
        if (buffer.isEmpty()) {
            resetSelection();
        } else {
            selectionStart = 0;
            selectionEnd = buffer.length();
        }
        caretPosition = buffer.length();
        resetCaretBlink();
    }

    private void copySelection() {
        if (hasSelection()) host.setClipboardText(buffer.substring(selectionStart, selectionEnd));
    }

    private void cutSelection() {
        if (hasSelection()) {
            copySelection();
            deleteSelection();
        }
    }

    private void setCaretPosition(int position) {
        caretPosition = Mth.clamp(position, 0, buffer.length());
        selectionAnchor = -1;
        resetSelection();
        resetCaretBlink();
    }

    private void moveCaretTo(int position, boolean extendSelection) {
        position = Mth.clamp(position, 0, buffer.length());
        if (extendSelection) {
            if (selectionAnchor == -1) selectionAnchor = caretPosition;
            int start = Math.min(selectionAnchor, position);
            int end = Math.max(selectionAnchor, position);
            if (start == end) resetSelection();
            else { selectionStart = start; selectionEnd = end; }
        } else {
            selectionAnchor = -1;
            resetSelection();
        }
        caretPosition = position;
        resetCaretBlink();
        host.clearParameterDropdownSuppression();
    }

    private void resetSelection() { selectionStart = -1; selectionEnd = -1; }
    private void resetCaretBlink() {
        caretVisible = true;
        caretLastToggleTime = System.currentTimeMillis();
    }

    private boolean smartPasteCoordinates(String clipboard) {
        if (!isEditing() || !isCoordinateNode(node) || clipboard == null || clipboard.isEmpty()) return false;
        List<NodeParameter> parameters = node.getParameters();
        String[] parts = clipboard.trim().split("[\\s,]+");
        if (parts.length != parameters.size()) return false;
        String[] parsed = new String[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            String cleaned = cleanCoordinateToken(parts[i].trim());
            if (cleaned.isEmpty() || !isValidCoordinateValue(cleaned)) return false;
            parsed[i] = cleaned;
        }
        Node editedNode = node;
        stop(false);
        for (int i = 0; i < parameters.size(); i++) {
            editedNode.setParameterValueAndPropagate(parameters.get(i).getName(), parsed[i]);
        }
        editedNode.recalculateDimensions();
        host.notifyNodeParametersChanged(editedNode);
        return true;
    }

    private void copyCoordinateValues() {
        List<NodeParameter> parameters = node.getParameters();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) result.append(", ");
            String value = i == index ? buffer : parameters.get(i).getStringValue();
            result.append(value == null ? "" : value);
        }
        host.setClipboardText(result.toString());
    }

    private boolean cycleCoordinateAxis(boolean backward) {
        if (!isEditing() || !isCoordinateNode(node) || node.getParameters().size() <= 1) return false;
        Node editedNode = node;
        int next = (index + (backward ? -1 : 1) + editedNode.getParameters().size())
            % editedNode.getParameters().size();
        start(editedNode, next);
        return true;
    }

    private static boolean isCoordinateNode(Node candidate) {
        return candidate != null && candidate.getType() == NodeType.PARAM_COORDINATE;
    }

    private static String cleanCoordinateToken(String token) {
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isDigit(c) || (c == '-' && i == 0)) cleaned.append(c);
        }
        return cleaned.toString();
    }

    private static boolean isValidCoordinateValue(String value) {
        if (value.isEmpty() || "-".equals(value)) return true;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start >= value.length()) return false;
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    static int findPreviousWordBoundary(String text, int fromPosition) {
        if (text == null || fromPosition <= 0) return 0;
        int position = fromPosition - 1;
        while (position > 0 && Character.isWhitespace(text.charAt(position))) position--;
        if (position >= 0 && Character.isLetterOrDigit(text.charAt(position))) {
            while (position > 0 && Character.isLetterOrDigit(text.charAt(position - 1))) position--;
        } else if (position >= 0) {
            while (position > 0 && !Character.isLetterOrDigit(text.charAt(position - 1))
                && !Character.isWhitespace(text.charAt(position - 1))) position--;
        }
        return position;
    }
}
