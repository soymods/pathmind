package com.pathmind.nodes;

import java.util.Map;
import java.util.Optional;

final class SensorComparableDefinitions {
    static Optional<NodeComparableBehavior> definition(NodeType type) {
        return switch (type) {
            case SENSOR_TARGETED_BLOCK_FACE -> Optional.of(NodeBehaviorDefinitionSupport.stringComparable(
                SensorComparableDefinitions::resolveTargetedBlockFaceString));
            case SENSOR_CURRENT_GUI -> Optional.of(NodeBehaviorDefinitionSupport.stringComparable(
                SensorComparableDefinitions::resolveGuiString));
            case SENSOR_LOOK_DIRECTION -> Optional.of(NodeBehaviorDefinitionSupport.combinedComparable(
                SensorComparableDefinitions::resolveLookDirectionString,
                SensorComparableDefinitions::resolveSingleAxisAmount));
            case SENSOR_POSITION_OF -> Optional.of(NodeBehaviorDefinitionSupport.combinedComparable(
                SensorComparableDefinitions::resolvePositionString,
                SensorComparableDefinitions::resolveSingleAxisAmount));
            case SENSOR_DISTANCE_BETWEEN, SENSOR_IS_ON_GROUND -> Optional.of(
                NodeBehaviorDefinitionSupport.numberComparable(SensorComparableDefinitions::resolveDistanceValue));
            case SENSOR_SLOT_ITEM_COUNT -> Optional.of(
                NodeBehaviorDefinitionSupport.numberComparable(SensorComparableDefinitions::resolveSlotItemCount));
            default -> Optional.empty();
        };
    }

    private static Optional<String> resolveTargetedBlockFaceString(Node owner, Node node) {
        Map<String, String> values = node.exportParameterValues();
        String face = owner.getRuntimeValue(values, "face");
        if (face.isEmpty()) {
            face = owner.getRuntimeValue(values, "side");
        }
        return face.isEmpty() ? Optional.empty() : Optional.of(face.trim());
    }

    private static Optional<String> resolveGuiString(Node owner, Node node) {
        Map<String, String> values = node.exportParameterValues();
        String gui = owner.getRuntimeValue(values, "gui");
        if (gui.isEmpty()) {
            gui = owner.getRuntimeValue(values, "mode");
        }
        if (gui.isEmpty()) {
            gui = owner.getRuntimeValue(values, "guimode");
        }
        if (gui.isEmpty()) {
            gui = owner.getRuntimeValue(values, "selection");
        }
        return gui.isEmpty() ? Optional.empty() : Optional.of(gui.trim());
    }

    private static Optional<String> resolveLookDirectionString(Node owner, Node node) {
        String formatted = owner.formatRotationValues(node.exportParameterValues());
        if (!formatted.isEmpty()) {
            return Optional.of(formatted);
        }
        Map<String, String> values = node.exportParameterValues();
        String direction = owner.getRuntimeValue(values, "direction");
        if (direction.isEmpty()) {
            direction = owner.getRuntimeValue(values, "side");
        }
        if (direction.isEmpty()) {
            direction = owner.getRuntimeValue(values, "face");
        }
        return direction.isEmpty() ? Optional.empty() : Optional.of(direction.trim());
    }

    private static Optional<String> resolvePositionString(Node owner, Node node) {
        if (node.isSensorPositionSingleAxisMode()) {
            return Optional.empty();
        }
        String formatted = owner.formatCoordinateValues(node.exportParameterValues());
        return formatted.isEmpty() ? Optional.empty() : Optional.of(formatted);
    }

    private static Optional<Double> resolveDistanceValue(Node owner, Node node) {
        String distanceValue = owner.getRuntimeValue(node.exportParameterValues(), "distance");
        return distanceValue.isEmpty() ? Optional.empty() : Optional.ofNullable(Node.parseDoubleOrNull(distanceValue));
    }

    private static Optional<Double> resolveSingleAxisAmount(Node owner, Node node) {
        boolean singleAxis = node.getType() == NodeType.SENSOR_POSITION_OF
            ? node.isSensorPositionSingleAxisMode()
            : node.isSensorLookSingleAxisMode();
        if (!singleAxis) {
            return Optional.empty();
        }
        Map<String, String> values = node.exportParameterValues();
        String amountValue = owner.getRuntimeValue(values, "amount");
        if (amountValue.isEmpty()) {
            amountValue = owner.getRuntimeValue(values, "value");
        }
        return amountValue.isEmpty() ? Optional.empty() : Optional.ofNullable(Node.parseDoubleOrNull(amountValue));
    }

    private static Optional<Double> resolveSlotItemCount(Node owner, Node node) {
        Map<String, String> values = node.exportParameterValues();
        String amountValue = owner.getRuntimeValue(values, "amount");
        if (amountValue.isEmpty()) {
            amountValue = owner.getRuntimeValue(values, "count");
        }
        return amountValue.isEmpty() ? Optional.empty() : Optional.ofNullable(Node.parseDoubleOrNull(amountValue));
    }

    private SensorComparableDefinitions() {
    }
}
