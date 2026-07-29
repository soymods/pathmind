package com.pathmind.nodes;

final class NodeAttachmentCoordinator {
    private final Node owner;

    NodeAttachmentCoordinator(Node owner) {
        this.owner = owner;
    }

    boolean attachSensor(Node sensor) {
        if (!owner.canAcceptSensor() || sensor == null || !sensor.isSensorNode() || sensor == owner) {
            return false;
        }

        NodeAttachments attachments = owner.getAttachments();
        if (attachments.isSensorAttachedTo(owner, sensor)) {
            owner.updateAttachedSensorPosition();
            return true;
        }

        if (sensor.getAttachments().getParentControl() != null) {
            sensor.getAttachments().getParentControl().detachSensor();
        }

        Node previousSensor = attachments.attachSensor(owner, sensor);
        if (previousSensor != null) {
            previousSensor.setDragging(false);
            previousSensor.setSelected(false);
            previousSensor.setPositionSilently(owner.getX() + owner.getWidth() + Node.SENSOR_SLOT_MARGIN_HORIZONTAL, owner.getY());
        }

        sensor.setDragging(false);
        sensor.setSelected(false);

        owner.recalculateDimensions();
        owner.updateAttachedSensorPosition();
        return true;
    }

    void detachSensor() {
        Node sensor = owner.getAttachments().detachSensor();
        if (sensor != null) {
            owner.recalculateDimensions();
        }
    }

    boolean attachParameter(Node parameter, int slotIndex, boolean enforceCompatibility) {
        if (parameter == null
            || !Node.isUsableAsParameterType(parameter.getType())
            || parameter == owner) {
            return false;
        }
        NodeType ownerType = owner.getType();
        if ((ownerType == NodeType.PLACE || ownerType == NodeType.PLACE_HAND)
            && slotIndex == 1
            && parameter.getType() != null) {
            NodeType parameterType = parameter.getType();
            if (parameterType == NodeType.PARAM_INVENTORY_SLOT) {
                // Inventory-slot parameters should always occupy the first slot
                slotIndex = 0;
            }
        }
        if (!owner.canAcceptParameterAt(slotIndex)) {
            return false;
        }

        NodeAttachments parameterAttachments = parameter.getAttachments();
        if (parameterAttachments.isAttachedToParameterHost(owner, slotIndex)) {
            parameter.recalculateDimensions();
            owner.refreshAttachedParameterValues();
            owner.recalculateDimensions();
            owner.updateAttachedParameterPosition(slotIndex);
            updateParentControlLayout();
            return true;
        }

        if (enforceCompatibility && !owner.isParameterSupported(parameter, slotIndex)) {
            owner.sendIncompatibleParameterMessage(parameter);
            return false;
        }

        Node previousHost = parameterAttachments.getParentParameterHost();
        int previousSlot = parameterAttachments.getParentParameterSlotIndex();

        if (previousHost != null && (previousHost != owner || previousSlot != slotIndex)) {
            previousHost.detachParameter(previousSlot);
        }

        NodeAttachments attachments = owner.getAttachments();
        Node replaced = attachments.getAttachedParameter(slotIndex);
        if (replaced != null && replaced != parameter) {
            replaced = attachments.detachParameter(slotIndex);
            if (replaced != null) {
                replaced.setSocketsHidden(false);
                replaced.recalculateDimensions();
                replaced.setPositionSilently(owner.getX() + owner.getWidth() + Node.PARAMETER_SLOT_MARGIN_HORIZONTAL, owner.getY());
            }
        }

        attachments.attachParameter(owner, slotIndex, parameter);
        parameter.setDragging(false);
        parameter.setSelected(false);
        parameter.setSocketsHidden(true);
        parameter.recalculateDimensions();

        owner.refreshAttachedParameterValues();

        owner.recalculateDimensions();
        owner.updateAttachedParameterPositions();
        updateParentControlLayout();

        return true;
    }

    void detachParameter(int slotIndex) {
        Node parameter = owner.getAttachments().detachParameter(slotIndex);
        if (parameter == null) {
            return;
        }
        parameter.setSocketsHidden(false);
        parameter.recalculateDimensions();
        parameter.setPositionSilently(owner.getX() + owner.getWidth() + Node.PARAMETER_SLOT_MARGIN_HORIZONTAL, owner.getY());

        owner.refreshAttachedParameterValues();
        owner.recalculateDimensions();
        owner.updateAttachedParameterPositions();
        updateParentControlLayout();
    }

    boolean canAcceptActionNode(Node node) {
        return NodeCompatibility.canAttachToSlot(owner, node, NodeSlotType.ACTION, 0);
    }

    boolean attachActionNode(Node node) {
        if (!canAcceptActionNode(node)) {
            return false;
        }

        NodeAttachments attachments = owner.getAttachments();
        if (attachments.isActionNodeAttachedTo(owner, node)) {
            owner.updateAttachedActionPosition();
            return true;
        }

        if (node.getAttachments().getParentActionControl() != null) {
            node.getAttachments().getParentActionControl().detachActionNode();
        }

        Node previous = attachments.attachActionNode(owner, node);
        if (previous != null) {
            previous.setDragging(false);
            previous.setSelected(false);
            previous.setPositionSilently(owner.getX() + owner.getWidth() + Node.ACTION_SLOT_MARGIN_HORIZONTAL, owner.getY());
        }

        node.setDragging(false);
        node.setSelected(false);
        node.setSocketsHidden(true);

        owner.recalculateDimensions();
        owner.updateAttachedActionPosition();
        return true;
    }

    void detachActionNode() {
        Node node = owner.getAttachments().detachActionNode();
        if (node != null) {
            node.setSocketsHidden(false);
            owner.recalculateDimensions();
        }
    }

    void notifyParentParameterHostOfResize() {
        NodeAttachments attachments = owner.getAttachments();
        if (attachments.getParentParameterHost() == null || attachments.getParentParameterSlotIndex() < 0) {
            return;
        }
        attachments.getParentParameterHost().onAttachedParameterResized(attachments.getParentParameterSlotIndex());
    }

    void onAttachedParameterResized(int slotIndex) {
        owner.recalculateDimensions();
        updateParentControlLayout();
    }

    void notifyParentActionControlOfResize() {
        NodeAttachments attachments = owner.getAttachments();
        if (attachments.getParentActionControl() == null) {
            return;
        }
        attachments.getParentActionControl().onAttachedActionResized();
    }

    void onAttachedActionResized() {
        owner.recalculateDimensions();
        owner.updateAttachedActionPosition();
    }

    void notifyParentControlOfResize() {
        NodeAttachments attachments = owner.getAttachments();
        if (attachments.getParentControl() == null) {
            return;
        }
        attachments.getParentControl().onAttachedSensorResized();
    }

    void onAttachedSensorResized() {
        owner.recalculateDimensions();
        owner.updateAttachedSensorPosition();
    }

    void updateParentControlLayout() {
        NodeAttachments attachments = owner.getAttachments();
        if (attachments.getParentControl() != null) {
            attachments.getParentControl().recalculateDimensions();
            attachments.getParentControl().updateAttachedSensorPosition();
        }
    }
}
