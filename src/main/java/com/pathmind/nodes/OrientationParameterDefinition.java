package com.pathmind.nodes;

import com.pathmind.util.EntityCompatibilityBridge;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class OrientationParameterDefinition {
    static Optional<NodeBehaviorDefinition> definition(NodeType type) {
        return switch (type) {
            case PARAM_ROTATION -> Optional.of(rotationDefinition());
            case PARAM_DIRECTION -> Optional.of(directionDefinition());
            case PARAM_BLOCK_FACE -> Optional.of(blockFaceDefinition());
            default -> Optional.empty();
        };
    }

    static NodeBehaviorDefinitionSupport.Orientation applyDirection(String direction, float currentYaw, float currentPitch) {
        return NodeBehaviorDefinitionSupport.applyDirection(direction, currentYaw, currentPitch);
    }

    private static NodeBehaviorDefinition rotationDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_ROTATION)
            .parameterBehavior(OrientationParameterDefinition::exportRotationValues)
            .runtimeBehavior(OrientationParameterDefinition::resolvePositionTarget)
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable((owner, node) -> {
                String formatted = owner.formatRotationValues(node.exportParameterValues());
                return formatted.isEmpty() ? Optional.empty() : Optional.of(formatted);
            }))
            .build();
    }

    private static NodeBehaviorDefinition directionDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_DIRECTION)
            .parameterBehavior(OrientationParameterDefinition::exportDirectionValues)
            .runtimeBehavior(OrientationParameterDefinition::resolvePositionTarget)
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable(OrientationParameterDefinition::resolveDirectionComparableString))
            .build();
    }

    private static NodeBehaviorDefinition blockFaceDefinition() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_BLOCK_FACE)
            .parameterBehavior(OrientationParameterDefinition::exportBlockFaceValues)
            .runtimeBehavior(OrientationParameterDefinition::resolvePositionTarget)
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable(OrientationParameterDefinition::resolveBlockFaceComparableString))
            .build();
    }

    private static Map<String, String> exportRotationValues(Node node, Map<String, String> values) {
        String yaw = values.get("Yaw");
        if (yaw != null) {
            NodeBehaviorDefinitionSupport.put(values, "YawOffset", yaw);
        }
        String pitch = values.get("Pitch");
        if (pitch != null) {
            NodeBehaviorDefinitionSupport.put(values, "PitchOffset", pitch);
        }
        return values;
    }

    private static Map<String, String> exportDirectionValues(Node node, Map<String, String> values) {
        String modeValue = node.isDirectionModeExact() ? "exact" : "cardinal";
        NodeBehaviorDefinitionSupport.put(values, "Mode", modeValue);
        String direction = values.get("Direction");
        if ("cardinal".equals(modeValue) && direction != null && !direction.trim().isEmpty()) {
            applyCardinalDirection(values, direction);
        }
        return values;
    }

    private static Map<String, String> exportBlockFaceValues(Node node, Map<String, String> values) {
        String face = values.get("Face");
        if (face != null && !face.trim().isEmpty()) {
            NodeBehaviorDefinitionSupport.put(values, "Side", face);
            NodeBehaviorDefinitionSupport.put(values, "Direction", face);
        }
        return values;
    }

    private static Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return Optional.empty();
        }
        Vec3d origin = EntityCompatibilityBridge.getPos(client.player);
        if (origin == null) {
            return Optional.empty();
        }

        NodeType parameterType = parameterNode.getType();
        Float yawParam = Node.parseNodeFloat(parameterNode, "Yaw");
        Float pitchParam = Node.parseNodeFloat(parameterNode, "Pitch");
        float yaw = yawParam != null ? yawParam : client.player.getYaw();
        float pitch = pitchParam != null ? pitchParam : client.player.getPitch();

        NodeBehaviorDefinitionSupport.Orientation orientation = resolveNamedOrientation(parameterType, parameterNode, yaw, pitch);
        yaw = orientation.yaw;
        pitch = orientation.pitch;

        if (isGotoLike(owner.getType()) && !isCoordinateMode(owner.getMode())) {
            return Optional.empty();
        }

        Float yawOffset = Node.parseNodeFloat(parameterNode, "YawOffset");
        Float pitchOffset = Node.parseNodeFloat(parameterNode, "PitchOffset");
        if (yawOffset != null) {
            yaw += yawOffset;
        }
        if (pitchOffset != null) {
            pitch += pitchOffset;
        }

        double distance = Math.max(0.0, Node.parseNodeDouble(parameterNode, "Distance", defaultDirectionDistance(parameterType, parameterNode)));
        Vec3d target = projectTarget(origin, yaw, pitch, distance);
        if (data != null) {
            data.targetVector = target;
            data.targetBlockPos = new BlockPos(MathHelper.floor(target.x), MathHelper.floor(target.y), MathHelper.floor(target.z));
            data.resolvedYaw = yaw;
            data.resolvedPitch = pitch;
        }
        return Optional.of(target);
    }

    private static Optional<String> resolveDirectionComparableString(Node owner, Node node) {
        String formatted = owner.formatRotationValues(node.exportParameterValues());
        if (!formatted.isEmpty()) {
            return Optional.of(formatted);
        }
        String direction = Node.getParameterString(node, "Direction");
        if (direction == null || direction.trim().isEmpty()) {
            direction = Node.getParameterString(node, "Side");
        }
        if (direction == null || direction.trim().isEmpty()) {
            direction = Node.getParameterString(node, "Face");
        }
        return direction == null || direction.trim().isEmpty() ? Optional.empty() : Optional.of(direction.trim());
    }

    private static Optional<String> resolveBlockFaceComparableString(Node owner, Node node) {
        String face = Node.getParameterString(node, "Face");
        if (face == null || face.trim().isEmpty()) {
            face = Node.getParameterString(node, "Side");
        }
        if (face == null || face.trim().isEmpty()) {
            face = Node.getParameterString(node, "Direction");
        }
        return face == null || face.trim().isEmpty() ? Optional.empty() : Optional.of(face.trim());
    }

    private static NodeBehaviorDefinitionSupport.Orientation resolveNamedOrientation(NodeType parameterType, Node parameterNode,
                                                                                    float currentYaw, float currentPitch) {
        if (parameterType == NodeType.PARAM_DIRECTION && parameterNode.isDirectionModeCardinal()) {
            String direction = Node.getParameterString(parameterNode, "Direction");
            return applyDirection(direction, currentYaw, currentPitch);
        }
        if (parameterType == NodeType.PARAM_BLOCK_FACE) {
            String direction = Node.getParameterString(parameterNode, "Face");
            if (direction == null || direction.trim().isEmpty()) {
                direction = Node.getParameterString(parameterNode, "Side");
            }
            return applyDirection(direction, currentYaw, currentPitch);
        }
        return new NodeBehaviorDefinitionSupport.Orientation(currentYaw, currentPitch);
    }

    private static boolean isGotoLike(NodeType ownerType) {
        return ownerType == NodeType.GOTO || ownerType == NodeType.TRAVEL || ownerType == NodeType.GOAL;
    }

    private static boolean isCoordinateMode(NodeMode mode) {
        return mode == NodeMode.GOTO_XYZ
            || mode == NodeMode.GOTO_XZ
            || mode == NodeMode.GOAL_XYZ
            || mode == NodeMode.GOAL_XZ;
    }

    private static double defaultDirectionDistance(NodeType parameterType, Node parameterNode) {
        if (parameterType == NodeType.PARAM_DIRECTION) {
            return parameterNode.isDirectionModeExact() ? Node.DEFAULT_DIRECTION_DISTANCE : 1.0;
        }
        if (parameterType == NodeType.PARAM_BLOCK_FACE) {
            return 1.0;
        }
        return Node.DEFAULT_DIRECTION_DISTANCE;
    }

    private static Vec3d projectTarget(Vec3d origin, float yaw, float pitch, double distance) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double xDir = -Math.sin(yawRad) * Math.cos(pitchRad);
        double yDir = -Math.sin(pitchRad);
        double zDir = Math.cos(yawRad) * Math.cos(pitchRad);
        return origin.add(xDir * distance, yDir * distance, zDir * distance);
    }

    private static void applyCardinalDirection(Map<String, String> values, String direction) {
        String normalized = direction.trim().toLowerCase(java.util.Locale.ROOT);
        Double yaw = null;
        Double pitch = null;
        switch (normalized) {
            case "north":
                yaw = 180.0;
                break;
            case "south":
                yaw = 0.0;
                break;
            case "west":
                yaw = 90.0;
                break;
            case "east":
                yaw = -90.0;
                break;
            case "up":
                pitch = -90.0;
                break;
            case "down":
                pitch = 90.0;
                break;
            default:
                break;
        }
        if (yaw != null) {
            NodeBehaviorDefinitionSupport.put(values, "Yaw", Double.toString(yaw));
        }
        if (pitch != null) {
            NodeBehaviorDefinitionSupport.put(values, "Pitch", Double.toString(pitch));
        }
        NodeBehaviorDefinitionSupport.put(values, "Side", direction);
        NodeBehaviorDefinitionSupport.put(values, "Face", direction);
        NodeBehaviorDefinitionSupport.put(values, "Text", direction);
        NodeBehaviorDefinitionSupport.put(values, "Message", direction);
    }

    private OrientationParameterDefinition() {
    }
}
