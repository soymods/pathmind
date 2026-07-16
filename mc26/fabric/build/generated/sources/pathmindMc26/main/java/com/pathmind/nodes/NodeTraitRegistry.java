package com.pathmind.nodes;

import java.util.EnumSet;
import java.util.Map;

/**
 * Compatibility facade for node value traits and parameter slots.
 *
 * <p>New node metadata belongs in {@link NodeCatalog}. This class keeps the
 * older call sites stable while the editor and execution code are consolidated.
 */
public final class NodeTraitRegistry {
    private NodeTraitRegistry() {
    }

    public static boolean isBooleanSensor(NodeType type) {
        return NodeCatalog.isBooleanSensor(type);
    }

    public static boolean isSensorWithoutParameterSlot(NodeType type) {
        return NodeCatalog.isSensorWithoutParameterSlot(type);
    }

    public static boolean isSensorParameterRequired(NodeType type) {
        return NodeCatalog.isSensorParameterRequired(type);
    }

    public static boolean isParameterNode(NodeType type) {
        return NodeCatalog.isParameterNode(type);
    }

    public static EnumSet<NodeValueTrait> getProvidedTraits(NodeType type) {
        return NodeCatalog.providedTraits(type);
    }

    public static EnumSet<NodeValueTrait> getAcceptedTraits(NodeType hostType, int slotIndex) {
        return NodeCatalog.acceptedTraits(hostType, slotIndex);
    }

    public static Map<NodeType, EnumSet<NodeValueTrait>> getProvidedTraitsSnapshot() {
        return NodeCatalog.providedTraitsSnapshot();
    }

    public static boolean canHostParameter(NodeType type) {
        return NodeCatalog.canHostParameter(type);
    }

    public static int getParameterSlotCount(NodeType hostType) {
        return NodeCatalog.parameterSlotCount(hostType);
    }

    public static String getParameterSlotLabel(NodeType hostType, int slotIndex) {
        return NodeCatalog.parameterSlotLabel(hostType, slotIndex);
    }

    public static boolean isParameterSlotAlwaysRequired(NodeType hostType, int slotIndex) {
        return NodeCatalog.isParameterSlotAlwaysRequired(hostType, slotIndex);
    }
}
