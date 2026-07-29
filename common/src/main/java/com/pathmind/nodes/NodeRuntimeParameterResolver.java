package com.pathmind.nodes;

import com.pathmind.execution.ExecutionManager;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class NodeRuntimeParameterResolver {
    private static final String LOOK_DIRECTION_SOURCE_KEY = "__pathmind_source";
    private static final String LOOK_DIRECTION_AXIS_KEY = "__pathmind_look_axis";
    private static final String LOOK_DIRECTION_SOURCE_VALUE = "look_direction";
    private final Node owner;

    NodeRuntimeParameterResolver(Node owner) {
        this.owner = owner;
    }

    Node.ParameterHandlingResult preprocessAttachedParameter(
        EnumSet<Node.ParameterUsage> usages, CompletableFuture<Void> future
    ) {
        if (owner.getAttachments().hasAttachedParameters()) {
            java.util.List<Integer> slotIndices =
                new java.util.ArrayList<>(
                    owner.getAttachments().getAttachedParameterSlotIndices());
            java.util.Collections.sort(slotIndices);
            Node.ParameterHandlingResult result = Node.ParameterHandlingResult.CONTINUE;
            boolean resetRuntime = true;
            for (int slotIndex : slotIndices) {
                Node.ParameterHandlingResult slotResult =
                    preprocessParameterSlot(slotIndex, usages, future, resetRuntime);
                resetRuntime = false;
                if (slotResult == Node.ParameterHandlingResult.COMPLETE) {
                    result = Node.ParameterHandlingResult.COMPLETE;
                    break;
                }
            }
            return result;
        }

        int slotCount = owner.getParameterSlotCount();
        Node.ParameterHandlingResult result = Node.ParameterHandlingResult.CONTINUE;
        boolean resetRuntime = true;
        for (int i = 0; i < slotCount; i++) {
            Node.ParameterHandlingResult slotResult =
                preprocessParameterSlot(i, usages, future, resetRuntime);
            resetRuntime = false;
            if (slotResult == Node.ParameterHandlingResult.COMPLETE) {
                result = Node.ParameterHandlingResult.COMPLETE;
                break;
            }
        }
        return result;
    }

    Node.ParameterHandlingResult preprocessParameterSlot(
        int slotIndex,
        EnumSet<Node.ParameterUsage> usages,
        CompletableFuture<Void> future,
        boolean resetRuntimeData
    ) {
        if (!owner.canAcceptParameterAt(slotIndex)) {
            return Node.ParameterHandlingResult.CONTINUE;
        }
        if (resetRuntimeData) {
            owner.runtimeState().runtimeParameterData = null;
        }
        Node parameterNode = owner.getAttachedParameter(slotIndex);
        return preprocessParameterNode(parameterNode, slotIndex, usages, future);
    }

    private Node.ParameterHandlingResult preprocessParameterNode(
        Node parameterNode,
        int slotIndex,
        EnumSet<Node.ParameterUsage> usages,
        CompletableFuture<Void> future
    ) {
        if (parameterNode == null) {
            return Node.ParameterHandlingResult.CONTINUE;
        }
        if (parameterNode.hasParameterSlot()) {
            int requiredSlotCount = parameterNode.getParameterSlotCount();
            for (int i = 0; i < requiredSlotCount; i++) {
                if (parameterNode.isParameterSlotRequired(i)
                    && parameterNode.getAttachedParameter(i) == null) {
                    if (future != null && !future.isDone()) {
                        String label = parameterNode.getParameterSlotLabel(i);
                        NodeExecutionCompletion.failWithCurrentClient(
                            owner,
                            future,
                            parameterNode.getType().getDisplayName()
                                + " requires a "
                                + label.toLowerCase(Locale.ROOT)
                                + " parameter before it can run.");
                    }
                    return Node.ParameterHandlingResult.COMPLETE;
                }
            }
        }
        if (owner.runtimeState().runtimeParameterData == null) {
            owner.runtimeState().runtimeParameterData = new RuntimeParameterData();
        }

        boolean handled = false;
        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = resolveVariableValueNode(parameterNode, slotIndex, future);
            if (parameterNode == null) {
                return Node.ParameterHandlingResult.COMPLETE;
            }
        }

        if (!owner.reportEmptyParametersForNode(parameterNode, future)) {
            return Node.ParameterHandlingResult.COMPLETE;
        }

        Map<String, String> exported = parameterNode.exportParameterValues();
        Map<String, String> adjustedValues =
            owner.adjustParameterValuesForSlot(exported, slotIndex, parameterNode);
        if (!exported.isEmpty()) {
            handled = owner.applyParameterValuesFromMap(adjustedValues);
        }
        if (owner.getType() == NodeType.WAIT) {
            String durationValue = adjustedValues.get("Duration");
            if (durationValue == null) {
                durationValue = adjustedValues.get(Node.normalizeParameterKey("Duration"));
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get("DurationSeconds");
            }
            if (durationValue == null) {
                durationValue =
                    adjustedValues.get(Node.normalizeParameterKey("DurationSeconds"));
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get("WaitSeconds");
            }
            if (durationValue == null) {
                durationValue =
                    adjustedValues.get(Node.normalizeParameterKey("WaitSeconds"));
            }
            if (durationValue == null) {
                durationValue = adjustedValues.get("IntervalSeconds");
            }
            if (durationValue == null) {
                durationValue =
                    adjustedValues.get(Node.normalizeParameterKey("IntervalSeconds"));
            }
            if (durationValue != null && !durationValue.trim().isEmpty()) {
                String trimmedDuration = durationValue.trim();
                Double parsedDurationSeconds = parseDoubleOrNull(trimmedDuration);
                if (owner.runtimeState().runtimeParameterData != null
                    && parsedDurationSeconds != null) {
                    owner.runtimeState().runtimeParameterData.durationSeconds =
                        Math.max(0.0, parsedDurationSeconds);
                }
                if (!handled) {
                    owner.setParameterValueAndPropagate("Duration", trimmedDuration);
                    handled = true;
                }
            } else if (owner.providesTrait(parameterNode, NodeValueTrait.DURATION)
                || owner.providesTrait(parameterNode, NodeValueTrait.NUMBER)) {
                handled = true;
            }
        }

        if (parameterNode.getType() == NodeType.LIST_ITEM) {
            Entity resolved =
                owner.resolveListItemEntity(
                    parameterNode, owner.runtimeState().runtimeParameterData, future);
            if (resolved != null) {
                handled = true;
            } else if (future != null && future.isDone()) {
                return Node.ParameterHandlingResult.COMPLETE;
            }
        }

        if (usages.contains(Node.ParameterUsage.POSITION)) {
            Optional<Vec3> targetVec =
                owner.resolvePositionTarget(
                    parameterNode, owner.runtimeState().runtimeParameterData, future);
            if (targetVec.isPresent()) {
                handled = true;
                owner.runtimeState().runtimeParameterData.targetVector = targetVec.get();
                owner.applyVectorToCoordinateParameters(targetVec.get());
            } else if (future != null && future.isDone()) {
                return Node.ParameterHandlingResult.COMPLETE;
            }
        }

        if (usages.contains(Node.ParameterUsage.LOOK_ORIENTATION)) {
            boolean oriented =
                owner.resolveLookOrientation(
                    parameterNode, owner.runtimeState().runtimeParameterData, future);
            if (oriented) {
                handled = true;
            } else if (future != null && future.isDone()) {
                return Node.ParameterHandlingResult.COMPLETE;
            }
        }

        if (!handled
            && owner.getType() == NodeType.MOVE_ITEM
            && owner.providesTrait(parameterNode, NodeValueTrait.ITEM)) {
            if (owner.resolveMoveItemSlotFromItemParameter(parameterNode, slotIndex, future)) {
                handled = true;
            } else {
                return Node.ParameterHandlingResult.COMPLETE;
            }
        }
        if (!handled
            && owner.getType() == NodeType.MOVE_ITEM
            && owner.providesTrait(parameterNode, NodeValueTrait.GUI)) {
            handled = true;
        }
        if (!handled
            && owner.isDropNodeType()
            && (owner.providesTrait(parameterNode, NodeValueTrait.ITEM)
                || owner.providesTrait(parameterNode, NodeValueTrait.INVENTORY_SLOT))) {
            if (owner.resolveDropParameterSelection(parameterNode, future)) {
                handled = true;
            } else {
                return Node.ParameterHandlingResult.COMPLETE;
            }
        }
        if (!handled && owner.getType() == NodeType.USE) {
            if (owner.resolveUseParameterSelection(parameterNode, future)) {
                handled = true;
            } else {
                return Node.ParameterHandlingResult.COMPLETE;
            }
        }
        // Special case: block parameters in slot 0 of PLACE/PLACE_HAND nodes are valid
        // even when usages is empty (they provide block type, not position)
        if (!handled
            && usages.isEmpty()
            && (owner.getType() == NodeType.PLACE
                || owner.getType() == NodeType.PLACE_HAND)) {
            NodeType parameterType = parameterNode.getType();
            if (parameterType == NodeType.PARAM_BLOCK
                && parameterNode.getAttachments().getParentParameterSlotIndex() == 0) {
                handled = true;
            }
            if (parameterType == NodeType.PARAM_INVENTORY_SLOT
                && parameterNode.getAttachments().getParentParameterSlotIndex() == 0) {
                handled = true;
            }
        }
        if (!handled && owner.getType() == NodeType.PRESS_KEY) {
            if (owner.providesTrait(parameterNode, NodeValueTrait.KEY)) {
                String buttonValue = getParameterString(parameterNode, "Key");
                if (buttonValue != null && !buttonValue.isBlank()) {
                    owner.runtimeState().runtimeParameterData.resolvedButtonValue =
                        buttonValue;
                    owner.runtimeState().runtimeParameterData.resolvedButtonIsMouse = false;
                }
                handled = true;
            } else if (owner.providesTrait(parameterNode, NodeValueTrait.MOUSE_BUTTON)) {
                String buttonValue = getParameterString(parameterNode, "MouseButton");
                if (buttonValue != null && !buttonValue.isBlank()) {
                    owner.runtimeState().runtimeParameterData.resolvedButtonValue =
                        buttonValue;
                    owner.runtimeState().runtimeParameterData.resolvedButtonIsMouse = true;
                }
                handled = true;
            }
        }
        if (!handled
            && owner.getType() == NodeType.BREAK
            && owner.providesTrait(parameterNode, NodeValueTrait.BLOCK)) {
            handled = true;
        }

        if (!handled
            && (owner.getType() == NodeType.GOTO
                || owner.getType() == NodeType.TRAVEL)) {
            NodeType parameterType = parameterNode.getType();
            if (parameterType == NodeType.PARAM_ENTITY
                || parameterType == NodeType.PARAM_PLAYER
                || parameterType == NodeType.PARAM_ITEM
                || parameterType == NodeType.PARAM_BLOCK) {
                return Node.ParameterHandlingResult.CONTINUE;
            }
        }

        if (!handled) {
            if (future != null && !future.isDone()) {
                owner.sendIncompatibleParameterMessage(parameterNode);
                future.complete(null);
            }
            return Node.ParameterHandlingResult.COMPLETE;
        }

        return Node.ParameterHandlingResult.CONTINUE;
    }

    Node resolveVariableValueNode(
        Node variableNode, int slotIndex, CompletableFuture<Void> future
    ) {
        if (variableNode == null) {
            return null;
        }
        String variableName = getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            sendVariableError(Node.tr("pathmind.error.variableNameEmpty"), future);
            return null;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = owner.resolveExecutionStartNode();
        RuntimeValueScope scope = variableNode.getRuntimeValueScope();
        ExecutionManager.RuntimeVariable runtimeVariable =
            manager.getRuntimeVariable(startNode, variableName.trim(), scope);
        if (runtimeVariable == null) {
            sendVariableError(
                Node.tr("pathmind.error.variableNotSet", variableName.trim()), future);
            return null;
        }

        NodeType valueType = runtimeVariable.getType();
        if (valueType == null) {
            sendVariableError(
                Node.tr("pathmind.error.variableNoValue", variableName.trim()), future);
            return null;
        }

        Node snapshot = owner.createRuntimeVariableSnapshot(runtimeVariable);
        if (owner.getType() == NodeType.LOOK) {
            snapshot = createLookVariableSnapshot(snapshot, runtimeVariable);
        }
        if (snapshot == null) {
            sendVariableError(
                Node.tr("pathmind.error.variableNoValue", variableName.trim()), future);
            return null;
        }

        boolean variableSupported = owner.isParameterSupported(snapshot, slotIndex);
        if (!variableSupported
            && (owner.getType() == NodeType.OPERATOR_GREATER
                || owner.getType() == NodeType.OPERATOR_LESS)) {
            variableSupported = owner.resolveComparableNumber(snapshot).isPresent();
        }

        if (!variableSupported) {
            sendVariableError(
                Node.tr(
                    "pathmind.error.variableUnsupportedForNode",
                    variableName.trim(),
                    owner.getType().getDisplayName()),
                future);
            return null;
        }

        return snapshot;
    }

    private Node createLookVariableSnapshot(
        Node snapshot, ExecutionManager.RuntimeVariable runtimeVariable
    ) {
        if (snapshot == null
            || runtimeVariable == null
            || runtimeVariable.getType() != NodeType.PARAM_AMOUNT) {
            return snapshot;
        }
        Map<String, String> values = runtimeVariable.getValues();
        if (values == null || values.isEmpty()) {
            return snapshot;
        }
        String source = values.get(LOOK_DIRECTION_SOURCE_KEY);
        String axis = values.get(LOOK_DIRECTION_AXIS_KEY);
        if (!LOOK_DIRECTION_SOURCE_VALUE.equals(source)
            || axis == null
            || axis.isEmpty()) {
            return snapshot;
        }

        String amount = values.get("Amount");
        if (amount == null || amount.isEmpty()) {
            amount = values.get(Node.normalizeParameterKey("Amount"));
        }
        if (amount == null || amount.isEmpty()) {
            return snapshot;
        }

        Node rotationSnapshot = new Node(NodeType.PARAM_ROTATION, 0, 0);
        rotationSnapshot.setSocketsHidden(true);
        if ("Yaw".equalsIgnoreCase(axis)) {
            rotationSnapshot.setParameterValueAndPropagate("Yaw", amount);
            rotationSnapshot.setParameterValueAndPropagate("Pitch", "");
        } else if ("Pitch".equalsIgnoreCase(axis)) {
            rotationSnapshot.setParameterValueAndPropagate("Yaw", "");
            rotationSnapshot.setParameterValueAndPropagate("Pitch", amount);
        } else {
            return snapshot;
        }
        return rotationSnapshot;
    }

    Map<String, String> remapSingleAxisLookValues(
        Map<String, String> values, Node parameterNode
    ) {
        if (values == null || values.isEmpty() || parameterNode == null) {
            return values;
        }
        String axis = null;
        if (parameterNode.getType() == NodeType.SENSOR_LOOK_DIRECTION
            && parameterNode.isSensorLookSingleAxisMode()) {
            axis = parameterNode.getSensorLookComponentKey();
        } else {
            String source = values.get(LOOK_DIRECTION_SOURCE_KEY);
            if (LOOK_DIRECTION_SOURCE_VALUE.equals(source)) {
                axis = values.get(LOOK_DIRECTION_AXIS_KEY);
            }
        }
        if (axis == null || axis.isEmpty()) {
            return values;
        }

        String amount = values.get("Amount");
        if (amount == null || amount.isEmpty()) {
            amount = values.get(Node.normalizeParameterKey("Amount"));
        }
        if (amount == null || amount.isEmpty()) {
            return values;
        }

        Map<String, String> remapped = new HashMap<>(values);
        if ("Yaw".equalsIgnoreCase(axis)) {
            remapped.put("Yaw", amount);
            remapped.put(Node.normalizeParameterKey("Yaw"), amount);
            remapped.remove("Pitch");
            remapped.remove(Node.normalizeParameterKey("Pitch"));
        } else if ("Pitch".equalsIgnoreCase(axis)) {
            remapped.put("Pitch", amount);
            remapped.put(Node.normalizeParameterKey("Pitch"), amount);
            remapped.remove("Yaw");
            remapped.remove(Node.normalizeParameterKey("Yaw"));
        } else {
            return values;
        }
        return remapped;
    }

    private void sendVariableError(String message, CompletableFuture<Void> future) {
        NodeExecutionCompletion.failWithCurrentClient(owner, future, message);
    }

    static float normalizeLookYaw(float yaw) {
        return Mth.wrapDegrees(yaw);
    }

    static int parseNodeInt(Node node, String name, int defaultValue) {
        NodeType nodeType = node.getType();
        switch (nodeType) {
            case NodeType.OPERATOR_RANDOM -> {
              double min = node.getDoubleParameter("Min", 0.0);
              double max = node.getDoubleParameter("Max", 1.0);
              return (int) Math.round(node.generateRandomValueWithRounding(min, max));
            }
            case NodeType.OPERATOR_MOD -> {
              return (int) Math.round(node.resolveModValue().orElse((double) defaultValue));
            }
            case NodeType.LIST_LENGTH -> {
              return node.resolveListLengthValue(node).orElse(defaultValue);
            }
            case NodeType.VARIABLE -> {
              String variableName = getParameterString(node, "Variable");
              Node resolved = node.resolveVariableValueNode(node, 0, null);
              if (resolved == null) {
                return defaultValue;
              }
              if (resolved.getType() == NodeType.PARAM_INVENTORY_SLOT) {
                return parseNodeInt(resolved, name, defaultValue);
              }
              Optional<Double> value = node.resolveComparableNumber(resolved);
              if (value.isPresent()) {
                return (int) Math.round(value.get());
              }
              Minecraft client = Minecraft.getInstance();
              if (client != null && variableName != null && !variableName.trim().isEmpty()) {
                node.sendNodeErrorMessage(client, Node.tr("pathmind.error.variableNotNumeric", variableName.trim()));
              }
              return defaultValue;
            }
        }
			String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        Integer relativeCoordinate = resolveRelativeCoordinateValue(node, name, value);
        if (relativeCoordinate != null) {
            return relativeCoordinate;
        }
        Double evaluated = evaluateNumericExpression(value);
        if (evaluated != null) {
            return (int) Math.round(evaluated);
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null) {
                node.sendNodeErrorMessage(client, Node.tr("pathmind.error.enterNumberExpressionOrVariable"));
            }
            return defaultValue;
        }
    }

    static double parseNodeDouble(Node node, String name, double defaultValue) {
        NodeType nodeType = node.getType();
        switch (nodeType) {
            case NodeType.OPERATOR_RANDOM -> {
                double min = node.getDoubleParameter("Min", 0.0);
                double max = node.getDoubleParameter("Max", 1.0);
                return node.generateRandomValueWithRounding(min, max);
            }
            case NodeType.OPERATOR_MOD -> {
                return node.resolveModValue().orElse(defaultValue);
            }
            case NodeType.LIST_LENGTH -> {
                Optional<Integer> length = node.resolveListLengthValue(node);
                if (length.isPresent()) {
                    return length.get();
                }
            }
            case NodeType.VARIABLE -> {
                String variableName = getParameterString(node, "Variable");
                Node resolved = node.resolveVariableValueNode(node, 0, null);
                if (resolved == null) {
                    return defaultValue;
                }
                if (resolved.getType() == NodeType.PARAM_INVENTORY_SLOT) {
                    return parseNodeDouble(resolved, name, defaultValue);
                }
                Optional<Double> value = node.resolveComparableNumber(resolved);
                if (value.isPresent()) {
                    return value.get();
                }
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                if (client != null && variableName != null && !variableName.trim().isEmpty()) {
                    node.sendNodeErrorMessage(client, Node.tr("pathmind.error.variableNotNumeric", variableName.trim()));
                }
                return defaultValue;
            }
        };
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        Double evaluated = evaluateNumericExpression(value);
        if (evaluated != null) {
            return evaluated;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null) {
                node.sendNodeErrorMessage(client, Node.tr("pathmind.error.enterNumberExpressionOrVariable"));
            }
            return defaultValue;
        }
    }

    static boolean parseNodeBoolean(Node node, String name, boolean defaultValue) {
        if (node != null && node.getType() == NodeType.VARIABLE) {
            Node resolved = node.resolveVariableValueNode(node, 0, null);
            return node.resolveBooleanFromNode(resolved).orElse(defaultValue);
        }
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return node.resolveBooleanValueFromRaw(value, false).orElse(defaultValue);
    }

    static Float parseNodeFloat(Node node, String name) {
        String value = getParameterString(node, name);
        if (value == null || value.isEmpty()) {
            return null;
        }
        Float relativeLook = resolveRelativeLookValue(node, name, value);
        if (relativeLook != null) {
            return relativeLook;
        }
        Double evaluated = evaluateNumericExpression(value);
        if (evaluated != null) {
            return evaluated.floatValue();
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Float parseFloatOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer parseIntOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Double parseDoubleOrNull(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    int getIntParameter(String name, int defaultValue) {
        NodeParameter param = owner.getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String rawValue = param.getStringValue();
        String resolvedValue = owner.resolveRuntimeVariablesInText(rawValue);
        if (resolvedValue == null || resolvedValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(resolvedValue.trim());
        } catch (NumberFormatException ignored) {
            try {
                return (int) Math.round(Double.parseDouble(resolvedValue.trim()));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    String getStringParameter(String name, String defaultValue) {
        NodeParameter param = owner.getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String value = param.getStringValue();
        if (value == null) {
            return defaultValue;
        }
        String resolved = owner.resolveRuntimeVariablesInText(value);
        return resolved != null ? resolved : defaultValue;
    }

    static String getParameterString(Node node, String name) {
        if (node == null || name == null) {
            return null;
        }
        NodeParameter parameter = node.getParameter(name);
        String value = parameter != null ? parameter.getStringValue() : null;
        if (value == null) {
            Map<String, String> exported = node.exportParameterValues();
            if (exported != null && !exported.isEmpty()) {
                value = exported.get(name);
                if (value == null) {
                    value = exported.get(Node.normalizeParameterKey(name));
                }
            }
        }
        if (value == null) {
            return null;
        }
        return node.resolveRuntimeVariablesInText(value);
    }

    double getDoubleParameter(String name, double defaultValue) {
        NodeParameter param = owner.getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        String rawValue = param.getStringValue();
        String resolvedValue = owner.resolveRuntimeVariablesInText(rawValue);
        if (resolvedValue == null || resolvedValue.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(resolvedValue.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    boolean getBooleanParameter(String name, boolean defaultValue) {
        NodeParameter param = owner.getParameter(name);
        if (param == null) {
            return defaultValue;
        }
        return resolveBooleanValueFromRaw(param.getStringValue(), false).orElse(defaultValue);
    }

    Optional<Boolean> resolveBooleanValueFromRaw(
        String rawValue, boolean allowBareVariableName
    ) {
        if (rawValue == null) {
            return Optional.empty();
        }
        String trimmedRaw = rawValue.trim();
        if (trimmedRaw.isEmpty()) {
            return Optional.empty();
        }

        String resolvedValue = owner.resolveRuntimeVariablesInText(trimmedRaw);
        Optional<Boolean> parsedResolved = parseFlexibleBoolean(resolvedValue);
        if (parsedResolved.isPresent()) {
            return parsedResolved;
        }

        if (!allowBareVariableName) {
            return Optional.empty();
        }

        String variableName =
            trimmedRaw.startsWith("$") ? trimmedRaw.substring(1).trim() : trimmedRaw;
        if (variableName.isEmpty()) {
            return Optional.empty();
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = owner.getOwningStartNode();
        if (startNode == null && owner.getParentControl() != null) {
            startNode = owner.getParentControl().getOwningStartNode();
        }
        ExecutionManager.RuntimeVariable variable =
            owner.resolveRuntimeVariableForName(manager, startNode, variableName);
        return parseRuntimeVariableBoolean(variable);
    }

    private Optional<Boolean> parseRuntimeVariableBoolean(
        ExecutionManager.RuntimeVariable variable
    ) {
        if (variable == null) {
            return Optional.empty();
        }
        if (variable.getType() == NodeType.PARAM_BOOLEAN) {
            return parseFlexibleBoolean(owner.getRuntimeValue(variable.getValues(), "toggle"));
        }
        return parseFlexibleBoolean(owner.formatRuntimeVariableValue(variable));
    }

    Optional<Boolean> resolveBooleanNodeValue(Node node) {
        if (node == null || node.getType() != NodeType.PARAM_BOOLEAN) {
            return Optional.empty();
        }
        node.ensureBooleanParameters();
        if (node.isBooleanModeVariable()) {
            NodeParameter variableParameter = node.getParameter("Variable");
            String variableValue =
                variableParameter != null ? variableParameter.getStringValue() : null;
            return node.resolveBooleanValueFromRaw(variableValue, true);
        }
        NodeParameter toggleParameter = node.getParameter("Toggle");
        String value = toggleParameter != null ? toggleParameter.getStringValue() : null;
        if ((value == null || value.trim().isEmpty()) && toggleParameter != null) {
            value = toggleParameter.getDefaultValue();
        }
        return node.resolveBooleanValueFromRaw(value, false);
    }

    private static Optional<Boolean> parseFlexibleBoolean(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return Optional.of(true);
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    static double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        Double evaluated = evaluateNumericExpression(value);
        if (evaluated != null) {
            return evaluated;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static Double evaluateNumericExpression(String value) {
        if (value == null) {
            return null;
        }
        NumericExpressionParser parser = new NumericExpressionParser(value);
        return parser.parse();
    }

    private static Integer resolveRelativeCoordinateValue(
        Node node, String name, String value
    ) {
        if (!RelativeInputSupport.supportsRelativeCoordinate(node, name)
            || !RelativeInputSupport.isRelativeExpression(value)) {
            return null;
        }
        Double resolved =
            RelativeInputSupport.resolveRelativeExpression(
                value, getCurrentCoordinateAxisValue(name));
        return resolved != null ? (int) Math.round(resolved) : null;
    }

    private static Float resolveRelativeLookValue(Node node, String name, String value) {
        if (!RelativeInputSupport.supportsRelativeLook(node, name)
            || !RelativeInputSupport.isRelativeExpression(value)) {
            return null;
        }
        Double resolved =
            RelativeInputSupport.resolveRelativeExpression(value, getCurrentLookAxisValue(name));
        return resolved != null ? resolved.floatValue() : null;
    }

    private static int getCurrentCoordinateAxisValue(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return 0;
        }
        BlockPos playerPos = client.player.blockPosition();
        if ("X".equalsIgnoreCase(name)) {
            return playerPos.getX();
        }
        if ("Y".equalsIgnoreCase(name)) {
            return playerPos.getY();
        }
        if ("Z".equalsIgnoreCase(name)) {
            return playerPos.getZ();
        }
        return 0;
    }

    private static float getCurrentLookAxisValue(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return 0.0F;
        }
        if ("Yaw".equalsIgnoreCase(name)) {
            return client.player.getYRot();
        }
        if ("Pitch".equalsIgnoreCase(name)) {
            return client.player.getXRot();
        }
        return 0.0F;
    }

    private static final class NumericExpressionParser {
        private final String input;
        private int index;

        private NumericExpressionParser(String input) {
            this.input = input == null ? "" : input;
        }

        private Double parse() {
            skipWhitespace();
            Double result = parseExpression();
            if (result == null) {
                return null;
            }
            skipWhitespace();
            return index == input.length() ? result : null;
        }

        private Double parseExpression() {
            Double value = parseTerm();
            if (value == null) {
                return null;
            }
            while (true) {
                skipWhitespace();
                if (consume('+')) {
                    Double rhs = parseTerm();
                    if (rhs == null) {
                        return null;
                    }
                    value += rhs;
                } else if (consume('-')) {
                    Double rhs = parseTerm();
                    if (rhs == null) {
                        return null;
                    }
                    value -= rhs;
                } else {
                    return value;
                }
            }
        }

        private Double parseTerm() {
            Double value = parsePower();
            if (value == null) {
                return null;
            }
            while (true) {
                skipWhitespace();
                if (consume('*')) {
                    Double rhs = parsePower();
                    if (rhs == null) {
                        return null;
                    }
                    value *= rhs;
                } else if (consume('/')) {
                    Double rhs = parsePower();
                    if (rhs == null || rhs == 0.0D) {
                        return null;
                    }
                    value /= rhs;
                } else {
                    return value;
                }
            }
        }

        private Double parsePower() {
            Double base = parseFactor();
            if (base == null) {
                return null;
            }
            skipWhitespace();
            if (!consume('^')) {
                return base;
            }
            Double exponent = parsePower();
            if (exponent == null) {
                return null;
            }
            return Math.pow(base, exponent);
        }

        private Double parseFactor() {
            skipWhitespace();
            if (consume('+')) {
                return parseFactor();
            }
            if (consume('-')) {
                Double value = parseFactor();
                return value != null ? -value : null;
            }
            return parseNumber();
        }

        private Double parseNumber() {
            skipWhitespace();
            int start = index;
            boolean sawDigit = false;
            boolean sawDecimal = false;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isDigit(current)) {
                    sawDigit = true;
                    index++;
                    continue;
                }
                if (current == '.') {
                    if (sawDecimal) {
                        break;
                    }
                    sawDecimal = true;
                    index++;
                    continue;
                }
                break;
            }
            if (!sawDigit) {
                index = start;
                return null;
            }
            try {
                return Double.parseDouble(input.substring(start, index));
            } catch (NumberFormatException e) {
                index = start;
                return null;
            }
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean consume(char expected) {
            if (index >= input.length() || input.charAt(index) != expected) {
                return false;
            }
            index++;
            return true;
        }
    }
}
