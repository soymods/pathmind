package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityStateOptions;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class NodeParameterValueExporter {
    private NodeParameterValueExporter() {
    }

    static Map<String, String> exportParameterValues(Node node) {
        Map<String, String> values = new HashMap<>();
        for (NodeParameter parameter : node.getParameters()) {
            String key = parameter.getName();
            String value = parameter.getStringValue();
            values.put(key, value);
            values.put(Node.normalizeParameterKey(key), value);
        }

        NodeBehaviorDefinition behaviorDefinition = NodeBehaviorDefinitionRegistry.get(node.getType());
        if (behaviorDefinition != null && behaviorDefinition.hasParameterBehavior()) {
            return behaviorDefinition.exportValues(node, values);
        }

        switch (node.getType()) {
            case LIST_LENGTH -> {
                Optional<Integer> length = node.resolveListLengthValue(node);
                String amount = length.map(String::valueOf).orElse("0");
                values.put("Amount", amount);
                values.put(Node.normalizeParameterKey("Amount"), amount);
                values.put("Count", amount);
                values.put(Node.normalizeParameterKey("Count"), amount);
                values.put("Threshold", amount);
                values.put(Node.normalizeParameterKey("Threshold"), amount);
                values.put("Value", amount);
                values.put(Node.normalizeParameterKey("Value"), amount);
            }
            case LIST_ITEM -> {
                Node resolved = node.resolveListItemValueNode(node, null, false, null);
                if (resolved != null) {
                    return resolved.exportParameterValues();
                }
            }
            case OPERATOR_RANDOM -> {
                double min = node.getDoubleParameter("Min", 0.0);
                double max = node.getDoubleParameter("Max", 1.0);
                double randomValue = node.generateRandomValueWithRounding(min, max);
                String value = Double.toString(randomValue);
                values.put("Amount", value);
                values.put(Node.normalizeParameterKey("Amount"), value);
                values.put("Count", value);
                values.put(Node.normalizeParameterKey("Count"), value);
                values.put("Threshold", value);
                values.put(Node.normalizeParameterKey("Threshold"), value);
                values.put("Value", value);
                values.put(Node.normalizeParameterKey("Value"), value);
            }
            case OPERATOR_MOD -> {
                double modValue = node.resolveModValue().orElse(0.0);
                String value = Double.toString(modValue);
                values.put("Amount", value);
                values.put(Node.normalizeParameterKey("Amount"), value);
                values.put("Count", value);
                values.put(Node.normalizeParameterKey("Count"), value);
                values.put("Threshold", value);
                values.put(Node.normalizeParameterKey("Threshold"), value);
                values.put("Value", value);
                values.put(Node.normalizeParameterKey("Value"), value);
            }
            case SENSOR_POSITION_OF -> {
                Node parameterNode = node.getAttachedParameter(0);
                if (parameterNode == null) {
                    break;
                }
                Optional<Vec3> resolved = node.resolvePositionTarget(parameterNode, null, null);
                if (resolved.isEmpty()) {
                    break;
                }
                Vec3 position = resolved.get();
                if (node.isSensorPositionSingleAxisMode()) {
                    String componentKey = node.getSensorPositionComponentKey();
                    String componentValue = switch (componentKey) {
                        case "X" -> Double.toString(position.x);
                        case "Y" -> Double.toString(position.y);
                        case "Z" -> Double.toString(position.z);
                        default -> "";
                    };
                    if (!componentValue.isEmpty()) {
                        values.put("Amount", componentValue);
                        values.put(Node.normalizeParameterKey("Amount"), componentValue);
                        values.put("Count", componentValue);
                        values.put(Node.normalizeParameterKey("Count"), componentValue);
                        values.put("Threshold", componentValue);
                        values.put(Node.normalizeParameterKey("Threshold"), componentValue);
                        values.put("Value", componentValue);
                        values.put(Node.normalizeParameterKey("Value"), componentValue);
                    }
                } else {
                    String xValue = Double.toString(position.x);
                    String yValue = Double.toString(position.y);
                    String zValue = Double.toString(position.z);
                    values.put("X", xValue);
                    values.put(Node.normalizeParameterKey("X"), xValue);
                    values.put("Y", yValue);
                    values.put(Node.normalizeParameterKey("Y"), yValue);
                    values.put("Z", zValue);
                    values.put(Node.normalizeParameterKey("Z"), zValue);
                }
            }
            case SENSOR_DISTANCE_BETWEEN -> {
                Node parameterNodeA = node.resolveSensorParameterNode(node.getAttachedParameter(0), 0);
                Node parameterNodeB = node.resolveSensorParameterNode(node.getAttachedParameter(1), 1);
                if (parameterNodeA == null || parameterNodeB == null) {
                    break;
                }
                if (!node.isDistanceBetweenSupportedTarget(parameterNodeA)) {
                    break;
                }
                if (!node.isDistanceBetweenSupportedTarget(parameterNodeB)) {
                    break;
                }
                Optional<Vec3> resolvedA = node.resolveDistanceBetweenTarget(parameterNodeA);
                Optional<Vec3> resolvedB = node.resolveDistanceBetweenTarget(parameterNodeB);
                if (resolvedA.isEmpty() || resolvedB.isEmpty()) {
                    break;
                }
                double distance = Math.sqrt(resolvedA.get().distanceToSqr(resolvedB.get()));
                String distanceValue = Double.toString(distance);
                values.put("Distance", distanceValue);
                values.put(Node.normalizeParameterKey("Distance"), distanceValue);
            }
            case SENSOR_TARGETED_BLOCK -> {
                Optional<BlockState> targetState = node.getTargetedBlockState();
                if (targetState.isEmpty()) {
                    break;
                }
                BlockState state = targetState.get();
                Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                String blockId = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
                String stateValue = BlockSelection.describeState(state);
                values.put("Block", blockId);
                values.put(Node.normalizeParameterKey("Block"), blockId);
                values.put("State", stateValue);
                values.put(Node.normalizeParameterKey("State"), stateValue);
            }
            case SENSOR_TARGETED_ENTITY -> {
                Optional<Entity> targetedEntity = node.getTargetedEntity();
                if (targetedEntity.isEmpty()) {
                    break;
                }
                Entity entity = targetedEntity.get();
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                String entityId = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
                values.put("Entity", entityId);
                values.put(Node.normalizeParameterKey("Entity"), entityId);
                String stateValue = EntityStateOptions.describe(entity);
                values.put("State", stateValue);
                values.put(Node.normalizeParameterKey("State"), stateValue);
            }
            case SENSOR_CURRENT_GUI -> {
                node.guiSensorEvaluator().exportCurrentGuiValues(values);
            }
            case SENSOR_LOOK_DIRECTION -> {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.player != null) {
                    float yaw = Node.normalizeLookYaw(client.player.getYRot());
                    float pitch = client.player.getXRot();
                    String yawValue = Node.formatFloat(yaw);
                    String pitchValue = Node.formatFloat(pitch);
                    if (node.isSensorLookSingleAxisMode()) {
                        String componentKey = node.getSensorLookComponentKey();
                        String componentValue = "Yaw".equals(componentKey) ? yawValue : "Pitch".equals(componentKey) ? pitchValue : "";
                        if (!componentValue.isEmpty()) {
                            values.put("Amount", componentValue);
                            values.put(Node.normalizeParameterKey("Amount"), componentValue);
                            values.put("Count", componentValue);
                            values.put(Node.normalizeParameterKey("Count"), componentValue);
                            values.put("Threshold", componentValue);
                            values.put(Node.normalizeParameterKey("Threshold"), componentValue);
                            values.put("Value", componentValue);
                            values.put(Node.normalizeParameterKey("Value"), componentValue);
                        }
                    } else {
                        values.put("Yaw", yawValue);
                        values.put(Node.normalizeParameterKey("Yaw"), yawValue);
                        values.put("Pitch", pitchValue);
                        values.put(Node.normalizeParameterKey("Pitch"), pitchValue);
                    }
                }
            }
            case SENSOR_CURRENT_HAND -> {
                Optional<Integer> currentSlot = node.getCurrentHotbarSlot();
                if (currentSlot.isEmpty()) {
                    break;
                }
                String slotValue = Integer.toString(currentSlot.get());
                values.put("Slot", slotValue);
                values.put(Node.normalizeParameterKey("Slot"), slotValue);
                values.put("SourceSlot", slotValue);
                values.put(Node.normalizeParameterKey("SourceSlot"), slotValue);
                values.put("TargetSlot", slotValue);
                values.put(Node.normalizeParameterKey("TargetSlot"), slotValue);
            }
            case SENSOR_IS_ON_GROUND -> {
                Optional<Double> distanceFromGround = node.getDistanceFromGround();
                if (distanceFromGround.isEmpty()) {
                    break;
                }
                String distanceValue = Double.toString(distanceFromGround.get());
                values.put("Distance", distanceValue);
                values.put(Node.normalizeParameterKey("Distance"), distanceValue);
                values.put("Value", distanceValue);
                values.put(Node.normalizeParameterKey("Value"), distanceValue);
            }
            case SENSOR_TARGETED_BLOCK_FACE -> {
                Optional<Direction> targetFace = node.getTargetedBlockFace();
                if (targetFace.isEmpty()) {
                    break;
                }
                String faceValue = targetFace.get().toString().toLowerCase(Locale.ROOT);
                values.put("Side", faceValue);
                values.put(Node.normalizeParameterKey("Side"), faceValue);
                values.put("Face", faceValue);
                values.put(Node.normalizeParameterKey("Face"), faceValue);
                values.put("Text", faceValue);
                values.put(Node.normalizeParameterKey("Text"), faceValue);
                values.put("Message", faceValue);
                values.put(Node.normalizeParameterKey("Message"), faceValue);
            }
            case SENSOR_SLOT_ITEM_COUNT -> {
                Node slotNode = node.resolveSensorParameterNode(node.getAttachedParameter(0), 0);
                int count = 0;
                if (slotNode != null && node.providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
                    count = Math.max(0, node.resolveInventorySlotCount(slotNode).orElse(0));
                }
                String countValue = Integer.toString(count);
                values.put("Amount", countValue);
                values.put(Node.normalizeParameterKey("Amount"), countValue);
                values.put("Count", countValue);
                values.put(Node.normalizeParameterKey("Count"), countValue);
                values.put("Value", countValue);
                values.put(Node.normalizeParameterKey("Value"), countValue);
            }
            case SENSOR_DURABILITY_OF -> {
                Node slotNode = node.resolveSensorParameterNode(node.getAttachedParameter(0), 0);
                int durability = 0;
                if (slotNode != null && node.providesTrait(slotNode, NodeValueTrait.INVENTORY_SLOT)) {
                    durability = Math.max(0, node.resolveInventorySlotDurability(slotNode).orElse(0));
                }
                String durabilityValue = Integer.toString(durability);
                values.put("Amount", durabilityValue);
                values.put(Node.normalizeParameterKey("Amount"), durabilityValue);
                values.put("Durability", durabilityValue);
                values.put(Node.normalizeParameterKey("Durability"), durabilityValue);
                values.put("Value", durabilityValue);
                values.put(Node.normalizeParameterKey("Value"), durabilityValue);
            }
            case SENSOR_FIND_TRADE -> {
                node.villagerTradeSensorEvaluator().exportTradeSlotValues(values);
            }
        }

        return values;
    }
}
