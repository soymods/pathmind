package com.pathmind.ui.overlay;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.execution.ExecutionManager.RuntimeVariable;
import com.pathmind.execution.ExecutionManager.RuntimeVariableEntry;
import com.pathmind.nodes.NodeType;
import com.pathmind.ui.animation.AnimatedValue;
import com.pathmind.ui.animation.AnimationHelper;
import com.pathmind.ui.theme.UITheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.pathmind.util.DrawContextBridge;

/**
 * HUD overlay that displays active runtime variables in the top left corner.
 */
public class VariablesOverlay {
    private static final int OVERLAY_WIDTH = 190;
    private static final int MARGIN = 10;
    private static final int PADDING = 6;
    private static final int LINE_SPACING = 2;
    private static final int SLIDE_OFFSET = 12;
    private static final int OPEN_DURATION_MS = 180;
    private static final int CLOSE_DURATION_MS = 140;

    private final ExecutionManager executionManager;
    private final AnimatedValue visibility;

    public VariablesOverlay() {
        this.executionManager = ExecutionManager.getInstance();
        this.visibility = new AnimatedValue(0f, AnimationHelper::easeOutCubic);
    }

    public void render(DrawContext context, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        List<RuntimeVariableEntry> entries = executionManager.getRuntimeVariableEntries();
        List<String> lines = entries.isEmpty() ? List.of() : buildDisplayLines(entries);
        boolean shouldShow = !lines.isEmpty();

        visibility.animateTo(shouldShow ? 1f : 0f, shouldShow ? OPEN_DURATION_MS : CLOSE_DURATION_MS);
        visibility.tick();

        float progress = visibility.getValue();
        if (progress <= 0.001f) {
            return;
        }

        int lineHeight = textRenderer.fontHeight + LINE_SPACING;
        int overlayHeight = PADDING * 2 + lineHeight * lines.size();
        int slideOffset = (int) ((1f - progress) * SLIDE_OFFSET);
        int overlayX = MARGIN - slideOffset;
        int overlayY = MARGIN;

        context.fill(
            overlayX,
            overlayY,
            overlayX + OVERLAY_WIDTH,
            overlayY + overlayHeight,
            applyAlpha(UITheme.OVERLAY_BACKGROUND, progress)
        );
        DrawContextBridge.drawBorder(
            context,
            overlayX,
            overlayY,
            OVERLAY_WIDTH,
            overlayHeight,
            applyAlpha(UITheme.BORDER_HIGHLIGHT, progress)
        );

        int textX = overlayX + PADDING;
        int textY = overlayY + PADDING;
        for (int i = 0; i < lines.size(); i++) {
            String line = trimTextToWidth(lines.get(i), textRenderer, OVERLAY_WIDTH - PADDING * 2);
            int color = i == 0 ? UITheme.ACCENT_AMBER : UITheme.TEXT_HEADER;
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(line),
                textX,
                textY + i * lineHeight,
                applyAlpha(color, progress)
            );
        }
    }

    private List<String> buildDisplayLines(List<RuntimeVariableEntry> entries) {
        Map<String, Integer> nameCounts = new HashMap<>();
        for (RuntimeVariableEntry entry : entries) {
            String name = entry.getName();
            if (name == null) {
                continue;
            }
            nameCounts.put(name, nameCounts.getOrDefault(name, 0) + 1);
        }

        List<String> lines = new ArrayList<>();
        lines.add("Variables");

        for (RuntimeVariableEntry entry : entries) {
            RuntimeVariable variable = entry.getVariable();
            if (variable == null) {
                continue;
            }
            String value = formatValue(variable.getType(), variable.getValues());
            if (value.isEmpty()) {
                continue;
            }

            String name = entry.getName();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }

            String label = name.trim();
            if (nameCounts.getOrDefault(name, 0) > 1) {
                String suffix = shortId(entry.getStartNodeId());
                if (!suffix.isEmpty()) {
                    label = label + " (" + suffix + ")";
                }
            }

            lines.add(label + " = " + value);
        }

        if (lines.size() == 1) {
            lines.clear();
        }
        return lines;
    }

    private String formatValue(NodeType type, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        String result;
        switch (type) {
            case PARAM_COORDINATE:
                result = formatCoordinates(values);
                break;
            case PARAM_ROTATION:
                result = formatPair(values, "Yaw", "Pitch", "yaw", "pitch");
                break;
            case PARAM_RANGE:
            case PARAM_CLOSEST:
                result = formatSingle(values, "Range", "range");
                break;
            case PARAM_BLOCK:
                result = formatSingle(values, "Block", "block");
                break;
            case PARAM_ITEM:
                result = formatSingle(values, "Item", "item");
                break;
            case PARAM_ENTITY:
                result = formatSingle(values, "Entity", "entity");
                break;
            case PARAM_PLAYER:
                result = formatSingle(values, "Player", "player");
                break;
            case PARAM_WAYPOINT:
                result = formatSingle(values, "Waypoint", "waypoint");
                break;
            case PARAM_SCHEMATIC:
                result = formatSchematic(values);
                break;
            case PARAM_INVENTORY_SLOT:
                result = formatSlot(values);
                break;
            case PARAM_DURATION:
                result = formatSingle(values, "Duration", "duration");
                break;
            case PARAM_AMOUNT:
                result = formatSingle(values, "Amount", "amount");
                break;
            case PARAM_BOOLEAN:
                result = formatSingle(values, "Toggle", "toggle");
                break;
            case PARAM_HAND:
                result = formatSingle(values, "Hand", "hand");
                break;
            case PARAM_PLACE_TARGET:
                result = formatPlaceTarget(values);
                break;
            case VARIABLE:
                result = formatSingle(values, "Variable", "variable");
                break;
            default:
                result = formatFallback(values);
                break;
        }
        return result == null ? "" : result;
    }

    private String formatCoordinates(Map<String, String> values) {
        String x = getValue(values, "X");
        String y = getValue(values, "Y");
        String z = getValue(values, "Z");
        if (isEmpty(x) && isEmpty(y) && isEmpty(z)) {
            return "";
        }
        return String.format("(%s, %s, %s)", blankIfNull(x), blankIfNull(y), blankIfNull(z));
    }

    private String formatPair(Map<String, String> values, String firstKey, String secondKey, String firstLabel, String secondLabel) {
        String first = getValue(values, firstKey);
        String second = getValue(values, secondKey);
        if (isEmpty(first) && isEmpty(second)) {
            return "";
        }
        return firstLabel + ":" + blankIfNull(first) + ", " + secondLabel + ":" + blankIfNull(second);
    }

    private String formatSingle(Map<String, String> values, String key, String label) {
        String value = getValue(values, key);
        if (isEmpty(value)) {
            return "";
        }
        return label + ":" + value;
    }

    private String formatSchematic(Map<String, String> values) {
        String name = getValue(values, "Schematic");
        String coords = formatCoordinates(values);
        if (isEmpty(name) && coords.isEmpty()) {
            return "";
        }
        if (coords.isEmpty()) {
            return "schematic:" + name;
        }
        if (isEmpty(name)) {
            return coords;
        }
        return "schematic:" + name + " " + coords;
    }

    private String formatSlot(Map<String, String> values) {
        String slot = getValue(values, "Slot");
        String mode = getValue(values, "Mode");
        if (isEmpty(slot) && isEmpty(mode)) {
            return "";
        }
        if (isEmpty(mode)) {
            return "slot:" + slot;
        }
        if (isEmpty(slot)) {
            return "mode:" + mode;
        }
        return "slot:" + slot + ", mode:" + mode;
    }

    private String formatPlaceTarget(Map<String, String> values) {
        String block = getValue(values, "Block");
        String coords = formatCoordinates(values);
        if (isEmpty(block) && coords.isEmpty()) {
            return "";
        }
        if (coords.isEmpty()) {
            return "block:" + block;
        }
        if (isEmpty(block)) {
            return coords;
        }
        return "block:" + block + " " + coords;
    }

    private String formatFallback(Map<String, String> values) {
        String[] keys = {
            "Value", "Text", "Name", "Item", "Block", "Blocks", "Entity", "Player", "Waypoint", "Range",
            "Amount", "Duration", "X", "Y", "Z"
        };
        for (String key : keys) {
            String value = getValue(values, key);
            if (!isEmpty(value)) {
                return key.toLowerCase() + ":" + value;
            }
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String value = entry.getValue();
            if (!isEmpty(value)) {
                return entry.getKey() + ":" + value;
            }
        }
        return "";
    }

    private String getValue(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return null;
        }
        String direct = values.get(key);
        if (!isEmpty(direct)) {
            return direct;
        }
        String normalized = values.get(key.toLowerCase());
        return isEmpty(normalized) ? null : normalized;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private String shortId(String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim();
        if (trimmed.length() <= 6) {
            return trimmed;
        }
        return trimmed.substring(0, 6);
    }

    private String trimTextToWidth(String text, TextRenderer textRenderer, int maxWidth) {
        if (text == null || textRenderer == null) {
            return "";
        }
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 0 && textRenderer.getWidth(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private int applyAlpha(int color, float alpha) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int adjustedAlpha = (int) (baseAlpha * AnimationHelper.clamp01(alpha));
        return (adjustedAlpha << 24) | (color & 0x00FFFFFF);
    }
}
