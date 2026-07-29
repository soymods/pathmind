package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class NodeWorldTargetResolver {
    private static final Pattern UNSAFE_RESOURCE_ID_PATTERN = Pattern.compile("[^a-z0-9_:/.-]");

    private final Node owner;

    NodeWorldTargetResolver(Node owner) {
        this.owner = owner;
    }

    static boolean isAnySelectionValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "any".equalsIgnoreCase(trimmed)
            || "any state".equalsIgnoreCase(trimmed);
    }

    String sanitizeResourceId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
        int bracketIndex = lower.indexOf('[');
        if (bracketIndex >= 0) {
            lower = lower.substring(0, bracketIndex);
        }
        String sanitized = UNSAFE_RESOURCE_ID_PATTERN.matcher(lower).replaceAll("");
        int firstColon = sanitized.indexOf(':');
        if (firstColon != -1) {
            int nextColon = sanitized.indexOf(':', firstColon + 1);
            if (nextColon != -1) {
                sanitized = sanitized.substring(0, firstColon + 1) + sanitized.substring(firstColon + 1).replace(':', '_');
            }
        }
        return sanitized;
    }

    String normalizeResourceId(String value, String defaultNamespace) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (isAnySelectionValue(trimmed)) {
            return "";
        }
        if (!trimmed.contains(":")) {
            return defaultNamespace + ":" + trimmed;
        }
        return trimmed;
    }

    List<BlockSelection> resolveBlocksFromParameter(Node parameterNode) {
        List<BlockSelection> selections = new ArrayList<>();
        String primary = owner.getBlockParameterValue(parameterNode);
        String listValue = Node.getParameterString(parameterNode, "Blocks");
        for (String entry : splitMultiValueList(listValue)) {
            addBlockSelection(selections, entry);
        }
        for (String entry : splitMultiValueList(primary)) {
            addBlockSelection(selections, entry);
        }
        return selections;
    }

    private void addBlockSelection(List<BlockSelection> selections, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        BlockSelection.parse(rawValue).ifPresent(selection -> {
            if (selection.getBlock() != null) {
                boolean exists = selections.stream().anyMatch(existing -> existing.asString().equals(selection.asString()));
                if (!exists) {
                    selections.add(selection);
                }
            }
        });
    }

    List<String> resolveItemIdsFromParameter(Node parameterNode) {
        List<String> itemIds = new ArrayList<>();
        if (parameterNode == null) {
            return itemIds;
        }
        String listValue = Node.getParameterString(parameterNode, "Items");
        for (String entry : splitMultiValueList(listValue)) {
            addItemIdentifier(itemIds, entry);
        }
        for (String entry : splitMultiValueList(Node.getParameterString(parameterNode, "Item"))) {
            addItemIdentifier(itemIds, entry);
        }
        return itemIds;
    }

    String resolveTradeKeyFromParameter(Node parameterNode) {
        if (parameterNode == null) {
            return "";
        }
        String trade = Node.getParameterString(parameterNode, "Trade");
        if (trade != null && !trade.isEmpty()) {
            return trade;
        }
        String legacy = Node.getParameterString(parameterNode, "Item");
        return legacy != null ? legacy : "";
    }

    List<String> resolveEntityIdsFromParameter(Node parameterNode) {
        List<String> entityIds = new ArrayList<>();
        if (parameterNode == null) {
            return entityIds;
        }
        for (String entry : splitMultiValueList(Node.getParameterString(parameterNode, "Entity"))) {
            addEntityIdentifier(entityIds, entry);
        }
        return entityIds;
    }

    void addItemIdentifier(List<String> itemIds, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        String sanitized = sanitizeResourceId(rawValue);
        if (sanitized == null || sanitized.isEmpty()) {
            return;
        }
        String normalized = normalizeResourceId(sanitized, "minecraft");
        if (!itemIds.contains(normalized)) {
            itemIds.add(normalized);
        }
    }

    private void addEntityIdentifier(List<String> entityIds, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        String sanitized = sanitizeResourceId(rawValue);
        if (sanitized == null || sanitized.isEmpty()) {
            return;
        }
        String normalized = normalizeResourceId(sanitized, "minecraft");
        if (!entityIds.contains(normalized)) {
            entityIds.add(normalized);
        }
    }

    List<String> splitMultiValueList(String rawValue) {
        if (rawValue == null) {
            return Collections.emptyList();
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }
            if ((c == ',' || c == ';') && bracketDepth == 0) {
                String entry = current.toString().trim();
                if (!entry.isEmpty()) {
                    parts.add(entry);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String entry = current.toString().trim();
        if (!entry.isEmpty()) {
            parts.add(entry);
        }
        return parts;
    }
}
