package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class NodeBehaviorDefinitionSupport {
    static Map<String, String> copyIfPresent(Map<String, String> values, String sourceKey, String targetKey) {
        String value = values.get(sourceKey);
        if (value != null) {
            put(values, targetKey, value);
        }
        return values;
    }

    static double parseNonNegativeDouble(String value, double defaultValue) {
        String trimmed = value == null ? "" : value.trim();
        double parsed = defaultValue;
        if (!trimmed.isEmpty()) {
            try {
                parsed = Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
                parsed = 0.0;
            }
        }
        return Math.max(0.0, parsed);
    }

    static double durationUnitSeconds(NodeMode mode) {
        NodeMode durationMode = mode != null ? mode : NodeMode.WAIT_SECONDS;
        return switch (durationMode) {
            case WAIT_TICKS -> 0.05;
            case WAIT_MINUTES -> 60.0;
            case WAIT_HOURS -> 3600.0;
            case WAIT_SECONDS -> 1.0;
            default -> 1.0;
        };
    }

    static void syncSingularAndPlural(Map<String, String> values, String singularKey, String pluralKey) {
        String singular = values.get(singularKey);
        String plural = values.get(pluralKey);
        if ((plural == null || plural.isEmpty()) && singular != null && !singular.isEmpty()) {
            put(values, pluralKey, singular);
            return;
        }
        if ((singular == null || singular.isEmpty()) && plural != null && !plural.isEmpty()) {
            String first = firstCommaSeparatedEntry(plural);
            if (first != null && !first.isEmpty()) {
                put(values, singularKey, first);
            }
        }
    }

    static void put(Map<String, String> values, String key, String value) {
        values.put(key, value);
        values.put(Node.normalizeParameterKey(key), value);
    }

    static NodeComparableBehavior stringComparable(ComparableStringResolver resolver) {
        return new NodeComparableBehavior() {
            @Override
            public Optional<String> resolveString(Node owner, Node node) {
                return resolver.resolve(owner, node);
            }
        };
    }

    static NodeComparableBehavior numberComparable(ComparableNumberResolver resolver) {
        return new NodeComparableBehavior() {
            @Override
            public Optional<Double> resolveNumber(Node owner, Node node) {
                return resolver.resolve(owner, node);
            }
        };
    }

    static NodeComparableBehavior combinedComparable(ComparableStringResolver stringResolver,
                                                     ComparableNumberResolver numberResolver) {
        return new NodeComparableBehavior() {
            @Override
            public Optional<String> resolveString(Node owner, Node node) {
                return stringResolver.resolve(owner, node);
            }

            @Override
            public Optional<Double> resolveNumber(Node owner, Node node) {
                return numberResolver.resolve(owner, node);
            }
        };
    }

    static String playerSearchFailureMessage(Node owner, String playerName) {
        if (Node.isAnyPlayerValue(playerName)) {
            return tr("pathmind.error.noPlayersNearby", owner.getType().getDisplayName());
        }
        if (Node.isSelfPlayerValue(playerName)) {
            return tr("pathmind.error.localPlayerUnavailable", owner.getType().getDisplayName());
        }
        return tr("pathmind.error.playerNotNearby", playerName, owner.getType().getDisplayName());
    }

    static String noNearbyEntityMessage(Node owner) {
        return tr("pathmind.error.noNearbyEntity", owner.getType().getDisplayName());
    }

    static String unknownItemMessage(Node owner, String reference) {
        return tr("pathmind.error.unknownItemForNode", reference, owner.getType().getDisplayName());
    }

    static String noDroppedItemMessage(Node owner, java.util.List<String> itemIds) {
        return tr("pathmind.error.noDroppedItemForNode", String.join(", ", itemIds), owner.getType().getDisplayName());
    }

    static String noBlocksDefinedMessage(Node owner) {
        return tr("pathmind.error.noBlocksDefinedOnParameter", owner.getType().getDisplayName());
    }

    static String noNearbyBlockMessage(Node owner) {
        return tr("pathmind.error.noNearbyBlock", owner.getType().getDisplayName());
    }

    static String noMatchingBlockMessage(Node owner) {
        return tr("pathmind.error.noMatchingBlockFromParameter", owner.getType().getDisplayName());
    }

    static String noOpenBlockMessage(Node owner) {
        return tr("pathmind.error.noOpenBlockInRange", owner.getType().getDisplayName());
    }

    static Orientation applyDirection(String direction, float currentYaw, float currentPitch) {
        if (direction == null) {
            return new Orientation(currentYaw, currentPitch);
        }
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> new Orientation(180.0F, 0.0F);
            case "south" -> new Orientation(0.0F, 0.0F);
            case "west" -> new Orientation(90.0F, 0.0F);
            case "east" -> new Orientation(-90.0F, 0.0F);
            case "up" -> new Orientation(currentYaw, -90.0F);
            case "down" -> new Orientation(currentYaw, 90.0F);
            default -> new Orientation(currentYaw, currentPitch);
        };
    }

    private static String firstCommaSeparatedEntry(String value) {
        for (String entry : value.split(",")) {
            String trimmed = entry == null ? null : entry.trim();
            if (trimmed != null && !trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    static final class Orientation {
        final float yaw;
        final float pitch;

        Orientation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    @FunctionalInterface
    interface ComparableStringResolver {
        Optional<String> resolve(Node owner, Node node);
    }

    @FunctionalInterface
    interface ComparableNumberResolver {
        Optional<Double> resolve(Node owner, Node node);
    }

    private NodeBehaviorDefinitionSupport() {
    }
}
