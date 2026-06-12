package com.pathmind.nodes;

import java.util.EnumSet;

public final class NodeCompatibility {
    private NodeCompatibility() {
    }

    public static boolean canHostSlot(NodeType hostType, NodeSlotType slotType) {
        if (hostType == null || slotType == null) {
            return false;
        }
        switch (slotType) {
            case SENSOR:
                return hostType == NodeType.CONTROL_IF
                    || hostType == NodeType.CONTROL_IF_ELSE
                    || hostType == NodeType.CONTROL_REPEAT_UNTIL
                    || hostType == NodeType.CONTROL_WAIT_UNTIL;
            case ACTION:
                return hostType == NodeType.CONTROL_REPEAT
                    || hostType == NodeType.CONTROL_REPEAT_UNTIL
                    || hostType == NodeType.CONTROL_FOREVER;
            case PARAMETER:
            default:
                return true;
        }
    }

    public static boolean canAttachToSlot(Node host, Node candidate, NodeSlotType slotType, int slotIndex) {
        if (host == null || candidate == null || slotType == null) {
            return false;
        }
        if (host == candidate) {
            return false;
        }
        switch (slotType) {
            case SENSOR:
                return canHostSlot(host.getType(), NodeSlotType.SENSOR)
                    && NodeTraitRegistry.isBooleanSensor(candidate.getType());
            case ACTION:
                return canAttachActionNode(host, candidate);
            case PARAMETER:
            default:
                return canAttachParameterNode(host, candidate, slotIndex);
        }
    }

    private static boolean canAttachActionNode(Node host, Node candidate) {
        if (!canHostSlot(host.getType(), NodeSlotType.ACTION)) {
            return false;
        }
        if (candidate.isSensorNode()) {
            return false;
        }
        if (candidate.getType() == NodeType.EVENT_FUNCTION) {
            return false;
        }
        if (host.getAttachedActionNode() != null && host.getAttachedActionNode() != candidate) {
            return false;
        }
        return true;
    }

    private static boolean canAttachParameterNode(Node host, Node candidate, int slotIndex) {
        if (!host.canAcceptParameterAt(slotIndex)) {
            return false;
        }

        if (isConfigurableNumericComparisonOperand(host.getType(), candidate.getType())) {
            return true;
        }

        NodeType candidateType = candidate.getType();
        EnumSet<NodeValueTrait> accepted = NodeTraitRegistry.getAcceptedTraits(host.getType(), slotIndex);
        if (accepted.isEmpty()) {
            return false;
        }

        EnumSet<NodeValueTrait> provided = candidate.getProvidedTraits();
        boolean isBooleanSensor = NodeTraitRegistry.isBooleanSensor(candidateType);
        boolean isParameterLike = NodeTraitRegistry.isParameterNode(candidateType)
            || !provided.isEmpty()
            || candidateType == NodeType.VARIABLE;
        if (!isParameterLike) {
            return isBooleanSensor
                && (accepted.contains(NodeValueTrait.BOOLEAN) || accepted.contains(NodeValueTrait.ANY));
        }

        if (candidateType == NodeType.VARIABLE) {
            return true;
        }

        if (accepted.contains(NodeValueTrait.BOOLEAN) && isBooleanSensor) {
            return true;
        }
        if (accepted.contains(NodeValueTrait.ANY)) {
            return true;
        }
        for (NodeValueTrait trait : provided) {
            if (accepted.contains(trait)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canAttachResolvedType(NodeType hostType, NodeType candidateType, int slotIndex) {
        if (hostType == null || candidateType == null) {
            return false;
        }
        EnumSet<NodeValueTrait> accepted = NodeTraitRegistry.getAcceptedTraits(hostType, slotIndex);
        if (accepted.isEmpty()) {
            return false;
        }
        if (accepted.contains(NodeValueTrait.ANY)) {
            return true;
        }
        EnumSet<NodeValueTrait> provided = NodeTraitRegistry.getProvidedTraits(candidateType);
        if (provided.isEmpty()) {
            return false;
        }
        for (NodeValueTrait trait : provided) {
            if (accepted.contains(trait)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigurableNumericComparisonOperand(NodeType hostType, NodeType candidateType) {
        if (hostType != NodeType.OPERATOR_GREATER && hostType != NodeType.OPERATOR_LESS) {
            return false;
        }
        return candidateType == NodeType.SENSOR_POSITION_OF
            || candidateType == NodeType.SENSOR_DISTANCE_BETWEEN
            || candidateType == NodeType.SENSOR_IS_ON_GROUND;
    }
}
