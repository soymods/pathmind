package com.pathmind.nodes;

import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Attachment relationship state for a node.
 *
 * <p>Attachment behavior still lives in Node during this migration; this class
 * only centralizes the relationship storage.
 */
final class NodeAttachments {
    private Node attachedSensor;
    private Node parentControl;
    private Node attachedActionNode;
    private Node parentActionControl;
    private final Map<Integer, Node> attachedParameters = new HashMap<>();
    private Node parentParameterHost;
    private int parentParameterSlotIndex = -1;

    boolean hasAttachedSensor() {
        return attachedSensor != null;
    }

    Node getAttachedSensor() {
        return attachedSensor;
    }

    boolean isSensorAttachedTo(Node owner, Node sensor) {
        return sensor != null
            && attachedSensor == sensor
            && sensor.getAttachments().parentControl == owner;
    }

    Node attachSensor(Node owner, Node sensor) {
        Node previous = attachedSensor;
        if (previous != null && previous != sensor) {
            previous.getAttachments().parentControl = null;
        }
        attachedSensor = sensor;
        sensor.getAttachments().parentControl = owner;
        return previous != sensor ? previous : null;
    }

    Node detachSensor() {
        Node sensor = attachedSensor;
        if (sensor != null) {
            sensor.getAttachments().parentControl = null;
            attachedSensor = null;
        }
        return sensor;
    }

    boolean hasParentControl() {
        return parentControl != null;
    }

    Node getParentControl() {
        return parentControl;
    }

    boolean hasAttachedActionNode() {
        return attachedActionNode != null;
    }

    Node getAttachedActionNode() {
        return attachedActionNode;
    }

    boolean isActionNodeAttachedTo(Node owner, Node actionNode) {
        return actionNode != null
            && attachedActionNode == actionNode
            && actionNode.getAttachments().parentActionControl == owner;
    }

    Node attachActionNode(Node owner, Node actionNode) {
        Node previous = attachedActionNode;
        if (previous != null && previous != actionNode) {
            previous.getAttachments().parentActionControl = null;
        }
        attachedActionNode = actionNode;
        actionNode.getAttachments().parentActionControl = owner;
        return previous != actionNode ? previous : null;
    }

    Node detachActionNode() {
        Node actionNode = attachedActionNode;
        if (actionNode != null) {
            actionNode.getAttachments().parentActionControl = null;
            attachedActionNode = null;
        }
        return actionNode;
    }

    boolean hasParentActionControl() {
        return parentActionControl != null;
    }

    Node getParentActionControl() {
        return parentActionControl;
    }

    boolean hasAttachedParameters() {
        return !attachedParameters.isEmpty();
    }

    Node getAttachedParameter(int slotIndex) {
        return attachedParameters.get(slotIndex);
    }

    Node attachParameter(Node owner, int slotIndex, Node parameter) {
        Node previous = attachedParameters.put(slotIndex, parameter);
        if (previous != null && previous != parameter) {
            previous.getAttachments().clearParentParameterHost();
        }
        parameter.getAttachments().setParentParameterHost(owner, slotIndex);
        return previous != parameter ? previous : null;
    }

    Node detachParameter(int slotIndex) {
        Node parameter = attachedParameters.remove(slotIndex);
        if (parameter != null) {
            parameter.getAttachments().clearParentParameterHost();
        }
        return parameter;
    }

    Map<Integer, Node> getAttachedParametersView() {
        return Collections.unmodifiableMap(attachedParameters);
    }

    Set<Integer> getAttachedParameterSlotIndices() {
        return attachedParameters.keySet();
    }

    Collection<Node> getAttachedParameterNodes() {
        return attachedParameters.values();
    }

    Node getParentParameterHost() {
        return parentParameterHost;
    }

    int getParentParameterSlotIndex() {
        return parentParameterSlotIndex;
    }

    boolean isAttachedToParameterHost(Node host, int slotIndex) {
        return parentParameterHost == host && parentParameterSlotIndex == slotIndex;
    }

    void setParentParameterHost(Node host, int slotIndex) {
        parentParameterHost = host;
        parentParameterSlotIndex = slotIndex;
    }

    void clearParentParameterHost() {
        parentParameterHost = null;
        parentParameterSlotIndex = -1;
    }
}
