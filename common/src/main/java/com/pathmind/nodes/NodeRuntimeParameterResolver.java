package com.pathmind.nodes;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.util.EntityCompatibilityBridge;
import com.pathmind.util.EntityStateOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
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
        if (!handled
            && owner.getType() == NodeType.CLICK_SLOT
            && owner.providesTrait(parameterNode, NodeValueTrait.INVENTORY_SLOT)) {
            // Click Slot keeps no parameter of its own; the executor reads the index straight off
            // this attachment. So applyParameterValuesFromMap has nothing to write into and cannot
            // mark the slot handled, which would otherwise fall through as an incompatible parameter.
            handled = true;
        }
        if (!handled
            && owner.getType() == NodeType.WALK
            && owner.isWalkUntilMode()
            && slotIndex == 1
            && parameterNode.isSensorNode()
            && (NodeCatalog.isBooleanSensor(parameterNode.getType())
                || parameterNode.getProvidedTraits().contains(NodeValueTrait.BOOLEAN))) {
            // Walk Until evaluates this sensor directly while it keeps the node active. It has no
            // host parameter to write into, so claim the attachment explicitly.
            handled = true;
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

    Optional<Vec3> resolvePositionTarget(
        Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future
    ) {
        if (parameterNode == null) {
            return Optional.empty();
        }
        if (parameterNode.getType() == NodeType.OPERATOR_BOOLEAN_OR) {
            Optional<Vec3> resolved =
                resolveNearestPositionTargetFromOrNode(parameterNode, future);
            if (resolved.isPresent() && data != null) {
                Vec3 vec = resolved.get();
                data.targetVector = vec;
                data.targetBlockPos =
                    new BlockPos(Mth.floor(vec.x), Mth.floor(vec.y), Mth.floor(vec.z));
            }
            return resolved;
        }
        if (parameterNode != null && parameterNode.getType() == NodeType.LIST_ITEM) {
            Node resolved =
                owner.resolveListItemValueNode(parameterNode, future, false, data);
            if (resolved != null) {
                return resolvePositionTarget(resolved, data, future);
            }
        }
        if (parameterNode != null
            && parameterNode.getType() == NodeType.SENSOR_POSITION_OF) {
            Node resolved =
                parameterNode.getAttachedParameterOfType(
                    NodeType.PARAM_ENTITY,
                    NodeType.PARAM_BLOCK,
                    NodeType.PARAM_ITEM,
                    NodeType.PARAM_PLAYER);
            if (resolved != null) {
                return resolvePositionTarget(resolved, data, future);
            }
            return Optional.empty();
        }
        if (parameterNode != null
            && parameterNode.getType() == NodeType.SENSOR_TARGETED_ENTITY) {
            Optional<Entity> resolved = owner.getTargetedEntity();
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            Entity entity = resolved.get();
            if (data != null) {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                data.targetEntity = entity;
                data.targetEntityId = id.toString();
                data.targetBlockPos = entity.blockPosition();
            }
            Vec3 pos = EntityCompatibilityBridge.getPos(entity);
            if (pos == null) {
                pos = Vec3.atCenterOf(entity.blockPosition());
            }
            if (data != null) {
                data.targetVector = pos;
            }
            return Optional.of(pos);
        }
        if (parameterNode != null
            && parameterNode.getType() == NodeType.SENSOR_TARGETED_BLOCK) {
            Optional<BlockPos> resolved = owner.getTargetedBlockPos();
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            if (data != null) {
                data.targetBlockPos = resolved.get();
            }
            return Optional.of(Vec3.atCenterOf(resolved.get()));
        }
        if (data != null && data.targetVector != null) {
            return Optional.of(data.targetVector);
        }
        if (data != null
            && data.targetBlockPos != null
            && parameterNode.getType() == NodeType.LIST_ITEM) {
            return Optional.of(Vec3.atCenterOf(data.targetBlockPos));
        }

        NodeType parameterType = parameterNode.getType();

        NodeBehaviorDefinition behaviorDefinition =
            NodeBehaviorDefinitionRegistry.get(parameterType);
        if (behaviorDefinition != null && behaviorDefinition.hasRuntimeBehavior()) {
            return behaviorDefinition.resolvePositionTarget(
                owner, parameterNode, data, future);
        }

        String xValue = getParameterString(parameterNode, "X");
        String yValue = getParameterString(parameterNode, "Y");
        String zValue = getParameterString(parameterNode, "Z");
        if (xValue != null && yValue != null && zValue != null) {
            double x = parseNodeDouble(parameterNode, "X", 0.0);
            double y = parseNodeDouble(parameterNode, "Y", 0.0);
            double z = parseNodeDouble(parameterNode, "Z", 0.0);
            BlockPos pos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            if (data != null) {
                data.targetBlockPos = pos;
            }
            Vec3 vector = new Vec3(x, y, z);
            if (data != null) {
                data.targetVector = vector;
            }
            return Optional.of(vector);
        }

        return Optional.empty();
    }

    private Optional<Vec3> resolveNearestPositionTargetFromOrNode(
        Node orNode, CompletableFuture<Void> future
    ) {
        net.minecraft.client.Minecraft client =
            net.minecraft.client.Minecraft.getInstance();
        Vec3 reference =
            client != null && client.player != null
                ? EntityCompatibilityBridge.getPos(client.player)
                : null;
        if (reference == null && client != null && client.player != null) {
            reference = Vec3.atCenterOf(client.player.blockPosition());
        }

        Optional<Vec3> firstResolved = Optional.empty();
        Vec3 nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        List<Integer> slotIndices = orNode.getAttachedParameterSlotIndices();
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node child = orNode.getAttachedParameter(slotIndex);
            if (child == null) {
                continue;
            }
            Optional<Vec3> candidate = resolvePositionTarget(child, null, future);
            if (candidate.isEmpty()) {
                if (future != null && future.isDone()) {
                    return Optional.empty();
                }
                continue;
            }
            if (firstResolved.isEmpty()) {
                firstResolved = candidate;
            }
            if (reference == null) {
                continue;
            }
            double distanceSq = candidate.get().distanceToSqr(reference);
            if (nearest == null || distanceSq < nearestDistanceSq) {
                nearest = candidate.get();
                nearestDistanceSq = distanceSq;
            }
        }

        if (nearest != null) {
            return Optional.of(nearest);
        }
        return firstResolved;
    }

    Optional<Vec3> resolveDistanceBetweenTarget(Node parameterNode) {
        if (parameterNode == null) {
            return Optional.empty();
        }
        int slotIndex = parameterNode.getParentParameterSlotIndex();
        if (slotIndex < 0) {
            slotIndex = 0;
        }
        parameterNode = owner.resolveSensorParameterNode(parameterNode, slotIndex);
        if (parameterNode == null) {
            return Optional.empty();
        }
        if (parameterNode.getType() != NodeType.PARAM_ENTITY) {
            return resolvePositionTarget(parameterNode, null, null);
        }

        net.minecraft.client.Minecraft client =
            net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }

        String state = owner.getEntityParameterState(parameterNode);
        double range = parseNodeDouble(parameterNode, "Range", 256.0);
        double searchRadius = Math.max(1.0, range);
        List<String> entityIds = owner.resolveEntityIdsFromParameter(parameterNode);
        if (entityIds.isEmpty()) {
            Entity nearestAny = null;
            double nearestAnyDistance = Double.MAX_VALUE;
            AABB anySearchBox = client.player.getBoundingBox().inflate(searchRadius);
            for (Entity entity : client.level.getEntities(client.player, anySearchBox)) {
                if (entity == null || entity.isRemoved()) {
                    continue;
                }
                if (!EntityStateOptions.matchesState(entity, state)) {
                    continue;
                }
                double distance = entity.distanceToSqr(client.player);
                if (nearestAny == null || distance < nearestAnyDistance) {
                    nearestAny = entity;
                    nearestAnyDistance = distance;
                }
            }
            if (nearestAny == null) {
                return Optional.empty();
            }
            Vec3 pos = EntityCompatibilityBridge.getPos(nearestAny);
            if (pos != null) {
                return Optional.of(pos);
            }
            return Optional.of(Vec3.atCenterOf(nearestAny.blockPosition()));
        }

        AABB searchBox = client.player.getBoundingBox().inflate(searchRadius);

        java.util.Set<Identifier> targetIds = new java.util.HashSet<>();
        for (String candidateId : entityIds) {
            Identifier id = Identifier.tryParse(candidateId);
            if (id != null) {
                targetIds.add(id);
            }
        }
        if (targetIds.isEmpty()) {
            return Optional.empty();
        }

        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : client.level.getEntities(client.player, searchBox)) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            Identifier candidateId =
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (!targetIds.contains(candidateId)
                || !EntityStateOptions.matchesState(entity, state)) {
                continue;
            }
            double distance = entity.distanceToSqr(client.player);
            if (nearest == null || distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }

        if (nearest == null) {
            return Optional.empty();
        }
        Vec3 pos = EntityCompatibilityBridge.getPos(nearest);
        if (pos != null) {
            return Optional.of(pos);
        }
        return Optional.of(Vec3.atCenterOf(nearest.blockPosition()));
    }

    boolean isDistanceBetweenSupportedTarget(Node parameterNode) {
        return parameterNode != null
            && (owner.providesTrait(parameterNode, NodeValueTrait.ENTITY)
                || owner.providesTrait(parameterNode, NodeValueTrait.COORDINATE)
                || owner.providesTrait(parameterNode, NodeValueTrait.BLOCK)
                || owner.providesTrait(parameterNode, NodeValueTrait.ITEM)
                || owner.providesTrait(parameterNode, NodeValueTrait.PLAYER));
    }

    void applyVectorToCoordinateParameters(Vec3 targetVec) {
        if (targetVec == null) {
            return;
        }
        int x = Mth.floor(targetVec.x);
        int y = Mth.floor(targetVec.y);
        int z = Mth.floor(targetVec.z);
        if (owner.runtimeState().runtimeParameterData != null) {
            owner.runtimeState().runtimeParameterData.targetBlockPos =
                new BlockPos(x, y, z);
        }
        owner.setParameterValueAndPropagate("X", Integer.toString(x));
        owner.setParameterValueAndPropagate("Y", Integer.toString(y));
        owner.setParameterValueAndPropagate("Z", Integer.toString(z));
    }

    boolean isPlayerAtCoordinates(
        Integer targetX, Integer targetY, Integer targetZ
    ) {
        net.minecraft.client.Minecraft client =
            net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }
        BlockPos playerPos = client.player.blockPosition();
        if (targetX != null && playerPos.getX() != targetX) {
            return false;
        }
        if (targetY != null && playerPos.getY() != targetY) {
            return false;
        }
        if (targetZ != null && playerPos.getZ() != targetZ) {
            return false;
        }
        return true;
    }

    boolean resolveLookOrientation(
        Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future
    ) {
        net.minecraft.client.Minecraft client =
            net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            return false;
        }

        if (parameterNode != null && parameterNode.getType() == NodeType.LIST_ITEM) {
            Node resolved =
                owner.resolveListItemValueNode(parameterNode, future, false, data);
            if (resolved != null) {
                return resolveLookOrientation(resolved, data, future);
            }
        }

        if (parameterNode != null
            && parameterNode.getType() == NodeType.PARAM_BLOCK_FACE) {
            Node targetNode = parameterNode.getAttachedParameter(0);
            if (targetNode == null) {
                return false;
            }
            if (targetNode.getType() == NodeType.VARIABLE) {
                targetNode = resolveVariableValueNode(targetNode, 0, future);
                if (targetNode == null) {
                    return false;
                }
            }

            String faceName = getParameterString(parameterNode, "Face");
            if (faceName == null || faceName.trim().isEmpty()) {
                faceName = getParameterString(parameterNode, "Side");
            }
            Direction targetFace = parseDirectionValue(faceName);
            if (targetFace == null) {
                targetFace = Direction.NORTH;
            }

            // Resolve the nested target independently so any temporary vector state on the outer
            // runtime context cannot override the actual block/coordinate target.
            RuntimeParameterData targetData = new RuntimeParameterData();
            Optional<Vec3> resolvedTarget =
                resolvePositionTarget(targetNode, targetData, future);
            if (resolvedTarget.isEmpty()) {
                return false;
            }

            BlockPos targetBlockPos = targetData.targetBlockPos;
            if (targetBlockPos == null) {
                Vec3 targetVec = resolvedTarget.get();
                targetBlockPos =
                    new BlockPos(
                        Mth.floor(targetVec.x),
                        Mth.floor(targetVec.y),
                        Mth.floor(targetVec.z));
                if (data != null) {
                    data.targetBlockPos = targetBlockPos;
                }
            }

            Vec3 faceCenter =
                Vec3.atCenterOf(targetBlockPos)
                    .add(
                        targetFace.getStepX() * 0.5D,
                        targetFace.getStepY() * 0.5D,
                        targetFace.getStepZ() * 0.5D);
            Vec3 eyes = client.player.getEyePosition();
            Vec3 delta = faceCenter.subtract(eyes);
            if (delta.lengthSqr() < 1.0E-6) {
                return false;
            }

            float yaw =
                (float)
                    (Mth.wrapDegrees(
                        Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
            float pitch =
                (float)
                    (-Math.toDegrees(
                        Math.atan2(
                            delta.y,
                            Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
            float clampedPitch = Mth.clamp(pitch, -90.0F, 90.0F);

            setParameterIfPresent("Yaw", formatFloat(yaw));
            setParameterIfPresent("Pitch", formatFloat(clampedPitch));

            if (data != null) {
                data.targetBlockPos = targetBlockPos;
                data.targetVector = faceCenter;
                data.resolvedYaw = yaw;
                data.resolvedPitch = clampedPitch;
            }
            return true;
        }

        boolean allowDirectRotation =
            parameterNode.getType() != NodeType.PARAM_DIRECTION
                || parameterNode.isDirectionModeExact();
        Float yawParam =
            allowDirectRotation ? parseNodeFloat(parameterNode, "Yaw") : null;
        Float pitchParam =
            allowDirectRotation ? parseNodeFloat(parameterNode, "Pitch") : null;
        if (allowDirectRotation
            && yawParam == null
            && pitchParam == null
            && owner.providesTrait(parameterNode, NodeValueTrait.ROTATION)) {
            Map<String, String> exported = parameterNode.exportParameterValues();
            yawParam = parseFloatOrNull(exported.get("Yaw"));
            pitchParam = parseFloatOrNull(exported.get("Pitch"));
            if (data != null) {
                Double distance = parseDoubleOrNull(exported.get("Distance"));
                if (distance == null) {
                    distance = parseDoubleOrNull(exported.get("LookDistance"));
                }
                if (distance == null) {
                    distance = parseDoubleOrNull(exported.get("Range"));
                }
                if (distance != null && distance > 0.0) {
                    data.resolvedLookDistance = distance;
                }
            }
        }
        if (yawParam != null || pitchParam != null) {
            if (yawParam != null) {
                setParameterIfPresent("Yaw", formatFloat(yawParam));
                if (data != null) {
                    data.resolvedYaw = yawParam;
                }
            }
            if (pitchParam != null) {
                float clamped = Mth.clamp(pitchParam, -90.0F, 90.0F);
                setParameterIfPresent("Pitch", formatFloat(clamped));
                if (data != null) {
                    data.resolvedPitch = clamped;
                }
            }
            if (data != null) {
                double distance = parseNodeDouble(parameterNode, "Distance", -1.0);
                if (distance > 0.0) {
                    data.resolvedLookDistance = distance;
                }
            }
            return true;
        }

        if (owner.getType() == NodeType.LOOK
            && owner.providesTrait(parameterNode, NodeValueTrait.NUMBER)) {
            float yaw =
                (float)
                    Mth.wrapDegrees(
                        client.player.getYRot()
                            + parseNodeDouble(parameterNode, "Amount", 0.0));
            setParameterIfPresent("Yaw", formatFloat(yaw));
            if (data != null) {
                data.resolvedYaw = yaw;
                data.resolvedPitch = client.player.getXRot();
            }
            return true;
        }

        if (owner.providesTrait(parameterNode, NodeValueTrait.DIRECTION)
            && (parameterNode.getType() != NodeType.PARAM_DIRECTION
                || parameterNode.isDirectionModeCardinal())) {
            String direction = getParameterString(parameterNode, "Direction");
            if (direction == null || direction.isEmpty()) {
                direction = getParameterString(parameterNode, "Side");
            }
            if (direction == null || direction.isEmpty()) {
                direction = getParameterString(parameterNode, "Face");
            }
            if (direction == null || direction.isEmpty()) {
                Map<String, String> exported = parameterNode.exportParameterValues();
                direction = exported.get("Direction");
                if (direction == null || direction.isEmpty()) {
                    direction = exported.get("Side");
                }
                if (direction == null || direction.isEmpty()) {
                    direction = exported.get("Face");
                }
            }
            if (direction != null) {
                String normalized = direction.trim().toLowerCase(Locale.ROOT);
                Float yaw = null;
                Float pitch = null;
                switch (normalized) {
                    case "north" -> {
                        yaw = 180.0F;
                    }
                    case "south" -> {
                        yaw = 0.0F;
                    }
                    case "west" -> {
                        yaw = 90.0F;
                    }
                    case "east" -> {
                        yaw = -90.0F;
                    }
                    case "up" -> {
                        yaw = client.player.getYRot();
                        pitch = -90.0F;
                    }
                    case "down" -> {
                        yaw = client.player.getYRot();
                        pitch = 90.0F;
                    }
                }
                if (yaw != null) {
                    setParameterIfPresent("Yaw", formatFloat(yaw));
                    if (data != null) {
                        data.resolvedYaw = yaw;
                    }
                }
                if (pitch != null) {
                    float clamped = Mth.clamp(pitch, -90.0F, 90.0F);
                    setParameterIfPresent("Pitch", formatFloat(clamped));
                    if (data != null) {
                        data.resolvedPitch = clamped;
                    }
                }
                if (yaw != null) {
                    if (data != null) {
                        double distance =
                            parseNodeDouble(parameterNode, "Distance", -1.0);
                        if (distance > 0.0) {
                            data.resolvedLookDistance = distance;
                        }
                    }
                    return true;
                }
            }
        }

        Vec3 target = null;
        if (data != null && data.targetEntity != null && data.targetEntity.isAlive()) {
            target = data.targetEntity.getBoundingBox().getCenter();
        }
        if (target == null && data != null) {
            target = data.targetVector;
        }
        if (target == null) {
            Optional<Vec3> resolved =
                resolvePositionTarget(parameterNode, data, future);
            if (resolved.isEmpty()) {
                return false;
            }
            target = resolved.get();
        }

        Vec3 eyes = client.player.getEyePosition();
        Vec3 delta = target.subtract(eyes);
        if (delta.lengthSqr() < 1.0E-6) {
            return false;
        }
        float yaw =
            (float)
                (Mth.wrapDegrees(
                    Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
        float pitch =
            (float)
                (-Math.toDegrees(
                    Math.atan2(
                        delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        float clampedPitch = Mth.clamp(pitch, -90.0F, 90.0F);

        setParameterIfPresent("Yaw", formatFloat(yaw));
        setParameterIfPresent("Pitch", formatFloat(clampedPitch));

        if (data != null) {
            data.resolvedYaw = yaw;
            data.resolvedPitch = clampedPitch;
        }
        return true;
    }

    private Direction parseDirectionValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    void orientPlayerTowardsRuntimeTarget(
        net.minecraft.client.Minecraft client, RuntimeParameterData data
    ) {
        if (client == null || client.player == null || data == null) {
            return;
        }

        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();
        boolean applyYaw = false;
        boolean applyPitch = false;

        Vec3 targetVector = null;
        if (data.targetEntity != null && data.targetEntity.isAlive()) {
            targetVector = data.targetEntity.getBoundingBox().getCenter();
        }
        if (targetVector == null && data.targetVector != null) {
            targetVector = data.targetVector;
        }
        if (targetVector == null && data.targetBlockPos != null) {
            targetVector = Vec3.atCenterOf(data.targetBlockPos);
        }

        if (targetVector != null) {
            Vec3 eyes = client.player.getEyePosition();
            Vec3 delta = targetVector.subtract(eyes);
            if (delta.lengthSqr() > 1.0E-6) {
                yaw =
                    (float)
                        (Mth.wrapDegrees(
                            Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0D));
                pitch =
                    (float)
                        (-Math.toDegrees(
                            Math.atan2(
                                delta.y,
                                Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
                pitch = Mth.clamp(pitch, -90.0F, 90.0F);
                applyYaw = true;
                applyPitch = true;
            }
        }

        if (!applyYaw && data.resolvedYaw != null) {
            yaw = data.resolvedYaw;
            applyYaw = true;
        }
        if (!applyPitch && data.resolvedPitch != null) {
            pitch = Mth.clamp(data.resolvedPitch, -90.0F, 90.0F);
            applyPitch = true;
        }

        if (!applyYaw && !applyPitch) {
            return;
        }

        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
        client.player.setYHeadRot(yaw);

        if (applyYaw) {
            data.resolvedYaw = yaw;
        }
        if (applyPitch) {
            data.resolvedPitch = pitch;
        }
    }

    void sendIncompatibleParameterMessage(Node parameterNode) {
        net.minecraft.client.Minecraft client =
            net.minecraft.client.Minecraft.getInstance();
        if (client == null) {
            return;
        }
        if (parameterNode != null
            && (owner.getType() == NodeType.PLACE
                || owner.getType() == NodeType.PLACE_HAND)) {
            NodeType parameterType = parameterNode.getType();
            // Allow PARAM_CLOSEST in any slot
            if (parameterType == NodeType.PARAM_CLOSEST) {
                return;
            }
            // Allow block parameters in slot 0 (they provide block type, not position)
            if (parameterType == NodeType.PARAM_BLOCK
                && parameterNode.getAttachments().getParentParameterSlotIndex() == 0) {
                return;
            }
        }
        owner.sendNodeErrorMessage(
            client,
            Node.tr(
                "pathmind.error.incompatibleParameter",
                parameterNode.getType().getDisplayName(),
                owner.getType().getDisplayName()));
    }

    void sendParameterSearchFailure(
        String message, CompletableFuture<Void> future
    ) {
        // Only surface search failures during execution contexts (future != null).
        // UI/preview calls (future == null) should not spam chat.
        if (future != null) {
            NodeExecutionCompletion.failWithCurrentClient(owner, future, message);
        }
    }

    boolean reportEmptyParametersForNode(
        Node target, CompletableFuture<Void> future
    ) {
        if (target == null) {
            return true;
        }
        List<String> emptyNames = new ArrayList<>();
        collectEmptyUserEditedParameters(target, emptyNames);
        if (emptyNames.isEmpty()) {
            return true;
        }
        String joined = String.join(", ", emptyNames);
        String subject =
            target.getType() != null
                ? Node.tr(
                    "pathmind.error.subjectNodeType",
                    target.getType().getDisplayName())
                : Node.tr("pathmind.error.subjectNode");
        String message =
            emptyNames.size() == 1
                ? Node.tr("pathmind.error.parameterEmptyOnNode", joined, subject)
                : Node.tr("pathmind.error.parametersEmptyOnNode", joined, subject);
        NodeExecutionCompletion.failWithCurrentClient(owner, future, message);
        return false;
    }

    private void collectEmptyUserEditedParameters(
        Node target, List<String> emptyNames
    ) {
        if (target == null || emptyNames == null) {
            return;
        }
        for (NodeParameter parameter : target.getParameters()) {
            if (parameter == null || !parameter.isUserEdited()) {
                continue;
            }
            String value = parameter.getStringValue();
            if (value != null && !value.trim().isEmpty()) {
                continue;
            }
            String defaultValue = parameter.getDefaultValue();
            if (defaultValue != null && !defaultValue.isEmpty()) {
                emptyNames.add(parameter.getName());
            }
        }
    }

    boolean reportEmptyParametersForAttachedParameters(
        CompletableFuture<Void> future
    ) {
        if (!owner.getAttachments().hasAttachedParameters()) {
            return true;
        }
        for (Node parameterNode : owner.getAttachments().getAttachedParameterNodes()) {
            if (parameterNode == null || !parameterNode.isParameterNode()) {
                continue;
            }
            if (!reportEmptyParametersForNode(parameterNode, future)) {
                return false;
            }
        }
        return true;
    }

    void setParameterIfPresent(String name, String value) {
        if (name == null || value == null) {
            return;
        }
        NodeParameter parameter = owner.getParameter(name);
        if (parameter != null) {
            parameter.setStringValue(value);
        }
    }

    static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    double generateRandomValueWithRounding(double min, double max) {
        double value = generateRandomValue(min, max);
        if (!owner.isRandomRoundingEnabled()) {
            return value;
        }
        String mode = owner.getRandomRoundingMode();
        return switch (mode) {
            case "floor" -> Math.floor(value);
            case "ceil" -> Math.ceil(value);
            default -> (double) Math.round(value);
        };
    }

    private double generateRandomValue(double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max)) {
            return 0.0;
        }
        double lower = min;
        double upper = max;
        if (lower > upper) {
            double swap = lower;
            lower = upper;
            upper = swap;
        }
        if (lower == upper) {
            return lower;
        }
        Random generator = getRandomGenerator();
        double range = upper - lower;
        if (generator == null) {
            return lower + Math.random() * range;
        }
        return lower + generator.nextDouble() * range;
    }

    Optional<Double> resolveModValue() {
        Node left = owner.getAttachedParameter(0);
        Node right = owner.getAttachedParameter(1);
        if (left == null || right == null) {
            return Optional.empty();
        }
        Optional<Double> leftNumber =
            owner.resolveComparableNumberWithVariables(left, 0);
        Optional<Double> rightNumber =
            owner.resolveComparableNumberWithVariables(right, 1);
        if (leftNumber.isEmpty() || rightNumber.isEmpty()) {
            return Optional.empty();
        }
        double divisor = rightNumber.get();
        if (divisor == 0.0) {
            return Optional.empty();
        }
        return Optional.of(leftNumber.get() % divisor);
    }

    private Random getRandomGenerator() {
        String seed = getParameterString(owner, "Seed");
        if (seed == null || seed.trim().isEmpty() || isAnySeedValue(seed)) {
            owner.runtimeState().randomGenerator = null;
            owner.runtimeState().randomSeedCache = null;
            return null;
        }
        String trimmed = seed.trim();
        if (owner.runtimeState().randomGenerator == null
            || owner.runtimeState().randomSeedCache == null
            || !owner.runtimeState().randomSeedCache.equals(trimmed)) {
            long hashedSeed = hashSeedString(trimmed);
            owner.runtimeState().randomGenerator = new Random(hashedSeed);
            owner.runtimeState().randomSeedCache = trimmed;
        }
        return owner.runtimeState().randomGenerator;
    }

    private static long hashSeedString(String seed) {
        if (seed == null) {
            return 0L;
        }
        long hash = 1125899906842597L;
        for (int i = 0; i < seed.length(); i++) {
            hash = 31L * hash + seed.charAt(i);
        }
        return hash;
    }

    private boolean isAnySeedValue(String seed) {
        if (seed == null) {
            return true;
        }
        String trimmed = seed.trim();
        return trimmed.isEmpty() || "any".equalsIgnoreCase(trimmed);
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
        Double relativeCoordinate = resolveRelativeCoordinateDoubleValue(node, name, value);
        if (relativeCoordinate != null) {
            return relativeCoordinate;
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
        Double resolved = resolveRelativeCoordinateDoubleValue(node, name, value);
        return resolved != null ? (int) Math.round(resolved) : null;
    }

    private static Double resolveRelativeCoordinateDoubleValue(
        Node node, String name, String value
    ) {
        if (!RelativeInputSupport.supportsRelativeCoordinate(node, name)
            || !RelativeInputSupport.isRelativeExpression(value)) {
            return null;
        }
        return RelativeInputSupport.resolveRelativeExpression(
            value, getCurrentCoordinateAxisValue(name));
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
