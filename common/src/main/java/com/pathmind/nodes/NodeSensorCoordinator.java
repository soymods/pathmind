package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

final class NodeSensorCoordinator {
    private final Node owner;

    NodeSensorCoordinator(Node owner) {
        this.owner = owner;
    }

    boolean evaluateSensor() {
        if (!owner.isSensorNode()) {
            return false;
        }

        if (!owner.reportEmptyParametersForNode(owner, null)) {
            return false;
        }
        if (!owner.reportEmptyParametersForAttachedParameters(null)) {
            return false;
        }
        if (!ensureRequiredSensorParameterAttached()) {
            owner.runtimeState().lastSensorResult = false;
            return false;
        }

        boolean result = switch (owner.getType()) {
            case OPERATOR_EQUALS -> operatorSensorEvaluator().evaluateOperatorEquals();
            case OPERATOR_NOT -> operatorSensorEvaluator().evaluateOperatorNot();
            case OPERATOR_BOOLEAN_NOT -> operatorSensorEvaluator().evaluateOperatorBooleanNot();
            case OPERATOR_BOOLEAN_OR -> operatorSensorEvaluator().evaluateOperatorBooleanOr();
            case OPERATOR_BOOLEAN_AND -> operatorSensorEvaluator().evaluateOperatorBooleanAnd();
            case OPERATOR_BOOLEAN_XOR -> operatorSensorEvaluator().evaluateOperatorBooleanXor();
            case OPERATOR_GREATER -> operatorSensorEvaluator().evaluateOperatorGreater();
            case OPERATOR_LESS -> operatorSensorEvaluator().evaluateOperatorLess();
            case SENSOR_TOUCHING_BLOCK -> proximitySensorEvaluator().evaluateTouchingBlock();
            case SENSOR_TOUCHING_ENTITY -> proximitySensorEvaluator().evaluateTouchingEntity();
            case SENSOR_AT_COORDINATES -> proximitySensorEvaluator().evaluateAtCoordinates();
            case SENSOR_TARGETED_BLOCK -> targetSensorEvaluator().getTargetedBlockState().isPresent();
            case SENSOR_TARGETED_ENTITY -> targetSensorEvaluator().getTargetedEntity().isPresent();
            case SENSOR_LOOK_DIRECTION -> targetSensorEvaluator().getLookDirection().isPresent();
            case SENSOR_CURRENT_HAND -> targetSensorEvaluator().getCurrentHotbarSlot().isPresent();
            case SENSOR_TARGETED_BLOCK_FACE -> targetSensorEvaluator().getTargetedBlockFace().isPresent();
            case SENSOR_IS_DAYTIME -> basicSensorEvaluator().isDaytime();
            case SENSOR_IS_RAINING -> basicSensorEvaluator().isRaining();
            case SENSOR_GUI_FILLED -> guiSensorEvaluator().isOpenGuiFilled();
            case SENSOR_CURRENT_GUI -> guiSensorEvaluator().getCurrentGui().isPresent();
            case SENSOR_HEALTH_BELOW -> basicSensorEvaluator().evaluateHealthBelow();
            case SENSOR_HUNGER_BELOW -> basicSensorEvaluator().evaluateHungerBelow();
            case SENSOR_ITEM_IN_INVENTORY -> inventorySensorEvaluator().evaluateItemInInventory();
            case SENSOR_ITEM_IN_SLOT -> inventorySensorEvaluator().evaluateItemInSlot();
            case SENSOR_SLOT_ITEM_COUNT -> inventorySensorEvaluator().evaluateSlotItemCount();
            case SENSOR_DURABILITY_OF -> inventorySensorEvaluator().evaluateDurabilityOf();
            case SENSOR_IS_SWIMMING -> playerStateSensorEvaluator().isSwimming();
            case SENSOR_IS_IN_LAVA -> playerStateSensorEvaluator().isInLava();
            case SENSOR_IS_UNDERWATER -> playerStateSensorEvaluator().isUnderwater();
            case SENSOR_IS_FALLING -> playerStateSensorEvaluator().evaluateFalling();
            case SENSOR_KEY_PRESSED -> basicSensorEvaluator().evaluateKeyPressed();
            case SENSOR_IS_RENDERED -> visibilitySensorEvaluator().evaluateRendered();
            case SENSOR_IS_VISIBLE -> visibilitySensorEvaluator().evaluateVisible();
            case SENSOR_VILLAGER_TRADE -> villagerTradeSensorEvaluator().evaluateVillagerTrade();
            case SENSOR_IN_STOCK -> villagerTradeSensorEvaluator().evaluateInStock();
            case SENSOR_CHAT_MESSAGE -> eventSensorEvaluator().evaluateChatMessage();
            case SENSOR_JOINED_SERVER -> evaluateJoinedServerEdge();
            case SENSOR_FABRIC_EVENT -> eventSensorEvaluator().evaluateFabricEvent();
            case SENSOR_ATTRIBUTE_DETECTION -> attributeDetectionEvaluator().evaluateAttributeDetectionSensor();
            default -> false;
        };
        result = adjustBooleanToggleResult(result);
        recordSensorResult(result);
        return result;
    }

    boolean evaluateConditionFromParameters() {
        if (owner.getAttachedSensor() != null) {
            boolean result = owner.getAttachedSensor().evaluateSensor();
            owner.runtimeState().lastSensorResult = result;
            return result;
        }

        // Legacy fallback when no sensor is attached
        String condition = owner.getStringParameter("Condition", "Touching Block");
        String blockId = owner.getStringParameter("Block", "stone");
        String entityId = owner.getStringParameter("Entity", "zombie");
        int x = owner.getIntParameter("X", 0);
        int y = owner.getIntParameter("Y", 64);
        int z = owner.getIntParameter("Z", 0);
        boolean result = proximitySensorEvaluator().evaluateSensorCondition(
            Node.SensorConditionType.fromLabel(condition), blockId, entityId, x, y, z);
        owner.runtimeState().lastSensorResult = result;
        return result;
    }

    Node getAttachedParameterOfType(NodeType... allowedTypes) {
        NodeAttachments attachments = owner.getAttachments();
        if (!attachments.hasAttachedParameters()) {
            return null;
        }
        List<Integer> slotIndices = new ArrayList<>(attachments.getAttachedParameterSlotIndices());
        Collections.sort(slotIndices);
        for (Integer slotIndex : slotIndices) {
            Node parameter = attachments.getAttachedParameter(slotIndex);
            if (parameter == null || !parameter.isParameterNode()) {
                continue;
            }
            NodeType parameterType = parameter.getType();
            NodeType resolvedType = parameterType == NodeType.LIST_ITEM
                ? parameter.getResolvedValueType()
                : parameterType;
            for (NodeType allowed : allowedTypes) {
                if (parameterType == allowed || resolvedType == allowed) {
                    return parameter;
                }
            }
        }
        Node fallback = owner.getAttachedParameter();
        if (fallback != null) {
            owner.sendIncompatibleParameterMessage(fallback);
        }
        return null;
    }

    boolean providesTrait(Node node, NodeValueTrait trait) {
        if (node == null || trait == null) {
            return false;
        }
        EnumSet<NodeValueTrait> traits = node.getProvidedTraits();
        return traits.contains(trait);
    }

    Node resolveSensorParameterNode(Node parameterNode, int slotIndex) {
        if (parameterNode == null) {
            return null;
        }
        if (parameterNode.getType() == NodeType.VARIABLE) {
            return owner.resolveVariableValueNode(parameterNode, slotIndex, null);
        }
        return parameterNode;
    }

    Optional<BlockState> getTargetedBlockState() {
        return targetSensorEvaluator().getTargetedBlockState();
    }

    Optional<BlockPos> getTargetedBlockPos() {
        return targetSensorEvaluator().getTargetedBlockPos();
    }

    Optional<Entity> getTargetedEntity() {
        return targetSensorEvaluator().getTargetedEntity();
    }

    Optional<Integer> getCurrentHotbarSlot() {
        return targetSensorEvaluator().getCurrentHotbarSlot();
    }

    Optional<Direction> getTargetedBlockFace() {
        return targetSensorEvaluator().getTargetedBlockFace();
    }

    Optional<BlockHitResult> getCurrentBlockHitResult() {
        return targetSensorEvaluator().getCurrentBlockHitResult();
    }

    Optional<Double> getDistanceFromGround() {
        return playerStateSensorEvaluator().getDistanceFromGround();
    }

    Optional<Boolean> resolveBooleanFromNode(Node node) {
        return operatorSensorEvaluator().resolveBooleanFromNode(node);
    }

    Node createRuntimeVariableSnapshot(com.pathmind.execution.ExecutionManager.RuntimeVariable runtimeVariable) {
        return operatorSensorEvaluator().createRuntimeVariableSnapshot(runtimeVariable);
    }

    Optional<Boolean> compareParameterNodes(Node left, Node right) {
        return operatorSensorEvaluator().compareParameterNodes(left, right);
    }

    String formatCanonicalValueMap(Map<String, String> values) {
        return operatorSensorEvaluator().formatCanonicalValueMap(values);
    }

    Optional<Double> resolveComparableNumber(Node node) {
        return operatorSensorEvaluator().resolveComparableNumber(node);
    }

    Optional<Double> resolveComparableNumberWithVariables(Node node, int slotIndex) {
        return operatorSensorEvaluator().resolveComparableNumberWithVariables(node, slotIndex);
    }

    Optional<Integer> resolveInventorySlotCount(Node slotNode) {
        if (slotNode == null || !providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
            return Optional.empty();
        }
        Optional<ItemStack> resolvedStack = resolveInventorySlotStack(slotNode);
        if (resolvedStack.isEmpty()) {
            return Optional.empty();
        }
        ItemStack stack = resolvedStack.get();
        if (stack == null || stack.isEmpty()) {
            return Optional.of(0);
        }
        return Optional.of(stack.getCount());
    }

    Optional<Integer> resolveInventorySlotDurability(Node slotNode) {
        if (slotNode == null || !providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
            return Optional.empty();
        }
        Optional<ItemStack> resolvedStack = resolveInventorySlotStack(slotNode);
        if (resolvedStack.isEmpty()) {
            return Optional.empty();
        }
        ItemStack stack = resolvedStack.get();
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return Optional.of(0);
        }
        return Optional.of(Math.max(0, stack.getMaxDamage() - stack.getDamageValue()));
    }

    private Optional<ItemStack> resolveInventorySlotStack(Node slotNode) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        Inventory inventory = client.player.getInventory();
        AbstractContainerMenu handler = client.player.containerMenu;
        Map<String, String> values = slotNode.exportParameterValues();
        Integer comparableSlot = InventorySlotValueResolver.resolveComparableSlotIndex(values);
        int slotValue = comparableSlot != null ? comparableSlot : Node.parseNodeInt(slotNode, "Slot", 0);
        SlotSelectionType selectionType = comparableSlot != null
            ? InventorySlotValueResolver.resolveComparableSlotSelectionType(values)
            : owner.resolveInventorySlotSelectionType(slotNode);
        SlotResolution resolved = owner.resolveInventorySlot(handler, inventory, slotValue, selectionType);
        if (resolved == null || resolved.slot == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolved.slot.getItem());
    }

    boolean matchesAnyBlock(List<BlockSelection> selections, BlockState state) {
        return proximitySensorEvaluator().matchesAnyBlock(selections, state);
    }

    boolean stackMatchesAnyItem(ItemStack stack, List<String> itemIds) {
        return inventorySensorEvaluator().stackMatchesAnyItem(stack, itemIds);
    }

    Integer resolveKeyCode(String keyName) {
        return basicSensorEvaluator().resolveKeyCode(keyName);
    }

    Integer resolveMouseButtonCode(String buttonName) {
        return basicSensorEvaluator().resolveMouseButtonCode(buttonName);
    }

    private void recordSensorResult(boolean result) {
        owner.runtimeState().lastSensorResult = result;
        owner.runtimeState().hasSensorResult = true;
        owner.runtimeState().lastSensorUpdatedAt = System.currentTimeMillis();
    }

    private boolean evaluateJoinedServerEdge() {
        boolean rawResult = eventSensorEvaluator().evaluateJoinedServer();
        boolean edge = rawResult && !owner.runtimeState().lastJoinedServerRawResult;
        owner.runtimeState().lastJoinedServerRawResult = rawResult;
        return edge;
    }

    private boolean ensureRequiredSensorParameterAttached() {
        if (!owner.isSensorNode() || owner.hasAttachedParameter() || !sensorRequiresParameterNode()) {
            return true;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null) {
            owner.sendNodeErrorMessage(client, Node.tr(
                "pathmind.error.requiresParameterNode", owner.getType().getDisplayName()));
        }
        return false;
    }

    private boolean sensorRequiresParameterNode() {
        return NodeCatalog.isSensorParameterRequired(owner.getType());
    }

    private boolean adjustBooleanToggleResult(boolean rawResult) {
        if (!owner.hasBooleanToggle()) {
            return rawResult;
        }
        return owner.getBooleanToggleValue() == rawResult;
    }

    private NodeTargetSensorEvaluator targetSensorEvaluator() {
        return new NodeTargetSensorEvaluator(owner);
    }

    private NodeGuiSensorEvaluator guiSensorEvaluator() {
        return new NodeGuiSensorEvaluator(owner);
    }

    private NodeEventSensorEvaluator eventSensorEvaluator() {
        return new NodeEventSensorEvaluator(owner);
    }

    private NodeOperatorSensorEvaluator operatorSensorEvaluator() {
        return new NodeOperatorSensorEvaluator(owner);
    }

    private NodeAttributeDetectionEvaluator attributeDetectionEvaluator() {
        return new NodeAttributeDetectionEvaluator(owner);
    }

    private NodePlayerStateSensorEvaluator playerStateSensorEvaluator() {
        return new NodePlayerStateSensorEvaluator(owner);
    }

    private NodeProximitySensorEvaluator proximitySensorEvaluator() {
        return new NodeProximitySensorEvaluator(owner);
    }

    private NodeBasicSensorEvaluator basicSensorEvaluator() {
        return new NodeBasicSensorEvaluator(owner);
    }

    private NodeInventorySensorEvaluator inventorySensorEvaluator() {
        return new NodeInventorySensorEvaluator(owner);
    }

    private NodeVisibilitySensorEvaluator visibilitySensorEvaluator() {
        return new NodeVisibilitySensorEvaluator(owner);
    }

    private NodeVillagerTradeSensorEvaluator villagerTradeSensorEvaluator() {
        return new NodeVillagerTradeSensorEvaluator(owner);
    }
}
