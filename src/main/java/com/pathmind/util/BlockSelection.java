package com.pathmind.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/**
 * Represents a block selection that may optionally include block state data.
 * Provides helpers for parsing parameter strings and matching block states.
 */
public final class BlockSelection {
    private static final String FILLED_SLOTS_PROPERTY = "filled";

    private final Identifier blockId;
    private final Block block;
    private final Map<Property<?>, Comparable<?>> properties;
    private final String propertyString;
    private final Integer filledSlots;

    private BlockSelection(
        Identifier blockId,
        Block block,
        Map<Property<?>, Comparable<?>> properties,
        String propertyString,
        Integer filledSlots
    ) {
        this.blockId = blockId;
        this.block = block;
        this.properties = properties;
        this.propertyString = propertyString;
        this.filledSlots = filledSlots;
    }

    public Identifier getBlockId() {
        return blockId;
    }

    public String getBlockIdString() {
        return blockId != null ? blockId.toString() : "";
    }

    public Block getBlock() {
        return block;
    }

    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    public String getPropertyString() {
        return propertyString;
    }

    /**
     * Returns the canonical parameter string for this selection.
     */
    public String asString() {
        if (blockId == null) {
            return "";
        }
        if (properties.isEmpty()) {
            return blockId.toString();
        }
        return blockId + "[" + propertyString + "]";
    }

    /**
     * Returns {@code true} when the provided state matches both the block and any requested properties.
     */
    public boolean matches(BlockState state) {
        if (state == null || block == null) {
            return false;
        }
        if (!state.isOf(block)) {
            return false;
        }
        if (filledSlots != null) {
            int filled = countFilledSlots(state);
            if (filled != filledSlots) {
                return false;
            }
        }
        if (properties.isEmpty()) {
            return true;
        }
        for (Map.Entry<Property<?>, Comparable<?>> entry : properties.entrySet()) {
            Property<?> property = entry.getKey();
            Comparable<?> requested = entry.getValue();
            Comparable<?> actual = state.get(property);
            if (!Objects.equals(actual, requested)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a block selection string (e.g., {@code minecraft:carrots[age=7]}).
     */
    public static Optional<BlockSelection> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        int startBracket = trimmed.indexOf('[');
        String blockPart = startBracket >= 0 ? trimmed.substring(0, startBracket) : trimmed;
        String statePart = startBracket >= 0
            ? trimmed.substring(startBracket + 1, trimmed.endsWith("]") ? trimmed.length() - 1 : trimmed.length())
            : "";

        Identifier identifier = Identifier.tryParse(blockPart.trim());
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return Optional.empty();
        }
        Block block = Registries.BLOCK.get(identifier);
        Map<Property<?>, Comparable<?>> requested = new LinkedHashMap<>();
        Integer filledSlots = null;
        if (!statePart.isEmpty()) {
            String[] entries = statePart.split(",");
            for (String entry : entries) {
                String assignment = entry.trim();
                if (assignment.isEmpty()) {
                    continue;
                }
                String[] parts = assignment.split("=");
                if (parts.length != 2) {
                    return Optional.empty();
                }
                String propertyName = parts[0].trim();
                String propertyValue = parts[1].trim();
                if (FILLED_SLOTS_PROPERTY.equalsIgnoreCase(propertyName)) {
                    int maxSlots = countSlotOccupiedProperties(block);
                    if (maxSlots <= 0) {
                        return Optional.empty();
                    }
                    try {
                        int parsed = Integer.parseInt(propertyValue);
                        if (parsed < 0 || parsed > maxSlots) {
                            return Optional.empty();
                        }
                        filledSlots = parsed;
                        continue;
                    } catch (NumberFormatException ignored) {
                        return Optional.empty();
                    }
                }
                Property<?> property = block.getStateManager().getProperty(propertyName);
                if (property == null) {
                    return Optional.empty();
                }
                if (isDirectionProperty(property)) {
                    continue;
                }
                Optional<?> parsedValue = property.parse(propertyValue);
                if (parsedValue.isEmpty()) {
                    return Optional.empty();
                }
                requested.put(property, (Comparable<?>) parsedValue.get());
            }
        }

        String propertyString = buildPropertyString(requested, filledSlots);
        return Optional.of(new BlockSelection(identifier, block, requested, propertyString, filledSlots));
    }

    /**
     * Builds a normalized selection string using the provided block + state string.
     */
    public static Optional<String> combine(String blockId, String stateProperties) {
        if (blockId == null || blockId.trim().isEmpty()) {
            return Optional.empty();
        }
        String trimmedBlock = blockId.trim();
        String props = stateProperties != null ? stateProperties.trim() : "";
        if (props.isEmpty()) {
            return sanitizeBlockOnly(trimmedBlock);
        }
        String candidate = trimmedBlock + "[" + props + "]";
        return parse(candidate).map(BlockSelection::asString);
    }

    /**
     * Returns a normalized property string for a block state (e.g., {@code age=7}).
     */
    public static String describeState(BlockState state) {
        if (state == null) {
            return "";
        }
        Map<Property<?>, Comparable<?>> entries = state.getEntries();
        return buildPropertyString(entries, null);
    }

    private static Optional<String> sanitizeBlockOnly(String blockId) {
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return Optional.empty();
        }
        return Optional.of(identifier.toString());
    }

    /**
     * Extracts the {@link Identifier} of the block portion from the provided string.
     */
    public static Identifier extractBlockIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int bracket = trimmed.indexOf('[');
        String blockPart = bracket >= 0 ? trimmed.substring(0, bracket) : trimmed;
        return Identifier.tryParse(blockPart.trim());
    }

    /**
     * Removes the {@code [state]} suffix and returns the raw block identifier string.
     */
    public static String stripState(String raw) {
        if (raw == null) {
            return null;
        }
        int bracket = raw.indexOf('[');
        return bracket >= 0 ? raw.substring(0, bracket).trim() : raw.trim();
    }

    /**
     * Retrieves available block state descriptions for the provided block id.
     */
    public static List<StateOption> getStateOptions(String blockId) {
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return Collections.emptyList();
        }
        Block block = Registries.BLOCK.get(identifier);
        if (hasSlotOccupiedProperties(block)) {
            return getFilledSlotsOptions(block);
        }
        List<StateOption> options = new ArrayList<>();
        for (BlockState state : block.getStateManager().getStates()) {
            Map<Property<?>, Comparable<?>> entries = state.getEntries();
            String description = buildPropertyString(entries, null);
            if (!description.isEmpty()) {
                options.add(new StateOption(description, description));
            }
        }
        // Remove duplicates and sort for stable dropdown ordering
        Map<String, StateOption> deduplicated = new LinkedHashMap<>();
        for (StateOption option : options) {
            deduplicated.put(option.value(), option);
        }
        List<StateOption> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort(Comparator.comparing(StateOption::value, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static String buildPropertyString(Map<Property<?>, Comparable<?>> properties, Integer filledSlots) {
        List<Map.Entry<Property<?>, Comparable<?>>> entries = new ArrayList<>(properties.entrySet());
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Property<?>, Comparable<?>> entry : entries) {
            Property<?> property = entry.getKey();
            if (isDirectionProperty(property)) {
                continue;
            }
            Comparable<?> value = entry.getValue();
            parts.add(propertyValueToString(property, value));
        }
        if (filledSlots != null) {
            parts.add(FILLED_SLOTS_PROPERTY + "=" + filledSlots);
        }
        if (parts.isEmpty()) {
            return "";
        }
        parts.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(",", parts);
    }

    /**
     * Represents a dropdown option for a block state.
     */
    public record StateOption(String value, String displayText) {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueToString(Property<?> property, Comparable<?> value) {
        Property rawProperty = property;
        return property.getName() + "=" + rawProperty.name(value);
    }

    private static boolean isDirectionProperty(Property<?> property) {
        String name = property.getName();
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("facing")
            || "axis".equals(normalized)
            || "rotation".equals(normalized)
            || "orientation".equals(normalized);
    }

    private static boolean hasSlotOccupiedProperties(Block block) {
        return countSlotOccupiedProperties(block) > 0;
    }

    private static int countSlotOccupiedProperties(Block block) {
        if (block == null) {
            return 0;
        }
        int count = 0;
        for (Property<?> property : block.getStateManager().getProperties()) {
            String name = property.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("slot_") && name.endsWith("_occupied")) {
                count++;
            }
        }
        return count;
    }

    private static List<StateOption> getFilledSlotsOptions(Block block) {
        List<StateOption> options = new ArrayList<>();
        for (BlockState state : block.getStateManager().getStates()) {
            int filled = countFilledSlots(state);
            String value = FILLED_SLOTS_PROPERTY + "=" + filled;
            String display = filled == 1 ? "1 slot filled" : filled + " slots filled";
            options.add(new StateOption(value, display));
        }
        Map<String, StateOption> deduplicated = new LinkedHashMap<>();
        for (StateOption option : options) {
            deduplicated.put(option.value(), option);
        }
        List<StateOption> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort(Comparator.comparing(StateOption::value, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static int countFilledSlots(BlockState state) {
        if (state == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            String name = entry.getKey().getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("slot_") && name.endsWith("_occupied")) {
                Object value = entry.getValue();
                if (value instanceof Boolean && (Boolean) value) {
                    count++;
                }
            }
        }
        return count;
    }
}
